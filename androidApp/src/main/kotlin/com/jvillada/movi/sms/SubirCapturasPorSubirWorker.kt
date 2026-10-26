package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jvillada.movi.avisos.Avisador
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.notificaciones.AlmacenDeNotificaciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * **Sube lo que el banco mandó mientras la sesión estaba cerrada** ([CapturasPorSubir]), en un solo
 * lote por el mismo `POST /api/sms/sync` de siempre. Lo encola `MoviAplicacion` apenas el dueño
 * vuelve a entrar. El server deduplica por texto y tiempo, así que si el barrido del inbox sube los
 * mismos SMS, no se escribe nada dos veces.
 *
 * Solo sube lo de quien entró: lo de otra persona se descarta (ver [CapturasPorSubir]).
 */
class SubirCapturasPorSubirWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val contexto = applicationContext
        val token = SessionManager.token
        val userId = SessionManager.userId
        if (token.isNullOrBlank() || userId.isNullOrBlank()) return@withContext Result.success()
        val capturas = CapturasPorSubir.deQuien(contexto, userId)
        if (capturas.isEmpty()) {
            CapturasPorSubir.quitarSubidas(contexto, userId, emptySet())
            return@withContext Result.success()
        }
        val payload = buildSmsSyncPayload(capturas.map { captureItem(it.id, it.sender, it.body, it.ts) })
        when (val result = postSmsSync(token, payload)) {
            is SmsSyncResult.Success -> {
                CapturasPorSubir.quitarSubidas(contexto, userId, capturas.map { it.id }.toSet())
                // Las mismas marcas que el Worker de cada captura, cada sensor la suya.
                if (capturas.any { it.deNotificacion }) AlmacenDeNotificaciones.marcarUltima(contexto)
                if (capturas.any { !it.deNotificacion }) SmsFilterConfigStore.markLastCapture(contexto)
                SmsFilterConfigStore.clearAuthExpired(contexto)
                runCatching { Avisador.avisarMovimientos(contexto, result.porRevisar) }
                    .onFailure { Log.w(TAG, "no se pudo avisar lo que subió", it) }
                Result.success()
            }
            // Siguen guardadas: suben la próxima vez que entre.
            SmsSyncResult.Unauthorized -> {
                SessionManager.seRechazoElToken(token)
                Result.success()
            }
            is SmsSyncResult.ServerError, SmsSyncResult.Network -> Result.retry()
            is SmsSyncResult.Rejected -> {
                Log.w(TAG, "subir lo guardado: rechazado con ${result.code} — queda guardado")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "movi"
        private const val UNICO = "movi-subir-capturas-guardadas"

        fun encolar(context: Context) {
            val pedido = OneTimeWorkRequestBuilder<SubirCapturasPorSubirWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNICO, ExistingWorkPolicy.REPLACE, pedido)
        }
    }
}
