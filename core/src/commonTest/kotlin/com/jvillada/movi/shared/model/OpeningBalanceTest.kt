package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpeningBalanceTest {

    @Test
    fun `no opening event for zero balance`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 0)
        assertNull(openingEventFor(acc, now = 1000L))
    }

    @Test
    fun `asset account opening balance is INCOME Saldo inicial`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 1_000_000, currency = "COP")
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(TransactionType.INCOME, ev.type)
        assertEquals(1_000_000, ev.amount)
        assertEquals("COP", ev.currency)
        assertEquals("Saldo inicial", ev.description)
        assertEquals(OPENING_CATEGORY, ev.category)
        assertEquals(EventSource.MANUAL, ev.source)
        assertEquals("a", ev.accountId)
        assertEquals(1000L, ev.timestamp)
        // el evento, aplicado con signedDelta, deja el saldo derivado en exactamente lo declarado
        // (mismo cálculo que usa LocalRepository.postEvent y que computeBalances hace del lado
        // del server — no se importa computeBalances acá porque vive en :server, aguas abajo
        // de :core).
        assertEquals(1_000_000L, signedDelta(acc.type, ev.type, ev.amount))
        // F54: la apertura no es un ingreso del mes, sin importar el tipo de cuenta.
        assertEquals(false, isCashFlow(acc.type, ev.type, ev.category))
    }

    @Test
    fun `credit card opening balance is EXPENSE Deuda inicial and raises debt`() {
        val acc = Account(id = "c", name = "tc", type = AccountType.CREDIT_CARD, balance = 222_933, currency = "COP")
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(TransactionType.EXPENSE, ev.type)
        assertEquals("Deuda inicial", ev.description)
        assertEquals(OPENING_CATEGORY, ev.category)
        // derived debt must equal the declared opening debt
        assertEquals(222_933L, signedDelta(acc.type, ev.type, ev.amount))
        // F54: tampoco es un egreso del mes.
        assertEquals(false, isCashFlow(acc.type, ev.type, ev.category))
    }

    @Test
    fun `loan opening balance is EXPENSE Deuda inicial and a payment lowers it`() {
        val acc = Account(id = "l", name = "Crédito Vehículo", type = AccountType.LOAN, balance = 540_786, currency = "COP")
        val opening = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(TransactionType.EXPENSE, opening.type)
        assertEquals("Deuda inicial", opening.description)
        assertEquals(OPENING_CATEGORY, opening.category)
        assertEquals(540_786, opening.amount)
        assertEquals(540_786L, signedDelta(acc.type, opening.type, opening.amount))
        val paymentDelta = signedDelta(acc.type, TransactionType.INCOME, 40_786)
        assertEquals(500_000L, signedDelta(acc.type, opening.type, opening.amount) + paymentDelta)
    }

    @Test
    fun `negative input balance uses magnitude`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = -5_000)
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(5_000, ev.amount)
    }

    @Test
    fun `id por defecto se genera con newId y prefijo ev — cada apertura tiene su propia PK`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 1_000)
        val first = assertNotNull(openingEventFor(acc, now = 1000L))
        val second = assertNotNull(openingEventFor(acc, now = 1000L))
        assertTrue(first.id.startsWith("ev_"))
        assertTrue(first.id != second.id, "dos llamadas sin id explícito no deberían colisionar")
    }

    @Test
    fun `id explicito se respeta — lo usa CreditRoutes para el alta atomica de credito`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 1_000)
        val ev = assertNotNull(openingEventFor(acc, now = 1000L, id = "ev-fijo"))
        assertEquals("ev-fijo", ev.id)
    }
}
