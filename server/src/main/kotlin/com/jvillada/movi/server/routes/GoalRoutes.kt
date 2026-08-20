package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountCopValue
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.group
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * F26 — metas de ahorro. Reemplaza el `GET /api/goals` hardcodeado que vivía en
 * `FinanceRoutes.kt` (siempre devolvía `[]`; no había ni tabla ni alta). El "ahorrado" nunca se
 * guarda: cada GET lo deriva del saldo real de la cuenta elegida con el mismo [accountCopValue]
 * que ya usa `AccountRoutes.kt` — así una meta nunca puede desincronizarse de la plata real.
 */
fun Route.goalRoutes() {
    route("/api/goals") {
        get {
            val uid = call.userId()
            val rows = dbQuery {
                Goals.selectAll().where { Goals.userId eq uid }.map { it.toGoal() }
            }
            if (rows.isEmpty()) return@get call.respond(emptyList<Goal>())

            val accountTypeById = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.id inList rows.map { it.accountId }) }
                    .associate { it[Accounts.id] to AccountType.valueOf(it[Accounts.type]) }
            }
            val rate = FxRateService.usdToCop()
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            val enriched = rows.map { g ->
                // La cuenta pudo borrarse después de crear la meta (F55: borrar cuenta no
                // toca metas huérfanas todavía — anotado en el reporte). Sin tipo conocido,
                // "ahorrado" cae a 0 en vez de reventar el endpoint entero.
                val type = accountTypeById[g.accountId]
                val saved = if (type != null) accountCopValue(type, eventsByAccount[g.accountId] ?: emptyList(), rate) else 0L
                g.copy(saved = saved)
            }
            call.respond(enriched)
        }

        post {
            val uid = call.userId()
            val body = call.receive<Goal>()
            val name = body.name.trim()
            if (name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
            if (body.target <= 0L) return@post call.respond(HttpStatusCode.BadRequest, "El monto objetivo debe ser mayor a 0")

            val accountRow = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq body.accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            val type = AccountType.valueOf(accountRow[Accounts.type])
            if (type.group == AccountGroup.DEUDA) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "Elige una cuenta de Dinero o Inversión — una meta no se ahorra en una deuda")
            }

            val goal = body.copy(id = "goal_${UUID.randomUUID()}", name = name)
            dbQuery {
                Goals.insert {
                    it[id]         = goal.id
                    it[userId]     = uid
                    it[Goals.name] = goal.name
                    it[target]     = goal.target
                    it[accountId]  = goal.accountId
                    it[targetDate] = goal.targetDate
                    it[createdAt]  = System.currentTimeMillis()
                }
            }
            val rate = FxRateService.usdToCop()
            val saved = accountCopValue(type, loadNonVoidedEvents(uid, goal.accountId), rate)
            call.respond(HttpStatusCode.Created, goal.copy(saved = saved))
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receive<Goal>()
            val name = body.name.trim()
            if (name.isBlank()) return@put call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
            if (body.target <= 0L) return@put call.respond(HttpStatusCode.BadRequest, "El monto objetivo debe ser mayor a 0")

            val accountRow = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq body.accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            val type = AccountType.valueOf(accountRow[Accounts.type])
            if (type.group == AccountGroup.DEUDA) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Elige una cuenta de Dinero o Inversión — una meta no se ahorra en una deuda")
            }

            val updated = dbQuery {
                Goals.update({ (Goals.id eq id) and (Goals.userId eq uid) }) {
                    it[Goals.name] = name
                    it[target]     = body.target
                    it[accountId]  = body.accountId
                    it[targetDate] = body.targetDate
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound)

            val rate = FxRateService.usdToCop()
            val saved = accountCopValue(type, loadNonVoidedEvents(uid, body.accountId), rate)
            call.respond(Goal(id = id, name = name, target = body.target, accountId = body.accountId, targetDate = body.targetDate, saved = saved))
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val deleted = dbQuery {
                Goals.deleteWhere { (Goals.id eq id) and (Goals.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ResultRow.toGoal() = Goal(
    id         = this[Goals.id],
    name       = this[Goals.name],
    target     = this[Goals.target],
    accountId  = this[Goals.accountId],
    targetDate = this[Goals.targetDate],
    saved      = 0L,  // se llena aparte — ver goalRoutes() GET
)
