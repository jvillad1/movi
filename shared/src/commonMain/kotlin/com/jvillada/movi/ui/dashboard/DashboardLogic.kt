package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.components.formatCOP
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Todo lo que el Inicio carga del server, junto, para que el renderer SDUI reciba un solo
 * valor y no doce parámetros. Cada campo arranca vacío y se va llenando a medida que llega
 * cada respuesta; una sección que no tiene todavía sus datos simplemente no se pinta (ver
 * [visibleSections]) o muestra la cifra en blanco — nunca un número inventado.
 */
data class DashboardData(
    val summary: FinanceSummary? = null,
    val accounts: List<Account> = emptyList(),
    val credits: List<CreditSummary> = emptyList(),
    val upcoming: List<UpcomingPayment> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    /** Gasto del mes en curso por categoría (ver [spentByCategoryForMonth]). */
    val spentByCategory: Map<String, Long> = emptyMap(),
    val cardCandidates: Int = 0,
    val pendingSms: Int = 0,
    val goals: List<Goal> = emptyList(),
    val subscriptions: SubscriptionsResult? = null,
) {
    val hasAccount: Boolean get() = accounts.isNotEmpty()
    val hasCredit: Boolean get() = credits.isNotEmpty()
    /** F54: el evento de apertura no cuenta — ver KDoc de `FinanceSummary.eventCount`. */
    val hasMovement: Boolean get() = (summary?.eventCount ?: 0) > 0
    /**
     * F6: "Anota tus pagos fijos" se tilda con una regla recurrente REAL. `getUpcomingPayments`
     * también trae las cuotas sintéticas de los créditos (id con [CREDIT_RULE_PREFIX]); esas
     * tildan el paso de Créditos, no este.
     */
    val hasRecurringRule: Boolean get() = upcoming.any { !it.rule.id.startsWith(CREDIT_RULE_PREFIX) }
}

// ── Próximos pagos ─────────────────────────────────────────────────────────────────

/**
 * Pagos (egresos) que vencen dentro de [days] días, incluidos los ya vencidos que el server
 * todavía considera de este periodo (daysUntil negativo): son los más urgentes y van primero.
 * Los ingresos recurrentes (sueldo) no son "pagos" y quedan fuera.
 */
fun upcomingPaymentsWithin(payments: List<UpcomingPayment>, days: Int = 7, max: Int = 3): List<UpcomingPayment> =
    payments
        .filter { it.rule.type == TransactionType.EXPENSE && it.daysUntil <= days }
        .sortedBy { it.daysUntil }
        .take(max)

fun dueLabel(daysUntil: Int): String = when {
    daysUntil < -1 -> "Vencido hace ${-daysUntil} días"
    daysUntil == -1 -> "Vencido ayer"
    daysUntil == 0 -> "Vence hoy"
    daysUntil == 1 -> "Vence mañana"
    else -> "Vence en $daysUntil días"
}

// ── Gasto del mes ──────────────────────────────────────────────────────────────────

/**
 * Gasto por categoría del mes [monthPrefix] ("2026-08"). Misma regla que el resumen del
 * server: solo egresos en COP que cuentan como flujo de caja (un ajuste de deuda o un pago
 * de tarjeta no es gasto del mes).
 */
