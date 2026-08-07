package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * En qué estado está el permiso de SMS y qué acción queda disponible.
 *
 * No intenta atribuir la falta del permiso a nadie: en Android 15+ una app instalada fuera
 * de una tienda recibe exactamente lo mismo que ve tras una denegación permanente del
 * usuario — denegado, sin diálogo y con `shouldShowRequestPermissionRationale` en false — y
 * ninguna API separa los dos casos. Lo que sí cambia entre estados es qué botón tiene
 * sentido ofrecer, y eso es lo que estos tests fijan.
 */
class SmsPermissionVerdictTest {

    private fun verdict(
        askedBefore: Boolean = true,
        granted: Boolean = false,
        canShowRationale: Boolean = false,
    ) = smsPermissionVerdict(askedBefore, granted, canShowRationale)

    @Test
    fun `a denial the system will not re-prompt for leaves only the settings route`() {
        assertEquals(SmsPermissionVerdict.DENIED, verdict())
    }

    @Test
    fun `an ordinary first denial keeps asking in-app`() {
        // El sistema todavía deja volver a mostrar el diálogo.
        assertEquals(SmsPermissionVerdict.ASK_IN_APP, verdict(canShowRationale = true))
    }

    @Test
    fun `nothing to show once the permission is granted`() {
        assertEquals(SmsPermissionVerdict.GRANTED, verdict(granted = true))
        // Concedido gana incluso sobre el rationale, que puede quedar en true de una
        // denegación anterior.
        assertEquals(SmsPermissionVerdict.GRANTED, verdict(granted = true, canShowRationale = true))
    }

    @Test
    fun `never asked cannot be a denial`() {
        assertEquals(SmsPermissionVerdict.ASK_IN_APP, verdict(askedBefore = false))
    }
}

/**
 * El aviso condicional, que es todo lo que se dice sobre los ajustes restringidos.
 *
 * Con el permiso denegado y el rationale en false, el copy genérico manda a conceder el
 * permiso en la ficha de la app — y si el bloqueo existe, ese interruptor está gris. El
 * aviso nombra esa posibilidad sin afirmarla, y solo donde el mecanismo puede aplicar. Un
 * condicional no puede estar equivocado; a lo sumo es irrelevante, y por eso no hace falta
 * (ni se puede) probar antes que el bloqueo exista.
 */
class RestrictedSettingsHintTest {

    @Test
    fun `a sideloaded install on Android 15 gets the conditional pointer`() {
        assertTrue(shouldHintRestrictedSettings(35, InstallSource.SIDELOADED))
    }

    @Test
    fun `un origen sin clasificar tambien recibe la pista`() {
        // El APK llega por Drive: si el sistema anota a Drive como instalador y no da un
        // packageSource utilizable, el origen queda UNKNOWN. Excluirlo silenciaba la pista
        // justo en el teléfono para el que se escribió. Como es condicional y no una
        // acusación, bajo UNKNOWN el antecedente sigue protegiendo la frase.
        assertTrue(shouldHintRestrictedSettings(35, InstallSource.UNKNOWN))
    }

    @Test
    fun `nothing to hint where the mechanism cannot apply`() {
        // Mismo sideload, Android donde el permiso de SMS no entra en ajustes restringidos.
        assertFalse(shouldHintRestrictedSettings(34, InstallSource.SIDELOADED))
        assertFalse(shouldHintRestrictedSettings(24, InstallSource.SIDELOADED))
        assertFalse(shouldHintRestrictedSettings(34, InstallSource.UNKNOWN))
        // STORE es el único origen donde la pista sería afirmativamente falsa: una
        // instalación de tienda no puede tener el interruptor gris por esta razón.
        assertFalse(shouldHintRestrictedSettings(35, InstallSource.STORE))
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
