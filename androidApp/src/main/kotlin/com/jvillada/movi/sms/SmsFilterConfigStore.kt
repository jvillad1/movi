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
 * sensor y el Worker. Fail-open: sin cache válida → BankSenderFilter.DEFAULTS.
 */
object SmsFilterConfigStore {
    private const val PREFS = "movi_sms_filter"
    private const val KEY_JSON = "config_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    const val KEY_LAST_CAPTURE_AT = "last_capture_at"
    private const val TTL_MS = 24 * 3_600_000L

    fun load(context: Context): FilterConfig {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        return json?.let { parseConfigJson(it) } ?: BankSenderFilter.DEFAULTS
    }

    fun refreshIfStale(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force && !isStale(prefs.getLong(KEY_FETCHED_AT, 0), System.currentTimeMillis())) return
        thread(name = "sms-filter-refresh") {
            runCatching {
                val conn = URL("$apiBaseUrl/api/sms/filter-config").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                if (parseConfigJson(body) != null) {
                    prefs.edit()
                        .putString(KEY_JSON, body)
                        .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                        .apply()
                }
            }  // silencioso: el fallback compilado cubre
        }
    }

    fun isStale(fetchedAt: Long, now: Long): Boolean = now - fetchedAt > TTL_MS

    fun parseConfigJson(json: String): FilterConfig? = runCatching {
        val obj = JSONObject(json)
        val codes = obj.getJSONArray("senderCodes").let { a -> (0 until a.length()).map { a.getString(it) } }
        val kws = obj.getJSONArray("bodyKeywords").let { a -> (0 until a.length()).map { a.getString(it) } }
        if (codes.isEmpty() && kws.isEmpty()) null else FilterConfig(codes, kws)
    }.getOrNull()
}
