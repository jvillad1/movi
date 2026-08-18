package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.FailingCreateAccountRepository
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.shared.repository.NoOpRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private val testUserId = "user-sync-1"

    /**
     * Remoto de prueba que exige la validación real del server (ver `EventRoutes.kt` POST): un
     * evento cuyo `accountId` todavía no existe en `knownAccountIds` rebota con el mismo 404 que
     * daría el server real. Es lo que hace que la prueba de orden (`syncAccounts` antes de
     * `syncEvents`) sea real y no un caso feliz que pasaría igual sin importar el orden.
     */
    private class OrderSensitiveRemote : NoOpRepository() {
        private val knownAccountIds = mutableSetOf<String>()
        val pushedAccountIds = mutableListOf<String>()
        val pushedEventIds = mutableListOf<String>()

        override suspend fun createAccount(account: Account): Account {
            knownAccountIds += account.id
            pushedAccountIds += account.id
            return account
        }

        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (event.accountId !in knownAccountIds) throw ApiException(404, "Account not found")
            pushedEventIds += event.id
            return event
        }
    }

    /**
     * Camino feliz de [SyncEngine.syncAccounts]: la cuenta pendiente (creada offline, ver
     * [LocalRepository.createAccount]) se empuja al server y queda sellada (`syncedAt != null`)
     * — si quedara sin sellar, el próximo ciclo la volvería a empujar aunque el server ya la
     * tenga.
     */
    @Test
    fun syncAccounts_empuja_las_cuentas_pendientes_y_las_marca_sincronizadas() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        // Sin red al crearla: la fila queda local, syncedAt = null (ver LocalRepositoryTest).
        local.createAccount(Account("acc-pending", "Efectivo", AccountType.CASH, 10_000L))
        assertNull(db.accountQueries.selectById("acc-pending").executeAsOne().syncedAt)

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()

        assertTrue("acc-pending" in remote.pushedAccountIds)
        assertTrue(db.accountQueries.selectById("acc-pending").executeAsOne().syncedAt != null)
    }

    /**
     * Hallazgo 3 del brief (SyncEngine ya no se traga errores en silencio, pero la política de
     * reintento no cambia): si el push sigue fallando, la fila se queda SIN sellar — el próximo
     * ciclo de 30s la vuelve a intentar. No se pierde ni se marca como si hubiera llegado.
     */
    @Test
    fun syncAccounts_deja_la_fila_sin_sellar_si_el_push_sigue_fallando() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-sigue-offline", "Efectivo", AccountType.CASH, 0L))

        val engine = SyncEngine(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        engine.syncAccounts()

        assertNull(db.accountQueries.selectById("acc-sigue-offline").executeAsOne().syncedAt)
    }

    /**
     * Prueba end-to-end la razón de que [SyncEngine.start] llame a `syncAccounts()` ANTES que
     * `syncEvents()`: el evento de una cuenta creada offline rebota con 404 mientras el server
     * todavía no conoce la cuenta ([OrderSensitiveRemote.postEvent] imita exactamente esa
     * validación de `EventRoutes.kt`). Primero se demuestra el orden EQUIVOCADO (para no probar
     * un caso feliz que pasaría igual sin importar el orden) y después el correcto.
     */
    @Test
    fun syncAccounts_antes_que_syncEvents_permite_que_el_evento_de_una_cuenta_offline_llegue() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-nueva", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-nueva-cuenta", "acc-nueva", TransactionType.INCOME, 50_000L))

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })

        // Orden equivocado: sin la cuenta en el server, el evento rebota con 404 y syncEvents lo
        // traga (queda pendiente, no se pierde, pero tampoco llega).
        engine.syncEvents()
        assertTrue(remote.pushedEventIds.isEmpty())
        assertNull(db.financialEventQueries.selectById("ev-nueva-cuenta", testUserId).executeAsOne().syncedAt)

        // Orden correcto: syncAccounts primero, después syncEvents.
        engine.syncAccounts()
        engine.syncEvents()

        assertTrue("acc-nueva" in remote.pushedAccountIds)
        assertTrue("ev-nueva-cuenta" in remote.pushedEventIds)
        assertTrue(db.financialEventQueries.selectById("ev-nueva-cuenta", testUserId).executeAsOne().syncedAt != null)
    }

    private fun event(id: String, accountId: String, type: TransactionType, amount: Long) =
        FinancialEvent(
            id = id, accountId = accountId, type = type, amount = amount,
            category = "test", description = "test",
            timestamp = System.currentTimeMillis(),
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.UNCONFIRMED,
        )
}
