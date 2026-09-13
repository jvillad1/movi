package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **El «Flujo del día» es uno solo.**
 *
 * El mismo número se calculaba en dos lados con dos reglas: el server sumaba solo los movimientos
 * en pesos, y Movimientos lo recalculaba sin mirar la moneda. Con una compra de US$20 en la Master
 * Black, la pantalla habría restado veinte pesos del día mientras el server no contaba nada.
 *
 * [aporteAlFlujoDelDia] es la regla, en un solo lugar, y las dos puntas la usan.
 */
class AporteAlFlujoDelDiaTest {

    private fun evento(
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "COP",
        countsAsCashFlow: Boolean = true,
    ) = FinancialEvent(
        id = "e",
        accountId = "a",
        type = type,
        amount = amount,
        currency = currency,
        category = "Comida",
        description = "x",
        timestamp = 0L,
        countsAsCashFlow = countsAsCashFlow,
    )

    @Test
    fun `un gasto en pesos resta`() {
        assertEquals(-18_500L, aporteAlFlujoDelDia(evento(18_500L)))
    }

    @Test
    fun `un ingreso en pesos suma`() {
        assertEquals(12L, aporteAlFlujoDelDia(evento(12L, type = TransactionType.INCOME)))
    }

    /** **El caso que abrió esto.** Un cobro en dólares no son veinte pesos. */
    @Test
    fun `un gasto en dolares no suma ni resta`() {
        assertEquals(0L, aporteAlFlujoDelDia(evento(20L, currency = "USD")))
    }

    @Test
    fun `lo que no es flujo no cuenta, este en la moneda que este`() {
        // Un ajuste de saldo, la apertura de una cuenta, un pago de tarjeta: `isCashFlow` los
        // deja afuera, y el día tampoco puede contarlos.
        assertEquals(0L, aporteAlFlujoDelDia(evento(9_006L, countsAsCashFlow = false)))
        assertEquals(0L, aporteAlFlujoDelDia(evento(256L, currency = "USD", countsAsCashFlow = false)))
    }

    @Test
    fun `un dia mezclado suma solo lo que corresponde`() {
        val dia = listOf(
            evento(18_500L),                                   // Carnes y Legumbres
            evento(12L, type = TransactionType.INCOME),        // Abono intereses
            evento(20L, currency = "USD"),                     // Anthropic en dólares
            evento(200_000L, countsAsCashFlow = false),        // un ajuste de saldo
        )
        assertEquals(-18_488L, dia.sumOf { aporteAlFlujoDelDia(it) })
    }
}
