package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.CreditTerms
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **La fila de los intereses que no salen de su cuenta dice QUIÉN los paga.**
 *
 * Decía «Los paga tu nómina o un tercero: $15.675.870». En los datos del dueño no hay ningún
 * tercero: son dos libranzas que descuenta la nómina y dos hipotecas que paga Skandia, que es su
 * propia pensión voluntaria. «Un tercero» es exactamente la lectura equivocada que Movi AI repitió
 * el 23-sep («Skandia, tu seguro»), y la pantalla la seguía diciendo.
 */
class QuienPagaLoQueNoSaleDeTuBolsilloTest {

    private fun credito(porNomina: Boolean = false, loPaga: String? = null) = CreditTerms(
        accountId = "c", bank = "Banco", principal = 1L, rateEa = 10.0, termMonths = 12,
        installment = 1L, dayOfMonth = 1, startDate = "2026-01-01",
        payrollDeduction = porNomina, paidBy = loPaga,
    )

    private val delDueno = listOf(
        credito(), // Vehículo 8761: de su bolsillo, no entra
        credito(porNomina = true), // Libranza 4818
        credito(porNomina = true), // Libranza 4608
        credito(loPaga = "Skandia"), // Hipoteca 1254
        credito(loPaga = "Skandia"), // Hipotecario 2334
    )

    @Test
    fun `con los creditos del dueno nombra la nomina y a Skandia, sin terceros`() {
        val quienes = quienesPaganLoQueNoSaleDeTuBolsillo(delDueno)
        assertEquals(listOf("tu nómina", "Skandia"), quienes)
        assertEquals("Los pagan tu nómina y Skandia", alcanceDelInteresAjeno(quienes))
        assertEquals("En los que pagan tu nómina y Skandia, y se terminan", alcanceDeLaFaltaAjena(quienes))
    }

    @Test
    fun `un solo pagador se nombra en singular`() {
        assertEquals("Los descuenta tu nómina", alcanceDelInteresAjeno(listOf("tu nómina")))
        assertEquals("Los paga Skandia", alcanceDelInteresAjeno(listOf("Skandia")))
        assertEquals("En los que paga Skandia y se terminan", alcanceDeLaFaltaAjena(listOf("Skandia")))
    }

    @Test
    fun `tres pagadores van con comas y una y`() {
        assertEquals(
            "Los pagan tu nómina, Skandia y Papá",
            alcanceDelInteresAjeno(listOf("tu nómina", "Skandia", "Papá")),
        )
    }

    @Test
    fun `sin datos de quien paga cae en el rotulo generico`() {
        assertEquals(ALCANCE_INTERES_AJENO, alcanceDelInteresAjeno(emptyList()))
        assertEquals(ALCANCE_FALTA_AJENO, alcanceDeLaFaltaAjena(emptyList()))
    }
    /** Vuelta 141 · la cuota que sale de la AFC (fuera de Tu plata) va aparte y dice de qué cuenta se paga. */
    @Test
    fun `la cuota que sale de la AFC dice que se paga desde la AFC`() {
        val afc = com.jvillada.movi.shared.model.Account("afc", "AFC Davibank 9497", com.jvillada.movi.shared.model.AccountType.SAVINGS, 1L, condicionadaA = "vivienda")
        val hipoteca = com.jvillada.movi.shared.model.CreditSummary(
            account = com.jvillada.movi.shared.model.Account("h1254", "Hipoteca 1254", com.jvillada.movi.shared.model.AccountType.LOAN, 1L),
            terms = credito().copy(sePagaDesde = afc.id), paidPct = null,
            cuotaSePagaDesdeFuera = com.jvillada.movi.shared.model.cuentaDeLaCuotaFueraDeTuPlata(credito().copy(sePagaDesde = afc.id), listOf(afc))?.name,
        )
        val cuentas = cuentasFueraDeLasCuotas(listOf(hipoteca))
        assertEquals(listOf("AFC Davibank 9497"), cuentas)
        assertEquals("Se pagan desde AFC Davibank 9497", alcanceDelInteresAjeno(emptyList(), cuentas))
        assertEquals("En los que se pagan desde AFC Davibank 9497 y se terminan", alcanceDeLaFaltaAjena(emptyList(), cuentas))
        assertEquals("Los paga Skandia o salen de AFC Davibank 9497", alcanceDelInteresAjeno(listOf("Skandia"), cuentas))
        val deuda = com.jvillada.movi.shared.model.deudasParaSalir(listOf(hipoteca), emptyList()).single()
        assertEquals("Se paga desde AFC Davibank 9497", quienPagaEnTexto(deuda))
    }
}
