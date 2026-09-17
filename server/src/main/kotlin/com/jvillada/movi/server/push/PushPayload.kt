package com.jvillada.movi.server.push

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.server.reminders.DEFAULT_GRACE_DAYS
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
fun buildPushPayload(
    selected: List<RecurringRule>,
    today: LocalDate,
    leadDays: Int,
    // Los mismos periodos ocurridos con los que se eligió qué avisar; ver `buildHtmlEmail`.
    occurredBy: Map<String, Set<String>> = emptyMap(),
    settings: PeriodSettings = PeriodSettings(),
): String {
    val lines = selected.take(MAX_LINES).map { rule ->
        val due = dueDateFor(rule, today, DEFAULT_GRACE_DAYS, occurredBy[rule.id].orEmpty(), settings)
        val daysAgo = ChronoUnit.DAYS.between(due, today).toInt()
        val daysUntil = ChronoUnit.DAYS.between(today, due).toInt()
        val estado = when (statusFor(due, today, leadDays)) {
            PaymentStatus.OVERDUE   -> "vencido hace $daysAgo ${if (daysAgo == 1) "día" else "días"}"
            PaymentStatus.DUE_TODAY -> "vence hoy"
            PaymentStatus.DUE_SOON  -> "vence en $daysUntil ${if (daysUntil == 1) "día" else "días"}"
            PaymentStatus.UPCOMING  -> "próximamente"
        }
        // Una tarjeta no tiene cuota: su monto es el SALDO. **Este es el canal que suena**, así
        // que anunciar «Pago tarjeta AMEX 9208 — $27.501.150 (vence hoy)» es la versión más
        // ruidosa del número que esta rama vino a corregir. Mismo copy que el email y que el
        // Inicio: el saldo, dicho con su nombre. Ver `RecurringRule.montoEsSaldo`.
        //
        // **Y en la moneda de la regla.** El saldo de una tarjeta viaja en la moneda de la cuenta
        // (ver `RecurringRule.currency`): sin mirarla, una deuda de US$1.200 sonaba como «saldo
        // $1.200», una cifra 4.000 veces más chica que la real.
        val monto = montoConMoneda(rule)
        "${rule.name} — $monto ($estado)"
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
    val lines = parsed.take(MAX_LINES).map {
        val signo = if (it.currency == "USD") "US$" else "$"
        "$signo${formatMiles(it.amount.roundToLong())} en ${it.merchant}"
    }
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

/**
 * El monto de una regla con su moneda, y con el «saldo» delante cuando es la deuda de una tarjeta.
 *
 * Es el gemelo de `montoConMoneda` del correo, con el formato de miles de acá (puntos, como en el
 * resto de la app) en vez del `Locale.US` que usa el HTML. Existe separado porque `:server` no
 * puede importar `:shared` —la misma nota que ya tenía `textoDelMonto`—, y lo que evita que se
 * separen son las pruebas de los dos lados.
 */
private fun montoConMoneda(rule: RecurringRule): String {
    val monto = formatMiles(rule.amount)
    val texto = when (rule.currency) {
        "COP" -> "$" + monto
        "USD" -> "US$" + monto
        else  -> rule.currency + " " + monto
    }
    return if (rule.montoEsSaldo) "saldo $texto" else texto
}
