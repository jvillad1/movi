package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.compartir.TokenDeEnlace
import com.jvillada.movi.server.correo.momentoDelCorreo
import com.jvillada.movi.server.db.AtajosDescartados
import com.jvillada.movi.server.db.CodigosDeCaptura
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.sms.SmsDedupeIndex
import com.jvillada.movi.server.sms.SmsKey
import com.jvillada.movi.server.sms.textoDelPagoConApplePay
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.CodigoDeCapturaNuevo
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.DIAS_DE_ATAJOS_DESCARTADOS
import com.jvillada.movi.shared.model.GrupoDeAtajosDescartados
import com.jvillada.movi.shared.model.MotivoDelAtajoDescartado
import com.jvillada.movi.shared.model.ResumenDeAtajosDescartados
import com.jvillada.movi.shared.model.ORIGEN_DEL_ATAJO
import com.jvillada.movi.shared.model.ORIGEN_DE_APPLE_PAY
import com.jvillada.movi.shared.model.ORIGEN_DE_NU_POR_ATAJO
import com.jvillada.movi.shared.model.esUnRemitentePersonal
import com.jvillada.movi.shared.model.notificacionDeNuDelAtajo
import com.jvillada.movi.shared.model.RUTA_VIEJA_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.RUTA_VIEJA_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.marcaDelRemitente
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/*
 * # Vuelta 111 · el iPhone le avisa a Movi con Atajos
 *
 * Dos partes:
 *
 * 1. **El código de captura** (`GET/POST/DELETE /api/captura/codigo`, autenticadas): un token largo y
 *    aleatorio que el dueño pega en sus atajos. No es la sesión: no lee nada, solo manda avisos, y se
 *    revoca sin cerrar la sesión. Se guarda solo su SHA-256 ([CodigosDeCaptura]).
 * 2. **`POST /api/captura/atajo`** (pública, la protege el código): lo que mandó el atajo. Un SMS pasa el
 *    filtro de bancos ([pasaElFiltroDeBancos]: desde la vuelta 113, `seGuardaElSmsDelAtajo` de `:core`, la
 *    misma regla del teléfono con el matiz del remitente que falta) y sigue el camino de los correos del
 *    banco —`sms_messages`, `SmsDedupe`, `queEsEsteMensaje`, «Por revisar»—; un pago con Apple Pay se
 *    guarda como un aviso con su texto legible ([textoDelPagoConApplePay]). Nada se anota solo. Vuelta 127:
 *    antes del filtro, una notificación de Nu (la automatización «Notificación» de la app Nu) se reconoce por
 *    su forma (`notificacionDeNuDelAtajo`) y se guarda como la sube un Android, con «Atajo · Notificación · Nu».
 *
 * **El código nunca va en una URL ni en un log**: viaja en `Authorization: Bearer <código>`
 * (`CallLogging` imprime la ruta y el status, no las cabeceras), y ninguna línea de log de este archivo
 * nombra el código, el texto, el remitente ni el monto.
 */

/** El cuerpo más grande que se acepta: un SMS o un pago son unos cientos de bytes. */
internal const val MAX_CUERPO_DEL_ATAJO: Int = 16_000

/**
 * **Cuántos avisos por minuto con un mismo código.** Un banco manda unos pocos SMS por hora; 30 por
 * minuto es holgado para un día de muchas compras y corta en seco a un atajo que se quedó en un bucle.
 * Elegido, no medido.
 */
internal const val MAX_AVISOS_POR_MINUTO_POR_CODIGO: Int = 30

/**
 * **Cuántos intentos FALLIDOS (sin código, o con uno que no sirve) por minuto desde una misma dirección.**
 * Revisión de la vuelta 111: un balde por IP que contara todos los pedidos y se revisara antes del código
 * era, detrás de Railway, un balde global (el peer TCP es el borde): diez pedidos por segundo sin cabecera
 * dejaban a todos los atajos en 429. Ahora un pedido con un código válido **no gasta de ningún balde
 * compartido** (su freno es [MAX_AVISOS_POR_MINUTO_POR_CODIGO]); este solo cuenta los fallidos, y pasado el
 * tope solo frena a los que no traen un código válido. Elegido, no medido.
 */
