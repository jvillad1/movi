package com.jvillada.movi.sensor

import android.content.Context
import android.os.Build

/**
 * Ajustes restringidos.
 *
 * Cuando la app NO viene de una tienda, el sistema se niega a conceder ciertos permisos
 * sensibles por el diálogo normal: `requestPermissions` vuelve denegado sin mostrar nada
 * y el interruptor queda gris en la ficha de la app. El desbloqueo está escondido en
 * Ajustes → Aplicaciones → Movi → menú de tres puntos → "Permitir ajustes restringidos",
 * y NO existe API ni intent para activarlo desde la app: ese es el punto del mecanismo.
 * Lo único que podemos hacer es reconocerlo y decirlo.
 *
 * El problema es que desde el lado de la app ese bloqueo es idéntico a "el usuario denegó
 * para siempre": denegado + `shouldShowRequestPermissionRationale` en false en los dos
 * casos, y ninguna API expone la diferencia. Se intentó deducirla (origen de la
 * instalación + cuánto tardó la petición) y no se sostiene: en el AVD de prueba el bloqueo
 * silencioso tardó 1,4–1,6 s porque el controlador de permisos arranca en frío, o sea más
 * que el diálogo real, así que cualquier umbral que separe los dos casos acusa en falso a
 * alguien. Por eso acá NO se afirma nada: lo único que se hace es nombrar la posibilidad
 * donde el mecanismo puede aplicar (ver [shouldHintRestrictedSettings]). Un condicional no
 * puede estar equivocado, apenas puede ser irrelevante.
 */

/**
 * En qué versión de Android el permiso de SMS entra en ajustes restringidos.
 *
 * NO es 33. Los ajustes restringidos de Android 13 cubren accesibilidad y escucha de
 * notificaciones — dos accesos especiales que se activan desde Ajustes, no permisos de
 * ejecución. El permiso de SMS entra recién en Android 15 (API 35), cuando el "modo de
 * confirmación ampliada" extiende el mecanismo a permisos de ejecución (SMS), a roles
 * (SMS y teléfono por defecto) y a otros accesos especiales (admin de dispositivo,
 * superposición, acceso de uso). Verificado contra la documentación pública del cambio
 * de Android 15 y contra los reportes del comportamiento real: en 13 y 14 un APK
 * sideloaded sigue recibiendo el diálogo de SMS normal. Poner 33 acá haría que el aviso
 * saliera en Android 13 y 14, donde el mecanismo no puede aplicarle al permiso de SMS.
 *
 * minSdk del APK es 24.
 */
internal const val RESTRICTED_SETTINGS_MIN_SDK = Build.VERSION_CODES.VANILLA_ICE_CREAM

/** `InstallSourceInfo.getPackageSource()` existe desde Android 13. */
internal const val PACKAGE_SOURCE_MIN_SDK = Build.VERSION_CODES.TIRAMISU

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
    // Un `adb install` es un sideload en el sentido llano, así que el clasificador lo dice.
    // Ojo: en la práctica no se llega acá desde API 33+, porque ahí packageSource está
    // presente y —medido en API 35— un `adb install` reporta STORE (Android lo exime), y
    // packageSource gana antes de que se mire el nombre.
    "com.android.shell",
)
// Fuera a propósito: "com.google.android.packageinstaller.permission" no es un paquete real
// —el controlador de permisos es com.google.android.permissioncontroller— así que nunca
// puede figurar como instalador.

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

    /**
     * El permiso falta y el diálogo ya no sale. Puede ser una denegación del usuario o el
     * bloqueo por ajustes restringidos: desde acá no se distinguen, y el copy no elige.
     */
    DENIED,
}

/**
 * En qué estado está el permiso de SMS, en función de lo que el sistema deja ver.
 *
 * No intenta atribuir la falta del permiso a nadie — no se puede, ver el encabezado de
 * este archivo. Solo separa los tres casos que tienen acciones distintas: no falta nada,
 * todavía sirve pedirlo en la app, o el único camino que queda son los ajustes.
 */
internal fun smsPermissionVerdict(
    askedBefore: Boolean,
    granted: Boolean,
    canShowRationale: Boolean,
): SmsPermissionVerdict = when {
    granted -> SmsPermissionVerdict.GRANTED
    // Si nunca pedimos, no hay denegación de nadie; si el sistema todavía deja mostrar el
    // diálogo, el camino sigue siendo pedirlo dentro de la app.
    !askedBefore || canShowRationale -> SmsPermissionVerdict.ASK_IN_APP
    else -> SmsPermissionVerdict.DENIED
}

