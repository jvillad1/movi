package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.TransactionType
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
        assertEquals("Otros ingresos", ev.category)
        assertEquals(EventSource.MANUAL, ev.source)
        assertEquals("a", ev.accountId)
        assertEquals(1000L, ev.timestamp)
        // derived balance must equal the declared opening balance
        assertEquals(1_000_000, computeBalances(acc.type, listOf(ev))["COP"])
    }

    @Test
    fun `credit card opening balance is EXPENSE Deuda inicial and raises debt`() {
        val acc = Account(id = "c", name = "tc", type = AccountType.CREDIT_CARD, balance = 222_933, currency = "COP")
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(TransactionType.EXPENSE, ev.type)
        assertEquals("Deuda inicial", ev.description)
        assertEquals("Otros", ev.category)
        // derived debt must equal the declared opening debt
        assertEquals(222_933, computeBalances(acc.type, listOf(ev))["COP"])
    }

    @Test
    fun `negative input balance uses magnitude`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = -5_000)
        val ev = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(5_000, ev.amount)
    }
}
