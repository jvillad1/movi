package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.push.WebPushSender
import com.jvillada.movi.server.push.buildPushPayload
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * In-process daily email scheduler for payment reminders.
 *
 * Wired in [Application.module] via [startReminderScheduler].
 * NO-OPS gracefully (logs a warning) when RESEND_API_KEY is absent.
 *
 * Lifecycle: the launched coroutine is a child of Application's coroutineContext
 * (which has a SupervisorJob cancelled on app stop), so it cleans up automatically.
 */
fun Application.startReminderScheduler() {
    val apiKey = readEnv("RESEND_API_KEY")
    val emailEnabled = !apiKey.isNullOrBlank()
    val pushEnabled = WebPushSender.isConfigured()
    if (!emailEnabled) log.warn("ReminderScheduler: RESEND_API_KEY not set — email reminders disabled")
    if (!pushEnabled) log.warn("ReminderScheduler: VAPID keys not set — push reminders disabled")
    if (!emailEnabled && !pushEnabled) {
        log.warn("ReminderScheduler: ningún canal configurado — scheduler apagado")
        return
    }

    val from      = readEnv("REMINDER_FROM") ?: "movi <reminders@movi.app>"
    val leadDays  = readEnv("REMINDER_LEAD_DAYS")?.toIntOrNull()  ?: 3
    val sweepHours = readEnv("REMINDER_SWEEP_HOURS")?.toLongOrNull() ?: 12L

    log.info("ReminderScheduler: starting (sweepHours=$sweepHours, leadDays=$leadDays, from=$from)")

    launch {
        // Run once at startup, then on each interval
        while (true) {
            runCatching { sweep(apiKey, from, leadDays) }
                .onFailure { log.error("ReminderScheduler: unhandled sweep error: ${it.message}", it) }
            delay(sweepHours * 3_600_000L)
        }
    }
}

// ── Sweep ────────────────────────────────────────────────────────────────────

private suspend fun sweep(apiKey: String?, from: String, leadDays: Int) {
    val today = LocalDate.now(ZoneOffset.UTC)

    // Load all users (id + email)
    val users = dbQuery {
        Users.selectAll().map { row ->
            Pair(row[Users.id], row[Users.email])
        }
    }

    for ((userId, userEmail) in users) {
        runCatching { processUser(userId, userEmail, today, leadDays, apiKey, from) }
            .onFailure {
                // One user's failure must not stop the sweep for others
                org.slf4j.LoggerFactory.getLogger("ReminderScheduler")
                    .warn("ReminderScheduler: error processing user $userId: ${it.message}", it)
            }
    }
}

private suspend fun processUser(
    userId: String,
    userEmail: String,
    today: LocalDate,
    leadDays: Int,
    apiKey: String?,
    from: String,
) {
    val logger = org.slf4j.LoggerFactory.getLogger("ReminderScheduler")

    // Load this user's rules WITH lastRemindedPeriod (server-only column)
    val rulePairs: List<Pair<RecurringRule, String?>> = dbQuery {
        RecurringRules.selectAll()
            .where { RecurringRules.userId eq userId }
            .map { row -> row.toRulePair() }
    }
    val allPairs = rulePairs + loadCreditRulePairs(userId) + loadCardRulePairs(userId)

    val selected = selectDueForReminder(allPairs, today, leadDays)

    if (selected.isEmpty()) return

    val emailSent = if (!apiKey.isNullOrBlank()) {
        val html = buildHtmlEmail(selected, today, leadDays)
        ResendClient.sendEmail(
            to = userEmail, subject = "Pagos próximos en movi",
            html = html, apiKey = apiKey, from = from,
        )
    } else false

    val pushSent = if (WebPushSender.isConfigured()) {
        runCatching { WebPushSender.sendToUser(userId, buildPushPayload(selected, today, leadDays)) }
            .getOrElse { logger.warn("push sweep falló para $userId: ${it.message}"); false }
    } else false

    if (emailSent || pushSent) {
        // Seal each selected rule so we don't re-notify for the SAME due date.
        // El sello es reminderKeyFor: el periodo del vencimiento (no el de hoy), calculado con
        // la MISMA función que usa selectDueForReminder para filtrar. Si algún día vuelven a
        // divergir (p.ej. alguien "simplifica" esto de vuelta a periodOf(today)) el bug que este
        // cambio arregló reaparece: se notificaría el mismo vencimiento dos veces al mes.
        // Update rules one-by-one: Exposed's update DSL doesn't support an IN clause
        // in the where block, so individual updates are the cleanest approach.
        for (rule in selected) {
            val duePeriod = reminderKeyFor(rule, today)
            dbQuery {
                if (rule.id.startsWith(CREDIT_RULE_PREFIX)) {
                    Credits.update({
                        (Credits.accountId eq rule.id.removePrefix(CREDIT_RULE_PREFIX)) and (Credits.userId eq userId)
                    }) { it[Credits.lastRemindedPeriod] = duePeriod }
                } else if (rule.id.startsWith(CARD_RULE_PREFIX)) {
                    // F20: la regla sintética de una tarjeta se sella en card_terms — mismo
                    // criterio de dedupe (reminderKeyFor) que créditos y reglas reales.
                    Cards.update({
                        (Cards.accountId eq rule.id.removePrefix(CARD_RULE_PREFIX)) and (Cards.userId eq userId)
                    }) { it[Cards.lastRemindedPeriod] = duePeriod }
                } else {
                    RecurringRules.update({
                        (RecurringRules.id eq rule.id) and (RecurringRules.userId eq userId)
                    }) { it[RecurringRules.lastRemindedPeriod] = duePeriod }
                }
            }
        }
        val periods = selected.map { reminderKeyFor(it, today) }.distinct().sorted()
        logger.info(
            "ReminderScheduler: reminded user $userId about ${selected.size} payment(s) " +
                "for period(s) ${periods.joinToString(", ")}",
        )
    }
}

