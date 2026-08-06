package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El caso que motiva todo esto: en Android 15+ una app instalada fuera de una tienda no
 * puede recibir el permiso de SMS por el diálogo normal. `requestPermissions` devuelve
 * denegado SIN mostrar nada y `shouldShowRequestPermissionRationale` sigue en false —
 * exactamente lo mismo que ve la app cuando el usuario denegó para siempre. Estos tests
 * fijan cuándo tenemos derecho a decir "esto lo bloqueó Android".
 */
class SmsPermissionVerdictTest {

    private fun verdict(
        sdkInt: Int = 35,
        installSource: InstallSource = InstallSource.SIDELOADED,
        askedBefore: Boolean = true,
        blockLikeRequestSeen: Boolean = true,
        granted: Boolean = false,
        canShowRationale: Boolean = false,
    ) = smsPermissionVerdict(sdkInt, installSource, askedBefore, blockLikeRequestSeen, granted, canShowRationale)

    @Test
    fun `a sideloaded denial with a request that never drew a dialog is the system blocking it`() {
        assertEquals(SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS, verdict())
    }

    @Test
    fun `without a request that behaved like the block there is nothing to blame Android for`() {
        // La regla entera, en una línea: la acusación necesita una PRUEBA POSITIVA. No
        // haber visto un diálogo no es una; solo lo es haber visto una petición volver sin
        // ninguno. Todo lo demás de este caso es idéntico al de arriba.
        assertEquals(SmsPermissionVerdict.DENIED, verdict(blockLikeRequestSeen = false))
    }

    @Test
    fun `an ordinary first denial keeps asking in-app`() {
        // El diálogo SÍ salió y el sistema todavía deja volver a pedirlo.
        assertEquals(
            SmsPermissionVerdict.ASK_IN_APP,
            verdict(blockLikeRequestSeen = false, canShowRationale = true),
        )
    }

    @Test
    fun `a real permanent denial is not blamed on Android`() {
        // Mismo APK sideloaded, misma señal del sistema; lo único que cambia es que la
        // última petición mostró el diálogo, así que la marca del bloqueo quedó borrada.
        // Sin esta distinción mandaríamos a todo el mundo a buscar un menú que no le sirve.
        assertEquals(
            SmsPermissionVerdict.DENIED,
            verdict(blockLikeRequestSeen = false, canShowRationale = false),
        )
    }

    @Test
    fun `nothing to show once the permission is granted`() {
        assertEquals(SmsPermissionVerdict.GRANTED, verdict(granted = true))
        // Ni siquiera con las señales del bloqueo puestas: concedido gana siempre.
        assertEquals(
            SmsPermissionVerdict.GRANTED,
            verdict(granted = true, askedBefore = true, blockLikeRequestSeen = true),
        )
    }

    @Test
    fun `never asked cannot be a denial`() {
        assertEquals(SmsPermissionVerdict.ASK_IN_APP, verdict(askedBefore = false))
    }

    @Test
    fun `below Android 15 there are no restricted settings on SMS to blame`() {
        // minSdk 24. Los ajustes restringidos de Android 13 cubren accesibilidad y escucha
        // de notificaciones; el permiso de SMS entra recién en API 35. Por debajo, el mismo
        // par de señales solo puede significar una denegación común.
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 24))
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 31))
        // Los dos que el umbral viejo (33) acusaba en falso.
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 33))
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 34))
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = RESTRICTED_SETTINGS_MIN_SDK - 1))
        assertEquals(SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS, verdict(sdkInt = RESTRICTED_SETTINGS_MIN_SDK))
    }

    @Test
    fun `an install we could not classify falls back to the generic copy`() {
        assertEquals(SmsPermissionVerdict.DENIED, verdict(installSource = InstallSource.UNKNOWN))
    }

    @Test
    fun `an app installed from a store is never restricted`() {
        assertEquals(SmsPermissionVerdict.DENIED, verdict(installSource = InstallSource.STORE))
    }
}

