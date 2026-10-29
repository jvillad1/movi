package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 67 · el título de la fila es la categoría o el concepto, nunca la cuenta
 *
 * La tercera revisión: con el concepto igual a la categoría la fila decía «Bancolombia Ahorros 4057 ·
 * Internet» y el detalle se titulaba «Nu». Y la revisión de la 66: la cuota se llamaba «Cuota de
 * crédito» en Todo y «Cuota de Vehículo 4083 · Pago Occidente» en Gastos.
 */
class ElTituloEsLaCategoriaOElConceptoTest {

    private fun evento(
        id: String, cuenta: String, tipo: TransactionType, monto: Long, categoria: String, descripcion: String, par: String? = null,
    ) = FinancialEvent(id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = descripcion, timestamp = 0L, transferId = par)

    @Test
    fun `el concepto igual a la categoria titula con la categoria y la cuenta va debajo`() {
        val internet = evento("e", "ahorros", TransactionType.EXPENSE, 3_500, "Internet", "Internet")
        assertEquals(TextosDeLaFila("Internet", "Bancolombia Ahorros 4057"), textosDeLaFila(internet, "Bancolombia Ahorros 4057"))
        assertEquals("Internet", tituloDelMovimiento(internet, "Nu"), "el detalle se titula igual")
    }

    @Test
    fun `la cuota se llama por su credito en Todo y en Gastos`() {
        val dinero = evento("p1", "ahorros", TransactionType.EXPENSE, 980_000, CUOTA_CATEGORY, "Cuota de Vehículo 4083 · Pago Occidente", "tr")
        val deuda = evento("p2", "carro", TransactionType.INCOME, 700_000, CUOTA_CATEGORY, "Abono a capital desde Ahorros", "tr")
        // En Gastos se ve la pata del dinero, sola.
        assertEquals("Cuota de Vehículo 4083", textosDeLaFila(dinero, "Ahorros").titulo)
        // En Todo, el renglón del par.
        val par = MovementRow.Transfer(out = dinero, into = deuda)
        assertEquals("Cuota de Vehículo 4083", transferRowTitle(par, mapOf("ahorros" to AccountType.SAVINGS, "carro" to AccountType.LOAN)))
        // Sin el concepto de siempre, el nombre de la categoría.
        assertEquals(CUOTA_CATEGORY, tituloDeLaCuota(dinero.copy(description = "")))
    }
}
