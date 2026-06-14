package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DueDatesTest {
    private fun rule(day: Int) = RecurringRule("r$day", "Pago", "Otros", 1000, day, TransactionType.EXPENSE)

    @Test fun `due date is the day of the current month`() {
        assertEquals(LocalDate.of(2026, 6, 5), dueDateFor(rule(5), LocalDate.of(2026, 6, 13)))
    }

    @Test fun `day past month length clamps to last day`() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 10)))
    }

    @Test fun `status overdue when due before today`() {
        assertEquals(PaymentStatus.OVERDUE, statusFor(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due today`() {
        assertEquals(PaymentStatus.DUE_TODAY, statusFor(LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due soon within lead`() {
        assertEquals(PaymentStatus.DUE_SOON, statusFor(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status upcoming beyond lead`() {
        assertEquals(PaymentStatus.UPCOMING, statusFor(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `upcomingPayments sorts by due date and computes fields`() {
        val rules = listOf(rule(25), rule(5), rule(13))
        val out = upcomingPayments(rules, LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf(5, 13, 25), out.map { LocalDate.parse(it.dueDate).dayOfMonth })
        assertEquals(PaymentStatus.OVERDUE, out[0].status)   // day 5 < 13
        assertEquals(-8, out[0].daysUntil)
        assertEquals(PaymentStatus.DUE_TODAY, out[1].status) // day 13
        assertEquals(0, out[1].daysUntil)
    }
}
