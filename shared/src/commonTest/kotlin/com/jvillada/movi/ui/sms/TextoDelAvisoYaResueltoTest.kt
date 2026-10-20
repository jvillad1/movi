package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/** Un aviso apartado o ignorado no dice «ya se confirmó»: dice lo que pasó. */
class TextoDelAvisoYaResueltoTest {

    private fun aviso(estado: String, apartadoPor: String? = null) = SmsMessage(
        id = "sms_1", time = "2026-10-20 09:47", bank = "Notificación · Nu",
        text = "Tienes un pago por \$450.000,00 de Coomeva el 20/10/2026", state = estado, det = "",
        apartadoPor = apartadoPor,
    )

    @Test
    fun `confirmado dice que se confirmo`() {
        assertEquals("Este mensaje ya se confirmó", textoDelAvisoYaResuelto(aviso(SMS_STATE_CONFIRMED)))
    }

    @Test
    fun `apartado por Movi dice por que`() {
        assertEquals(
            "Movi lo apartó: recordatorio de pago",
            textoDelAvisoYaResuelto(aviso(SMS_STATE_IGNORED, apartadoPor = "RECORDATORIO_DE_PAGO")),
        )
    }

    @Test
    fun `ignorado por el dueno lo dice`() {
        assertEquals("Ignoraste este mensaje", textoDelAvisoYaResuelto(aviso(SMS_STATE_IGNORED)))
    }
}
