package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.balance.withCashFlowFlag
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
import io.ktor.server.routing.put
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
            // El eco lleva la bandera derivada, no la que mandó el cliente: countsAsCashFlow
            // sale del tipo de la cuenta y el cliente no tiene voz ahí. Sin esto, el POST
            // devolvía el default `true` para un evento de una cuenta de deuda y contradecía
            // a los GET, que sí la derivan.
            val accountType = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.let { runCatching { AccountType.valueOf(it[Accounts.type]) }.getOrNull() }
            }
            call.respond(
                HttpStatusCode.Created,
                accountType?.let { event.copy(countsAsCashFlow = isCashFlow(it, event.type, event.category)) } ?: event,
            )
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
                        // El total del día es flujo de caja, igual que el del mes: los
                        // movimientos de cuentas de deuda quedan fuera (ver countsAsCashFlow).
                        // El renglón del ajuste SÍ se sigue listando —es un movimiento real de
                        // la cuenta— pero un ajuste de $60.000.000 no puede encabezar el día
                        // como "+$60.000.000", que es el mismo número engañoso del Dashboard.
                        date  = date,
                        total = items.filter { it.currency == "COP" && it.countsAsCashFlow }.sumOf { e ->
                            if (e.type == TransactionType.INCOME) e.amount else -e.amount
                        },
                        items = items,
                    )
                }
                .sortedByDescending { it.date }
            call.respond(result)
        }

        // Candidatos a pago de tarjeta ya cargados con otra categoría (Task 2 de
        // SP-ajustar-saldo). Solo LEE y PROPONE — nada se recategoriza acá; el dueño confirma
        // en un paso posterior. Por eso alcanza con reusar loadNonVoidedEventsIn +
        // accountTypesFor: los mismos que ya deciden qué es flujo de caja.
        get("/card-payment-candidates") {
            val uid = call.userId()
            val assetTypes = setOf(
                AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT,
            )
            val candidates = dbQuery {
                val accountTypes = accountTypesFor(uid)
                loadNonVoidedEventsIn(uid).filter { event ->
                    event.type == TransactionType.EXPENSE &&
                        accountTypes[event.accountId] in assetTypes &&
                        looksLikeCardPayment(event.description, event.category)
                }
            }
            call.respond(candidates)
        }

        // Recategorizar un movimiento (Task 3 de SP-ajustar-saldo). Es la pieza que le falta al
        // GET /card-payment-candidates de arriba: propone, esta confirma. Aislado por usuario
        // (404, no 403, si el evento es de otro) y countsAsCashFlow siempre se recalcula acá —
        // nunca se guarda ni se toma del cliente.
        put("/{id}/category") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val category = call.receive<UpdateEventCategoryRequest>().category.trim()
            if (category.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, "La categoría no puede estar vacía")
            }
            if (category.length > 60) {
                return@put call.respond(HttpStatusCode.BadRequest, "La categoría no puede superar 60 caracteres")
            }

            val updated: FinancialEvent? = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                if (event != null) {
                    Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                        it[Events.category] = category
                    }
                }
                event?.copy(category = category)?.withCashFlowFlag(accountTypesFor(uid))
            }
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
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
