package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.estimatedTotalCop
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            val uid = call.userId()
            val rate = FxRateService.usdToCop()
            val rows = dbQuery {
                Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
            }
            val byAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            val enriched = rows.map { enrichWith(it, byAccount[it.id] ?: emptyList(), rate) }
            call.respond(enriched)
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val base = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(enrichWith(base, loadNonVoidedEvents(uid, base.id), FxRateService.usdToCop()))
        }

        post {
            val body = call.receive<Account>()
            val uid = call.userId()
            val account = body.copy(
                id = body.id.ifBlank { "acc_${System.currentTimeMillis()}" }
            )
            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[name]     = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
            }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}

private fun enrichWith(base: Account, events: List<FinancialEvent>, rate: Double): Account {
    val balances = computeBalances(base.type, events)
    return base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
    )
}

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
