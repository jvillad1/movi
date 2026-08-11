package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fija la matriz de `isCashFlow`: qué combinación de (tipo de cuenta, tipo de movimiento,
 * categoría) cuenta como ingreso/egreso del mes. Ver el KDoc de [isCashFlow] para el porqué
 * de cada regla — estos tests solo blindan el comportamiento.
 */
class CashFlowTest {

    // ── Cuentas de activo: siempre cuentan, sin importar el tipo de movimiento ─────────────

    @Test
    fun `CASH con INCOME cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.CASH, TransactionType.INCOME, "cat_salary"))
    }

    @Test
    fun `CASH con EXPENSE cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.CASH, TransactionType.EXPENSE, "cat_food"))
    }

    @Test
    fun `CHECKING con INCOME cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.CHECKING, TransactionType.INCOME, "cat_salary"))
    }

    @Test
    fun `CHECKING con EXPENSE cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.CHECKING, TransactionType.EXPENSE, "cat_food"))
    }

    @Test
    fun `SAVINGS con INCOME cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.SAVINGS, TransactionType.INCOME, "cat_salary"))
    }

    @Test
    fun `SAVINGS con EXPENSE cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, "cat_food"))
    }

    @Test
    fun `INVESTMENT con INCOME cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.INVESTMENT, TransactionType.INCOME, "cat_invest_inc"))
    }

    @Test
    fun `INVESTMENT con EXPENSE cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.INVESTMENT, TransactionType.EXPENSE, "cat_other_exp"))
    }

    // ── CREDIT_CARD: solo la compra (EXPENSE) es flujo de caja ──────────────────────────────

    @Test
    fun `CREDIT_CARD con EXPENSE — la compra — cuenta como flujo de caja`() {
        assertTrue(isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, "cat_food"))
    }

    @Test
    fun `CREDIT_CARD con INCOME — el pago del extracto — no cuenta como flujo de caja`() {
        assertFalse(isCashFlow(AccountType.CREDIT_CARD, TransactionType.INCOME, "cat_other_inc"))
    }

    // ── LOAN: nunca cuenta, ni la cuota ni el desembolso ─────────────────────────────────────

    @Test
    fun `LOAN con INCOME — el abono a la cuota — no cuenta como flujo de caja`() {
        assertFalse(isCashFlow(AccountType.LOAN, TransactionType.INCOME, "cat_other_inc"))
    }

    @Test
    fun `LOAN con EXPENSE no cuenta como flujo de caja`() {
        assertFalse(isCashFlow(AccountType.LOAN, TransactionType.EXPENSE, "cat_other_exp"))
    }

    // ── Categoría "Pago de tarjeta": traslado, gana sobre la regla de la cuenta ──────────────

    @Test
    fun `Pago de tarjeta en una cuenta de activo no cuenta como flujo de caja — es el traslado, no el gasto`() {
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
    }

    @Test
    fun `Pago de tarjeta en la propia tarjeta de credito no cuenta como flujo de caja`() {
        assertFalse(isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
    }

    @Test
    fun `Pago de tarjeta en un prestamo no cuenta como flujo de caja — ya era false por LOAN`() {
        assertFalse(isCashFlow(AccountType.LOAN, TransactionType.INCOME, CARD_PAYMENT_CATEGORY))
    }
}
