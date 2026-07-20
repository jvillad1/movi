package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.openingEventFor
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

fun Route.creditRoutes() {
    route("/api/credits") {
        get {
            val uid = call.userId()
            val loans = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.LOAN.name) }
                    .map { it.toAccount() }
            }
            if (loans.isEmpty()) return@get call.respond(emptyList<CreditSummary>())
            val rate = FxRateService.usdToCop()
            val termsByAccount = dbQuery {
                Credits.selectAll().where { Credits.userId eq uid }
                    .associate { it[Credits.accountId] to it.toCreditTerms() }
            }
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            call.respond(loans.map { acc ->
                summaryFor(acc, termsByAccount[acc.id], eventsByAccount[acc.id] ?: emptyList(), rate)
            })
        }

        // Alta atómica: cuenta LOAN + evento de apertura + términos en UNA transacción.
        // Evita el flujo cliente en dos pasos (crear cuenta y luego PUT términos), que
        // dejaba cuentas huérfanas/duplicadas ante fallos parciales y no funcionaba en
        // móvil (LocalRepository crea cuentas solo localmente).
        post {
            val uid = call.userId()
            val body = call.receive<CreateCreditRequest>()
            val name = body.name.trim()
            if (name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Nombre de cuenta requerido")
            if (body.initialDebt <= 0L) return@post call.respond(HttpStatusCode.BadRequest, "Deuda inicial debe ser mayor a 0")

            val account = Account(
                id       = "acc_${System.currentTimeMillis()}",
                name     = name,
                type     = AccountType.LOAN,
                balance  = body.initialDebt,
                currency = "COP",
            )
            val terms = body.terms
                .copy(accountId = account.id)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
            val opening = openingEventFor(account, now = System.currentTimeMillis())

            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[Accounts.name] = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
                if (opening != null) insertEventRow(uid, opening)
                Credits.insert { fillTerms(it, uid, terms) }
            }
            call.respond(
                HttpStatusCode.Created,
                summaryFor(account, terms, listOfNotNull(opening), FxRateService.usdToCop()),
            )
        }

        put("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            if (account.type != AccountType.LOAN) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Solo cuentas LOAN llevan términos de crédito")
            }
            val body = call.receive<CreditTerms>()
                .copy(accountId = accountId)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
            // upsert atómico por PK (accountId): elimina la carrera check-then-insert.
            // lastRemindedPeriod no está en el body del upsert, así que se conserva
            // a propósito: un cambio de día aplica desde el mes siguiente (v1).
            dbQuery {
                Credits.upsert { fillTerms(it, uid, body) }
            }
            call.respond(summaryFor(account, body, loadNonVoidedEvents(uid, accountId), FxRateService.usdToCop()))
        }

        delete("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val deleted = dbQuery {
                Credits.deleteWhere { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun fillTerms(
    it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>,
    uid: String,
    terms: CreditTerms,
) {
    it[Credits.accountId]   = terms.accountId
    it[Credits.userId]      = uid
    it[Credits.bank]        = terms.bank
    it[Credits.principal]   = terms.principal
    it[Credits.rateEa]      = terms.rateEa
    it[Credits.termMonths]  = terms.termMonths
    it[Credits.installment] = terms.installment
    it[Credits.dayOfMonth]  = terms.dayOfMonth
    it[Credits.startDate]   = terms.startDate
    it[Credits.notes]       = terms.notes
}

private fun summaryFor(base: Account, terms: CreditTerms?, events: List<FinancialEvent>, rate: Double): CreditSummary {
    val account = enrichWith(base, events, rate)
    return CreditSummary(
        account = account,
        terms   = terms,
        paidPct = terms?.let { paidPctFor(it.principal, account.balance) },
    )
}
