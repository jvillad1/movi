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
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * **El resumen de la mañana** (vuelta 68): «Ayer gastaste $85.000» · «Te quedan $1,3M para 4 días · 2
 * avisos por confirmar». Una vez al día, cerca de las 8:00 de Bogotá.
 *
 * Las cifras son las del Inicio: la instantánea que el teléfono ya guardó **si es de hoy**; si no, las
 * pide al server ([leerElInicioParaElResumen], las mismas lecturas del Inicio). Lo gastado ayer sale de
 * una lectura chica de los últimos días ([leerElGastoDeAyer]). Qué dice y si suena lo decide
 * [resumenDeLaManana], puro: sin cifras de hoy o sin nada útil, no suena. Tocarlo abre el Inicio.
 *
 * Mismo patrón que [AvisoDeCierreWorker]: no avisa con el interruptor apagado, sin permiso o sin
 * sesión, y suena **una sola vez por día** (la huella es el día).
 */
class AvisoDeLaMananaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        if (!PreferenciasDeAvisos.avisarResumenDeLaManana(contexto) || !puedeAvisar(contexto)) return Result.success()
        if (SessionManager.token.isNullOrBlank()) return Result.success()
        val ahora = System.currentTimeMillis()
        val usuario = SessionManager.userId
        val instantanea = runCatching { InstantaneaDelInicio.delAparato }.getOrNull()
        val guardada = instantanea?.datos(usuario)
        val guardadaEn = instantanea?.selladaEn(usuario)
        val (data, leidaEn) = if (guardada != null && esDeHoy(guardadaEn, ahora)) {
            guardada to guardadaEn
        } else {
            try {
                leerElInicioParaElResumen(ahora) to ahora
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudieron leer las cifras del resumen de la mañana", e)
                // Sin cifras de hoy no se dice lo de ayer como si fuera de hoy: se reintenta, y si no
                // llegan, hoy no suena.
                return if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.success()
            }
        }
        val tipos = data.accounts.orEmpty().associate { it.id to it.type }
        val ayer = try {
            leerElGastoDeAyer(tipos, ahora)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val ocultos = runCatching { SaldosOcultosStore.ocultos }.getOrDefault(true)
        val resumen = resumenDeLaManana(
            data = data,
            leidaEn = leidaEn,
            gastoDeAyer = ayer,
            ahora = ahora,
            yaAvisado = PreferenciasDeAvisos.resumenDeLaMananaAvisado(contexto),
            ocultos = ocultos,
        ) ?: return Result.success()
        Avisador.avisarResumenDeLaManana(contexto, resumen.texto)
        PreferenciasDeAvisos.guardarResumenDeLaMananaAvisado(contexto, resumen.huella)
        return Result.success()
    }

    companion object {
        private const val TAG = "movi"
        private const val UNIQUE_NAME = "movi-resumen-de-la-manana"
        private const val MAX_REINTENTOS = 3

        /** Idempotente (KEEP), como los otros: la primera vuelta a las 8:00 y de ahí cada 24 h. */
        fun programar(context: Context) {
            val demora = milisHastaLaProxima(HORA_DEL_RESUMEN_DE_LA_MANANA, System.currentTimeMillis())
            val pedido = PeriodicWorkRequestBuilder<AvisoDeLaMananaWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(demora, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
