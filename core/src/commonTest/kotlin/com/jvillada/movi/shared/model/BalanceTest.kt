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
}
