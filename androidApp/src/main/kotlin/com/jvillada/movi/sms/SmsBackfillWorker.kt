package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jvillada.movi.shared.model.PeriodSettings
import java.util.concurrent.TimeUnit

/**
 * **La red que atrapa los SMS que la captura en vivo no alcanzó a subir.**
 *
 * `SmsRealtimeReceiver` no sube nada por sí mismo: encola un `SmsSyncWorker` por mensaje. Si
 * Android tiene a Movi en un cubo de espera castigado —lo normal en un teléfono donde la app se
 * abre cada varios días: `am get-standby-bucket` daba 40 (RARE)— esos trabajos se posponen y se
 * pierden. Medido en el teléfono del dueño el 16-sep: de once SMS bancarios entre el 12 y el 15,
 * **uno solo** llegó al server; los diez que faltaban seguían en el inbox y subieron enteros
 * cuando tocó «Sincronizar el período» a mano.
 *
 * Que se recuperen solo cuando él se acuerde de tocar un botón no sirve: el aviso de lo que hay
 * por confirmar, los pagos que se cruzan con lo anotado y las coincidencias del extracto dependen
 * de que el SMS esté arriba. Así que el mismo barrido corre en segundo plano cada 6 horas. El
 * trabajo periódico sí tiene cuota propia en cada cubo de espera, y el server deduplica por
 * (texto, tiempo), así que repetir un barrido no escribe nada dos veces.
 */
class SmsBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // El período de calendario alcanza y evita pedir el perfil: lee desde el inicio del mes
        // pasado, que cubre cualquier corte. Ver SmsBackfill.conElPeriodo.
        return when (val resultado = SmsBackfill.conElPeriodo(applicationContext, PeriodSettings())) {
            is BackfillOutcome.Uploaded, BackfillOutcome.NothingFound -> Result.success()
            // Sin sesión y sin permiso no se arreglan reintentando: se arreglan en la pantalla.
            BackfillOutcome.NoSession, BackfillOutcome.NoPermission, BackfillOutcome.SessionExpired -> Result.success()
            BackfillOutcome.AuthRetry, BackfillOutcome.Failed -> {
                Log.w(TAG, "backfill periódico sin éxito ($resultado) — se reintenta")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-sms-backfill"
        private const val UNIQUE_AHORA = "movi-sms-backfill-ahora"

        /**
         * **Y un barrido al abrir la app**, que es lo que tapa el hueco entre ciclos: el periódico
         * puede tardar hasta seis horas en tocarle turno (Android no lo adelanta ni forzándolo), y
         * abrir Movi es justo el momento en que el dueño va a mirar los números. REPLACE y no KEEP
         * porque lo que importa es el barrido más reciente, no el que quedó colgado sin red.
         */
        fun barrerAhora(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_AHORA, ExistingWorkPolicy.REPLACE, request)
        }

        /** Idempotente (KEEP): llamarlo en cada apertura no reinicia el ciclo. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsBackfillWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
