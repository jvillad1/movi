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
        // El webhook del proveedor de correo entrante. Público porque Postmark/Mailgun no tienen
        // sesión; lo protege un secreto compartido y se apaga solo si falta la env var. Ver
        // CorreoEntranteRoutes.kt.
        correoEntranteRoutes()
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
            destinoRoutes()
            screenRoutes()
            pushRoutes()
            reminderRoutes()
            smsRoutes()
            direccionDeCorreoRoutes()
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
            // **El navegador tiene que preguntar antes de reusar el bundle.**
            //
            // Hasta acá esto salía sin NINGUNA instrucción de caché —ni `Cache-Control`, ni
            // `Last-Modified`, ni `ETag`— y un navegador sin instrucciones es libre de aplicar su
            // heurística (RFC 9111 §4.2.2): puede servir `composeApp.js` o el `.wasm` desde su
            // disco, sin preguntar, después de un despliegue, y dejar al dueño mirando la app de
            // antes mientras `/version` jura que el commit nuevo está arriba. Los nombres no
            // llevan hash (se mantienen estables a propósito, el Dockerfile los copia por nombre)
            // y no hay parámetro de versión, así que no hay nada más que rompa el empate.
            // `push-sw.js` ya se niega a cachear por exactamente este motivo.
            //
            // `no-cache` **no** es «no guardes»: es «guarda, pero pregunta siempre». Y para que
            // preguntar sea barato está `configureConditionalHeaders()` —la otra mitad de esto—,
            // que hace que la petición condicional se conteste 304 sin cuerpo cuando nada cambió.
            //
            // Va parejo para todo, fuentes incluidas: viven en una ruta fija
            // (`composeResources/…/font/space_grotesk_regular.ttf`), sin hash de contenido, así
            // que un `immutable` de un año dejaría clavada la fuente vieja el día que cambie. Un
            // 304 de cinco archivos es un precio bajo por no tener esa trampa.
            cacheControl { listOf(CacheControl.NoCache(null)) }
        }
    }
}
