package com.jvillada.movi.sensor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jvillada.movi.sms.SmsFilterConfigStore

/*
 * Soporte de la sección «Captura de SMS» (ui/sms/SmsSensorSetupSection.android.kt).
 * Vino de la SensorScreen del APK sensor: cuando la app completa pasó a correr en el
 * teléfono, la pantalla propia del sensor (con su login redundante) desapareció y estas
 * piezas — chequeo de permisos, rutas a ajustes, re-chequeo al volver — se mudaron acá.
 */

/**
 * Recuerda que YA pedimos los permisos en la app: distingue "nunca preguntó" de "denegó".
 *
 * Es un hecho de ESTA instalación, así que el archivo de prefs entero queda excluido de
 * Auto Backup (ver androidApp res/xml/backup_rules.xml): restaurado en un teléfono nuevo
 * diría que ya preguntamos cuando nunca lo hicimos, y esconde el botón que sí funcionaría ahí.
 */
private const val KEY_PERM_REQUESTED = "perm_requested"

internal val SmsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

internal fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

internal fun hasSmsPermissions(context: Context): Boolean = SmsPermissions.all { hasPermission(context, it) }

/** El backfill lee el inbox: necesita READ_SMS, que el camino en tiempo real no usa. */
internal fun hasReadSmsPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.READ_SMS)

internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

internal fun canShowRationaleFor(activity: Activity?, permission: String): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

internal fun canShowRationale(activity: Activity?): Boolean =
    SmsPermissions.any { canShowRationaleFor(activity, it) }

/**
 * Re-chequeo al volver a la pantalla. Los permisos concedidos desde los ajustes del
 * sistema no llegan por el launcher: sin esto las tarjetas seguirían diciendo "Faltan"
 * hasta reiniciar la app.
 */
@Composable
internal fun OnResume(activity: ComponentActivity?, onResume: () -> Unit) {
    val current by rememberUpdatedState(onResume)
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}

/**
 * Decide entre pedir el permiso en la app y mandar a los ajustes del sistema.
 *
 * En Android 11+ tras una denegación (13+ tras dos), `requestPermissions` es un no-op
 * silencioso: el botón no haría nada y el sensor quedaría mudo sin salida desde la app.
 * Si ya preguntamos y el sistema ya no deja mostrar el diálogo, el único camino es
 * ACTION_APPLICATION_DETAILS_SETTINGS.
 */
internal fun shouldOpenSettings(askedBefore: Boolean, canShowRationale: Boolean): Boolean =
    askedBefore && !canShowRationale

internal fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Lleva a la pantalla donde se apaga la pausa por inactividad.
 *
 * Verificado en API 35 (AOSP): ACTION_AUTO_REVOKE_PERMISSIONS resuelve a
 * com.android.settings InstalledAppDetails, o sea la ficha "Información de la app" con el
 * interruptor de pausa. Si no resolviera (ROM sin esa Activity, filtrado por visibilidad
 * de paquetes), caemos a los ajustes de la app, que es el mismo destino por otro camino.
 */
internal fun openHibernationSettings(context: Context) {
    val intent = Intent(
        Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Sin precheck de resolveActivity a propósito: con targetSdk 35 y sin <queries>, el
    // filtrado de visibilidad de paquetes puede devolver null aunque la Activity exista,
    // y nos sacaría del intent específico sin motivo. startActivity NO sufre ese filtrado
    // (resuelve en system_server), y runCatching ya cubre el único fallo real.
    runCatching { context.startActivity(intent) }
        .onFailure { openAppSettings(context) }
}

internal fun readPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PERM_REQUESTED, false)

internal fun markPermissionAsked(context: Context) {
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PERM_REQUESTED, true).apply()
}
