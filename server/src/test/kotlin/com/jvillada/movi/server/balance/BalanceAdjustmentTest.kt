package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.openingEventFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BalanceAdjustmentTest {

    private fun loan(balance: Long = 0L, currency: String = "COP") =
        Account(id = "acc-loan", name = "Libranza", type = AccountType.LOAN, balance = balance, currency = currency)

    // ── balanceAdjustmentFor ────────────────────────────────────────────────────

    @Test
    fun `subir la deuda al objetivo es un cargo por la diferencia`() {
        val adj = assertNotNull(balanceAdjustmentFor(AccountType.LOAN, current = 100_000_000L, target = 120_000_000L))
        assertEquals(TransactionType.EXPENSE, adj.type)
        assertEquals(20_000_000L, adj.amount)
    }

    @Test
    fun `bajar la deuda al objetivo es un abono por la diferencia`() {
        val adj = assertNotNull(balanceAdjustmentFor(AccountType.LOAN, current = 100_000_000L, target = 80_000_000L))
        assertEquals(TransactionType.INCOME, adj.type)
        assertEquals(20_000_000L, adj.amount)
    }

    @Test
    fun `sin diferencia no hay movimiento que registrar`() {
        assertNull(balanceAdjustmentFor(AccountType.LOAN, current = 226_465_057L, target = 226_465_057L))
    }

    @Test
    fun `llevar la deuda a cero es un abono por todo el saldo`() {
        val adj = assertNotNull(balanceAdjustmentFor(AccountType.LOAN, current = 5_000_000L, target = 0L))
        assertEquals(TransactionType.INCOME, adj.type)
        assertEquals(5_000_000L, adj.amount)
    }

    @Test
    fun `el ajuste aplicado deja la deuda exactamente en el objetivo`() {
        val opening = openingEventFor(loan(balance = 100_000_000L), now = 1L)!!
        listOf(0L, 1L, 99_999_999L, 100_000_000L, 226_465_057L).forEach { target ->
            val current = computeBalances(AccountType.LOAN, listOf(opening))["COP"]!!
            val adjustment = balanceAdjustmentEventFor(loan(), current, target, now = 2L)
            val events = listOf(opening) + listOfNotNull(adjustment)
            assertEquals(target, computeBalances(AccountType.LOAN, events)["COP"] ?: 0L, "objetivo $target")
        }
    }

    // ── balanceAdjustmentEventFor ───────────────────────────────────────────────

    @Test
    fun `el evento de ajuste sigue las convenciones del evento de apertura`() {
        val opening  = assertNotNull(openingEventFor(loan(balance = 100_000_000L), now = 1L))
        val adjusted = assertNotNull(balanceAdjustmentEventFor(loan(), current = 100_000_000L, target = 120_000_000L, now = 2L))
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
        assertNull(balanceAdjustmentEventFor(loan(), current = 7L, target = 7L, now = 2L))
    }

    @Test
    fun `el evento hereda la moneda de la cuenta`() {
        val ev = assertNotNull(
            balanceAdjustmentEventFor(loan(currency = "USD"), current = 100L, target = 250L, now = 2L)
        )
        assertEquals("USD", ev.currency)
        assertEquals("Ajuste al saldo del banco — quedó en 250 USD", ev.description)
    }

    // ── cuentas de activo (el cuadre de saldos) ──────────────────────────────

    private fun ahorros(balance: Long = 0L, currency: String = "COP") =
        Account(id = "acc-nu", name = "Nu", type = AccountType.SAVINGS, balance = balance, currency = currency)

    /**
     * **El signo va al revés que en una deuda, y esto es lo que lo fija.** En una cuenta de ahorros
     * subir el saldo es un INGRESO; con la función vieja —que solo sabía de deudas— habría sido un
     * GASTO, o sea que cuadrar los $745.856 de rendimientos de Nu habría movido la plata para el
     * lado contrario del que dice la pantalla.
     */
    @Test
    fun `subir el saldo de una cuenta de ahorros es un ingreso`() {
        val adj = assertNotNull(balanceAdjustmentFor(AccountType.SAVINGS, current = 352_082L, target = 1_097_938L))
        assertEquals(TransactionType.INCOME, adj.type)
        assertEquals(745_856L, adj.amount)
    }

    @Test
    fun `bajar el saldo de una cuenta de ahorros es un gasto`() {
        val adj = assertNotNull(balanceAdjustmentFor(AccountType.SAVINGS, current = 352_082L, target = 270_730L))
        assertEquals(TransactionType.EXPENSE, adj.type)
        assertEquals(81_352L, adj.amount)
    }

    @Test
    fun `sin diferencia tampoco se escribe nada en una cuenta de activo`() {
        assertNull(balanceAdjustmentFor(AccountType.INVESTMENT, current = 5L, target = 5L))
        assertNull(balanceAdjustmentEventFor(ahorros(), current = 5L, target = 5L, now = 2L))
    }

    /** Aplicado sobre los eventos, el saldo queda exactamente en lo que dijo el banco. */
    @Test
    fun `el cuadre deja la cuenta de ahorros exactamente en el saldo del banco`() {
        val apertura = assertNotNull(openingEventFor(ahorros(balance = 352_082L), now = 1L))
        listOf(0L, 1L, 270_730L, 352_082L, 1_097_938L).forEach { objetivo ->
            val actual = computeBalances(AccountType.SAVINGS, listOf(apertura))["COP"]!!
            val ajuste = balanceAdjustmentEventFor(ahorros(), actual, objetivo, now = 2L)
            val eventos = listOf(apertura) + listOfNotNull(ajuste)
            assertEquals(objetivo, computeBalances(AccountType.SAVINGS, eventos)["COP"] ?: 0L, "objetivo $objetivo")
        }
    }

    /**
     * **La honestidad del ajuste, fijada donde se construye.** Un ajuste corrige lo que Movi CREÍA;
     * no es plata que entró ni que salió. En una cuenta de ahorros eso no se cumple solo: el evento
     * es un INCOME o un EXPENSE común y corriente, y lo único que lo deja fuera de los totales del
     * período es su categoría reservada. Si alguien le cambiara la categoría acá, el cuadre de Nu
     * aparecería como «+$745.856 de ingresos del mes».
     */
    @Test
    fun `el ajuste de una cuenta de ahorros no cuenta como ingreso ni gasto del periodo`() {
        val ajuste = assertNotNull(
            balanceAdjustmentEventFor(ahorros(), current = 352_082L, target = 1_097_938L, now = 2L),
        )
        assertEquals(ADJUSTMENT_CATEGORY, ajuste.category)
        assertFalse(isCashFlow(AccountType.SAVINGS, ajuste.type, ajuste.category))
        assertFalse(cuentaEnGastosEIngresos(ajuste.withCashFlowFlag(mapOf("acc-nu" to AccountType.SAVINGS))))
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
