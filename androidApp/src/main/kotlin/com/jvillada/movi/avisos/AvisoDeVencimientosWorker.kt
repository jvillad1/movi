package com.jvillada.movi.avisos

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jvillada.movi.data.SessionManager
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * **«Vence mañana / vence hoy»** (Ola 1 · Movi avisa): una vez al día, cerca de las 8:00 de Bogotá,
 * lee los pagos del período ([leerPagosDelPeriodo], la misma lista que «Pagos del período» de Plan)
 * y avisa en UNA notificación los que vencen hoy o mañana y no están pagados
 * ([vencimientosParaAvisar]). Tocarla abre Plan.
 *
 * No avisa con el interruptor apagado, sin permiso o sin sesión. Si el Worker corre dos veces el
 * mismo día con lo mismo pendiente, la segunda no suena ([huellaDeVencimientos]).
 */
class AvisoDeVencimientosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarVencimientos(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val ahora = System.currentTimeMillis()
        val pagos = try {
            vencimientosParaAvisar(leerPagosDelPeriodo(ahora))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red o el server caído: reintentar un par de veces; si no, mañana vuelve.
            Log.w(TAG, "no se pudieron leer los pagos del período", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val texto = textoDeVencimientos(pagos) ?: return Result.success()
        val huella = huellaDeVencimientos(ahora, pagos)
        if (PreferenciasDeAvisos.huellaDeVencimientos(contexto) == huella) return Result.success()
        Avisador.avisarVencimientos(contexto, texto)
        PreferenciasDeAvisos.guardarHuellaDeVencimientos(contexto, huella)
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-aviso-de-vencimientos"
        private const val MAX_REINTENTOS = 3

        /**
         * Idempotente (KEEP): llamarlo en cada `onCreate` no corre el reloj. La primera vuelta cae a
         * las 8:00 de Bogotá ([milisHastaLaProxima]) y de ahí cada 24 horas.
         */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_AVISO_DE_VENCIMIENTOS, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeVencimientosWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
