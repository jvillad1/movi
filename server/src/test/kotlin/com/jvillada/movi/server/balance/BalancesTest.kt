package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.patrimonioDe
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

    // ── El patrimonio del server (Hallazgo menor 4 de la revisión de `feat/ajustar-saldo`) ────
    //
    // Eran pruebas de `netWorth`, una suma propia del server que se reemplazó por `patrimonioDe`
    // (la regla única, en :core). Se quedan, apuntadas a la regla nueva, porque lo que fijan —que
    // una deuda RESTA— sigue siendo lo que no puede volver a romperse, ahora con saldos derivados
    // por `enrichWith` como los arma `/api/finance-summary`.

    @Test
    fun `el patrimonio resta las cuentas de deuda en vez de sumarlas`() {
        val accountRows = listOf("cash" to AccountType.CASH, "loan" to AccountType.LOAN)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 1_000_000, "COP")),
            "loan" to listOf(ev(TransactionType.EXPENSE, 400_000, "COP")), // deuda = 400.000
        )
        // Antes del fix esto sumaba 1.000.000 + 400.000 = 1.400.000 ("activos + deudas").
        assertEquals(600_000L, neto(accountRows, eventsByAccount))
    }

    @Test
    fun `el patrimonio con solo cuentas de activo es la suma simple`() {
        val accountRows = listOf("cash" to AccountType.CASH, "savings" to AccountType.SAVINGS)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 500_000, "COP")),
            "savings" to listOf(ev(TransactionType.INCOME, 2_000_000, "COP")),
        )
        assertEquals(2_500_000L, neto(accountRows, eventsByAccount))
    }

    @Test
    fun `el patrimonio puede dar negativo cuando la deuda supera los activos`() {
        val accountRows = listOf("cash" to AccountType.CASH, "loan" to AccountType.LOAN)
        val eventsByAccount = mapOf(
            "cash" to listOf(ev(TransactionType.INCOME, 100_000, "COP")),
            "loan" to listOf(ev(TransactionType.EXPENSE, 40_000_000, "COP")),
        )
        assertEquals(100_000L - 40_000_000L, neto(accountRows, eventsByAccount))
    }

    @Test
    fun `un bien suma su valor al patrimonio y no sus movimientos`() {
        // Un movimiento anotado contra la casa (desde un APK viejo que la ve como inversión) no
        // la convierte en plata: `conSaldos` la deja en cero y vale su avalúo.
        val casa = enrichWith(
            Account("casa", "Casa", AccountType.INVESTMENT, 0L, bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L)),
            listOf(ev(TransactionType.INCOME, 5_000_000, "COP")),
            3950.0,
        )
        assertEquals(0L, casa.balance)
        assertEquals(emptyMap(), casa.balancesByCurrency)
        assertNull(casa.estimatedTotalCop)
        assertEquals(1_411_903_920L, patrimonioDe(listOf(casa)).neto)
        assertEquals(0L, patrimonioDe(listOf(casa)).tuPlata)
    }

    private fun neto(
        accountRows: List<Pair<String, AccountType>>,
        eventsByAccount: Map<String, List<FinancialEvent>>,
    ): Long = patrimonioDe(
        accountRows.map { (id, tipo) -> enrichWith(Account(id, id, tipo, 0L), eventsByAccount[id].orEmpty(), 3950.0) },
    ).neto

    private fun ev(t: TransactionType, amount: Long, cur: String) = FinancialEvent(
        id = "x", accountId = "a", type = t, amount = amount, currency = cur,
        category = "Otros", description = "", timestamp = 0L,
    )
}
