package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.StatementParser
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs

fun Route.statementRoutes() {

    post("/api/statements/upload") {
        val uid = call.userId()
        val multipart = call.receiveMultipart()
        var fileName = "statement"
        var bytes = ByteArray(0)

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "statement"
                bytes = part.streamProvider().readBytes()
            }
            part.dispose()
        }

        if (bytes.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "No file received")
            return@post
        }

        val text = StatementParser.extractText(bytes, fileName)
        val bankName = StatementParser.detectBankName(fileName)
        val rules = Stores.merchantRules.getRules(uid)
        val parsed = ClaudeStatementParser.parse(text, rules)

        val existing = dbQuery {
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()
            Events.selectAll()
                .where { Events.userId eq uid }
                .filter { row -> row[Events.id] !in voidedIds }
                .map { row ->
                    FinancialEvent(
                        id = row[Events.id],
                        accountId = row[Events.accountId],
                        type = TransactionType.valueOf(row[Events.type]),
                        amount = row[Events.amount],
                        category = row[Events.category],
                        description = row[Events.description],
                        merchant = row[Events.merchant],
                        timestamp = row[Events.timestamp],
                        source = EventSource.valueOf(row[Events.eventSource]),
                        rawPayload = row[Events.rawPayload],
                        reconciliationStatus = ReconciliationStatus.valueOf(row[Events.reconciliationStatus]),
                        syncedAt = row[Events.syncedAt],
                    )
                }
        }

        val matches = mutableListOf<ReconciliationMatch>()
        val newTransactions = mutableListOf<ParsedTransaction>()

        for (tx in parsed) {
            val parsedEpoch = runCatching {
                LocalDate.parse(tx.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()

            val match = if (parsedEpoch != null) {
                existing.firstOrNull { ev ->
                    ev.amount == tx.amount &&
                        abs(parsedEpoch - ev.timestamp) <= 2 * 86_400_000L
                }
            } else null

            if (match != null) {
                val sameDay = parsedEpoch != null &&
                    parsedEpoch / 86_400_000L == match.timestamp / 86_400_000L
                matches += ReconciliationMatch(
                    parsed = tx,
                    existingEventId = match.id,
                    existingEvent = match,
                    matchConfidence = if (sameDay) 0.95f else 0.7f,
                )
            } else {
                newTransactions += tx
            }
        }

        val period = runCatching {
            val date = LocalDate.parse(parsed.firstOrNull()?.date ?: "2025-01-01")
            "${monthName(date.monthValue)} ${date.year}"
        }.getOrDefault("")

        call.respond(
            StatementParseResult(
                statementId = UUID.randomUUID().toString(),
                bankName = bankName,
                period = period,
                newTransactions = newTransactions,
                matches = matches,
            )
        )
    }

    post("/api/statements/import") {
        val uid = call.userId()
        val decision = call.receive<ImportDecision>()
        var imported = 0

        val accountExists = dbQuery {
            Accounts.selectAll()
                .where { (Accounts.id eq decision.accountId) and (Accounts.userId eq uid) }
                .count() > 0
        }
        if (!accountExists) {
            call.respond(HttpStatusCode.NotFound, "Account not found")
            return@post
        }

        // Create events for new transactions
        for (tx in decision.imports) {
            createEventFromParsed(tx, decision.accountId, uid)
            imported++
        }

        // Process reconciliation decisions
        for (dec in decision.reconciliations) {
            if (dec.confirm) {
                val existingEvent = dbQuery {
                    Events.selectAll()
                        .where { (Events.id eq dec.existingEventId) and (Events.userId eq uid) }
                        .firstOrNull()?.let {
                            Triple(it[Events.category], it[Events.description], it[Events.merchant])
                        }
                }

                if (existingEvent != null) {
                    val (existCat, existDesc, existMerchant) = existingEvent
                    val finalCategory    = if (dec.categorySource    == FieldSource.STATEMENT) dec.parsed.category    else existCat
                    val finalDescription = if (dec.descriptionSource == FieldSource.STATEMENT) dec.parsed.description else existDesc
                    val finalMerchant    = if (dec.merchantSource    == FieldSource.STATEMENT) dec.parsed.merchant    else existMerchant

                    dbQuery {
                        Events.update({ (Events.id eq dec.existingEventId) and (Events.userId eq uid) }) {
                            it[category]    = finalCategory
                            it[description] = finalDescription
                            it[merchant]    = finalMerchant
                        }
                    }

                    // Save merchant rule when category differed
                    if (dec.parsed.category != existCat) {
                        Stores.merchantRules.saveRule(uid, MerchantRule(
                            merchantPattern = dec.parsed.merchant.lowercase().trim(),
                            category = finalCategory,
                        ))
                    }
                    imported++
                }
            } else {
                // User said "not the same" — create new event from parsed
                createEventFromParsed(dec.parsed, decision.accountId, uid)
                imported++
            }
        }

        call.respond(HttpStatusCode.OK, mapOf("imported" to imported))
    }
}

private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String) {
    val eventId = "ev_${UUID.randomUUID()}"
    val ts = runCatching {
        LocalDate.parse(tx.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }
    dbQuery {
        Events.insert {
            it[id]                   = eventId
            it[userId]               = uid
            it[Events.accountId]     = accountId
            it[type]                 = tx.type.name
            it[amount]               = tx.amount
            it[category]             = tx.category
            it[description]          = tx.description
            it[merchant]             = tx.merchant
            it[timestamp]            = ts
            it[eventSource]          = EventSource.STATEMENT.name
            it[rawPayload]           = tx.rawText.ifBlank { null }
            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
            it[syncedAt]             = null
        }
        val delta = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
        Accounts.update({ (Accounts.id eq accountId) and (Accounts.userId eq uid) }) {
            it[balance] = Accounts.balance + delta
        }
    }
}

private fun monthName(month: Int) = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)[month - 1]
