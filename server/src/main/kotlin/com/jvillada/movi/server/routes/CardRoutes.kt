package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.cardSummaryFor
import com.jvillada.movi.server.credits.toCardTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.openingEventFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

/**
 * F20 — tarjetas de crédito como deuda de primera clase: cupo, corte, día de pago.
 * Espejo de [creditRoutes] para cuentas CREDIT_CARD; la deuda se sigue derivando de eventos.
 */
fun Route.cardRoutes() {
    route("/api/cards") {
        get {
            val uid = call.userId()
            val cards = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.CREDIT_CARD.name) }
                    .map { it.toAccount() }
            }
            if (cards.isEmpty()) return@get call.respond(emptyList<com.jvillada.movi.shared.model.CardSummary>())
            val rate = FxRateService.usdToCop()
            val termsByAccount = dbQuery {
                Cards.selectAll().where { Cards.userId eq uid }
                    .associate { it[Cards.accountId] to it.toCardTerms() }
            }
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            call.respond(cards.map { acc ->
                cardSummaryFor(enrichWith(acc, eventsByAccount[acc.id] ?: emptyList(), rate), termsByAccount[acc.id])
            })
        }

        // Alta atómica: cuenta CREDIT_CARD + evento de deuda inicial (si la hay) + términos en
        // UNA transacción — mismo patrón que POST /api/credits (la excepción atómica documentada
        // en el KDoc de openingEventFor: este endpoint no pasa por ningún flujo offline, así que
        // no hay ventana en la que cliente y server fabriquen la apertura dos veces).
        post {
            val uid = call.userId()
            val body = call.receive<CreateCardRequest>()
            val name = body.name.trim()
            if (name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Nombre de la tarjeta requerido")
            // A diferencia de un préstamo, 0 es válido: una tarjeta recién sacada no debe nada.
            if (body.initialDebt < 0L) return@post call.respond(HttpStatusCode.BadRequest, "La deuda no puede ser negativa")
            if (body.currency !in setOf("COP", "USD")) {
                return@post call.respond(HttpStatusCode.BadRequest, "Moneda no soportada — usa COP o USD")
            }

            val account = Account(
                id       = "acc_${System.currentTimeMillis()}",
                name     = name,
                type     = AccountType.CREDIT_CARD,
                balance  = body.initialDebt,
                currency = body.currency,
            )
            val terms = body.terms.copy(accountId = account.id).sanitized()
            val opening = openingEventFor(account, now = System.currentTimeMillis())

            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[Accounts.name] = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
                if (opening != null) insertEventRow(uid, opening)
                Cards.insert { fillCardTerms(it, uid, terms) }
            }
            call.respond(
                HttpStatusCode.Created,
                cardSummaryFor(enrichWith(account, listOfNotNull(opening), FxRateService.usdToCop()), terms),
            )
        }

        // Upsert de términos para una CREDIT_CARD que ya existe (creada desde Cuentas antes de
        // F51/F52): así las tarjetas viejas ganan cupo/corte/pago sin recrearse.
        put("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            if (account.type != AccountType.CREDIT_CARD) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Solo tarjetas de crédito llevan estos términos")
            }
            val body = call.receive<CardTerms>().copy(accountId = accountId).sanitized()
            // upsert atómico por PK (accountId), igual que en creditRoutes: lastRemindedPeriod
            // no está en el upsert, así que se conserva — un cambio de día aplica desde el mes
            // siguiente.
            dbQuery {
                Cards.upsert { fillCardTerms(it, uid, body) }
            }
            val enriched = enrichWith(account, loadNonVoidedEvents(uid, accountId), FxRateService.usdToCop())
            call.respond(cardSummaryFor(enriched, body))
        }

        delete("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val deleted = dbQuery {
                Cards.deleteWhere { (Cards.accountId eq accountId) and (Cards.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** Días acotados a 1–31 (mismo criterio que dayOfMonth en creditRoutes); el corte solo si vino. */
private fun CardTerms.sanitized(): CardTerms = copy(
    paymentDay = paymentDay.coerceIn(1, 31),
    cutoffDay  = cutoffDay?.coerceIn(1, 31),
)

private fun fillCardTerms(
    it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>,
    uid: String,
    terms: CardTerms,
) {
    it[Cards.accountId]   = terms.accountId
    it[Cards.userId]      = uid
    it[Cards.bank]        = terms.bank
    it[Cards.creditLimit] = terms.creditLimit
    it[Cards.cutoffDay]   = terms.cutoffDay
    it[Cards.paymentDay]  = terms.paymentDay
    it[Cards.notes]       = terms.notes
}
