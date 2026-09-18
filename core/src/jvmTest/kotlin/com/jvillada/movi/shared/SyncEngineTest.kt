package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EdicionDeMovimiento
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
import kotlin.test.assertNotNull
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
        val pushedEvents = mutableListOf<FinancialEvent>()

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
    /**
     * **La moneda viaja.** El teléfono no la guardaba y el POST salía con el default "COP": un gasto
     * de US$120 anotado sin señal sobre la Master Black USD llegaba al server como $120 pesos.
     */
    @Test
    fun syncEvents_sube_la_moneda_del_movimiento() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-usd", "Master USD", AccountType.CREDIT_CARD, 0L, "USD"))
        local.postEvent(event("ev-usd", "acc-usd", TransactionType.EXPENSE, 120L).copy(currency = "USD"))
        assertEquals("USD", local.getEvents("acc-usd").single { it.id == "ev-usd" }.currency, "el teléfono la guarda")

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()

        assertEquals("USD", remote.pushedEvents.single { it.id == "ev-usd" }.currency, "y la sube")
    }

    /**
     * **La edad de la versión viaja, y dice la verdad en los dos casos.**
     *
     * Es la mitad cliente de la carrera que `pisaElReenvio` (server, `EventRoutes.kt`) resuelve: el
     * `POST /api/events` puede haber LLEGADO sin que el teléfono viera la respuesta, así que la
     * fila local se queda sin sellar y el ciclo de 30 s la reenvía. Si el dueño corrigió ese
     * movimiento en la web mientras tanto, el reenvío pisaba su corrección sin decir nada.
     *
     * El server decide con lo que llegue acá, así que las dos direcciones importan:
     * - **sin editar → `null`**, que es lo que le dice al server «esta copia es la original, si vos
     *   tenés una edición guardada es posterior a la mía y gana»;
     * - **editado sin señal → un instante**, que es lo que le deja ganarle a la copia más vieja del
     *   server. Sin esto, arreglar el primer caso rompería el segundo.
     */
    @Test
    fun syncEvents_sube_la_edad_de_la_version_de_cada_movimiento() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-edicion", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-sin-editar", "acc-edicion", TransactionType.EXPENSE, 50_000L))
        local.postEvent(event("ev-editado", "acc-edicion", TransactionType.EXPENSE, 50_000L))
        local.updateEvent("ev-editado", EdicionDeMovimiento(amount = 9_000L))

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()

        assertNotNull(
            remote.pushedEvents.single { it.id == "ev-editado" }.lastEditedAt,
            "corregir sin señal es el caso normal del teléfono, y esa corrección tiene que poder ganar",
        )
        assertNull(
            remote.pushedEvents.single { it.id == "ev-sin-editar" }.lastEditedAt,
            "anotarlo no es editarlo: null es lo que le hace perder contra la corrección de la web",
        )
    }

    /**
     * **Toda corrección local sella, venga por la puerta que venga.** Es la regla frágil de este
     * arreglo: una corrección que no selle sale diciendo «yo no edité nada» y el server se la hace
     * perder contra la copia de la web — el mismo bug al revés, esta vez perdiendo lo que el dueño
     * escribió en el teléfono. Por eso se prueban las cuatro puertas y no solo la del monto.
     */
    @Test
    fun syncEvents_sella_la_edicion_venga_por_la_puerta_que_venga() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-puertas", "Efectivo", AccountType.CASH, 0L))
        listOf("ev-cat", "ev-fecha", "ev-repite", "ev-confirma").forEach {
            local.postEvent(event(it, "acc-puertas", TransactionType.EXPENSE, 50_000L))
        }
        local.updateEventCategory("ev-cat", "Restaurantes")
        local.updateEventTimestamp("ev-fecha", System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000)
        local.updateEventRepeats("ev-repite", false)
        local.confirmEvent("ev-confirma")

        val remote = OrderSensitiveRemote()
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()

        listOf("ev-cat", "ev-fecha", "ev-repite", "ev-confirma").forEach { id ->
            assertNotNull(remote.pushedEvents.single { it.id == id }.lastEditedAt, "$id salió sin sellar")
        }
    }

    /** Un 422 del server se reintentaba cada 30 s para siempre sin avisar. Ahora queda el motivo. */
    @Test
    fun syncEvents_guarda_el_motivo_cuando_el_server_rechaza_el_movimiento() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-rechazo", "Efectivo", AccountType.CASH, 0L))
        val remote = object : NoOpRepository() {
            var fallo = true
            override suspend fun createAccount(account: Account): Account = account
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent =
                if (fallo) throw ApiException(422, "Esa categoría no se puede anotar.") else event
        }
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        local.postEvent(event("ev-rechazado", "acc-rechazo", TransactionType.EXPENSE, 9_000L))

        engine.syncEvents()
        val rechazado = local.getMovimientosRechazados().single()
        assertEquals("ev-rechazado", rechazado.evento.id)
        assertEquals("Esa categoría no se puede anotar.", rechazado.motivo)

        // Cuando por fin sube, deja de mostrarse.
        remote.fallo = false
        engine.syncEvents()
        assertTrue(local.getMovimientosRechazados().isEmpty())
    }

    /** Sin red no es un rechazo, y el 404 de una cuenta que todavía no subió tampoco. */
    @Test
    fun syncEvents_no_marca_rechazo_por_red_ni_por_cuenta_sin_subir() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-offline", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-offline", "acc-offline", TransactionType.EXPENSE, 9_000L))

        SyncEngine(db = db, remote = OrderSensitiveRemote(), userId = { testUserId }).syncEvents()
        SyncEngine(db = db, remote = object : NoOpRepository() {
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = throw IllegalStateException("sin red")
        }, userId = { testUserId }).syncEvents()

        assertTrue(local.getMovimientosRechazados().isEmpty())
    }

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
            1_700_000_000_000L, null, 0L, "COP", null,
        )
        // Y un evento normal al lado, para que el test distinga "no empuja la pata" de
        // "no empuja nada".
        db.financialEventQueries.insert(
            "ev-normal", "acc-tr", "EXPENSE", 5_000L, "Mercado", "pan", null,
            1_700_000_000_000L, "MANUAL", null, "RECONCILED", null, testUserId, null,
            1_700_000_000_000L, null, 0L, "COP", null,
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

    /** «Este no se repite» marcado sin señal tiene que llegar marcado al server. */
    @Test
    fun syncEvents_sube_la_marca_de_no_se_repite() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = NoOpRepository(), userId = { testUserId })
        local.createAccount(Account("acc-nsr", "Efectivo", AccountType.CASH, 100_000L))
        local.postEvent(event("ev-nsr", "acc-nsr", TransactionType.EXPENSE, 20_000L).copy(noSeRepite = true))

        var subido: FinancialEvent? = null
        val remote = object : NoOpRepository() {
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent { subido = event; return event }
        }
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncEvents()

        assertEquals(true, subido?.noSeRepite)
        assertNotNull(db.financialEventQueries.selectById("ev-nsr", testUserId).executeAsOne().syncedAt)
        Unit
    }

    /** Y marcarlo mientras el POST viaja no sella la fila con la marca vieja. */
    @Test
    fun syncEvents_no_sella_si_la_marca_de_no_se_repite_cambio_en_vuelo() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = NoOpRepository(), userId = { testUserId })
        local.createAccount(Account("acc-nsr2", "Efectivo", AccountType.CASH, 100_000L))
        local.postEvent(event("ev-nsr2", "acc-nsr2", TransactionType.EXPENSE, 20_000L))

        val remote = object : NoOpRepository() {
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                db.financialEventQueries.updateNoSeRepite(1L, event.id, testUserId)
                return event
            }
        }
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncEvents()

        assertNull(db.financialEventQueries.selectById("ev-nsr2", testUserId).executeAsOne().syncedAt)
    }

    /**
     * Remoto que imita `POST /api/events/{id}/void` del server: anula lo que conoce y contesta
     * 404 a lo que nunca le llegó.
     */
    private class VoidAwareRemote(private val conocidos: MutableSet<String> = mutableSetOf()) : NoOpRepository() {
        var rechazarEventos = false
        val eventosSubidos = mutableListOf<String>()
        val anulados = mutableListOf<String>()
        override suspend fun createAccount(account: Account): Account = account
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            eventosSubidos += event.id
            if (rechazarEventos) throw ApiException(422, "Esa categoría no se puede anotar.")
            conocidos += event.id
            return event
        }
        override suspend fun voidEvent(id: String, reason: String?): com.jvillada.movi.shared.model.VoidEvent {
            if (id !in conocidos) throw ApiException(404, "Event not found")
            anulados += id
            return com.jvillada.movi.shared.model.VoidEvent(id = "v_$id", originalEventId = id, reason = reason, timestamp = 0L)
        }
    }

    /**
     * **Anular un movimiento que el server rechazó lo saca del aviso y del ciclo.** Antes el ciclo
     * lo reenviaba cada 30 s, la anulación rebotaba contra un 404 eterno y el aviso «corrígelo o
     * anúlalo» no se iba nunca, aunque el dueño hubiera hecho justo lo que decía.
     */
    @Test
    fun anular_un_movimiento_rechazado_lo_saca_del_aviso_y_deja_de_reintentarlo() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-anula", "Efectivo", AccountType.CASH, 0L))
        val remote = VoidAwareRemote().apply { rechazarEventos = true }
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        local.postEvent(event("ev-rechazado", "acc-anula", TransactionType.EXPENSE, 9_000L))
        engine.syncEvents()
        assertEquals(1, local.getMovimientosRechazados().size, "el aviso aparece")

        local.voidEvent("ev-rechazado", null)
        assertTrue(local.getMovimientosRechazados().isEmpty(), "anulado, el aviso se va")

        remote.eventosSubidos.clear()
        engine.syncEvents()
        engine.syncVoids()
        assertTrue(remote.eventosSubidos.isEmpty(), "no se vuelve a subir un movimiento anulado")
        assertTrue(db.voidEventQueries.selectUnsynced().executeAsList().isEmpty(), "el 404 de algo que nunca subió se sella")

        // Y el ciclo siguiente no insiste con nada.
        engine.syncEvents()
        engine.syncVoids()
        assertTrue(remote.eventosSubidos.isEmpty())
        assertTrue(remote.anulados.isEmpty())
    }

    /**
     * El caso que obliga a empujar la anulación en vez de sellarla de antemano: el POST llegó al
     * server pero la respuesta se perdió, así que acá sigue sin sellar. La anulación tiene que
     * llegar allá, o el server se queda con un movimiento que el dueño anuló.
     */
    @Test
    fun anular_un_movimiento_que_llego_al_server_sin_respuesta_lo_anula_alla() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-perdido", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-perdido", "acc-perdido", TransactionType.EXPENSE, 9_000L))
        assertNull(db.financialEventQueries.selectById("ev-perdido", testUserId).executeAsOne().syncedAt)
        // El server ya lo tiene, aunque el teléfono nunca se enteró.
        val remote = VoidAwareRemote(conocidos = mutableSetOf("ev-perdido"))

        local.voidEvent("ev-perdido", null)
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()
        engine.syncVoids()

        assertTrue(remote.eventosSubidos.isEmpty())
        assertEquals(listOf("ev-perdido"), remote.anulados, "la anulación llega al server")
        assertTrue(db.voidEventQueries.selectUnsynced().executeAsList().isEmpty())
    }

    /** Un 404 sobre un movimiento que SÍ subió no es «nunca llegó»: no se sella y se reintenta. */
    @Test
    fun un_404_sobre_un_movimiento_que_si_subio_no_se_sella() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-subido", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-subido", "acc-subido", TransactionType.EXPENSE, 9_000L))
        val engine = SyncEngine(db = db, remote = VoidAwareRemote(), userId = { testUserId })
        engine.syncAccounts()
        engine.syncEvents()
        assertNotNull(db.financialEventQueries.selectById("ev-subido", testUserId).executeAsOne().syncedAt)

        local.voidEvent("ev-subido", null)
        // Otro server que no lo conoce: el 404 no se puede tomar como «no hay nada que anular».
        SyncEngine(db = db, remote = VoidAwareRemote(), userId = { testUserId }).syncVoids()

        assertEquals(1, db.voidEventQueries.selectUnsynced().executeAsList().size)
    }

    // ── El 5xx que no afloja ──────────────────────────────────────────────────

    /**
     * Server que se rompe las primeras [fallos] veces y después recibe bien. Cuenta los intentos
     * para que las pruebas puedan afirmar que **se siguió reintentando**, que es la mitad de este
     * arreglo que sería fácil romper sin que ninguna otra prueba se quejara.
     */
    private class ServidorRoto(var fallos: Int, val status: Int = 500) : NoOpRepository() {
        var intentos = 0
        val subidos = mutableListOf<String>()

        override suspend fun createAccount(account: Account): Account = account

        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            intentos++
            if (fallos > 0) {
                fallos--
                throw ApiException(status, "Internal Server Error")
            }
            subidos += event.id
            return event
        }
    }

    /**
     * **Un tropezón del server no se anuncia.** Dos 5xx seguidos y al tercer ciclo entra: eso es un
     * despliegue o un reinicio, y avisar ahí sería enseñarle al dueño a ignorar el aviso. El umbral
     * ([SyncEngine.INTENTOS_ANTES_DE_AVISAR]) existe justamente para no gastar el aviso en esto.
     */
    @Test
    fun syncEvents_un_5xx_que_se_recupera_nunca_llega_a_avisar() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-5xx-ok", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-5xx-ok", "acc-5xx-ok", TransactionType.EXPENSE, 9_000L))
        val remote = ServidorRoto(fallos = 2)
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()

        repeat(3) {
            engine.syncEvents()
            assertTrue(local.getMovimientosRechazados().isEmpty(), "no se avisa por un tropezón")
        }

        assertEquals(3, remote.intentos, "los dos fallos no cortaron los reintentos")
        assertNotNull(db.financialEventQueries.selectById("ev-5xx-ok", testUserId).executeAsOne().syncedAt)
        assertEquals(listOf("ev-5xx-ok"), remote.subidos)
    }

    /**
     * **El 5xx que no se arregla sí se dice, y se sigue intentando igual.**
     *
     * Era el agujero: `syncError` solo se marcaba para un 4xx, así que un movimiento que el server
     * rechazaba con 500 —la forma que tenía un concepto demasiado largo— se quedaba reintentando
     * cada 30 segundos para siempre y en Movimientos no aparecía nada. Ahora, pasados
     * [SyncEngine.INTENTOS_ANTES_DE_AVISAR] fallos seguidos (unos cinco minutos), el aviso lo dice
     * — sin dejar de empujar, que es lo que le permite subir solo cuando el server vuelve.
     */
    @Test
    fun syncEvents_un_5xx_que_persiste_avisa_y_no_deja_de_reintentar() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-5xx", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-5xx", "acc-5xx", TransactionType.EXPENSE, 9_000L))
        val remote = ServidorRoto(fallos = Int.MAX_VALUE, status = 503)
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()

        val antesDelUmbral = (SyncEngine.INTENTOS_ANTES_DE_AVISAR - 1L).toInt()
        repeat(antesDelUmbral) { engine.syncEvents() }
        assertTrue(local.getMovimientosRechazados().isEmpty(), "todavía puede ser pasajero")

        engine.syncEvents()
        val rechazado = local.getMovimientosRechazados().single()
        assertEquals("ev-5xx", rechazado.evento.id)
        assertEquals(SyncEngine.elServidorNoLoRecibe(503), rechazado.motivo)
        assertTrue("sigue intentando" in rechazado.motivo, rechazado.motivo)

        // Y sigue empujando: el aviso no es una rendición.
        engine.syncEvents()
        assertEquals(SyncEngine.INTENTOS_ANTES_DE_AVISAR + 1L, remote.intentos.toLong())
        assertNull(db.financialEventQueries.selectById("ev-5xx", testUserId).executeAsOne().syncedAt)
    }

    /** Cuando el server vuelve, el movimiento sube y el aviso se va con la cuenta de fallos. */
    @Test
    fun syncEvents_un_exito_borra_la_cuenta_de_fallos_y_el_aviso() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-vuelve", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-vuelve", "acc-vuelve", TransactionType.EXPENSE, 9_000L))
        val remote = ServidorRoto(fallos = SyncEngine.INTENTOS_ANTES_DE_AVISAR.toInt())
        val engine = SyncEngine(db = db, remote = remote, userId = { testUserId })
        engine.syncAccounts()

        repeat(SyncEngine.INTENTOS_ANTES_DE_AVISAR.toInt()) { engine.syncEvents() }
        assertEquals(1, local.getMovimientosRechazados().size, "llegó al umbral y avisó")
        assertEquals(
            SyncEngine.INTENTOS_ANTES_DE_AVISAR,
            db.financialEventQueries.selectById("ev-vuelve", testUserId).executeAsOne().intentosFallidos,
        )

        engine.syncEvents()

        assertEquals(listOf("ev-vuelve"), remote.subidos)
        val fila = db.financialEventQueries.selectById("ev-vuelve", testUserId).executeAsOne()
        assertNotNull(fila.syncedAt)
        assertNull(fila.intentosFallidos, "la cuenta arranca de cero la próxima vez")
        assertNull(fila.syncError)
        assertTrue(local.getMovimientosRechazados().isEmpty())
    }

    /** Sin red no hay server que se niegue: eso no cuenta para el aviso. */
    @Test
    fun syncEvents_la_falta_de_red_no_cuenta_como_un_servidor_que_se_niega() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-sin-red", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-sin-red", "acc-sin-red", TransactionType.EXPENSE, 9_000L))
        val sinRed = object : NoOpRepository() {
            override suspend fun createAccount(account: Account): Account = account
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = throw IllegalStateException("sin red")
        }
        val engine = SyncEngine(db = db, remote = sinRed, userId = { testUserId })
        engine.syncAccounts()

        repeat(SyncEngine.INTENTOS_ANTES_DE_AVISAR.toInt() + 2) { engine.syncEvents() }

        assertTrue(local.getMovimientosRechazados().isEmpty(), "el teléfono sin señal no es culpa del server")
        assertNull(db.financialEventQueries.selectById("ev-sin-red", testUserId).executeAsOne().intentosFallidos)
    }

    /**
     * **«La cuenta todavía no subió» y «la cuenta no está en este teléfono» no son lo mismo.**
     *
     * El descarte de [SyncEngine.syncEvents] estaba escrito como
     * `selectById(...)?.syncedAt == null`, y con el operador seguro una fila AUSENTE daba
     * `null == null` = «no subió». O sea: cualquier rechazo real del server (un 422, un 400) sobre
     * un movimiento cuya cuenta no está espejada acá se tragaba sin escribir el `syncError`, y el
     * aviso de Movimientos —el único lugar donde el dueño se entera de que algo no llegó— no se
     * encendía nunca para esos.
     *
     * La diferencia importa porque los dos casos tienen arreglos opuestos: una cuenta pendiente la
     * empuja `syncAccounts` en el ciclo siguiente y el 404 se cura solo, pero una cuenta que no
     * está no la va a empujar nadie — callarse el motivo no arregla nada, solo lo esconde.
     */
    @Test
    fun syncEvents_avisa_del_rechazo_aunque_la_cuenta_no_este_espejada_en_este_telefono() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-fantasma", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-sin-cuenta", "acc-fantasma", TransactionType.EXPENSE, 9_000L))
        // La cuenta deja de estar en el espejo local (borrada desde la web, o nunca bajada).
        db.accountQueries.deleteById("acc-fantasma")
        assertNull(db.accountQueries.selectById("acc-fantasma").executeAsOneOrNull())

        val remote = object : NoOpRepository() {
            override suspend fun createAccount(account: Account): Account = account
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent =
                throw ApiException(422, "Esa categoría no se puede anotar.")
        }
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncEvents()

        val rechazado = local.getMovimientosRechazados().single()
        assertEquals("ev-sin-cuenta", rechazado.evento.id)
        assertEquals("Esa categoría no se puede anotar.", rechazado.motivo)
    }

    /**
     * La otra mitad, que es la que el descarte existe para proteger: si la cuenta SÍ está acá y
     * todavía no subió, el 404 del server («Account not found») se arregla solo en el ciclo
     * siguiente y no merece un aviso. Va junto con el de arriba a propósito: sin este, el arreglo
     * podría ser «avisar siempre», que reencendería el aviso de un caso que se cura solo.
     */
    @Test
    fun syncEvents_sigue_sin_avisar_cuando_la_cuenta_esta_aca_pero_no_subio() = runBlocking {
        val db = createDatabase("sync-test.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-pendiente", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(event("ev-espera", "acc-pendiente", TransactionType.EXPENSE, 9_000L))
        assertNull(
            db.accountQueries.selectById("acc-pendiente").executeAsOne().syncedAt,
            "la cuenta está espejada pero sin sellar",
        )

        // OrderSensitiveRemote rechaza con el mismo 404 que da el server real mientras no conoce
        // la cuenta.
        SyncEngine(db = db, remote = OrderSensitiveRemote(), userId = { testUserId }).syncEvents()

        assertTrue(
            local.getMovimientosRechazados().isEmpty(),
            "ese 404 se cura solo cuando syncAccounts empuje la cuenta",
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
