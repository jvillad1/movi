package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.UpcomingPayment
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Due date in `today`'s month, clamping the rule's day to the month length. */
fun dueDateFor(rule: RecurringRule, today: LocalDate): LocalDate {
    val day = rule.dayOfMonth.coerceIn(1, today.lengthOfMonth())
    return LocalDate.of(today.year, today.month, day)
}

fun statusFor(dueDate: LocalDate, today: LocalDate, leadDays: Int): PaymentStatus = when {
    dueDate.isBefore(today) -> PaymentStatus.OVERDUE
    dueDate.isEqual(today)  -> PaymentStatus.DUE_TODAY
    ChronoUnit.DAYS.between(today, dueDate) <= leadDays -> PaymentStatus.DUE_SOON
    else -> PaymentStatus.UPCOMING
}

fun upcomingPayments(rules: List<RecurringRule>, today: LocalDate, leadDays: Int): List<UpcomingPayment> =
    rules.map { rule ->
        val due = dueDateFor(rule, today)
        UpcomingPayment(
            rule = rule,
            dueDate = due.toString(),
            daysUntil = ChronoUnit.DAYS.between(today, due).toInt(),
            status = statusFor(due, today, leadDays),
        )
    }.sortedBy { it.dueDate }
