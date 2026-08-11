package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BalanceAdjustmentTest {

    private fun loan(balance: Long = 0L, currency: String = "COP") =
        Account(id = "acc-loan", name = "Libranza", type = AccountType.LOAN, balance = balance, currency = currency)

    // ── debtAdjustmentFor ────────────────────────────────────────────────────

    @Test
    fun `subir la deuda al objetivo es un cargo por la diferencia`() {
        val adj = assertNotNull(debtAdjustmentFor(current = 100_000_000L, target = 120_000_000L))
        assertEquals(TransactionType.EXPENSE, adj.type)
        assertEquals(20_000_000L, adj.amount)
    }

    @Test
    fun `bajar la deuda al objetivo es un abono por la diferencia`() {
        val adj = assertNotNull(debtAdjustmentFor(current = 100_000_000L, target = 80_000_000L))
        assertEquals(TransactionType.INCOME, adj.type)
        assertEquals(20_000_000L, adj.amount)
    }

    @Test
    fun `sin diferencia no hay movimiento que registrar`() {
        assertNull(debtAdjustmentFor(current = 226_465_057L, target = 226_465_057L))
    }

    @Test
    fun `llevar la deuda a cero es un abono por todo el saldo`() {
        val adj = assertNotNull(debtAdjustmentFor(current = 5_000_000L, target = 0L))
        assertEquals(TransactionType.INCOME, adj.type)
        assertEquals(5_000_000L, adj.amount)
    }

    @Test
    fun `el ajuste aplicado deja la deuda exactamente en el objetivo`() {
        val opening = openingEventFor(loan(balance = 100_000_000L), now = 1L)!!
        listOf(0L, 1L, 99_999_999L, 100_000_000L, 226_465_057L).forEach { target ->
            val current = computeBalances(AccountType.LOAN, listOf(opening))["COP"]!!
            val adjustment = debtAdjustmentEventFor(loan(), current, target, now = 2L)
            val events = listOf(opening) + listOfNotNull(adjustment)
            assertEquals(target, computeBalances(AccountType.LOAN, events)["COP"] ?: 0L, "objetivo $target")
        }
    }

    // ── debtAdjustmentEventFor ───────────────────────────────────────────────

    @Test
    fun `el evento de ajuste sigue las convenciones del evento de apertura`() {
        val opening  = assertNotNull(openingEventFor(loan(balance = 100_000_000L), now = 1L))
        val adjusted = assertNotNull(debtAdjustmentEventFor(loan(), current = 100_000_000L, target = 120_000_000L, now = 2L))
        // La categoría es lo único que NO sigue a la apertura: el ajuste va bajo nombre propio
        // para no confundirse con un gasto misceláneo ni chocar con un presupuesto "Otros".
        assertEquals(ADJUSTMENT_CATEGORY, adjusted.category)
        assertNotEquals(opening.category, adjusted.category)
        assertEquals(EventSource.MANUAL, adjusted.source)
        assertEquals(ReconciliationStatus.RECONCILED, adjusted.reconciliationStatus)
        assertEquals("acc-loan", adjusted.accountId)
        assertEquals("COP", adjusted.currency)
        assertEquals(2L, adjusted.timestamp)
        assertTrue(adjusted.id.startsWith("ev_"))
    }

    @Test
    fun `sin diferencia no se construye evento`() {
        assertNull(debtAdjustmentEventFor(loan(), current = 7L, target = 7L, now = 2L))
    }

    @Test
    fun `el evento hereda la moneda de la cuenta`() {
        val ev = assertNotNull(
            debtAdjustmentEventFor(loan(currency = "USD"), current = 100L, target = 250L, now = 2L)
        )
        assertEquals("USD", ev.currency)
        assertEquals("Ajuste al saldo del banco — quedó en 250 USD", ev.description)
    }

    // ── descripción ──────────────────────────────────────────────────────────

    @Test
    fun `la descripcion dice contra que saldo del banco se cuadro`() {
        assertEquals(
            "Ajuste al saldo del banco — quedó en $226.465.057",
            adjustmentDescription(226_465_057L, "COP"),
        )
        assertEquals("$0", formatAmount(0L, "COP"))
        assertEquals("$999", formatAmount(999L, "COP"))
        assertEquals("$1.000", formatAmount(1_000L, "COP"))
    }
}
