package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class SyncEngine(
    private val db: MoviDatabase,
    private val remote: WalletRepository,
    private val userId: () -> String,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        scope.launch {
            while (true) {
                delay(30_000L)
                try { syncEvents() } catch (_: Exception) {}
                try { syncVoids() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Empuja los eventos pendientes y sella los que llegaron con `markSyncedIfUnchanged`.
     *
     * `markSyncedIfUnchanged`, no `markSynced` a secas: entre el SELECT de acá arriba y el
     * `postEvent` (una llamada de red, sin ningún lock sobre la fila mientras está en vuelo) la
     * categoría puede cambiar por otro camino — [com.jvillada.movi.shared.repository.LocalRepository.updateEventCategory]
     * resuelve local cuando `syncedAt` sigue null, que es exactamente la ventana en la que este
     * ciclo está trabajando. Si esta función sellara con `markSynced` a secas usando el snapshot
     * viejo (`row.category`), la fila quedaría "sincronizada" con la categoría vieja en el server
     * y la corregida solo en local — y como ya no sale en `selectUnsynced`, ningún ciclo futuro
     * la volvería a empujar: la divergencia sería silenciosa y permanente. `updateEventCategory`
     * revalida esa misma carrera del otro lado (adentro de su propia transacción), pero eso solo
     * cubre la mitad: la revalidación ve si SyncEngine YA selló antes de que ese código corriera,
     * no si SyncEngine va a sellar DESPUÉS con un snapshot desactualizado — que es este caso.
     *
     * Con la condición `AND category = :category`, si la categoría cambió el UPDATE no toca
     * ninguna fila: `syncedAt` se queda en null y el próximo ciclo (30s) la vuelve a levantar de
     * `selectUnsynced`, esta vez con la categoría ya corregida. Nota: eso reintenta un
     * `postEvent` con un id que el server ya tiene — si el evento original sí llegó a insertarse,
     * ese reintento va a fallar (conflicto de id) y quedar atrapado por el catch de abajo,
     * reintentando en silencio cada ciclo. Es preferible a la alternativa (divergencia
     * silenciosa y PERMANENTE): acá el evento sigue visible en `selectUnsynced`, así que el
     * problema es diagnosticable. Arreglar ese reintento de raíz —enseñarle a SyncEngine a
     * distinguir "nunca llegó" de "ya llegó, solo cambió la categoría" y usar
     * `remote.updateEventCategory` en ese segundo caso— queda fuera de este fix.
     */
    private suspend fun syncEvents() {
        val unsynced = db.financialEventQueries.selectUnsynced(userId()).executeAsList()
        for (row in unsynced) {
            try {
                remote.postEvent(
                    FinancialEvent(
                        id = row.id, accountId = row.accountId,
                        type = TransactionType.valueOf(row.type),
                        amount = row.amount, category = row.category,
                        description = row.description, merchant = row.merchant,
                        timestamp = row.timestamp,
                        source = EventSource.valueOf(row.source),
                        rawPayload = row.rawPayload,
                        reconciliationStatus = ReconciliationStatus.valueOf(row.reconciliationStatus),
                        syncedAt = row.syncedAt,
                    )
                )
                db.financialEventQueries.markSyncedIfUnchanged(
                    Clock.System.now().toEpochMilliseconds(), row.id, row.category,
                )
            } catch (_: Exception) {}
        }
    }

    private suspend fun syncVoids() {
        val unsynced = db.voidEventQueries.selectUnsynced().executeAsList()
        for (row in unsynced) {
            try {
                remote.voidEvent(row.originalEventId, row.reason)
                db.voidEventQueries.markSynced(
                    Clock.System.now().toEpochMilliseconds(), row.id
                )
            } catch (_: Exception) {}
        }
    }
}
