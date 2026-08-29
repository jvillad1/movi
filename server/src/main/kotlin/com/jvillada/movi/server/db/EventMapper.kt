package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.ResultRow

/** Single source of truth for mapping an [Events] row to a [FinancialEvent]. */
fun ResultRow.toFinancialEvent(): FinancialEvent = FinancialEvent(
    id                   = this[Events.id],
    accountId            = this[Events.accountId],
    type                 = TransactionType.valueOf(this[Events.type]),
    amount               = this[Events.amount],
    currency             = this[Events.currency],
    category             = this[Events.category],
    description          = this[Events.description],
    merchant             = this[Events.merchant],
    timestamp            = this[Events.timestamp],
    source               = EventSource.valueOf(this[Events.eventSource]),
    rawPayload           = this[Events.rawPayload],
    reconciliationStatus = ReconciliationStatus.valueOf(this[Events.reconciliationStatus]),
    syncedAt             = this[Events.syncedAt],
    transferId           = this[Events.transferId],
    createdAt            = this[Events.createdAt],
)
