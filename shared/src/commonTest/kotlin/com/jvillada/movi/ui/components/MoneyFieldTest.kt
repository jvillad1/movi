package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F14 · F23 · F34 · F53: un solo componente de monto para toda la app. Estos son los tests de
 * las funciones puras que arman el formateo mientras se escribe (miles al vuelo) y el parseo de
 * vuelta a [Long] — el mismo par de funciones que usan [MoneyField] y los teclados numéricos
 * propios de Presupuestos y Agregar movimiento.
 */
class MoneyFieldTest {

    @Test
    fun `groupDigitsForDisplay agrupa de a tres desde la derecha`() {
        assertEquals("2.000.000", groupDigitsForDisplay("2000000"))
    }

    @Test
    fun `groupDigitsForDisplay con menos de cuatro digitos no agrega puntos`() {
        assertEquals("500", groupDigitsForDisplay("500"))
    }

    @Test
    fun `groupDigitsForDisplay de vacio es vacio`() {
        assertEquals("", groupDigitsForDisplay(""))
    }

    @Test
    fun `formatAmountKeypadDisplay de vacio muestra cero`() {
        assertEquals("0", formatAmountKeypadDisplay(""))
    }

    @Test
    fun `formatAmountKeypadDisplay agrupa la parte entera y respeta el decimal`() {
        assertEquals("2.000.000.5", formatAmountKeypadDisplay("2000000.5"))
    }

    @Test
    fun `parseMoneyDigits de digitos crudos`() {
        assertEquals(2000000L, parseMoneyDigits("2000000"))
    }

    @Test
    fun `parseMoneyDigits ignora separadores de miles y el simbolo de moneda`() {
        assertEquals(2000000L, parseMoneyDigits("$2.000.000"))
    }

    @Test
    fun `parseMoneyDigits de vacio es null`() {
        assertNull(parseMoneyDigits(""))
    }

    @Test
    fun `parseMoneyDigits de solo separadores es null`() {
        assertNull(parseMoneyDigits("$.,"))
    }
}
