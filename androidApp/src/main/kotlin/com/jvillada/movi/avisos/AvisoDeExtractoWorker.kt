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
 * **«¿Ya bajaste el extracto de Nu de octubre?»** (vuelta 94). Una vez al día, cerca de las 11:00 de
 * Bogotá, lee los extractos de tarjeta que faltan subir ([leerExtractosPorSubir], la misma lista que
 * propone Hoy) y avisa lo que este teléfono no avisó todavía ([extractosParaAvisar]): una vez por tarjeta
 * y corte. Tocarlo abre el Inicio, donde está «Subir el extracto».
 *
 * Mismo patrón que [AvisoDeDebitosAutomaticosWorker]: no avisa con el interruptor apagado, sin permiso
 * o sin sesión.
 */
class AvisoDeExtractoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarExtracto(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val extractos = try {
            leerExtractosPorSubir()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Vuelta 100: lo atrapado también se reporta (sin cambiar lo que sigue); la red caída, no.
            com.jvillada.movi.fallos.CapturaDeFallos.reportarAtrapado(applicationContext, e, "AvisoDeExtractoWorker")
            Log.w(TAG, "no se pudieron leer los extractos por subir", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val avisados = PreferenciasDeAvisos.extractosAvisados(contexto)
        val nuevos = extractosParaAvisar(extractos, avisados)
        val texto = textoDelAvisoDeExtracto(nuevos) ?: return Result.success()
        Avisador.avisarExtracto(contexto, texto)
        PreferenciasDeAvisos.guardarExtractosAvisados(contexto, recordarExtractosAvisados(avisados, nuevos))
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-aviso-de-extracto-por-subir"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP), como los otros: la primera vuelta a las 11:00 y de ahí cada 24 h. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_AVISO_DE_EXTRACTO, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeExtractoWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
