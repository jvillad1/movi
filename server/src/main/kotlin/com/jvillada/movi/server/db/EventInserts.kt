package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.insert

/** Inserta un [FinancialEvent] wire para [uid]. Debe llamarse dentro de una transacción. */
fun insertEventRow(uid: String, event: FinancialEvent) {
    Events.insert {
        it[id]                   = event.id
        it[userId]               = uid
        it[accountId]            = event.accountId
        it[type]                 = event.type.name
        it[amount]               = event.amount
        it[Events.currency]      = event.currency
        it[category]             = event.category
        it[description]          = event.description
        it[merchant]             = event.merchant
        it[timestamp]            = event.timestamp
        it[eventSource]          = event.source.name
        it[rawPayload]           = event.rawPayload
        it[reconciliationStatus] = event.reconciliationStatus.name
        it[syncedAt]             = event.syncedAt
        it[Events.transferId]    = event.transferId
    }
}
