package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.dismissedCardPaymentEventIds
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.monthWindowOf
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList

/**
 * `GET /api/dashboard/summary?scope=SELF|FAMILY` — los números del Inicio, ya reducidos.
 *
 * Existe porque la pantalla más usada se bajaba tres colecciones enteras para sacar tres
 * cifras: `GET /api/sms` para contar los pendientes, `GET /api/events/card-payment-candidates`
 * para un `.size` y `GET /api/events/by-day` (toda la historia) para el gasto del mes por
 * categoría. Con meses de uso real eso crece lineal; acá cada cifra se acota en SQL (el mes,
 * el estado, el tipo de cuenta) y lo que no se puede expresar en SQL sin reimplementar una
 * regla de negocio (`isCashFlow`, `looksLikeCardPayment`) se aplica en memoria sobre el
 * subconjunto ya acotado, con LA MISMA función que usa el resto del server.
 *
 * `scope` se valida y se devuelve igual que en `finance-summary` — y, igual que allí, hoy no
 * filtra nada: no hay modelo de familia todavía.
 */
fun Route.dashboardRoutes() {
    get("/api/dashboard/summary") {
        val uid = call.userId()
        val raw = call.request.queryParameters["scope"] ?: "SELF"
        val scope = runCatching { Scope.valueOf(raw.uppercase()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope: $raw")

        // Misma convención de mes que `finance-summary` (FinanceRoutes.kt): el mes civil en la
        // zona de la app (AppClock, Bogotá), del primer milisegundo del mes al primero del
        // siguiente. Las dos rutas pasan por currentMonthWindow(): es el mismo "mes" para el usuario.
        val now = AppClock.now()
        val (monthStart, monthEnd) = monthWindowOf(now)
        val month = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"

        val summary = dbQuery {
            val accountTypeById = accountTypesFor(uid)
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()

            val (income, spentByCategory) = monthCashFlow(uid, monthStart, monthEnd, voidedIds, accountTypeById)

            DashboardSummary(
                scope = scope,
                month = month,
                monthIncome = income,
                monthSpent = spentByCategory.values.sum(),
                spentByCategory = spentByCategory,
                cardPaymentCandidates = cardPaymentCandidateCount(uid, voidedIds, accountTypeById),
                pendingSms = SmsMessages.select(SmsMessages.id.count())
                    .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq "pending") }
                    .single()[SmsMessages.id.count()].toInt(),
            )
        }
        call.respond(summary)
    }
}

/**
 * Ingresos del mes y egresos del mes por categoría, en COP y solo flujo de caja — la misma
 * regla que `finance-summary` (ingresos/egresos) y que `spentByCategoryForMonth` del cliente,
 * ahora sobre las filas del mes nada más. Solo se piden las columnas que la regla necesita.
 */
private fun Transaction.monthCashFlow(
    uid: String,
    monthStart: Long,
    monthEnd: Long,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Pair<Long, Map<String, Long>> {
    val rows = Events.select(Events.id, Events.accountId, Events.type, Events.amount, Events.category)
        .where {
            (Events.userId eq uid) and
                (Events.currency eq "COP") and
                (Events.timestamp greaterEq monthStart) and
                (Events.timestamp less monthEnd)
        }
        .filterNot { it[Events.id] in voidedIds }
        .filter { row ->
            val accountType = accountTypeById[row[Events.accountId]]
            accountType == null ||
                isCashFlow(accountType, TransactionType.valueOf(row[Events.type]), row[Events.category])
        }
    val income = rows.filter { it[Events.type] == TransactionType.INCOME.name }.sumOf { it[Events.amount] }
    val spentByCategory = rows.filter { it[Events.type] == TransactionType.EXPENSE.name }
        .groupBy { it[Events.category] }
        .mapValues { (_, r) -> r.sumOf { it[Events.amount] } }
    return income to spentByCategory
}

/**
 * Cuántos devolvería `GET /api/events/card-payment-candidates` — mismo filtro (egreso, cuenta
 * de activo, no anulado, no descartado, `looksLikeCardPayment`), pero el SQL ya acota a los
 * egresos de cuentas de activo y solo se leen descripción y categoría.
 */
private fun Transaction.cardPaymentCandidateCount(
    uid: String,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Int {
    val assetTypes = setOf(AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT)
    val assetAccountIds = accountTypeById.filterValues { it in assetTypes }.keys.toList()
    if (assetAccountIds.isEmpty()) return 0
    val excluded = (voidedIds + dismissedCardPaymentEventIds(uid)).toList()
    return Events.select(Events.description, Events.category)
        .where {
            val base = (Events.userId eq uid) and
                (Events.type eq TransactionType.EXPENSE.name) and
                (Events.accountId inList assetAccountIds)
            if (excluded.isEmpty()) base else base and (Events.id notInList excluded)
        }
        .count { looksLikeCardPayment(it[Events.description], it[Events.category]) }
}
