package com.jvillada.movi.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Día a día»: lo que gastaste ese día contra lo que podías gastar
 *
 * Las frases exactas, leídas tal cual. Los números son los del dueño (período 25-sep → 24-oct,
 * meta de $41.924 al día).
 */
class DiaADiaTest {

    @Test
    fun `un dia dentro de la meta dice cuanto gasto y de cuanto, sin aviso`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = 6_700L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$6.700 de \$41.924", linea.texto)
        assertNull(linea.aviso)
        assertFalse(linea.pasada)
    }

    @Test
    fun `un dia pasado dice cuanto se paso`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = 44_000L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$44.000 de \$41.924 · te pasaste \$2.076", linea.texto)
        assertEquals("te pasaste \$2.076", linea.aviso)
        assertEquals("Día a día: \$44.000 de \$41.924", linea.base)
        assertTrue(linea.pasada)
    }

    @Test
    fun `el otro dia del dueno tambien`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = 44_100L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$44.100 de \$41.924 · te pasaste \$2.176", linea.texto)
    }

    @Test
    fun `gastar justo la meta no es pasarse`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = 41_924L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$41.924 de \$41.924", linea.texto)
        assertFalse(linea.pasada)
    }

    @Test
    fun `un dia sin gasto se dice, es informacion`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = 0L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$0 de \$41.924", linea.texto)
    }

    @Test
    fun `un reembolso no da un gasto negativo`() {
        val linea = assertNotNull(lineaDelDiaADia(gastoDelDia = -5_000L, metaPorDia = 41_924L))
        assertEquals("Día a día: \$0 de \$41.924", linea.texto)
    }

    @Test
    fun `sin meta no se dibuja nada`() {
        assertNull(lineaDelDiaADia(gastoDelDia = 10_000L, metaPorDia = null))
        assertNull(lineaDelDiaADia(gastoDelDia = 10_000L, metaPorDia = 0L))
        assertNull(lineaDelDiaADia(gastoDelDia = 0L, metaPorDia = -1L))
    }
}
