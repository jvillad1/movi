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
 * 401 → la racha de SessionManager decide el logout; mientras la sesión viva, retry acotado.
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
                // La captura volvió a subir: si quedó una marca de 401 de un episodio
                // anterior, ya no describe la realidad — sin esto, la sección de captura
                // seguiría contando una pausa que terminó.
                SmsFilterConfigStore.clearAuthExpired(applicationContext)
                // Bloqueante a propósito: WorkManager puede dejar morir el proceso
                // apenas doWork retorna, así que un thread{} suelto no se ejecutaría.
                SmsFilterConfigStore.refreshIfStaleBlocking(applicationContext)
                Result.success()
            }
            SmsSyncResult.Unauthorized -> {
                // El token no pasó: puede ser vencimiento real (30 días, sin refresh) o un
                // 401 transitorio. La racha de SessionManager (3 seguidos) decide si la
                // sesión se cierra — un clear() directo acá deslogueaba al usuario de TODA
                // la app por un solo 401 en background. La marca de authExpired queda para
                // que la sección «Captura en este teléfono» pueda contar la pausa.
                Log.w(TAG, "sync 401 — token rechazado; la racha decide el logout")
                SmsFilterConfigStore.markAuthExpired(applicationContext)
                SessionManager.onUnauthorized()
                if (SessionManager.loggedIn && runAttemptCount < MAX_AUTH_ATTEMPTS) {
                    // La sesión sobrevivió: reintentar para no perder ESTE SMS. Acotado
                    // porque la racha vive en memoria — si WorkManager relanza el intento
                    // en un proceso nuevo, el contador arranca de cero y sin tope este
                    // 401 persistente reintentaría para siempre.
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
            is SmsSyncResult.ServerError -> Result.retry()
            SmsSyncResult.Network -> Result.retry()
            is SmsSyncResult.Rejected -> {
                Log.w(TAG, "sync rechazado con ${result.code} — sin retry")
                Result.failure()
            }
        }
    }

    private companion object {
        const val TAG = "movi"

        /** Tope de reintentos ante 401 con sesión viva — ver el comentario del branch. */
        const val MAX_AUTH_ATTEMPTS = 5
    }
}
