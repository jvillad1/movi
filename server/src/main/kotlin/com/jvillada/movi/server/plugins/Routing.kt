package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cuerpo del 404 de una ruta `/api/…` inexistente. Se serializa a mano (ver [apiNotFound]) para
 * no depender de que ContentNegotiation esté instalado: este handler tiene que contestar igual de
 * bien en cualquier armado de la app.
 */
@Serializable
private data class ApiNotFound(
    val error: String,
    val message: String,
    val path: String,
)

private val notFoundJson = Json { encodeDefaults = true }

/**
 * Antes de esto, el `staticResources("/", "static") { default("index.html") }` del final se comía
 * TODA ruta no registrada: `GET /api/documents` contra un server que no tiene esa ruta devolvía
 * 200 con el HTML de la SPA. El cliente entonces fallaba al deserializar con un error de parseo
 * que no dice «esa ruta no existe» — horas de diagnóstico por un simple desfase de versiones
 * entre app y server (ya pasó una vez con `usedCategories`).
 *
 * Esta ruta comodín se registra ANTES del static: cualquier `/api/...` que ninguna ruta real haya
 * reclamado cae acá y contesta 404 con JSON. Las rutas reales siguen ganando porque Ktor puntúa
 * los segmentos constantes por encima de un tailcard.
 */
private fun Route.apiNotFound() {
    route("/api/{...}") {
        handle {
            val path = call.request.path()
            val body = ApiNotFound(
                error = "not_found",
                message = "No existe el endpoint ${call.request.httpMethod.value} $path en este servidor. " +
                    "Si tu app lo espera, es probable que el servidor esté desactualizado.",
                path = path,
            )
            call.respondText(
                text = notFoundJson.encodeToString(ApiNotFound.serializer(), body),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.NotFound,
            )
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        versionRoutes()                   // public — qué commit está corriendo (ver VersionRoutes.kt)
        authRoutes()                     // public — no auth required
        pushPublicRoutes()                // public — no auth required
        smsFilterConfigRoutes()           // public — no auth required
        // El CONTENIDO de un documento va fuera del bloque autenticado a propósito: se abre
        // desde el navegador, que no puede mandar `Authorization`. Lo protege un token de
        // descarga aparte —otra audiencia, un solo documento, cinco minutos—. Ver
        // JwtConfig.makeDownloadToken.
        documentContentRoutes()

        authenticate("jwt") {
            userRoutes()
            accountRoutes()
            eventRoutes()
            transferRoutes()
            pagoDeCuotaRoutes()
            financeRoutes()
            categoryRoutes()
            dashboardRoutes()
            creditRoutes()
            cardRoutes()
            subscriptionRoutes()
            goalRoutes()
            screenRoutes()
            pushRoutes()
            reminderRoutes()
            smsRoutes()
            aiRoutes()
            statementRoutes()
            documentRoutes()
        }

        // /api/** que nadie registró → 404 JSON, nunca el index.html de la SPA.
        // Debe ir después de las rutas reales y antes del static.
        apiNotFound()

        // Serve wasmJs web app — must be last so API routes take priority
        staticResources("/", "static") {
            default("index.html")
            // Ktor 3.x doesn't register application/wasm by default
            contentType { url ->
                if (url.path.endsWith(".wasm")) ContentType("application", "wasm") else null
            }
        }
    }
}
