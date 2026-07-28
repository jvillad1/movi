package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.apiBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sube UN SMS bancario capturado al endpoint idempotente /api/sms/sync.
 * Sin token (deslogueado) → failure sin retry. IOException/5xx → retry con backoff.
 */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = SessionManager.token
        if (token.isNullOrBlank()) {
            Log.w(TAG, "sin sesión — descartando SMS capturado")
            return@withContext Result.failure()
        }
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val sender = inputData.getString("sender").orEmpty()
        val body = inputData.getString("body").orEmpty()
        val ts = inputData.getLong("ts", System.currentTimeMillis())

        val payload = JSONArray().put(
            JSONObject()
                .put("id", id)
                .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts)))
                .put("bank", sender.ifBlank { "SMS" })
                .put("text", body)
                .put("state", "new")
                .put("det", "")
        ).toString()

        try {
            val conn = URL("$apiBaseUrl/api/sms/sync").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            when {
                code in 200..299 -> {
                    SmsFilterConfigStore.markLastCapture(applicationContext)
                    // Bloqueante a propósito: WorkManager puede dejar morir el proceso
                    // apenas doWork retorna, así que un thread{} suelto no se ejecutaría.
                    SmsFilterConfigStore.refreshIfStaleBlocking(applicationContext)
                    Result.success()
                }
                code == 401 -> {
                    // El token venció (30 días, sin endpoint de refresh). Sin esto el
                    // sensor queda mudo para siempre y la pantalla sigue mostrando una
                    // sesión sana: marcamos el error y cerramos sesión para que
                    // SensorScreen muestre el aviso y el formulario de login.
                    Log.w(TAG, "sync 401 — sesión vencida, se requiere volver a entrar")
                    SmsFilterConfigStore.markAuthExpired(applicationContext)
                    SessionManager.clear()
                    Result.failure()
                }
                code >= 500 -> Result.retry()
                else -> {
                    Log.w(TAG, "sync rechazado con $code — sin retry")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            Result.retry()
        }
    }

    private companion object { const val TAG = "movi" }
}
