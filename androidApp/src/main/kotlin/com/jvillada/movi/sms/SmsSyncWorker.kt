package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvillada.movi.avisos.Avisador
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.notificaciones.AlmacenDeNotificaciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Valor de la clave `origen` para una captura que vino de una notificación de una app. */
const val ORIGEN_NOTIFICACION = "notificacion"

/**
 * Vuelta 92 · `true` para una captura que sale del barrido de la bandeja al reconectarse el listener:
 * sube igual, pero no hace sonar «Movi anotó…» (serían varias seguidas de cosas que el banco ya
 * mostró). Solo decide el aviso local; el pedido al server es el mismo.
 */
const val CLAVE_SILENCIOSA = "silenciosa"

/**
 * Sube UN mensaje capturado al endpoint idempotente /api/sms/sync (ver SmsSync.kt,
 * compartido con el backfill de la pantalla).
 * IOException/5xx → retry con backoff: sin red la sesión sigue y el mensaje espera.
 * 401 → el server rechazó la sesión: se cierra (`SessionManager.seRechazoElToken`, que además
 * avisa UNA vez con una notificación), el mensaje queda en [CapturasPorSubir] y sube cuando el
 * dueño vuelve a entrar. Sin reintentos: con un token muerto, reintentar no arregla nada.
 * Sin token → si la sesión la cerró el server, también se guarda; si el dueño salió a mano, se
 * descarta como siempre.
 *
 * Lo usan los DOS sensores en tiempo real: `SmsRealtimeReceiver` (un SMS del banco) y
 * `EscuchaDeNotificaciones` (una notificación de una app del banco). Comparten Worker a propósito
 * —mismo payload, mismo auth, mismo manejo de códigos, misma política de reintento— y se
 * distinguen solo por la clave `origen` del inputData, que decide QUÉ marca de tiempo se toca al
 * subir con éxito. Esa marca no es cosmética: «última captura de SMS» es el único indicador de que
 * el receiver de SMS quedó mudo, y si una notificación la escribiera, la mudez del banco —el
 * defecto que la captura por notificaciones vino a tapar— quedaría invisible para siempre.
 */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val sender = inputData.getString("sender").orEmpty()
        val body = inputData.getString("body").orEmpty()
        val ts = inputData.getLong("ts", System.currentTimeMillis())
        val deNotificacion = inputData.getString("origen") == ORIGEN_NOTIFICACION

        fun guardarPara(dueno: String) = CapturasPorSubir.guardar(
            applicationContext,
            CapturasPorSubir.Captura(id, sender, body, ts, deNotificacion, dueno),
        )

        val token = SessionManager.token
        if (token.isNullOrBlank()) {
            val dueno = duenoDeLaCapturaSinSesion(SessionManager.sesionRechazadaDe)
            if (dueno == null) {
                Log.w(TAG, "sin sesión — descartando SMS capturado")
                return@withContext Result.failure()
            }
            Log.w(TAG, "la sesión la cerró el server — la captura queda guardada para cuando vuelva")
            guardarPara(dueno)
            return@withContext Result.success()
        }
        val dueno = SessionManager.userId.orEmpty()

        val payload = buildSmsSyncPayload(listOf(captureItem(id, sender, body, ts)))

        when (val result = postSmsSync(token, payload)) {
            is SmsSyncResult.Success -> {
                // Cada sensor escribe SU marca: ver el KDoc de la clase y el de
                // AlmacenDeNotificaciones. Mezclarlas escondería la mudez del otro.
                if (deNotificacion) {
                    AlmacenDeNotificaciones.marcarUltima(applicationContext)
                } else {
                    SmsFilterConfigStore.markLastCapture(applicationContext)
                }
                // La captura volvió a subir: si quedó una marca de 401 de un episodio
                // anterior, ya no describe la realidad — sin esto, la sección de captura
                // seguiría contando una pausa que terminó.
                SmsFilterConfigStore.clearAuthExpired(applicationContext)
                // Bloqueante a propósito: WorkManager puede dejar morir el proceso
                // apenas doWork retorna, así que un thread{} suelto no se ejecutaría.
                SmsFilterConfigStore.refreshIfStaleBlocking(applicationContext)
                // Ola 1 · Movi avisa: «Movi anotó $180.000», con lo que el server leyó y dejó en
                // «Por revisar» (nada de lo que entró ya resuelto). Best-effort: un aviso que no
                // sale no puede convertir un upload exitoso en reintento.
                if (!inputData.getBoolean(CLAVE_SILENCIOSA, false)) {
                    runCatching { Avisador.avisarMovimientos(applicationContext, result.porRevisar) }
                        .onFailure { Log.w(TAG, "no se pudo avisar el movimiento", it) }
                }
                // Vuelta 78: entró un aviso por fuera de la app (mismo proceso que la pantalla): lo
                // leído hace menos de 30 s ya no es fresco, y el Inicio vuelve a preguntar.
                com.jvillada.movi.data.olvidarLoFresco()
                // Vuelta 100: en la línea de tiempo del uso, que el teléfono capturó un aviso (solo el
                // origen, nunca el texto). Sube ya: el proceso puede morir apenas esto termina.
                com.jvillada.movi.observabilidad.Uso.evento(
                    com.jvillada.movi.shared.model.EventoDeUso.AVISO_CAPTURADO,
                    mapOf(com.jvillada.movi.shared.model.ClaveDeUso.ORIGEN to if (deNotificacion) "NOTIFICACION" else "SMS"),
                )
                runCatching { com.jvillada.movi.observabilidad.Uso.enviar() }
                Result.success()
            }
            SmsSyncResult.Unauthorized -> {
                // El 26-oct (JWT_SECRET rotado) esto reintentaba y terminaba en failure(): la
                // notificación del banco se perdía y nadie se enteraba. Un 401 de Movi es
                // definitivo (ver `laSesionTras`): se cierra la sesión —lo que avisa UNA vez,
                // con la notificación de `MoviAplicacion`— y la captura espera a su dueño.
                Log.w(TAG, "sync 401 — el server rechazó la sesión; la captura queda guardada")
                SmsFilterConfigStore.markAuthExpired(applicationContext)
                SessionManager.seRechazoElToken(token)
                if (SessionManager.isLoggedIn) {
                    // Entró de nuevo mientras esto subía: el reintento sale con el token nuevo.
                    Result.retry()
                } else {
                    guardarPara(dueno)
                    Result.success()
                }
            }
            is SmsSyncResult.ServerError -> Result.retry()
            SmsSyncResult.Network -> Result.retry()
            is SmsSyncResult.Rejected -> {
                Log.w(TAG, "sync rechazado con ${result.code} — sin retry")
                Result.failure()
            }
        }
    }

    private companion object {
        const val TAG = "movi"
    }
}
