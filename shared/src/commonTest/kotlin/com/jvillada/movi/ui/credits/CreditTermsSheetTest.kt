package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ── Ola 16: la hoja pregunta si el crédito acaba de desembolsarse ─────────────────────

    private fun cuenta(
        id: String,
        nombre: String,
        tipo: AccountType = AccountType.CHECKING,
        moneda: String = "COP",
    ) = Account(id = id, name = nombre, type = tipo, balance = 0L, currency = moneda)

    @Test
    fun `el selector solo ofrece cuentas de dinero o inversion en pesos`() {
        val todas = listOf(
            cuenta("a", "Bancolombia"),
            cuenta("b", "CDT", AccountType.INVESTMENT),
            cuenta("c", "Efectivo", AccountType.CASH),
            cuenta("d", "Otra libranza", AccountType.LOAN),
            cuenta("e", "Visa", AccountType.CREDIT_CARD),
            cuenta("f", "Cuenta en dólares", AccountType.SAVINGS, moneda = "USD"),
        )
        assertEquals(listOf("a", "b", "c"), cuentasParaDesembolso(todas).map { it.id })
    }

    @Test
    fun `sin cuentas no hay ninguna a la que ofrecer el desembolso`() {
        assertTrue(cuentasParaDesembolso(emptyList()).isEmpty())
    }

    @Test
    fun `el monto del desembolso viene con el capital puesto`() {
        assertEquals(257_000_000L, montoDelDesembolso(editado = null, capital = 257_000_000L))
    }

    @Test
    fun `lo que el dueno escribe le gana al capital`() {
        // El desembolso neto de costos: $250M de un capital de $257M.
        assertEquals(250_000_000L, montoDelDesembolso(editado = 250_000_000L, capital = 257_000_000L))
    }

    @Test
    fun `borrar el campo vuelve al capital en vez de dejarlo vacio`() {
        // `MoneyField` devuelve null cuando el campo queda en blanco: el efectivo tiene que seguir
        // siendo lo que se ve en el campo, y lo que se ve vuelve a ser el capital.
        assertEquals(257_000_000L, montoDelDesembolso(editado = null, capital = 257_000_000L))
        assertNull(montoDelDesembolso(editado = null, capital = null))
    }

    @Test
    fun `la aritmetica dice las dos cifras cuando coinciden`() {
        assertEquals(
            "Entran \$257.000.000 a Bancolombia y el crédito arranca debiendo \$257.000.000.",
            explicacionDelDesembolso(capital = 257_000_000L, entro = 257_000_000L, destino = "Bancolombia"),
        )
    }

    @Test
    fun `la aritmetica explica la diferencia cuando el banco descuenta costos`() {
        val linea = explicacionDelDesembolso(capital = 257_000_000L, entro = 250_000_000L, destino = "Bancolombia")
        assertTrue(linea!!.contains("\$250.000.000"), "tiene que decir lo que entró")
        assertTrue(linea.contains("\$257.000.000"), "y lo que se debe")
        assertTrue(linea.contains("\$7.000.000"), "y de dónde sale la diferencia")
    }

    @Test
    fun `la aritmetica se calla mientras falten datos`() {
        assertNull(explicacionDelDesembolso(capital = null, entro = 1L, destino = "X"))
        assertNull(explicacionDelDesembolso(capital = 1L, entro = null, destino = "X"))
        assertNull(explicacionDelDesembolso(capital = 0L, entro = 1L, destino = "X"))
    }

    @Test
    fun `la aritmetica no le pone buena cara a un monto mayor que el capital`() {
        // Ese caso es un rechazo, no una explicación: lo cubre validateCreditDisbursement con su
        // propio mensaje. Dos textos sobre el mismo error, uno diciendo que está bien, es peor.
        assertNull(explicacionDelDesembolso(capital = 257_000_000L, entro = 260_000_000L, destino = "Bancolombia"))
    }

    @Test
    fun `sin cuenta elegida la aritmetica sigue diciendo las cifras`() {
        val linea = explicacionDelDesembolso(capital = 100_000L, entro = 100_000L, destino = null)
        assertEquals("Entran \$100.000 a tu cuenta y el crédito arranca debiendo \$100.000.", linea)
    }
}
