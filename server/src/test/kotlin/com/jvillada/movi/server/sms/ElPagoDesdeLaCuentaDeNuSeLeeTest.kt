package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.conLoQueMoviRecuerda
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **El pago que sale de la cuenta de ahorros de Nu es un gasto** (3-oct-2026).
 *
 * El dueño pagó la medicina prepagada desde su cuenta de Nu, la notificación llegó a «Por revisar»
 * y Reconciliar le dijo «Este mensaje no trae un movimiento para anotar»: con Nu solo se aceptaba
 * la compra aprobada, el pago a la tarjeta y la plata que llega. Ahora el pago aprobado también.
 *
 * Y el recordatorio de ese mismo pago («Tienes un pago por … Completa tu pago…») sigue sin serlo:
 * habla de plata, pero no se movió nada.
 */
class ElPagoDesdeLaCuentaDeNuSeLeeTest {

    private val deNu = "Notificación · Nu"

    /** Como lo escribe Nu, en una línea (como lo cita el dueño) y en dos (como lo arma el teléfono). */
    private val pagoAprobado =
        "Nu: Pago aprobado por \$139.154,40 Pagaste en Coomeva Medicina Prepagada S.A. con tu cuenta de ahorros. Si tienes dudas contáctanos vía chat."
    private val pagoAprobadoEnDosLineas =
        "Nu: Pago aprobado por \$139.154,40\nPagaste en Coomeva Medicina Prepagada S.A. con tu cuenta de ahorros. Si tienes dudas contáctanos via chat."

    @Test
    fun `el pago aprobado desde la cuenta de Nu es un gasto con su monto y a quien se le pago`() {
        for (texto in listOf(pagoAprobado, pagoAprobadoEnDosLineas)) {
            val leido = assertNotNull(parseSms(texto, deNu), "no se leyó: $texto")
            assertEquals(139_154.40, leido.amount)
            assertEquals("COP", leido.currency)
            assertEquals(TransactionType.EXPENSE, leido.type)
            assertEquals("Coomeva Medicina Prepagada S.A.", leido.merchant)
            // Un pago a un tercero, no el abono a la tarjeta de Nu.
            assertNotEquals(CARD_PAYMENT_CATEGORY, leido.category)
        }
    }

    @Test
    fun `otro comercio con la misma forma tambien se lee`() {
        val leido = assertNotNull(
            parseSms("Nu: Pago aprobado por \$52.300,00 Pagaste en Empresa de Energia ABC con tu cuenta de ahorros.", deNu),
        )
        assertEquals(52_300.0, leido.amount)
        assertEquals("Empresa de Energia ABC", leido.merchant)
    }

    @Test
    fun `pasa por la memoria de categorias como todo lo demas`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(AnotacionPasada("Coomeva Medicina Prepagada S.A.", "Coomeva Medicina Prepagada S.A.", "Salud", 10)),
        )
        val propuesta = conLoQueMoviRecuerda(assertNotNull(parseSms(pagoAprobado, deNu)), memoria)
        assertEquals("Salud", propuesta.category)
        assertEquals("Así lo anotaste la última vez", propuesta.aprendidoDe)
    }

    @Test
    fun `el recordatorio del mismo pago sigue sin ser un movimiento`() {
        assertNull(
            parseSms(
                "Tienes un pago por \$125.400,00 de Medicina Prepagada Ejemplo S.A.: Completa tu pago de forma fácil y segura en tu app Nu.",
                deNu,
            ),
        )
    }

    @Test
    fun `lo de Nu que no es movimiento sigue afuera aunque diga pago`() {
        val avisos = listOf(
            "Recuerda tu pago mínimo: Paga al menos \$85.000,00 antes de la fecha límite.",
            "Tu factura está lista: el total a pagar es \$1.250.300,00 y la fecha límite es el 5 de octubre.",
            "Tu Cajita creció: Ganaste \$1.234,56 en rendimientos esta semana.",
        )
        for (texto in avisos) assertNull(parseSms(texto, deNu), "se leyó como movimiento: $texto")
    }
}
