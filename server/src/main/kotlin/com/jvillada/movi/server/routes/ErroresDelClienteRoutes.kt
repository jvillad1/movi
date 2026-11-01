package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * **«Algo falló» deja rastro** (vuelta 96).
 *
 * El 1-nov-2026 una cuenta nueva no podía entrar desde un iPhone: la web cargaba y mostraba el aviso
 * «Algo falló» de `index.html`, y no había forma de saber qué excepción lo disparaba. Ni el server
 * (todo daba 200) ni la persona, que solo veía un botón de «Recargar».
 *
 * Ahora ese aviso manda el primer error a esta ruta, que lo escribe en el log del server (Railway) en
 * **una sola línea**, recortada, y no guarda nada más. Es pública a propósito: la app puede caerse
 * antes de tener sesión, y un `401` acá borraría justo el caso que más importa. El freno es el
 * limitador por IP; el cuerpo no se cree nunca más de [MAX_MENSAJE] caracteres.
 *
 * Lo que llega: el texto del error, el navegador y el id del usuario si lo había (un id, no un
 * secreto). Nunca el token.
 */
@Serializable
data class ErrorDelCliente(
    val mensaje: String = "",
    val navegador: String = "",
    val usuario: String? = null,
)

internal const val MAX_MENSAJE = 1_000
private const val MAX_NAVEGADOR = 200
private const val MAX_USUARIO = 64
private const val MAX_CUERPO = 8_000
private const val REPORTES_POR_VENTANA = 20
private const val VENTANA_MS = 10 * 60_000L

private val log = LoggerFactory.getLogger("ErroresDelCliente")
private val json = Json { ignoreUnknownKeys = true }

/** La línea del log: un solo renglón (nada de saltos que finjan otra entrada del log) y con tope. */
internal fun lineaDelErrorDelCliente(e: ErrorDelCliente): String {
    fun limpio(s: String?, max: Int) = (s ?: "").replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(max)
    return "Error del cliente web · usuario=${limpio(e.usuario, MAX_USUARIO).ifEmpty { "-" }}" +
        " · navegador=${limpio(e.navegador, MAX_NAVEGADOR)} · ${limpio(e.mensaje, MAX_MENSAJE)}"
}

fun Route.erroresDelClienteRoutes() {
    post("/api/errores-del-cliente") {
        val ip = call.request.origin.remoteHost
        if (!RateLimiter.allow("errores-del-cliente:$ip", REPORTES_POR_VENTANA, VENTANA_MS)) {
            return@post call.respond(HttpStatusCode.TooManyRequests)
        }
        // `sendBeacon` manda el cuerpo con el tipo que tenga el Blob; se lee como texto y se
        // interpreta acá, así no depende del Content-Type que el navegador haya puesto.
        val crudo = runCatching { call.receiveText() }.getOrNull()?.take(MAX_CUERPO)
        val error = crudo?.let { runCatching { json.decodeFromString(ErrorDelCliente.serializer(), it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        log.warn(lineaDelErrorDelCliente(error))
        call.respond(HttpStatusCode.NoContent)
    }
}
