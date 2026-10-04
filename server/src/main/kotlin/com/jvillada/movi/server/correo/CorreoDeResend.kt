package com.jvillada.movi.server.correo

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory

/**
 * # El correo entrante por Resend
 *
 * Postmark y Mailgun postean el correo entero; **Resend no**. Su webhook `email.received`
 * (https://resend.com/docs/webhooks/emails/received) trae solo metadatos —`email_id`, `from`, `to`,
 * `cc`, `bcc`, `received_for`, `subject`, `message_id`— y el cuerpo hay que pedirlo aparte con
 * `GET https://api.resend.com/emails/receiving/{email_id}`
 * (https://resend.com/docs/api-reference/emails/retrieve-received-email), que devuelve además
 * `text`, `html` y `headers`.
 *
 * Este archivo hace solo esas dos lecturas y arma con ellas el MISMO [CorreoEntrante] que arma
 * [leerCorreoEntrante] para Postmark. De ahí en adelante el camino es uno solo: el de
 * `guardarCorreoEntrante` en `CorreoEntranteRoutes.kt`.
 *
 * ## A quién se le atribuye (lo que decide si sirve con un reenvío de Gmail)
 *
 * Un reenvío automático de Gmail **conserva el `To:` del banco** (el Gmail del dueño): el `to` del
 * correo recibido, si es la cabecera, no trae la dirección de Movi. Por eso los destinatarios van
 * en este orden de confianza (ver [destinatariosDeResend]):
 *
 * 1. `received_for` — Resend lo documenta como «las direcciones para las que se reenvió el
 *    correo, tomadas de la cláusula `for` de las cabeceras `Received`»: el destinatario de SOBRE
 *    con que Gmail entregó el reenvío (`alertas+<token>@…resend.app`). Es el equivalente del
 *    `OriginalRecipient` de Postmark.
 * 2. Las cabeceras que agregan los que reenvían: `X-Forwarded-To` (Gmail la pone en el reenvío
 *    automático con la dirección de destino), `X-Original-To`, `Delivered-To`, `Envelope-To`.
 * 3. `to`, `cc` y `bcc` — sirven cuando el correo llega directo (la confirmación de reenvío de
 *    Gmail se manda directo a la dirección de Movi) o reenviado a mano.
 *
 * Y la ruta prueba los tokens de todos, no solo el primero (ver `tokensDeLosDestinatarios`).
 */

/** El evento del webhook, lo poco que se necesita de él. */
data class EventoDeResend(
    val tipo: String,
    /** El id del correo recibido; `null` si no vino o no tiene forma de id. */
    val idDelCorreo: String?,
    /** `received_for`, `to`, `cc` y `bcc` del evento, por si el contenido no los trajera. */
    val destinatarios: List<String>,
)

const val EVENTO_CORREO_RECIBIDO = "email.received"

/** Los ids de Resend son UUID; esto solo impide que un id raro se meta en la ruta de la API. */
private val formaDeId = Regex("""^[A-Za-z0-9-]{1,100}$""")

private val jsonDeResend = Json { isLenient = true; ignoreUnknownKeys = true }