internal const val MAX_FALLIDOS_POR_MINUTO_POR_IP: Int = 60

/**
 * **La dirección del que pide, para el balde de los fallidos.** Railway pone delante su borde, que agrega la
 * dirección real del cliente al final de `X-Forwarded-For`: el valor **más a la derecha** es el que vio el
 * borde (los de la izquierda los puede escribir cualquiera). Solo para esta ruta, y solo para contar fallidos:
 * el server no instala `XForwardedHeaders` (ver `AuthRoutes.kt`) y nada más confía en esta cabecera. Sin
 * ella (las pruebas, un server sin borde), el peer TCP.
 */
internal fun ipDelPedido(call: ApplicationCall): String =
    call.request.headers["X-Forwarded-For"]?.split(',')?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
        ?: call.request.origin.remoteHost

private const val VENTANA_DEL_ATAJO_MS = 60_000L

/** El texto que se guarda tiene el mismo tope que el de un correo: es la clave del dedupe. */
private const val MAX_TEXTO_DEL_AVISO = 1_000

/** Lo que contesta la ruta pública: nunca el texto ni el código. */
@Serializable
internal data class RespuestaDelAtajo(
    val guardado: Boolean,
    val motivo: String? = null,
    val id: String? = null,
)


private val jsonDelAtajo = Json { ignoreUnknownKeys = true; isLenient = true }

/** El cuerpo del atajo, ya leído: todo texto (el iPhone puede mandar el monto como número). */
internal data class AvisoDelAtajo(
    val tipo: String,
    val texto: String? = null,
    val remitente: String? = null,
    val comercio: String? = null,
    val monto: String? = null,
    val tarjeta: String? = null,
)

