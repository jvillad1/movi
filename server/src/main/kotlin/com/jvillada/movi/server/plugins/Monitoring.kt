package com.jvillada.movi.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
    }
    // Vuelta 102: plantilla, método, status y duración de cada pedido, por minuto (ver MetricasPorRuta.kt).
    install(MetricasPorRuta)
}
