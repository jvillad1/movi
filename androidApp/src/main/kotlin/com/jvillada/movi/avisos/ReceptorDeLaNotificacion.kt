package com.jvillada.movi.avisos

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jvillada.movi.data.SessionManager
import kotlinx.coroutines.CancellationException

/**
 * **«Confirmar» en la notificación de un aviso** (Ola 5 · sin abrir la app).
 *
 * El toque llega acá, un `BroadcastReceiver` que no abre ninguna pantalla. Hace dos cosas, rápido:
 * cambia la notificación a «Anotando…» —sin botones, así un segundo toque no tiene dónde caer— y
 * encola [ConfirmarDesdeLaNotificacionWorker] con un nombre único por aviso y `KEEP`: dos toques que
 * llegaran igual encolan **un** trabajo. La red va en el Worker, no acá: un receptor tiene segundos
 * para terminar y confirmar son cinco idas al server.
 *
 * **No pasa por la huella**, a propósito: ver `ConfirmarDesdeLaNotificacion.kt` en `:shared`.
 */
class ReceptorDeLaNotificacion : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACCION_CONFIRMAR) return
        val avisoId = intent.getStringExtra(EXTRA_AVISO) ?: return
        val categoria = intent.getStringExtra(EXTRA_CATEGORIA) ?: return
        val cuentaId = intent.getStringExtra(EXTRA_CUENTA) ?: return
        val monto = intent.getLongExtra(EXTRA_MONTO, -1L).takeIf { it > 0 } ?: return
        Avisador.mostrarConfirmacion(context, avisoId, Avisador.EstadoDeLaConfirmacion.ANOTANDO)
        val pedido = OneTimeWorkRequestBuilder<ConfirmarDesdeLaNotificacionWorker>()
            .setInputData(workDataOf(EXTRA_AVISO to avisoId, EXTRA_CATEGORIA to categoria, EXTRA_CUENTA to cuentaId, EXTRA_MONTO to monto))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("movi-confirmar-aviso-$avisoId", ExistingWorkPolicy.KEEP, pedido)
    }

    companion object {
        const val ACCION_CONFIRMAR = "com.jvillada.movi.avisos.CONFIRMAR"
        const val EXTRA_AVISO = "aviso"
        const val EXTRA_CATEGORIA = "categoria"
        const val EXTRA_CUENTA = "cuenta"
        const val EXTRA_MONTO = "monto"

        /** El `PendingIntent` del botón «Confirmar» de [avisoId], con lo que la notificación [mostro]. */
        fun alConfirmar(context: Context, avisoId: String, mostro: LoQueSeMostro): PendingIntent {
            val intent = Intent(context, ReceptorDeLaNotificacion::class.java)
                .setAction(ACCION_CONFIRMAR)
                .putExtra(EXTRA_AVISO, avisoId)
                .putExtra(EXTRA_CATEGORIA, mostro.categoria)
                .putExtra(EXTRA_CUENTA, mostro.cuentaId)
                .putExtra(EXTRA_MONTO, mostro.monto)
            return PendingIntent.getBroadcast(
                context,
                avisoId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/**
 * Confirma el aviso por el camino de siempre ([confirmarElAvisoDesdeLaNotificacion]) y deja la
 * notificación en «Anotado ✓» (se cierra sola) o en «No se pudo, ábrelo en Movi». Sin reintentos:
 * si no hay red, el dueño ya está mirando la notificación y lo útil es decírselo ahora, no anotar
 * dentro de media hora algo que quizá ya resolvió en la app.
 */
class ConfirmarDesdeLaNotificacionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        val avisoId = inputData.getString(ReceptorDeLaNotificacion.EXTRA_AVISO) ?: return Result.failure()
        val mostro = LoQueSeMostro(
            categoria = inputData.getString(ReceptorDeLaNotificacion.EXTRA_CATEGORIA) ?: return Result.failure(),
            cuentaId = inputData.getString(ReceptorDeLaNotificacion.EXTRA_CUENTA) ?: return Result.failure(),
            monto = inputData.getLong(ReceptorDeLaNotificacion.EXTRA_MONTO, -1L).takeIf { it > 0 } ?: return Result.failure(),
        )
        val resultado = if (SessionManager.token.isNullOrBlank()) {
            ResultadoDeConfirmar.NoSePudo("sin sesión")
        } else {
            try {
                confirmarElAvisoDesdeLaNotificacion(avisoId, mostro)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Vuelta 100: lo atrapado también se reporta (sin cambiar lo que sigue); la red caída, no.
                com.jvillada.movi.fallos.CapturaDeFallos.reportarAtrapado(applicationContext, e, "ConfirmarDesdeLaNotificacionWorker")
                ResultadoDeConfirmar.NoSePudo("sin conexión")
            }
        }
        val estado = when (resultado) {
            ResultadoDeConfirmar.Anotado, ResultadoDeConfirmar.YaEstabaAnotado -> Avisador.EstadoDeLaConfirmacion.ANOTADO
            is ResultadoDeConfirmar.NoSePudo -> {
                Log.w(TAG, "no se pudo confirmar el aviso desde la notificación: ${resultado.porQue}")
                Avisador.EstadoDeLaConfirmacion.NO_SE_PUDO
            }
        }
        Avisador.mostrarConfirmacion(contexto, avisoId, estado)
        return Result.success()
    }

    private companion object {
        const val TAG = "movi"
    }
}
