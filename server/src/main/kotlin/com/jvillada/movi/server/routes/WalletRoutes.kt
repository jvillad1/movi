package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val dayLabelByTimestamp: (Long) -> String = { ts ->
    when {
        ts >= 1_745_812_800_000L -> "Hoy · 28 abr"
        ts >= 1_745_726_400_000L -> "Ayer · 27 abr"
        else -> "26 abr"
    }
}

private fun signedAmount(tx: Transaction): Double =
    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount

internal suspend fun applyTransaction(tx: Transaction): Boolean {
    val notFound = Stores.wallets.mutate { wallets ->
        val idx = wallets.indexOfFirst { it.id == tx.walletId }
        if (idx == -1) return@mutate true
        val w = wallets[idx]
        wallets[idx] = w.copy(balance = w.balance + signedAmount(tx))
        false
    }
    if (notFound) return false
    Stores.transactions.mutate { it.add(tx) }
    return true
}

fun Route.walletRoutes() {
    route("/api/wallets") {
        get { call.respond(Stores.wallets.snapshot()) }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wallet = Stores.wallets.snapshot().find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(wallet)
        }

        get("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(Stores.transactions.snapshot().filter { it.walletId == id })
        }

        post("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val raw = call.receive<Transaction>()
            val now = System.currentTimeMillis()
            val tx = raw.copy(
                walletId = id,
                id = raw.id.ifBlank { "m-$now" },
                timestamp = if (raw.timestamp == 0L) now else raw.timestamp,
            )
            if (!applyTransaction(tx)) return@post call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.Created, tx)
        }
    }

    route("/api/transactions") {
        get("/by-day") {
            val grouped = Stores.transactions.snapshot()
                .sortedByDescending { it.timestamp }
                .groupBy { dayLabelByTimestamp(it.timestamp) }
                .map { (date, items) ->
                    TransactionDay(
                        date = date,
                        total = items.sumOf { signedAmount(it) },
                        items = items,
                    )
                }
            call.respond(grouped)
        }
    }
}
