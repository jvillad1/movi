package com.jvillada.monedero.server.plugins

import com.jvillada.monedero.server.routes.walletRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        walletRoutes()
    }
}
