package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # El total del día dice lo mismo que sus filas (vuelta de diseño 32)
 *
 * En el detalle de la tarjeta «Nu» el día decía «+$42.000» y su única fila, «−$42.000»: el total
 * salía de la convención de la deuda. Ahora es la suma de las filas con el signo de Movimientos, en
 * toda cuenta.
 */
class ElTotalDelDiaTest {

    private fun ev(tipo: TransactionType, monto: Long, moneda: String = "COP") = FinancialEvent(
        id = "e", accountId = "acc_nu", type = tipo, amount = monto, currency = moneda,
        category = "Comida", description = "Almuerzo con el equipo", timestamp = 0L,
    )

    @Test
    fun `en una deuda una compra de 42000 es menos 42000, como su fila`() {
        assertEquals(-42_000L, totalDelDiaComoSusFilas(listOf(ev(TransactionType.EXPENSE, 42_000))))
    }

    @Test
    fun `un abono suma y una compra resta, en cualquier cuenta`() {
        val dia = listOf(ev(TransactionType.INCOME, 500_000), ev(TransactionType.EXPENSE, 42_000))
        assertEquals(458_000L, totalDelDiaComoSusFilas(dia))
    }

    @Test
    fun `en una cuenta de dinero es lo que movio el saldo`() {
        val dia = listOf(ev(TransactionType.INCOME, 1_000_000), ev(TransactionType.EXPENSE, 300_000))
        assertEquals(700_000L, totalDelDiaComoSusFilas(dia))
    }

    @Test
    fun `lo que no es en pesos no se suma`() {
        assertEquals(0L, totalDelDiaComoSusFilas(listOf(ev(TransactionType.EXPENSE, 20, moneda = "USD"))))
    }
}
