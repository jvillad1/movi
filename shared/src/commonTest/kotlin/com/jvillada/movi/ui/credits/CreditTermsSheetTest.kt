package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.validateCreditDisbursement
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
        val linea = explicacionDelDesembolso(capital = 257_000_000L, entro = 257_000_000L, destino = "Bancolombia")
        assertEquals(
            "Entran \$257.000.000 a Bancolombia y el crédito arranca debiendo \$257.000.000.",
            linea!!.texto,
        )
        assertFalse(linea.esAdvertencia)
    }

    @Test
    fun `la aritmetica explica la diferencia cuando el banco descuenta costos`() {
        val linea = explicacionDelDesembolso(capital = 257_000_000L, entro = 250_000_000L, destino = "Bancolombia")!!
        assertTrue(linea.texto.contains("\$250.000.000"), "tiene que decir lo que entró")
        assertTrue(linea.texto.contains("\$257.000.000"), "y lo que se debe")
        assertTrue(linea.texto.contains("\$7.000.000"), "y de dónde sale la diferencia")
        assertTrue(linea.texto.contains("descuenta costos"), "un 3% de brecha sí son costos")
        assertFalse(linea.esAdvertencia)
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
    fun `sin cuenta elegida la aritmetica todavia no habla`() {
        // «Entran $100.000 a tu cuenta» se lee como frase cerrada, y quedaba debajo de un botón
        // apagado justamente porque falta elegir la cuenta. En ese momento se muestra en su lugar
        // la ayuda del valor por defecto.
        assertNull(explicacionDelDesembolso(capital = 100_000L, entro = 100_000L, destino = null))
    }

    /**
     * **El dedo que se come dígitos.** $2 sobre un capital de $257.000.000 dejaba el botón
     * habilitado y la hoja decía que los $256.999.998 de diferencia eran «lo que pasa cuando el
     * banco descuenta costos». Un error de dedo no puede recibir una justificación.
     */
    @Test
    fun `una brecha implausible avisa en vez de justificarla`() {
        val linea = explicacionDelDesembolso(capital = 257_000_000L, entro = 2L, destino = "Bancolombia")!!
        assertTrue(linea.esAdvertencia)
        assertTrue(linea.texto.contains("Revisa el monto"), "tiene que pedir que revise")
        assertFalse(linea.texto.contains("descuenta costos"), "y NO puede decirle que es normal")
        assertTrue(linea.texto.contains("\$256.999.998"), "nombrando la plata que no le entró")
    }

    @Test
    fun `el umbral esta donde dejan de ser costos y no antes`() {
        // 70% del capital: costos financiados reales son porcentajes de un dígito, así que el
        // umbral es holgado a propósito y el caso común nunca lo toca.
        assertFalse(explicacionDelDesembolso(100_000_000L, 70_000_000L, "X")!!.esAdvertencia)
        assertTrue(explicacionDelDesembolso(100_000_000L, 69_000_000L, "X")!!.esAdvertencia)
    }

    /**
     * Y avisa en vez de bloquear porque la brecha enorme **puede ser real**: en una compra de
     * cartera el banco gira la mayor parte directo al otro acreedor y a la cuenta del dueño le
     * entra el resto. Bloquearlo le impediría registrar un crédito que sí existe.
     */
    @Test
    fun `la compra de cartera avisa pero se puede guardar`() {
        val linea = explicacionDelDesembolso(capital = 257_000_000L, entro = 57_000_000L, destino = "Bancolombia")!!
        assertTrue(linea.esAdvertencia)
        // La validación —la que apaga el botón— sigue dejándolo pasar: avisar no es bloquear.
        assertNull(
            validateCreditDisbursement(
                257_000_000L,
                cuenta("a", "Bancolombia"),
                57_000_000L,
            ),
        )
    }
}
