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
 *   2. Have NOT already been reminded for that due date
 *      (lastRemindedPeriod != reminderKeyFor(rule, today) — la MISMA función que usa el sellado)
 */
class ReminderSelectionTest {

    // today = 2026-06-13
    private val today    = LocalDate.of(2026, 6, 13)
    private val leadDays = 3
    private val period   = "2026-06"   // YYYY-MM de los vencimientos de junio usados abajo

    private fun expense(day: Int, name: String = "Rule day=$day") =
        RecurringRule("r$day", name, "Otros", 1000, day, TransactionType.EXPENSE)

    private fun income(day: Int) =
        RecurringRule("i$day", "Ingreso day=$day", "Ingresos", 5000, day, TransactionType.INCOME)

    // ── Core window tests ─────────────────────────────────────────────────────

    @Test
    fun `OVERDUE rule (due before today) is selected`() {
        // day 10 in June 2026 = 2026-06-10: 3 días de atraso, dentro de la gracia → OVERDUE
        val pairs = listOf(expense(10) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size)
        assertEquals("r10", result[0].id)
    }

    @Test
    fun `DUE_TODAY rule is selected`() {
        // day 13 in June 2026 = 2026-06-13 = today → DUE_TODAY
        val pairs = listOf(expense(13) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size)
        assertEquals("r13", result[0].id)
    }

    @Test
    fun `DUE_SOON rule within lead window is selected`() {
        // day 15 → 2 days from today, leadDays=3 → DUE_SOON
        val pairs = listOf(expense(15) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size)
        assertEquals("r15", result[0].id)
    }

    @Test
    fun `DUE_SOON rule at exact lead boundary is selected`() {
        // day 16 → 3 days from today = exactly leadDays → DUE_SOON
        val pairs = listOf(expense(16) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size)
        assertEquals("r16", result[0].id)
    }

    @Test
    fun `UPCOMING rule beyond lead window is excluded`() {
        // day 25 → 12 days from today, beyond leadDays=3 → UPCOMING → excluded
        val pairs = listOf(expense(25) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertTrue(result.isEmpty(), "UPCOMING rules must not be selected")
    }

    // ── Period deduplication ──────────────────────────────────────────────────

    @Test
    fun `already reminded this period is excluded`() {
        // day 10 is OVERDUE, but lastRemindedPeriod = el periodo de su vencimiento → skip
        val pairs = listOf(expense(10) to period)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertTrue(result.isEmpty(), "Rule already reminded this period must be excluded")
    }

    @Test
    fun `reminded in a prior period is selected again`() {
        // day 10 is OVERDUE, lastRemindedPeriod = prior month → still needs reminder
        val pairs = listOf(expense(10) to "2026-05")
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size, "Rule reminded in a prior period should be selected again")
    }

    @Test
    fun `null lastRemindedPeriod is treated as never reminded`() {
        // day 13 DUE_TODAY, never reminded → must be selected
        val pairs = listOf(expense(13) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(1, result.size)
    }

    // ── Income rules excluded ─────────────────────────────────────────────────

    @Test
    fun `INCOME rules are excluded even when overdue`() {
        // day 10 → OVERDUE, but type=INCOME → excluded
        val pairs = listOf(income(10) to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertTrue(result.isEmpty(), "INCOME rules must never be selected for reminders")
    }

    // ── Mixed scenarios ───────────────────────────────────────────────────────

    @Test
    fun `mixed list selects only eligible EXPENSE rules`() {
        val alreadyReminded = expense(10).copy(id = "r10b")
        val pairs = listOf(
            expense(10)          to null,    // OVERDUE, not reminded → SELECT
            expense(13)         to null,    // DUE_TODAY, not reminded → SELECT
            expense(15)         to null,    // DUE_SOON (2 days), not reminded → SELECT
            expense(16)         to null,    // DUE_SOON (3 days, exact boundary) → SELECT
            expense(25)         to null,    // UPCOMING → EXCLUDE
            alreadyReminded     to period,  // OVERDUE but already reminded → EXCLUDE
            income(10)           to null,    // INCOME → EXCLUDE
        )
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(4, result.size, "Expected 4 selected: overdue + today + soon + boundary")
        val ids = result.map { it.id }.toSet()
        assertTrue("r10"  in ids)
        assertTrue("r13" in ids)
        assertTrue("r15" in ids)
        assertTrue("r16" in ids)
        assertFalse("r25" in ids,  "UPCOMING should not be selected")
        assertFalse("r10b" in ids,  "Already-reminded-this-period should not be selected")
        assertFalse("i10"  in ids,  "INCOME should not be selected")
    }

    // ── Sellado por periodo del vencimiento ───────────────────────────────────

    @Test
    fun `a payment whose due date rolled into next month is not notified twice`() {
        val rule = expense(1)
        val julyEnd = LocalDate.of(2026, 7, 31)
        // el 31 de julio la ocurrencia del día 1 ya rodó a agosto → DUE_SOON, entra al barrido
        assertEquals(LocalDate.of(2026, 8, 1), dueDateFor(rule, julyEnd))
        assertEquals(1, selectDueForReminder(listOf(rule to null), julyEnd, leadDays).size)

        // el scheduler sella con reminderKeyFor — la MISMA función que usa selectDueForReminder
        // para filtrar (no una expresión espejo), así que este test ejercita el sellado real.
        val sealed = reminderKeyFor(rule, julyEnd)
        assertEquals("2026-08", sealed)

        // el 1 de agosto vence hoy, pero ya se avisó por ese vencimiento → no se repite
        assertTrue(
            selectDueForReminder(listOf(rule to sealed), LocalDate.of(2026, 8, 1), leadDays).isEmpty(),
            "el mismo vencimiento no puede notificarse dos veces",
        )
        // tampoco en el segundo barrido del propio 31 de julio
        assertTrue(selectDueForReminder(listOf(rule to sealed), julyEnd, leadDays).isEmpty())
        // pero el vencimiento de septiembre sí vuelve a avisarse
        assertEquals(1, selectDueForReminder(listOf(rule to sealed), LocalDate.of(2026, 8, 31), leadDays).size)
    }

    @Test
    fun `empty input returns empty result`() {
        val result = selectDueForReminder(emptyList(), today, leadDays)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns RecurringRule objects (not just count)`() {
        val rule = expense(10)
        val pairs = listOf(rule to null)
        val result = selectDueForReminder(pairs, today, leadDays)
        assertEquals(rule, result[0])
    }
}
