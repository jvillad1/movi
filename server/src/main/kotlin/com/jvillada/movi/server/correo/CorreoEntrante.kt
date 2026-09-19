package com.jvillada.movi.server.correo

import com.jvillada.movi.server.time.AppClock
import io.ktor.http.parseQueryString
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * # Las alertas que el banco manda por correo
 *
 * ## El defecto que cierra
 *
 * El banco se quedó tres días sin mandar un solo SMS, y hay cobros que **nunca** avisa por ningún
 * canal que dependa del teléfono: la «cuota de manejo cupo rotativo» ($21.640), su IVA ($4.112),
 * los intereses, las retenciones, los débitos automáticos. Esos aparecen en el extracto —un mes
 * después— o en la **alerta por correo**, que es el canal que el banco sí sostiene.
 *
 * Es el tercer hermano de la misma familia, no un sistema nuevo:
 *
 * | Canal | Quién captura | Qué cubre |
 * |---|---|---|
 * | SMS (`SmsRealtimeReceiver`, `SmsBackfill`) | el teléfono | lo que el banco manda por SMS |
 * | Notificaciones (`FiltroDeNotificaciones`, #317) | el teléfono | lo que **él** hace en la app del banco |
 * | **Correo (esto)** | el **server** | lo que el banco **se cobra solo**, con el teléfono apagado |
 *
 * Los tres desembocan en la MISMA tabla (`sms_messages`), la MISMA bandeja («Mensajes del banco»),
 * el MISMO parser (`parseSms`) y el MISMO dedupe (`SmsDedupe`). Superficie nueva de cliente: cero.
 *
 * ## Por qué esto vive en el server y los otros dos en el teléfono
 *
 * Porque la premisa es justo que el teléfono no esté. Un correo que Gmail reenvía llega aunque el
 * teléfono esté apagado, sin batería o con la app del banco desinstalada — que es exactamente la
 * clase de día en que se pierde un cobro automático.
 */
data class CorreoEntrante(
    /** Dirección del remitente, en minúsculas y sin el nombre de pantalla. */
    val remitente: String,
    /** El nombre de pantalla del remitente («Bancolombia»), vacío si no vino. */
    val nombreDelRemitente: String,
    /**
     * A quién iba, **en orden de confianza**: primero el destinatario de sobre (el que el proveedor
     * sí conoce), después los `To`/`Cc` de cabecera. Ver [tokenDelDestinatario].
     */
    val destinatarios: List<String>,
    val asunto: String,
    /** El cuerpo en texto plano, ya limpio de citas y firma. */
    val cuerpo: String,
    /** La fecha cruda tal como vino (`Date:` RFC 1123, o el epoch en segundos de Mailgun). */
    val fecha: String?,
    val idDelMensaje: String?,
)

/**
 * Tope del texto que se guarda en la fila. Una alerta de banco por correo es mucho más verbosa que
 * un SMS (160 caracteres) o que una notificación (500), pero el movimiento siempre está en las
 * primeras líneas: el resto es pie de página legal y «no respondas a este correo».
 *
 * El texto es además la clave de dedupe (ver `SmsDedupe`), así que guardar kilobytes no compra nada
 * y sí hace más frágil la comparación.
 */
internal const val MAX_TEXTO_DE_CORREO = 1_000

/** Tope del campo `bank` del wire (la columna `bank` es varchar(100)). */
internal const val MAX_MARCA_DE_ORIGEN_DE_CORREO = 100

/**
 * **Tope del cuerpo de la petición, en bytes.** Un proveedor de correo entrante reenvía el mensaje
 * COMPLETO —HTML, cabeceras, a veces los adjuntos en base64— dentro de un solo cuerpo. Sin este
 * corte, cualquiera que consiga el secreto (o el propio proveedor ante un correo con un PDF pegado)
 * le hace tragar decenas de megas en memoria al mismo proceso por el que el teléfono sincroniza.
 *
 * 256 KB es holgado para una alerta bancaria con su HTML y estrecho para un adjunto: de los
 * adjuntos no necesitamos nada, el movimiento está en el texto.
 */
internal const val MAX_CUERPO_DE_CORREO_BYTES = 256 * 1024

private val json = Json { isLenient = true; ignoreUnknownKeys = true }

/** Las direcciones que aparecen en una cabecera tipo `"Banco" <alertas@banco.com>, otro@x.com`. */
private val direccionRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

/** El nombre de pantalla de `"Bancolombia" <alertas@…>` o `Bancolombia <alertas@…>`. */
private val nombreRegex = Regex("""^\s*"?([^"<]*?)"?\s*<""")

/**
 * **Lee el cuerpo de la petición del proveedor, venga como venga.**
 *
 * Postmark y Mailgun no se parecen en nada —`TextBody` contra `body-plain`, `OriginalRecipient`
 * contra `recipient`— y Mailgun además postea `application/x-www-form-urlencoded` y no JSON. Por
 * eso acá no hay un `@Serializable` por proveedor sino una búsqueda por lista de nombres
 * candidatos, sin distinguir mayúsculas: un proveedor nuevo (Resend, Cloudflare Email Workers)
 * normalmente entra sin tocar nada, y uno que renombre un campo degrada a «no pude leerlo» en vez
 * de tirar una excepción.
 *
 * Devuelve `null` si el cuerpo no es ni JSON ni un formulario, o si no trae NADA de texto (ni
 * asunto ni cuerpo): eso no es un correo, es ruido.
 */
fun leerCorreoEntrante(crudo: String): CorreoEntrante? {
    val campos = comoJson(crudo) ?: comoFormulario(crudo) ?: return null

    val remitenteCrudo = campos.primero("From", "from", "sender").orEmpty()
    val remitente = (campos.primero("FromFull.Email", "sender")
        ?: direccionRegex.find(remitenteCrudo)?.value).orEmpty().trim().lowercase()
    val nombre = campos.primero("FromFull.Name")
        ?: nombreRegex.find(remitenteCrudo)?.groupValues?.get(1)?.trim().orEmpty()

    // El orden importa: ver [tokenDelDestinatario]. Primero el destinatario de SOBRE.
    val destinatarios = buildList {
        addAll(direcciones(campos.primero("OriginalRecipient", "recipient")))
        addAll(direcciones(campos.primero("ToFull.Email")))
        addAll(direcciones(campos.primero("To", "to")))
        addAll(direcciones(campos.primero("CcFull.Email")))
        addAll(direcciones(campos.primero("Cc", "cc")))
    }.distinct()

    val asunto = campos.primero("Subject", "subject").orEmpty().trim()

    // Texto primero; el HTML solo si no hay texto, y pasado por [sinEtiquetas] — nunca se
    // interpreta, se renderiza ni se le sigue un enlace.
    val texto = campos.primero("TextBody", "body-plain", "stripped-text", "text")
    val cuerpoCrudo = texto?.takeIf { it.isNotBlank() }
        ?: campos.primero("HtmlBody", "body-html", "html")?.let { sinEtiquetas(it) }
        ?: ""
    val cuerpo = limpiarCuerpoDelCorreo(cuerpoCrudo)

    if (asunto.isBlank() && cuerpo.isBlank()) return null

    return CorreoEntrante(
        remitente = remitente,
        nombreDelRemitente = nombre,
        destinatarios = destinatarios,
        asunto = asunto,
        cuerpo = cuerpo,
        fecha = campos.primero("Date", "date", "timestamp"),
        idDelMensaje = campos.primero("MessageID", "MessageId", "Message-Id", "message-id"),
    )
}

/**
 * Los campos del payload aplanados a `nombre -> valor`, con las claves en minúsculas.
 *
 * `FromFull.Email` y `ToFull.Email` son la forma aplanada de los objetos/arreglos anidados de
 * Postmark; un arreglo de objetos se aplana juntando sus valores con coma, que es justo lo que
 * [direcciones] sabe leer.
 */
private class CamposDelCorreo(private val valores: Map<String, String>) {
    fun primero(vararg nombres: String): String? =
        nombres.firstNotNullOfOrNull { valores[it.lowercase()]?.takeIf { v -> v.isNotBlank() } }
}

private fun comoJson(crudo: String): CamposDelCorreo? = runCatching {
    val raiz = json.parseToJsonElement(crudo) as? JsonObject ?: return@runCatching null
    val plano = LinkedHashMap<String, String>()
    aplanar(raiz, prefijo = "", destino = plano)
    CamposDelCorreo(plano)
}.getOrNull()

private fun aplanar(objeto: JsonObject, prefijo: String, destino: MutableMap<String, String>) {
    for ((clave, valor) in objeto) {
        val nombre = (if (prefijo.isEmpty()) clave else "$prefijo.$clave").lowercase()
        when (valor) {
            is JsonPrimitive -> destino.putIfAbsent(nombre, valor.content)
            is JsonObject -> aplanar(valor, nombre, destino)
            is JsonArray -> {
                // `ToFull: [{Email, Name}, …]` → `tofull.email = "a@x, b@y"`. Los arreglos de
                // cabeceras (`Headers: [{Name, Value}]`) se aplanan igual y no molestan a nadie.
                val hijos = valor.mapNotNull { it as? JsonObject }
                if (hijos.isNotEmpty()) {
                    val juntos = LinkedHashMap<String, MutableList<String>>()
                    hijos.forEach { hijo ->
                        val parcial = LinkedHashMap<String, String>()
                        aplanar(hijo, nombre, parcial)
                        parcial.forEach { (k, v) -> juntos.getOrPut(k) { mutableListOf() } += v }
                    }
                    juntos.forEach { (k, v) -> destino.putIfAbsent(k, v.joinToString(", ")) }
                } else {
                    val textos = valor.mapNotNull { (it as? JsonPrimitive)?.content }
                    if (textos.isNotEmpty()) destino.putIfAbsent(nombre, textos.joinToString(", "))
                }
            }
        }
    }
}

/** Mailgun postea `application/x-www-form-urlencoded`, no JSON. Cuesta seis líneas aceptarlo. */
private fun comoFormulario(crudo: String): CamposDelCorreo? = runCatching {
    val parametros = parseQueryString(crudo)
    if (parametros.isEmpty()) return@runCatching null
    val plano = LinkedHashMap<String, String>()
    parametros.entries().forEach { (clave, valores) ->
        valores.firstOrNull()?.let { plano.putIfAbsent(clave.lowercase(), it) }
    }
    // Un JSON malformado también «parsea» como query string (una sola clave gigante sin `=`), así
    // que esto solo se acepta si trae al menos un campo que un proveedor de correo escribiría.
    if (plano.keys.none { it in setOf("subject", "body-plain", "recipient", "from", "stripped-text") }) {
        return@runCatching null
    }
    CamposDelCorreo(plano)
}.getOrNull()

private fun direcciones(crudo: String?): List<String> =
    crudo?.let { texto -> direccionRegex.findAll(texto).map { it.value.trim().lowercase() }.toList() }.orEmpty()

/**
 * **A quién se le atribuye el correo: al token de la dirección a la que llegó.**
 *
 * Bancolombia le escribe a *él*, no a Movi. El camino real es un filtro de Gmail que reenvía sus
 * alertas a la dirección del proveedor — y un reenvío automático de Gmail **conserva el `From:`
 * original** (Bancolombia) y hasta el `To:` original (su correo personal): lo único que cambia es
 * el destinatario de SOBRE. Por eso mirar el remitente para saber de quién es el correo no
 * funcionaría ni un día: el remitente es el banco.
 *
 * El token es la parte que sigue al ÚLTIMO `+` de la parte local, o sea el sub-direccionamiento que
 * todos los proveedores soportan: sirve igual con una dirección propia
 * (`alertas+abc@midominio.com`) que con la dirección con hash que da Postmark
 * (`9f3c…+abc@inbound.postmarkapp.com`).
 *
 * Se recorren [CorreoEntrante.destinatarios] en orden y gana el primero que traiga token: el
 * destinatario de sobre va primero justo porque es el único que el reenvío no reescribe.
 *
 * Y si no hay token no hay a quién atribuirlo — la ruta contesta 202 y **no escribe nada**.
 * Adivinar el dueño sería meterle a alguien un movimiento ajeno en su bandeja de plata.
 */
fun tokenDelDestinatario(destinatarios: List<String>): String? =
    destinatarios.firstNotNullOfOrNull { direccion ->
        val local = direccion.substringBefore('@')
        if (!local.contains('+')) null
        else local.substringAfterLast('+').trim().takeIf { it.isNotBlank() }
    }

/**
 * **El token de un usuario**, derivado de su id y nada más.
 *
 * Determinista y sin columna nueva: no hay migración, no hay nada que sincronizar, y la dirección
 * de reenvío no se rompe si mañana se rota el secreto del webhook (que es otra cosa, y que sí se
 * rota). Un id de usuario no es público, así que el token tampoco se adivina — pero la dirección
 * **no** es la barrera de seguridad: la barrera es el secreto compartido que exige la ruta.
 */
fun tokenDeCorreoDe(userId: String): String = sha256Hex("movi-correo-entrante:$userId").take(16)

/**
 * La dirección a la que el dueño tiene que reenviar, armada sobre la que le dio el proveedor:
 * `9f3c@inbound.postmarkapp.com` + token → `9f3c+<token>@inbound.postmarkapp.com`.
 *
 * Si la base ya traía un `+…`, se reemplaza: así reconfigurar no acumula sub-direcciones.
 */
fun direccionDeReenvio(base: String, token: String): String? {
    val limpia = base.trim()
    val local = limpia.substringBefore('@', missingDelimiterValue = "")
    val dominio = limpia.substringAfter('@', missingDelimiterValue = "")
    if (local.isBlank() || dominio.isBlank()) return null
    return "${local.substringBefore('+')}+$token@$dominio"
}

/**
 * **De dónde vino esta fila de la bandeja.** Va al campo `bank`, el rótulo que «Mensajes del banco»
 * pinta arriba de cada mensaje: con los SMS ahí va el código del remitente («85540»), con las
 * notificaciones «Notificación · Bancolombia», y con esto «Correo · Bancolombia».
 *
 * El `bank` NO entra en la clave de dedupe del server (texto + tiempo, ver `SmsDedupe`), así que
 * marcar el origen acá no puede partir en dos un movimiento que ya llegó por SMS.
 */
fun marcaDeOrigenDelCorreo(nombre: String, remitente: String): String {
    val quien = nombre.trim().ifBlank { remitente.substringAfter('@').ifBlank { "correo" } }
    return "Correo · $quien".take(MAX_MARCA_DE_ORIGEN_DE_CORREO)
}

/**
 * Asunto + cuerpo en una sola línea de texto, que es la forma en que `parseSms` lee un mensaje.
 *
 * El asunto va adelante por el mismo motivo que el título de una notificación: media frase vive ahí
 * («Compra aprobada» / «por $21.640 en …»). Si el cuerpo ya arranca con el asunto no se repite —
 * eso solo le daría al dedupe por texto una cadena distinta para el mismo hecho.
 *
 * **No se toca el parser.** Una alerta por correo es más verbosa que un SMS y a veces no va a
 * parsear: esa fila igual entra a la bandeja como «por confirmar» sin propuesta, exactamente igual
 * que un SMS que no parsea, y él la confirma a mano. Perder el aviso sería peor.
 */
fun textoDelCorreo(asunto: String, cuerpo: String): String {
    val a = asunto.trim()
    val c = cuerpo.trim()
    val junto = when {
        a.isEmpty() -> c
        c.isEmpty() -> a
        c.startsWith(a, ignoreCase = true) -> c
        else -> "$a: $c"
    }
    return junto.take(MAX_TEXTO_DE_CORREO)
}

/** Una línea que abre una respuesta citada. Cortar acá tira la cita y deja lo que se escribió. */
private val aperturaDeCita = Regex(
    """^\s*(?:El\s.+\sescribi[oó]:|On\s.+\swrote:|-{2,}\s*Mensaje original\s*-{2,})\s*$""",
    RegexOption.IGNORE_CASE,
)

/**
 * **Limpieza conservadora**: se quitan las líneas citadas (`>`), la firma después de un `--` solo en
 * su línea, y todo lo que siga a una apertura de cita.
 *
 * Lo que **no** se toca, a propósito: el bloque `---------- Forwarded message ----------` que Gmail
 * pone cuando se reenvía A MANO. Cortar ahí borraría justo el contenido —la alerta viene DESPUÉS
 * del separador—, y ese es el modo en que el dueño va a probar esto antes de que el filtro
 * automático esté armado. Las cuatro líneas de cabecera que Gmail intercala no le hacen nada al
 * parser: busca un monto, y ahí no hay ninguno.
 */
fun limpiarCuerpoDelCorreo(crudo: String): String {
    val lineas = crudo.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val utiles = mutableListOf<String>()
    for (linea in lineas) {
        if (aperturaDeCita.matches(linea)) break
        if (linea.trimEnd() == "--") break          // firma (RFC 3676 §4.3: "-- ")
        if (linea.trimStart().startsWith(">")) continue
        utiles += linea
    }
    return utiles.joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()
}

/**
 * HTML a texto, **sin interpretarlo**: se tiran `<script>`/`<style>` enteros, las etiquetas se
 * reemplazan por un espacio y se deshacen seis entidades. No se renderiza, no se sigue un enlace,
 * no se baja una imagen. Solo se usa cuando el correo no trae parte de texto.
 */
internal fun sinEtiquetas(html: String): String =
    html.replace(Regex("""(?is)<(script|style)\b.*?</\1>"""), " ")
        .replace(Regex("""(?i)<br\s*/?>|</p>|</div>|</tr>"""), "\n")
        .replace(Regex("""(?s)<[^>]*>"""), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

private val FORMATO_DEL_WIRE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

/**
 * **Cuándo pasó**, en el mismo formato que escriben los otros dos capturadores
 * (`"yyyy-MM-dd HH:mm"`, ver `SmsSync.captureItem`) y en la zona de la app (Bogotá), no en UTC: el
 * server corre en Railway, que está en UTC, y una fila fechada cinco horas adelante caería en el
 * período equivocado y rompería el orden de la bandeja.
 *
 * Se aceptan la fecha RFC 1123 del `Date:` (Postmark, y la cabecera de cualquiera) y un epoch en
 * segundos (el `timestamp` de Mailgun). Una fecha ilegible cae a [ahora]: el mensaje llegó, y una
 * fila sin fecha ordenaría mal la bandeja entera.
 */
fun momentoDelCorreo(fecha: String?, ahora: Long, zone: ZoneId = AppClock.zone): String {
    val instante = fecha?.trim()?.takeIf { it.isNotBlank() }?.let { crudo ->
        crudo.toLongOrNull()?.let { segundos -> runCatching { Instant.ofEpochSecond(segundos) }.getOrNull() }
            ?: runCatching { ZonedDateTime.parse(crudo, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(crudo) }.getOrNull()
    } ?: Instant.ofEpochMilli(ahora)
    return instante.atZone(zone).format(FORMATO_DEL_WIRE)
}

/**
 * Id del wire. Prefijo propio, distinto de `sms_`, `sms_rt_` y `notif_`: el hook de push del server
 * está acotado a `sms_rt_` para que un backfill no dispare notificaciones, y una alerta por correo
 * tampoco debería (ya le llegó el correo). Ver `SmsRoutes`.
 *
 * La base es el `Message-Id` cuando vino —el proveedor puede reintentar el mismo webhook, y con el
 * mismo id el chequeo por id de la inserción lo vuelve idempotente— y el texto + el tiempo cuando
 * no.
 */
fun idDeCorreo(idDelMensaje: String?, texto: String, tiempo: String): String =
    "correo_" + sha256Hex(idDelMensaje?.takeIf { it.isNotBlank() } ?: "$texto|$tiempo").take(16)

internal fun sha256Hex(entrada: String): String =
    MessageDigest.getInstance("SHA-256").digest(entrada.toByteArray())
        .joinToString("") { "%02x".format(it) }
