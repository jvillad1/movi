package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.insert

/**
 * Inserta un [FinancialEvent] wire para [uid]. Debe llamarse dentro de una transacción.
 *
 * **La creación se completa acá, en el único embudo de escritura**, y no en cada ruta: por esta
 * función pasan la apertura de una cuenta, la de una tarjeta, el ajuste de un crédito y las dos
 * patas de un traspaso. Ninguna de esas rutas tuvo que cambiar — todas heredan el sello, que es
 * lo que se quiere: un movimiento sin fecha de creación es un movimiento que no se puede ordenar
 * dentro de su día.
 *
 * Si el evento **ya trae** [FinancialEvent.createdAt] se respeta: lo puso el cliente en el
 * momento real en que el dueño lo escribió, y ese instante el server no lo conoce (ver el KDoc
 * del campo). Si no viene, el server sella `now`, que para la web —donde el POST sale apenas se
 * guarda— es exactamente el mismo instante.
 */
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
        it[Events.createdAt]     = event.createdAt ?: System.currentTimeMillis()
    }
}
