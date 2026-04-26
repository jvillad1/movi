package com.jvillada.monedero.server

import com.jvillada.monedero.server.plugins.configureCORS
import com.jvillada.monedero.server.plugins.configureMonitoring
import com.jvillada.monedero.server.plugins.configureRouting
import com.jvillada.monedero.server.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    configureCORS()
    configureSerialization()
    configureMonitoring()
    configureRouting()
}
