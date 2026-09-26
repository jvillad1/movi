package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.conLosAvisosParecidos
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Dos avisos que parecen el mismo pago
 *
 * El 25-sep el dueño pagó $15.100 con su tarjeta Glim y le llegaron dos avisos del mismo pago, uno
 * de Google Wallet y otro de la app de Glim. Los aprobó los dos con seis segundos de diferencia y
 * quedaron dos movimientos. Estos textos son los reales.
 */
class AvisosDelMismoPagoTest {

    /** Mucho después de todos los mensajes: `momentoDelSms` no recorta ninguno. */
    private val ahora = 1_900_000_000_000L

    private fun sms(
        id: String,
        bank: String,
        text: String,
        time: String = "2026-09-25 09:15",
        state: String = SMS_STATE_PENDING,
    ) = SmsMessage(id = id, time = time, bank = bank, text = text, state = state, det = "")

    private val wallet = sms("w", "Notificación · Google Wallet", "TOSTAO CAFE Y PAN VISC: COP15,100 with Glim ••3037")
    private val glim = sms(
        "g", "Notificación · Glim",
        "¡Usaste tus beneficios!: Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim el 25/09/2026 " +
            "a las 14:15 en TOSTAO CAFE Y PAN.",
    )

    private fun parecidos(vararg mensajes: SmsMessage): Map<String, String?> =
        conLosAvisosParecidos(mensajes.toList(), ahora).associate { it.id to it.parecidoA }

    @Test
    fun `los dos avisos reales del pago con la Glim se apuntan el uno al otro`() {
        assertEquals(mapOf("w" to "g", "g" to "w"), parecidos(wallet, glim))
    }

    @Test
    fun `no cambia el orden ni ningun otro campo`() {
        val entrada = listOf(glim, wallet)
        val salida = conLosAvisosParecidos(entrada, ahora)
        assertEquals(entrada.map { it.id }, salida.map { it.id })
        assertEquals(entrada, salida.map { it.copy(parecidoA = null) })
    }

    @Test
    fun `dos cafes iguales del mismo origen no son el mismo pago`() {
        val otroCafe = wallet.copy(id = "w2", time = "2026-09-25 09:17")
        assertEquals(mapOf("w" to null, "w2" to null), parecidos(wallet, otroCafe))
    }

    @Test
    fun `con otro monto no se parecen`() {
        val otroMonto = glim.copy(text = glim.text.replace("15.100,00", "15.200,00"))
        assertEquals(mapOf("w" to null, "g" to null), parecidos(wallet, otroMonto))
    }

    @Test
    fun `a mas de diez minutos no se parecen`() {
        assertEquals(mapOf("w" to null, "g" to null), parecidos(wallet, glim.copy(time = "2026-09-25 09:26")))
        // Y a diez justos, sí.
        assertEquals(mapOf("w" to "g", "g" to "w"), parecidos(wallet, glim.copy(time = "2026-09-25 09:25")))
    }

    @Test
    fun `el otro puede estar confirmado o ignorado, pero solo el pendiente lleva la marca`() {
        val confirmado = glim.copy(state = SMS_STATE_CONFIRMED)
        assertEquals(mapOf("w" to "g", "g" to null), parecidos(wallet, confirmado))
        val ignorado = glim.copy(state = SMS_STATE_IGNORED)
        assertEquals(mapOf("w" to "g", "g" to null), parecidos(wallet, ignorado))
    }

    @Test
    fun `dos confirmados no llevan marca`() {
        assertEquals(
            mapOf("w" to null, "g" to null),
            parecidos(wallet.copy(state = SMS_STATE_CONFIRMED), glim.copy(state = SMS_STATE_CONFIRMED)),
        )
    }

    @Test
    fun `un mensaje que no es un movimiento no se parece a nada`() {
        val atajo = sms("a", "Notificación · Google Wallet", "Set up a shortcut to pay: double press the power button")
        assertNull(parecidos(atajo, glim)["a"])
        assertNull(parecidos(atajo, glim)["g"])
    }

    @Test
    fun `entre varios parecidos apunta al mas cercano`() {
        val sms1 = sms("s1", "85540", "Bancolombia: Compraste \$15.100,00 en TOSTAO con tu T.Deb *4057", time = "2026-09-25 09:22")
        val sms2 = sms1.copy(id = "s2", bank = "Correo · Bancolombia", time = "2026-09-25 09:16")
        assertEquals("s2", parecidos(wallet, sms1, sms2)["w"])
    }
}
