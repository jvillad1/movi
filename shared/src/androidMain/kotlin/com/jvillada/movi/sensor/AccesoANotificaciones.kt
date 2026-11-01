package com.jvillada.movi.sensor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService

/*
 * El permiso de «acceso a las notificaciones» — el que habilita
 * `com.jvillada.movi.notificaciones.EscuchaDeNotificaciones` (:androidApp).
 *
 * No es un permiso de los normales: no existe un `requestPermissions` que lo pida, no hay diálogo
 * que se pueda lanzar desde la app y no aparece en la ficha de permisos de la app. Se concede a
 * mano, en una pantalla del sistema que lista TODAS las apps que lo piden. Por eso acá solo hay dos
 * cosas: leer si está concedido y abrir esa pantalla.
 */

/** La clave de `Settings.Secure` donde Android guarda los oyentes habilitados. */
private const val OYENTES_HABILITADOS = "enabled_notification_listeners"

/**
 * **¿Está este paquete en la lista de oyentes habilitados?**
 *
 * El valor de ajustes es una lista de `ComponentName` aplanados separados por `:`
 * (`com.una.app/com.una.app.Servicio:com.otra/...`). Se compara el paquete **completo y exacto**
 * contra la parte de antes de la barra: un `contains` daría por concedido a Movi porque otra app se
 * llame `com.jvillada.movi.falsa`, y eso pintaría «Concedido» sobre una captura que no existe —
 * exactamente la mentira que esta pantalla ya cometió una vez con «AUTO-LECTURA ACTIVA».
 *
 * Pura para poder probarla: el que lee los ajustes es [tieneAccesoANotificaciones].
 */
internal fun elPaqueteEscuchaNotificaciones(valorDeAjustes: String?, paquete: String): Boolean =
    valorDeAjustes.orEmpty()
        .split(':')
        .any { it.isNotBlank() && it.substringBefore('/').trim() == paquete }

/**
 * Se relee cada vez (no se cachea): el usuario lo concede y lo revoca FUERA de la app, y un
 * `true` viejo escondería una captura ya apagada.
 *
 * Igual que `rememberSmsCaptureReady`, un `true` acá **no** prueba que la captura funcione: prueba
 * que el sistema dejaría escuchar. Lo que prueba que anda es un mensaje que haya llegado
 * (`CapturaDeSms`, en `:core`).
 */
fun tieneAccesoANotificaciones(context: Context): Boolean = accesoANotificaciones(context) ?: false

/**
 * Vuelta 92 · Lo mismo, pero con «no sé»: `null` si la lectura de ajustes falló. La vigía lo usa
 * para no confundir una falla de lectura con un acceso revocado (ver `queHaceLaVigia`).
 */
fun accesoANotificaciones(context: Context): Boolean? = runCatching {
    elPaqueteEscuchaNotificaciones(
        Settings.Secure.getString(context.contentResolver, OYENTES_HABILITADOS),
        context.packageName,
    )
}.getOrNull()

/**
 * Abre «Acceso a las notificaciones» de los ajustes del sistema.
 *
 * Sin fallback a la ficha de la app, a diferencia de [openHibernationSettings]: ahí el interruptor
 * de notificaciones NO está, así que mandar al usuario a esa ficha sería mandarlo a buscar algo que
 * no va a encontrar. Si la acción no resuelve (ROM rara), es preferible que el botón no haga nada y
 * el texto de al lado siga diciendo dónde está la pantalla.
 */
fun abrirAjustesDeNotificaciones(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * El nombre de la clase del listener, que vive en `:androidApp` (`com.jvillada.movi.notificaciones.
 * EscuchaDeNotificaciones`) y por eso no se puede nombrar desde acá con `::class`. Es el mismo que
 * declara el manifiesto.
 */
const val CLASE_DE_LA_ESCUCHA = "com.jvillada.movi.notificaciones.EscuchaDeNotificaciones"

/**
 * Vuelta 92 · **Le pide a Android que vuelva a enchufar el listener** (`requestRebind`). Sirve
 * cuando el acceso sigue concedido pero el servicio quedó desconectado: después de una actualización
 * de la app, o porque el sistema lo mató. Si el acceso no está concedido no hace nada (Android lo
 * ignora), y nunca lanza: lo llaman un Worker y un botón.
 */
fun pedirQueReconecte(context: Context) {
    runCatching {
        NotificationListenerService.requestRebind(ComponentName(context.packageName, CLASE_DE_LA_ESCUCHA))
    }
}

/**
 * Vuelta 92 · ¿Está Movi exenta de la optimización de batería? Por debajo de Android 6 no existe, y
 * si la consulta falla se responde «exenta»: ante la duda, no ofrecer algo que no hace falta (el
 * mismo criterio que [isAutoRevokeExempt]).
 */
fun estaExentaDelAhorroDeBateria(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return runCatching {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }.getOrDefault(true)
}

/**
 * Vuelta 92 · **Pide la exención**: el diálogo del sistema («¿Permitir que Movi se ejecute siempre en
 * segundo plano?»), que necesita `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` en el manifiesto. Si la ROM
 * no lo resuelve, cae a la lista de optimización de batería, donde el dueño busca Movi. Solo lo llama
 * un botón: nunca se pide solo.
 */
fun pedirLaExencionDeBateria(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val directo = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val lista = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(directo) }
        .recoverCatching { context.startActivity(lista) }
}
