package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun signedAmount(e: FinancialEvent): Long =
    if (e.type == TransactionType.EXPENSE) -e.amount else e.amount

private fun dayLabel(ts: Long): String {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return when {
        ts >= now - day      -> "Hoy"
        ts >= now - 2 * day  -> "Ayer"
        ts >= now - 7 * day  -> "Esta semana"
        else                 -> "Antes"
    }
}

fun Route.eventRoutes() {
    route("/api/events") {
        post {
            val body = call.receive<FinancialEvent>()
            val now = System.currentTimeMillis()
            val event = body.copy(
                id = body.id.ifBlank { "ev_$now" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
            )
            Stores.events.mutate { it.add(event) }

            Stores.accounts.mutate { accounts ->
                val idx = accounts.indexOfFirst { it.id == event.accountId }
                if (idx != -1) {
                    val acc = accounts[idx]
                    accounts[idx] = acc.copy(balance = acc.balance + signedAmount(event))
                }
            }
            call.respond(HttpStatusCode.Created, event)
        }

        get {
            val accountId = call.request.queryParameters["accountId"]
            val all = Stores.events.snapshot()
            val voidIds = Stores.voidEvents.snapshot().map { it.originalEventId }.toSet()
            val active = all.filter { it.id !in voidIds }
            val result = if (accountId != null) active.filter { it.accountId == accountId } else active
            call.respond(result.sortedByDescending { it.timestamp })
        }

        get("/by-day") {
            val voidIds = Stores.voidEvents.snapshot().map { it.originalEventId }.toSet()
            val grouped = Stores.events.snapshot()
                .filter { it.id !in voidIds }
                .sortedByDescending { it.timestamp }
                .groupBy { dayLabel(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        date = date,
                        total = items.sumOf { signedAmount(it) },
                        items = items,
                    )
                }
            call.respond(grouped)
        }

        post("/{id}/void") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val exists = Stores.events.snapshot().any { it.id == id }
            if (!exists) return@post call.respond(HttpStatusCode.NotFound)
            val alreadyVoided = Stores.voidEvents.snapshot().any { it.originalEventId == id }
            if (alreadyVoided) return@post call.respond(HttpStatusCode.Conflict, "Already voided")

            val reason = call.request.queryParameters["reason"]
            val void = VoidEvent(
                id = "void_${System.currentTimeMillis()}",
                originalEventId = id,
                reason = reason,
                timestamp = System.currentTimeMillis(),
            )
            Stores.voidEvents.mutate { it.add(void) }

            val event = Stores.events.snapshot().first { it.id == id }
            Stores.accounts.mutate { accounts ->
                val idx = accounts.indexOfFirst { it.id == event.accountId }
                if (idx != -1) {
                    val acc = accounts[idx]
                    accounts[idx] = acc.copy(balance = acc.balance - signedAmount(event))
                }
            }
            call.respond(HttpStatusCode.Created, void)
        }
    }
}
