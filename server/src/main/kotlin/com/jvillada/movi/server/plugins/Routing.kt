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

        authenticate("jwt") {
            userRoutes()
            accountRoutes()
            eventRoutes()
            financeRoutes()
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
