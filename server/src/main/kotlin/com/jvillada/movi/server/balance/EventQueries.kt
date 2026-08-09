package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.isCashFlow
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/** Non-voided events for a user, optionally filtered to one account. */
suspend fun loadNonVoidedEvents(uid: String, accountId: String? = null): List<FinancialEvent> =
    dbQuery { loadNonVoidedEventsIn(uid, accountId) }

/**
 * Igual que [loadNonVoidedEvents] pero **dentro de una transacción ya abierta**.
 *
 * Existe para que un handler que ya tomó un lock de fila pueda leer los eventos sin salirse de
 * su transacción: `dbQuery` abre una nueva y soltaría la lectura fuera del lock.
 */
fun Transaction.loadNonVoidedEventsIn(uid: String, accountId: String? = null): List<FinancialEvent> {
    val voided = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    val types = accountTypesFor(uid)
    val accountFilter = if (accountId != null) Events.accountId eq accountId else Op.TRUE
    return Events.selectAll()
        .where { (Events.userId eq uid) and accountFilter }
        .filterNot { it[Events.id] in voided }
        .map { it.toFinancialEvent().withCashFlowFlag(types) }
}

/**
 * Tipo de cuenta por id, para [uid].
 *
 * Es la pieza que le falta a la tabla de eventos: `financial_events` no sabe si su cuenta es
 * un crédito o una caja de ahorros, y sin eso no se puede decidir qué suma como flujo de caja.
 */
fun Transaction.accountTypesFor(uid: String): Map<String, AccountType> =
    Accounts.selectAll()
        .where { Accounts.userId eq uid }
        .mapNotNull { row ->
            runCatching { AccountType.valueOf(row[Accounts.type]) }.getOrNull()
                ?.let { row[Accounts.id] to it }
        }
        .toMap()

/** Marca el evento con si cuenta o no como ingreso/egreso del mes (ver [isCashFlow]). */
fun FinancialEvent.withCashFlowFlag(typeByAccount: Map<String, AccountType>): FinancialEvent {
    val accountType = typeByAccount[accountId] ?: return this
    return copy(countsAsCashFlow = isCashFlow(accountType, type))
}
