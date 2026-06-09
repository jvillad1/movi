package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/** Non-voided events for a user, optionally filtered to one account. */
suspend fun loadNonVoidedEvents(uid: String, accountId: String? = null): List<FinancialEvent> = dbQuery {
    val voided = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    val accountFilter = if (accountId != null) Events.accountId eq accountId else Op.TRUE
    Events.selectAll()
        .where { (Events.userId eq uid) and accountFilter }
        .filterNot { it[Events.id] in voided }
        .map { it.toFinancialEvent() }
}
