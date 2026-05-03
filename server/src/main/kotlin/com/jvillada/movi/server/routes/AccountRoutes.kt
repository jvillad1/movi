package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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
            val accounts = dbQuery {
                Accounts.selectAll()
                    .where { Accounts.userId eq uid }
                    .map { it.toAccount() }
            }
            call.respond(accounts)
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(account)
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

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
