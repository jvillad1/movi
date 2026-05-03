package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
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
                id        = body.id.ifBlank { "ev_$now" },
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
                    it[category]             = event.category
                    it[description]          = event.description
                    it[merchant]             = event.merchant
                    it[timestamp]            = event.timestamp
                    it[eventSource]          = event.source.name
                    it[rawPayload]           = event.rawPayload
                    it[reconciliationStatus] = event.reconciliationStatus.name
                    it[syncedAt]             = event.syncedAt
                }
                val currentBalance = Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .first()[Accounts.balance]
                val delta = if (event.type == TransactionType.INCOME) event.amount else -event.amount
                Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                    it[balance] = currentBalance + delta
                }
            }
            call.respond(HttpStatusCode.Created, event)
        }

        get {
            val uid = call.userId()
            val accountId = call.request.queryParameters["accountId"]
            val result = dbQuery {
                val voidedIds = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                val notVoided = if (voidedIds.isEmpty()) Op.TRUE
                                else Events.id notInList voidedIds.toList()
                val accountFilter = if (accountId != null) Events.accountId eq accountId else Op.TRUE
                Events.selectAll()
                    .where { (Events.userId eq uid) and accountFilter and notVoided }
                    .orderBy(Events.timestamp, SortOrder.DESC)
                    .map { it.toEvent() }
            }
            call.respond(result)
        }

        get("/by-day") {
            val uid = call.userId()
            val result = dbQuery {
                val voidedIds = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                val notVoided = if (voidedIds.isEmpty()) Op.TRUE
                                else Events.id notInList voidedIds.toList()
                Events.selectAll()
                    .where { (Events.userId eq uid) and notVoided }
                    .orderBy(Events.timestamp, SortOrder.DESC)
                    .map { it.toEvent() }
                    .groupBy { epochToUtcDate(it.timestamp) }
                    .map { (date, items) ->
                        EventDay(
                            date  = date,
                            total = items.sumOf { e ->
                                if (e.type == TransactionType.INCOME) e.amount else -e.amount
                            },
                            items = items,
                        )
                    }
                    .sortedByDescending { it.date }
            }
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
                    .firstOrNull()?.toEvent()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val alreadyVoided = dbQuery {
                VoidEvents.selectAll()
                    .where { VoidEvents.originalEventId eq id }
                    .count() > 0
            }
            if (alreadyVoided) return@post call.respond(HttpStatusCode.Conflict, "Already voided")

            val void = dbQuery {
                val now = System.currentTimeMillis()
                val voidId = "void_$now"
                VoidEvents.insert {
                    it[VoidEvents.id]              = voidId
                    it[VoidEvents.userId]          = uid
                    it[VoidEvents.originalEventId] = id
                    it[VoidEvents.reason]          = reason
                    it[VoidEvents.timestamp]       = now
                }
                val currentBalance = Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .first()[Accounts.balance]
                val delta = if (event.type == TransactionType.INCOME) -event.amount else event.amount
                Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                    it[balance] = currentBalance + delta
                }
                VoidEvent(
                    id              = voidId,
                    originalEventId = id,
                    reason          = reason,
                    timestamp       = now,
                )
            }
            call.respond(HttpStatusCode.Created, void)
        }
    }
}

private fun ResultRow.toEvent() = FinancialEvent(
    id                   = this[Events.id],
    accountId            = this[Events.accountId],
    type                 = TransactionType.valueOf(this[Events.type]),
    amount               = this[Events.amount],
    category             = this[Events.category],
    description          = this[Events.description],
    merchant             = this[Events.merchant],
    timestamp            = this[Events.timestamp],
    source               = EventSource.valueOf(this[Events.eventSource]),
    rawPayload           = this[Events.rawPayload],
    reconciliationStatus = ReconciliationStatus.valueOf(this[Events.reconciliationStatus]),
    syncedAt             = this[Events.syncedAt],
)

private fun epochToUtcDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
