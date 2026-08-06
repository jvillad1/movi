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
 * casos. Por eso el veredicto combina varias señales en vez de mirar una sola.
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
 * sideloaded sigue recibiendo el diálogo de SMS normal. Poner 33 acá haría que TODO
 * veredicto de bloqueo en un Android 13 o 14 fuera falso por construcción.
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
 * TODAS las señales a la vez: API >= [RESTRICTED_SETTINGS_MIN_SDK], instalación
 * clasificada como sideload (UNKNOWN no alcanza), ya haber pedido el permiso, y una
 * petición observada que se haya comportado como el bloqueo.
 *
 * [blockLikeRequestSeen] es la que separa el bloqueo de una denegación real, y es una
 * afirmación POSITIVA: esta versión pidió el permiso y la respuesta volvió sin diálogo
 * (ver [requestLooksBlocked]). La versión anterior preguntaba lo contrario — "¿nunca vimos
 * un diálogo?" — y tratar la ausencia de observación como prueba acusaba a Android en toda
 * instalación de la que no tuviéramos historia (una actualización desde una versión que no
 * anotaba nada, por ejemplo). No observar nada no prueba nada: sin una petición que mirar,
 * el veredicto es DENIED.
 *
 * A diferencia de un latch, la señal se puede desmentir: cualquier prueba de
 * [sawPermissionDialog] la borra, así que una medición desafortunada se corrige sola en el
 * siguiente intento en vez de quedar clavada para siempre.
 */
internal fun smsPermissionVerdict(
    sdkInt: Int,
    installSource: InstallSource,
    askedBefore: Boolean,
    blockLikeRequestSeen: Boolean,
    granted: Boolean,
    canShowRationale: Boolean,
): SmsPermissionVerdict = when {
    granted -> SmsPermissionVerdict.GRANTED
    // Si nunca pedimos, no hay denegación de nadie; si el sistema todavía deja mostrar el
    // diálogo, el camino sigue siendo pedirlo dentro de la app.
    !askedBefore || canShowRationale -> SmsPermissionVerdict.ASK_IN_APP
    sdkInt >= RESTRICTED_SETTINGS_MIN_SDK &&
        installSource == InstallSource.SIDELOADED &&
        blockLikeRequestSeen -> SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS
    else -> SmsPermissionVerdict.DENIED
}

/**
 * ¿Corresponde nombrar los ajustes restringidos como POSIBILIDAD, sin afirmar nada?
 *
 * Hay un estado que no se puede resolver ni con la señal positiva ni con ninguna otra: una
 * instalación de la que no tenemos historia (venía de una versión que no anotaba señales) y
 * cuyo permiso ya está denegado con el rationale en false. Ahí el copy genérico manda a
 * conceder el permiso en la ficha de la app — y si el bloqueo es real, ese interruptor está
 * gris y el usuario se queda sin explicación ni salida.
 *
 * La respuesta no es adivinar el veredicto, sino nombrar la condición: "SI el interruptor
 * aparece gris, es por esto y se arregla así". No afirma que Android bloqueó nada, así que
 * no es la acusación con otro nombre; solo aparece donde el bloqueo es posible (API 35+ y
 * sideload), porque en cualquier otro lado sería ruido.
 */
internal fun shouldHintRestrictedSettings(sdkInt: Int, installSource: InstallSource): Boolean =
    sdkInt >= RESTRICTED_SETTINGS_MIN_SDK && installSource == InstallSource.SIDELOADED

/**
 * Piso de duración para creer que el sistema llegó a dibujar el diálogo de permisos.
 *
 * Medio segundo. De un lado, el bloqueo por ajustes restringidos no abre ninguna ventana:
 * el sistema resuelve la denegación y devuelve el resultado en el orden de los
 * milisegundos, y aun despertando en frío al controlador de permisos ese ida y vuelta no
 * llega ni cerca de 500 ms. Del otro, para que un humano cancele con Atrás el diálogo
 * tiene que animarse hacia adentro (~250 ms) y recién ahí la persona puede verlo y
 * reaccionar (~250 ms de reacción visual en el mejor caso, con la mano ya apoyada).
 *
 * El umbral es a propósito BAJO y no alto: "tardó ≥ 500 ms" se interpreta como "el diálogo
 * se mostró", que apaga la acusación y deja el copy genérico. Si el número quedó corto, el
 * costo es perder el mensaje de ajustes restringidos en un intento de un teléfono lento; si
 * quedara largo, el costo sería acusar a Android de un bloqueo que no ocurrió. Solo el
 * primero es aceptable. No subirlo.
 *
 * Ese costo además ya no es permanente: la señal que sostiene la acusación se recalcula en
 * cada petición, así que un arranque en frío desafortunado lo pierde una vez y el siguiente
 * intento lo recupera.
 */
