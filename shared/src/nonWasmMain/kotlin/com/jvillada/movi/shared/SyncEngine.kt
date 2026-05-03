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
                db.financialEventQueries.markSynced(
                    Clock.System.now().toEpochMilliseconds(), row.id
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
