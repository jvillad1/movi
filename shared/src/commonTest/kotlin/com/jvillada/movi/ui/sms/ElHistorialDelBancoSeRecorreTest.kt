package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 67 · «Mensajes del banco» se puede recorrer
 *
 * Una lista compacta por día, chips Pendientes · Confirmados · Ignorados, el comercio de título y cada
 * fila abre lo suyo.
 */
class ElHistorialDelBancoSeRecorreTest {

    private val hoy = LocalDate.parse("2026-10-29")
    private val texto = "Bancolombia: Pagaste \$980.000,00 a Banco de Occidente S A ATH desde tu producto 4057"

    private fun sms(id: String, time: String, estado: String, comercio: String? = "Banco de Occidente S A ATH", eventoId: String? = null) =
        SmsMessage(id = id, time = time, bank = "85540", text = texto, state = estado, det = "", comercio = comercio, monto = 980_000.0, eventoId = eventoId)

    private val mensajes = listOf(
        sms("a", "2026-10-29 09:10", SMS_STATE_PENDING),
        sms("b", "2026-10-28 23:45", SMS_STATE_CONFIRMED, eventoId = "ev-1"),
        sms("c", "2026-10-28 20:00", SMS_STATE_IGNORED),
        sms("d", "2026-10-20 10:00", SMS_STATE_CONFIRMED),
    )

    @Test
    fun `agrupado por dia, el mas nuevo primero`() {
        val dias = historialPorDia(mensajes, FiltroDelHistorial.TODOS, hoy)
        assertEquals(listOf("Hoy", "Ayer", "20 de octubre"), dias.map { it.titulo })
        assertEquals(listOf("b", "c"), dias[1].mensajes.map { it.id })
    }

    @Test
    fun `los chips filtran por estado`() {
        assertEquals(listOf("a"), historialPorDia(mensajes, FiltroDelHistorial.PENDIENTES, hoy).flatMap { d -> d.mensajes.map { it.id } })
        assertEquals(listOf("b", "d"), historialPorDia(mensajes, FiltroDelHistorial.CONFIRMADOS, hoy).flatMap { d -> d.mensajes.map { it.id } })
        assertEquals(listOf("c"), historialPorDia(mensajes, FiltroDelHistorial.IGNORADOS, hoy).flatMap { d -> d.mensajes.map { it.id } })
    }

    @Test
    fun `el comercio va de titulo y el banco abajo, con la hora`() {
        val fila = filaDelHistorial(mensajes[1], hoy, conEstado = true)
        assertEquals("Banco de Occidente S A ATH", fila.titulo)
        assertEquals(980_000L, fila.monto)
        assertEquals(true, fila.subtitulo.startsWith("Bancolombia · 11:45"), fila.subtitulo)
        assertEquals(true, filaDelHistorial(mensajes[0], hoy, conEstado = true).subtitulo.endsWith("Pendiente"))
        // Sin comercio, el banco de título, como antes.
        assertEquals("Bancolombia", filaDelHistorial(sms("x", "2026-10-29 09:10", SMS_STATE_PENDING, comercio = null), hoy, conEstado = false).titulo)
    }

    @Test
    fun `tocar un confirmado abre su movimiento, un pendiente Revisar aviso`() {
        assertEquals(AlTocarElMensaje.Revisar("a"), alTocarElMensaje(mensajes[0]))
        assertEquals(AlTocarElMensaje.Movimiento("ev-1"), alTocarElMensaje(mensajes[1]))
        assertEquals(AlTocarElMensaje.VerElTexto, alTocarElMensaje(mensajes[2]))
        assertEquals(AlTocarElMensaje.VerElTexto, alTocarElMensaje(mensajes[3]), "sin movimiento vivo, el texto")
    }
}
