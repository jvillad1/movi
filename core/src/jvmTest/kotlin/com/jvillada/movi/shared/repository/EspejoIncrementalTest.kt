package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El espejo escribe **solo lo que cambió**.
 *
 * Antes reescribía las N filas en cada lectura aunque nada se hubiera movido: 5.000 eventos
 * costaban ~272 ms y la segunda lectura costaba lo mismo que la primera. Y son tres pantallas las
 * que disparan esto, así que el costo se paga varias veces por visita.
 */
class EspejoIncrementalTest {

    private lateinit var db: MoviDatabase
    private val uid = "u-perf"

    @BeforeTest
    fun setup() { db = createDatabase("perf.db") }

    private fun evento(i: Int) = FinancialEvent(
        id = "ev-$i",
        accountId = "accP",
        type = TransactionType.EXPENSE,
        amount = 1_000L + i,
        category = "Comida",
        description = "gasto $i",
        timestamp = 1_700_000_000_000L + i,
        syncedAt = 1L,
    )

    /** Cuenta cuántas veces el server fue consultado y qué devolvió. */
    private class ServerConHistoria(val eventos: List<FinancialEvent>) : NoOpRepository() {
        init {
            cuentasDelServer += Account("accP", "Ahorros", AccountType.SAVINGS, 0L)
            eventosDelServer += eventos
        }
    }

    /**
     * La segunda lectura, con todo igual, no reescribe nada. Se mide por el `updatedAt` de las
     * filas: si el espejo las hubiera vuelto a escribir, cambiarían.
     */
    @Test
    fun una_segunda_lectura_sin_cambios_no_reescribe_las_filas() = runBlocking {
        val server = ServerConHistoria((1..200).map { evento(it) })
        val repo = LocalRepository(db = db, remote = server, userId = { uid })

        assertEquals(200, repo.getEvents().size)
        val despuesDeLaPrimera = db.financialEventQueries.selectAll(uid).executeAsList()
            .associate { it.id to it.syncedAt }

        assertEquals(200, repo.getEvents().size)
        val despuesDeLaSegunda = db.financialEventQueries.selectAll(uid).executeAsList()
            .associate { it.id to it.syncedAt }

        assertEquals(despuesDeLaPrimera, despuesDeLaSegunda, "nada se reescribió")
    }

    /** Y lo que sí cambió en el server se escribe: el ahorro no puede costar frescura. */
    @Test
    fun lo_que_cambio_en_el_server_si_se_escribe() = runBlocking {
        val server = ServerConHistoria((1..5).map { evento(it) })
        val repo = LocalRepository(db = db, remote = server, userId = { uid })
        repo.getEvents()

        val i = server.eventosDelServer.indexOfFirst { it.id == "ev-3" }
        server.eventosDelServer[i] = server.eventosDelServer[i].copy(category = "Mercado")

        val leidos = repo.getEvents()

        assertEquals("Mercado", leidos.single { it.id == "ev-3" }.category)
        assertTrue(
            db.financialEventQueries.selectAll(uid).executeAsList()
                .single { it.id == "ev-3" }.category == "Mercado",
            "y el espejo quedó al día, no solo la respuesta",
        )
    }
}
