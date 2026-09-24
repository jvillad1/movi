package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Qué cuenta como «por revisar», y cuánto suma** — la parte sin dibujo de la bandeja de la ola
 * C. El renglón de Movimientos y la bandeja llaman a estas mismas funciones: si el número de uno y
 * lo que se ve en la otra difirieran, el dueño tocaría «3 por revisar» y encontraría dos cosas.
 */
class PorRevisarLogicaTest {

    private var n = 0

    private fun evento(estado: ReconciliationStatus) = FinancialEvent(
        id = "ev_${n++}",
        accountId = "acc_1",
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = "Carnes y Legumbres Santa Elena",
        timestamp = 1_757_000_000_000L,
        reconciliationStatus = estado,
    )

    private fun dia(vararg eventos: FinancialEvent) =
        EventDay(date = "2026-09-09", total = 0L, items = eventos.toList())

    private fun sms(id: String, estado: String, cuando: String = "2026-09-03 07:15") = SmsMessage(
        id = id, time = cuando, bank = "Bancolombia", text = "Compra \$28.500", state = estado, det = "Uber",
    )

    @Test
    fun `solo los mensajes pendientes, del mas nuevo al mas viejo`() {
        val pendientes = mensajesPorRevisar(
            listOf(
                sms("viejo", SMS_STATE_PENDING, "2026-08-01 10:00"),
                sms("ok", SMS_STATE_CONFIRMED),
                sms("nuevo", SMS_STATE_PENDING, "2026-09-10 08:00"),
                sms("no", SMS_STATE_IGNORED),
            ),
        )
        assertEquals(listOf("nuevo", "viejo"), pendientes.map { it.id })
    }

    /** Lo que falta confirmar falta igual aunque sea de otro día: se miran todos. */
    @Test
    fun `entraron solos se cuentan en todos los dias, no solo en uno`() {
        val dias = listOf(
            dia(evento(ReconciliationStatus.UNCONFIRMED), evento(ReconciliationStatus.RECONCILED)),
            dia(evento(ReconciliationStatus.UNCONFIRMED)),
        )
        assertEquals(2, entraronSolos(dias).size)
        assertTrue(entraronSolos(listOf(dia(evento(ReconciliationStatus.RECONCILED)))).isEmpty())
    }

    @Test
    fun `el renglon suma las tres fuentes`() {
        val cuantos = cuantosPorRevisar(
            mensajes = listOf(sms("s1", SMS_STATE_PENDING), sms("s2", SMS_STATE_CONFIRMED)),
            dias = listOf(dia(evento(ReconciliationStatus.UNCONFIRMED), evento(ReconciliationStatus.UNCONFIRMED))),
            candidatos = listOf(evento(ReconciliationStatus.RECONCILED)),
        )
        assertEquals(4, cuantos)
        assertEquals("4 por revisar", textoDePorRevisar(cuantos))
        assertEquals("1 por revisar", textoDePorRevisar(1))
    }

    /**
     * **Se cuenta lo que se dibuja.** Un traspaso son dos eventos —la salida y la entrada— pero un
     * solo renglón en la bandeja (ver `collapseTransfers`); si el número los contara por separado,
     * «2 por revisar» abriría una bandeja con una sola cosa.
     */
    @Test
    fun `un traspaso que entro solo cuenta como un renglon`() {
        val salida = evento(ReconciliationStatus.UNCONFIRMED).copy(transferId = "t1", type = TransactionType.EXPENSE)
        val entrada = evento(ReconciliationStatus.UNCONFIRMED).copy(transferId = "t1", type = TransactionType.INCOME)
        val suelto = evento(ReconciliationStatus.UNCONFIRMED)
        val dias = listOf(dia(salida, entrada, suelto))

        assertEquals(2, renglonesQueEntraronSolos(dias).size)
        assertEquals(2, cuantosPorRevisar(mensajes = emptyList(), dias = dias, candidatos = emptyList()))
    }

    /** Una fuente que no contestó no inventa pendientes: suma cero, y la bandeja dice cuál falló. */
    @Test
    fun `una fuente sin leer no suma`() {
        assertEquals(0, cuantosPorRevisar(null, null, null))
        assertEquals(1, cuantosPorRevisar(null, listOf(dia(evento(ReconciliationStatus.UNCONFIRMED))), null))
    }

    /** «Todo al día» es una afirmación: solo con las tres lecturas contestadas y vacías. */
    @Test
    fun `todo al dia solo con las tres fuentes leidas y vacias`() {
        val vacio = emptyList<FinancialEvent>()
        assertTrue(bandejaAlDia(listOf(sms("s", SMS_STATE_CONFIRMED)), listOf(dia(evento(ReconciliationStatus.RECONCILED))), vacio))
        assertFalse(bandejaAlDia(null, emptyList(), vacio), "sin los mensajes no se sabe")
        assertFalse(bandejaAlDia(emptyList(), null, vacio), "sin los movimientos no se sabe")
        assertFalse(bandejaAlDia(emptyList(), emptyList(), null), "sin los candidatos no se sabe")
        assertFalse(bandejaAlDia(listOf(sms("s", SMS_STATE_PENDING)), emptyList(), vacio))
    }

    /** La misma condición que el aviso del Hoy: nunca llegó nada, y el dueño no lo silenció. */
    @Test
    fun `el aviso de la captura usa la misma regla que el Hoy`() {
        assertEquals("Movi nunca ha recibido un mensaje de tu banco", avisoDeCapturaEnLaBandeja(emptyList(), silenciada = false))
        assertNull(avisoDeCapturaEnLaBandeja(emptyList(), silenciada = true))
        assertNull(avisoDeCapturaEnLaBandeja(listOf(sms("s", SMS_STATE_CONFIRMED)), silenciada = false))
        // Sin la lista o sin el perfil no se afirma nada.
        assertNull(avisoDeCapturaEnLaBandeja(null, silenciada = false))
        assertNull(avisoDeCapturaEnLaBandeja(emptyList(), silenciada = null))
    }
}
