package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El relevo de scroll de los márgenes solo existe donde hay márgenes. En el teléfono (Compact)
 * era un segundo `scrollable` sobre toda la pantalla y la lista «se pegaba» al llegar al final
 * (ver el KDoc de `ScrollDesdeLosMargenes.kt`, «En el teléfono NO va, y se midió»).
 */
class ElRelevoDeScrollSoloEnPantallaAnchaTest {

    @Test
    fun `en el telefono el relevo no se aplica`() {
        assertFalse(elRelevoDeScrollAplica(WindowWidthClass.Compact))
    }

    @Test
    fun `con rail, donde hay margenes, si se aplica`() {
        assertTrue(elRelevoDeScrollAplica(WindowWidthClass.Medium))
        assertTrue(elRelevoDeScrollAplica(WindowWidthClass.Expanded))
    }
}