// ── HTML builder ─────────────────────────────────────────────────────────────

private fun buildHtmlEmail(
    rules: List<RecurringRule>,
    today: LocalDate,
    leadDays: Int,
): String {
    val items = rules.joinToString(separator = "") { rule ->
        val due     = dueDateFor(rule, today)
        val status  = statusFor(due, today, leadDays)
        // Con la ventana de gracia, una regla de día<=leadDays puede vencer legítimamente el
        // mes PASADO (p.ej. el 29 de julio, un día 1 vence "el 1 de agosto"). Mostrar solo el
        // número de día bajo un encabezado "este mes" leería como si fuera el mes en curso —
        // el mismo tipo de afirmación falsa que esta rama existe para evitar. Se incluye el mes.
        val dayStr  = "${due.dayOfMonth} de ${SPANISH_MONTHS[due.monthValue - 1]}"
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(due, today).toInt()
        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, due).toInt()

        val statusText = when (status) {
            com.jvillada.movi.shared.model.PaymentStatus.OVERDUE    -> "vencido hace $daysAgo ${if (daysAgo == 1) "día" else "días"}"
            com.jvillada.movi.shared.model.PaymentStatus.DUE_TODAY  -> "vence hoy"
            com.jvillada.movi.shared.model.PaymentStatus.DUE_SOON   -> "vence en $daysUntil ${if (daysUntil == 1) "día" else "días"}"
            com.jvillada.movi.shared.model.PaymentStatus.UPCOMING   -> "próximamente"
        }

        val colorStyle = when (status) {
            com.jvillada.movi.shared.model.PaymentStatus.OVERDUE    -> "color:#dc2626"
            com.jvillada.movi.shared.model.PaymentStatus.DUE_TODAY  -> "color:#d97706"
            com.jvillada.movi.shared.model.PaymentStatus.DUE_SOON   -> "color:#d97706"
            com.jvillada.movi.shared.model.PaymentStatus.UPCOMING   -> "color:#6b7280"
        }

        val amountFormatted = String.format(java.util.Locale.US, "%,d", rule.amount)

        """<tr>
          <td style="padding:8px 12px;border-bottom:1px solid #f0f0f0">
            <strong>${rule.name}</strong><br>
            <span style="color:#4b5563">$${amountFormatted} COP</span>
          </td>
          <td style="padding:8px 12px;border-bottom:1px solid #f0f0f0;text-align:right">
            <span>vence el $dayStr</span><br>
            <span style="$colorStyle;font-size:0.875em">$statusText</span>
          </td>
        </tr>"""
    }

    return """<!DOCTYPE html>
<html lang="es">
<head><meta charset="UTF-8"><title>Pagos próximos en movi</title></head>
<body style="font-family:sans-serif;max-width:560px;margin:0 auto;padding:20px;color:#1f2937">
  <h2 style="margin-bottom:4px">💳 Pagos próximos en movi</h2>
  <p style="color:#6b7280;margin-top:0">Tienes pagos pendientes.</p>
  <table style="width:100%;border-collapse:collapse;margin-top:16px">
    <thead>
      <tr style="background:#f9fafb">
        <th style="padding:8px 12px;text-align:left;font-size:0.8em;text-transform:uppercase;color:#6b7280">Pago</th>
        <th style="padding:8px 12px;text-align:right;font-size:0.8em;text-transform:uppercase;color:#6b7280">Vencimiento</th>
      </tr>
    </thead>
    <tbody>
      $items
    </tbody>
  </table>
  <p style="margin-top:24px;font-size:0.8em;color:#9ca3af">Este recordatorio fue enviado por movi. Para dejar de recibirlo, desactiva los recordatorios en la app.</p>
</body>
</html>"""
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Nombres de mes en español, indexados 0=enero..11=diciembre (evita depender de Locale("es")). */
private val SPANISH_MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

private fun ResultRow.toRulePair(): Pair<RecurringRule, String?> {
    val rule = RecurringRule(
        id         = this[RecurringRules.id],
        name       = this[RecurringRules.name],
        category   = this[RecurringRules.category],
        amount     = this[RecurringRules.amount],
        dayOfMonth = this[RecurringRules.dayOfMonth],
        type       = TransactionType.valueOf(this[RecurringRules.type]),
    )
    val lastReminded = this[RecurringRules.lastRemindedPeriod]
    return rule to lastReminded
}

/**
 * Reads a key from environment variables, then falls back to server/.env or .env files
 * (same resolution order as [DatabaseFactory] and [JwtConfig]).
 */
private fun readEnv(key: String): String? {
    System.getenv(key)?.let { return it }
    val files = listOf(
        File(System.getProperty("user.dir"), "server/.env"),
        File(System.getProperty("user.dir"), ".env"),
    )
    return files.firstNotNullOfOrNull { f ->
        if (!f.exists()) null
        else f.readLines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
    }
}
