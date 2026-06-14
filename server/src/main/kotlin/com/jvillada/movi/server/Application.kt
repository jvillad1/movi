package com.jvillada.movi.server

import com.jvillada.movi.server.db.DatabaseFactory
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureCORS
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.startReminderScheduler
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureCORS()
    configureSerialization()
    configureMonitoring()
    configureAuth()
    configureRouting()
    startReminderScheduler()  // no-op if RESEND_API_KEY is absent
}