/** Lee el cuerpo del webhook. `null` si no es un objeto JSON con `type`. */
fun leerEventoDeResend(crudo: String): EventoDeResend? = runCatching {
    val raiz = jsonDeResend.parseToJsonElement(crudo) as? JsonObject ?: return@runCatching null
    val tipo = (raiz["type"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
    val datos = raiz["data"] as? JsonObject
    val id = (datos?.get("email_id") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { formaDeId.matches(it) }
    EventoDeResend(
        tipo = tipo,
        idDelCorreo = id,
        destinatarios = datos?.let { d ->
            listOf("received_for", "to", "cc", "bcc").flatMap { direccionesDe(d[it]) }
        }.orEmpty().distinct(),
    )
}.getOrNull()

/** Cabeceras que un reenvío agrega con la dirección a la que de verdad se entregó. */
private val CABECERAS_DE_DESTINO = listOf("x-forwarded-to", "x-original-to", "delivered-to", "envelope-to")

/**
 * **El correo recibido como [CorreoEntrante]**, a partir de la respuesta de la API de Resend y del
 * evento que la pidió. `null` si no trae nada de texto (ni asunto ni cuerpo).
 */
fun correoDeResend(contenido: String, evento: EventoDeResend): CorreoEntrante? = runCatching {
    val raiz = jsonDeResend.parseToJsonElement(contenido) as? JsonObject ?: return@runCatching null
    val cabeceras = cabecerasDe(raiz["headers"])

    val fromCabecera = cabeceras["from"].orEmpty()
    val remitente = (texto(raiz["from"])?.let { direccionRegex.find(it)?.value }
        ?: direccionRegex.find(fromCabecera)?.value).orEmpty().trim().lowercase()
    val nombre = nombreRegex.find(fromCabecera)?.groupValues?.get(1)?.trim().orEmpty()

    val asunto = (texto(raiz["subject"]) ?: cabeceras["subject"]).orEmpty().trim()
    val cuerpoCrudo = texto(raiz["text"])?.takeIf { it.isNotBlank() }
        ?: texto(raiz["html"])?.let { sinEtiquetas(it) }
        ?: ""
    val cuerpo = limpiarCuerpoDelCorreo(cuerpoCrudo)
    if (asunto.isBlank() && cuerpo.isBlank()) return@runCatching null

    // La fecha del banco (`Date:`) si se entiende; si no, cuándo la recibió Resend.
    val fecha = cabeceras["date"]?.takeIf { instanteDelCorreo(it) != null }
        ?: texto(raiz["created_at"])

    CorreoEntrante(
        remitente = remitente,
        nombreDelRemitente = nombre,
        destinatarios = destinatariosDeResend(raiz, cabeceras, evento),
        asunto = asunto,
        cuerpo = cuerpo,
        fecha = fecha,
        idDelMensaje = texto(raiz["message_id"]) ?: cabeceras["message-id"],
    )
}.getOrNull()

/** El orden de confianza del KDoc del archivo: sobre, cabeceras de reenvío, y después `to`/`cc`/`bcc`. */
private fun destinatariosDeResend(
    raiz: JsonObject,
    cabeceras: Map<String, String>,
    evento: EventoDeResend,
): List<String> = buildList {
    addAll(direccionesDe(raiz["received_for"]))
    CABECERAS_DE_DESTINO.forEach { addAll(direcciones(cabeceras[it])) }
    listOf("to", "cc", "bcc").forEach { addAll(direccionesDe(raiz[it])) }
    addAll(evento.destinatarios)
}.distinct()

/** Un campo que puede venir como arreglo de direcciones o como un string con varias. */
private fun direccionesDe(elemento: JsonElement?): List<String> = when (elemento) {
    is JsonArray -> elemento.flatMap { direcciones(texto(it)) }
    is JsonPrimitive -> direcciones(elemento.contentOrNull)
    else -> emptyList()
}

/**
 * Las cabeceras con el nombre en minúsculas. Resend las da como objeto (`{"from": "…"}`); una
 * cabecera repetida puede venir como arreglo, y se acepta también la forma `[{name, value}]`.
 */
private fun cabecerasDe(elemento: JsonElement?): Map<String, String> {
    val plano = LinkedHashMap<String, String>()
    when (elemento) {
        is JsonObject -> elemento.forEach { (clave, valor) ->
            val v = when (valor) {
                is JsonArray -> valor.mapNotNull { texto(it) }.joinToString(", ")
                else -> texto(valor)
            }
            if (!v.isNullOrBlank()) plano.putIfAbsent(clave.lowercase(), v)
        }
        is JsonArray -> elemento.mapNotNull { it as? JsonObject }.forEach { h ->
            val nombre = texto(h["name"]) ?: return@forEach
            val v = texto(h["value"]) ?: return@forEach
            plano.putIfAbsent(nombre.lowercase(), v)
        }
        else -> Unit
    }
    return plano
}

private fun texto(elemento: JsonElement?): String? = (elemento as? JsonPrimitive)?.contentOrNull

// ── La API de Resend ─────────────────────────────────────────────────────────────────────────

/** Lo que contestó la API al pedir un correo recibido. */
sealed interface ContenidoDeResend {
    data class Encontrado(val json: String) : ContenidoDeResend
    /** 404: Resend no tiene ese id. */
    data object NoEncontrado : ContenidoDeResend
    /** No hay clave de API en el server. */
    data object SinClave : ContenidoDeResend
    /** La respuesta pasa de [MAX_CONTENIDO_DE_RESEND_BYTES]. */
    data object DemasiadoGrande : ContenidoDeResend
    /** Cualquier otra cosa: otro código (401/403 = clave sin permiso de lectura), red, timeout. */
    data class Fallo(val estado: Int?) : ContenidoDeResend
}

/**
 * **Tope de la respuesta de la API.** Se pide con `html_format=cid` para que las imágenes en línea
 * no vengan pegadas en base64 dentro del `html`; con eso una alerta bancaria pesa unos KB y 2 MB
 * es de sobra.
 */
internal const val MAX_CONTENIDO_DE_RESEND_BYTES = 2 * 1024 * 1024

/**
 * Quién trae el contenido de un correo recibido. Una interfaz por la misma razón que
 * `LectorDePapeles`: **ninguna prueba puede llamar a Resend**. [actual] es la costura — el server
 * usa [LectorDeResendPorHttp]; una prueba pone uno falso y lo devuelve en su `@AfterTest`.
 */
fun interface LectorDeCorreosRecibidos {
    suspend fun traer(idDelCorreo: String): ContenidoDeResend

    companion object {
        @Volatile
        var actual: LectorDeCorreosRecibidos = LectorDeResendPorHttp
    }
}

/** El de verdad: `java.net.http`, como `ResendClient` de los recordatorios. Nunca lanza. */
object LectorDeResendPorHttp : LectorDeCorreosRecibidos {

    private val log = LoggerFactory.getLogger(LectorDeResendPorHttp::class.java)

    private val cliente: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    }

    override suspend fun traer(idDelCorreo: String): ContenidoDeResend {
        val clave = ConfigDeCorreoEntrante.claveDeLaApiDeResend() ?: return ContenidoDeResend.SinClave
        if (!formaDeId.matches(idDelCorreo)) return ContenidoDeResend.NoEncontrado
        return try {
            val pedido = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails/receiving/$idDelCorreo?html_format=cid"))
                // Resend contesta 15 s antes de dar el webhook por caído: hay que dejarle margen.
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $clave")
                .header("Accept", "application/json")
                // La API rechaza con 403 cualquier petición sin User-Agent.
                .header("User-Agent", "movi-server/1.0")
                .GET()
                .build()
            val respuesta = withContext(Dispatchers.IO) {
                cliente.send(pedido, HttpResponse.BodyHandlers.ofInputStream())
            }
            respuesta.body().use { cuerpo ->
                when (val estado = respuesta.statusCode()) {
                    in 200..299 -> leerConTope(cuerpo)?.let { ContenidoDeResend.Encontrado(it) }
                        ?: ContenidoDeResend.DemasiadoGrande
                    404 -> ContenidoDeResend.NoEncontrado
                    else -> {
                        // Solo el código: el cuerpo de un error de Resend puede repetir datos del correo.
                        log.warn(
                            "correo entrante (Resend): la API contestó $estado al pedir el contenido" +
                                if (estado == 401 || estado == 403) " — ¿la clave es de solo envío?" else "",
                        )
                        ContenidoDeResend.Fallo(estado)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("correo entrante (Resend): no pude pedir el contenido: ${e.javaClass.simpleName}")
            ContenidoDeResend.Fallo(null)
        }
    }

    private fun leerConTope(cuerpo: InputStream): String? {
        val bytes = cuerpo.readNBytes(MAX_CONTENIDO_DE_RESEND_BYTES + 1)
        if (bytes.size > MAX_CONTENIDO_DE_RESEND_BYTES) return null
        return String(bytes, Charsets.UTF_8)
    }
}
