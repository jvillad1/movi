package com.jvillada.monedero.server.routes

import com.jvillada.monedero.shared.model.Transaction
import com.jvillada.monedero.shared.model.TransactionType
import com.jvillada.monedero.shared.model.Wallet
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

// In-memory store — replace with a real DB later
private val wallets = mutableListOf(
    Wallet("1", "Efectivo", 1500.0, "MXN"),
    Wallet("2", "Débito BBVA", 8200.0, "MXN"),
)
private val transactions = mutableListOf<Transaction>()

fun Route.walletRoutes() {
    route("/api/wallets") {
        get {
            call.respond(wallets)
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wallet = wallets.find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(wallet)
        }

        get("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(transactions.filter { it.walletId == id })
        }

        post("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val tx = call.receive<Transaction>()
            val wallet = wallets.find { it.id == id }
                ?: return@post call.respond(HttpStatusCode.NotFound)

            val delta = if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
            val updated = wallet.copy(balance = wallet.balance + delta)
            wallets[wallets.indexOf(wallet)] = updated

            transactions.add(tx)
            call.respond(HttpStatusCode.Created, tx)
        }
    }
}
