package com.jvillada.movi.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jvillada.movi.avisos.Avisador
import com.jvillada.movi.avisos.puedeAvisar
import com.jvillada.movi.sensor.pedirQueReconecte
import com.jvillada.movi.sensor.accesoANotificaciones
import java.util.concurrent.TimeUnit

/**
 * **Vuelta 92 · la vigía de la captura por notificaciones.** Cada 6 horas —el mismo ritmo del
 * barrido de los SMS (`SmsBackfillWorker`)— mira si el acceso a las notificaciones sigue concedido y
 * si el listener está conectado, y hace lo que diga `queHaceLaVigia` (`:shared`, puro y probado):
 *
 * - concedido pero desconectado → `requestRebind`, sin molestar a nadie;
 * - revocado (antes funcionaba) → UNA notificación «Movi dejó de leer las notificaciones del banco ·
 *   Activar»; la misma huella no suena dos veces.
 *
 * No pide red ni sesión: todo lo que mira está en el teléfono. Liviano a propósito: dos lecturas de
 * prefs y una de `Settings.Secure`.
 */
class VigiaDeLaEscuchaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        revisar(applicationContext, conAviso = true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "movi-vigia-de-la-escucha"

        /**
         * La regla, aplicada. [conAviso] en `false` (al abrir la app) solo reconecta: con Movi a
         * la vista, la sección «Captura en este teléfono» ya dice lo que pasa.
         */
        fun revisar(context: Context, conAviso: Boolean) {
            val ultimaConexion = AlmacenDeNotificaciones.conectadaAt(context)
            when (
                queHaceLaVigia(
                    // `null` si no se pudo leer: «no sé» no avisa nada.
                    concedido = accesoANotificaciones(context),
                    conectada = AlmacenDeNotificaciones.estaConectada(context),
                    ultimaConexion = ultimaConexion,
                    huellaYaAvisada = AlmacenDeNotificaciones.revocadoAvisado(context),
                )
            ) {
                LoQueHaceLaVigia.NADA -> Unit
                LoQueHaceLaVigia.RECONECTAR -> pedirQueReconecte(context)
                // Sin permiso para avisar no se anota la huella: cuando lo conceda, suena.
                LoQueHaceLaVigia.AVISAR_QUE_SE_REVOCO -> if (conAviso && puedeAvisar(context)) {
                    Avisador.avisarAccesoRevocado(context)
                    AlmacenDeNotificaciones.guardarRevocadoAvisado(context, huellaDelAccesoRevocado(ultimaConexion))
                }
            }
        }

        /** Idempotente (KEEP), como el barrido de los SMS: llamarlo en cada apertura no reinicia el ciclo. */
        fun programar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<VigiaDeLaEscuchaWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
