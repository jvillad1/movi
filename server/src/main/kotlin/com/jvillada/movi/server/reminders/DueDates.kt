package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Ventana de gracia por defecto, en días.
 *
 * movi no sabe si un pago se hizo: solo conoce la regla y el día. Afirmar "vencido" de forma
 * indefinida sería afirmar un hecho que no puede verificar, así que la afirmación se acota:
 * pasados [DEFAULT_GRACE_DAYS] días del vencimiento, el pago se considera hecho y la fecha
 * rueda al mes siguiente. Cinco días cubren un vencimiento en viernes más el fin de semana
 * y un par de días hábiles de rezago bancario, que es el atraso que sí vale la pena avisar.
 */
const val DEFAULT_GRACE_DAYS: Int = 5

/** La ocurrencia de [dayOfMonth] dentro de [month], recortada al largo real de ese mes. */
fun occurrenceInMonth(month: YearMonth, dayOfMonth: Int): LocalDate =
    month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

/** Periodo "YYYY-MM" de una fecha — la unidad con la que se sella un recordatorio. */
fun periodOf(date: LocalDate): String = YearMonth.from(date).toString()

/**
 * Clave de dedupe del vencimiento actual de una regla.
 *
 * Es el periodo del vencimiento vigente (no el de hoy), calculado con la misma [dueDateFor] que
 * decide el estado del pago. [selectDueForReminder] y [com.jvillada.movi.server.reminders.ReminderScheduler]
 * DEBEN sellar/filtrar con esta misma función — de lo contrario nada garantiza que sus criterios
 * de "ya se avisó este vencimiento" coincidan.
 */
fun reminderKeyFor(rule: RecurringRule, today: LocalDate, graceDays: Int = DEFAULT_GRACE_DAYS): String =
    periodOf(dueDateFor(rule, today, graceDays))

/**
 * Fecha de vencimiento vigente de la regla.
 *
 * Es la ocurrencia de este mes mientras siga adelante o lleve como mucho [graceDays] de atraso;
 * pasada la gracia rueda a la ocurrencia del mes siguiente (recortada al largo de *ese* mes, y
 * con cambio de año en diciembre). Una fecha rodada nunca queda en el pasado, así que nunca
 * vuelve a leerse como OVERDUE.
 */
fun dueDateFor(rule: RecurringRule, today: LocalDate, graceDays: Int = DEFAULT_GRACE_DAYS): LocalDate {
    val thisMonth = YearMonth.from(today)
    val due = occurrenceInMonth(thisMonth, rule.dayOfMonth)
    return if (ChronoUnit.DAYS.between(due, today) > graceDays) {
        occurrenceInMonth(thisMonth.plusMonths(1), rule.dayOfMonth)
    } else {
        due
    }
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
 * Given a list of (RecurringRule, lastRemindedPeriod?) pairs, returns the EXPENSE rules whose
 * due date is OVERDUE, DUE_TODAY, or DUE_SOON, that have `remindMe` on, AND that have not yet
 * been reminded *for that due date*.
 *
 * La unidad de deduplicación es el periodo del **vencimiento**, no el de hoy: cerca de fin de
 * mes [dueDateFor] puede devolver una fecha del mes siguiente, y comparar contra el periodo de
 * hoy notificaría el mismo pago dos veces (una en el mes viejo y otra al cambiar el mes).
 * [ReminderScheduler] sella con [reminderKeyFor], la misma función que filtra aquí.
 *
 * @param rules       pairs of rule + the value of `lastRemindedPeriod` from the DB row
 * @param today       reference date (normally AppClock.today(), la fecha civil de Bogotá)
 * @param leadDays    how many days before due is considered DUE_SOON
 */
fun selectDueForReminder(
    rules: List<Pair<RecurringRule, String?>>,
    today: LocalDate,
    leadDays: Int,
): List<RecurringRule> =
    rules
        .filter { (rule, lastRemindedPeriod) ->
            val due = dueDateFor(rule, today)
            // remindMe primero: si el dueño desmarcó «Recordarme unos días antes» para ESTE
            // pago, no hay nada más que evaluar. El pago sigue existiendo (aparece en Próximos
            // y en los totales) — lo único que se apaga es el aviso.
            rule.remindMe &&
                rule.type == TransactionType.EXPENSE &&
                lastRemindedPeriod != reminderKeyFor(rule, today) &&
                statusFor(due, today, leadDays) != PaymentStatus.UPCOMING
        }
        .map { it.first }
