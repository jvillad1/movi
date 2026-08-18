package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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
                // syncAccounts ANTES que syncEvents: una cuenta creada offline (ver
                // LocalRepository.createAccount) tiene que existir en el server antes que sus
                // eventos, o EventRoutes.kt POST la rechaza con 404 "Account not found" — y ese
                // 404 quedaba tragado en silencio por el catch de syncEvents, así que el evento
                // (y la cuenta) nunca llegaban al server aunque hubiera red.
                try { syncAccounts() } catch (e: Exception) { logSyncFailure("syncAccounts", e) }
                try { syncEvents() } catch (e: Exception) { logSyncFailure("syncEvents", e) }
                try { syncVoids() } catch (e: Exception) { logSyncFailure("syncVoids", e) }
            }
        }
    }

    /**
     * Empuja las cuentas creadas offline (`syncedAt IS NULL`, ver [com.jvillada.movi.shared.repository.LocalRepository.createAccount])
     * y sella las que llegaron. Corre antes que [syncEvents] en [start] a propósito: los eventos
     * de una cuenta todavía no sincronizada rebotan con 404 contra el server (ver arriba).
     *
     * Igual que [syncEvents]: si `remote.createAccount` falla (sin red, o el server la rechaza)
     * la fila se queda sin sellar y el próximo ciclo la vuelve a intentar — sin distinguir esos
     * dos casos, mismo trade-off documentado en el KDoc de `LocalRepository.createAccount`.
     */
    internal suspend fun syncAccounts() {
        val unsynced = db.accountQueries.selectUnsynced(userId()).executeAsList()
        for (row in unsynced) {
            try {
                val created = remote.createAccount(
                    Account(
                        id = row.id, name = row.name,
                        type = AccountType.valueOf(row.type),
                        balance = row.balance, currency = row.currency,
                    )
                )
                db.accountQueries.markSynced(Clock.System.now().toEpochMilliseconds(), created.id)
            } catch (e: Exception) {
                logSyncFailure("syncAccounts", e, id = row.id)
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
    internal suspend fun syncEvents() {
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
            } catch (e: Exception) {
                logSyncFailure("syncEvents", e, id = row.id)
            }
        }
    }

    internal suspend fun syncVoids() {
        val unsynced = db.voidEventQueries.selectUnsynced().executeAsList()
        for (row in unsynced) {
            try {
                remote.voidEvent(row.originalEventId, row.reason)
                db.voidEventQueries.markSynced(
                    Clock.System.now().toEpochMilliseconds(), row.id
                )
            } catch (e: Exception) {
                logSyncFailure("syncVoids", e, id = row.id)
            }
        }
    }

    /**
     * Antes esta clase se tragaba TODO error de sync con `catch (_: Exception) {}` — ni el id de
     * la fila que falló ni el motivo quedaban en ningún lado, así que un evento/cuenta atascado
     * (id en conflicto, cuenta inexistente, lo que sea) era indiagnosticable desde afuera: el
     * dueño solo veía que "algo" no llegaba al server. `println` porque no hay ningún logger ya
     * elegido en `:core` (no hay dependencia tipo Napier/co.touchlab acá, y este módulo corre en
     * JVM/Android/iOS — `println` es lo único que las tres plataformas comparten sin agregar una
     * dependencia nueva). No cambia la política de reintento: la fila se queda sin sellar y el
     * próximo ciclo de 30s la vuelve a intentar, exactamente igual que antes de este log.
     */
    private fun logSyncFailure(step: String, error: Exception, id: String? = null) {
        val target = id?.let { " id=$it" } ?: ""
        println("[SyncEngine] $step falló$target: ${error.message}")
    }
}
