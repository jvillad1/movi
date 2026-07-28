package com.jvillada.movi.sms

import android.content.Context
import com.jvillada.movi.data.apiBaseUrl
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Config remota del filtro con cache local. El camino del SMS (receiver) SOLO lee
 * SharedPreferences — la red vive en refreshIfStale, disparado desde la pantalla del
 * sensor, el Worker de sync y el Worker periódico. Fail-open: sin cache válida →
 * BankSenderFilter.DEFAULTS.
 */
object SmsFilterConfigStore {
    /** Prefs del sensor. Único dueño del nombre: nadie más debe escribir el literal. */
    internal const val PREFS = "movi_sms_filter"
    private const val KEY_JSON = "config_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    const val KEY_LAST_CAPTURE_AT = "last_capture_at"

    /**
     * Momento del último 401 del Worker de sync. Marca que el token venció: el sensor
     * sigue capturando pero no puede subir nada hasta que el usuario vuelva a entrar.
     */
    const val KEY_AUTH_ERROR_AT = "auth_error_at"
    private const val TTL_MS = 24 * 3_600_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): FilterConfig {
        val json = prefs(context).getString(KEY_JSON, null)
        return json?.let { parseConfigJson(it) } ?: BankSenderFilter.DEFAULTS
    }

    /**
     * Dispara el refresh en un hilo aparte — para el camino de UI, donde el proceso sigue
     * vivo. [onUpdated], si se provee, se invoca tras CADA fetch+parse exitoso (no compara
     * el contenido con la cache previa, así que también se dispara con una config
     * idéntica). Se llama desde el hilo background del fetch — quien lo pase es
     * responsable de saltar al hilo principal si va a tocar estado de Compose.
     */
    fun refreshIfStale(context: Context, force: Boolean = false, onUpdated: (() -> Unit)? = null) {
        if (!shouldRefresh(context, force)) return
        thread(name = "sms-filter-refresh") { fetchAndStore(context, onUpdated) }
    }

    /**
     * Igual que [refreshIfStale] pero bloqueante. Es la variante correcta dentro de un
     * Worker: ya corre en un hilo background y WorkManager puede dejar morir el proceso
     * apenas doWork retorna, así que un `thread {}` suelto no es confiable.
     * Devuelve true si la cache quedó actualizada.
     */
    fun refreshIfStaleBlocking(context: Context, force: Boolean = false): Boolean {
        if (!shouldRefresh(context, force)) return false
        return fetchAndStore(context, null)
    }

    private fun shouldRefresh(context: Context, force: Boolean): Boolean =
        force || isStale(prefs(context).getLong(KEY_FETCHED_AT, 0), System.currentTimeMillis())

    private fun fetchAndStore(context: Context, onUpdated: (() -> Unit)?): Boolean = runCatching {
        val conn = URL("$apiBaseUrl/api/sms/filter-config").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val body = try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
        if (parseConfigJson(body) == null) return@runCatching false
        prefs(context).edit()
            .putString(KEY_JSON, body)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
        onUpdated?.invoke()
        true
    }.getOrDefault(false)  // silencioso: el fallback compilado cubre

    fun isStale(fetchedAt: Long, now: Long): Boolean = now - fetchedAt > TTL_MS

    fun parseConfigJson(json: String): FilterConfig? = runCatching {
        val obj = JSONObject(json)
        val codes = obj.getJSONArray("senderCodes").let { a -> (0 until a.length()).map { a.getString(it) } }
        val kws = obj.getJSONArray("bodyKeywords").let { a -> (0 until a.length()).map { a.getString(it) } }
        if (codes.isEmpty() && kws.isEmpty()) null else FilterConfig(codes, kws)
    }.getOrNull()

    fun markLastCapture(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CAPTURE_AT, System.currentTimeMillis()).apply()
    }

    fun lastCaptureAt(context: Context): Long = prefs(context).getLong(KEY_LAST_CAPTURE_AT, 0L)

    fun markAuthExpired(context: Context) {
        prefs(context).edit().putLong(KEY_AUTH_ERROR_AT, System.currentTimeMillis()).apply()
    }

    fun clearAuthExpired(context: Context) {
        prefs(context).edit().remove(KEY_AUTH_ERROR_AT).apply()
    }

    fun authErrorAt(context: Context): Long = prefs(context).getLong(KEY_AUTH_ERROR_AT, 0L)

    /**
     * El sensor está mudo por sesión vencida: hubo un 401 y no hay sesión activa. Volver a
     * entrar limpia la marca, así que un login exitoso apaga el aviso.
     */
    fun isSessionExpired(authErrorAt: Long, loggedIn: Boolean): Boolean = authErrorAt > 0L && !loggedIn
}
