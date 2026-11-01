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
 * **«Cerró tu período de septiembre».** Una vez al día, cerca de las 9:00 de Bogotá, lee el resumen
 * del período que acaba de cerrar ([leerCierreDelPeriodo], el mismo de la tarjeta de Hoy) y, si este
 * teléfono no lo avisó todavía y es uno de los primeros días del período nuevo ([cierreParaAvisar]),
 * avisa. Tocarlo abre el detalle de ese período.
 *
 * Mismo patrón que [AvisoDeDebitosAutomaticosWorker]: no avisa con el interruptor apagado, sin permiso
 * o sin sesión, y suena **una sola vez por período**.
 */
class AvisoDeCierreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarCierre(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val cierre = try {
            leerCierreDelPeriodo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Vuelta 100: lo atrapado también se reporta (sin cambiar lo que sigue); la red caída, no.
            com.jvillada.movi.fallos.CapturaDeFallos.reportarAtrapado(applicationContext, e, "AvisoDeCierreWorker")
            Log.w(TAG, "no se pudo leer el cierre del período", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val aAvisar = cierreParaAvisar(cierre, PreferenciasDeAvisos.cierreAvisado(contexto)) ?: return Result.success()
        Avisador.avisarCierre(contexto, aAvisar.periodo, textoDelAvisoDeCierre(aAvisar))
        PreferenciasDeAvisos.guardarCierreAvisado(contexto, aAvisar.periodo)
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-aviso-de-cierre-del-periodo"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP), como los otros: la primera vuelta a las 9:00 y de ahí cada 24 h. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_AVISO_DE_CIERRE, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeCierreWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
