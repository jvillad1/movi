package com.jvillada.movi.notificaciones

import android.app.Notification
import android.os.Bundle
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
 * ## Cómo se prueba a mano, sin esperar a que el banco cobre
 *
 * `com.android.shell` puede estar en la lista de apps que sirve el server (ver
 * `SmsFilterConfigRoutes`) justamente para poder publicar una notificación de prueba desde adb.
 * **Las comillas van dos veces**, y eso no es un detalle:
 *
 * ```
 * adb shell "cmd notification post -S bigtext -t 'Bancolombia' PruebaMovi \
 *   'Bancolombia: Compraste \$12.345,00 en PRUEBA DE MOVI con tu T.Deb *4057, el 21/09/2026 a las 14:00.'"
 * ```
 *
 * Con un solo nivel de comillas, `adb shell` junta los argumentos y el shell DEL TELÉFONO los vuelve
 * a partir por los espacios: la notificación se publica con el cuerpo «Bancolombia:» y el resto se
 * pierde (el `\$12` además se lo come como variable). Así se midió la prueba del 21-sep —la fila que
 * llegó al server decía «Bancolombia:» y nada más— y por un rato pareció que Movi estaba tirando el
 * cuerpo. No: la app subió fielmente lo único que la notificación traía.
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
        // De qué extra sale cada cosa, y por qué en ese orden: [cuerpoDeLaNotificacion] en `:shared`.
        val datos = loQueMoviMiraDe(contenido.extras)
        val titulo = tituloDeLaNotificacion(datos)
        val texto = cuerpoDeLaNotificacion(datos)

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
        // Una descartada termina acá: no se encola, no se sube y —porque la marca de «última
        // captura» la escribe el Worker recién cuando el POST sale bien— tampoco cuenta como una
        // captura. Un título sin cuerpo no puede volverse un movimiento: subirlo dejaría una fila
        // imparseable en la bandeja Y haría decir a la pantalla que el sensor anda.
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
     * **Los extras de la notificación, pasados a mapa plano**, para que la regla de qué se lee y en
     * qué orden viva en una función pura que se prueba sin Android.
     *
     * Se copian SOLO las llaves de las que puede salir el título o el cuerpo, nunca el bundle
     * entero: lo que no se copia no se puede subir por accidente, y una notificación trae además
     * `PendingIntent`s y `Bitmap`s que no son texto de nadie.
     *
     * `getCharSequence` devuelve `null` si la llave no está o si trae otra cosa —ahí la llave
     * simplemente no entra al mapa— y el `runCatching` cubre el bundle que no se puede
     * desempaquetar: una excepción en `onNotificationPosted` tumba al listener, y con él TODAS las
     * notificaciones que vengan después.
     */
    private fun loQueMoviMiraDe(extras: Bundle?): Map<String, Any?> {
        val bundle = extras ?: return emptyMap()
        return runCatching {
            buildMap<String, Any?> {
                LLAVES_DE_TEXTO.forEach { llave -> bundle.getCharSequence(llave)?.let { put(llave, it) } }
                // Las líneas de una `InboxStyle` no son un CharSequence sino un arreglo de ellos.
                bundle.getCharSequenceArray(LlavesDeNotificacion.LINEAS)
                    ?.let { put(LlavesDeNotificacion.LINEAS, it) }
            }
        }.getOrDefault(emptyMap())
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

    private companion object {
        /** Las llaves de texto suelto. [LlavesDeNotificacion.LINEAS] se lee aparte: es un arreglo. */
        val LLAVES_DE_TEXTO = listOf(
            LlavesDeNotificacion.TITULO,
            LlavesDeNotificacion.TITULO_GRANDE,
            LlavesDeNotificacion.TEXTO,
            LlavesDeNotificacion.TEXTO_GRANDE,
            LlavesDeNotificacion.RESUMEN,
        )
    }
}
