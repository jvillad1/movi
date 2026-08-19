package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.netWorth
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun Route.financeRoutes() {
    // F50: ya no tiene consumidor — Inversiones (:shared) pasó a mostrar cuentas tipo
    // INVESTMENT en vez de "posiciones" (un modelo que nunca tuvo alta). Se deja el endpoint
    // porque no rompe nada mantenerlo, pero WalletRepository (:core) ya no expone getHoldings.
    get("/api/holdings") { call.respond(emptyList<Holding>()) }
    get("/api/goals") { call.respond(emptyList<Goal>()) }

    // ── Budgets — per-user, DB-backed ─────────────────────────────────────────

    get("/api/budgets") {
        val uid = call.userId()
        val list = dbQuery {
            Budgets.selectAll()
                .where { Budgets.userId eq uid }
                .map { Budget(it[Budgets.category], it[Budgets.monthlyLimit]) }
        }
        call.respond(list)
    }

    post("/api/budgets") {
        val uid = call.userId()
        val body = call.receive<Budget>()
        val exists = dbQuery {
            Budgets.selectAll()
                .where { (Budgets.userId eq uid) and (Budgets.category eq body.category) }
                .count() > 0
        }
        if (exists) return@post call.respond(HttpStatusCode.Conflict, "Category exists: ${body.category}")
        dbQuery {
            Budgets.insert {
                it[userId]       = uid
                it[category]     = body.category
                it[monthlyLimit] = body.monthlyLimit
            }
        }
        call.respond(HttpStatusCode.Created, body)
    }

    put("/api/budgets/{category}") {
        val uid = call.userId()
        val cat = call.parameters["category"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<Budget>()
        val updated = dbQuery {
            Budgets.update({ (Budgets.userId eq uid) and (Budgets.category eq cat) }) {
                it[monthlyLimit] = body.monthlyLimit
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound)
        else call.respond(body.copy(category = cat))
    }

    delete("/api/budgets/{category}") {
        val uid = call.userId()
        val cat = call.parameters["category"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val deleted = dbQuery {
            Budgets.deleteWhere { (Budgets.userId eq uid) and (Budgets.category eq cat) }
        }
        if (deleted == 0) call.respond(HttpStatusCode.NotFound)
        else call.respond(HttpStatusCode.NoContent)
    }

    // ── Finance summary — computed from real Events ────────────────────────────

    get("/api/finance-summary") {
        val uid = call.userId()
        val raw = call.request.queryParameters["scope"] ?: "SELF"
        val scope = runCatching { Scope.valueOf(raw.uppercase()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope: $raw")

        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val monthStart = now.withDayOfMonth(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
        val monthEnd = now.withDayOfMonth(1).plusMonths(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

        val rate = FxRateService.usdToCop()
        val accountRows = dbQuery {
            Accounts.selectAll().where { Accounts.userId eq uid }
                .map { it[Accounts.id] to AccountType.valueOf(it[Accounts.type]) }
        }
        val nonVoidedEvents = loadNonVoidedEvents(uid)
        val eventsByAccount = nonVoidedEvents.groupBy { it.accountId }
        // netWorth, no accountCopValue sumado de frente (Hallazgo menor 4 de la revisión de esta
        // rama) — ver su KDoc. Nadie renderiza este campo hoy (Dashboard y el SDUI usan
        // assetsDebtsNet(accounts) del lado del cliente), pero dejarlo mal calculado a la espera
        // de que alguien lo cablee es peor que arreglarlo ahora: quien lo use primero se
        // encontraría con el mismo "patrimonio" hinchado por la deuda que esta rama vino a matar
        // en otras pantallas.
        val derivedBalance = netWorth(accountRows, eventsByAccount, rate)

        val summary = dbQuery {
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()

            val monthEvents = Events.selectAll().where {
                (Events.userId eq uid) and
                (Events.timestamp greaterEq monthStart) and
                (Events.timestamp less monthEnd)
            }.filterNot { it[Events.id] in voidedIds }

            val accountTypeById = accountTypesFor(uid)
            // Movimientos de cuentas de deuda NO son flujo de caja del mes (ver isCashFlow):
            // bajar la deuda de un crédito al saldo real del banco no es un ingreso, y subirla
            // no es un gasto — es la misma plata cambiando de periodo. Esto también corrige la
            // "Deuda inicial" de apertura, que venía contándose como egreso del mes.
            val cashFlow = monthEvents.filter { row ->
                val accountType = accountTypeById[row[Events.accountId]]
                accountType == null ||
                    isCashFlow(accountType, TransactionType.valueOf(row[Events.type]), row[Events.category])
            }

            val ingresos = cashFlow
                .filter { it[Events.type] == TransactionType.INCOME.name && it[Events.currency] == "COP" }
                .sumOf { it[Events.amount] }
            val egresos = cashFlow
                .filter { it[Events.type] == TransactionType.EXPENSE.name && it[Events.currency] == "COP" }
                .sumOf { it[Events.amount] }

            FinanceSummary(
                scope = scope,
                balance = derivedBalance,
                ingresos = ingresos,
                egresos = egresos,
                // Del usuario completo, no del mes ni del `scope` — ver KDoc del campo en
                // FinanceSummary. `scope` hoy no filtra nada en este endpoint (ver arriba:
                // ni accountRows ni nonVoidedEvents lo usan), así que no hay nada que
                // "desfiltrar" acá más que la apertura de cuenta (F54): un "Saldo inicial" no es
                // un movimiento que el usuario haya anotado, así que no cuenta para la guía de
                // primeros pasos.
                eventCount = nonVoidedEvents.count { it.category != OPENING_CATEGORY },
            )
        }
        call.respond(summary)
    }
}
