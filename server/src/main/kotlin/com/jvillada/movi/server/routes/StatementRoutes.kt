package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.StatementDocumentType
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
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

        val docType = StatementParser.detectDocumentType(text)
        if (docType != StatementDocumentType.TRANSACTION_STATEMENT) {
            val msg = when (docType) {
                StatementDocumentType.LOAN_SUMMARY ->
                    "Este documento es un resumen de crédito, no un extracto de movimientos. No contiene transacciones importables."
                StatementDocumentType.INVESTMENT_FUND ->
                    "Este documento es un estado de fondo de inversión. No contiene transacciones importables."
                else -> "Documento no reconocido como extracto de transacciones."
            }
            call.respond(HttpStatusCode.UnprocessableEntity, msg)
            return@post
        }

        val bankName = StatementParser.detectBankName(fileName, text)
        val rules = Stores.merchantRules.getRules(uid)
        val parsed = ClaudeStatementParser.parse(text, rules)

        val voidedIds = dbQuery {
            VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
        }
        val existing = dbQuery {
            Events.selectAll()
                .where {
                    (Events.userId eq uid) and
                    (if (voidedIds.isNotEmpty()) Events.id notInList voidedIds else Op.TRUE)
                }
                .map { it.toFinancialEvent() }
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
                        ev.currency == tx.currency &&
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

        val accountExists = dbQuery {
            Accounts.selectAll()
                .where { (Accounts.id eq decision.accountId) and (Accounts.userId eq uid) }
                .count() > 0
        }
        if (!accountExists) {
            call.respond(HttpStatusCode.NotFound, "Account not found")
            return@post
        }

        val importId = "si_${UUID.randomUUID()}"
        var importedCount = 0
        var reconciledCount = 0

        for (tx in decision.imports) {
            createEventFromParsed(tx, decision.accountId, uid, importId)
            importedCount++
        }

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
                            it[category]          = finalCategory
                            it[description]       = finalDescription
                            it[merchant]          = finalMerchant
                            it[statementImportId] = importId
                        }
                    }

                    if (dec.parsed.category != existCat) {
                        Stores.merchantRules.saveRule(uid, MerchantRule(
                            merchantPattern = dec.parsed.merchant.lowercase().trim(),
                            category = finalCategory,
                        ))
                    }
                    reconciledCount++
                }
            } else {
                createEventFromParsed(dec.parsed, decision.accountId, uid, importId)
                importedCount++
            }
        }

        dbQuery {
            StatementImports.insert {
                it[id]             = importId
                it[userId]         = uid
                it[accountId]      = decision.accountId
                it[bankName]       = decision.bankName
                it[period]         = decision.period
                it[importedAt]     = System.currentTimeMillis()
                it[StatementImports.importedCount]   = importedCount
                it[StatementImports.reconciledCount] = reconciledCount
            }
        }

        call.respond(HttpStatusCode.OK, mapOf("imported" to importedCount + reconciledCount))
    }

    get("/api/statements/imports") {
        val uid = call.userId()
        val imports = dbQuery {
            StatementImports.selectAll()
                .where { StatementImports.userId eq uid }
                .orderBy(StatementImports.importedAt, SortOrder.DESC)
                .map { rowToStatementImport(it) }
        }
        call.respond(imports)
    }

    get("/api/statements/imports/{id}") {
        val uid = call.userId()
        val importId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing id")
            return@get
        }

        val importRow = dbQuery {
            StatementImports.selectAll()
                .where { (StatementImports.id eq importId) and (StatementImports.userId eq uid) }
                .firstOrNull()
        }
        if (importRow == null) {
            call.respond(HttpStatusCode.NotFound, "Import not found")
            return@get
        }

        val events = dbQuery {
            Events.selectAll()
                .where { (Events.statementImportId eq importId) and (Events.userId eq uid) }
                .map { it.toFinancialEvent() }
        }

        call.respond(StatementImportDetail(rowToStatementImport(importRow), events))
    }
}

private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String, importId: String) {
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
            it[Events.currency]      = tx.currency
            it[category]             = tx.category
            it[description]          = tx.description
            it[merchant]             = tx.merchant
            it[timestamp]            = ts
            it[eventSource]          = EventSource.STATEMENT.name
            it[rawPayload]           = tx.rawText.ifBlank { null }
            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
            it[syncedAt]             = null
            it[statementImportId]    = importId
        }
    }
}

private fun rowToStatementImport(row: ResultRow) = StatementImport(
    id              = row[StatementImports.id],
    accountId       = row[StatementImports.accountId],
    bankName        = row[StatementImports.bankName],
    period          = row[StatementImports.period],
    importedAt      = row[StatementImports.importedAt],
    importedCount   = row[StatementImports.importedCount],
    reconciledCount = row[StatementImports.reconciledCount],
)

private fun monthName(month: Int) = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)[month - 1]
