package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F36: `formatCOP`/`formatMoneyCompact` tiraban el signo — un mes en rojo (egresos > ingresos)
 * se mostraba en positivo en toda la app porque `groupThousands` usaba `abs` y nadie lo volvía
 * a poner. Este test fija que el signo (U+2212, no el guion ASCII) es responsabilidad del
 * formateador, no de cada pantalla.
 *
 * Ola 8 · V5: y además fija que la notación compacta **se lea como plata** — que no diga
 * «$0,00M» con la base vacía ni «$0,25M» por $250.000.
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
    fun `formatMoneyCompact de un negativo trae el signo menos tipografico`() {
        assertEquals("−$2M", formatMoneyCompact(-2_000_000))
    }

    @Test
    fun `formatMoneyCompact de un positivo no trae signo`() {
        assertEquals("$2M", formatMoneyCompact(2_000_000))
    }

    /** V5: la base vacía decía «$0,00M» tres veces en la primera pantalla. */
    @Test
    fun `formatMoneyCompact de cero dice cero pesos, no cero millones`() {
        assertEquals("$0", formatMoneyCompact(0))
    }

    /** V5: «$0,25M» obligaba a hacer la cuenta mental para leer un cuarto de millón. */
    @Test
    fun `formatMoneyCompact por debajo del millon dice los pesos`() {
        assertEquals("$250.000", formatMoneyCompact(250_000))
        assertEquals("$999.999", formatMoneyCompact(999_999))
        assertEquals("−$250.000", formatMoneyCompact(-250_000))
    }

    @Test
    fun `formatMoneyCompact desde el millon dice millones con un decimal`() {
        assertEquals("$1M", formatMoneyCompact(1_000_000))
        assertEquals("$4,5M", formatMoneyCompact(4_500_000))
        assertEquals("$7,8M", formatMoneyCompact(7_750_000))
        assertEquals("$12M", formatMoneyCompact(12_000_000))
    }

    /** Que la cifra entre en una de las tres columnas de la tarjeta, hasta con miles de millones. */
    @Test
    fun `formatMoneyCompact nunca pasa de ocho caracteres`() {
        listOf(0L, 999_999L, 1_000_000L, 4_500_000L, 99_900_000L, 1_500_000_000L).forEach {
            assertEquals(true, formatMoneyCompact(it).length <= 8, "muy largo: ${formatMoneyCompact(it)}")
        }
    }
}
