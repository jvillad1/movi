package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.LoteDeUso
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

/**
 * # Fallos y uso (vuelta 100)
 *
 * - `POST /api/fallos` — **pública**: la app puede caerse antes de tener sesión, y un 401 borraría
 *   justo el caso que más importa (el teléfono del papá con la app cerrada). El token es opcional y
 *   solo sirve para atribuir el fallo: uno vencido o ajeno no rechaza el reporte, lo deja sin dueño.
 *   La frenan el límite por IP y el tamaño máximo del cuerpo.
 * - `POST /api/uso` — autenticada, en lotes de a lo sumo `MAX_EVENTOS_POR_LOTE`.
 * - `GET /api/admin/fallos`, `GET /api/admin/uso` y el visor del recorrido (`/api/admin/uso/usuarios`,
 *   `…/usuarios/{id}/sesiones`, `…/sesiones/{sesionId}/eventos`) — solo un admin (`ADMIN_USER_IDS`);
 *   cualquier otro, 403.
 *
 * Ver [Observabilidad] para lo que se guarda y cómo se cuenta.
 */

internal const val MAX_CUERPO_DEL_FALLO = 16_000
internal const val MAX_CUERPO_DEL_USO = 96_000
internal const val FALLOS_POR_VENTANA = 30
internal const val LOTES_DE_USO_POR_VENTANA = 120
private const val VENTANA_DE_OBSERVABILIDAD_MS = 10 * 60_000L

private val jsonDeObservabilidad = Json { ignoreUnknownKeys = true }

/** El usuario que dice un `Authorization: Bearer …` válido de Movi, o `null`. Nunca lanza. */
internal fun usuarioDelTokenDeMovi(encabezado: String?): String? = runCatching {
    val token = encabezado?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    JwtConfig.verifier().verify(token).getClaim("userId").asString()
}.getOrNull()

/** La ruta pública de los fallos. [usuarioDelToken] es inyectable para las pruebas (otro secreto). */
fun Route.fallosRoutes(
    usuarioDelToken: (String?) -> String? = ::usuarioDelTokenDeMovi,
    ahora: () -> Long = System::currentTimeMillis,
) {
    post("/api/fallos") {
        val ip = call.request.origin.remoteHost
        if (!RateLimiter.allow("fallos:$ip", FALLOS_POR_VENTANA, VENTANA_DE_OBSERVABILIDAD_MS)) {
            return@post call.respond(HttpStatusCode.TooManyRequests)
        }
        val crudo = runCatching { call.receiveText() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (crudo.length > MAX_CUERPO_DEL_FALLO) return@post call.respond(HttpStatusCode.PayloadTooLarge)
        val fallo = runCatching { jsonDeObservabilidad.decodeFromString(FalloDeLaApp.serializer(), crudo) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        Observabilidad.guardarFallo(fallo, usuarioDelToken(call.request.headers[HttpHeaders.Authorization]), ahora())
        call.respond(HttpStatusCode.NoContent)
    }
}

/** Las rutas autenticadas: el uso y las dos de admin. Van dentro de `authenticate("jwt")`. */
fun Route.usoRoutes(ahora: () -> Long = System::currentTimeMillis) {
    post("/api/uso") {
        val uid = call.userId()
        if (!RateLimiter.allow("uso:$uid", LOTES_DE_USO_POR_VENTANA, VENTANA_DE_OBSERVABILIDAD_MS)) {
            return@post call.respond(HttpStatusCode.TooManyRequests)
        }
        val crudo = runCatching { call.receiveText() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (crudo.length > MAX_CUERPO_DEL_USO) return@post call.respond(HttpStatusCode.PayloadTooLarge)
        val lote = runCatching { jsonDeObservabilidad.decodeFromString(LoteDeUso.serializer(), crudo) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        call.respond(Observabilidad.guardarUso(uid, lote.eventos, ahora()))
    }

    get("/api/admin/fallos") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.resumenDeFallos(ahora()))
    }

    get("/api/admin/uso") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.resumenDeUso(ahora()))
    }

    // El visor del recorrido: elegir un usuario, ver sus sesiones y la línea de tiempo de una.
    get("/api/admin/uso/usuarios") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.usuariosConUso())
    }
    get("/api/admin/uso/usuarios/{id}/sesiones") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        val id = call.parameters["id"].orEmpty().take(50)
        call.respond(Observabilidad.sesionesDe(id))
    }
    get("/api/admin/uso/sesiones/{sesionId}/eventos") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        val id = call.parameters["sesionId"].orEmpty().take(80)
        call.respond(Observabilidad.eventosDeLaSesion(id))
    }
}
