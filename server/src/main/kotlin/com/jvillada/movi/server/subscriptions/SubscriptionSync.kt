package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Corre la detección de suscripciones y el upsert por estados para [uid].
 * Best-effort: el caller decide si un fallo importa (el import de extractos lo
 * envuelve en runCatching; la ruta /detect lo deja propagar).
 */
suspend fun runSubscriptionDetection(uid: String) {
    val events = loadNonVoidedEvents(uid)
        .filterNot { it.description.startsWith(FAMIRIOS_STAMP_PREFIX) }
    val detected = detectSubscriptions(events, LocalDate.now(ZoneOffset.UTC))
    dbQuery {
        val existing = Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .associateBy { it[Subscriptions.merchantKey] to it[Subscriptions.currency] }
        for (d in detected) {
            upsertDetected(uid, d, existing[d.merchantKey to d.currency])
        }
    }
}

// SQLSTATE estándar (Postgres y H2) para violación de índice único.
private const val UNIQUE_VIOLATION_SQLSTATE = "23505"

// FamiriosParser.kt stampa cada ParsedTransaction que genera con este prefijo exacto en
// `description` ("Famirios · $label · $mes $año"). Son agregados mensuales de presupuesto
// (un EXPENSE por categoría×mes, monto estable, fecha de fin de mes) — cumplen toda la
// heurística del detector pero NO son suscripciones reales, así que se excluyen del pool de
// eventos ANTES de detectar. Se filtra por `description` (no por `category`, que varía por
// categoría de gasto, ni por `rawPayload`, que no se usa) porque es el único campo con un
// discriminador fijo y determinístico para todos los eventos de este origen.
private const val FAMIRIOS_STAMP_PREFIX = "Famirios · "

private fun statusForNew(d: DetectedSub): SubStatus =
    if (d.confidence == SubConfidence.HIGH) SubStatus.AUTO else SubStatus.CANDIDATE

// Check-then-insert de por sí no es atómico: dos detects concurrentes (doble tap en
// "Re-escanear") pueden ver `row == null` a la vez y ambos intentar el mismo
// (userId, merchantKey, currency). El índice único uq_subscriptions_user_merchant_currency
// deja pasar solo a uno; el otro cae en ExposedSQLException (23505). En vez de propagar
// un 500, el perdedor re-lee la fila ganadora y aplica la MISMA rama que habría aplicado
// si la hubiera visto desde el inicio (DISMISSED se respeta, CONFIRMED se actualiza
// parcialmente, AUTO/CANDIDATE se refresca por completo) — ambas transacciones calculan el
// mismo DetectedSub a partir de los mismos eventos, así que converger en el update es
// equivalente a haber ganado el insert.
//
// El insert va detrás de un SAVEPOINT: en Postgres, un error dentro de una transacción la
// deja "abortada" (cualquier statement posterior falla) salvo que se haga rollback a un
// savepoint — por eso no basta con un try/catch simple si se quiere seguir usando la misma
// transacción externa (dbQuery) para el re-read + update.
private fun Transaction.upsertDetected(uid: String, d: DetectedSub, row: ResultRow?) {
    if (row != null) {
        applyExisting(row, d)
        return
    }
    val savepoint = connection.setSavepoint("sub_detect_${d.merchantKey}_${d.currency}")
    try {
        insertNew(uid, d)
        connection.releaseSavepoint(savepoint)
    } catch (e: ExposedSQLException) {
        if (e.sqlState != UNIQUE_VIOLATION_SQLSTATE) throw e
        connection.rollback(savepoint)
        val winner = Subscriptions.selectAll()
            .where {
                (Subscriptions.userId eq uid) and
                    (Subscriptions.merchantKey eq d.merchantKey) and
                    (Subscriptions.currency eq d.currency)
            }
            .firstOrNull() ?: throw e
        applyExisting(winner, d)
    }
}

private fun applyExisting(row: ResultRow, d: DetectedSub) {
    when {
        row[Subscriptions.status] == SubStatus.DISMISSED.name -> Unit  // el usuario dijo que no
        row[Subscriptions.status] == SubStatus.CONFIRMED.name ->
            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                it[amount]      = d.amount
                it[lastSeen]    = d.lastSeen
                it[occurrences] = d.occurrences
                it[confidence]  = d.confidence.name
            }
        else -> refreshRow(row[Subscriptions.id], d)  // AUTO o CANDIDATE: refrescar todo y re-evaluar estado
    }
}

private fun refreshRow(rowId: String, d: DetectedSub) {
    Subscriptions.update({ Subscriptions.id eq rowId }) {
        it[displayName] = d.displayName
        it[amount]      = d.amount
        it[dayOfMonth]  = d.dayOfMonth
        it[status]      = statusForNew(d).name
        it[confidence]  = d.confidence.name
        it[firstSeen]   = d.firstSeen
        it[lastSeen]    = d.lastSeen
        it[occurrences] = d.occurrences
        it[accountId]   = d.accountId
    }
}

private fun insertNew(uid: String, d: DetectedSub) {
    Subscriptions.insert {
        it[id]          = "sub_${UUID.randomUUID()}"
        it[userId]      = uid
        it[merchantKey] = d.merchantKey
        it[displayName] = d.displayName
        it[amount]      = d.amount
        it[currency]    = d.currency
        it[dayOfMonth]  = d.dayOfMonth
        it[status]      = statusForNew(d).name
        it[confidence]  = d.confidence.name
        it[firstSeen]   = d.firstSeen
        it[lastSeen]    = d.lastSeen
        it[occurrences] = d.occurrences
        it[accountId]   = d.accountId
    }
}
