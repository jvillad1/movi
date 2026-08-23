package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadCardRulePairs
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.reminders.upcomingPayments
import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID
import com.jvillada.movi.server.time.AppClock

private fun org.jetbrains.exposed.sql.ResultRow.toRule() = RecurringRule(
    id = this[RecurringRules.id],
    name = this[RecurringRules.name],
    category = this[RecurringRules.category],
    amount = this[RecurringRules.amount],
    dayOfMonth = this[RecurringRules.dayOfMonth],
    type = TransactionType.valueOf(this[RecurringRules.type]),
    remindMe = this[RecurringRules.remindMe],
)

fun Route.reminderRoutes() {
    get("/api/recurring-rules") {
        val uid = call.userId()
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        call.respond(rules)
    }

    post("/api/recurring-rules") {
        val uid = call.userId()
        val body = call.receive<RecurringRule>()
        val newId = "rr_${UUID.randomUUID()}"
        dbQuery {
            RecurringRules.insert {
                it[id] = newId
                it[userId] = uid
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
                // El body de un cliente viejo no trae el campo; el default del modelo lo pone
                // en true, que es el comportamiento que ese cliente espera.
                it[remindMe] = body.remindMe
            }
        }
        call.respond(HttpStatusCode.Created, body.copy(id = newId))
    }

    put("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<RecurringRule>()
        val updated = dbQuery {
            RecurringRules.update({ (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }) {
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
                it[remindMe] = body.remindMe
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(body.copy(id = id))
    }

    delete("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val deleted = dbQuery {
            RecurringRules.deleteWhere { (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }
        }
        if (deleted == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }

    get("/api/payments/upcoming") {
        val uid = call.userId()
        val leadDays = System.getenv("REMINDER_LEAD_DAYS")?.toIntOrNull() ?: DEFAULT_REMINDER_LEAD_DAYS
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        val creditRules = loadCreditRulePairs(uid).map { it.first }
        // F20: el pago de la tarjeta también es un próximo pago — con la deuda actual como monto.
        val cardRules = loadCardRulePairs(uid).map { it.first }
        call.respond(upcomingPayments(rules + creditRules + cardRules, AppClock.today(), leadDays))
    }
}
