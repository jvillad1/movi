package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private val amountRegex = Regex("""\$\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?)""")
private val merchantInRegex = Regex("""en\s+(.+?)(?:\s+el\s|\s+a\s+las|\.|$)""", RegexOption.IGNORE_CASE)
private val merchantOfRegex = Regex("""de\s+(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)

internal fun parseSms(text: String): ParsedSms? {
    val rawAmount = amountRegex.find(text)?.groupValues?.get(1) ?: return null
    val amount = rawAmount.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null

    val type = when {
        text.contains("Recibiste", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Nómina recibida", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Compra", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Pago", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Retiro", ignoreCase = true) -> TransactionType.EXPENSE
        else -> TransactionType.EXPENSE
    }

    val merchant = when {
        text.contains("Nómina recibida", ignoreCase = true) -> "Nómina"
        type == TransactionType.INCOME -> merchantOfRegex.find(text)?.groupValues?.get(1)?.trim() ?: "Transferencia recibida"
        else -> merchantInRegex.find(text)?.groupValues?.get(1)?.trim() ?: "Movimiento"
    }

    val category = categoryFor(merchant, type)
    return ParsedSms(amount, merchant, type, category)
}

private fun categoryFor(merchant: String, type: TransactionType): String {
    if (type == TransactionType.INCOME) {
        return if (merchant.equals("Nómina", true)) "Nómina" else "Transferencia"
    }
    val m = merchant.lowercase()
    return when {
        "uber" in m || "didi" in m || "taxi" in m -> "Transporte"
        "crepes" in m || "waffles" in m || "rappi" in m || "mcdonald" in m -> "Restaurantes"
        "éxito" in m || "exito" in m || "carulla" in m || "olímpica" in m || "olimpica" in m || "d1" in m || "ara" in m -> "Mercado"
        "drogas" in m || "farma" in m || "salud" in m || "medi" in m -> "Salud"
        "netflix" in m || "spotify" in m || "disney" in m || "hbo" in m || "youtube" in m -> "Suscripción"
        "claro" in m || "movistar" in m || "tigo" in m || "epm" in m || "energía" in m -> "Servicios"
        else -> "Otro"
    }
}

fun Route.smsRoutes() {
    get("/api/sms") {
        val uid = call.userId()
        val list = dbQuery {
            SmsMessages.selectAll().where { SmsMessages.userId eq uid }.map { it.toSmsMessage() }
        }
        call.respond(list)
    }

    get("/api/sms/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(sms)
    }

    get("/api/sms/{id}/parse") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text)
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, "No se pudo parsear")
        call.respond(parsed)
    }

    post("/api/sms/{id}/confirm") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = "confirmed"
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.OK)
    }

    post("/api/sms/{id}/ignore") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = "ignored"
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }

    post("/api/sms/sync") {
        val uid = call.userId()
        val messages = call.receive<List<SmsMessage>>()
        val insertedCount = dbQuery {
            // Collect existing ids for this user so we can skip duplicates
            // without touching rows that may already have a user-set state.
            val existingIds = SmsMessages
                .selectAll()
                .where { SmsMessages.userId eq uid }
                .map { it[SmsMessages.id] }
                .toSet()

            var count = 0
            for (msg in messages) {
                if (msg.id in existingIds) continue
                SmsMessages.insert {
                    it[id]     = msg.id
                    it[userId] = uid
                    it[time]   = msg.time
                    it[bank]   = msg.bank
                    it[text]   = msg.text
                    it[state]  = "new" // server owns state; /confirm + /ignore transition it. Never trust client.
                    it[det]    = msg.det
                }
                count++
            }
            count
        }
        call.respond(mapOf("synced" to insertedCount))
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toSmsMessage() = SmsMessage(
    id    = this[SmsMessages.id],
    time  = this[SmsMessages.time],
    bank  = this[SmsMessages.bank],
    text  = this[SmsMessages.text],
    state = this[SmsMessages.state],
    det   = this[SmsMessages.det],
)