/** Lee el JSON del atajo; `null` si no es un objeto o no trae `tipo`. Cada campo, como texto. */
internal fun leerElAvisoDelAtajo(crudo: String): AvisoDelAtajo? {
    val objeto = runCatching { jsonDelAtajo.parseToJsonElement(crudo) as? JsonObject }.getOrNull() ?: return null
    fun campo(nombre: String): String? = (objeto[nombre] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
    val tipo = campo("tipo")?.lowercase() ?: return null
    return AvisoDelAtajo(
        tipo = tipo,
        texto = campo("texto"),
        remitente = campo("remitente"),
        comercio = campo("comercio"),
        monto = campo("monto"),
        tarjeta = campo("tarjeta"),
    )
}

/** El código de `Authorization: Bearer <código>`, o `null` si no viene o no tiene la forma de uno. */
internal fun codigoPresentado(authorization: String?): String? {
    val valor = authorization?.trim() ?: return null
    if (!valor.startsWith("Bearer ", ignoreCase = true)) return null
    return valor.substring(7).trim().takeIf { TokenDeEnlace.tieneForma(it) }
}

/**
 * **El banco de un SMS que mandó un atajo**, para el rótulo «Atajo · Bancolombia»: el que dice el
 * remitente («85540», «Bancolombia») o el texto («DAVIVIENDA Descuento…»), con las reglas de siempre
 * ([marcaDelRemitente]). Si no se sabe, «tu banco»: el remitente crudo nunca se guarda (podría ser el
 * nombre de un contacto).
 */
internal fun origenDelSmsDelAtajo(remitente: String?, texto: String): String {
    val banco = marcaDelRemitente(remitente.orEmpty(), texto)?.nombre ?: "tu banco"
    return "$ORIGEN_DEL_ATAJO · $banco".take(100)
}

/** Id del aviso: el texto y el minuto en que llegó, como el dedupe. Prefijo propio (no dispara la push de `sms_rt_`). */
private fun idDelAtajo(uid: String, texto: String, tiempo: String): String =
    "atajo_" + MessageDigest.getInstance("SHA-256")
        .digest("$uid|$texto|$tiempo".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

/**
 * Los atajos firmados que el server sabe servir: el nombre pedido en la ruta → el archivo en los recursos del
 * JAR (`server/src/main/resources/atajos/`, los escribe `scripts/generar-atajos.py`), no en `static/`: esa
 * carpeta está en `.gitignore` porque ahí copia la web el armado.
 *
 * Vuelta 114 · el archivo se llama como el atajo («Movi - Avisar un mensaje.shortcut»), porque Atajos lo
 * instala con el nombre del archivo. La ruta vieja de la vuelta 111 (`movi-mensaje.shortcut`) sigue sirviendo
 * el mismo archivo, con el nombre nuevo.
 */
private val ATAJOS_QUE_SE_SIRVEN: Map<String, String> = mapOf(
    ARCHIVO_DEL_ATAJO_DE_MENSAJES to ARCHIVO_DEL_ATAJO_DE_MENSAJES,
    RUTA_VIEJA_DEL_ATAJO_DE_MENSAJES.substringAfterLast('/') to ARCHIVO_DEL_ATAJO_DE_MENSAJES,
    ARCHIVO_DEL_ATAJO_DE_APPLE_PAY to ARCHIVO_DEL_ATAJO_DE_APPLE_PAY,
    RUTA_VIEJA_DEL_ATAJO_DE_APPLE_PAY.substringAfterLast('/') to ARCHIVO_DEL_ATAJO_DE_APPLE_PAY,
)

private fun bytesDelAtajo(archivo: String): ByteArray? =
    CodigoDeCapturaRecursos::class.java.classLoader.getResourceAsStream("atajos/$archivo")?.use { it.readBytes() }

/** ¿Está en el server el atajo firmado [archivo]? */
private fun hayAtajoFirmado(archivo: String): Boolean =
    CodigoDeCapturaRecursos::class.java.classLoader.getResource("atajos/$archivo") != null

/**
 * La cabecera de la descarga: el nombre del atajo como nombre del archivo, en `filename` y en `filename*`
 * (RFC 6266). Los nombres son ASCII, así que los dos dicen lo mismo.
 */
internal fun contentDispositionDelAtajo(archivo: String): String =
    "attachment; filename=\"$archivo\"; filename*=UTF-8''${archivo.replace(" ", "%20")}"

private object CodigoDeCapturaRecursos

/** El código vigente de [uid] (sin el código, que no se guarda). */
internal suspend fun codigoDeCapturaDe(uid: String): CodigoDeCaptura {
    val fila = dbQuery {
        CodigosDeCaptura.selectAll()
            .where { (CodigosDeCaptura.userId eq uid) and CodigosDeCaptura.revocadoEn.isNull() }
            .maxByOrNull { it[CodigosDeCaptura.creadoEn] }
    }
    return CodigoDeCaptura(
        vigente = fila != null,
        creadoEn = fila?.get(CodigosDeCaptura.creadoEn),
        ultimoUso = fila?.get(CodigosDeCaptura.ultimoUso),
        atajoDeMensajes = hayAtajoFirmado(ARCHIVO_DEL_ATAJO_DE_MENSAJES),
        atajoDeApplePay = hayAtajoFirmado(ARCHIVO_DEL_ATAJO_DE_APPLE_PAY),
    )
}

/** Crea un código nuevo para [uid] y revoca el anterior, en una transacción con su fila de `users` bloqueada. Devuelve el código en claro. */
internal suspend fun crearCodigoDeCaptura(uid: String, ahora: Long): CodigoDeCapturaNuevo {
    val codigo = TokenDeEnlace.nuevo()
    dbQuery {
        // Revisión de la vuelta 111: dos «Crear» a la vez dejaban dos códigos vigentes (cada uno revocaba lo
        // que veía y los dos insertaban). Con la fila del usuario bloqueada, el segundo espera al primero y
        // revoca el que este acaba de crear.
        Users.select(Users.id).where { Users.id eq uid }.forUpdate().firstOrNull()
        CodigosDeCaptura.update({ (CodigosDeCaptura.userId eq uid) and CodigosDeCaptura.revocadoEn.isNull() }) {
            it[revocadoEn] = ahora
        }
        CodigosDeCaptura.insert {
            it[id] = UUID.randomUUID().toString()
            it[userId] = uid
            it[tokenHash] = TokenDeEnlace.hashDelToken(codigo)
            it[creadoEn] = ahora
        }
    }
    return CodigoDeCapturaNuevo(codigo = codigo, creadoEn = ahora)
}

/** Revoca el código vigente de [uid]. Devuelve cuántos revocó (0 o 1). */
internal suspend fun revocarCodigoDeCaptura(uid: String, ahora: Long): Int = dbQuery {
    CodigosDeCaptura.update({ (CodigosDeCaptura.userId eq uid) and CodigosDeCaptura.revocadoEn.isNull() }) {
        it[revocadoEn] = ahora
    }
}

/** De quién es [codigo] si está vigente; `null` si no existe o fue revocado. */
private suspend fun duenoDelCodigo(codigo: String): Pair<String, String>? {
    val hash = TokenDeEnlace.hashDelToken(codigo)
    return dbQuery {
        CodigosDeCaptura.selectAll()
            .where { (CodigosDeCaptura.tokenHash eq hash) and CodigosDeCaptura.revocadoEn.isNull() }
            .firstOrNull()
            ?.takeIf { TokenDeEnlace.esElMismoHash(it[CodigosDeCaptura.tokenHash], hash) }
            ?.let { it[CodigosDeCaptura.id] to it[CodigosDeCaptura.userId] }
    }
}

/**
 * **Cuenta lo que no se guardó**: una fila por dueño, día y motivo, sin texto. Nunca falla hacia afuera:
 * contar no puede tumbar la respuesta del atajo.
 */
internal suspend fun contarAtajoDescartado(uid: String, motivo: MotivoDelAtajoDescartado, ahora: Long) {
    runCatching {
        val dia = Instant.ofEpochMilli(ahora).atZone(AppClock.zone).toLocalDate().toString()
        dbQuery {
            val donde = (AtajosDescartados.userId eq uid) and (AtajosDescartados.dia eq dia) and (AtajosDescartados.motivo eq motivo.name)
            val actual = AtajosDescartados.selectAll().where { donde }.firstOrNull()?.get(AtajosDescartados.cuantos)
            if (actual == null) {
                AtajosDescartados.insert {
                    it[userId] = uid
                    it[AtajosDescartados.dia] = dia
                    it[AtajosDescartados.motivo] = motivo.name
                    it[cuantos] = 1
                }
            } else {
                AtajosDescartados.update({ donde }) { it[cuantos] = actual + 1 }
            }
        }
    }
}

/**
 * **Lo que los atajos de [uid] mandaron y no se guardó** en los últimos [DIAS_DE_ATAJOS_DESCARTADOS] días
 * (hoy incluido), sumado por motivo. Solo los propios: la tabla no tiene filas de nadie.
 */
internal suspend fun atajosDescartadosDe(uid: String, ahora: Long): ResumenDeAtajosDescartados {
    val hoy = Instant.ofEpochMilli(ahora).atZone(AppClock.zone).toLocalDate()
    val desde = hoy.minusDays(DIAS_DE_ATAJOS_DESCARTADOS - 1L).toString()
    val grupos = dbQuery {
        AtajosDescartados.selectAll()
            .where { (AtajosDescartados.userId eq uid) and (AtajosDescartados.dia greaterEq desde) }
            .groupBy({ it[AtajosDescartados.motivo] }, { it[AtajosDescartados.cuantos] })
            .map { (motivo, cuantos) -> GrupoDeAtajosDescartados(motivo, cuantos.sum()) }
            .sortedBy { it.motivo }
    }
    return ResumenDeAtajosDescartados(grupos = grupos)
}

/**
 * De qué clase era lo que mandó el atajo, para el tracking: un SMS (`AVISO`), un pago con Apple Pay
 * (`GASTO`) o —vuelta 127— una notificación de Nu (`NOTIFICACION`).
 */
private enum class ClaseDeLaCaptura { AVISO, GASTO, NOTIFICACION }

/** El evento del tracking del server: qué pasó y de qué clase era. Sin el texto. */
private suspend fun registrarLaCaptura(uid: String, estado: String, clase: ClaseDeLaCaptura, avisoId: String?, ahora: Long) {
    val dia = Instant.ofEpochMilli(ahora).atZone(AppClock.zone).toLocalDate().toString()
    val id = Observabilidad.idEstable("atajo", avisoId ?: "$estado:$dia")
    Observabilidad.registrarSistema(
        uid, id, EventoDeUso.CAPTURA_POR_ATAJO,
        buildMap {
            put(ClaveDeUso.ORIGEN, "ATAJO")
            put(ClaveDeUso.ESTADO, estado)
            put(ClaveDeUso.CLASE, clase.name)
            if (avisoId != null && ClaveDeUso.AVISO_ID.admite(avisoId)) put(ClaveDeUso.AVISO_ID, avisoId)
        },
        ahora,
    )
}

/**
 * **Guarda lo que mandó un atajo** en la bandeja de [uid], por el camino de los correos: el dedupe
 * (texto + tiempo) y `comoLlega` (lo que no es un movimiento entra apartado). Devuelve el id y si se
 * guardó (`false` = ya estaba).
 */
private suspend fun guardarElAvisoDelAtajo(uid: String, texto: String, origen: String, ahora: Long): Pair<String, Boolean> {
    val tiempo = momentoDelCorreo(null, ahora = ahora)
    val id = idDelAtajo(uid, texto, tiempo)
    val (estado, motivo) = comoLlega(texto, origen)
    val guardado = dbQuery {
        val ya = SmsMessages.selectAll().where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }.any()
        if (ya) return@dbQuery false
        val existentes = SmsMessages.selectAll()
            .where { SmsMessages.userId eq uid }
            .map { SmsKey(it[SmsMessages.text], it[SmsMessages.time]) }
        if (SmsDedupeIndex(existentes).isDuplicate(SmsKey(texto, tiempo))) return@dbQuery false
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = tiempo
            it[bank] = origen
            it[text] = texto
            it[state] = estado
            it[motivoApartado] = motivo?.name
            it[det] = ""
        }
        true
    }
    if (guardado) {
        Observabilidad.registrarAvisosCapturados(uid, listOf(Observabilidad.AvisoQueLlego(id, origen, apartado = motivo != null)), ahora)
    }
    return id to guardado
}

