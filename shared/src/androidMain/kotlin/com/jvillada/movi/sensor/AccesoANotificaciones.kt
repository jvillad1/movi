package com.jvillada.movi.sensor

import android.content.Context
import android.content.Intent
import android.provider.Settings

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
internal fun tieneAccesoANotificaciones(context: Context): Boolean = runCatching {
    elPaqueteEscuchaNotificaciones(
        Settings.Secure.getString(context.contentResolver, OYENTES_HABILITADOS),
        context.packageName,
    )
}.getOrDefault(false)

/**
 * Abre «Acceso a las notificaciones» de los ajustes del sistema.
 *
 * Sin fallback a la ficha de la app, a diferencia de [openHibernationSettings]: ahí el interruptor
 * de notificaciones NO está, así que mandar al usuario a esa ficha sería mandarlo a buscar algo que
 * no va a encontrar. Si la acción no resuelve (ROM rara), es preferible que el botón no haga nada y
 * el texto de al lado siga diciendo dónde está la pantalla.
 */
internal fun abrirAjustesDeNotificaciones(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
