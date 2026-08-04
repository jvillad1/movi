package com.jvillada.movi.sensor

import android.content.Context
import android.os.Build

/** Android 11 (API 30) es donde aparece el auto-revoke; por debajo no existe hibernación. */
internal const val AUTO_REVOKE_MIN_SDK = Build.VERSION_CODES.R

/**
 * Único criterio para mostrar el aviso de hibernación.
 *
 * El APK es un sensor de "instalar y olvidar", que es justo el perfil que Android
 * hiberna: pasados unos meses sin abrirlo revoca sus permisos y lo force-stopea, y un
 * paquete force-stopeado no recibe NINGÚN SMS_RECEIVED hasta que alguien lo abra a mano.
 *
 * Se avisa solo cuando el aviso es cierto Y accionable: si el SO no tiene hibernación
 * (API < 30) o la app ya está exenta, no se muestra nada. Un aviso permanente que no se
 * puede apagar es peor que ningún aviso.
 */
internal fun shouldWarnAboutHibernation(sdkInt: Int, exempt: Boolean): Boolean =
    sdkInt >= AUTO_REVOKE_MIN_SDK && !exempt

/**
 * Lee del sistema si la app está exenta del auto-revoke.
 *
 * Por debajo de API 30 no hay hibernación, así que "exenta" es la respuesta honesta.
 * Si la consulta falla también devolvemos exenta: ante la duda, callar antes que
 * instalar un aviso permanente que el usuario no puede apagar.
 */
internal fun isAutoRevokeExempt(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < AUTO_REVOKE_MIN_SDK) return true
    return runCatching { context.packageManager.isAutoRevokeWhitelisted }.getOrDefault(true)
}
