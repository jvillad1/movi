package com.jvillada.movi.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory

private val corsLog = LoggerFactory.getLogger("CORS")

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        val raw = System.getenv("ALLOWED_ORIGINS")?.trim().orEmpty()
        if (raw.isBlank()) {
            corsLog.warn("ALLOWED_ORIGINS is not set — CORS is open (anyHost). Set it in production.")
            anyHost()
        } else {
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { entry ->
                // Strip scheme if present (e.g. "https://example.com" → "example.com")
                val hostPart = if ("://" in entry) entry.substringAfter("://") else entry
                // Split host:port if a port is present
                val colonIdx = hostPart.lastIndexOf(':')
                if (colonIdx > 0) {
                    val host = hostPart.substring(0, colonIdx)
                    allowHost(host, schemes = listOf("https", "http"))
                } else {
                    allowHost(hostPart, schemes = listOf("https", "http"))
                }
            }
        }
    }
}
