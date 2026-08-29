package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.insert

/**
 * Inserta un [FinancialEvent] wire para [uid]. Debe llamarse dentro de una transacción.
 *
 * **La creación se completa acá para todo lo que pase por esta función**: la apertura de una
 * cuenta, la de una tarjeta, el ajuste de un crédito y las dos patas de un traspaso. Ninguna de
 * esas rutas tuvo que cambiar — todas heredan el sello, que es lo que se quiere: un movimiento sin
 * fecha de creación es un movimiento que no se puede ordenar dentro de su día.
 *
 * Ojo: esto **no es el único embudo de escritura**, y llamarlo así era falso. Un `financial_event`
 * nace hoy por **seis** puertas, tres por lado, y el invariante «nada nace sin sello» solo se
 * sostiene porque las seis sellan. Están enumeradas para que se pueda verificar de un vistazo el
 * día que aparezca una séptima:
 *
 * **Server**
 * 1. `insertEventRow` — esta función (aperturas, ajuste de crédito, las dos patas de un traspaso).
 *    Respeta el sello del cliente; si no viene, `now`.
 * 2. `POST /api/events` (`EventRoutes`) — tiene su propio `Events.insert`, no pasa por acá.
 *    Respeta el sello del cliente; si no viene, `now`.
 * 3. `createEventFromParsed` (`StatementRoutes`) — cada fila de un extracto importado. Sella `now`
 *    (la hora de la importación), porque el extracto no trae cuándo se anotó.
 *
 * **Espejo local del teléfono** (`LocalRepository`, `:core`)
 * 4. `postEvent` — acá nace el sello del lado del cliente, que es el punto del diseño: el instante
 *    real en que el dueño escribió, no aquel en que el `SyncEngine` lo empujó.
 * 5. `mirrorTransferLocally` — copia el sello que vino en la respuesta del server; `now` de
 *    respaldo.
 * 6. `adjustCreditBalance` — ídem: el ajuste lo creó el server y se copia su sello.
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
