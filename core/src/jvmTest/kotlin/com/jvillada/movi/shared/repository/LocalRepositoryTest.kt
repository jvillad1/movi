package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.openingEventFor
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalRepositoryTest {

    private lateinit var repo: LocalRepository

    /**
     * La misma DB que usa [repo]. `createDatabase` levanta una SQLite en memoria NUEVA en cada
     * llamada, así que un test que quiera ejercitar otro remoto (p. ej. "sin red") tiene que
     * construir su LocalRepository sobre ESTA instancia — si no, escribiría en una base aparte y
     * la aserción sobre `repo` no probaría nada.
     */
    private lateinit var db: com.jvillada.movi.shared.db.MoviDatabase

    private val testUserId = "user-test-1"

    @BeforeTest
    fun setup() {
        db = createDatabase("test.db")
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

    /**
     * Hallazgo bloqueante 2 de la revisión de `feat/ajustar-saldo`: `postEvent` aplicaba el
     * delta local con la convención de cuenta de activo (INCOME suma) sin mirar el tipo de
     * cuenta. Escenario real: libranza ajustada a $40.000.000 vía [adjustCreditBalance] (ver
     * el test de arriba), después un abono de $1.000.000 registrado desde QuickAdd. El abono
     * es un INCOME — baja la deuda — pero antes de este fix el cálculo local le sumaba el
     * monto, dejando la deuda en $41.000.000 en el teléfono mientras el server (que sí deriva
     * con el signo correcto) decía $39.000.000.
     */
    @Test
    fun postEvent_en_cuenta_LOAN_un_abono_INCOME_baja_la_deuda_no_la_sube() = runBlocking {
        repo.createAccount(Account("acc-loan-abono", "Libranza", AccountType.LOAN, 40_000_000L))

        repo.postEvent(event("ev-abono", "acc-loan-abono", TransactionType.INCOME, 1_000_000L))

        assertEquals(39_000_000L, repo.getAccount("acc-loan-abono").balance)
    }

    /** Mismo hallazgo, la otra cara: un desembolso (EXPENSE) en LOAN sube la deuda. */
    @Test
    fun postEvent_en_cuenta_LOAN_un_desembolso_EXPENSE_sube_la_deuda() = runBlocking {
        repo.createAccount(Account("acc-loan-desem", "Libranza", AccountType.LOAN, 40_000_000L))

        repo.postEvent(event("ev-desembolso", "acc-loan-desem", TransactionType.EXPENSE, 2_000_000L))

        assertEquals(42_000_000L, repo.getAccount("acc-loan-desem").balance)
    }

    /**
     * `voidEvent` tiene el mismo problema en espejo: revertía con la convención de activo. Si
     * el abono de arriba se anula, la deuda tiene que volver exactamente a como estaba antes
     * — no a otra cifra por aplicar el signo equivocado en la reversión.
     */
    @Test
    fun voidEvent_en_cuenta_LOAN_revierte_con_la_convencion_de_deuda() = runBlocking {
        repo.createAccount(Account("acc-loan-void", "Libranza", AccountType.LOAN, 40_000_000L))
        repo.postEvent(event("ev-abono-void", "acc-loan-void", TransactionType.INCOME, 1_000_000L))
        assertEquals(39_000_000L, repo.getAccount("acc-loan-void").balance)

        repo.voidEvent("ev-abono-void")

        assertEquals(40_000_000L, repo.getAccount("acc-loan-void").balance)
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

    /**
     * "No es" no toca ninguna fila local (a diferencia de updateEventCategory/adjustCreditBalance
     * arriba): el candidato siempre se lee del server, así que acá alcanza con probar que
     * LocalRepository delega — no hay ningún espejo que verificar.
     */
    @Test
    fun dismissCardPaymentCandidate_delega_al_remoto() = runBlocking {
        val remote = NoOpRepository()
        val repoConRemote = LocalRepository(db = createDatabase("test.db"), remote = remote, userId = { testUserId })

        repoConRemote.dismissCardPaymentCandidate("evt-descartado")

        assertTrue("evt-descartado" in remote.dismissedCandidateIds)
    }

    /**
     * Red de seguridad de [LocalRepository.postEvent] (Hallazgo Crítico de la revisión de la Ola
     * 1: `id = ""` en la UI + `INSERT OR REPLACE` por PK `id` = el segundo evento reemplaza al
     * primero en el teléfono). La UI ya manda `newId("ev")` en los tres call sites, pero acá se
     * prueba la red de seguridad de `postEvent` en sí: nunca insertar con PK en blanco. Sin este
     * fix, el segundo `postEvent("", ...)` de abajo pisaría al primero y `getEvents` devolvería
     * uno solo con saldo de 2.000, no dos con saldo de 3.000.
     */
    @Test
    fun postEvent_con_id_en_blanco_genera_uno_en_vez_de_pisar_el_evento_anterior() = runBlocking {
        repo.createAccount(Account("acc-idgen", "Efectivo", AccountType.CASH, 0L))

        val primero = repo.postEvent(event("", "acc-idgen", TransactionType.INCOME, 1_000L))
        val segundo = repo.postEvent(event("", "acc-idgen", TransactionType.INCOME, 2_000L))

        assertTrue(primero.id.isNotBlank())
        assertTrue(segundo.id.isNotBlank())
        assertTrue(primero.id != segundo.id)
        assertEquals(2, repo.getEvents("acc-idgen").size)
        assertEquals(3_000L, repo.getAccount("acc-idgen").balance)
    }

    /**
     * Camino feliz de [LocalRepository.createAccount] (Hallazgo Crítico, Ola 1b): antes esto
     * escribía SOLO local — el `SyncEngine` no sincronizaba cuentas, así que una cuenta creada en
     * el teléfono nunca llegaba al server. Ahora llama a `remote.createAccount` primero (mismo
     * patrón que `adjustCreditBalance`/`updateEventCategory` arriba) y espeja lo que devolvió, ya
     * marcada sincronizada — si quedara `syncedAt = null`, `SyncEngine.syncAccounts` la volvería
     * a empujar aunque el server ya la tenga.
     */
    @Test
    fun createAccount_llama_al_server_primero_y_la_espeja_ya_sincronizada() = runBlocking {
        val db = createDatabase("test.db")
        val repoConRemote = LocalRepository(db = db, remote = NoOpRepository(), userId = { testUserId })

        val created = repoConRemote.createAccount(Account("acc-remote", "Ahorros", AccountType.SAVINGS, 500_000L))

        assertEquals("acc-remote", created.id)
        val row = db.accountQueries.selectById("acc-remote").executeAsOne()
        assertEquals(500_000L, row.balance)
        assertTrue(row.syncedAt != null)
    }

    /**
     * Camino sin red de [LocalRepository.createAccount]: `remote.createAccount` falla y la cuenta
     * se escribe igual, local, PENDIENTE (`syncedAt = null`) — no se pierde. Es la decisión que
     * pide el brief de la Ola 1b: la alternativa (no escribir nada localmente) le hace desaparecer
     * al dueño la cuenta que acaba de crear con sus propios dedos. `SyncEngine.syncAccounts` la
     * recoge en su próximo ciclo (ver SyncEngineTest).
     */
    @Test
    fun createAccount_sin_red_escribe_local_pendiente_de_sync() = runBlocking {
        val db = createDatabase("test.db")
        val repoSinRed = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })

        val created = repoSinRed.createAccount(Account("acc-offline", "Efectivo", AccountType.CASH, 0L))

        assertEquals("acc-offline", created.id)
        val row = db.accountQueries.selectById("acc-offline").executeAsOne()
        assertEquals(0L, row.balance)
        assertNull(row.syncedAt)
    }

    /** Red de seguridad de createAccount, mismo criterio que postEvent arriba. */
    @Test
    fun createAccount_con_id_en_blanco_genera_uno() = runBlocking {
        val created = repo.createAccount(Account("", "Efectivo", AccountType.CASH, 0L))

        assertTrue(created.id.isNotBlank())
    }

    /**
     * Camino que sigue `CreateAccountSheet.kt` (único call site en la UI, Ola 1b): crea la cuenta
     * en $0 y postea el saldo inicial aparte, como su propio evento. `LocalRepository` no sabe
     * nada de "apertura" — solo tiene que espejar correctamente lo que la UI le manda: la cuenta
     * creada y el evento posteado después. Camino ONLINE (`remote` responde sin fallar).
     */
    @Test
    fun crear_cuenta_con_saldo_deja_el_opening_en_getEvents_y_el_balance_correcto_camino_online() = runBlocking {
        val db = createDatabase("test.db")
        val repoOnline = LocalRepository(db = db, remote = NoOpRepository(), userId = { testUserId })

        val created = repoOnline.createAccount(Account("acc-con-saldo", "Ahorros", AccountType.SAVINGS, 0L))
        val opening = openingEventFor(
            Account("acc-con-saldo", "Ahorros", AccountType.SAVINGS, balance = 700_000L),
            now = 1_700_000_000_000L,
        )!!
        repoOnline.postEvent(opening)

        assertEquals("acc-con-saldo", created.id)
        val mirrored = repoOnline.getEvents("acc-con-saldo").single()
        assertEquals(OPENING_CATEGORY, mirrored.category)
        assertEquals("Saldo inicial", mirrored.description)
        assertEquals(700_000L, repoOnline.getAccount("acc-con-saldo").balance)
    }

    /**
     * Mismo camino, pero SIN red al crear la cuenta (`FailingCreateAccountRepository`): la cuenta
     * queda local, `syncedAt = null`. `postEvent` para el opening no depende de que la cuenta ya
     * esté sincronizada con el server — escribe local igual, como cualquier otro evento — así que
     * el resultado que ve el dueño en su teléfono es idéntico al camino online.
     */
    @Test
    fun crear_cuenta_con_saldo_deja_el_opening_en_getEvents_y_el_balance_correcto_camino_offline() = runBlocking {
        val db = createDatabase("test.db")
        val repoOffline = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })

        val created = repoOffline.createAccount(Account("acc-offline-saldo", "Efectivo", AccountType.CASH, 0L))
        assertNull(db.accountQueries.selectById("acc-offline-saldo").executeAsOne().syncedAt)

        val opening = openingEventFor(
            Account("acc-offline-saldo", "Efectivo", AccountType.CASH, balance = 300_000L),
            now = 1_700_000_000_000L,
        )!!
        repoOffline.postEvent(opening)

        assertEquals("acc-offline-saldo", created.id)
        val mirrored = repoOffline.getEvents("acc-offline-saldo").single()
        assertEquals(OPENING_CATEGORY, mirrored.category)
        assertEquals(300_000L, repoOffline.getAccount("acc-offline-saldo").balance)
    }

    /**
     * F20 (Ola 5): la cuenta que `POST /api/credits` crea nace en el server, pero Cuentas en
     * Android lee de esta DB y el SyncEngine solo empuja — sin espejo, el crédito recién creado
     * no aparece en Cuentas del teléfono.
     */
    @Test
    fun createCredit_mirrors_the_server_account_locally() = runBlocking {
        val summary = repo.createCredit(
            com.jvillada.movi.shared.model.CreateCreditRequest(
                name = "Crédito Vehículo",
                initialDebt = 50_000_000L,
                terms = com.jvillada.movi.shared.model.CreditTerms(
                    accountId = "acc-loan-server", bank = "Santander", principal = 60_000_000L,
                    rateEa = 18.0, termMonths = 60, installment = 1_500_000L,
                    dayOfMonth = 25, startDate = "2026-01-15",
                ),
            ),
        )
        val local = repo.getAccounts().single { it.id == summary.account.id }
        assertEquals(AccountType.LOAN, local.type)
    }

    /** Mismo espejo que createCredit, para `POST /api/cards`. */
    @Test
    fun createCard_mirrors_the_server_account_locally() = runBlocking {
        val summary = repo.createCard(
            com.jvillada.movi.shared.model.CreateCardRequest(
                name = "Visa Bancolombia",
                initialDebt = 2_000_000L,
                terms = com.jvillada.movi.shared.model.CardTerms(
                    accountId = "", bank = "Bancolombia", creditLimit = 10_000_000L,
                    cutoffDay = 10, paymentDay = 25,
                ),
            ),
        )
        val local = repo.getAccounts().single { it.id == summary.account.id }
        assertEquals(AccountType.CREDIT_CARD, local.type)
        assertEquals(2_000_000L, local.balance)
    }

    // ── Traspasos ─────────────────────────────────────────────────────────────

    private fun transferRequest(amount: Long = 250_000L) = com.jvillada.movi.shared.model.CreateTransferRequest(
        transferId = "tr-1",
        fromEventId = "ev-tr-from",
        toEventId = "ev-tr-to",
        fromAccountId = "acc-ahorros",
        toAccountId = "acc-cdt",
        amount = amount,
        timestamp = 1_700_000_000_000L,
    )

    private suspend fun crearCuentasDelTraspaso() {
        repo.createAccount(Account("acc-ahorros", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.createAccount(Account("acc-cdt", "CDT", AccountType.INVESTMENT, 0L))
    }

    /**
     * Las dos patas tienen que quedar en la DB local: en Android, Movimientos y el detalle de
     * cada cuenta leen de acá, no del server. Sin el espejo el traspaso era invisible en el
     * teléfono y los dos saldos se quedaban en el valor viejo.
     */
    @Test
    fun createTransfer_espeja_las_dos_patas_y_mueve_los_dos_saldos() = runBlocking {
        crearCuentasDelTraspaso()

        repo.createTransfer(transferRequest())

        val ahorros = repo.getEvents("acc-ahorros")
        val cdt = repo.getEvents("acc-cdt")
        assertEquals(1, ahorros.size)
        assertEquals(1, cdt.size)
        assertEquals(TransactionType.EXPENSE, ahorros.single().type)
        assertEquals(TransactionType.INCOME, cdt.single().type)
        assertEquals("tr-1", ahorros.single().transferId)
        assertEquals("tr-1", cdt.single().transferId)
        // Las dos patas quedan fuera del flujo de caja aunque estén en cuentas de activo.
        assertFalse(ahorros.single().countsAsCashFlow)
        assertFalse(cdt.single().countsAsCashFlow)

        assertEquals(750_000L, repo.getAccount("acc-ahorros").balance)
        assertEquals(250_000L, repo.getAccount("acc-cdt").balance)
    }

    /**
     * El traspaso es remote-first SIN respaldo local (mismo criterio que [LocalRepository.deleteAccount]):
     * la atomicidad de las dos patas vive en la transacción del server, y el `SyncEngine` empuja
     * eventos de a uno — un traspaso anotado offline podía llegar por mitades. Si el POST falla,
     * la excepción sale para que la UI lo diga, y **nada** se escribe local.
     */
    @Test
    fun createTransfer_sin_red_no_deja_medio_traspaso_local() = runBlocking {
        crearCuentasDelTraspaso()
        val sinRed = object : NoOpRepository() {
            override suspend fun createTransfer(request: com.jvillada.movi.shared.model.CreateTransferRequest) =
                error("sin red: el POST /api/transfers no llegó")
        }
        val offline = LocalRepository(db = db, remote = sinRed, userId = { testUserId })

        val fallo = runCatching { offline.createTransfer(transferRequest()) }

        assertTrue(fallo.isFailure)
        assertTrue(repo.getEvents("acc-ahorros").isEmpty())
        assertTrue(repo.getEvents("acc-cdt").isEmpty())
        assertEquals(1_000_000L, repo.getAccount("acc-ahorros").balance)
    }

    /**
     * Anular una pata anula la otra también en el espejo local: si no, el teléfono mostraría la
     * plata saliendo de Ahorros sin volver del CDT hasta el próximo arranque.
     */
    @Test
    fun voidEvent_de_una_pata_revierte_los_dos_saldos_locales() = runBlocking {
        crearCuentasDelTraspaso()
        repo.createTransfer(transferRequest())

        repo.voidEvent("ev-tr-from")

        assertEquals(1_000_000L, repo.getAccount("acc-ahorros").balance)
        assertEquals(0L, repo.getAccount("acc-cdt").balance)
    }

    /**
     * Y al revés: anular la pata de destino también deshace la de origen.
     */
    @Test
    fun voidEvent_de_la_pata_de_destino_tambien_revierte_la_de_origen() = runBlocking {
        crearCuentasDelTraspaso()
        repo.createTransfer(transferRequest())

        repo.voidEvent("ev-tr-to")

        assertEquals(1_000_000L, repo.getAccount("acc-ahorros").balance)
        assertEquals(0L, repo.getAccount("acc-cdt").balance)
    }

    /**
     * La cola de C1. El escenario completo: (1) el server commitea las dos patas y la respuesta se
     * pierde; (2) el dueño vuelve a tocar Guardar con los MISMOS ids; (3) el server reconoce el
     * reintento. Si esa segunda respuesta llega como 409 y `createTransfer` la deja salir como
     * excepción, el espejo local nunca se escribe — y como el `SyncEngine` solo empuja, jamás
     * trae, el traspaso queda invisible en el teléfono PARA SIEMPRE: Movimientos, Cuentas y el
     * detalle leen local, mientras Inicio (que lee remoto) sí lo cuenta. El teléfono se
     * contradice a sí mismo, y el reflejo del dueño es rehacerlo desde el formulario —ahora con
     * ids nuevos— que es el duplicado real que C1 vino a evitar.
     *
     * Las patas se reconstruyen con `transferLegsFor`, la MISMA función que usó el server: mismos
     * ids, mismo monto, misma marca de tiempo, misma descripción. No es una adivinanza.
     */
    @Test
    fun createTransfer_ante_un_409_espeja_igual_las_dos_patas() = runBlocking {
        crearCuentasDelTraspaso()
        val yaRegistrado = object : NoOpRepository() {
            override suspend fun createTransfer(request: com.jvillada.movi.shared.model.CreateTransferRequest) =
                throw ApiException(409, "Ese traspaso ya está registrado")
        }
        val repoConflicto = LocalRepository(db = db, remote = yaRegistrado, userId = { testUserId })

        val result = repoConflicto.createTransfer(transferRequest())

        assertEquals("ev-tr-from", result.from.id)
        assertEquals("ev-tr-to", result.to.id)
        assertEquals(1, repo.getEvents("acc-ahorros").size, "la pata de origen tiene que quedar local")
        assertEquals(1, repo.getEvents("acc-cdt").size, "y la de destino también")
        assertEquals(750_000L, repo.getAccount("acc-ahorros").balance)
        assertEquals(250_000L, repo.getAccount("acc-cdt").balance)
    }

    /**
     * Y el espejo es idempotente: el reintento que termina en 409 no puede volver a mover los
     * saldos si la primera pasada YA los movió. Sin esta guarda, «guardar dos veces» dejaba dos
     * filas (bien, por la PK) pero el saldo descontado dos veces (mal, y en silencio).
     */
    @Test
    fun createTransfer_repetido_no_mueve_los_saldos_dos_veces() = runBlocking {
        crearCuentasDelTraspaso()
        repo.createTransfer(transferRequest())

        val yaRegistrado = object : NoOpRepository() {
            override suspend fun createTransfer(request: com.jvillada.movi.shared.model.CreateTransferRequest) =
                throw ApiException(409, "Ese traspaso ya está registrado")
        }
        LocalRepository(db = db, remote = yaRegistrado, userId = { testUserId })
            .createTransfer(transferRequest())

        assertEquals(1, repo.getEvents("acc-ahorros").size)
        assertEquals(1, repo.getEvents("acc-cdt").size)
        assertEquals(750_000L, repo.getAccount("acc-ahorros").balance, "el saldo se movió UNA vez")
        assertEquals(250_000L, repo.getAccount("acc-cdt").balance)
    }

    /** Cualquier otro error sigue saliendo: un 500 no puede disfrazarse de «ya estaba guardado». */
    @Test
    fun createTransfer_ante_un_error_que_no_es_409_sigue_fallando() = runBlocking {
        crearCuentasDelTraspaso()
        val roto = object : NoOpRepository() {
            override suspend fun createTransfer(request: com.jvillada.movi.shared.model.CreateTransferRequest) =
                throw ApiException(500, "No se pudo registrar el traspaso")
        }

        val fallo = runCatching {
            LocalRepository(db = db, remote = roto, userId = { testUserId }).createTransfer(transferRequest())
        }

        assertTrue(fallo.isFailure)
        assertTrue(repo.getEvents("acc-ahorros").isEmpty())
    }

    // ── M3: la guarda simétrica del server, del lado del cliente ──────────────

    /**
     * Sin esto, un evento con la categoría reservada entraba al espejo local y el daño era
     * silencioso y permanente: `isCashFlow` lo deja fuera del mes (el gasto REAL del dueño
     * desaparece del teléfono), el `SyncEngine` lo empuja, el server contesta 422 (`POST
     * /api/events` no acepta patas sueltas), el catch se traga el error y la fila se reintenta
     * cada 30 segundos para siempre.
     *
     * Y era alcanzable sin mala fe: Movimientos y Presupuestos metían TODAS las categorías en el
     * caché de sugerencias, así que «Traspaso» se ofrecía para escribir en Agregar.
     */
    @Test
    fun postEvent_rechaza_la_categoria_reservada_en_vez_de_esconder_el_gasto() = runBlocking {
        repo.createAccount(Account("acc-guarda", "Ahorros", AccountType.SAVINGS, 100_000L))

        val fallo = runCatching {
            repo.postEvent(
                event("ev-falso", "acc-guarda", TransactionType.EXPENSE, 50_000L)
                    .copy(category = TRANSFER_CATEGORY),
            )
        }

        assertTrue(fallo.isFailure)
        assertTrue(repo.getEvents("acc-guarda").isEmpty(), "no puede quedar ninguna fila local")
        assertEquals(100_000L, repo.getAccount("acc-guarda").balance, "ni moverse el saldo")
    }

    /** Y recategorizar HACIA la categoría reservada tampoco: sería fabricar media pata. */
    @Test
    fun updateEventCategory_rechaza_la_categoria_reservada_como_destino() = runBlocking {
        repo.createAccount(Account("acc-dest", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("ev-normal", "acc-dest", TransactionType.EXPENSE, 10_000L))

        val fallo = runCatching { repo.updateEventCategory("ev-normal", TRANSFER_CATEGORY) }

        assertTrue(fallo.isFailure)
        assertEquals("test", repo.getEvents("acc-dest").single().category)
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
