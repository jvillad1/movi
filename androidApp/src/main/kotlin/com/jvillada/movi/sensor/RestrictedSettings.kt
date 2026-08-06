package com.jvillada.movi.sensor

import android.content.Context
import android.os.Build

/**
 * Ajustes restringidos (Android 13+).
 *
 * Cuando la app NO viene de una tienda, el sistema se niega a conceder ciertos permisos
 * sensibles (SMS, accesibilidad) por el diálogo normal: `requestPermissions` vuelve
 * denegado sin mostrar nada y el sistema muestra su propio aviso. El desbloqueo está
 * escondido en Ajustes → Aplicaciones → Movi → menú de tres puntos → "Permitir ajustes
 * restringidos", y NO existe API ni intent para activarlo desde la app: ese es el punto
 * del mecanismo. Lo único que podemos hacer es reconocerlo y decirlo.
 *
 * El problema es que desde el lado de la app ese bloqueo es idéntico a "el usuario denegó
 * para siempre": denegado + `shouldShowRequestPermissionRationale` en false en los dos
 * casos. Por eso el veredicto combina varias señales en vez de mirar una sola.
 */

/** Los ajustes restringidos aparecen en Android 13. minSdk del APK es 24. */
internal const val RESTRICTED_SETTINGS_MIN_SDK = Build.VERSION_CODES.TIRAMISU

/** `getInstallSourceInfo` existe desde Android 11. */
internal const val INSTALL_SOURCE_MIN_SDK = Build.VERSION_CODES.R

// Constantes de PackageInstaller.PACKAGE_SOURCE_* (API 33). Se copian a mano para que la
// clasificación sea una función pura testeable en la JVM y para no arrastrar un acceso a
// API nueva dentro de código que corre en API 24.
internal const val PACKAGE_SOURCE_UNSPECIFIED = 0
internal const val PACKAGE_SOURCE_STORE = 1
internal const val PACKAGE_SOURCE_LOCAL_FILE = 2
internal const val PACKAGE_SOURCE_DOWNLOADED_FILE = 3
internal const val PACKAGE_SOURCE_OTHER = 4

/** Tiendas conocidas: instalar desde acá NO activa ajustes restringidos. */
private val StoreInstallers = setOf(
    "com.android.vending",
    "com.sec.android.app.samsungapps",
    "com.huawei.appmarket",
    "com.amazon.venezia",
    "com.xiaomi.mipicks",
)

/** Instaladores de paquetes locales: instalar desde acá SÍ es un sideload. */
private val SideloadInstallers = setOf(
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.shell", // `adb install`
    "com.google.android.packageinstaller.permission",
)

internal enum class InstallSource {
    /** Vino de una tienda: los ajustes restringidos no aplican. */
    STORE,

    /** Se instaló por archivo / adb / instalador del sistema: los ajustes restringidos aplican. */
    SIDELOADED,

    /** No lo pudimos determinar. Nunca acusamos a Android con esta respuesta. */
    UNKNOWN,
}

internal enum class SmsPermissionVerdict {
    GRANTED,

    /** El diálogo del sistema todavía sirve: pedir el permiso dentro de la app. */
    ASK_IN_APP,

    /** Android negó el permiso sin preguntar porque el APK no vino de una tienda. */
    BLOCKED_BY_RESTRICTED_SETTINGS,

    /** Denegación común: el usuario vio el diálogo y dijo que no. Copy de siempre. */
    DENIED,
}

/**
 * Único criterio para culpar a Android del permiso faltante.
 *
 * Cómo falla ante la duda: hacia [SmsPermissionVerdict.DENIED], o sea el estado y el copy
 * que ya existían. Un "Android bloqueó esto" falso manda al usuario a buscar un menú que
 * no le va a resolver nada, y eso es peor que el mensaje genérico. Por eso hacen falta
 * TODAS las señales a la vez: API >= 33, instalación clasificada como sideload (UNKNOWN
 * no alcanza), ya haber pedido el permiso, y que el diálogo del sistema no se haya
 * mostrado NUNCA. Esa última es la que separa el bloqueo de una denegación real: el
 * camino de la denegación pasa obligatoriamente por `shouldShowRequestPermissionRationale
 * == true` (primera negativa) antes de llegar a false (negativa definitiva), mientras que
 * el bloqueo por ajustes restringidos nace en false y nunca pasa por true.
 */
internal fun smsPermissionVerdict(
    sdkInt: Int,
    installSource: InstallSource,
    askedBefore: Boolean,
    dialogEverShown: Boolean,
    granted: Boolean,
    canShowRationale: Boolean,
): SmsPermissionVerdict = when {
    granted -> SmsPermissionVerdict.GRANTED
    // Si nunca pedimos, no hay denegación de nadie; si el sistema todavía deja mostrar el
    // diálogo, el camino sigue siendo pedirlo dentro de la app.
    !askedBefore || canShowRationale -> SmsPermissionVerdict.ASK_IN_APP
    sdkInt >= RESTRICTED_SETTINGS_MIN_SDK &&
        installSource == InstallSource.SIDELOADED &&
        !dialogEverShown -> SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS
    else -> SmsPermissionVerdict.DENIED
}

/**
 * Traduce lo que reporta el sistema sobre el origen de la instalación.
 *
 * [packageSource] es `InstallSourceInfo.getPackageSource()` (API 33+, null si no está
 * disponible) y manda cuando dice algo concreto; si no, se cae al nombre del paquete
 * instalador. Un instalador que no reconocemos queda en UNKNOWN a propósito.
 *
 * Que packageSource gane no es una preferencia estética. Verificado en API 35 (AOSP): un
 * `adb install` reporta packageSource=1 (STORE) con installingPackageName=null, y el
 * sistema le pone a los permisos el flag RESTRICTION_INSTALLER_EXEMPT — o sea, ESE
 * install está exento de ajustes restringidos. Si mirásemos solo el nombre (null ⇒
 * sideload) acusaríamos a Android en instalaciones que el propio Android no restringe.
 */
internal fun classifyInstallSource(packageSource: Int?, installingPackageName: String?): InstallSource =
    when (packageSource) {
        PACKAGE_SOURCE_STORE -> InstallSource.STORE
        PACKAGE_SOURCE_LOCAL_FILE, PACKAGE_SOURCE_DOWNLOADED_FILE -> InstallSource.SIDELOADED
        else -> when {
            installingPackageName.isNullOrBlank() -> InstallSource.SIDELOADED
            installingPackageName in StoreInstallers -> InstallSource.STORE
            installingPackageName in SideloadInstallers -> InstallSource.SIDELOADED
            else -> InstallSource.UNKNOWN
        }
    }

/**
 * Consulta al sistema de dónde salió esta instalación.
 *
 * Por debajo de API 30 no existe `getInstallSourceInfo` y devolvemos UNKNOWN — da igual,
 * los ajustes restringidos empiezan en 33. Cualquier excepción también cae en UNKNOWN:
 * ante la duda, el veredicto se queda con el copy genérico.
 */
internal fun readInstallSource(context: Context): InstallSource {
    if (Build.VERSION.SDK_INT < INSTALL_SOURCE_MIN_SDK) return InstallSource.UNKNOWN
    return runCatching {
        val info = context.packageManager.getInstallSourceInfo(context.packageName)
        val packageSource =
            if (Build.VERSION.SDK_INT >= RESTRICTED_SETTINGS_MIN_SDK) info.packageSource else null
        classifyInstallSource(packageSource, info.installingPackageName)
    }.getOrDefault(InstallSource.UNKNOWN)
}
