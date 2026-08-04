package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HibernationWarningTest {

    @Test
    fun `warns when the OS hibernates and the app is not exempt`() {
        assertTrue(shouldWarnAboutHibernation(sdkInt = 35, exempt = false))
    }

    @Test
    fun `says nothing once the app is exempt`() {
        // El usuario ya lo resolvió: dejar el aviso puesto lo convierte en ruido permanente.
        assertFalse(shouldWarnAboutHibernation(sdkInt = 35, exempt = true))
    }

    @Test
    fun `says nothing on Android versions without hibernation`() {
        // minSdk 24: por debajo de API 30 no hay auto-revoke que desactivar, así que el
        // aviso sería falso y el botón no llevaría a ninguna parte.
        assertFalse(shouldWarnAboutHibernation(sdkInt = 24, exempt = false))
        assertFalse(shouldWarnAboutHibernation(sdkInt = 29, exempt = false))
    }

    @Test
    fun `API 30 is the first version that warns`() {
        assertTrue(shouldWarnAboutHibernation(sdkInt = AUTO_REVOKE_MIN_SDK, exempt = false))
        assertFalse(shouldWarnAboutHibernation(sdkInt = AUTO_REVOKE_MIN_SDK - 1, exempt = false))
    }
}
