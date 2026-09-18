package com.jvillada.movi.server

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.DatabaseFactory
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureAutoHead
import com.jvillada.movi.server.plugins.configureCORS
import com.jvillada.movi.server.plugins.configureConditionalHeaders
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.plugins.configureStatusPages
import com.jvillada.movi.server.reminders.startReminderScheduler
import com.jvillada.movi.server.time.AppClock
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    // Zona civil de la app (APP_TIMEZONE, default Bogotá): si alguien la cambia en Railway,
    // que quede en el log de arranque.
    log.info("AppClock zone=${AppClock.zone.id} (APP_TIMEZONE=${System.getenv("APP_TIMEZONE") ?: "<sin definir>"})")
    // La huella de la llave que firma las sesiones. Si cambia entre dos arranques sin que nadie
    // la haya rotado, todas las sesiones abiertas se caen — y desde el teléfono eso se ve como
    // «tu sesión venció». Ver JwtConfig.huellaDelSecreto.
    log.info("JWT secreto huella=${JwtConfig.huellaDelSecreto} (si cambia entre arranques, las sesiones se caen)")
    DatabaseFactory.init()
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    // Antes de routing: le da a lo estático la etiqueta con la que el navegador revalida
    // (ver ConditionalHeaders.kt — es la otra mitad del `no-cache` del bundle).
    configureConditionalHeaders()
    // Y la respuesta a un HEAD sobre lo estático deja de ser un 404 (ver AutoHead.kt).
    configureAutoHead()
    configureAuth()
    configureRouting()
    startReminderScheduler()  // no-op if RESEND_API_KEY is absent
}
