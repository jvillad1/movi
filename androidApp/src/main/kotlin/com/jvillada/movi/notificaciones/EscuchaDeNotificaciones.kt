package com.jvillada.movi.notificaciones

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jvillada.movi.sms.ORIGEN_NOTIFICACION
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.SmsSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Captura los movimientos que las apps del banco publican como notificación de Android y los encola
 * para subir por el MISMO camino que los SMS (`SmsSyncWorker` → `POST /api/sms/sync`).
 *
 * Existe porque el banco dejó de mandar SMS y la tarjeta Glim nunca los mandó; el porqué completo,
 * y por qué la lista de apps no es un piso, están en el KDoc de
 * [com.jvillada.movi.notificaciones.AppsQueAvisan] (`:shared`).
 *
 * ## Lo que sale del teléfono, y lo que no
 *
 * Este servicio ve TODAS las notificaciones del aparato: WhatsApp, correo, el chat de la pareja.
 * Lo primero que hace [onNotificationPosted] —antes de tocar `notification`, antes de abrir los
 * `extras`, antes de anotar nada— es mirar el **paquete**. Si la app no está en la lista, retorna:
 * no se lee, no se cuenta, no se registra, no se sube. No hay un contador de descartadas ni un log
 * con su nombre a propósito: un contador es un dato sobre notificaciones ajenas, y la única forma
 * de que nunca se filtre es no producirlo.
 *
 * ## Nada de parsear acá
 *
 * El texto se sube crudo. Quién decide si eso es un movimiento, de cuánto y de qué categoría es
 * `SmsRoutes.parseSms`, en el server, que es donde ya se decide lo mismo para los SMS. Un segundo
 * parser en el teléfono se desincronizaría con el primero y habría que entregar un APK para
 * arreglarlo.
 */
class EscuchaDeNotificaciones : NotificationListenerService() {

    /**
     * El sistema acaba de enchufar el servicio: el usuario recién concedió el acceso, o el teléfono
     * arrancó. Es el momento exacto en que conviene traer la lista de apps del server —si el dueño
     * concede el permiso hoy y su app de banco se agregó ayer, sin esto el teléfono capturaría
     * recién cuando venza el TTL de 24 h.
     */
    override fun onListenerConnected() {
        SmsFilterConfigStore.refreshIfStale(applicationContext, force = true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notificacion = sbn ?: return
        val paquete = notificacion.packageName ?: return

        // ── La frontera de privacidad. Nada de lo de abajo corre para una app ajena. ──
        val apps = SmsFilterConfigStore.loadNotificationApps(applicationContext)
        if (!FiltroDeNotificaciones.laAppEstaEnLaLista(paquete, apps)) return

        val contenido = notificacion.notification ?: return
        val extras = contenido.extras
        val titulo = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // `bigText` primero: es el texto completo cuando la notificación viene expandida, y el
        // `text` corto de esa misma notificación suele venir recortado con «…», que le comería el
        // monto al parser.
        val texto = (
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()

        // postTime y no `Notification.when`: `when` puede venir en 0 (la app no lo pobló) y ahí el
        // mensaje quedaría fechado en 1970, fuera de cualquier período.
        val cuando = if (notificacion.postTime > 0L) notificacion.postTime else System.currentTimeMillis()

        val decision = decidirNotificacion(
            NotificacionEntrante(
                paquete = paquete,
                titulo = titulo,
                texto = texto,
                esPersistente = (contenido.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                esResumenDeGrupo = (contenido.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                cuando = cuando,
            ),
            apps,
        )
        if (decision !is DecisionDeNotificacion.Subir) return

        // Las apps de banco re-publican: actualizan la misma notificación, la reponen al
        // desbloquear, la repiten al abrir la app. Sin esta memoria, cada re-post sería una fila
        // más en la bandeja del dueño.
        val huella = huellaDeLaNotificacion(paquete, titulo, texto, cuando)
        if (!AlmacenDeNotificaciones.esNuevaYAnotala(applicationContext, huella)) return

        val id = idDeNotificacion(huella)
        val trabajo = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInputData(
                workDataOf(
                    "id" to id,
                    // El Worker escribe esto en el campo `bank` del wire, que es el rótulo que la
                    // bandeja pinta arriba del mensaje.
                    "sender" to marcaDeOrigen(etiquetaDe(paquete)),
                    "body" to decision.texto,
                    "ts" to cuando,
                    "origen" to ORIGEN_NOTIFICACION,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Único por id, igual que el camino de SMS: una re-entrega no encola dos veces.
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, trabajo)
    }

    /**
     * El nombre que el dueño ve en su lanzador («Bancolombia»), no el paquete
     * («com.todo1.mobile»). Si el PackageManager no lo resuelve —app desinstalada entre el post y
     * esto, o filtrado por visibilidad de paquetes— cae al paquete: peor de leer, pero cierto.
     */
    private fun etiquetaDe(paquete: String): String = runCatching {
        val pm = applicationContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: paquete
}
