package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.observabilidad.LecturasDelPanel
import com.jvillada.movi.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * # La observabilidad del servidor, para un admin (vuelta 102)
 *
 * - `GET /health/detalle` — la salud con detalle: la base (un ping y su latencia), la última corrida
 *   de cada job, las variables críticas **presentes o ausentes** (nunca su valor), la versión, el uptime,
 *   la memoria y el pool. `/health` sigue siendo el «OK» de texto que mira Railway.
 * - `GET /api/admin/servidor/resumen?horas=24|168` — pedidos, errores y p95 hora por hora, las 10 rutas
 *   más lentas y las 10 con más 5xx.
 * - `GET /api/admin/servidor/servicios?dias=7` — Anthropic (por modelo), Resend y la TRM; la IA por día.
 * - `GET /api/admin/servidor/jobs` — la última corrida de cada job y las que fallaron.
 * - `GET /api/admin/servidor/salud` — lo mismo que `/health/detalle`, junto a las otras.
 *
 * Van dentro de `authenticate("jwt")` y contestan **403** a quien no está en `ADMIN_USER_IDS`.
 */
fun Route.adminServidorRoutes() {
    get("/health/detalle") {
        if (!soloAdmin()) return@get
        call.respond(LecturasDelPanel.salud())
    }
    route("/api/admin/servidor") {
        get("/resumen") {
            if (!soloAdmin()) return@get
            val horas = call.request.queryParameters["horas"]?.toIntOrNull()?.coerceIn(1, 24 * 31) ?: 24
            call.respond(LecturasDelPanel.resumen(horas))
        }
        get("/servicios") {
            if (!soloAdmin()) return@get
            val dias = call.request.queryParameters["dias"]?.toIntOrNull()?.coerceIn(1, 365) ?: 7
            call.respond(LecturasDelPanel.servicios(dias))
        }
        get("/jobs") {
            if (!soloAdmin()) return@get
            call.respond(LecturasDelPanel.jobs())
        }
        get("/salud") {
            if (!soloAdmin()) return@get
            call.respond(LecturasDelPanel.salud())
        }
    }
}

/** `false` (y ya contestó 403) si quien pide no es admin. */
private suspend fun RoutingContext.soloAdmin(): Boolean {
    if (AdminConfig.isAdmin(call.userId())) return true
    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Solo para quien administra Movi."))
    return false
}
