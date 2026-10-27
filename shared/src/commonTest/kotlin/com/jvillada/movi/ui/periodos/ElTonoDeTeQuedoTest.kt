package com.jvillada.movi.ui.periodos

import com.jvillada.movi.ui.components.ds.TonoDeCifra
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «Te quedó» en Tus períodos (vuelta de diseño 19): la `Cifra` del sistema con este tono pone el signo
 * y el color que antes se armaban a mano — «+» y `entra` si quedó algo, «−» y `sale` si no alcanzó,
 * apagado en cero.
 */
class ElTonoDeTeQuedoTest {

    @Test
    fun `te quedo va en entra si quedo algo, en sale si no alcanzo, apagado en cero`() {
        assertEquals(TonoDeCifra.Ingreso, tonoDeTeQuedo(1))
        assertEquals(TonoDeCifra.Alerta, tonoDeTeQuedo(-1))
        assertEquals(TonoDeCifra.Apagado, tonoDeTeQuedo(0))
    }
}
