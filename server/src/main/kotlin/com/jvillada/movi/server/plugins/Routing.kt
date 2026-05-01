package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.routes.financeRoutes
import com.jvillada.movi.server.routes.smsRoutes
import com.jvillada.movi.server.routes.walletRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        walletRoutes()
        financeRoutes()
        smsRoutes()
        aiRoutes()
    }
}
