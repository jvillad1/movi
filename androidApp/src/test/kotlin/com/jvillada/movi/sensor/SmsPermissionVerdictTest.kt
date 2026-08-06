package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El caso que motiva todo esto: en Android 13+ una app instalada fuera de una tienda
 * no puede recibir el permiso de SMS por el diálogo normal. `requestPermissions`
 * devuelve denegado SIN mostrar nada y `shouldShowRequestPermissionRationale` sigue en
 * false — exactamente lo mismo que ve la app cuando el usuario denegó para siempre.
 * Estos tests fijan cuándo tenemos derecho a decir "esto lo bloqueó Android".
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
    fun `below Android 13 there are no restricted settings to blame`() {
        // minSdk 24. Los ajustes restringidos existen desde API 33: por debajo, el mismo
        // par de señales solo puede significar una denegación común.
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 24))
        assertEquals(SmsPermissionVerdict.DENIED, verdict(sdkInt = 31))
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
 * Clasificación del origen de instalación. Solo dos respuestas habilitan el mensaje de
 * ajustes restringidos: SIDELOADED. Todo lo demás cae en UNKNOWN, que no afirma nada.
 */
class InstallSourceClassificationTest {

    @Test
    fun `no installing package at all is a sideload`() {
        // adb install / instalación por archivo sin atribución.
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(null, null))
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(null, ""))
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
        // Lo que devuelve un `adb install`: el SO no fija packageSource y no hay instalador.
        assertEquals(InstallSource.SIDELOADED, classifyInstallSource(PACKAGE_SOURCE_UNSPECIFIED, null))
        assertEquals(InstallSource.STORE, classifyInstallSource(PACKAGE_SOURCE_UNSPECIFIED, "com.android.vending"))
    }

    @Test
    fun `an installer we do not recognise stays unknown`() {
        // Un gestor de apps de terceros puede o no disparar ajustes restringidos: sin
        // certeza no acusamos a Android.
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(null, "com.acme.appmanager"))
        assertEquals(InstallSource.UNKNOWN, classifyInstallSource(PACKAGE_SOURCE_OTHER, "com.acme.appmanager"))
    }
}
