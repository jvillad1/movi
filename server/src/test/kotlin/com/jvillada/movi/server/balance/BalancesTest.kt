package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.signedDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Los tests de signedDelta en sí viven en :core (BalanceTest.kt), junto a la función —se movió
// ahí para que cliente y server compartan una sola definición del signo. Acá se sigue usando
// (importada) para blindar computeBalances/accountCopValue, que sí son server-only.
class BalancesTest {

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

    // ── netWorth (Hallazgo menor 4 de la revisión de `feat/ajustar-saldo`) ──────────────────

    @Test
    fun `netWorth resta las cuentas de deuda en vez de sumarlas`() {
        val accountRows = listOf("cash" to AccountType.CASH, "loan" to AccountType.LOAN)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 1_000_000, "COP")),
            "loan" to listOf(ev(TransactionType.EXPENSE, 400_000, "COP")), // deuda = 400.000
        )
        // Antes del fix esto sumaba 1.000.000 + 400.000 = 1.400.000 ("activos + deudas").
        assertEquals(600_000L, netWorth(accountRows, eventsByAccount, 3950.0))
    }

    @Test
    fun `netWorth con solo cuentas de activo es la suma simple`() {
        val accountRows = listOf("cash" to AccountType.CASH, "savings" to AccountType.SAVINGS)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 500_000, "COP")),
            "savings" to listOf(ev(TransactionType.INCOME, 2_000_000, "COP")),
        )
        assertEquals(2_500_000L, netWorth(accountRows, eventsByAccount, 3950.0))
    }

    @Test
    fun `netWorth puede dar negativo cuando la deuda supera los activos`() {
        val accountRows = listOf("cash" to AccountType.CASH, "loan" to AccountType.LOAN)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 100_000, "COP")),
            "loan" to listOf(ev(TransactionType.EXPENSE, 40_000_000, "COP")),
        )
        assertEquals(100_000L - 40_000_000L, netWorth(accountRows, eventsByAccount, 3950.0))
    }

    private fun ev(t: TransactionType, amount: Long, cur: String) = FinancialEvent(
        id = "x", accountId = "a", type = t, amount = amount, currency = cur,
        category = "Otros", description = "", timestamp = 0L,
    )
}
