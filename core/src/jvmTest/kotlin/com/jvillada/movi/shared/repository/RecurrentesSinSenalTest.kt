package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Los recurrentes son **avisos de vencimiento**: sin señal el dueño veía una pantalla vacía justo
 * cuando menos sirve. Ahora ve lo último que Movi supo.
 */
class RecurrentesSinSenalTest {

    private lateinit var db: MoviDatabase
    private val uid = "user-cache"

    private val regla = RecurringRule(
        id = "rr-1",
        name = "Gimnasio",
        category = "Gimnasio",
        amount = 180_000L,
        dayOfMonth = 25,
        type = TransactionType.EXPENSE,
    )

    /** Un server que contesta una vez y después se cae, como una conexión que se pierde. */
    private class ServerIntermitente(
        private val reglas: List<RecurringRule>,
    ) : NoOpRepository() {
        var caido = false
        override suspend fun getRecurringRules(): List<RecurringRule> {
            if (caido) throw RuntimeException("sin señal")
            return reglas
        }
    }

    @BeforeTest
    fun setup() { db = createDatabase("cache.db") }

    private fun repo(remote: NoOpRepository) =
        LocalRepository(db = db, remote = remote, userId = { uid })

    @Test
    fun sin_senal_se_ven_los_recurrentes_de_la_ultima_vez() = runBlocking {
        val server = ServerIntermitente(listOf(regla))
        val r = repo(server)

        assertEquals(listOf("rr-1"), r.getRecurringRules().map { it.id }, "con señal, lo del server")

        server.caido = true
        assertEquals(listOf("rr-1"), r.getRecurringRules().map { it.id }, "sin señal, lo último que se supo")
    }

    /**
     * Sin nada guardado se propaga el error: contestar «no tienes recurrentes» sin haber podido
     * preguntar es una afirmación sin respaldo — el mismo criterio que `getAccounts`.
     */
    @Test
    fun sin_senal_y_sin_cache_se_propaga_el_error() = runBlocking {
        val server = ServerIntermitente(emptyList()).also { it.caido = true }

        assertFailsWith<RuntimeException> { repo(server).getRecurringRules() }
        Unit
    }

    /**
     * Una respuesta buena pisa el caché **aunque venga vacía**: si el dueño borró todos sus
     * recurrentes, la lista vacía es la verdad y no puede quedar tapada por la anterior.
     */
    @Test
    fun una_lista_vacia_del_server_tambien_se_guarda() = runBlocking {
        val conRegla = ServerIntermitente(listOf(regla))
        repo(conRegla).getRecurringRules()

        val vacio = ServerIntermitente(emptyList())
        assertTrue(repo(vacio).getRecurringRules().isEmpty(), "el server dijo que no hay: eso vale")

        vacio.caido = true
        assertTrue(repo(vacio).getRecurringRules().isEmpty(), "y sin señal sigue diciendo que no hay")
    }
}
