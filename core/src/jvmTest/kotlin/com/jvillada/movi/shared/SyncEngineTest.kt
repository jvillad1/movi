package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.openingEventFor
import com.jvillada.movi.shared.model.signedDelta
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.FailingCreateAccountRepository
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.shared.repository.NoOpRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private val testUserId = "user-sync-1"

    /**
     * Remoto de prueba que exige la validación real del server (ver `EventRoutes.kt` POST): un
     * evento cuyo `accountId` todavía no existe en `knownAccountIds` rebota con el mismo 404 que
     * daría el server real. Es lo que hace que la prueba de orden (`syncAccounts` antes de
     * `syncEvents`) sea real y no un caso feliz que pasaría igual sin importar el orden.
     *
     * También imita cómo el server deriva el balance de una cuenta (`enrichWith`/
     * `computeBalances`, ver `Balances.kt`): [derivedBalance] suma `signedDelta` sobre los
     * eventos que este stub efectivamente recibió — nunca fabrica uno propio a partir de
     * `account.balance`, igual que `AccountRoutes.kt` POST desde la Ola 1b. Es lo que hace real
     * la prueba del hallazgo Critical de abajo: si `createAccount` volviera a fabricar una
     * apertura (el bug viejo), `pushedEvents` tendría una fila de más y `derivedBalance` daría el
     * doble.
     */
    private class OrderSensitiveRemote : NoOpRepository() {
        private val knownAccountIds = mutableSetOf<String>()
        private val accountTypes = mutableMapOf<String, AccountType>()
        val pushedAccountIds = mutableListOf<String>()
        val pushedEventIds = mutableListOf<String>()
        private val pushedEvents = mutableListOf<FinancialEvent>()

        override suspend fun createAccount(account: Account): Account {
            knownAccountIds += account.id
            accountTypes[account.id] = account.type
            pushedAccountIds += account.id
            return account
        }

        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (event.accountId !in knownAccountIds) throw ApiException(404, "Account not found")
            pushedEventIds += event.id
            pushedEvents += event
            return event
        }

        fun derivedBalance(accountId: String): Long {
            val type = accountTypes[accountId] ?: return 0L
            return pushedEvents.filter { it.accountId == accountId }
                .sumOf { signedDelta(type, it.type, it.amount) }
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

    /**
     * El escenario EXACTO del hallazgo Critical de la revisión de la Ola 1b: una cuenta creada
     * offline con el evento de apertura (mismo camino que `CreateAccountSheet.kt`: la cuenta
     * arranca en $0, el saldo inicial es un evento aparte) Y un ingreso real anotado ANTES de que
     * le toque ciclo de sync. `LocalRepository.postEvent` ya deja el saldo LOCAL en la suma de
     * los dos ($100.000): eso es correcto y no es lo que se prueba acá.
     *
     * Lo que se prueba es qué llega al server. Antes de este fix, `SyncEngine.syncAccounts`
     * mandaba `row.balance` (ya en $100.000 por los dos eventos locales) y `AccountRoutes.kt`
     * POST lo convertía en una TERCERA apertura fabricada; `syncEvents` empujaba después los dos
     * eventos reales encima — el server terminaba con 3 eventos y un balance derivado de
     * $200.000, el doble del real. Desde la Ola 1b el server no fabrica nada: acá se verifica que
     * el remoto termina con la cuenta y EXACTAMENTE los dos eventos que el cliente creó, y que el
     * balance derivado (`OrderSensitiveRemote.derivedBalance`, calculado igual que
     * `enrichWith`/`computeBalances` del lado del server) da el saldo real, no el doble.
     */
    @Test
    fun cuenta_offline_con_apertura_y_evento_real_antes_del_sync_no_duplica_el_balance_en_el_servidor() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })

        // 1. La cuenta se crea en $0 (mismo camino que CreateAccountSheet.kt).
        local.createAccount(Account("acc-critico", "Ahorros", AccountType.SAVINGS, 0L))
        // 2. El saldo inicial declarado ($50.000) se anota como su propio evento — la apertura
        //    que hoy crea el cliente, no el server.
        val opening = openingEventFor(
            account = Account("acc-critico", "Ahorros", AccountType.SAVINGS, balance = 50_000L),
            now = 1_700_000_000_000L,
            id = "ev-apertura",
        )!!
        local.postEvent(opening)
        // 3. Un ingreso real, anotado (p. ej. desde QuickAdd) ANTES de que corra el próximo ciclo
        //    de sync de 30s — la ventana exacta del hallazgo Critical.
        local.postEvent(event("ev-ingreso-real", "acc-critico", TransactionType.INCOME, 50_000L))
        assertEquals(
            100_000L,
            db.accountQueries.selectById("acc-critico").executeAsOne().balance,
            "localmente el saldo YA es la suma de sus dos eventos propios — nada que sincronizar duplica esto",
        )

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()

        assertEquals(
            setOf("ev-apertura", "ev-ingreso-real"), remote.pushedEventIds.toSet(),
            "exactamente los dos eventos que el cliente creó — ninguno fabricado por el server",
        )
        assertEquals(2, remote.pushedEventIds.size)
        assertEquals(
            100_000L,
            remote.derivedBalance("acc-critico"),
            "el balance derivado del lado del servidor tiene que ser el saldo real (50.000 + 50.000), " +
                "no 200.000 — el resultado si el servidor hubiera fabricado una tercera apertura a partir " +
                "del balance ya sincronizado de la cuenta",
        )
    }

    /**
     * El `SyncEngine` empuja eventos **de a uno** (`postEvent`), así que si una pata de traspaso
     * llegara a quedar pendiente, este ciclo podría subir media transferencia: plata saliendo de
     * una cuenta sin la pata que la compensa del otro lado, y encima con `transferId` apuntando
     * a una hermana que el server nunca vio.
     *
     * Por diseño eso no debería poder pasar —[com.jvillada.movi.shared.repository.LocalRepository.createTransfer]
     * es remote-first y espeja las dos patas ya selladas—, pero "no debería poder pasar" no es
     * una garantía: acá se fuerza el caso escribiendo una pata pendiente a mano en la DB local
     * (lo que dejaría una versión vieja de la app, o una fila a medio escribir) y se verifica que
     * el ciclo la deja quieta en vez de subirla sola. Los eventos normales de al lado sí suben:
     * la guarda es para las patas, no un freno general.
     */
    @Test
    fun syncEvents_nunca_empuja_una_pata_de_traspaso_sola() = runBlocking {
        val db = createDatabase("sync-test.db")
        val remote = OrderSensitiveRemote()
        remote.createAccount(Account("acc-tr", "Ahorros", AccountType.SAVINGS, 0L))
        db.accountQueries.insert("acc-tr", "Ahorros", "SAVINGS", 0L, "COP", testUserId, 1L, null)

        // Una pata suelta, pendiente de sync (el escenario que no debería existir).
        db.financialEventQueries.insert(
            "ev-pata-suelta", "acc-tr", "EXPENSE", 100_000L, "Traspaso", "Traspaso a CDT", null,
            1_700_000_000_000L, "MANUAL", null, "RECONCILED", null, testUserId, "tr-huerfano",
            1_700_000_000_000L,
        )
        // Y un evento normal al lado, para que el test distinga "no empuja la pata" de
        // "no empuja nada".
        db.financialEventQueries.insert(
            "ev-normal", "acc-tr", "EXPENSE", 5_000L, "Mercado", "pan", null,
            1_700_000_000_000L, "MANUAL", null, "RECONCILED", null, testUserId, null,
            1_700_000_000_000L,
        )

        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncEvents()

        assertEquals(listOf("ev-normal"), remote.pushedEventIds)
        assertNull(
            db.financialEventQueries.selectById("ev-pata-suelta", testUserId).executeAsOne().syncedAt,
            "la pata queda sin sellar: pendiente y diagnosticable, no subida a medias",
        )
    }

    /**
     * **Corregir el monto mientras el POST está en vuelo no puede dejar la fila sellada con la
     * cifra vieja.**
     *
     * Es la misma carrera que `markSyncedIfUnchanged` ya cerraba para la categoría y la fecha,
     * abierta de nuevo por el monto, la cuenta y el concepto desde que se pueden corregir (ver
     * `LocalRepository.updateEvent`). El daño era **silencioso y permanente**: la fila quedaba
     * sellada, `selectUnsynced` dejaba de traerla, y el server se quedaba con $50.000 mientras el
     * teléfono mostraba $20.000 para siempre.
     *
     * El stub corrige la fila **desde adentro de `postEvent`**, que es exactamente el instante en
     * que la petición está en vuelo y nadie tiene la fila lockeada.
     */
    @Test
    fun syncEvents_no_sella_una_fila_cuyo_monto_cambio_mientras_el_push_estaba_en_vuelo() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = NoOpRepository(), userId = { testUserId })
        local.createAccount(Account("acc-carrera", "Efectivo", AccountType.CASH, 100_000L))
        local.postEvent(event("ev-carrera", "acc-carrera", TransactionType.EXPENSE, 50_000L))

        val remote = object : NoOpRepository() {
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                // El dueño corrige el monto justo mientras esto viaja.
                db.financialEventQueries.updateMovimiento(
                    20_000L, event.accountId, event.description, event.id, testUserId,
                )
                return event
            }
        }
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncEvents()

        val fila = db.financialEventQueries.selectById("ev-carrera", testUserId).executeAsOne()
        assertEquals(20_000L, fila.amount, "la corrección local no se pierde")
        assertNull(
            fila.syncedAt,
            "sin sellar: el próximo ciclo la vuelve a empujar con el monto corregido",
        )
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
