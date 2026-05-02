package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.routes.*
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        authRoutes()                     // public — no auth required

        authenticate("jwt") {
            accountRoutes()
            eventRoutes()
            walletRoutes()
            financeRoutes()
            smsRoutes()
            aiRoutes()
        }
    }
}
