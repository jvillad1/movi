package com.jvillada.movi.sms

import com.jvillada.movi.data.apiBaseUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Único uploader del sensor hacia el endpoint idempotente `POST /api/sms/sync`.
 *
 * Lo usan los DOS caminos que suben SMS: la captura en tiempo real (SmsSyncWorker, un
 * mensaje por request) y el backfill manual de la pantalla (SmsBackfill, un lote). Que
 * compartan payload, auth y manejo de códigos es deliberado: el server dedupea por texto
 * dentro del usuario, así que ambos caminos tienen que producir exactamente la misma
 * forma de mensaje o el dedupe se vuelve frágil. No agregar un segundo uploader.
 */
internal data class SmsSyncItem(
    val id: String,
    /** Formato del wire: "yyyy-MM-dd HH:mm". */
    val time: String,
    val bank: String,
    val text: String,
)

/** Item del camino de captura: normaliza timestamp y remitente al formato del wire. */
internal fun captureItem(id: String, sender: String, body: String, ts: Long) = SmsSyncItem(
    id = id,
    time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts)),
    // SimpleDateFormat no es thread-safe: se construye por llamada a propósito.
    bank = sender,
    text = body,
)

/**
 * Serializa el lote. `bank` en blanco → "SMS": el remitente vacío del inbox y el del
 * broadcast tienen que verse igual del lado del server.
 */
internal fun buildSmsSyncPayload(items: List<SmsSyncItem>): String {
    val array = JSONArray()
    for (item in items) {
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("time", item.time)
                .put("bank", item.bank.ifBlank { "SMS" })
                .put("text", item.text)
                .put("state", "new")
                .put("det", "")
        )
    }
    return array.toString()
}

internal sealed interface SmsSyncResult {
    /** [synced] es lo que el server dice haber insertado; null si la respuesta no se pudo leer. */
    data class Success(val synced: Int?) : SmsSyncResult

    /** 401: el token venció (30 días, sin refresh). Quien llama decide qué mostrar. */
    data object Unauthorized : SmsSyncResult

    /** 5xx — vale la pena reintentar. */
    data class ServerError(val code: Int) : SmsSyncResult

    /** Otro código de error — reintentar no lo va a arreglar. */
    data class Rejected(val code: Int) : SmsSyncResult

    /** Falla de red — vale la pena reintentar. */
    data object Network : SmsSyncResult
}

/** Cuenta reportada por el server. Devuelve null ante cuerpo ausente o inesperado. */
internal fun parseSyncedCount(body: String?): Int? =
    body?.let { runCatching { JSONObject(it).getInt("synced") }.getOrNull() }

/** POST bloqueante — llamar SIEMPRE desde un hilo de IO. */
internal fun postSmsSync(token: String, payload: String): SmsSyncResult = try {
    val conn = URL("$apiBaseUrl/api/sms/sync").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        // El cuerpo solo se lee en éxito y sin dejar que una falla de lectura degrade un 2xx
        // a reintento: el conteo es informativo, la inserción ya ocurrió.
        val body = if (code in 200..299) {
            runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
        } else {
            null
        }
        when {
            code in 200..299 -> SmsSyncResult.Success(parseSyncedCount(body))
            code == 401 -> SmsSyncResult.Unauthorized
            code >= 500 -> SmsSyncResult.ServerError(code)
            else -> SmsSyncResult.Rejected(code)
        }
    } finally {
        conn.disconnect()
    }
} catch (_: IOException) {
    SmsSyncResult.Network
}
