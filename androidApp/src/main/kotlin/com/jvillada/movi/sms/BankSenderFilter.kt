package com.jvillada.movi.sms

import java.security.MessageDigest

data class FilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

/**
 * Filtro de privacidad (LOCKED en el spec): SOLO los SMS que matchean aquí salen del
 * teléfono. Remitentes cortos de bancos + keyword en el cuerpo. La config es
 * parametrizable (ver SmsFilterConfigStore) para agregar bancos sin reinstalar el APK;
 * DEFAULTS es el fallback compilado, idéntico a la fuente del server.
 */
object BankSenderFilter {
    /** Idénticos a la fuente del server (GET /api/sms/filter-config) — fallback compilado. */
    val DEFAULTS = FilterConfig(
        senderCodes = listOf("85540", "891333", "87400"),
        bodyKeywords = listOf("bancolombia"),
    )

    fun matches(sender: String?, body: String, config: FilterConfig = DEFAULTS): Boolean {
        val s = sender.orEmpty()
        if (config.senderCodes.any { s.contains(it) }) return true
        val lower = body.lowercase()
        return config.bodyKeywords.any { lower.contains(it.lowercase()) }
    }
}

/** Id estable ante re-entregas del broadcast y reintentos del Worker (dedupe extremo a extremo). */
fun smsRealtimeId(sender: String?, timestamp: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${sender.orEmpty()}|$timestamp|$body".toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return "sms_rt_${hex.take(16)}"
}
