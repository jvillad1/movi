package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.estimatedTotalCop
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

fun Route.creditRoutes() {
    route("/api/credits") {
        get {
            val uid = call.userId()
            val rate = FxRateService.usdToCop()
            val loans = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.LOAN.name) }
                    .map { it.toAccount() }
            }
            val termsByAccount = dbQuery {
                Credits.selectAll().where { Credits.userId eq uid }
                    .associate { it[Credits.accountId] to it.toCreditTerms() }
            }
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            call.respond(loans.map { acc ->
                summaryFor(acc, termsByAccount[acc.id], eventsByAccount[acc.id] ?: emptyList(), rate)
            })
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
            dbQuery {
                val exists = Credits.selectAll()
                    .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
                    .count() > 0
                if (exists) {
                    // lastRemindedPeriod se conserva a propósito: un cambio de día aplica desde el mes siguiente (v1)
                    Credits.update({ (Credits.accountId eq accountId) and (Credits.userId eq uid) }) {
                        it[bank]        = body.bank
                        it[principal]   = body.principal
                        it[rateEa]      = body.rateEa
                        it[termMonths]  = body.termMonths
                        it[installment] = body.installment
                        it[dayOfMonth]  = body.dayOfMonth
                        it[startDate]   = body.startDate
                        it[notes]       = body.notes
                    }
                } else {
                    Credits.insert {
                        it[Credits.accountId] = accountId
                        it[userId]      = uid
                        it[bank]        = body.bank
                        it[principal]   = body.principal
                        it[rateEa]      = body.rateEa
                        it[termMonths]  = body.termMonths
                        it[installment] = body.installment
                        it[dayOfMonth]  = body.dayOfMonth
                        it[startDate]   = body.startDate
                        it[notes]       = body.notes
                    }
                }
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

private fun summaryFor(base: Account, terms: CreditTerms?, events: List<FinancialEvent>, rate: Double): CreditSummary {
    val balances = computeBalances(base.type, events)
    val account = base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
    )
    return CreditSummary(
        account = account,
        terms   = terms,
        paidPct = terms?.let { paidPctFor(it.principal, account.balance) },
    )
}

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
