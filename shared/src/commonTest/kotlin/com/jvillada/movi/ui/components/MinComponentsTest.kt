package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F36: `formatCOP`/`formatMillions` tiraban el signo — un mes en rojo (egresos > ingresos)
 * se mostraba en positivo en toda la app porque `groupThousands` usaba `abs` y nadie lo volvía
 * a poner. Este test fija que el signo (U+2212, no el guion ASCII) es responsabilidad del
 * formateador, no de cada pantalla.
 */
class MinComponentsTest {

    @Test
    fun `formatCOP de un negativo trae el signo menos tipografico`() {
        assertEquals("−$1.500", formatCOP(-1500))
    }

    @Test
    fun `formatCOP de un positivo no trae signo`() {
        assertEquals("$1.500", formatCOP(1500))
    }

    @Test
    fun `formatCOP de cero no trae signo`() {
        assertEquals("$0", formatCOP(0))
    }

    @Test
    fun `formatMillions de un negativo trae el signo menos tipografico`() {
        assertEquals("−$2,00M", formatMillions(-2_000_000))
    }

    @Test
    fun `formatMillions de un positivo no trae signo`() {
        assertEquals("$2,00M", formatMillions(2_000_000))
    }
}
