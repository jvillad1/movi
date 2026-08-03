package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DueDatesTest {
    private fun rule(day: Int) = RecurringRule("r$day", "Pago", "Otros", 1000, day, TransactionType.EXPENSE)

    /**
     * Espejo deliberado del default de producción (`DEFAULT_GRACE_DAYS`), no una referencia:
     * si alguien cambia el default, estos tests deben fallar y revisarse a conciencia.
     */
    private val grace = 5

    @Test fun `due date is the day of the current month`() {
        // hoy 3 y la regla vence el 5: el día sigue adelante → mes en curso (comportamiento intacto)
        assertEquals(LocalDate.of(2026, 6, 5), dueDateFor(rule(5), LocalDate.of(2026, 6, 3)))
    }

    @Test fun `day past month length clamps to last day`() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 10)))
    }

    // ── Ventana de gracia ─────────────────────────────────────────────────────

    @Test fun `day already passed but still within grace stays in the current month`() {
        // día 5, hoy 10 → 5 días de atraso = exactamente la gracia → sigue siendo junio
        val due = dueDateFor(rule(5), LocalDate.of(2026, 6, 10))
        assertEquals(LocalDate.of(2026, 6, 5), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, LocalDate.of(2026, 6, 10), 3))
    }

    @Test fun `day passed beyond grace rolls to next month and is not overdue`() {
        // día 5, hoy 13 → 8 días de atraso > gracia → rueda a julio, ya no se afirma vencido
        val due = dueDateFor(rule(5), LocalDate.of(2026, 6, 13))
        assertEquals(LocalDate.of(2026, 7, 5), due)
        assertEquals(PaymentStatus.UPCOMING, statusFor(due, LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `day 31 clamps to the length of the month it lands in`() {
        assertEquals(LocalDate.of(2026, 1, 31), occurrenceInMonth(YearMonth.of(2026, 1), 31))
        assertEquals(LocalDate.of(2026, 4, 30), occurrenceInMonth(YearMonth.of(2026, 4), 31))  // 31 → mes de 30
        assertEquals(LocalDate.of(2026, 2, 28), occurrenceInMonth(YearMonth.of(2026, 2), 31))  // 31 → febrero
        assertEquals(LocalDate.of(2028, 2, 29), occurrenceInMonth(YearMonth.of(2028, 2), 31))  // febrero bisiesto
    }

    @Test fun `rolling forward clamps to the length of the NEXT month, not the current one`() {
        // con gracia 0, el día 30 de enero rueda a "30 de febrero" → debe recortar a 28, no reventar
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(30), LocalDate.of(2026, 1, 31), graceDays = 0))
        // y desde un mes de 31 hacia uno de 30: día 29 de marzo con gracia 0 → 29 de abril
        assertEquals(LocalDate.of(2026, 4, 29), dueDateFor(rule(29), LocalDate.of(2026, 3, 31), graceDays = 0))
    }

    @Test fun `a rule pinned to the end of the month never rolls forward`() {
        // el día 31 (o el recorte al último día) nunca puede quedar atrasado: hoy no pasa del fin de mes
        assertEquals(LocalDate.of(2026, 1, 31), dueDateFor(rule(31), LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 28)))
    }

    @Test fun `rolling forward in December increments the year`() {
        assertEquals(LocalDate.of(2027, 1, 5), dueDateFor(rule(5), LocalDate.of(2026, 12, 20)))
    }

    @Test fun `no due date is ever more than the grace window in the past`() {
        // barrido: ningún día de regla contra ningún día del mes puede afirmar un atraso ilimitado
        for (day in 1..31) {
            for (dom in 1..31) {
                val today = LocalDate.of(2026, 1, dom)
                val due = dueDateFor(rule(day), today)
                val daysLate = ChronoUnit.DAYS.between(due, today)
                assertTrue(
                    daysLate <= grace,
                    "regla día=$day, hoy=$today → $due son $daysLate días de atraso",
                )
            }
        }
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
        // día 10: 3 días de atraso, dentro de la gracia → sigue en junio y sigue siendo OVERDUE
        val rules = listOf(rule(25), rule(10), rule(13))
        val out = upcomingPayments(rules, LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf(10, 13, 25), out.map { LocalDate.parse(it.dueDate).dayOfMonth })
        assertEquals(PaymentStatus.OVERDUE, out[0].status)   // day 10 < 13
        assertEquals(-3, out[0].daysUntil)
        assertEquals(PaymentStatus.DUE_TODAY, out[1].status) // day 13
        assertEquals(0, out[1].daysUntil)
    }

    @Test fun `upcomingPayments shows a payment past its grace as next month's, at the end`() {
        val out = upcomingPayments(listOf(rule(5), rule(25)), LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf("2026-06-25", "2026-07-05"), out.map { it.dueDate })
        assertEquals(PaymentStatus.UPCOMING, out[1].status)
        assertEquals(22, out[1].daysUntil)
    }
}
