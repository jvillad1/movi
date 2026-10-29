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
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.SessionManager
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * **El cierre semanal de la pareja** (vuelta 68): «Esta semana gastaron $480.000 juntos en Casa con
 * Caro» · «Caro te debe $76.050». Corre cada día cerca de las 19:00 de Bogotá, pero **solo el domingo**
 * (o el lunes, si el domingo no hubo red) hace algo: lee los grupos con su detalle
 * ([leerLosGruposConSuDetalle]) y [resumenSemanal] decide. Sin un grupo con gastos esa semana, no suena.
 * Tocarlo abre el grupo.
 *
 * Mismo patrón que los otros: interruptor, permiso y sesión, y **una sola vez por semana** (la huella
 * es su lunes).
 */
class AvisoSemanalDeLaParejaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarResumenSemanal(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val ahora = System.currentTimeMillis()
        // Entre semana no se pide nada: el resumen es del domingo.
        val huella = huellaDeLaSemanaDe(ahora) ?: return Result.success()
        if (PreferenciasDeAvisos.resumenSemanalAvisado(contexto) == huella) return Result.success()
        val grupos = try {
            leerLosGruposConSuDetalle()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "no se pudieron leer los grupos para el resumen semanal", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val ocultos = runCatching { SaldosOcultosStore.ocultos }.getOrDefault(true)
        val resumen = resumenSemanal(grupos, ahora, PreferenciasDeAvisos.resumenSemanalAvisado(contexto), ocultos)
            ?: return Result.success()
        Avisador.avisarResumenSemanal(contexto, resumen.texto, resumen.abrir)
        PreferenciasDeAvisos.guardarResumenSemanalAvisado(contexto, resumen.huella)
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-resumen-semanal-de-la-pareja"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP): la primera vuelta a las 19:00 y de ahí cada 24 h; decide al correr. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_RESUMEN_SEMANAL, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoSemanalDeLaParejaWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
