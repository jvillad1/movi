package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CUENTA_NO_ENCONTRADA
import com.jvillada.movi.shared.model.EVENT_DATE_IN_FUTURE
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import com.jvillada.movi.shared.model.MONTO_INVALIDO
import com.jvillada.movi.shared.model.PATA_NO_CAMBIA_DE_CUENTA
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_NOT_MANUAL
import com.jvillada.movi.shared.model.OPENING_CATEGORY_RESERVED
import com.jvillada.movi.shared.model.OPENING_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.ORPHANED_LEG_SUFFIX
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.openingEventFor
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toInstant
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

    /**
     * Este test se llamaba así desde siempre y **afirmaba lo contrario de su nombre**: que el
     * evento anulado seguía saliendo, con un comentario que lo justificaba («callers filter by
     * checking void_events»). Ningún llamador filtraba: en el teléfono, anular un movimiento
     * ajustaba el saldo y dejaba el renglón en la lista. El nombre decía la verdad y la aserción
     * documentaba el defecto.
     */
    @Test
    fun getEvents_excludes_voided_events() = runBlocking {
        repo.createAccount(Account("acc3", "Savings", AccountType.SAVINGS, 0L))
        repo.postEvent(event("evt3", "acc3", TransactionType.INCOME, 1_000L))
        repo.postEvent(event("evt4", "acc3", TransactionType.INCOME, 2_000L))

        repo.voidEvent("evt3")

        val events = repo.getEvents("acc3")
        assertTrue(events.none { it.id == "evt3" }, "el movimiento anulado no se lista")
        assertTrue(events.any { it.id == "evt4" }, "el que no se anuló sigue estando")
    }

    /**
     * El caso del dueño, que es el que originó todo esto: cargó un movimiento desde el teléfono y
     * en Movimientos le figuró **ese solo renglón**, y preguntó si había perdido su salario y sus
     * gastos del mes. No había perdido nada: estaban en el server, y el teléfono no los pedía.
     */
    @Test
    fun la_base_local_vacia_muestra_lo_que_el_server_tiene() = runBlocking {
        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accS", "Bancolombia", AccountType.SAVINGS, 0L)
        repeat(18) { i ->
            remoto.eventosDelServer += event("srv$i", "accS", TransactionType.EXPENSE, 1_000L)
                .copy(syncedAt = 1L)
        }
        val soloLectura = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = soloLectura.getEvents()

        assertEquals(18, eventos.size, "los 18 del server se ven aunque el teléfono no tuviera nada")
    }

    /**
     * La regla que puede convertir este arreglo en una pérdida de datos aparente: un gasto anotado
     * **sin señal** todavía no está en el server, y no puede desaparecer de la pantalla porque la
     * lista ahora salga de allá.
     *
     * Va sobre `getEvents()` **sin cuenta** a propósito: es la lista completa la única donde corre
     * la regla anti-fantasma, así que preguntar por una cuenta probaría el caso fácil. La primera
     * versión de este test hacía justamente eso y pasaba igual con el código viejo.
     */
    @Test
    fun lo_anotado_y_no_subido_sigue_viendose() = runBlocking {
        repo.createAccount(Account("accP", "Efectivo", AccountType.CASH, 0L))
        repo.postEvent(event("pendiente", "accP", TransactionType.EXPENSE, 7_000L))
        // El server conoce OTRO evento, no el pendiente: así la respuesta remota no está vacía
        // (que desactivaría la regla) y el pendiente es lo único que la regla podría tapar.
        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accP", "Efectivo", AccountType.CASH, 0L)
        remoto.eventosDelServer += event("otro", "accP", TransactionType.EXPENSE, 1_000L)
            .copy(syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = conRed.getEvents()

        assertTrue(eventos.any { it.id == "pendiente" }, "lo que falta subir se sigue viendo")
        assertTrue(eventos.any { it.id == "otro" }, "y lo del server también")
    }

    /**
     * El lado positivo de la regla anti-fantasma, que no tenía ni un test: un movimiento que el
     * dueño **borró en la web** tiene que dejar de verse en el teléfono. Es la mitad que justifica
     * que la regla exista.
     */
    @Test
    fun lo_que_el_server_ya_no_tiene_deja_de_verse() = runBlocking {
        repo.createAccount(Account("accF", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("borrado-en-la-web", "accF", TransactionType.EXPENSE, 9_000L))
        repo.postEvent(event("sigue-vivo", "accF", TransactionType.EXPENSE, 4_000L))
        // Selladas las dos: ya se subieron, o sea que el server las conocía.
        db.financialEventQueries.markSynced(1_700_000_000_000L, "borrado-en-la-web")
        db.financialEventQueries.markSynced(1_700_000_000_000L, "sigue-vivo")

        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accF", "Ahorros", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("sigue-vivo", "accF", TransactionType.EXPENSE, 4_000L)
            .copy(syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = conRed.getEvents()

        assertTrue(eventos.none { it.id == "borrado-en-la-web" }, "lo que el server ya no tiene se deja de mostrar")
        assertTrue(eventos.any { it.id == "sigue-vivo" }, "lo que sí tiene se queda")
    }

    /**
     * Y la guarda: una respuesta **vacía** no es evidencia de que el dueño haya borrado su
     * historia. Un filtro nuevo del lado del server, o un `uid` mal resuelto, no pueden costarle
     * el mes entero.
     */
    @Test
    fun una_respuesta_vacia_no_borra_la_pantalla() = runBlocking {
        repo.createAccount(Account("accV", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("sellado", "accV", TransactionType.EXPENSE, 9_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "sellado")

        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accV", "Ahorros", AccountType.SAVINGS, 0L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = conRed.getEvents()

        assertTrue(eventos.any { it.id == "sellado" }, "con el server en blanco se muestra de más, no de menos")
    }

    /**
     * El defecto que encontró la revisión de este mismo cambio: una corrección local que todavía
     * no se pudo subir **no puede** ser pisada por lo que baja del server.
     *
     * El dueño anota un gasto, el ciclo lo empuja como «Comida», él lo recategoriza a «Mercado»
     * —la fila queda sin sellar a propósito, ver `markSyncedIfUnchanged`— y la lectura siguiente
     * le escribía «Comida» encima **y sellaba la fila**: la corrección se perdía para siempre,
     * en silencio, y ningún ciclo futuro volvía a intentarlo.
     */
    @Test
    fun lo_que_baja_del_server_no_pisa_una_correccion_sin_subir() = runBlocking {
        repo.createAccount(Account("accC", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("corregido", "accC", TransactionType.EXPENSE, 50_000L))
        // Lo que el dueño acaba de corregir en el teléfono, todavía sin subir.
        db.financialEventQueries.updateCategory("Mercado", "corregido", testUserId)

        // El server sigue teniendo la versión vieja: es justo la ventana del problema.
        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accC", "Ahorros", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("corregido", "accC", TransactionType.EXPENSE, 50_000L)
            .copy(category = "Comida", syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val leido = conRed.getEvents().single { it.id == "corregido" }

        assertEquals("Mercado", leido.category, "la corrección local gana sobre la versión vieja del server")
        assertTrue(
            db.financialEventQueries.selectUnsynced(testUserId).executeAsList().any { it.id == "corregido" },
            "y la fila sigue en la cola de subida, para que el ciclo la empuje",
        )
    }

    /**
     * La otra mitad: entre que el dueño anula y el `SyncEngine` empuja, el server **todavía**
     * devuelve ese evento. Sin este filtro el movimiento reaparecería solo y se iría de nuevo al
     * rato — un renglón que parpadea es peor que uno que se queda.
     */
    @Test
    fun una_anulacion_sin_empujar_gana_sobre_lo_que_el_server_devuelve() = runBlocking {
        repo.createAccount(Account("accA", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("anulado", "accA", TransactionType.EXPENSE, 5_000L))
        repo.voidEvent("anulado")

        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accA", "Ahorros", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("anulado", "accA", TransactionType.EXPENSE, 5_000L)
            .copy(syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = conRed.getEvents("accA")

        assertTrue(eventos.none { it.id == "anulado" }, "lo anulado acá no revive aunque el server lo mande")
    }

    /**
     * Un evento que está en las dos fuentes sale una sola vez, **y gana el contenido del server**.
     *
     * La primera versión de este test solo contaba ocurrencias, y la clave primaria del espejo ya
     * lo impedía: no podía fallar. Lo que sí se puede romper es la mezcla — que el server sea la
     * autoridad para una fila ya sellada.
     */
    @Test
    fun no_se_duplica_lo_que_esta_en_las_dos_fuentes() = runBlocking {
        repo.createAccount(Account("accD", "CDT", AccountType.SAVINGS, 0L))
        repo.postEvent(event("comun", "accD", TransactionType.INCOME, 3_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "comun")

        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accD", "CDT", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("comun", "accD", TransactionType.INCOME, 3_000L)
            .copy(description = "el nombre que puso la web", syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val eventos = conRed.getEvents()

        assertEquals(1, eventos.count { it.id == "comun" }, "una sola vez")
        assertEquals(
            "el nombre que puso la web",
            eventos.single { it.id == "comun" }.description,
            "y para una fila ya sellada manda el server",
        )
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
        // `markSynced` de abajo simula que la fila YA se subió, así que el server tiene que
        // conocerla: un stub que la ignorara sería un server que rechaza por duplicado algo que
        // después jura no tener.
        val remoto = NoOpRepository(knownEventIds = setOf("evt-pago-sync"))
        remoto.cuentasDelServer += Account("acc-sync", "Ahorros", AccountType.SAVINGS, 1_000_000L)
        remoto.eventosDelServer += event("evt-pago-sync", "acc-sync", TransactionType.EXPENSE, 300_000L)
            .copy(syncedAt = 1_700_000_000_000L)
        val repoSincronizado = LocalRepository(
            db = db,
            remote = remoto,
            userId = { testUserId },
        )
        repoSincronizado.createAccount(Account("acc-sync", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repoSincronizado.postEvent(event("evt-pago-sync", "acc-sync", TransactionType.EXPENSE, 300_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "evt-pago-sync")
        val balanceBefore = repoSincronizado.getAccount("acc-sync").balance

        val result = repoSincronizado.updateEventCategory("evt-pago-sync", CARD_PAYMENT_CATEGORY)
        assertEquals(CARD_PAYMENT_CATEGORY, result.category)
        // El stub echoa description="stub" (ver NoOpRepository): que el resultado lo traiga es la
        // prueba de que sí pasó por remote y no se resolvió local. Antes se usaba un accountId
        // inventado, pero ningún server mueve un evento de cuenta al recategorizarlo — y desde
        // que el espejo escribe lo que el server devuelve, esa ficción se llevaba la fila a una
        // cuenta inexistente.
        assertEquals("stub", result.description)

        val mirrored = repoSincronizado.getEvents("acc-sync").single { it.id == "evt-pago-sync" }
        assertEquals(CARD_PAYMENT_CATEGORY, mirrored.category)
        assertFalse(mirrored.countsAsCashFlow)

        // Recategorizar no es un movimiento de plata: el saldo de la cuenta no se toca.
        assertEquals(balanceBefore, repoSincronizado.getAccount("acc-sync").balance)
    }


    /**
     * **La fecha de un movimiento todavía sin sincronizar se corrige SOLO LOCAL.**
     *
     * Mismo camino B que [updateEventCategory_evento_pendiente_se_resuelve_local_sin_llamar_al_server]
     * y por el mismo motivo: `postEvent` escribe solo local y `syncedAt` sigue `null` hasta que el
     * `SyncEngine` empuje en su ciclo de 30 s. Es exactamente la ventana en la que el dueño
     * corrige la fecha del gasto que **acaba de anotar**, que es cuando más se corrige. El stub no
     * conoce `"evt-fecha"`, así que si esto llamara a `remote` tiraría el 404 real y el test
     * fallaría antes de los asserts.
     */
    @Test
    fun updateEventTimestamp_evento_pendiente_se_resuelve_local_sin_llamar_al_server() = runBlocking {
        repo.createAccount(Account("acc-fecha", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-fecha", "acc-fecha", TransactionType.EXPENSE, 20_000L))
        val balanceBefore = repo.getAccount("acc-fecha").balance

        val ayer = 1_756_000_000_000L
        val result = repo.updateEventTimestamp("evt-fecha", ayer)
        assertEquals(ayer, result.timestamp)
        // Si hubiera ido al server, el stub habría devuelto accountId="acc-stub".
        assertEquals("acc-fecha", result.accountId)

        val mirrored = repo.getEvents("acc-fecha").single { it.id == "evt-fecha" }
        assertEquals(ayer, mirrored.timestamp)
        // Cambiar la fecha no mueve plata: el saldo de la cuenta no se toca.
        assertEquals(balanceBefore, repo.getAccount("acc-fecha").balance)
    }

    /**
     * **Las guardas corren también en el camino local.**
     *
     * Es el hallazgo de la revisión: el camino «evento todavía sin sincronizar» nunca llama a
     * `remote`, así que sin estas dos líneas la guarda de futuro del server no corría para el
     * movimiento recién anotado en el teléfono — y el `SyncEngine` lo subía después por
     * `POST /api/events`, que no valida fecha a propósito. Hoy la UI no ofrece días futuros, pero
     * «hoy no se llega» es exactamente lo que dejó de ser cierto todas las veces que esto salió
     * mal.
     */
    @Test
    fun updateEventTimestamp_rechaza_una_fecha_futura_sin_llamar_al_server() = runBlocking {
        repo.createAccount(Account("acc-futuro", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-futuro", "acc-futuro", TransactionType.EXPENSE, 20_000L))
        val original = repo.getEvents("acc-futuro").single { it.id == "evt-futuro" }.timestamp

        val manana = Clock.System.now().toEpochMilliseconds() + 2 * 24 * 60 * 60 * 1000L
        val fallo = runCatching { repo.updateEventTimestamp("evt-futuro", manana) }.exceptionOrNull()
        assertTrue(fallo is ApiException && fallo.status == 422, "esperaba 422, fue $fallo")
        assertEquals(EVENT_DATE_IN_FUTURE, (fallo as ApiException).serverMessage)

        // Y no tocó la fila: un rechazo no puede dejar la fecha a medio cambiar.
        assertEquals(original, repo.getEvents("acc-futuro").single { it.id == "evt-futuro" }.timestamp)
    }

    /** El piso de cordura: un epoch cerca de 0 esconde el movimiento en 1970 para siempre. */
    @Test
    fun updateEventTimestamp_rechaza_un_epoch_de_1970() = runBlocking {
        repo.createAccount(Account("acc-1970", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-1970", "acc-1970", TransactionType.EXPENSE, 20_000L))

        val fallo = runCatching { repo.updateEventTimestamp("evt-1970", 1_000L) }.exceptionOrNull()
        assertTrue(fallo is ApiException && fallo.status == 400, "esperaba 400, fue $fallo")
    }

    /**
     * Camino A: el evento ya está en el server, así que la corrección tiene que **pasar por él**
     * (es quien valida que la fecha no sea futura) y recién después espejarse. Sin este test, un
     * fix que resolviera todo localmente pasaría igual y dejaría el server con la fecha vieja.
     */
    @Test
    fun updateEventTimestamp_evento_sincronizado_pasa_por_el_server_y_se_espeja() = runBlocking {
        // Igual que en el test de categoría: `markSynced` simula que ya se subió, así que el
        // server tiene que conocer la fila.
        val remoto = NoOpRepository(knownEventIds = setOf("evt-fecha-sync"))
        remoto.cuentasDelServer += Account("acc-fecha-sync", "Ahorros", AccountType.SAVINGS, 1_000_000L)
        remoto.eventosDelServer += event("evt-fecha-sync", "acc-fecha-sync", TransactionType.EXPENSE, 20_000L)
            .copy(syncedAt = 1_700_000_000_000L)
        val repoSincronizado = LocalRepository(
            db = db,
            remote = remoto,
            userId = { testUserId },
        )
        repoSincronizado.createAccount(Account("acc-fecha-sync", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repoSincronizado.postEvent(event("evt-fecha-sync", "acc-fecha-sync", TransactionType.EXPENSE, 20_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "evt-fecha-sync")

        val ayer = 1_756_000_000_000L
        val result = repoSincronizado.updateEventTimestamp("evt-fecha-sync", ayer)
        assertEquals(ayer, result.timestamp)
        // El stub siempre echoa accountId="acc-stub": la prueba de que sí pasó por remote.
        // Mismo criterio que el test de categoría: la prueba de que pasó por remote es la
        // descripción «stub», no un accountId inventado que el espejo después escribiría.
        assertEquals("stub", result.description)

        assertEquals(
            ayer,
            repoSincronizado.getEvents("acc-fecha-sync").single { it.id == "evt-fecha-sync" }.timestamp,
        )
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

    /**
     * **Ola 16 — el desembolso que nace con el crédito también se espeja, y el saldo del crédito
     * NO se toca dos veces.**
     *
     * Las dos patas las escribió el server en su transacción, así que en el teléfono solo existen
     * si esta respuesta las trae y este espejo las guarda: Movimientos, Cuentas y el detalle leen
     * de SQLDelight, y el `SyncEngine` solo empuja.
     *
     * La aserción que de verdad importa es la del saldo del crédito. `mirrorAccountLocally` ya
     * escribió la deuda que el server derivó de TODOS sus eventos —el desembolso incluido—, así
     * que aplicarle además el delta de su propia pata lo dejaría en $514.000.000 sobre un crédito
     * de $257.000.000: el número inflado que toda esta rama existe para evitar, entrando por la
     * puerta de atrás del espejo.
     */
    @Test
    fun createCredit_espeja_el_desembolso_sin_contar_la_deuda_dos_veces() = runBlocking {
        repo.createAccount(Account("acc-corriente", "Bancolombia", AccountType.SAVINGS, 12_400_000L))

        val summary = repo.createCredit(
            com.jvillada.movi.shared.model.CreateCreditRequest(
                name = "Libranza",
                initialDebt = 0L,
                terms = com.jvillada.movi.shared.model.CreditTerms(
                    accountId = "acc-libranza", bank = "Bancolombia", principal = 257_000_000L,
                    rateEa = 12.0, termMonths = 120, installment = 3_500_000L,
                    dayOfMonth = 5, startDate = "2026-08-25",
                ),
                disbursement = com.jvillada.movi.shared.model.CreditDisbursement(
                    toAccountId = "acc-corriente", amount = 257_000_000L,
                ),
            ),
        )
        val idDelCredito = summary.account.id

        // Las dos patas quedaron, una en cada cuenta, con la categoría reservada.
        val delCredito = repo.getEvents(idDelCredito).single()
        val deLaCuenta = repo.getEvents("acc-corriente").single()
        assertEquals(TransactionType.EXPENSE, delCredito.type)
        assertEquals(TransactionType.INCOME, deLaCuenta.type)
        assertEquals(TRANSFER_CATEGORY, deLaCuenta.category)
        assertFalse(deLaCuenta.countsAsCashFlow)
        assertEquals(delCredito.transferId, deLaCuenta.transferId)

        // El efectivo subió lo que entró…
        assertEquals(269_400_000L, repo.getAccount("acc-corriente").balance)
        // …y la deuda vale el capital, NO el doble.
        assertEquals(257_000_000L, repo.getAccount(idDelCredito).balance)
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

    // ── deleteAccount: el espejo del desenlace de la pata hermana ─────────────

    /**
     * M1: la contraparte local de `desenlazarPatasHermanas` (ver `AccountRoutes.kt`).
     *
     * Toda la justificación de este espejo es "el teléfono y el server no pueden divergir", y
     * divergir es justo lo que un test atrapa: el `SyncEngine` **solo empuja**, nada baja del
     * server, así que si este UPDATE no corriera, el celular se quedaría para siempre con un
     * «Traspaso a CDT» enlazado a una hermana que ya no existe — y con la categoría reservada,
     * que además lo dejaría fuera de los chips Gastos e Ingresos y sin forma de recategorizarlo.
     *
     * Las aserciones son las MISMAS que las de `TransferRoutesTest`: si un lado cambia y el otro
     * no, uno de los dos tests se cae.
     */
    @Test
    fun deleteAccount_suelta_la_pata_hermana_del_traspaso() = runBlocking {
        crearCuentasDelTraspaso()
        repo.createTransfer(transferRequest())

        repo.deleteAccount("acc-cdt")

        val pata = repo.getEvents("acc-ahorros").single()
        assertNull(pata.transferId, "ya no es media pareja")
        assertEquals(ORPHANED_LEG_CATEGORY, pata.category)
        assertEquals("Traspaso a acc-cdt$ORPHANED_LEG_SUFFIX", pata.description)
        // Y NO vuelve a ser flujo de caja (ola 15): la bandera derivada del espejo local tiene que
        // decir lo mismo que `isCashFlow` del lado del server, o el teléfono y la web muestran dos
        // «Ingresos del mes» distintos para los mismos datos.
        assertFalse(pata.countsAsCashFlow)
        // El saldo de Ahorros NO se toca: la plata salió de verdad. Este par de líneas es la
        // promesa entera — el saldo se mueve, el mes no.
        assertEquals(750_000L, repo.getAccount("acc-ahorros").balance)
    }

    /** El espejo de M2: si se borra el origen, la que sobrevive es la pata de INGRESO. */
    @Test
    fun deleteAccount_de_la_cuenta_de_origen_deja_la_pata_de_ingreso_con_su_categoria() = runBlocking {
        crearCuentasDelTraspaso()
        repo.createTransfer(transferRequest())

        repo.deleteAccount("acc-ahorros")

        val pata = repo.getEvents("acc-cdt").single()
        assertEquals(TransactionType.INCOME, pata.type)
        assertNull(pata.transferId)
        assertEquals(ORPHANED_LEG_CATEGORY, pata.category)
        assertEquals("Traspaso desde acc-ahorros$ORPHANED_LEG_SUFFIX", pata.description)
        // El lado caro: acá la pata que sobrevive es un INGRESO, y es la que con un crédito de por
        // medio valía $257.000.000 de plata «ganada» que nadie ganó.
        assertFalse(pata.countsAsCashFlow)
        assertEquals(250_000L, repo.getAccount("acc-cdt").balance)
    }

    /** Una cuenta sin traspasos no le toca un pelo a los movimientos de las demás. */
    @Test
    fun deleteAccount_sin_traspasos_no_toca_ningun_otro_movimiento() = runBlocking {
        crearCuentasDelTraspaso()
        repo.postEvent(event("evt-mercado", "acc-ahorros", TransactionType.EXPENSE, 30_000L))
        repo.createAccount(Account("acc-suelta", "Efectivo", AccountType.CASH, 0L))
        repo.postEvent(event("evt-taxi", "acc-suelta", TransactionType.EXPENSE, 8_000L))

        repo.deleteAccount("acc-suelta")

        val mercado = repo.getEvents("acc-ahorros").single()
        assertEquals("evt-mercado", mercado.id)
        assertEquals("test", mercado.category)
        assertNull(mercado.transferId)
        assertTrue(repo.getEvents("acc-suelta").isEmpty())
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
        // Un 409 significa que el server YA tiene ese traspaso, así que su GET siguiente lo
        // devuelve. Sin esto el doble se contradiría —rechaza por duplicado algo que después jura
        // no tener— y las patas locales selladas quedarían como fantasmas de una historia que
        // ningún server real cuenta.
        yaRegistrado.cuentasDelServer += repo.getAccounts()
        yaRegistrado.eventosDelServer += com.jvillada.movi.shared.model.transferLegsFor(
            transferRequest(),
            repo.getAccount("acc-ahorros"),
            repo.getAccount("acc-cdt"),
        ).toList()
        val repoConflicto = LocalRepository(db = db, remote = yaRegistrado, userId = { testUserId })

        val result = repoConflicto.createTransfer(transferRequest())

        assertEquals("ev-tr-from", result.from.id)
        assertEquals("ev-tr-to", result.to.id)
        assertEquals(1, repoConflicto.getEvents("acc-ahorros").size, "la pata de origen tiene que quedar local")
        assertEquals(1, repoConflicto.getEvents("acc-cdt").size, "y la de destino también")
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

    /**
     * Ola 15: **«Cuenta eliminada» tampoco se escribe a mano**, y en el espejo local la guarda
     * hace tanta falta como en el server — más, porque acá escribe PRIMERO y pregunta después.
     * Desde que `isCashFlow` excluye esa categoría, dejarla pasar sacaría un gasto REAL de
     * «Gastos del mes» en el teléfono, el `SyncEngine` lo empujaría, el server contestaría 422 y
     * la fila se reintentaría cada 30 segundos para siempre.
     */
    @Test
    fun updateEventCategory_rechaza_Cuenta_eliminada_como_destino() = runBlocking {
        repo.createAccount(Account("acc-huerf", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("ev-gasto-real", "acc-huerf", TransactionType.EXPENSE, 80_000L))

        val fallo = runCatching { repo.updateEventCategory("ev-gasto-real", ORPHANED_LEG_CATEGORY) }
            .exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 422, "esperaba 422, fue $fallo")
        assertEquals(ORPHANED_LEG_NOT_MANUAL, (fallo as ApiException).serverMessage)
        val quedo = repo.getEvents("acc-huerf").single()
        assertEquals("test", quedo.category, "el gasto real se queda donde estaba")
        assertTrue(quedo.countsAsCashFlow, "y sigue contando en el mes")
    }

    /**
     * Ola 16: **«Saldo inicial» tampoco**, y por el mismo modo de falla que la de arriba — con un
     * agravante propio. Escribirla sobre un gasto real lo saca del mes en el teléfono, el
     * `SyncEngine` lo empuja, el server (que desde esta ola también la bloquea) contesta 422 y la
     * fila se reintenta cada 30 segundos para siempre. Y desde que Movimientos no lista las
     * aperturas, esa fila envenenada además **desaparece de la pantalla**: el dueño no tendría ni
     * cómo notar que su gasto se fue.
     */
    @Test
    fun updateEventCategory_rechaza_Saldo_inicial_como_destino() = runBlocking {
        repo.createAccount(Account("acc-open-dest", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("ev-gasto-50", "acc-open-dest", TransactionType.EXPENSE, 50_000L))

        val fallo = runCatching { repo.updateEventCategory("ev-gasto-50", OPENING_CATEGORY) }
            .exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 422, "esperaba 422, fue $fallo")
        assertEquals(OPENING_CATEGORY_RESERVED, (fallo as ApiException).serverMessage)
        val quedo = repo.getEvents("acc-open-dest").single()
        assertEquals("test", quedo.category, "el gasto real se queda donde estaba")
        assertTrue(quedo.countsAsCashFlow, "y sigue contando en el mes")
    }

    /**
     * Y el sentido inverso: una apertura no se saca de su categoría. Sin esta guarda, un «Saldo
     * inicial» de una cuenta de activo recategorizado a «Otros ingresos» se convierte en un
     * ingreso del mes de golpe — la cifra entera de apertura presentada como plata que llegó.
     */
    @Test
    fun updateEventCategory_rechaza_sacar_una_apertura_de_su_categoria() = runBlocking {
        repo.createAccount(Account("acc-open-src", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(
            event("ev-apertura", "acc-open-src", TransactionType.INCOME, 3_000_000L)
                .copy(category = OPENING_CATEGORY, description = "Saldo inicial"),
        )

        val fallo = runCatching { repo.updateEventCategory("ev-apertura", "Otros ingresos") }
            .exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 422, "esperaba 422, fue $fallo")
        assertEquals(OPENING_RECATEGORIZE_BLOCKED, (fallo as ApiException).serverMessage)
        val quedo = repo.getEvents("acc-open-src").single { it.id == "ev-apertura" }
        assertEquals(OPENING_CATEGORY, quedo.category)
        assertFalse(quedo.countsAsCashFlow, "y sigue fuera del mes")
    }

    // ── Cuentas que nacieron en el server ─────────────────────────────────────
    //
    // El teléfono leía SOLO SQLDelight y el SyncEngine solo empuja: una cuenta creada en la web
    // (o sembrada por API, o anterior a esta instalación) no existía para el celular. Cuentas
    // decía «Sin cuentas aún» con la plata cargada, y la hoja de un recurrente borraba la cuenta
    // de la regla al guardar.

    private fun cuentaServer(id: String, nombre: String, saldo: Long = 0L) =
        Account(id = id, name = nombre, type = AccountType.SAVINGS, balance = saldo)

    @Test
    fun getAccounts_trae_las_cuentas_del_server_aunque_no_esten_en_la_base_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Bancolombia Ahorros", 750_000L)))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        val cuentas = repoConServer.getAccounts()

        assertEquals(listOf("acc-web"), cuentas.map { it.id })
        assertEquals(750_000L, cuentas.single().balance, "el saldo es el que derivó el server")
    }

    /** El espejo es lo que deja la cuenta disponible sin red la próxima vez. */
    @Test
    fun getAccounts_espeja_lo_que_trajo_del_server_y_lo_sigue_mostrando_sin_red() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi", 120_000L)))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        repoConServer.getAccounts()
        server.falla = true

        val sinRed = repoConServer.getAccounts()
        assertEquals(listOf("acc-web"), sinRed.map { it.id }, "sin red se sigue viendo lo espejado")
        assertEquals(120_000L, sinRed.single().balance)
    }

    /** INSERT OR REPLACE sobre la misma PK: leer diez veces no crea diez cuentas. */
    @Test
    fun getAccounts_no_duplica_al_espejar_dos_veces() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Efectivo")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        repoConServer.getAccounts()
        repoConServer.getAccounts()

        assertEquals(1, db.accountQueries.selectAll(testUserId).executeAsList().size)
    }

    /** Espejar una cuenta del server no puede dejarla encolada para que el SyncEngine la re-suba. */
    @Test
    fun getAccounts_marca_como_sincronizado_lo_que_vino_del_server() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Efectivo")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        repoConServer.getAccounts()

        assertTrue(db.accountQueries.selectUnsynced(testUserId).executeAsList().isEmpty())
    }

    /**
     * Lo que se creó offline todavía no está en el server, y no por eso desaparece de la lista:
     * el conjunto es «lo que el server tiene» ∪ «lo que este teléfono todavía no pudo subir».
     */
    @Test
    fun getAccounts_conserva_las_cuentas_creadas_offline_que_el_server_no_conoce() = runBlocking {
        val db = createDatabase("test.db")
        val repoSinRed = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        repoSinRed.createAccount(Account("acc-offline", "Efectivo", AccountType.CASH, 0L))

        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        val ids = repoConServer.getAccounts().map { it.id }
        assertEquals(setOf("acc-offline", "acc-web"), ids.toSet())
        assertEquals(
            listOf("acc-offline"),
            db.accountQueries.selectUnsynced(testUserId).executeAsList().map { it.id },
            "la de offline sigue pendiente de subir",
        )
    }

    /** El orden lo pone SQLDelight (`lower(name), id`), igual que `GET /api/accounts`. */
    @Test
    fun getAccounts_respeta_el_orden_alfabetico_sin_distinguir_mayusculas() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(
            listOf(cuentaServer("acc-n", "Nequi"), cuentaServer("acc-e", "efectivo")),
        )
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        assertEquals(listOf("efectivo", "Nequi"), repoConServer.getAccounts().map { it.name })
    }

    /**
     * Sin red y sin nada local, «no tienes cuentas» sería una afirmación sin respaldo: se propaga
     * el error para que la pantalla muestre su reintento en vez de un vacío que se lee como un
     * hecho (y que, en el Inicio, invita a crear un duplicado de una cuenta que ya existe).
     */
    @Test
    fun getAccounts_sin_red_y_sin_nada_local_propaga_el_error_en_vez_de_afirmar_que_no_hay_cuentas() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(falla = true)
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        assertTrue(runCatching { repoConServer.getAccounts() }.isFailure)
    }

    /** Abrir el detalle de una cuenta del server tiraba una excepción: la fila no existía local. */
    @Test
    fun getAccount_encuentra_una_cuenta_que_nacio_en_el_server() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Bancolombia Ahorros", 750_000L)))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        assertEquals(750_000L, repoConServer.getAccount("acc-web").balance)
    }

    /** Y sin red cae a la fila local, que es lo que el teléfono tenía de antes. */
    @Test
    fun getAccount_sin_red_cae_a_la_fila_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi", 120_000L)))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()

        server.falla = true
        assertEquals("Nequi", repoConServer.getAccount("acc-web").name)
    }

    // ── La cuenta fantasma ────────────────────────────────────────────────────
    //
    // Espejar sin filtrar abría una puerta nueva al MISMO daño que la capa 1 cerró: una cuenta
    // borrada desde la web sobrevivía como fila local para siempre, sumaba al patrimonio, y el
    // selector del recurrente la ofrecía — elegirla ahí perdía la cuenta de la regla, porque el
    // server nulea un id que no conoce.

    @Test
    fun getAccounts_deja_de_mostrar_una_cuenta_que_el_server_ya_no_tiene() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(
            listOf(cuentaServer("acc-banco", "Bancolombia"), cuentaServer("acc-nequi", "Nequi", 2_499_000L)),
        )
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        assertEquals(2, repoConServer.getAccounts().size)

        // Borrada desde la web: el server deja de devolverla, con red perfecta.
        server.cuentas = server.cuentas.filterNot { it.id == "acc-nequi" }

        val visibles = repoConServer.getAccounts()
        assertEquals(listOf("acc-banco"), visibles.map { it.id })
        assertEquals(
            0L,
            visibles.filter { it.id == "acc-nequi" }.sumOf { it.balance },
            "el fantasma no puede seguir sumando al patrimonio",
        )
    }

    /** Ocultarla no es borrarla: la fila y sus eventos siguen ahí, intactos. */
    @Test
    fun getAccounts_oculta_el_fantasma_sin_borrarle_ni_un_dato() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-nequi", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()
        repoConServer.postEvent(event("ev-fantasma", "acc-nequi", TransactionType.EXPENSE, 10_000L))

        server.cuentas = emptyList()
        assertTrue(repoConServer.getAccounts().isEmpty())

        assertNotNull(db.accountQueries.selectById("acc-nequi").executeAsOneOrNull())
        assertEquals(1, repoConServer.getEvents("acc-nequi").size)
    }

    /**
     * La única condición para ocultar es «estaba sellada ANTES de preguntar y el server no la
     * devolvió». Una cuenta creada offline (nunca sellada) no cumple ninguna de las dos, así que
     * el filtro no puede tocarla por más que el server no la conozca.
     */
    @Test
    fun getAccounts_nunca_oculta_una_cuenta_creada_offline() = runBlocking {
        val db = createDatabase("test.db")
        val repoSinRed = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        repoSinRed.createAccount(Account("acc-offline", "Efectivo", AccountType.CASH, 0L))

        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        assertEquals(setOf("acc-offline", "acc-web"), repoConServer.getAccounts().map { it.id }.toSet())
    }

    /**
     * La carrera angosta: el `SyncEngine` sella una cuenta creada offline mientras el GET está en
     * vuelo. El fotograma de «selladas» se toma ANTES de preguntar, así que esa cuenta no estaba
     * sellada cuando se preguntó y no puede parpadear fuera de la lista.
     */
    @Test
    fun getAccounts_no_esconde_la_cuenta_que_el_SyncEngine_sella_durante_el_GET() = runBlocking {
        val db = createDatabase("test.db")
        val repoSinRed = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        repoSinRed.createAccount(Account("acc-en-vuelo", "Efectivo", AccountType.CASH, 0L))

        val server = object : ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi"))) {
            override suspend fun getAccounts(): List<Account> {
                // Lo que haría SyncEngine.syncAccounts en paralelo: sella la fila local.
                db.accountQueries.markSynced(1_700_000_000_000L, "acc-en-vuelo")
                return super.getAccounts()
            }
        }
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })

        assertEquals(
            setOf("acc-en-vuelo", "acc-web"),
            repoConServer.getAccounts().map { it.id }.toSet(),
        )
    }

    /** Sin red no se puede saber si desapareció: no se oculta nada. */
    @Test
    fun getAccounts_sin_red_no_oculta_nada() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()

        server.falla = true
        assertEquals(listOf("acc-web"), repoConServer.getAccounts().map { it.id })
    }

    /**
     * La salida de emergencia: si un fantasma llega igual a la vista, «Eliminar cuenta» tiene que
     * funcionar. El server contesta 404 («ya no existe»), que es exactamente el estado buscado.
     */
    @Test
    fun deleteAccount_trata_el_404_como_exito_y_limpia_la_fila_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-nequi", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()
        server.cuentas = emptyList()

        repoConServer.deleteAccount("acc-nequi")

        assertNull(db.accountQueries.selectById("acc-nequi").executeAsOneOrNull())
    }

    /** Un borrado que falla por red sigue propagando: eso no cambió. */
    @Test
    fun deleteAccount_sin_red_sigue_fallando_sin_tocar_la_fila_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-nequi", "Nequi")))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()

        server.falla = true
        assertTrue(runCatching { repoConServer.deleteAccount("acc-nequi") }.isFailure)
        assertTrue(
            db.accountQueries.selectById("acc-nequi").executeAsOneOrNull() != null,
            "un borrado que no llegó al server no puede sacar la fila local",
        )
    }

    /**
     * Red mala (no «sin red»): el engine espera hasta 30 s para conectar. Con algo local que
     * mostrar, la lectura se corta en el presupuesto de red (5 s) y contesta con lo que hay — si no,
     * la hoja de Agregar se queda medio minuto sin cuenta seleccionada y sin poder guardar.
     */
    @Test
    fun getAccounts_con_red_lenta_contesta_con_lo_local_sin_esperar_al_server() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(cuentaServer("acc-web", "Nequi", 120_000L)))
        val repoConServer = LocalRepository(db = db, remote = server, userId = { testUserId })
        repoConServer.getAccounts()

        server.demoraMs = 30_000L
        val empezo = System.currentTimeMillis()
        val cuentas = repoConServer.getAccounts()
        val tardo = System.currentTimeMillis() - empezo

        assertEquals(listOf("acc-web"), cuentas.map { it.id })
        assertTrue(tardo < 20_000L, "no puede quedarse esperando al server: tardó ${tardo}ms")
    }

    // ── Corregir el monto, la cuenta y el concepto (espejo de PUT /api/events/{id}) ──
    //
    // Lo que estas pruebas fijan y ninguna otra puede: del lado del server el saldo es DERIVADO
    // (se suma de los eventos en cada lectura), pero acá `account.balance` es un **acumulado
    // guardado**. Si `updateEvent` solo reescribiera el movimiento, el teléfono seguiría mostrando
    // el saldo viejo — y sin red no habría lectura que lo pisara nunca.

    @Test
    fun updateEvent_pendiente_corrige_monto_y_ajusta_el_saldo_sin_llamar_al_server() = runBlocking {
        repo.createAccount(Account("acc-monto", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-monto", "acc-monto", TransactionType.EXPENSE, 1_000_000L))
        assertEquals(0L, repo.getAccount("acc-monto").balance)

        val result = repo.updateEvent("evt-monto", EdicionDeMovimiento(amount = 300_000L))

        assertEquals(300_000L, result.amount)
        // Si hubiera ido al server, el stub (que no conoce "evt-monto") habría tirado 404.
        assertEquals("acc-monto", result.accountId)
        assertEquals(300_000L, repo.getEvents("acc-monto").single { it.id == "evt-monto" }.amount)
        // El saldo se rehace con la diferencia: 1.000.000 − 300.000.
        assertEquals(700_000L, repo.getAccount("acc-monto").balance)
    }

    /**
     * **El caso del dueño, entero**: el movimiento «Hija» pasa de $4.000.000 en Bancolombia a
     * $3.000.000 en Nu. Las DOS cuentas se mueven — la vieja recupera lo que había salido, la
     * nueva paga lo que ahora sale de ella.
     */
    @Test
    fun updateEvent_mover_de_cuenta_ajusta_las_DOS_cuentas() = runBlocking {
        repo.createAccount(Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 10_000_000L))
        repo.createAccount(Account("acc-nu", "Nu", AccountType.SAVINGS, 5_000_000L))
        repo.postEvent(event("evt-hija", "acc-banco", TransactionType.EXPENSE, 4_000_000L))
        assertEquals(6_000_000L, repo.getAccount("acc-banco").balance)

        val result = repo.updateEvent(
            "evt-hija",
            EdicionDeMovimiento(amount = 3_000_000L, accountId = "acc-nu", description = "Hija"),
        )

        assertEquals("acc-nu", result.accountId)
        assertEquals(3_000_000L, result.amount)
        assertEquals(10_000_000L, repo.getAccount("acc-banco").balance, "la cuenta vieja recupera los 4M")
        assertEquals(2_000_000L, repo.getAccount("acc-nu").balance, "la nueva paga los 3M")
    }

    /**
     * El signo lo pone la cuenta de DESTINO, no la de origen: en una tarjeta un EXPENSE **sube la
     * deuda**. Sin `signedDelta` mirando el tipo de cada cuenta, mudar un gasto a la tarjeta le
     * habría BAJADO la deuda — el mismo hallazgo que ya se corrigió en `postEvent` y `voidEvent`.
     */
    @Test
    fun updateEvent_mover_un_gasto_a_una_tarjeta_sube_la_deuda() = runBlocking {
        repo.createAccount(Account("acc-ah", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.createAccount(Account("acc-tc", "AMEX", AccountType.CREDIT_CARD, 200_000L))
        repo.postEvent(event("evt-compra", "acc-ah", TransactionType.EXPENSE, 150_000L))
        assertEquals(850_000L, repo.getAccount("acc-ah").balance)

        repo.updateEvent("evt-compra", EdicionDeMovimiento(accountId = "acc-tc"))

        assertEquals(1_000_000L, repo.getAccount("acc-ah").balance, "el ahorro vuelve entero")
        assertEquals(350_000L, repo.getAccount("acc-tc").balance, "en una tarjeta el gasto SUBE la deuda")
    }

    /** Las guardas de `:core` corren también en el camino local — igual que con la fecha. */
    @Test
    fun updateEvent_rechaza_un_monto_de_cero_sin_llamar_al_server() = runBlocking {
        repo.createAccount(Account("acc-cero", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-cero", "acc-cero", TransactionType.EXPENSE, 20_000L))

        val fallo = runCatching { repo.updateEvent("evt-cero", EdicionDeMovimiento(amount = 0L)) }
            .exceptionOrNull()
        assertTrue(fallo is ApiException && fallo.status == 400, "esperaba 400, fue $fallo")
        assertEquals(MONTO_INVALIDO, (fallo as ApiException).serverMessage)
        // Y no dejó la fila ni el saldo a medio cambiar.
        assertEquals(20_000L, repo.getEvents("acc-cero").single { it.id == "evt-cero" }.amount)
        assertEquals(980_000L, repo.getAccount("acc-cero").balance)
    }

    @Test
    fun updateEvent_no_deja_mudar_de_cuenta_una_pata_de_un_par() = runBlocking {
        repo.createAccount(Account("acc-o", "Ahorros", AccountType.SAVINGS, 5_000_000L))
        repo.createAccount(Account("acc-d", "CDT", AccountType.SAVINGS, 0L))
        repo.createAccount(Account("acc-tercera", "Nequi", AccountType.SAVINGS, 0L))
        val traspaso = repo.createTransfer(
            CreateTransferRequest(
                transferId = "tr-local-1",
                fromEventId = "ev-out-1",
                toEventId = "ev-in-1",
                fromAccountId = "acc-o",
                toAccountId = "acc-d",
                amount = 1_000_000L,
                timestamp = 1_788_000_000_000L,
            ),
        )

        val fallo = runCatching {
            repo.updateEvent(traspaso.from.id, EdicionDeMovimiento(accountId = "acc-tercera"))
        }.exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 422, "esperaba 422, fue $fallo")
        assertEquals(PATA_NO_CAMBIA_DE_CUENTA, (fallo as ApiException).serverMessage)
    }

    /**
     * Camino A: el evento ya está en el server, así que la corrección pasa por él y **después** se
     * espeja — incluido el saldo local, que es lo que el teléfono muestra.
     */
    @Test
    fun updateEvent_evento_sincronizado_pasa_por_el_server_y_espeja_saldo() = runBlocking {
        val remoto = NoOpRepository(knownEventIds = setOf("evt-sync-edit"))
        remoto.cuentasDelServer += Account("acc-sync-edit", "Ahorros", AccountType.SAVINGS, 1_000_000L)
        remoto.eventosDelServer += event("evt-sync-edit", "acc-sync-edit", TransactionType.EXPENSE, 100_000L)
            .copy(syncedAt = 1_700_000_000_000L)
        val repoSincronizado = LocalRepository(db = db, remote = remoto, userId = { testUserId })
        repoSincronizado.createAccount(Account("acc-sync-edit", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repoSincronizado.postEvent(event("evt-sync-edit", "acc-sync-edit", TransactionType.EXPENSE, 100_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "evt-sync-edit")
        assertEquals(900_000L, repoSincronizado.getAccount("acc-sync-edit").balance)

        val result = repoSincronizado.updateEvent("evt-sync-edit", EdicionDeMovimiento(amount = 400_000L))

        assertEquals(400_000L, result.amount)
        assertEquals(600_000L, repoSincronizado.getAccount("acc-sync-edit").balance)
        assertEquals(
            400_000L,
            repoSincronizado.getEvents("acc-sync-edit").single { it.id == "evt-sync-edit" }.amount,
        )
    }

    /**
     * **Un movimiento ANULADO no se edita, y el saldo del teléfono no se mueve dos veces.**
     *
     * El caso medido antes del arreglo: cuenta en $1.000.000 → gasto de $100.000 (queda en
     * $900.000) → anular (vuelve a $1.000.000) → `updateEvent(amount = 900.000)` devolvía **éxito**
     * y dejaba el saldo local en **$200.000**, porque `aplicarEdicionLocal` deshacía el efecto
     * viejo y aplicaba el nuevo sobre un movimiento cuyo efecto ya estaba deshecho. El server
     * nunca lo aceptó (trata un anulado como inexistente); era la única guarda suya que este
     * espejo no repetía.
     */
    @Test
    fun updateEvent_no_edita_un_movimiento_anulado_ni_le_mueve_el_saldo() = runBlocking {
        repo.createAccount(Account("acc-anul", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-anul", "acc-anul", TransactionType.EXPENSE, 100_000L))
        assertEquals(900_000L, repo.getAccount("acc-anul").balance)
        repo.voidEvent("evt-anul")
        assertEquals(1_000_000L, repo.getAccount("acc-anul").balance, "anular ya devolvió la plata")

        val fallo = runCatching { repo.updateEvent("evt-anul", EdicionDeMovimiento(amount = 900_000L)) }
            .exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 404, "esperaba 404, fue $fallo")
        // Sin texto: es el mismo 404 vacío que devuelve el server, que la UI lee como «Recurso no
        // encontrado.». Si acá inventáramos un mensaje, el mismo error se leería distinto con red
        // y sin ella.
        assertNull((fallo as ApiException).serverMessage)
        assertEquals(1_000_000L, repo.getAccount("acc-anul").balance, "y el saldo no se movió")
    }

    /** El mismo hueco en el camino de la FECHA. No toca saldos, pero se cierra igual. */
    @Test
    fun updateEventTimestamp_tampoco_refecha_un_movimiento_anulado() = runBlocking {
        repo.createAccount(Account("acc-anul-f", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-anul-f", "acc-anul-f", TransactionType.EXPENSE, 100_000L))
        repo.voidEvent("evt-anul-f")

        val fallo = runCatching { repo.updateEventTimestamp("evt-anul-f", 1_700_000_000_000L) }
            .exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 404, "esperaba 404, fue $fallo")
    }

    /**
     * **«Esa cuenta no existe» no se dice sobre una cuenta que sí existe.**
     *
     * La pre-validación corre siempre que la fila del movimiento esté local, y busca la cuenta
     * destino en la tabla local. Pero el espejo de cuentas de un dispositivo puede estar
     * incompleto, y entonces una edición que iba camino al server rebotaba con un
     * `CUENTA_NO_ENCONTRADA` falso — sin haberle preguntado al único que tiene la lista completa.
     * Acá el movimiento ya está sincronizado y la cuenta destino existe **solo del lado del
     * server**: la edición tiene que pasar por él y salir bien.
     */
    @Test
    fun updateEvent_una_cuenta_que_este_telefono_no_espejo_la_decide_el_server() = runBlocking {
        val remoto = NoOpRepository(knownEventIds = setOf("evt-cuenta-remota"))
        remoto.cuentasDelServer += Account("acc-local-1", "Ahorros", AccountType.SAVINGS, 1_000_000L)
        // La cuenta destino: existe arriba y NO en la base de este teléfono.
        remoto.cuentasDelServer += Account("acc-solo-server", "Nu", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("evt-cuenta-remota", "acc-local-1", TransactionType.EXPENSE, 100_000L)
            .copy(syncedAt = 1_700_000_000_000L)
        val repoSincronizado = LocalRepository(db = db, remote = remoto, userId = { testUserId })
        repoSincronizado.createAccount(Account("acc-local-1", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repoSincronizado.postEvent(event("evt-cuenta-remota", "acc-local-1", TransactionType.EXPENSE, 100_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "evt-cuenta-remota")

        val result = repoSincronizado.updateEvent(
            "evt-cuenta-remota",
            EdicionDeMovimiento(accountId = "acc-solo-server"),
        )

        assertEquals("acc-solo-server", result.accountId)
        // Y la cuenta local que se quedó sin el movimiento recupera su plata.
        assertEquals(1_000_000L, repoSincronizado.getAccount("acc-local-1").balance)
    }

    /**
     * La contracara: **sin red no hay a quién preguntarle**. Un movimiento que todavía no subió se
     * resuelve entero acá, así que una cuenta destino que este dispositivo no conoce sí es, para
     * este camino, una cuenta que no existe — y el 404 local es la respuesta correcta.
     */
    @Test
    fun updateEvent_sin_red_una_cuenta_desconocida_si_se_rechaza() = runBlocking {
        repo.createAccount(Account("acc-pend", "Ahorros", AccountType.SAVINGS, 1_000_000L))
        repo.postEvent(event("evt-pend", "acc-pend", TransactionType.EXPENSE, 100_000L))

        val fallo = runCatching {
            repo.updateEvent("evt-pend", EdicionDeMovimiento(accountId = "acc-que-no-esta"))
        }.exceptionOrNull()

        assertTrue(fallo is ApiException && fallo.status == 404, "esperaba 404, fue $fallo")
        assertEquals(CUENTA_NO_ENCONTRADA, (fallo as ApiException).serverMessage)
        assertEquals(900_000L, repo.getAccount("acc-pend").balance, "y no tocó ningún saldo")
    }

    private fun event(id: String, accountId: String, type: TransactionType, amount: Long) =
        FinancialEvent(
            id = id, accountId = accountId, type = type, amount = amount,
            category = "test", description = "test",
            timestamp = System.currentTimeMillis(),
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.UNCONFIRMED,
        )

    // ── Ola 10 · El orden DENTRO de cada día ────────────────────────────────

    /** Un instante del 20 de agosto de 2026 a la hora de Bogotá que se pida. */
    private fun eseDiaALas(hora: Int, minuto: Int = 0): Long =
        kotlinx.datetime.LocalDateTime(2026, 8, 20, hora, minuto)
            .toInstant(com.jvillada.movi.shared.time.AppTimeZone.zone)
            .toEpochMilliseconds()

    private fun eventoConFecha(
        id: String,
        ts: Long,
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = 10_000L,
        category: String = "Comida",
    ) = FinancialEvent(
        id = id, accountId = "acc-orden", type = type, amount = amount,
        category = category, description = "test", timestamp = ts,
        source = EventSource.MANUAL, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    /**
     * El espejo local ya traía `ORDER BY timestamp DESC` en `selectAll`, así que el grueso del
     * orden estaba; lo que faltaba era que fuera el MISMO criterio que el del server. Este test
     * lo fija: si alguien cambia la consulta, la pantalla del teléfono no se cae con el cambio.
     */
    @Test
    fun `getEventsByDay lista el mas reciente arriba dentro del dia`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(eventoConFecha("ev-8am", eseDiaALas(8)))
        repo.postEvent(eventoConFecha("ev-19pm", eseDiaALas(19)))
        repo.postEvent(eventoConFecha("ev-13pm", eseDiaALas(13)))

        val dia = repo.getEventsByDay().first { it.date == "2026-08-20" }
        assertEquals(listOf("ev-19pm", "ev-13pm", "ev-8am"), dia.items.map { it.id })
    }

    /**
     * Dos movimientos del mismo milisegundo (un lote de SMS, o de extracto) tienen que salir
     * siempre en el mismo orden. SQLite no promete nada para el empate de `ORDER BY timestamp
     * DESC`, así que el desempate por `id` es lo único que hace que la lista no se reordene sola.
     */
    @Test
    fun `dos movimientos del mismo instante salen siempre igual en el telefono`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        val instante = eseDiaALas(10, 30)
        repo.postEvent(eventoConFecha("ev_zzz", instante))
        repo.postEvent(eventoConFecha("ev_aaa", instante))

        val primera = repo.getEventsByDay().first { it.date == "2026-08-20" }.items.map { it.id }
        val segunda = repo.getEventsByDay().first { it.date == "2026-08-20" }.items.map { it.id }
        assertEquals(primera, segunda)
        assertEquals(listOf("ev_aaa", "ev_zzz"), primera)
    }

    /** El total del día no depende del orden — pero es la cifra que el dueño lee. */
    @Test
    fun `el total del dia no se mueve con el orden nuevo`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(eventoConFecha("ev-in", eseDiaALas(9), TransactionType.INCOME, 500_000L, "Salario"))
        repo.postEvent(eventoConFecha("ev-out1", eseDiaALas(20), TransactionType.EXPENSE, 120_000L))
        repo.postEvent(eventoConFecha("ev-out2", eseDiaALas(20), TransactionType.EXPENSE, 80_000L))

        val dia = repo.getEventsByDay().first { it.date == "2026-08-20" }
        assertEquals(300_000L, dia.total)
    }
    /**
     * **El caso del dueño, en el teléfono.** Tres gastos de un día pasado, anotados uno detrás del
     * otro: los tres quedan con el mismo `timestamp` (mediodía) y el orden lo decide el sello de
     * creación que pone [LocalRepository.postEvent] al escribirlos. Arriba, el último anotado.
     *
     * Se anotan con una pausa real entre uno y otro porque el sello sale del reloj: sin la pausa,
     * los tres podrían caer en el mismo milisegundo y el test no probaría nada.
     */
    @Test
    fun `entre gastos del mismo dia pasado manda el que se anoto ultimo`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        val mediodia = eseDiaALas(12)
        for (id in listOf("ev-1ro", "ev-2do", "ev-3ro")) {
            repo.postEvent(eventoConFecha(id, mediodia))
            kotlinx.coroutines.delay(5)
        }

        val dia = repo.getEventsByDay().first { it.date == "2026-08-20" }
        assertEquals(listOf("ev-3ro", "ev-2do", "ev-1ro"), dia.items.map { it.id })
    }

    /** `postEvent` sella la creación al escribir: es el instante que el server no conoce. */
    @Test
    fun `postEvent sella cuando se anoto el movimiento`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        val antes = Clock.System.now().toEpochMilliseconds()
        repo.postEvent(eventoConFecha("ev-sellado", eseDiaALas(12)))

        val sello = repo.getEvents().first { it.id == "ev-sellado" }.createdAt
        assertNotNull(sello)
        assertTrue(sello >= antes, "el sello quedó antes de que empezara el test: $sello")
    }

    /** La creación desempata, no manda: la hora real de un movimiento le sigue ganando. */
    @Test
    fun `la hora real le gana a la hora en que se anoto, tambien en el telefono`() = runBlocking {
        repo.createAccount(Account("acc-orden", "Ahorros", AccountType.SAVINGS, 0L))
        // El de las 23:00 se anota PRIMERO, así que su sello es más viejo; igual queda arriba.
        repo.postEvent(eventoConFecha("ev-23h", eseDiaALas(23)))
        kotlinx.coroutines.delay(5)
        repo.postEvent(eventoConFecha("ev-mediodia", eseDiaALas(12)))

        val dia = repo.getEventsByDay().first { it.date == "2026-08-20" }
        assertEquals(listOf("ev-23h", "ev-mediodia"), dia.items.map { it.id })
    }

    /**
     * Las dos pantallas cuentan lo mismo.
     *
     * Antes, la regla anti-fantasma corría solo sobre la lista completa: un movimiento borrado en
     * la web desaparecía de Movimientos y **seguía viéndose para siempre en el detalle de la
     * cuenta**. Dos pantallas de la misma app contando cosas distintas.
     */
    @Test
    fun lo_borrado_en_la_web_desaparece_tambien_del_detalle_de_la_cuenta() = runBlocking {
        repo.createAccount(Account("accX", "Ahorros", AccountType.SAVINGS, 0L))
        repo.postEvent(event("borrado-en-la-web", "accX", TransactionType.EXPENSE, 9_000L))
        repo.postEvent(event("sigue-vivo", "accX", TransactionType.EXPENSE, 4_000L))
        db.financialEventQueries.markSynced(1_700_000_000_000L, "borrado-en-la-web")
        db.financialEventQueries.markSynced(1_700_000_000_000L, "sigue-vivo")

        val remoto = NoOpRepository()
        remoto.cuentasDelServer += Account("accX", "Ahorros", AccountType.SAVINGS, 0L)
        remoto.eventosDelServer += event("sigue-vivo", "accX", TransactionType.EXPENSE, 4_000L)
            .copy(syncedAt = 1L)
        val conRed = LocalRepository(db = db, remote = remoto, userId = { testUserId })

        val enLaLista = conRed.getEvents().map { it.id }
        val enElDetalle = conRed.getEvents("accX").map { it.id }

        assertTrue(enLaLista.none { it == "borrado-en-la-web" }, "no está en Movimientos")
        assertTrue(enElDetalle.none { it == "borrado-en-la-web" }, "y tampoco en el detalle de la cuenta")
        assertTrue(enElDetalle.any { it == "sigue-vivo" }, "lo vivo sigue estando")
    }

    // ── La cascada del monto sobre un par ASIMÉTRICO ───────────────────────────

    /**
     * Escribe una pata a mano, con `transferId` y monto propio.
     *
     * A mano y no con `payInstallment` porque esa delega en el server y no espeja nada: para
     * ejercitar la cascada local hace falta el par ya presente en la base del teléfono, que es
     * como queda después de la primera lectura con red.
     */
    private fun pataLocal(
        id: String,
        accountId: String,
        tipo: TransactionType,
        monto: Long,
        transferId: String,
        categoria: String = CUOTA_CATEGORY,
        /** Lo que esa cuota NO amortizó, tal como baja del server en la pata de la deuda. */
        noAmortiza: Long? = null,
    ) = db.financialEventQueries.insert(
        id, accountId, tipo.name, monto, categoria, "Pata", null,
        1_788_000_000_000L, "MANUAL", null, "RECONCILED", null, testUserId, transferId, null, noAmortiza,
    )

    /**
     * **La mitad más cara de la asimetría: el saldo local es un acumulado mantenido a mano.**
     *
     * En el teléfono, `account.balance` no se deriva de los eventos como en el server — se suma y
     * se resta a mano en cada escritura. Así que si la cascada le copiara `montoNuevo` a la pata de
     * la deuda, no solo la fila quedaría mal: el saldo del crédito que el dueño ve arriba de la
     * pantalla quedaría mal por los intereses, y sin red no habría quién lo corrigiera.
     *
     * El interés del mes ($2.481.318) es un hecho ya ocurrido y no cambia porque él corrija lo que
     * pagó: la pata de la deuda se mueve por la **diferencia**, no por el monto entero.
     */
    @Test
    fun corregir_una_cuota_mueve_la_pata_de_la_deuda_por_la_DIFERENCIA() = runBlocking {
        // Los saldos arrancan **con la cuota ya aplicada**, que es el estado en el que el teléfono
        // encuentra el par: $20.000.000 − $4.215.223 en la cuenta, $177.200.000 − $1.733.905 en la
        // deuda. Las patas se escriben después, sin volver a mover el acumulado.
        repo.createAccount(Account("acc-ahorros-c", "Bancolombia", AccountType.SAVINGS, 15_784_777L))
        repo.createAccount(Account("acc-carro-c", "Vehículo 4083", AccountType.LOAN, 175_466_095L))
        pataLocal("ev-cuota-dinero", "acc-ahorros-c", TransactionType.EXPENSE, 4_215_223L, "tr-cuota")
        pataLocal(
            "ev-cuota-capital", "acc-carro-c", TransactionType.INCOME, 1_733_905L, "tr-cuota",
            noAmortiza = 2_481_318L,
        )

        repo.updateEvent("ev-cuota-dinero", EdicionDeMovimiento(amount = 4_500_000L))

        val patas = repo.getEvents().filter { it.transferId == "tr-cuota" }.associateBy { it.id }
        assertEquals(4_500_000L, patas.getValue("ev-cuota-dinero").amount)
        assertEquals(
            1_733_905L + (4_500_000L - 4_215_223L),
            patas.getValue("ev-cuota-capital").amount,
            "el interés del mes no cambia: solo se mueve lo que abona a capital",
        )
        // Y el acumulado local acompaña: de la cuenta salen los $4.500.000 corregidos, y a la deuda
        // le entran solo los $2.018.682 de capital. Copiar habría dejado el crédito en
        // $172.700.000 — $2,4 millones que sigue debiendo, evaporados en el teléfono.
        assertEquals(20_000_000L - 4_500_000L, repo.getAccount("acc-ahorros-c").balance)
        assertEquals(177_200_000L - 2_018_682L, repo.getAccount("acc-carro-c").balance)
    }

    /**
     * La otra mitad: un traspaso **sigue siendo simétrico**, y por el mismo camino de código. Sin
     * este test, «mover por la diferencia» podría haber roto en silencio lo que ya funcionaba.
     */
    @Test
    fun corregir_una_pata_de_traspaso_sigue_copiando_el_monto() = runBlocking {
        // Mismo montaje que arriba: saldos con el traspaso de $2.000.000 ya aplicado.
        repo.createAccount(Account("acc-tr-o", "Bancolombia", AccountType.SAVINGS, 3_000_000L))
        repo.createAccount(Account("acc-tr-d", "Nu", AccountType.SAVINGS, 2_000_000L))
        pataLocal("ev-tr-out", "acc-tr-o", TransactionType.EXPENSE, 2_000_000L, "tr-simetrico", TRANSFER_CATEGORY)
        pataLocal("ev-tr-in", "acc-tr-d", TransactionType.INCOME, 2_000_000L, "tr-simetrico", TRANSFER_CATEGORY)

        repo.updateEvent("ev-tr-out", EdicionDeMovimiento(amount = 1_500_000L))

        val patas = repo.getEvents().filter { it.transferId == "tr-simetrico" }.associateBy { it.id }
        assertEquals(1_500_000L, patas.getValue("ev-tr-out").amount)
        assertEquals(1_500_000L, patas.getValue("ev-tr-in").amount, "las dos mitades son la misma plata")
        assertEquals(5_000_000L - 1_500_000L, repo.getAccount("acc-tr-o").balance)
        assertEquals(1_500_000L, repo.getAccount("acc-tr-d").balance)
    }

    /**
     * **Ida y vuelta a través del piso en cero, sin señal.**
     *
     * El defecto que el interés guardado cerró: bajar la cuota por debajo del interés clampa el
     * capital a 0 (bien), pero con la regla de la diferencia la vuelta atrás no recuperaba el
     * capital original — quedaba $72.705 más alto, o sea $72.705 de deuda desaparecidos, en el
     * teléfono y para siempre (acá el saldo es un acumulado, nadie lo vuelve a derivar).
     *
     * Y de paso confirma el otro defecto: la pata de la deuda nace en $0 y NO se puede corregir
     * directamente (`validarEdicionDeMovimiento` rechaza montos <= 0), así que el único camino es
     * esta cascada desde la pata del dinero. Con el arreglo, ese camino la devuelve exacta.
     */
    @Test
    fun corregir_hacia_abajo_y_arrepentirse_devuelve_el_capital_exacto() = runBlocking {
        // Cuota del ·9695: $1.286.548, capital $813.843, $472.705 que no amortizan.
        repo.createAccount(Account("acc-ah-rt", "Bancolombia", AccountType.SAVINGS, 8_713_452L))
        repo.createAccount(Account("acc-9695-rt", "Libre inversión", AccountType.LOAN, 40_280_062L))
        pataLocal("ev-rt-dinero", "acc-ah-rt", TransactionType.EXPENSE, 1_286_548L, "tr-rt")
        pataLocal(
            "ev-rt-capital", "acc-9695-rt", TransactionType.INCOME, 813_843L, "tr-rt",
            noAmortiza = 472_705L,
        )

        repo.updateEvent("ev-rt-dinero", EdicionDeMovimiento(amount = 400_000L))
        val enElPiso = repo.getEvents().first { it.id == "ev-rt-capital" }
        assertEquals(0L, enElPiso.amount, "400.000 no cubren los 472.705 que no amortizan")

        // Se arrepiente y vuelve al monto real.
        repo.updateEvent("ev-rt-dinero", EdicionDeMovimiento(amount = 1_286_548L))

        val patas = repo.getEvents().filter { it.transferId == "tr-rt" }.associateBy { it.id }
        assertEquals(1_286_548L, patas.getValue("ev-rt-dinero").amount)
        assertEquals(
            813_843L,
            patas.getValue("ev-rt-capital").amount,
            "la regla de la diferencia devolvía 886.548 y borraba \$72.705 de deuda",
        )
        // Y el acumulado local vuelve exactamente a donde estaba.
        assertEquals(10_000_000L - 1_286_548L, repo.getAccount("acc-ah-rt").balance)
        assertEquals(41_093_905L - 813_843L, repo.getAccount("acc-9695-rt").balance)
    }
}