/**
 * Las tres formas de enterarse de que el diálogo del sistema sí se mostró.
 *
 * Cada una desmiente el bloqueo por su cuenta. Sin ellas la única señal disponible sería
 * `shouldShowRequestPermissionRationale`, y hay caminos legítimos que llegan a "denegado +
 * rationale false" sin haber pasado nunca por rationale true — cancelar el diálogo con
 * Atrás, o que la hibernación revoque un permiso ya concedido. Cada uno de esos caminos
 * terminaba en una acusación falsa.
 */
class DialogShownEvidenceTest {

    @Test
    fun `the rationale turning true is the classic proof`() {
        assertTrue(sawPermissionDialog(canShowRationale = true, granted = false))
    }

    @Test
    fun `having held the permission proves the dialog worked`() {
        // Auto-revoke por hibernación: concedido → revocado sin pasar jamás por rationale
        // true. A esta app le pasa de verdad (ver Hibernation.kt), y sin esta prueba el
        // estado posterior es indistinguible de un bloqueo.
        assertTrue(sawPermissionDialog(canShowRationale = false, granted = true))
    }

    @Test
    fun `a slow request proves a window was on screen`() {
        // Cancelar con Atrás o tocando afuera: denegado, rationale intacto, latch sin
        // escribir. Lo único que queda del diálogo es el tiempo que estuvo abierto.
        assertTrue(sawPermissionDialog(canShowRationale = false, granted = false, requestWasSlow = true))
    }

    @Test
    fun `no evidence at all is no evidence`() {
        assertFalse(sawPermissionDialog(canShowRationale = false, granted = false, requestWasSlow = false))
        // El default es "no hubo petición que medir": una relectura al volver a la pantalla
        // no puede inventar una prueba de duración.
        assertFalse(sawPermissionDialog(canShowRationale = false, granted = false))
    }
}

/**
 * La señal de duración, que es la que hace representable "nos preguntaron, el diálogo
 * salió, y no lo supimos ver".
 *
 * Va en reloj monotónico. Falla a favor de "se mostró", porque esa respuesta apaga la
 * acusación y deja el copy genérico.
 */
class RequestDurationTest {

    @Test
    fun `an instant return is the windowless block`() {
        assertFalse(requestDurationSaysDialogShown(startedAtMs = 1_000L, endedAtMs = 1_003L))
        assertFalse(requestDurationSaysDialogShown(startedAtMs = 1_000L, endedAtMs = 1_000L))
    }

    @Test
    fun `just under the threshold is still too fast for a human`() {
        assertFalse(
            requestDurationSaysDialogShown(
                startedAtMs = 1_000L,
                endedAtMs = 1_000L + DIALOG_SHOWN_MIN_ELAPSED_MS - 1,
            ),
        )
    }

    @Test
    fun `the threshold itself already counts as a dialog`() {
        assertTrue(
            requestDurationSaysDialogShown(
                startedAtMs = 1_000L,
                endedAtMs = 1_000L + DIALOG_SHOWN_MIN_ELAPSED_MS,
            ),
        )
    }

    @Test
    fun `a human pressing Back takes seconds`() {
        assertTrue(requestDurationSaysDialogShown(startedAtMs = 1_000L, endedAtMs = 3_400L))
    }

    @Test
    fun `an unusable measurement falls back to saying the dialog was shown`() {
        // Nunca arrancamos el cronómetro (proceso recreado con el diálogo abierto).
        assertTrue(requestDurationSaysDialogShown(startedAtMs = 0L, endedAtMs = 12_000L))
        assertTrue(requestDurationSaysDialogShown(startedAtMs = -5L, endedAtMs = 12_000L))
        // Fin anterior al inicio: la medición no significa nada.
        assertTrue(requestDurationSaysDialogShown(startedAtMs = 9_000L, endedAtMs = 8_000L))
    }
}