fun spentByCategoryForMonth(days: List<EventDay>, monthPrefix: String): Map<String, Long> =
    days.filter { it.date.startsWith(monthPrefix) }
        .flatMap { it.items }
        .filter { it.type == TransactionType.EXPENSE && it.countsAsCashFlow && it.currency == "COP" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

/** "2026-08" del día de hoy en UTC — la misma zona con la que el server fecha `EventDay.date`. */
fun currentMonthPrefixUtc(): String {
    val today = Clock.System.todayIn(TimeZone.UTC)
    return "${today.year}-${today.monthNumber.toString().padStart(2, '0')}"
}

// ── Alertas ────────────────────────────────────────────────────────────────────────

data class DashboardAlert(val text: String, val target: Screen)

/** Misma regla que Presupuestos: OVER = gastado ≥ límite; un límite 0 no puede "superarse". */
fun overBudgetCategories(budgets: List<Budget>, spentByCategory: Map<String, Long>): List<String> =
    budgets.filter { it.monthlyLimit > 0 && (spentByCategory[it.category] ?: 0L) >= it.monthlyLimit }
        .map { it.category }

/**
 * Cada alerta es una fila tocable que lleva a donde se resuelve. Sin nada pendiente devuelve
 * vacío y la sección entera no se pinta — nada de "Sin alertas por ahora".
 */
fun dashboardAlerts(overBudget: List<String>, cardCandidates: Int, pendingSms: Int): List<DashboardAlert> = buildList {
    when (overBudget.size) {
        0 -> Unit
        1 -> add(DashboardAlert("Presupuesto de ${overBudget[0]} superado", Screen.Budgets))
        else -> add(DashboardAlert("${overBudget.size} presupuestos superados", Screen.Budgets))
    }
    if (cardCandidates > 0) {
        add(DashboardAlert(plural(cardCandidates, "pago de tarjeta", "pagos de tarjeta") + " por confirmar", Screen.Transactions))
    }
    if (pendingSms > 0) {
        add(DashboardAlert(plural(pendingSms, "mensaje del banco", "mensajes del banco") + " por confirmar", Screen.SMSInbox))
    }
}

// ── Accesos con cifra ──────────────────────────────────────────────────────────────

/** Cifra (y línea secundaria) de un acceso; `value` null = no hay nada que mostrar todavía. */
data class LinkFigure(val value: String? = null, val sub: String? = null, val isAlert: Boolean = false)

/**
 * La cifra que acompaña a cada acceso según su destino de navegación — lo que antes mostraba
 * "Análisis" (F40). Un destino sin cifra conocida (Movi AI, Perfil…) devuelve todo en null
 * y la fila se pinta solo con el título.
 */
fun quickLinkFigure(target: String, data: DashboardData): LinkFigure = when (target) {
    "accounts" -> {
        val (activos, _, _) = assetsDebtsNet(data.accounts)
        if (data.accounts.isEmpty()) LinkFigure(sub = "Sin cuentas aún")
        else LinkFigure(formatCOP(activos), plural(data.accounts.size, "cuenta", "cuentas"))
    }
    "credits" -> {
        if (data.credits.isEmpty()) LinkFigure(sub = "Sin créditos")
        else LinkFigure(formatCOP(data.credits.sumOf { it.account.balance }), plural(data.credits.size, "crédito", "créditos"))
    }
    "budgets" -> {
        if (data.budgets.isEmpty()) LinkFigure(sub = "Sin presupuestos")
        else {
            val limit = data.budgets.sumOf { it.monthlyLimit }
            val spent = data.budgets.sumOf { data.spentByCategory[it.category] ?: 0L }
            LinkFigure(formatCOP(spent), "de ${formatCOP(limit)} este mes", isAlert = limit > 0 && spent >= limit)
        }
    }
    "goals" -> {
        if (data.goals.isEmpty()) LinkFigure(sub = "Sin metas")
        else LinkFigure(formatCOP(data.goals.sumOf { it.saved }), plural(data.goals.size, "meta", "metas"))
    }
    "investments" -> {
        // F50: misma fuente que la pantalla Inversiones — cuentas tipo INVESTMENT, no el
        // modelo de "posiciones" (holdings) que el server siempre devolvía vacío.
        val investmentAccounts = data.accounts.filter { it.type == AccountType.INVESTMENT }
        if (investmentAccounts.isEmpty()) LinkFigure(sub = "Sin inversiones")
        else LinkFigure(formatCOP(investmentAccounts.sumOf { it.balance }), plural(investmentAccounts.size, "cuenta", "cuentas"))
    }
    "subscriptions" -> {
        val subs = data.subscriptions
        // Solo las activas (AUTO/CONFIRMED) — es lo que suma monthlyTotalCop y lo que la pantalla
        // de Suscripciones llama «activas». Las candidatas y las descartadas no son suscripciones
        // todavía (o ya no): contarlas acá daba «$0 · 4 suscripciones al mes» con cero activas.
        val active = subs?.subscriptions?.count { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED } ?: 0
        val candidates = subs?.subscriptions?.count { it.status == SubStatus.CANDIDATE } ?: 0
        when {
            subs == null || (active == 0 && candidates == 0) -> LinkFigure(sub = "Sin suscripciones")
            active == 0 -> LinkFigure(sub = plural(candidates, "por confirmar", "por confirmar"))
            else -> LinkFigure(formatCOP(subs.monthlyTotalCop), plural(active, "suscripción", "suscripciones") + " al mes")
        }
    }
    else -> LinkFigure()
}

private fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

// ── Secciones visibles ─────────────────────────────────────────────────────────────

/**
 * Secciones de la definición que tienen algo que mostrar con estos datos. Próximos pagos y
 * Alertas desaparecen del todo cuando están vacías (así pidió el dueño: nada de cajas vacías
 * ocupando lugar); un bloque de accesos sin tarjetas tampoco se pinta.
 */
fun visibleSections(def: ScreenDefinition, data: DashboardData): List<ScreenSection> =
    renderableSections(def).filter { section ->
        when (section.type) {
            "UPCOMING_PAYMENTS" -> upcomingPaymentsWithin(data.upcoming).isNotEmpty()
            "ALERTS" -> dashboardAlerts(
                overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
            ).isNotEmpty()
            "QUICK_LINKS_WITH_TOTALS", "LINK_LIST", "CARD_ROW", "CARD_LIST" -> section.cards.isNotEmpty()
            else -> true
        }
    }
