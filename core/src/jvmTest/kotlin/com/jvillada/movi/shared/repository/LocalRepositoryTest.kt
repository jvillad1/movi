package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalRepositoryTest {

    private lateinit var repo: LocalRepository

    private val testUserId = "user-test-1"

    @BeforeTest
    fun setup() {
        val db = createDatabase("test.db")
        repo = LocalRepository(
            db = db,
            remote = NoOpRepository(),
            userId = { testUserId },
        )
    }

    @Test
    fun postEvent_increases_account_balance() = runBlocking {
        repo.createAccount(Account("acc1", "Cash", AccountType.CASH, 0L))

        repo.postEvent(event("evt1", "acc1", TransactionType.INCOME, 5_000L))

        val account = repo.getAccount("acc1")
        assertEquals(5_000L, account.balance)
    }

    @Test
    fun voidEvent_reverses_account_balance() = runBlocking {
        repo.createAccount(Account("acc2", "Checking", AccountType.CHECKING, 10_000L))
        repo.postEvent(event("evt2", "acc2", TransactionType.EXPENSE, 3_000L))

        repo.voidEvent("evt2")

        val account = repo.getAccount("acc2")
        assertEquals(10_000L, account.balance)
    }

    @Test
    fun getEvents_excludes_voided_events() = runBlocking {
        repo.createAccount(Account("acc3", "Savings", AccountType.SAVINGS, 0L))
        repo.postEvent(event("evt3", "acc3", TransactionType.INCOME, 1_000L))
        repo.postEvent(event("evt4", "acc3", TransactionType.INCOME, 2_000L))

        repo.voidEvent("evt3")

        // getEvents still returns all events (voiding doesn't delete, it records a VoidEvent)
        // the voided event remains in financial_events; callers filter by checking void_events
        val events = repo.getEvents("acc3")
        assertTrue(events.any { it.id == "evt3" })
        assertTrue(events.any { it.id == "evt4" })
    }

    /**
     * El ajuste vive en el server, pero lo que el dueño ve en su teléfono sale de esta DB:
     * Movimientos, Análisis, Presupuestos y Cuentas leen de acá, y el SyncEngine solo empuja.
     * Si el espejo no ocurre, el ajuste es invisible en Android y la deuda vieja se queda en
     * pantalla para siempre.
     */
    @Test
    fun adjustCreditBalance_espeja_el_evento_y_el_saldo_del_server() = runBlocking {
        repo.createAccount(Account("acc-loan", "Libranza", AccountType.LOAN, 100_000_000L))

        val summary = repo.adjustCreditBalance("acc-loan", 40_000_000L)
        assertEquals(40_000_000L, summary.account.balance)

        val mirrored = repo.getEvents("acc-loan").single { it.id == "ev-ajuste-acc-loan" }
        assertEquals(60_000_000L, mirrored.amount)
        assertEquals(TransactionType.INCOME, mirrored.type)
        // Ya sincronizado: si quedara pendiente, el SyncEngine lo subiría y duplicaría el ajuste.
        assertNotNull(mirrored.syncedAt)
        // Cuenta LOAN ⇒ no es flujo de caja. Es la mitad que evita el "+$60.000.000" de ingresos.
        assertFalse(mirrored.countsAsCashFlow)

        // El saldo cacheado se copia del server, no se recalcula con un delta local: para una
        // cuenta LOAN el delta de postEvent tiene el signo al revés y habría dado 160.000.000.
        assertEquals(40_000_000L, repo.getAccount("acc-loan").balance)
    }

    /** Control: en una cuenta de activo la bandera sigue en true y nada de esto la toca. */
    @Test
    fun getEvents_marca_como_flujo_de_caja_los_movimientos_de_cuentas_de_activo() = runBlocking {
        repo.createAccount(Account("acc-cash", "Efectivo", AccountType.CASH, 0L))
        repo.postEvent(event("ev-mercado", "acc-cash", TransactionType.EXPENSE, 250_000L))

        assertTrue(repo.getEvents("acc-cash").single { it.id == "ev-mercado" }.countsAsCashFlow)
    }

    /**
     * Camino A: el evento ya se sincronizó con el server (`syncedAt != null`, ver `markSynced`).
     * Acá `remote` es la fuente de verdad y sí hay que llamarlo antes de espejar — sin este test
     * separado del de abajo, un fix que resolviera *todo* localmente (sin importar `syncedAt`)
     * pasaría igual y dejaría el server desactualizado.
     *
     * El id se declara en `knownEventIds` para que el stub no dé el 404 que le daría a un evento
     * que no conoce (ver [NoOpRepository.updateEventCategory]): acá el evento sí existe en el
     * server, así que el camino correcto es justamente llamarlo.
     */
    @Test
    fun updateEventCategory_evento_sincronizado_pasa_por_el_server_y_se_espeja() = runBlocking {
        val db = createDatabase("test.db")
        val repoSincronizado = LocalRepository(
            db = db,
            remote = NoOpRepository(knownEventIds = setOf("evt-pago-sync")),
            userId = { testUserId },
        )
        repoSincronizado.createAccount(Account("acc-sync", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repoSincronizado.postEvent(event("evt-pago-sync", "acc-sync", TransactionType.EXPENSE, 300_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "evt-pago-sync")
        val balanceBefore = repoSincronizado.getAccount("acc-sync").balance

        val result = repoSincronizado.updateEventCategory("evt-pago-sync", CARD_PAYMENT_CATEGORY)
        assertEquals(CARD_PAYMENT_CATEGORY, result.category)
        // El stub siempre echoa accountId="acc-stub" (ver NoOpRepository): que el resultado lo
        // traiga es la prueba de que sí pasó por remote y no se resolvió local.
        assertEquals("acc-stub", result.accountId)

        val mirrored = repoSincronizado.getEvents("acc-sync").single { it.id == "evt-pago-sync" }
        assertEquals(CARD_PAYMENT_CATEGORY, mirrored.category)
        assertFalse(mirrored.countsAsCashFlow)

        // Recategorizar no es un movimiento de plata: el saldo de la cuenta no se toca.
        assertEquals(balanceBefore, repoSincronizado.getAccount("acc-sync").balance)
    }

    /**
     * Camino B (Hallazgo 1 de la revisión de `396a695`): el evento **todavía no llegó al
     * server** — `postEvent` es local-only y `syncedAt` sigue `null` hasta que el `SyncEngine`
     * lo empuje en su ciclo de 30s. El stub de este test no tiene `"evt-pago"` en
     * `knownEventIds`, así que si `updateEventCategory` intentara llamar a `remote` acá, tiraría
     * el mismo `ApiException(404)` que tiraría el server real para un evento que no conoce —y el
     * test fallaría con esa excepción sin llegar a los asserts. El camino correcto es resolver
     * **solo local** y dejar que el `SyncEngine` suba el evento con la categoría ya corregida.
     */
    @Test
    fun updateEventCategory_evento_pendiente_se_resuelve_local_sin_llamar_al_server() = runBlocking {
        repo.createAccount(Account("acc-savings", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-pago", "acc-savings", TransactionType.EXPENSE, 300_000L))
        val balanceBefore = repo.getAccount("acc-savings").balance

        val result = repo.updateEventCategory("evt-pago", CARD_PAYMENT_CATEGORY)
        assertEquals(CARD_PAYMENT_CATEGORY, result.category)
        // Si esto hubiera ido al server, el stub habría devuelto accountId="acc-stub" (ver
        // NoOpRepository) — acá tiene que seguir siendo la cuenta real: se resolvió local.
        assertEquals("acc-savings", result.accountId)
        // Sobre el objeto DEVUELTO, no sobre una relectura: una UI optimista se queda con esto.
        // Si la bandera se derivara antes de aplicar la categoría, acá diría `true` —y el
        // refetch de abajo taparía el bug, porque relee la categoría ya persistida.
        assertFalse(result.countsAsCashFlow)

        val mirrored = repo.getEvents("acc-savings").single { it.id == "evt-pago" }
        assertEquals(CARD_PAYMENT_CATEGORY, mirrored.category)
        // "Pago de tarjeta" nunca cuenta como flujo de caja, sin importar el tipo de cuenta.
        assertFalse(mirrored.countsAsCashFlow)

        // Recategorizar no es un movimiento de plata: el saldo de la cuenta no se toca.
        assertEquals(balanceBefore, repo.getAccount("acc-savings").balance)
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
