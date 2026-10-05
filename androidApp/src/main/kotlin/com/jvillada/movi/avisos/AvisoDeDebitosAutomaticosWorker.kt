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
 * **«El banco debió cobrar hoy la cuota de Libre inversión 9695. ¿Se cobró?»** Una vez al día, cerca
 * de las 10:00 de Bogotá, lee lo que el banco cobra solo y nadie confirmó ([leerDebitosAutomaticos],
 * la misma lista de «Por revisar») y avisa lo que este teléfono no avisó todavía
 * ([debitosParaAvisar]). Tocarlo abre «Por revisar», donde se confirma.
 *
 * Mismo patrón que [AvisoDeBancoMudoWorker]: no avisa con el interruptor apagado, sin permiso o sin
 * sesión, y cada débito suena **una sola vez por período** (la clave es regla@período).
 */
class AvisoDeDebitosAutomaticosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarDebitos(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val debitos = try {
            leerDebitosAutomaticos()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "no se pudieron leer los débitos automáticos", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val avisados = PreferenciasDeAvisos.debitosAvisados(contexto)
        val nuevos = debitosParaAvisar(debitos, avisados)
        val texto = textoDelAvisoDeDebitos(nuevos, System.currentTimeMillis()) ?: return Result.success()
        Avisador.avisarDebitosAutomaticos(contexto, texto)
        PreferenciasDeAvisos.guardarDebitosAvisados(contexto, recordarDebitosAvisados(avisados, nuevos))
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-aviso-de-debitos-automaticos"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP), como los otros dos: la primera vuelta a las 10:00 y de ahí cada 24 h. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_AVISO_DE_DEBITOS, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeDebitosAutomaticosWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
