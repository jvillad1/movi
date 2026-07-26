package com.jvillada.movi.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Config del filtro de SMS bancarios del APK sensor. Fuente única: editar estas
 * constantes + deploy web = el filtro cambia en los teléfonos SIN reinstalar APK
 * (el receiver la cachea con TTL 24h y fallback a defaults compilados idénticos).
 * Pública a propósito: solo contiene códigos de remitentes bancarios, nada sensible.
 */
@Serializable
private data class SmsFilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

private val CURRENT_FILTER = SmsFilterConfig(
    senderCodes = listOf("85540", "891333", "87400"),
    bodyKeywords = listOf("bancolombia"),
)

fun Route.smsFilterConfigRoutes() {
    get("/api/sms/filter-config") { call.respond(CURRENT_FILTER) }
}
