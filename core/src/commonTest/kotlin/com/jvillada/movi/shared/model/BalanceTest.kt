package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceTest {

    // ── signedDelta (movida acá desde :server — ver su KDoc) ────────────────────────────────

    @Test
    fun `signedDelta on asset account income adds expense subtracts`() {
        assertEquals(100, signedDelta(AccountType.SAVINGS, TransactionType.INCOME, 100))
        assertEquals(-100, signedDelta(AccountType.SAVINGS, TransactionType.EXPENSE, 100))
    }

    @Test
    fun `signedDelta on credit card purchase raises debt payment lowers it`() {
        assertEquals(100, signedDelta(AccountType.CREDIT_CARD, TransactionType.EXPENSE, 100)) // compra
        assertEquals(-100, signedDelta(AccountType.CREDIT_CARD, TransactionType.INCOME, 100)) // abono
    }

    @Test
    fun `signedDelta on loan purchase raises debt payment lowers it`() {
        assertEquals(100, signedDelta(AccountType.LOAN, TransactionType.EXPENSE, 100)) // desembolso/deuda
        assertEquals(-100, signedDelta(AccountType.LOAN, TransactionType.INCOME, 100)) // pago cuota
    }

    // ── accountDayTotal (Hallazgo bloqueante 1 de la revisión de `feat/ajustar-saldo`) ──────

    private fun ev(type: TransactionType, amount: Long, category: String = "test", currency: String = "COP") =
        FinancialEvent(
            id = "ev", accountId = "acc-loan", type = type, amount = amount, currency = currency,
            category = category, description = "test", timestamp = 0L,
        )

    /**
     * El escenario exacto del hallazgo: libranza de $100.000.000, "Ajustar saldo" a $40.000.000
     * registra un INCOME de $60.000.000 (el abono que baja la deuda — ver [debtAdjustmentFor]
     * en `:server`). El detalle de la cuenta mostraba "+$60.000.000" ese día, usando la
     * convención de cuenta de activo (INCOME suma). Lo correcto: la deuda BAJÓ $60.000.000,
     * así que el total del día tiene que dar negativo.
     */
    @Test
    fun `accountDayTotal en una cuenta LOAN usa la convencion de deuda, no la de activo`() {
        val abono = ev(TransactionType.INCOME, 60_000_000L)
        assertEquals(-60_000_000L, accountDayTotal(AccountType.LOAN, listOf(abono)))
    }

    @Test
    fun `accountDayTotal en una cuenta LOAN con un EXPENSE sube la deuda`() {
        val desembolso = ev(TransactionType.EXPENSE, 5_000_000L)
        assertEquals(5_000_000L, accountDayTotal(AccountType.LOAN, listOf(desembolso)))
    }

    @Test
    fun `accountDayTotal en CREDIT_CARD sigue la misma convencion que el saldo`() {
        val compra = ev(TransactionType.EXPENSE, 100_000L)
        val abono = ev(TransactionType.INCOME, 40_000L)
        assertEquals(60_000L, accountDayTotal(AccountType.CREDIT_CARD, listOf(compra, abono)))
    }

    /** Control: en una cuenta de activo el resultado es el mismo de siempre (INCOME suma). */
    @Test
    fun `accountDayTotal en una cuenta de activo no cambia respecto al calculo anterior`() {
        val ingreso = ev(TransactionType.INCOME, 1_000_000L)
        val gasto = ev(TransactionType.EXPENSE, 300_000L)
        assertEquals(700_000L, accountDayTotal(AccountType.CASH, listOf(ingreso, gasto)))
    }

    @Test
    fun `accountDayTotal ignora eventos en otra moneda, igual que el resto de la pantalla`() {
        val usd = ev(TransactionType.INCOME, 100L, currency = "USD")
        assertEquals(0L, accountDayTotal(AccountType.SAVINGS, listOf(usd)))
    }
}
