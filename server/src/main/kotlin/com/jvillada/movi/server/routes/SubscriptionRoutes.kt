package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.subscriptions.runSubscriptionDetection
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.math.roundToLong

fun Route.subscriptionRoutes() {
    route("/api/subscriptions") {
        get {
            val uid = call.userId()
            call.respond(resultFor(uid))
        }

        post("/detect") {
            val uid = call.userId()
            runSubscriptionDetection(uid)
            call.respond(resultFor(uid))
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receive<Subscription>()
            val updated = dbQuery {
                Subscriptions.update({ (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }) {
                    it[status]      = body.status.name
                    it[displayName] = body.displayName
                    it[amount]      = body.amount
                    it[dayOfMonth]  = body.dayOfMonth.coerceIn(1, 31)
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound)
            val row = dbQuery {
                Subscriptions.selectAll()
                    .where { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
                    .firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound)  // borrado concurrente entre el update y el re-read
            call.respond(row.toSubscription())
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val deleted = dbQuery {
                Subscriptions.deleteWhere { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun resultFor(uid: String): SubscriptionsResult {
    val subs = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .map { it.toSubscription() }
    }
    val active = subs.filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
    val needsFx = active.any { it.currency == "USD" }
    val rate = if (needsFx) FxRateService.usdToCop() else 0.0
    val total = active.sumOf { s ->
        when (s.currency) {
            "COP" -> s.amount
            "USD" -> (s.amount * rate).roundToLong()
            else  -> 0L
        }
    }
    return SubscriptionsResult(subscriptions = subs, monthlyTotalCop = total)
}

private fun ResultRow.toSubscription() = Subscription(
    id          = this[Subscriptions.id],
    merchantKey = this[Subscriptions.merchantKey],
    displayName = this[Subscriptions.displayName],
    amount      = this[Subscriptions.amount],
    currency    = this[Subscriptions.currency],
    dayOfMonth  = this[Subscriptions.dayOfMonth],
    status      = SubStatus.valueOf(this[Subscriptions.status]),
    confidence  = SubConfidence.valueOf(this[Subscriptions.confidence]),
    firstSeen   = this[Subscriptions.firstSeen],
    lastSeen    = this[Subscriptions.lastSeen],
    occurrences = this[Subscriptions.occurrences],
    accountId   = this[Subscriptions.accountId],
)
