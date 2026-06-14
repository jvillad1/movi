package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
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

/**
 * Pure sweep-selection filter.
 *
 * Given a list of (RecurringRule, lastRemindedPeriod?) pairs, returns the EXPENSE rules
 * whose due date this month is OVERDUE, DUE_TODAY, or DUE_SOON AND that have not yet been
 * reminded this [period] (in "YYYY-MM" form).
 *
 * @param rules       pairs of rule + the value of `lastRemindedPeriod` from the DB row
 * @param today       reference date (normally LocalDate.now(UTC))
 * @param leadDays    how many days before due is considered DUE_SOON
 * @param period      current period string, e.g. "2026-06"
 */
fun selectDueForReminder(
    rules: List<Pair<RecurringRule, String?>>,
    today: LocalDate,
    leadDays: Int,
    period: String,
): List<RecurringRule> =
    rules
        .filter { (rule, lastRemindedPeriod) ->
            rule.type == TransactionType.EXPENSE &&
                lastRemindedPeriod != period &&
                statusFor(dueDateFor(rule, today), today, leadDays) != PaymentStatus.UPCOMING
        }
        .map { it.first }
