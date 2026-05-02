package com.jvillada.movi.server

import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureCORS
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
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
    configureAuth()
    configureRouting()
}
