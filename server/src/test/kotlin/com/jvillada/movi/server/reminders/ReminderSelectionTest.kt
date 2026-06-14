package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD tests for [selectDueForReminder].
 *
 * The function takes a list of (RecurringRule, lastRemindedPeriod?) pairs and returns the
 * EXPENSE rules that:
 *   1. Have status OVERDUE, DUE_TODAY, or DUE_SOON (within leadDays)
 *   2. Have NOT already been reminded this period (lastRemindedPeriod != period)
 */
class ReminderSelectionTest {

    // today = 2026-06-13
    private val today    = LocalDate.of(2026, 6, 13)
    private val leadDays = 3
    private val period   = "2026-06"   // YYYY-MM of today

    private fun expense(day: Int, name: String = "Rule day=$day") =
        RecurringRule("r$day", name, "Otros", 1000, day, TransactionType.EXPENSE)

    private fun income(day: Int) =
        RecurringRule("i$day", "Ingreso day=$day", "Ingresos", 5000, day, TransactionType.INCOME)

    // ── Core window tests ─────────────────────────────────────────────────────

    @Test
    fun `OVERDUE rule (due before today) is selected`() {
        // day 5 in June 2026 = 2026-06-05, which is before 2026-06-13 → OVERDUE
        val pairs = listOf(expense(5) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size)
        assertEquals("r5", result[0].id)
    }

    @Test
    fun `DUE_TODAY rule is selected`() {
        // day 13 in June 2026 = 2026-06-13 = today → DUE_TODAY
        val pairs = listOf(expense(13) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size)
        assertEquals("r13", result[0].id)
    }

    @Test
    fun `DUE_SOON rule within lead window is selected`() {
        // day 15 → 2 days from today, leadDays=3 → DUE_SOON
        val pairs = listOf(expense(15) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size)
        assertEquals("r15", result[0].id)
    }

    @Test
    fun `DUE_SOON rule at exact lead boundary is selected`() {
        // day 16 → 3 days from today = exactly leadDays → DUE_SOON
        val pairs = listOf(expense(16) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size)
        assertEquals("r16", result[0].id)
    }

    @Test
    fun `UPCOMING rule beyond lead window is excluded`() {
        // day 25 → 12 days from today, beyond leadDays=3 → UPCOMING → excluded
        val pairs = listOf(expense(25) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertTrue(result.isEmpty(), "UPCOMING rules must not be selected")
    }

    // ── Period deduplication ──────────────────────────────────────────────────

    @Test
    fun `already reminded this period is excluded`() {
        // day 5 is OVERDUE, but lastRemindedPeriod = current period → skip
        val pairs = listOf(expense(5) to period)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertTrue(result.isEmpty(), "Rule already reminded this period must be excluded")
    }

    @Test
    fun `reminded in a prior period is selected again`() {
        // day 5 is OVERDUE, lastRemindedPeriod = prior month → still needs reminder
        val pairs = listOf(expense(5) to "2026-05")
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size, "Rule reminded in a prior period should be selected again")
    }

    @Test
    fun `null lastRemindedPeriod is treated as never reminded`() {
        // day 13 DUE_TODAY, never reminded → must be selected
        val pairs = listOf(expense(13) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(1, result.size)
    }

    // ── Income rules excluded ─────────────────────────────────────────────────

    @Test
    fun `INCOME rules are excluded even when overdue`() {
        // day 5 → OVERDUE, but type=INCOME → excluded
        val pairs = listOf(income(5) to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertTrue(result.isEmpty(), "INCOME rules must never be selected for reminders")
    }

    // ── Mixed scenarios ───────────────────────────────────────────────────────

    @Test
    fun `mixed list selects only eligible EXPENSE rules`() {
        val alreadyReminded = expense(5).copy(id = "r5b")
        val pairs = listOf(
            expense(5)          to null,    // OVERDUE, not reminded → SELECT
            expense(13)         to null,    // DUE_TODAY, not reminded → SELECT
            expense(15)         to null,    // DUE_SOON (2 days), not reminded → SELECT
            expense(16)         to null,    // DUE_SOON (3 days, exact boundary) → SELECT
            expense(25)         to null,    // UPCOMING → EXCLUDE
            alreadyReminded     to period,  // OVERDUE but already reminded → EXCLUDE
            income(5)           to null,    // INCOME → EXCLUDE
        )
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(4, result.size, "Expected 4 selected: overdue + today + soon + boundary")
        val ids = result.map { it.id }.toSet()
        assertTrue("r5"  in ids)
        assertTrue("r13" in ids)
        assertTrue("r15" in ids)
        assertTrue("r16" in ids)
        assertFalse("r25" in ids,  "UPCOMING should not be selected")
        assertFalse("r5b" in ids,  "Already-reminded-this-period should not be selected")
        assertFalse("i5"  in ids,  "INCOME should not be selected")
    }

    @Test
    fun `empty input returns empty result`() {
        val result = selectDueForReminder(emptyList(), today, leadDays, period)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns RecurringRule objects (not just count)`() {
        val rule = expense(5)
        val pairs = listOf(rule to null)
        val result = selectDueForReminder(pairs, today, leadDays, period)
        assertEquals(rule, result[0])
    }
}