/**
 * ¿Corresponde nombrar los ajustes restringidos como POSIBILIDAD, sin afirmar nada?
 *
 * El estado que motiva esto: el permiso ya está denegado y el rationale en false. Ahí el
 * copy genérico manda a conceder el permiso en la ficha de la app — y si el bloqueo es
 * real, ese interruptor está gris y el usuario se queda sin explicación ni salida.
 *
 * La respuesta no es adivinar el veredicto, sino nombrar la condición: "SI el interruptor
 * aparece gris, es por esto y se arregla así". No afirma que Android bloqueó nada, así que
 * no es la acusación con otro nombre.
 *
 * Por qué UNKNOWN también pasa: excluirlo tenía sentido cuando este gate alimentaba una
 * ACUSACIÓN — un origen que no sabemos clasificar no puede sostener "Android te bloqueó".
 * Para una condicional no aplica: bajo UNKNOWN el antecedente sigue protegiendo la frase y
 * la causa no se sabe falsa. STORE es el único valor donde la pista sería afirmativamente
 * incorrecta, porque una instalación de tienda no puede tener ese interruptor gris por esta
 * razón. Y la exclusión importa en concreto: el APK llega por Drive, y si el sistema anota
 * a Drive como instalador sin `packageSource` utilizable, el origen queda UNKNOWN — o sea
 * que dejarlo afuera silenciaba la pista justo en el teléfono para el que se escribió.
 */
internal fun shouldHintRestrictedSettings(sdkInt: Int, installSource: InstallSource): Boolean =
    sdkInt >= RESTRICTED_SETTINGS_MIN_SDK && installSource != InstallSource.STORE

/**
 * Traduce lo que reporta el sistema sobre el origen de la instalación.
 *
 * [packageSource] es `InstallSourceInfo.getPackageSource()` (API 33+, null si no está
 * disponible) y manda cuando dice algo concreto; si no, se cae al nombre del paquete
 * instalador. Un instalador que no reconocemos — y la ausencia total de instalador —
 * quedan en UNKNOWN a propósito.
 *
 * Que packageSource gane no es una preferencia estética. Verificado en API 35 (AOSP): un
 * `adb install` reporta packageSource=1 (STORE) con installingPackageName=null, y el
 * sistema le pone a los permisos el flag RESTRICTION_INSTALLER_EXEMPT — o sea, ESE
 * install está exento de ajustes restringidos. Si mirásemos solo el nombre (null ⇒
 * sideload) acusaríamos a Android en instalaciones que el propio Android no restringe.
 *
 * Por eso, sin packageSource utilizable, un instalador nulo o vacío tampoco puede ser
 * sideload: es exactamente la ambigüedad que el párrafo anterior describe, y la regla de
 * la casa es que ante la duda gana el copy genérico.
 */
internal fun classifyInstallSource(packageSource: Int?, installingPackageName: String?): InstallSource =
    when (packageSource) {
        PACKAGE_SOURCE_STORE -> InstallSource.STORE
        PACKAGE_SOURCE_LOCAL_FILE, PACKAGE_SOURCE_DOWNLOADED_FILE -> InstallSource.SIDELOADED
        else -> when {
            installingPackageName.isNullOrBlank() -> InstallSource.UNKNOWN
            installingPackageName in StoreInstallers -> InstallSource.STORE
            installingPackageName in SideloadInstallers -> InstallSource.SIDELOADED
            else -> InstallSource.UNKNOWN
        }
    }

/**
 * Consulta al sistema de dónde salió esta instalación.
 *
 * Por debajo de API 30 no existe `getInstallSourceInfo` y devolvemos UNKNOWN — da igual,
 * el permiso de SMS entra en ajustes restringidos recién en API 35, donde packageSource
 * siempre está disponible. Cualquier excepción también cae en UNKNOWN: ante la duda, el
 * veredicto se queda con el copy genérico.
 */
internal fun readInstallSource(context: Context): InstallSource {
    if (Build.VERSION.SDK_INT < INSTALL_SOURCE_MIN_SDK) return InstallSource.UNKNOWN
    return runCatching {
        val info = context.packageManager.getInstallSourceInfo(context.packageName)
        val packageSource =
            if (Build.VERSION.SDK_INT >= PACKAGE_SOURCE_MIN_SDK) info.packageSource else null
        classifyInstallSource(packageSource, info.installingPackageName)
    }.getOrDefault(InstallSource.UNKNOWN)
}