internal const val DIALOG_SHOWN_MIN_ELAPSED_MS = 500L

/**
 * ¿Lo que tardó la petición dice que hubo diálogo?
 *
 * Los tiempos van en reloj monotónico (`SystemClock.elapsedRealtime`): el reloj de pared
 * puede saltar hacia atrás por NTP o por el usuario justo mientras el diálogo está abierto
 * y produciría una duración negativa o absurda.
 *
 * Sin una medición utilizable (inicio perdido porque el proceso se recreó con el diálogo
 * abierto, o un fin anterior al inicio) devuelve true: no medimos nada, y ante la duda la
 * respuesta segura es "sí se mostró", que desactiva la acusación.
 */
internal fun requestDurationSaysDialogShown(startedAtMs: Long, endedAtMs: Long): Boolean {
    if (startedAtMs <= 0L || endedAtMs < startedAtMs) return true
    return endedAtMs - startedAtMs >= DIALOG_SHOWN_MIN_ELAPSED_MS
}

/**
 * Pruebas de que el diálogo del sistema SÍ llegó a mostrarse. Cualquiera alcanza.
 *
 * - [canShowRationale]: solo se pone en true después de que el usuario vio el diálogo y
 *   dijo que no. El bloqueo por ajustes restringidos jamás lo produce.
 * - [granted]: tener el permiso prueba que esta instalación NO está restringida. Es una
 *   afirmación más débil que "el diálogo funcionaba" — el permiso pudo concederse desde los
 *   ajustes del sistema sin que ningún diálogo saliera nunca — pero es exactamente la que
 *   el veredicto necesita: una instalación restringida no puede sostener el permiso por
 *   ningún camino, ni siquiera desde los ajustes. Importa incluso cuando después vuelve a
 *   faltar — la hibernación revoca permisos concedidos sin pasar nunca por rationale true,
 *   y sin esta prueba ese estado quedaría indistinguible de un bloqueo (ver Hibernation.kt:
 *   a esta app le pasa, no es hipotético).
 * - [requestDurationSaysDialogShown]: el usuario pudo cancelar el diálogo con Atrás o
 *   tocando afuera, lo que no toca ninguna de las dos señales anteriores. Lo que sí deja
 *   es tiempo transcurrido.
 *
 * [requestDurationSaysDialogShown] se pasa ya evaluado en vez de los timestamps crudos
 * porque hay relecturas (volver a la pantalla) donde no hubo ninguna petición que medir.
 */
internal fun sawPermissionDialog(
    canShowRationale: Boolean,
    granted: Boolean,
    requestWasSlow: Boolean = false,
): Boolean = canShowRationale || granted || requestWasSlow

/**
 * ¿Esta petición concreta se comportó como el bloqueo por ajustes restringidos?
 *
 * Es la única forma de anotar la señal que habilita la acusación, y por eso exige una
 * petición: no hay manera de responder que sí "sin querer". Un bloqueo devuelve denegado,
 * sin ganar rationale y sin haber dibujado ninguna ventana — o sea, la negación exacta de
 * [sawPermissionDialog]. Cualquier prueba de que el diálogo sí salió da false y borra la
 * marca anterior, así que la señal se puede desmentir tantas veces como haga falta.
 *
 * Ojo con lo que NO prueba: que la respuesta llegue instantáneamente solo dice que no hubo
 * ventana. Quién puede llamar a esto y en qué estado es lo que le da sentido — se anota al
 * volver de una petición real, nunca al releer la pantalla.
 */
internal fun requestLooksBlocked(
    canShowRationale: Boolean,
    granted: Boolean,
    requestWasSlow: Boolean,
): Boolean = !sawPermissionDialog(canShowRationale, granted, requestWasSlow)

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
