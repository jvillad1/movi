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
    fun `filterDateInput acepta barras como guiones y descarta el resto`() {
        // «2026/06/17» era el caso exacto que dejaba el botón en gris (F24): la barra pasa a
        // guion en vez de desaparecer, así la persona no tiene que borrar y reescribir.
        assertEquals("2026-06-17", filterDateInput("2026/06/17"))
        assertEquals("2026-06-17", filterDateInput("2026-06-17abc"))
    }

    @Test
    fun `filterRateInput acepta la coma como decimal`() {
        // «12,5» tecleado a la colombiana no puede convertirse en 125 en silencio.
        assertEquals("12.5", filterRateInput("12,5"))
        assertEquals("12.53", filterRateInput("12,5,3")) // el segundo separador se descarta, los dígitos quedan
    }

    @Test
    fun `isValidCreditDate exige mes y dia de dos digitos`() {
        assertFalse(isValidCreditDate("2026-6-7"))
        assertTrue(isValidCreditDate("2026-06-07"))
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
