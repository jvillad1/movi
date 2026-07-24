package com.jvillada.movi.sms

import java.security.MessageDigest

/**
 * Filtro de privacidad (LOCKED en el spec): SOLO los SMS que matchean aquí salen del
 * teléfono. Remitentes cortos de Bancolombia + keyword en el cuerpo. Ampliar estas
 * constantes cuando lleguen SMS reales de otros bancos.
 */
object BankSenderFilter {
    private val SENDER_CODES = listOf("85540", "891333", "87400")
    private const val BODY_KEYWORD = "bancolombia"

    fun matches(sender: String?, body: String): Boolean {
        val s = sender.orEmpty()
        if (SENDER_CODES.any { s.contains(it) }) return true
        return body.lowercase().contains(BODY_KEYWORD)
    }
}

/** Id estable ante re-entregas del broadcast y reintentos del Worker (dedupe extremo a extremo). */
fun smsRealtimeId(sender: String?, timestamp: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${sender.orEmpty()}|$timestamp|$body".toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return "sms_rt_${hex.take(16)}"
}
