package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class ElMismoPagoTest {

    private fun aviso(bank: String, time: String) =
        SmsMessage(id = "x", time = time, bank = bank, text = "", state = SMS_STATE_PENDING, det = "")

    @Test
    fun `dice el origen legible y la hora del otro aviso`() {
        assertEquals(
            "Parece el mismo pago que el aviso de Google Wallet de las 9:15 a. m.",
            avisoDelMismoPago(aviso("Notificación · Google Wallet", "2026-09-25 09:15")),
        )
        assertEquals(
            "Parece el mismo pago que el aviso de Bancolombia de las 2:15 p. m.",
            avisoDelMismoPago(aviso("Correo · Bancolombia", "2026-09-25 14:15")),
        )
    }

    @Test
    fun `a la una es de la una`() {
        assertEquals(
            "Parece el mismo pago que el aviso de Glim de la 1:05 p. m.",
            avisoDelMismoPago(aviso("Notificación · Glim", "2026-09-25 13:05")),
        )
    }

    @Test
    fun `un SMS conserva su remitente y una hora ilegible no se inventa`() {
        assertEquals("Parece el mismo pago que el aviso de 85540.", avisoDelMismoPago(aviso("85540", "ayer")))
    }

    @Test
    fun `sin el otro aviso se dice igual, sin detalle`() {
        assertEquals("Parece el mismo pago que otro aviso.", avisoDelMismoPago(null))
    }
}