/**
 * # `POST /api/captura/atajo`
 *
 * | Caso | Código |
 * |---|---|
 * | cuerpo de más de [MAX_CUERPO_DEL_ATAJO] bytes | 413 |
 * | sin `Authorization: Bearer <código>`, o con un código que no existe o fue revocado | 401 |
 * | lo de arriba, más de [MAX_FALLIDOS_POR_MINUTO_POR_IP] veces desde la misma dirección ([ipDelPedido]) | 429 |
 * | más de [MAX_AVISOS_POR_MINUTO_POR_CODIGO] con el mismo código | 429 |
 * | no es JSON, o no trae `tipo` (`mensaje` o `transaccion`) | 400 |
 * | una notificación de Nu (vuelta 127, `notificacionDeNuDelAtajo`) | 202, guardada como «Atajo · Notificación · Nu» |
 * | un mensaje que no pasa el filtro de bancos, o que llegó vacío | 202, **no se guarda**: solo se cuenta |
 * | ya estaba (el mismo aviso dos veces) | 202 |
 * | guardado | 202 |
 */
fun Route.capturaPorAtajoRoutes(ahora: () -> Long = System::currentTimeMillis) {
    post("/api/captura/atajo") { atajoRoute(call, ahora()) }
}

private suspend fun atajoRoute(call: ApplicationCall, ahora: Long) {
    call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
        if (it > MAX_CUERPO_DEL_ATAJO) {
            return call.respond(HttpStatusCode.PayloadTooLarge, RespuestaDelAtajo(false, "El aviso es demasiado grande."))
        }
    }
    val codigo = codigoPresentado(call.request.headers[HttpHeaders.Authorization])
    val (codigoId, uid) = codigo?.let { duenoDelCodigo(it) } ?: run {
        // Sin decir nada de lo que llegó: ni el código ni el cuerpo.
        if (codigo != null) call.application.log.warn("captura por atajo rechazada: código inexistente o revocado")
        return rechazarSinCodigo(call, if (codigo == null) "Falta el código de Movi." else "Ese código de Movi no sirve. Crea uno nuevo en la app.")
    }
    if (!RateLimiter.allow("captura-atajo:codigo:$codigoId", MAX_AVISOS_POR_MINUTO_POR_CODIGO, VENTANA_DEL_ATAJO_MS)) {
        // Un atajo en bucle: se cuenta (sin el texto) para que «Desde tu iPhone» lo diga.
        contarAtajoDescartado(uid, MotivoDelAtajoDescartado.DEMASIADOS, ahora)
        return call.respond(HttpStatusCode.TooManyRequests, RespuestaDelAtajo(false, "Demasiados avisos seguidos."))
    }
    val crudo = when (val leido = call.leerConTope(MAX_CUERPO_DEL_ATAJO)) {
        CuerpoLeido.Grande -> return call.respond(HttpStatusCode.PayloadTooLarge, RespuestaDelAtajo(false, "El aviso es demasiado grande."))
        CuerpoLeido.Ilegible -> return call.respond(HttpStatusCode.BadRequest, RespuestaDelAtajo(false, "No pude leer el aviso."))
        is CuerpoLeido.Texto -> leido.texto
    }
    val aviso = leerElAvisoDelAtajo(crudo)
        ?: return call.respond(HttpStatusCode.BadRequest, RespuestaDelAtajo(false, "El aviso no es un JSON con «tipo»."))

    // El código sirvió: se anota su último uso (lo dice la app: «último aviso hace 2 h»).
    runCatching { dbQuery { CodigosDeCaptura.update({ CodigosDeCaptura.id eq codigoId }) { it[ultimoUso] = ahora } } }
    Observabilidad.registrarCapturaActiva(uid, "ATAJO", ahora)

    val (estado, respuesta) = when (aviso.tipo) {
        "mensaje", "sms" -> guardarElMensaje(uid, aviso, ahora)
        "transaccion", "transacción", "pago", "applepay" -> guardarElPago(uid, aviso, ahora)
        else -> HttpStatusCode.BadRequest to RespuestaDelAtajo(false, "El tipo tiene que ser «mensaje» o «transaccion».")
    }
    call.respond(estado, respuesta)
}