/**
 * La señal positiva: qué petición tiene derecho a encender la acusación y cuál la apaga.
 *
 * Es la negación exacta de [sawPermissionDialog], pero el sentido de la escritura importa
 * tanto como el valor: se anota SOLO al volver de una petición real, nunca al releer la
 * pantalla, y se reescribe en los dos sentidos (nada de latch).
 */
class RequestLooksBlockedTest {

    @Test
    fun `an instant denial with nothing else to show for it is the block`() {
        assertTrue(requestLooksBlocked(canShowRationale = false, granted = false, requestWasSlow = false))
    }

    @Test
    fun `a request that drew a dialog denies the block`() {
        // Cada una de las tres pruebas del diálogo, por separado, apaga la marca.
        assertFalse(requestLooksBlocked(canShowRationale = true, granted = false, requestWasSlow = false))
        assertFalse(requestLooksBlocked(canShowRationale = false, granted = true, requestWasSlow = false))
        assertFalse(requestLooksBlocked(canShowRationale = false, granted = false, requestWasSlow = true))
    }
}

/**
 * La actualización desde una versión que no guardaba ninguna señal.
 *
 * Este es el estado que el modelo anterior no podía representar y que por eso dejó pasar el
 * bug: el teléfono real tiene `perm_requested` en true — lo puso el toque que produjo el
 * bloqueo silencioso — y ninguna otra señal. Al preguntar "¿nunca vimos un diálogo?", ese
 * estado respondía "no" y la acusación salía sola; al taparlo con una siembra en la
 * migración, respondía "sí" y el mensaje quedaba muerto para siempre en el único teléfono
 * que lo motivó. La pregunta positiva no necesita migración: no observamos nada, así que no
 * afirmamos nada, y la primera petición de esta versión decide.
 */
class LegacyUpgradeTest {

    private fun upgraded(blockLikeRequestSeen: Boolean) = smsPermissionVerdict(
        sdkInt = 35,
        installSource = InstallSource.SIDELOADED,
        // Lo escribió la versión vieja. Es lo ÚNICO que sobrevive a la actualización.
        askedBefore = true,
        blockLikeRequestSeen = blockLikeRequestSeen,
        granted = false,
        canShowRationale = false,
    )

    @Test
    fun `an upgrade with no observation of its own does not accuse Android`() {
        assertEquals(SmsPermissionVerdict.DENIED, upgraded(blockLikeRequestSeen = false))
    }

    @Test
    fun `the same upgrade after one request that came back without a dialog does`() {
        // El teléfono genuinamente bloqueado. Antes no había forma de llegar acá: el latch
        // sembrado no se podía apagar y el rationale ya nunca vuelve a true.
        assertEquals(SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS, upgraded(blockLikeRequestSeen = true))
    }

    @Test
    fun `an unlucky slow request is recoverable instead of permanent`() {
        // Arranque en frío del controlador de permisos que se pasa del umbral sin dibujar
        // nada: la petición se lee como "hubo diálogo" y el mensaje no sale…
        val slowFirstTry = requestLooksBlocked(canShowRationale = false, granted = false, requestWasSlow = true)
        assertFalse(slowFirstTry)
        assertEquals(SmsPermissionVerdict.DENIED, upgraded(blockLikeRequestSeen = slowFirstTry))
        // …y el intento siguiente, ya en caliente, lo recupera. Con un latch de una sola
        // escritura este segundo intento no habría podido cambiar nada.
        val secondTry = requestLooksBlocked(canShowRationale = false, granted = false, requestWasSlow = false)
        assertEquals(SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS, upgraded(blockLikeRequestSeen = secondTry))
    }
}

