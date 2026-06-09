package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultiCurrencyModelTest {

    @Test
    fun `ParsedTransaction defaults currency to COP`() {
        val tx = ParsedTransaction(
            id = "1", date = "2026-05-01", merchant = "X", amount = 100,
            type = TransactionType.EXPENSE, category = "Otros", description = "", rawText = "",
        )
        assertEquals("COP", tx.currency)
    }

    @Test
    fun `FinancialEvent keeps explicit currency through JSON round-trip`() {
        val ev = FinancialEvent(
            id = "ev1", accountId = "acc1", type = TransactionType.EXPENSE, amount = 100,
            category = "Tecnología", description = "Claude", timestamp = 0L, currency = "USD",
        )
        val json = Json.encodeToString(FinancialEvent.serializer(), ev)
        val back = Json.decodeFromString(FinancialEvent.serializer(), json)
        assertEquals("USD", back.currency)
    }

    @Test
    fun `Account defaults multi-currency fields to empty`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 0)
        assertEquals(emptyMap(), acc.balancesByCurrency)
        assertNull(acc.estimatedTotalCop)
    }
}