/**
 * Un pedido sin un código válido: 401, y cuenta en el balde de fallidos de su dirección. Pasado el tope, 429
 * — solo para estos: un pedido con código válido nunca pasa por acá.
 */
private suspend fun rechazarSinCodigo(call: ApplicationCall, motivo: String) {
    val ip = ipDelPedido(call)
    if (!RateLimiter.allow("captura-atajo:fallidos:$ip", MAX_FALLIDOS_POR_MINUTO_POR_IP, VENTANA_DEL_ATAJO_MS)) {
        return call.respond(HttpStatusCode.TooManyRequests, RespuestaDelAtajo(false, "Demasiados intentos sin un código válido."))
    }
    call.respond(HttpStatusCode.Unauthorized, RespuestaDelAtajo(false, motivo))
}

private suspend fun guardarElMensaje(uid: String, aviso: AvisoDelAtajo, ahora: Long): Pair<HttpStatusCode, RespuestaDelAtajo> {
    val texto = aviso.texto?.take(MAX_TEXTO_DEL_AVISO)
    if (texto == null) {
        contarAtajoDescartado(uid, MotivoDelAtajoDescartado.VACIO, ahora)
        registrarLaCaptura(uid, "VACIO", ClaseDeLaCaptura.AVISO, avisoId = null, ahora)
        return HttpStatusCode.Accepted to RespuestaDelAtajo(false, "El mensaje llegó vacío.")
    }
    // **Vuelta 127 · una notificación de Nu**, antes del filtro de bancos: la automatización «Notificación»
    // de la app Nu le pasa al atajo lo que publicó Nu, que no empieza por un banco (es la forma de un SMS).
    // Se reconoce por su forma (`notificacionDeNuDelAtajo`, en `:core`) y se guarda con el texto que sube
    // un Android y el rótulo «Atajo · Notificación · Nu»: el parser, el mismo pago y el dedupe la leen como
    // la de un Android. Un remitente personal nunca: una notificación no trae remitente.
    if (!esUnRemitentePersonal(aviso.remitente)) {
        notificacionDeNuDelAtajo(texto)?.let { notificacion ->
            val (id, guardado) = guardarElAvisoDelAtajo(uid, notificacion, ORIGEN_DE_NU_POR_ATAJO, ahora)
            registrarLaCaptura(uid, if (guardado) "GUARDADO" else "YA_ESTABA", ClaseDeLaCaptura.NOTIFICACION, avisoId = id, ahora)
            return HttpStatusCode.Accepted to
                if (guardado) RespuestaDelAtajo(true, id = id) else RespuestaDelAtajo(false, "Esa notificación ya estaba en la bandeja.")
        }
    }
    // **La regla del teléfono, en `:core`.** Lo que no es de un banco (o viene de un número personal) no se
    // guarda ni se lee más: solo se cuenta, con su motivo (vuelta 114: lo que nombra un banco sin la forma de
    // un aviso, una promoción o una clave, va aparte de lo que no es de un banco).
    porQueNoPasaElFiltroDeBancos(aviso.remitente, texto)?.let { motivo ->
        contarAtajoDescartado(uid, motivo, ahora)
        registrarLaCaptura(uid, motivo.name, ClaseDeLaCaptura.AVISO, avisoId = null, ahora)
        val porQue = if (motivo == MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO) {
            "No tiene la forma de un aviso del banco: no se guardó."
        } else {
            "No es un mensaje de un banco: no se guardó."
        }
        return HttpStatusCode.Accepted to RespuestaDelAtajo(false, porQue)
    }
    val (id, guardado) = guardarElAvisoDelAtajo(uid, texto, origenDelSmsDelAtajo(aviso.remitente, texto), ahora)
    registrarLaCaptura(uid, if (guardado) "GUARDADO" else "YA_ESTABA", ClaseDeLaCaptura.AVISO, avisoId = id, ahora)
    return HttpStatusCode.Accepted to
        if (guardado) RespuestaDelAtajo(true, id = id) else RespuestaDelAtajo(false, "Ese mensaje ya estaba en la bandeja.")
}

