package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.push.WebPushSender
import com.jvillada.movi.server.push.buildSmsPushPayload
import com.jvillada.movi.server.sms.SmsDedupeIndex
import com.jvillada.movi.server.sms.SmsKey
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
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

    val category = categoryFor(text, merchant, type)
    return ParsedSms(amount, merchant, type, category)
}

/**
 * [text] es el SMS completo, no solo [merchant]: "pago tc"/"pago tarjeta" viven en frases como
 * "Pago autom TC ...1234 por $80.894" que `merchantInRegex` no captura (no tiene "en <algo>"),
 * así que el merchant extraído llega como "Movimiento" y perdería la señal. Se revisa el texto
 * crudo antes de caer en las reglas por merchant.
 */
private fun categoryFor(text: String, merchant: String, type: TransactionType): String {
    if (type == TransactionType.EXPENSE && looksLikeCardPayment(text, category = "")) {
        return CARD_PAYMENT_CATEGORY
    }
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
        val inserted = mutableListOf<SmsMessage>()
        val insertedCount = dbQuery {
            // Collect existing rows for this user so we can skip duplicates
            // without touching rows that may already have a user-set state.
            // One query per sync, never one per message.
            val existingRows = SmsMessages
                .selectAll()
                .where { SmsMessages.userId eq uid }
                .map { Triple(it[SmsMessages.id], it[SmsMessages.text], it[SmsMessages.time]) }

            val existingIds = existingRows.map { it.first }.toSet()

            // Dedupe cross-esquema por texto + tiempo (issue #27).
            //
            // El camino realtime inserta ids `sms_rt_<16hex>` y el backfill del inbox
            // inserta ids `sms_<32hex>` para el MISMO SMS físico: hashean fuentes de
            // timestamp distintas, así que los ids nunca coinciden y el chequeo por id
            // de arriba deja pasar el mismo SMS bancario dos veces.
            //
            // El texto tampoco alcanza como clave. Los SMS de compra no traen fecha, ni
            // hora, ni referencia ("Compra aprobada $28.500 en Uber BV."), así que dos
            // transacciones REALES distintas producen texto byte-idéntico y el dedupe por
            // texto se comía la segunda en silencio, con `synced` mintiendo. En una app de
            // finanzas personales perder un movimiento sin señal es peor que mostrar un
            // duplicado que el usuario puede ignorar.
            //
            // Lo que separa los dos casos es el tiempo: las dos fuentes fechan el mismo
            // SMS físico a lo sumo un minuto aparte tras truncar a minutos, mientras que
            // dos transacciones distintas están a horas o días. Ver SMS_DEDUPE_TOLERANCE
            // para el residual conocido (delay de entrega) y por qué no se ensancha para
            // cubrirlo. `bank` sigue fuera de la clave: los dos caminos divergen en el
            // remitente vacío (backfill "" vs realtime "SMS").
            val dedupe = SmsDedupeIndex(existingRows.map { SmsKey(it.second, it.third) })

            // Repeats del mismo id dentro de UN payload: el chequeo por texto+tiempo de
            // arriba solo los atrapa si el tiempo parsea (mismo id ⇒ mismo texto y tiempo
            // ⇒ mismo SmsKey). Si no parsea, ambos caen en el fallback "ilegible → insertar"
            // y el segundo insert choca contra la primary key, abortando la transacción
            // entera del sync. seenIds los filtra antes de llegar ahí, sin depender del parseo.
            val seenIds = mutableSetOf<String>()

            var count = 0
            for (msg in messages) {
                if (msg.id in existingIds) continue
                if (!seenIds.add(msg.id)) continue
                val key = SmsKey(msg.text, msg.time)
                if (dedupe.isDuplicate(key)) continue
                SmsMessages.insert {
                    it[id]     = msg.id
                    it[userId] = uid
                    it[time]   = msg.time
                    it[bank]   = msg.bank
                    it[text]   = msg.text
                    it[state]  = "new" // server owns state; /confirm + /ignore transition it. Never trust client.
                    it[det]    = msg.det
                }
                dedupe.add(key)
                inserted += msg
                count++
            }
            count
        }

        // Hook de push (spec sms-realtime): SMS nuevos parseables → una push agrupada.
        // Best-effort — jamás falla el sync; los que no parsean quedan en el inbox como siempre.
        //
        // Scoped to realtime captures only (final review fix): manual pull syncs
        // (SmsReader.android.kt) upload historical, unfiltered inbox contents — pushing
        // for those would spam the user with notifications for old SMS. Only messages
        // captured live by SmsRealtimeReceiver (ids prefixed `sms_rt_`) participate.
        val realtimeCaptures = inserted.filter { it.id.startsWith("sms_rt_") }
        if (realtimeCaptures.isNotEmpty() && WebPushSender.isConfigured()) {
            runCatching {
                val parsed = realtimeCaptures.mapNotNull { parseSms(it.text) }
                if (parsed.isNotEmpty()) WebPushSender.sendToUser(uid, buildSmsPushPayload(parsed))
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                call.application.log.warn("push de sms-sync falló para $uid", it)
            }
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
