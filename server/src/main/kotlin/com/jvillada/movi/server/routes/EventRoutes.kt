package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Route.eventRoutes() {
    route("/api/events") {

        post {
            val body = call.receive<FinancialEvent>()
            val uid = call.userId()
            val now = System.currentTimeMillis()
            val event = body.copy(
                id        = body.id.ifBlank { "ev_${java.util.UUID.randomUUID()}" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
            )

            val accountExists = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .count() > 0
            }
            if (!accountExists) return@post call.respond(HttpStatusCode.NotFound, "Account not found")

            dbQuery {
                Events.insert {
                    it[id]                   = event.id
                    it[userId]               = uid
                    it[accountId]            = event.accountId
                    it[type]                 = event.type.name
                    it[amount]               = event.amount
                    it[Events.currency]      = event.currency
                    it[category]             = event.category
                    it[description]          = event.description
                    it[merchant]             = event.merchant
                    it[timestamp]            = event.timestamp
                    it[eventSource]          = event.source.name
                    it[rawPayload]           = event.rawPayload
                    it[reconciliationStatus] = event.reconciliationStatus.name
                    it[syncedAt]             = event.syncedAt
                }
            }
            call.respond(HttpStatusCode.Created, event)
        }

        get {
            val uid = call.userId()
            val accountId = call.request.queryParameters["accountId"]
            val result = loadNonVoidedEvents(uid, accountId).sortedByDescending { it.timestamp }
            call.respond(result)
        }

        get("/by-day") {
            val uid = call.userId()
            val result = loadNonVoidedEvents(uid)
                .groupBy { epochToUtcDate(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        date  = date,
                        total = items.filter { it.currency == "COP" }.sumOf { e ->
                            if (e.type == TransactionType.INCOME) e.amount else -e.amount
                        },
                        items = items,
                    )
                }
                .sortedByDescending { it.date }
            call.respond(result)
        }

        post("/{id}/void") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val reason = call.request.queryParameters["reason"]

            val event = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val void: VoidEvent? = dbQuery {
                val alreadyVoided = VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (alreadyVoided) {
                    null
                } else {
                    val now = System.currentTimeMillis()
                    val voidId = "void_${java.util.UUID.randomUUID()}"
                    VoidEvents.insert {
                        it[VoidEvents.id]              = voidId
                        it[VoidEvents.userId]          = uid
                        it[VoidEvents.originalEventId] = id
                        it[VoidEvents.reason]          = reason
                        it[VoidEvents.timestamp]       = now
                    }
                    VoidEvent(
                        id              = voidId,
                        originalEventId = id,
                        reason          = reason,
                        timestamp       = now,
                    )
                }
            }
            if (void == null) return@post call.respond(HttpStatusCode.Conflict, "Already voided")
            call.respond(HttpStatusCode.Created, void)
        }
    }
}

private fun epochToUtcDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
