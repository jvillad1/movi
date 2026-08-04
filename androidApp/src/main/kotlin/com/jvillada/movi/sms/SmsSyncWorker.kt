package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvillada.movi.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sube UN SMS bancario capturado al endpoint idempotente /api/sms/sync (ver SmsSync.kt,
 * compartido con el backfill de la pantalla).
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

        val payload = buildSmsSyncPayload(listOf(captureItem(id, sender, body, ts)))

        when (val result = postSmsSync(token, payload)) {
            is SmsSyncResult.Success -> {
                SmsFilterConfigStore.markLastCapture(applicationContext)
                // Bloqueante a propósito: WorkManager puede dejar morir el proceso
                // apenas doWork retorna, así que un thread{} suelto no se ejecutaría.
                SmsFilterConfigStore.refreshIfStaleBlocking(applicationContext)
                Result.success()
            }
            SmsSyncResult.Unauthorized -> {
                // El token venció (30 días, sin endpoint de refresh). Sin esto el
                // sensor queda mudo para siempre y la pantalla sigue mostrando una
                // sesión sana: marcamos el error y cerramos sesión para que
                // SensorScreen muestre el aviso y el formulario de login.
                Log.w(TAG, "sync 401 — sesión vencida, se requiere volver a entrar")
                SmsFilterConfigStore.markAuthExpired(applicationContext)
                SessionManager.clear()
                Result.failure()
            }
            is SmsSyncResult.ServerError -> Result.retry()
            SmsSyncResult.Network -> Result.retry()
            is SmsSyncResult.Rejected -> {
                Log.w(TAG, "sync rechazado con ${result.code} — sin retry")
                Result.failure()
            }
        }
    }

    private companion object { const val TAG = "movi" }
}
