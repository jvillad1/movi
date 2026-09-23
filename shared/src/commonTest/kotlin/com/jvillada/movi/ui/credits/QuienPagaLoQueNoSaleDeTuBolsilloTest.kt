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
}
