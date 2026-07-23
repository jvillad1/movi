package com.jvillada.movi.server.push

import com.jvillada.movi.server.reminders.dueDateFor
import com.jvillada.movi.server.reminders.statusFor
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

private const val MAX_LINES = 3

/** JSON {title, body, url} para la notificación. Mismo copy de estado que el email. */
fun buildPushPayload(selected: List<RecurringRule>, today: LocalDate, leadDays: Int): String {
    val lines = selected.take(MAX_LINES).map { rule ->
        val due = dueDateFor(rule, today)
        val daysAgo = ChronoUnit.DAYS.between(due, today).toInt()
        val daysUntil = ChronoUnit.DAYS.between(today, due).toInt()
        val estado = when (statusFor(due, today, leadDays)) {
            PaymentStatus.OVERDUE   -> "vencido hace $daysAgo ${if (daysAgo == 1) "día" else "días"}"
            PaymentStatus.DUE_TODAY -> "vence hoy"
            PaymentStatus.DUE_SOON  -> "vence en $daysUntil ${if (daysUntil == 1) "día" else "días"}"
            PaymentStatus.UPCOMING  -> "próximamente"
        }
        "${rule.name} — $${formatMiles(rule.amount)} ($estado)"
    }
    val extra = selected.size - MAX_LINES
    val body = (lines + if (extra > 0) listOf("…y $extra más") else emptyList()).joinToString("\n")
    return buildJsonObject {
        put("title", "Pagos próximos en movi")
        put("body", body)
        put("url", "/")
    }.toString()
}

/** Push para SMS bancarios recién capturados. Los montos de SMS COP son enteros → roundToLong. */
fun buildSmsPushPayload(parsed: List<ParsedSms>): String {
    val lines = parsed.take(MAX_LINES).map { "$${formatMiles(it.amount.roundToLong())} en ${it.merchant}" }
    val extra = parsed.size - MAX_LINES
    val allLines = lines + if (extra > 0) listOf("…y $extra más") else emptyList()
    val single = parsed.size == 1
    val body = if (single) "${allLines.first()} — toca para confirmar" else allLines.joinToString("\n")
    return buildJsonObject {
        put("title", if (single) "Nuevo movimiento" else "${parsed.size} movimientos nuevos")
        put("body", body)
        put("url", "/")
    }.toString()
}

private fun formatMiles(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(".").reversed()
