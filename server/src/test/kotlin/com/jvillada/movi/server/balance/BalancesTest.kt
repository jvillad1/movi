package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalancesTest {

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
    fun `computeBalances groups by currency with credit-card signs`() {
        val evs = listOf(
            ev(TransactionType.EXPENSE, 100, "USD"),  // compra USD  -> +100 debt
            ev(TransactionType.EXPENSE, 50_000, "COP"), // compra COP -> +50000 debt
            ev(TransactionType.INCOME, 20_000, "COP"),  // abono COP  -> -20000 debt
        )
        val balances = computeBalances(AccountType.CREDIT_CARD, evs)
        assertEquals(100, balances["USD"])
        assertEquals(30_000, balances["COP"])
    }

    @Test
    fun `estimatedTotalCop adds foreign converted at rate`() {
        val balances = mapOf("COP" to 30_000L, "USD" to 100L)
        // 30000 + 100*3950 = 425000
        assertEquals(425_000, estimatedTotalCop(balances, 3950.0))
    }

    @Test
    fun `estimatedTotalCop is null when only COP`() {
        assertNull(estimatedTotalCop(mapOf("COP" to 30_000L), 3950.0))
    }

    private fun ev(t: TransactionType, amount: Long, cur: String) = FinancialEvent(
        id = "x", accountId = "a", type = t, amount = amount, currency = cur,
        category = "Otros", description = "", timestamp = 0L,
    )
}
