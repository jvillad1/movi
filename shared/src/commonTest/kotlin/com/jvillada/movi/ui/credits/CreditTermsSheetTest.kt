package com.jvillada.movi.ui.credits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F23 · F24: la tasa aceptaba "12%" y la fecha cualquier texto — el botón "Guardar crédito" se
 * apagaba sin decir por qué. Estos son los tests de las funciones puras de filtrado/validación
 * que arreglan eso.
 */
class CreditTermsSheetTest {

    @Test
    fun `filterRateInput deja pasar digitos y un punto`() {
        assertEquals("12.5", filterRateInput("12.5"))
    }

    @Test
    fun `filterRateInput saca el simbolo de porcentaje`() {
        assertEquals("12", filterRateInput("12%"))
    }

    @Test
    fun `filterRateInput solo permite un punto`() {
        assertEquals("12.5", filterRateInput("12..5"))
    }

    @Test
    fun `filterDateInput deja pasar digitos y guiones`() {
        assertEquals("2026-06-17", filterDateInput("2026-06-17"))
    }

    @Test
    fun `filterDateInput saca cualquier otro caracter, no inserta guiones`() {
        // El filtro no reformatea, solo descarta lo que no sea dígito o guión — las barras
        // desaparecen sin dejar rastro, no se convierten en guiones.
        assertEquals("20260617", filterDateInput("2026/06/17"))
    }

    @Test
    fun `isValidCreditDate acepta AAAA-MM-DD en rango`() {
        assertTrue(isValidCreditDate("2026-06-17"))
    }

    @Test
    fun `isValidCreditDate rechaza el formato con barras`() {
        assertFalse(isValidCreditDate("2026/06/17"))
    }

    @Test
    fun `isValidCreditDate rechaza mes fuera de rango`() {
        assertFalse(isValidCreditDate("2026-13-01"))
    }

    @Test
    fun `isValidCreditDate rechaza dia fuera de rango`() {
        assertFalse(isValidCreditDate("2026-06-32"))
    }

    @Test
    fun `isValidCreditDate rechaza vacio`() {
        assertFalse(isValidCreditDate(""))
    }
}
