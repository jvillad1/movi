package com.jvillada.movi.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Auto-sanación de la config del filtro.
 *
 * Sin esto los únicos disparadores del refresh son abrir la pantalla del sensor (el
 * diseño asume que el usuario NO la abre) y una captura exitosa (que la propia config
 * habilita). Un teléfono que perdió la cache — datos borrados, restore de otro equipo,
 * instalado antes de que se agregara su banco en el server — nunca traería config, por lo
 * tanto nunca capturaría, por lo tanto nunca sincronizaría, por lo tanto nunca traería
 * config. Este Worker rompe el círculo: una vez por día, en background, sin abrir la app.
 */
class SmsFilterRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        // force: el punto es refrescar aunque el TTL parezca fresco tras un restore.
        SmsFilterConfigStore.refreshIfStaleBlocking(applicationContext, force = true)
        // Siempre success: fail-open — la cache vieja o los DEFAULTS compilados siguen
        // sirviendo, y reintentar agresivamente no aporta (volvemos en 24h).
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "movi-sms-filter-refresh"

        /** Idempotente (KEEP): re-llamar en cada onCreate no reinicia el ciclo periódico. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsFilterRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
