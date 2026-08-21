package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionCtaTest {

    @Test
    fun `first ask goes through the in-app dialog`() {
        assertFalse(shouldOpenSettings(askedBefore = false, canShowRationale = false))
    }

    @Test
    fun `after a denial that still allows the dialog we keep asking in-app`() {
        assertFalse(shouldOpenSettings(askedBefore = true, canShowRationale = true))
    }

    @Test
    fun `denied for good goes to system settings instead of a no-op button`() {
        // Android 11+ (13+ tras dos negativas) ignora requestPermissions en silencio:
        // sin este caso el botón no haría nada y el sensor quedaría mudo sin salida.
        assertTrue(shouldOpenSettings(askedBefore = true, canShowRationale = false))
    }
}
