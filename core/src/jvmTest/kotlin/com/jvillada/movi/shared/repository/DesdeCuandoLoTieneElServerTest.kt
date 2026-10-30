package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Vuelta 82 · ¿desde cuándo el server tiene lo que el dueño anotó en el teléfono?
 *
 * El optimista del héroe del Inicio solo se suelta con una carga que empezó cuando el server ya tenía el
 * movimiento. En el teléfono eso es el `syncedAt` real de la fila (y de sus anulaciones), no «cuando la app
 * lo notó». Y una fila que se anuló antes de subir nunca va a subir (`selectUnsynced` la excluye): cuenta
 * como lista, con efecto neto cero; si no, anotar sin señal y deshacer dejaba dos esperas para siempre.
 */
class DesdeCuandoLoTieneElServerTest {

    private val uid = "user-desde-cuando-82"

    private class Servidor : NoOpRepository() {
        var conRed = true
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (!conRed) throw IOException("sin red")
            return event
        }
    }

    private val db = createDatabase("desde-cuando-82.db")
    private val server = Servidor()
    private val sincronizador = SyncEngine(db, server, { uid })
    private val local = LocalRepository(db, server, { uid }, sincronizador = sincronizador)

    private fun gasto(id: String) = FinancialEvent(
        id = id, accountId = "nu", type = TransactionType.EXPENSE, amount = 12_300,
        category = "Restaurantes", description = "Almuerzo", timestamp = 1_790_000_000_000,
    )

    @Test
    fun `sin subir no lo tiene, y al subir lo tiene desde el sello de la fila`() = runBlocking<Unit> {
        server.conRed = false
        local.postEvent(gasto("ev_a"))
        assertNull(local.desdeCuandoLoTieneElServer("ev_a"), "sin señal, la fila no subió")
        local.subirLoPendienteAhora()
        assertNull(local.desdeCuandoLoTieneElServer("ev_a"), "un intento sin señal no la sella")

        server.conRed = true
        local.subirLoPendienteAhora()
        val sello = db.financialEventQueries.selectById("ev_a", uid).executeAsOne().syncedAt
        assertNotNull(sello)
        assertEquals(sello, local.desdeCuandoLoTieneElServer("ev_a"), "desde el syncedAt real")
    }

    @Test
    fun `anotar sin señal y deshacer antes de subir no deja nada esperando`() = runBlocking<Unit> {
        server.conRed = false
        local.postEvent(gasto("ev_b"))
        local.voidEvent("ev_b", "Deshacer")
        assertEquals(DESDE_LA_ESCRITURA, local.desdeCuandoLoTieneElServer("ev_b"), "anulada antes de subir: nunca sube, efecto neto cero")
    }

    @Test
    fun `un movimiento que no esta en el espejo no espera nada`() = runBlocking<Unit> {
        assertEquals(DESDE_LA_ESCRITURA, local.desdeCuandoLoTieneElServer("no-esta"))
    }
}
