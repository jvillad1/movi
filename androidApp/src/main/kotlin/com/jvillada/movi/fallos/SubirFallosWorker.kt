package com.jvillada.movi.fallos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * **Sube los fallos guardados en disco** (vuelta 100, ver [CapturaDeFallos]) a `POST /api/fallos`, con
 * red. Único con `APPEND_OR_REPLACE`: lo que se guarde mientras corre sube en la vuelta que se encola
 * detrás, y aunque un reporte se mandara dos veces el server lo guarda una (el id del archivo es el del
 * reporte). Lo que no sube (sin red,
 * un 5xx, el límite) espera al reintento.
 */
class SubirFallosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val quedan = CapturaDeFallos.subirLosFallosGuardados(CapturaDeFallos.carpeta(applicationContext), CapturaDeFallos::enviarAMovi)
        return if (quedan == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val UNICO = "movi-subir-fallos"

        /** Lo encola solo si hay algo guardado. */
        fun encolarSiHayAlgo(context: Context) {
            if (CapturaDeFallos.guardados(CapturaDeFallos.carpeta(context)).isEmpty()) return
            val pedido = OneTimeWorkRequestBuilder<SubirFallosWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // APPEND_OR_REPLACE: un fallo guardado mientras otra subida corre encola otra vuelta detrás
            // (con KEEP se perdía hasta el próximo arranque).
            WorkManager.getInstance(context).enqueueUniqueWork(UNICO, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
        }
    }
}
