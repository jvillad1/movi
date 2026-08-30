package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
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
