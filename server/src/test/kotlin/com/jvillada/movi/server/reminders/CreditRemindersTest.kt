package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditRemindersTest {

    private val terms = CreditTerms(
        accountId = "acc-loan-1", bank = "Santander", principal = 160_000_000,
        rateEa = 21.56, termMonths = 72, installment = 4_550_030,
        dayOfMonth = 25, startDate = "2025-11-25",
    )

    @Test
    fun `virtual rule maps terms to an EXPENSE recurring rule`() {
        val rule = virtualRuleFor(terms, accountName = "Crédito Vehículo")
        assertEquals("credit_acc-loan-1", rule.id)
        assertEquals("Cuota Crédito Vehículo", rule.name)
        assertEquals("Créditos", rule.category)
        assertEquals(4_550_030, rule.amount)
        assertEquals(25, rule.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, rule.type)
    }

    @Test
    fun `due virtual rule enters the reminder sweep`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)  // un día antes del día 25
        val selected = selectDueForReminder(listOf(rule to null), today, leadDays = 3, period = "2026-07")
        assertEquals(listOf(rule), selected)
    }

    @Test
    fun `already-reminded virtual rule is excluded this period`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(rule to "2026-07"), today, leadDays = 3, period = "2026-07")
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `manual rule with the same name coexists with the virtual one`() {
        val virtual = virtualRuleFor(terms, "Crédito Vehículo")
        val manual = virtual.copy(id = "rr_manual-dup")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(virtual to null, manual to null), today, leadDays = 3, period = "2026-07")
        assertEquals(2, selected.size)  // conviven por diseño; la de-duplicación es manual (siembra)
    }
}