private suspend fun guardarElPago(uid: String, aviso: AvisoDelAtajo, ahora: Long): Pair<HttpStatusCode, RespuestaDelAtajo> {
    val texto = textoDelPagoConApplePay(aviso.comercio, aviso.monto, aviso.tarjeta, aviso.texto)
    if (texto == null) {
        contarAtajoDescartado(uid, MotivoDelAtajoDescartado.VACIO, ahora)
        registrarLaCaptura(uid, "VACIO", ClaseDeLaCaptura.GASTO, avisoId = null, ahora)
        return HttpStatusCode.Accepted to RespuestaDelAtajo(false, "El pago llegó vacío.")
    }
    val (id, guardado) = guardarElAvisoDelAtajo(uid, texto, ORIGEN_DE_APPLE_PAY, ahora)
    registrarLaCaptura(uid, if (guardado) "GUARDADO" else "YA_ESTABA", ClaseDeLaCaptura.GASTO, avisoId = id, ahora)
    return HttpStatusCode.Accepted to
        if (guardado) RespuestaDelAtajo(true, id = id) else RespuestaDelAtajo(false, "Ese pago ya estaba en la bandeja.")
}

/**
 * `GET /atajos/{archivo}`: el atajo firmado, para instalarlo con un toque desde el iPhone. Pública: el
 * atajo no trae nada de nadie (el código se pega al importarlo). Solo los nombres de
 * [ATAJOS_QUE_SE_SIRVEN]; cualquier otro, 404.
 */
fun Route.atajosRoutes() {
    get("/atajos/{archivo}") {
        // Ktor ya decodifica el segmento: «Movi%20-%20Avisar…» llega con espacios.
        val archivo = ATAJOS_QUE_SE_SIRVEN[call.parameters["archivo"].orEmpty()]
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val bytes = bytesDelAtajo(archivo) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.headers.append(HttpHeaders.ContentDisposition, contentDispositionDelAtajo(archivo))
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }
}

/** `GET/POST/DELETE /api/captura/codigo` y `GET /api/captura/descartados`: autenticadas. El código se ve una sola vez, en el POST. */
fun Route.codigoDeCapturaRoutes(ahora: () -> Long = System::currentTimeMillis) {
    get("/api/captura/codigo") { call.respond(codigoDeCapturaDe(call.userId())) }
    post("/api/captura/codigo") {
        call.respond(HttpStatusCode.Created, crearCodigoDeCaptura(call.userId(), ahora()))
    }
    get("/api/captura/descartados") { call.respond(atajosDescartadosDe(call.userId(), ahora())) }
    delete("/api/captura/codigo") {
        revocarCodigoDeCaptura(call.userId(), ahora())
        call.respond(HttpStatusCode.NoContent)
    }
}
