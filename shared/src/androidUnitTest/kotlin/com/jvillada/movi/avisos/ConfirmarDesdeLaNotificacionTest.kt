package com.jvillada.movi.avisos

import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Ola 5 · «Confirmar» desde la notificación, por el camino de siempre
 *
 * Contra un repositorio de mentira que se porta como el server: `postEvent` es idempotente por id
 * (`POST /api/events`) y `confirmSmsCon` deja el aviso `confirmed` con su movimiento la primera vez.
 * Lo que se fija:
 *
 * - llama a los MISMOS métodos que la fila de «Por revisar» (`postEvent` + `confirmSmsCon`);
 * - dos toques, un solo movimiento (uno después del otro, o a la vez);
 * - lo que tiene dudas, o cambió desde que se mostró, no se anota;
 * - sin red no se anota y lo dice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfirmarDesdeLaNotificacionTest {

    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000L, "COP")

    private fun aviso(id: String, texto: String) =
        SmsMessage(id = id, time = "2026-10-10 08:10", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")

    private val cafe = aviso("cafe", "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10.")
    private val tarjeta = aviso("tarjeta", "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133 el 10/10/2026.")

    private val lasPropuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("cafe", leido = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "Así lo anotaste 4 veces")),
            PropuestaDelAviso("tarjeta", leido = ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)),
        ),
        categoriasDelDueno = listOf("Comida"),
    )

    private val loQueSeMostroDelCafe = LoQueSeMostro(categoria = "Comida", cuentaId = "acc-8133", monto = 46_000L)

    /** El server, en chico: los movimientos por id y el estado de cada aviso. */
    private inner class Server : RepositorioDePrueba() {
        val movimientos = linkedMapOf<String, FinancialEvent>()
        val avisos = linkedMapOf("cafe" to cafe, "tarjeta" to tarjeta)
        val llamadas = mutableListOf<String>()
        var sinRed = false

        override suspend fun getSmsMessages(): List<SmsMessage> {
            if (sinRed) throw java.io.IOException("sin red")
            return avisos.values.toList()
        }
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = lasPropuestas
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            llamadas += "postEvent"
            movimientos[event.id] = event
            return event
        }
        override suspend fun confirmSmsCon(id: String, eventoId: String) {
            llamadas += "confirmSmsCon"
            val antes = avisos.getValue(id)
            if (antes.state != SMS_STATE_CONFIRMED) avisos[id] = antes.copy(state = SMS_STATE_CONFIRMED, det = eventoId)
        }
    }

    @Test
    fun `confirma con los mismos metodos que la fila de Por revisar y deja el aviso confirmado con su movimiento`() = runBlocking {
        val server = Server()

        val resultado = confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe, ahora = 1_791_000_000_000L)

        assertEquals(ResultadoDeConfirmar.Anotado, resultado)
        assertEquals(listOf("postEvent", "confirmSmsCon"), server.llamadas)
        val movimiento = server.movimientos.values.single()
        assertEquals(idDelMovimientoDeLaNotificacion("cafe"), movimiento.id)
        assertEquals(46_000L, movimiento.amount)
        assertEquals("Comida", movimiento.category)
        assertEquals("acc-8133", movimiento.accountId)
        assertEquals(SMS_STATE_CONFIRMED, server.avisos.getValue("cafe").state)
        assertEquals(movimiento.id, server.avisos.getValue("cafe").det)
    }

    @Test
    fun `dos toques, un solo movimiento`() = runBlocking {
        val server = Server()

        val primero = confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe, ahora = 1_791_000_000_000L)
        val segundo = confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe, ahora = 1_791_000_000_000L)

        assertEquals(ResultadoDeConfirmar.Anotado, primero)
        assertEquals(ResultadoDeConfirmar.YaEstabaAnotado, segundo)
        assertEquals(1, server.movimientos.size)
        assertEquals(1, server.llamadas.count { it == "postEvent" })
    }

    @Test
    fun `dos toques a la vez tambien dejan un solo movimiento`() = runBlocking {
        val server = Server()

        val resultados = (1..2).map {
            async { confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe, ahora = 1_791_000_000_000L) }
        }.awaitAll()

        assertEquals(1, server.movimientos.size)
        assertTrue(ResultadoDeConfirmar.Anotado in resultados)
    }

    @Test
    fun `un pago de tarjeta no se confirma desde la notificacion`() = runBlocking {
        val server = Server()

        val resultado = confirmarDesdeLaNotificacion(
            server, "tarjeta", LoQueSeMostro(categoria = CARD_PAYMENT_CATEGORY, cuentaId = "acc-8133", monto = 386_902L), ahora = 1_791_000_000_000L,
        )

        assertIs<ResultadoDeConfirmar.NoSePudo>(resultado)
        assertTrue(server.movimientos.isEmpty())
        assertEquals(SMS_STATE_PENDING, server.avisos.getValue("tarjeta").state)
    }

    @Test
    fun `si lo que Movi propone cambio desde que se mostro, no se anota`() = runBlocking {
        val server = Server()

        val resultado = confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe.copy(categoria = "Mercado"), ahora = 1_791_000_000_000L)

        assertIs<ResultadoDeConfirmar.NoSePudo>(resultado)
        assertTrue(server.movimientos.isEmpty())
    }

    @Test
    fun `sin red no se anota nada y el aviso sigue pendiente`() = runBlocking {
        val server = Server().apply { sinRed = true }

        val resultado = confirmarDesdeLaNotificacion(server, "cafe", loQueSeMostroDelCafe, ahora = 1_791_000_000_000L)

        assertIs<ResultadoDeConfirmar.NoSePudo>(resultado)
        assertTrue(server.movimientos.isEmpty())
        assertEquals(SMS_STATE_PENDING, server.avisos.getValue("cafe").state)
    }
}
