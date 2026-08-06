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
        dialogEverShown: Boolean = false,
        granted: Boolean = false,
        canShowRationale: Boolean = false,
    ) = smsPermissionVerdict(sdkInt, installSource, askedBefore, dialogEverShown, granted, canShowRationale)

    @Test
    fun `sideloaded denial without the dialog ever appearing is the system blocking it`() {
        assertEquals(SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS, verdict())
    }

    @Test
    fun `an ordinary first denial keeps asking in-app`() {
        // El diálogo SÍ salió y el sistema todavía deja volver a pedirlo.
        assertEquals(
            SmsPermissionVerdict.ASK_IN_APP,
            verdict(dialogEverShown = true, canShowRationale = true),
        )
    }

    @Test
    fun `a real permanent denial is not blamed on Android`() {
        // Mismo APK sideloaded, misma señal del sistema; lo único que cambia es que el
        // usuario llegó a ver el diálogo y dijo que no. Sin esta señal mandaríamos a
        // todo el mundo a buscar un menú que no le va a servir.
        assertEquals(
            SmsPermissionVerdict.DENIED,
            verdict(dialogEverShown = true, canShowRationale = false),
        )
    }

    @Test
    fun `nothing to show once the permission is granted`() {
        assertEquals(SmsPermissionVerdict.GRANTED, verdict(granted = true))
        // Ni siquiera con las señales del bloqueo puestas: concedido gana siempre.
        assertEquals(
            SmsPermissionVerdict.GRANTED,
            verdict(granted = true, askedBefore = true, dialogEverShown = false),
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
 * Sin ellas el latch dependía únicamente de `shouldShowRequestPermissionRationale`, y hay
 * caminos legítimos que llegan a "denegado + rationale false" sin haber pasado nunca por
 * rationale true. Cada uno de esos caminos terminaba en una acusación falsa y sin salida:
 * el rationale ya no vuelve a true, así que el latch no podía encenderse nunca más.
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
 * Migración de las prefs al actualizar.
 *
 * Es el mismo bug que los otros dos, escrito en el disco en vez de en una variable: una
 * instalación vieja tiene "ya pedimos" sin "el diálogo se mostró", y eso leído de frente
 * es exactamente la firma del bloqueo.
 */
class PermissionSignalsMigrationTest {

    @Test
    fun `an upgrade that had already asked gets the dialog latched`() {
        assertTrue(shouldSeedDialogShown(storedVersion = 0, askedBefore = true))
    }

    @Test
    fun `a fresh install has nothing to seed`() {
        // Sin petición previa el veredicto es ASK_IN_APP igual, y sembrar el latch acá
        // desactivaría el mensaje para siempre sin motivo.
        assertFalse(shouldSeedDialogShown(storedVersion = 0, askedBefore = false))
    }

    @Test
    fun `signals written by this version are left alone`() {
        // Ya migrado: volver a sembrar borraría la única distinción que sostiene el mensaje.
        assertFalse(shouldSeedDialogShown(storedVersion = PERM_SIGNALS_VERSION, askedBefore = true))
        assertFalse(shouldSeedDialogShown(storedVersion = PERM_SIGNALS_VERSION + 1, askedBefore = true))
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
