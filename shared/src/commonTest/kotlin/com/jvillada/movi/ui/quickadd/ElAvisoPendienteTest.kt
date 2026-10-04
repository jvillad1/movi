package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.AvisoPendienteParecido
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.momentoDelSms
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «¿Es el aviso de hace 2 h?» — lo que «Agregar» pregunta y lo que guarda cuando el dueño dice que sí.
 * Ver `ElAvisoPendiente.kt`.
 */
class ElAvisoPendienteTest {

    private val aviso = AvisoPendienteParecido(
        id = "sms_rt_1",
        time = "2026-09-29 12:45",
        bank = "85540",
        text = "Bancolombia: JUAN pagaste \$22,000.00 por codigo QR desde tu cuenta *8133 a la llave 0047142708 el 29/09/2026 a las 12:45.",
        monto = 22_000.0,
        tipo = TransactionType.EXPENSE,
        descripcion = "Pago QR · llave 0047142708",
        comercio = "Pago QR · llave 0047142708",
    )
    private val llego = momentoDelSms(aviso.time, Long.MAX_VALUE)

    @Test
    fun `dice hace cuanto llego`() {
        assertEquals("recién", haceCuantoLlego(llego, llego + 20_000))
        assertEquals("hace 5 min", haceCuantoLlego(llego, llego + 5 * 60_000L))
        assertEquals("hace 2 h", haceCuantoLlego(llego, llego + 2 * 3_600_000L + 10 * 60_000L))
        assertEquals("hace 47 h", haceCuantoLlego(llego, llego + 47 * 3_600_000L))
    }

    @Test
    fun `la pregunta y lo que dice despues de decir que si`() {
        val ahora = llego + 2 * 3_600_000L
        assertEquals("¿Es el aviso de hace 2 h? Pago QR · llave 0047142708", preguntaDelAvisoPendiente(aviso, ahora))
        assertEquals("Va con el aviso de hace 2 h", avisoElegido(aviso, ahora))
    }

    @Test
    fun `con el aviso, el movimiento guarda lo del banco y deja lo que escribio el dueno`() {
        val deLaHoja = FinancialEvent(
            id = "ev_1", accountId = "aho", type = TransactionType.EXPENSE, amount = 22_000,
            category = "Comida", description = "Las Doce", source = EventSource.MANUAL, timestamp = llego + 2 * 3_600_000L,
        )
        val conElAviso = movimientoConElAviso(deLaHoja, aviso, ahora = llego + 2 * 3_600_000L)

        assertEquals("Pago QR · llave 0047142708", conElAviso.merchant, "de acá aprende la memoria")
        assertEquals(aviso.text, conElAviso.rawPayload)
        assertEquals(llego, conElAviso.timestamp, "cuando salió la plata, no cuando se anotó")
        assertEquals(deLaHoja.copy(merchant = conElAviso.merchant, rawPayload = conElAviso.rawPayload, timestamp = llego), conElAviso)
    }
}
