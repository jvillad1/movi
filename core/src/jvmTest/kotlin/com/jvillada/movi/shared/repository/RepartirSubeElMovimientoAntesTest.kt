package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # «Pagué yo» en el teléfono: el gasto sube antes de repartirse (vuelta de diseño 24)
 *
 * En Android e iOS el gasto se guarda primero en la base local ([LocalRepository.postEvent]) y lo
 * sube el [SyncEngine] cada 30 segundos. El reparto lo hace el server, que hasta entonces no conoce
 * el movimiento: la hoja caía a «Reintentar el reparto». Ahora [LocalRepository.repartirMovimiento]
 * da antes una vuelta de la misma subida. Se prueba con la base local de verdad y un server falso que
 * se porta como el real: reparte solo lo que ya recibió, y un `POST /api/events` repetido con el mismo
 * id no duplica (upsert).
 */
class RepartirSubeElMovimientoAntesTest {

    private val uid = "user-repartir-24"

    /** El server: sabe de la cuenta «nu», guarda por id y reparte solo lo que conoce. */
    private class ServidorDePrueba : NoOpRepository() {
        var conRed = true
        var rechazoDelGasto: String? = null
        val cuentas = mutableSetOf("nu")
        val movimientos = linkedMapOf<String, FinancialEvent>()
        val llamadas = mutableListOf<String>()

        private fun red() { if (!conRed) throw IOException("sin red") }

        override suspend fun createAccount(account: Account): Account {
            red()
            cuentas += account.id
            llamadas += "cuenta:${account.id}"
            return account
        }

        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            red()
            rechazoDelGasto?.let { throw ApiException(400, it) }
            if (event.accountId !in cuentas) throw ApiException(404, "Account not found")
            llamadas += "subir:${event.id}"
            movimientos[event.id] = event
            return event
        }

        override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
            red()
            llamadas += "repartir:$eventoId"
            val e = movimientos[eventoId] ?: throw ApiException(404, "Movimiento no encontrado")
            return GastoDelEspacio(id = "gde_$eventoId", espacioId = espacioId, monto = e.amount, eventoId = eventoId)
        }
    }

    private val db = createDatabase("repartir-24.db")
    private val server = ServidorDePrueba()
    private val sincronizador = SyncEngine(db, server, { uid })
    private val local = LocalRepository(db, server, { uid }, sincronizador = sincronizador)

    private fun gasto(id: String = "ev_mercado", cuenta: String = "nu") = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = 45_000,
        category = "Mercado", description = "Mercado", timestamp = 1_790_000_000_000,
    )

    private fun filasDe(id: String) =
        db.financialEventQueries.selectAll(uid).executeAsList().count { it.id == id }

    @Test
    fun `con señal pague yo sube el gasto y despues lo reparte`() = runBlocking<Unit> {
        local.postEvent(gasto())
        assertEquals(emptyList(), server.llamadas, "el teléfono guarda primero acá: el server todavía no lo conoce")

        val repartido = local.repartirMovimiento("esp_casa", "ev_mercado")

        assertEquals(listOf("subir:ev_mercado", "repartir:ev_mercado"), server.llamadas, "primero sube, después reparte")
        assertEquals("ev_mercado", repartido.eventoId)
        assertNotNull(db.financialEventQueries.selectById("ev_mercado", uid).executeAsOne().syncedAt, "quedó sellado")
    }

    @Test
    fun `el gasto nunca se guarda dos veces`() = runBlocking<Unit> {
        local.postEvent(gasto())
        local.repartirMovimiento("esp_casa", "ev_mercado")
        // El ciclo de 30 s llega después: la fila ya está sellada y no la vuelve a empujar.
        sincronizador.syncEvents()
        sincronizador.subirLoPendiente()

        assertEquals(1, server.llamadas.count { it == "subir:ev_mercado" }, "un solo POST del gasto")
        assertEquals(1, server.movimientos.size)
        assertEquals(1, filasDe("ev_mercado"), "una sola fila en el teléfono")
    }

    @Test
    fun `sin señal no manda el reparto y el gasto queda guardado una vez`() = runBlocking<Unit> {
        server.conRed = false
        local.postEvent(gasto())

        val e = assertFailsWith<MovimientoSinSubir> { local.repartirMovimiento("esp_casa", "ev_mercado") }
        assertNull(e.rechazo, "no lo rechazó nadie: falta la señal")
        assertEquals(emptyList(), server.llamadas, "sin el gasto allá, el reparto no se pide")
        assertNull(db.financialEventQueries.selectById("ev_mercado", uid).executeAsOne().syncedAt, "sigue pendiente, no perdido")
        assertEquals(1, filasDe("ev_mercado"))

        // Vuelve la señal y se reintenta solo el reparto: sube y reparte, sin duplicar.
        server.conRed = true
        local.repartirMovimiento("esp_casa", "ev_mercado")
        assertEquals(listOf("subir:ev_mercado", "repartir:ev_mercado"), server.llamadas)
        assertEquals(1, filasDe("ev_mercado"))
    }

    @Test
    fun `si el server rechazo el gasto se dice por que y no se reparte`() = runBlocking<Unit> {
        server.rechazoDelGasto = "La categoría no existe"
        local.postEvent(gasto())

        val e = assertFailsWith<MovimientoSinSubir> { local.repartirMovimiento("esp_casa", "ev_mercado") }
        assertEquals("La categoría no existe", e.rechazo)
        assertEquals(emptyList(), server.llamadas.filter { it.startsWith("repartir") })
    }

    @Test
    fun `la cuenta creada sin señal sube antes que su gasto`() = runBlocking<Unit> {
        server.conRed = false
        local.createAccount(Account("acc_efectivo", "Efectivo", AccountType.CASH, 0L))
        local.postEvent(gasto(id = "ev_tienda", cuenta = "acc_efectivo"))
        server.conRed = true

        local.repartirMovimiento("esp_casa", "ev_tienda")

        assertEquals(listOf("cuenta:acc_efectivo", "subir:ev_tienda", "repartir:ev_tienda"), server.llamadas)
    }

    @Test
    fun `un movimiento que ya esta en el server se reparte directo`() = runBlocking<Unit> {
        // No está en este teléfono (vino del server, o de la web): no hay nada que subir.
        server.movimientos["ev_web"] = gasto(id = "ev_web")

        local.repartirMovimiento("esp_casa", "ev_web")

        assertEquals(listOf("repartir:ev_web"), server.llamadas)
    }
}
