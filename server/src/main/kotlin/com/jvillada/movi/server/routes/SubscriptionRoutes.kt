package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.subscriptions.DetectedSub
import com.jvillada.movi.server.subscriptions.detectSubscriptions
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToLong

fun Route.subscriptionRoutes() {
    route("/api/subscriptions") {
        get {
            val uid = call.userId()
            call.respond(resultFor(uid))
        }

        post("/detect") {
            val uid = call.userId()
            val events = loadNonVoidedEvents(uid)
            val detected = detectSubscriptions(events, LocalDate.now(ZoneOffset.UTC))
            dbQuery {
                val existing = Subscriptions.selectAll()
                    .where { Subscriptions.userId eq uid }
                    .associateBy { it[Subscriptions.merchantKey] to it[Subscriptions.currency] }
                for (d in detected) {
                    val row = existing[d.merchantKey to d.currency]
                    when {
                        row == null -> Subscriptions.insert {
                            it[id]          = "sub_${UUID.randomUUID()}"
                            it[userId]      = uid
                            it[merchantKey] = d.merchantKey
                            it[displayName] = d.displayName
                            it[amount]      = d.amount
                            it[currency]    = d.currency
                            it[dayOfMonth]  = d.dayOfMonth
                            it[status]      = statusForNew(d).name
                            it[confidence]  = d.confidence.name
                            it[firstSeen]   = d.firstSeen
                            it[lastSeen]    = d.lastSeen
                            it[occurrences] = d.occurrences
                            it[accountId]   = d.accountId
                        }
                        row[Subscriptions.status] == SubStatus.DISMISSED.name -> Unit  // el usuario dijo que no
                        row[Subscriptions.status] == SubStatus.CONFIRMED.name ->
                            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                                it[amount]      = d.amount
                                it[lastSeen]    = d.lastSeen
                                it[occurrences] = d.occurrences
                                it[confidence]  = d.confidence.name
                            }
                        else ->  // AUTO o CANDIDATE: refrescar todo y re-evaluar estado
                            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                                it[displayName] = d.displayName
                                it[amount]      = d.amount
                                it[dayOfMonth]  = d.dayOfMonth
                                it[status]      = statusForNew(d).name
                                it[confidence]  = d.confidence.name
                                it[firstSeen]   = d.firstSeen
                                it[lastSeen]    = d.lastSeen
                                it[occurrences] = d.occurrences
                                it[accountId]   = d.accountId
                            }
                    }
                }
            }
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
                    .first().toSubscription()
            }
            call.respond(row)
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

private fun statusForNew(d: DetectedSub): SubStatus =
    if (d.confidence == SubConfidence.HIGH) SubStatus.AUTO else SubStatus.CANDIDATE

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
