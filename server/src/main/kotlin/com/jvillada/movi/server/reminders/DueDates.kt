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
fun reminderKeyFor(
    rule: RecurringRule,
    today: LocalDate,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    occurredPeriods: Set<String> = emptySet(),
): String = periodOf(dueDateFor(rule, today, graceDays, occurredPeriods))

/**
 * Cuántos periodos ya-ocurridos seguidos se aguanta [dueDateFor] antes de dejar de rodar.
 *
 * No es un límite de negocio: nadie cierra dos años de periodos por adelantado (el alta ni
 * siquiera deja marcar más allá del vencimiento en juego). Es un tope para que un conjunto raro
 * —una base tocada a mano, un cliente con un bug— no convierta un bucle en un cuelgue del server.
 */
private const val MAX_OCCURRENCE_ROLLS: Int = 24

/**
 * Fecha de vencimiento vigente de la regla.
 *
 * Es la ocurrencia de este mes mientras siga adelante o lleve como mucho [graceDays] de atraso;
 * pasada la gracia rueda a la ocurrencia del mes siguiente (recortada al largo de *ese* mes, y
 * con cambio de año en diciembre). Una fecha rodada nunca queda en el pasado, así que nunca
 * vuelve a leerse como OVERDUE.
 *
 * ## [occurredPeriods] — «esto ya ocurrió», con la misma mecánica que la gracia
 *
 * Son los periodos `"YYYY-MM"` que el dueño ya dio por ocurridos para ESTA regla (ver
 * [com.jvillada.movi.shared.model.RecurringOccurrence]). Un periodo cerrado **rueda al
 * siguiente**, exactamente como ya rodaba un vencimiento pasado de gracia.
 *
 * Se resolvió así —rodando la fecha— y no agregando un estado nuevo a `PaymentStatus`, por dos
 * razones que apuntan al mismo lado:
 *
 *  1. **El APK 1.6 que el dueño tiene instalado.** kotlinx revienta al deserializar un valor de
 *     enum que no conoce: un `PaymentStatus.OCCURRED` le rompería `GET /api/payments/upcoming`
 *     entero, o sea la pantalla, por una función que ni siquiera puede usar. Rodando la fecha, un
 *     cliente viejo simplemente lee la verdad («vence el 25 del mes que viene») sin enterarse de
 *     nada.
 *  2. **Una sola noción de "el vencimiento vigente".** Todo lo que ya deriva de [dueDateFor] —el
 *     estado, el orden de «Próximos», la clave de dedupe de los avisos— hereda el cierre sin que
 *     haya que acordarse de mirar la tabla en cada lugar. En particular
 *     [selectDueForReminder]: un recurrente cerrado deja de avisar ese mes porque su vencimiento
 *     vigente ya es el del mes que viene, no por un `if` aparte que alguien pueda olvidar.
 *
 * Y al mes siguiente vuelve a estar pendiente solo: el periodo nuevo no está en el conjunto.
 */
fun dueDateFor(
    rule: RecurringRule,
    today: LocalDate,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    occurredPeriods: Set<String> = emptySet(),
): LocalDate {
    val thisMonth = YearMonth.from(today)
    val natural = occurrenceInMonth(thisMonth, rule.dayOfMonth)
    var due = if (ChronoUnit.DAYS.between(natural, today) > graceDays) {
        occurrenceInMonth(thisMonth.plusMonths(1), rule.dayOfMonth)
    } else {
        natural
    }
    var rodadas = 0
    while (periodOf(due) in occurredPeriods && rodadas < MAX_OCCURRENCE_ROLLS) {
        due = occurrenceInMonth(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
        rodadas++
    }
    return due
}

fun statusFor(dueDate: LocalDate, today: LocalDate, leadDays: Int): PaymentStatus = when {
    dueDate.isBefore(today) -> PaymentStatus.OVERDUE
    dueDate.isEqual(today)  -> PaymentStatus.DUE_TODAY
    ChronoUnit.DAYS.between(today, dueDate) <= leadDays -> PaymentStatus.DUE_SOON
    else -> PaymentStatus.UPCOMING
}

/**
 * @param occurredBy id de regla → periodos que el dueño ya dio por ocurridos. Lo que no esté en
 *   el mapa se comporta exactamente como antes de esta función, que es lo que hoy ve todo el
 *   mundo: nadie tiene ninguna ocurrencia sellada todavía.
 */
fun upcomingPayments(
    rules: List<RecurringRule>,
    today: LocalDate,
    leadDays: Int,
    occurredBy: Map<String, Set<String>> = emptyMap(),
): List<UpcomingPayment> =
    rules.map { rule ->
        val due = dueDateFor(rule, today, DEFAULT_GRACE_DAYS, occurredBy[rule.id].orEmpty())
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
 * Un vencimiento que el dueño ya dio por ocurrido ([occurredBy]) no avisa: no hay un `if` aparte
 * para eso — su vencimiento vigente ya rodó al mes siguiente (ver [dueDateFor]), así que cae solo
 * en UPCOMING y sale por el filtro que ya estaba. Al mes siguiente vuelve a entrar.
 *
 * @param rules       pairs of rule + the value of `lastRemindedPeriod` from the DB row
 * @param today       reference date (normally AppClock.today(), la fecha civil de Bogotá)
 * @param leadDays    how many days before due is considered DUE_SOON
 * @param occurredBy  id de regla → periodos ya dados por ocurridos
 */
fun selectDueForReminder(
    rules: List<Pair<RecurringRule, String?>>,
    today: LocalDate,
    leadDays: Int,
    occurredBy: Map<String, Set<String>> = emptyMap(),
): List<RecurringRule> =
    rules
        .filter { (rule, lastRemindedPeriod) ->
            val ocurridos = occurredBy[rule.id].orEmpty()
            val due = dueDateFor(rule, today, DEFAULT_GRACE_DAYS, ocurridos)
            // remindMe primero: si el dueño desmarcó «Recordarme unos días antes» para ESTE
            // pago, no hay nada más que evaluar. El pago sigue existiendo (aparece en Próximos
            // y en los totales) — lo único que se apaga es el aviso.
            rule.remindMe &&
                rule.type == TransactionType.EXPENSE &&
                lastRemindedPeriod != reminderKeyFor(rule, today, DEFAULT_GRACE_DAYS, ocurridos) &&
                statusFor(due, today, leadDays) != PaymentStatus.UPCOMING
        }
        .map { it.first }
