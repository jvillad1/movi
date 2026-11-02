package com.jvillada.movi.sms

import com.jvillada.movi.shared.model.seSubeElSms
import java.security.MessageDigest

data class FilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

/**
 * Filtro de privacidad (LOCKED en el spec): SOLO los SMS que matchean aquí salen del
 * teléfono. Remitentes cortos de bancos + keyword en el cuerpo (de un remitente que no es un
 * número personal, desde la vuelta 113). La config es
 * parametrizable (ver SmsFilterConfigStore) para agregar bancos sin reinstalar el APK;
 * DEFAULTS es el fallback compilado — hoy arranca igual a la fuente del server, pero ya
 * no es un espejo: es el PISO. SmsFilterConfigStore.load() siempre une la config remota
 * con DEFAULTS (ver withDefaults), así que el server puede agregar códigos/keywords por
 * encima de este piso pero nunca angostarlo por debajo.
 */
object BankSenderFilter {
    /** Fallback compilado — piso mínimo garantizado, no un espejo del server (ver arriba). */
    val DEFAULTS = FilterConfig(
        senderCodes = listOf("85540", "891333", "87400"),
        // Vuelta 99: el piso solo suma «davivienda». Los otros bancos los manda el server
        // (`PALABRAS_DE_BANCOS`, SmsFilterRefreshWorker): `withDefaults` une siempre lo remoto con este
        // piso, así que lo que entra acá ya no se puede quitar sin otro APK.
        bodyKeywords = listOf("bancolombia", "nubank", "davivienda"),
    )

    /**
     * Vuelta 113 · la regla vive en `:core` ([seSubeElSms]): un remitente que contiene un código conocido
     * sale; una palabra de banco en el cuerpo solo si hay remitente y **no es un número personal** (10 o
     * más dígitos). La usan la captura en tiempo real (`SmsRealtimeReceiver`) y el barrido
     * ([filterBankMessages]).
     */
    fun matches(sender: String?, body: String, config: FilterConfig = DEFAULTS): Boolean =
        seSubeElSms(sender, body, config.senderCodes, config.bodyKeywords)
}

/** Id estable ante re-entregas del broadcast y reintentos del Worker (dedupe extremo a extremo). */
fun smsRealtimeId(sender: String?, timestamp: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${sender.orEmpty()}|$timestamp|$body".toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return "sms_rt_${hex.take(16)}"
}
