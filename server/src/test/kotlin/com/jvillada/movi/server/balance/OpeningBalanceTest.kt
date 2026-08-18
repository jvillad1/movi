package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

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
        // derived balance must equal the declared opening balance
        assertEquals(1_000_000, computeBalances(acc.type, listOf(ev))["COP"])
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
        assertEquals(222_933, computeBalances(acc.type, listOf(ev))["COP"])
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
        assertEquals(540_786, computeBalances(acc.type, listOf(opening))["COP"])
        val payment = opening.copy(id = "pay", type = TransactionType.INCOME, amount = 40_786)
        assertEquals(500_000, computeBalances(acc.type, listOf(opening, payment))["COP"])
    }

    @Test
    fun `negative input balance uses magnitude`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = -5_000)
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(5_000, ev.amount)
    }
}