/**
 * El aviso condicional del estado ambiguo.
 *
 * Hay un estado que ninguna señal resuelve: instalación sin historia propia, permiso ya
 * denegado y rationale en false. Ahí el veredicto es DENIED — el copy genérico manda a
 * conceder el permiso en la ficha de la app — y si el bloqueo existe, ese interruptor está
 * gris. El aviso nombra esa posibilidad sin afirmarla, y solo donde es posible.
 */
class RestrictedSettingsHintTest {

    @Test
    fun `a sideloaded install on Android 15 gets the conditional pointer`() {
        assertTrue(shouldHintRestrictedSettings(35, InstallSource.SIDELOADED))
    }

    @Test
    fun `nothing to hint where the mechanism cannot apply`() {
        // Mismo sideload, Android donde el permiso de SMS no entra en ajustes restringidos.
        assertFalse(shouldHintRestrictedSettings(34, InstallSource.SIDELOADED))
        assertFalse(shouldHintRestrictedSettings(24, InstallSource.SIDELOADED))
        // Y en Android 15 con una instalación que no puede estar restringida, o que no
        // pudimos clasificar: mandar a un menú que no existe para ellos es ruido.
        assertFalse(shouldHintRestrictedSettings(35, InstallSource.STORE))
        assertFalse(shouldHintRestrictedSettings(35, InstallSource.UNKNOWN))
    }
}

/**
 * Clasificación del origen de instalación. Una sola respuesta habilita el mensaje de
 * ajustes restringidos: SIDELOADED. Todo lo demás cae en STORE o en UNKNOWN, que no
 * afirman nada.
 */
class InstallSourceClassificationTest {

    @Test
    fun `no installing package and no packageSource is ambiguous, not a sideload`() {
        // El KDoc de classifyInstallSource explica por qué: en API 35 un `adb install`
        // llega acá con installingPackageName=null y el sistema lo trata como EXENTO. Sin
        // packageSource utilizable, "no hay instalador" no prueba nada.
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(null, null))
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(null, ""))
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(PACKAGE_SOURCE_UNSPECIFIED, null))
    }

    @Test
    fun `the system package installer is a sideload`() {
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(null, "com.android.packageinstaller"))
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(null, "com.google.android.packageinstaller"))
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(null, "com.android.shell"))
    }

    @Test
    fun `Play and other stores are stores`() {
        assertEquals(InstallSource.STORE, classifyInstallSource(null, "com.android.vending"))
        assertEquals(InstallSource.STORE, classifyInstallSource(null, "com.sec.android.app.samsungapps"))
        assertEquals(InstallSource.STORE, classifyInstallSource(null, "com.huawei.appmarket"))
    }

    @Test
    fun `packageSource wins over the installer name when the system reports it`() {
        assertEquals(InstallSource.STORE, classifyInstallSource(PACKAGE_SOURCE_STORE, null))
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(PACKAGE_SOURCE_LOCAL_FILE, "com.android.vending"))
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(PACKAGE_SOURCE_DOWNLOADED_FILE, null))
    }

    @Test
    fun `an unspecified packageSource falls back to the installer name`() {
        // Medido en API 35 (AOSP): un `adb install` reporta packageSource=1 (STORE), NO
        // unspecified. Este camino es el de los Android por debajo de 13, donde el dato no
        // existe — y donde, con el umbral en 35, ningún veredicto de bloqueo es posible.
        assertEquals(InstallSource.STORE, classifyInstallSource(PACKAGE_SOURCE_UNSPECIFIED, "com.android.vending"))
        assertEquals(
            InstallSource.SIDELOADED,
            classifyInstallSource(PACKAGE_SOURCE_UNSPECIFIED, "com.android.packageinstaller"),
        )
    }

    @Test
    fun `an installer we do not recognise stays unknown`() {
        // Un gestor de apps de terceros puede o no disparar ajustes restringidos: sin
        // certeza no acusamos a Android.
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(null, "com.acme.appmanager"))
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(PACKAGE_SOURCE_OTHER, "com.acme.appmanager"))
    }
}
