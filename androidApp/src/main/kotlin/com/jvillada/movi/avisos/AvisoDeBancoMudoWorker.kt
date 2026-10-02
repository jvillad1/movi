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
 * **«Hace 4 días no llega nada de Bancolombia»** (Ola 2 · banco mudo): una vez al día, cerca de las
 * 9:00 de Bogotá, le pregunta al server qué orígenes de captura se callaron ([leerOrigenesMudos]; la
 * regla es `origenesMudos`, en :core) y lo avisa en una notificación. Tocarla abre Captura del banco.
 *
 * No avisa con el interruptor apagado, sin permiso o sin sesión, y el mismo silencio no suena dos
 * veces ([huellaDeBancosMudos]). Los días que cuentan como silencio los elige el dueño en la app.
 */
class AvisoDeBancoMudoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarBancoMudo(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val mudos = try {
            leerOrigenesMudos()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "no se pudieron leer los orígenes mudos", e)
            return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
        }
        val texto = textoDeBancosMudos(mudos) ?: return Result.success()
        val huella = huellaDeBancosMudos(mudos)
        if (PreferenciasDeAvisos.huellaDeBancoMudo(contexto) == huella) return Result.success()
        Avisador.avisarBancoMudo(contexto, texto)
        PreferenciasDeAvisos.guardarHuellaDeBancoMudo(contexto, huella)
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-aviso-de-banco-mudo"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP), como el de vencimientos: la primera vuelta a las 9:00 y de ahí cada 24 h. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_AVISO_DE_BANCO_MUDO, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeBancoMudoWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
