package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.textoDePse
import com.jvillada.movi.server.routes.gruposDelMismoPago
import com.jvillada.movi.server.routes.horaEscritaDelSms
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.server.routes.propuestaDelGrupo
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 107 · dos compras iguales el mismo día son dos; el mismo cobro avisado dos veces, uno
 *
 * El 28-oct el dueño pagó dos veces $8.100 en la Tienda Libertario y Movi tiene una. En producción solo
 * llegó UN aviso de esas dos compras (el de Google Wallet de las 14:41, sin SMS): el otro no pasó del
 * teléfono. Esta prueba fija la regla del server para lo que sí llega: un SMS que escribe su hora y una
 * notificación son el mismo cobro solo cerca de esa hora, y dos avisos del mismo canal nunca lo son.
 * Textos sintéticos con la forma de los reales.
 */
class DosComprasIgualesTest {

    private val ahora = 1_900_000_000_000L

    private fun aviso(id: String, bank: String, text: String, time: String) =
        SmsMessage(id = id, time = time, bank = bank, text = text, state = SMS_STATE_PENDING, det = "")

    private fun delSms(id: String, hora: String) = aviso(
        id, "85540",
        "Bancolombia: Compraste \$8.100,00 en TIENDA LIBERTARIO 93 con tu T.Deb *4057, el 28/10/2026 a las $hora. Si tienes dudas, encuentranos aqui: 6045109095.",
        "2026-10-28 $hora",
    )

    private fun deWallet(id: String, hora: String) =
        aviso(id, "Notificación · Google Wallet", "TIENDA LIBERTARIO 93: COP8,100 with Debito Mastercard ••4057", "2026-10-28 $hora")

    private fun grupos(vararg avisos: SmsMessage): Set<Set<String>> =
        gruposDelMismoPago(avisos.toList(), ahora).map { g -> g.ids.toSet() }.toSet()

    @Test
    fun `el mismo cobro avisado por SMS y por Google Wallet es uno`() {
        assertEquals(setOf(setOf("s1", "w1")), grupos(delSms("s1", "14:41"), deWallet("w1", "14:41")))
        // Wallet un poco tarde (hasta 2 min del SMS) sigue siendo el mismo; a 3 ya son dos.
        assertEquals(setOf(setOf("s1", "w1")), grupos(delSms("s1", "14:41"), deWallet("w1", "14:43")))
        assertEquals(emptySet(), grupos(delSms("s1", "14:41"), deWallet("w1", "14:44")))
    }

    @Test
    fun `dos compras iguales a minutos distintos, cada una con sus dos avisos, son dos`() {
        assertEquals(
            setOf(setOf("s1", "w1"), setOf("s2", "w2")),
            grupos(delSms("s1", "14:35"), deWallet("w1", "14:35"), delSms("s2", "14:41"), deWallet("w2", "14:41")),
        )
    }

    @Test
    fun `dos compras iguales a minutos distintos, una avisada por Wallet y otra por SMS, son dos`() {
        // Antes las juntaba la ventana de 30 min y una de las dos compras se perdía.
        assertEquals(emptySet(), grupos(deWallet("w1", "14:35"), delSms("s2", "14:41")))
        assertEquals(emptySet(), grupos(delSms("s1", "14:35"), deWallet("w2", "14:41")))
    }

    @Test
    fun `dos avisos de Google Wallet iguales nunca son el mismo cobro`() {
        assertEquals(emptySet(), grupos(deWallet("w1", "14:35"), deWallet("w2", "14:41")))
    }

    @Test
    fun `la hora que escribe el SMS se lee en sus dos formas`() {
        assertEquals(momentoDelSms("2026-10-28 14:41", Long.MAX_VALUE), horaEscritaDelSms(delSms("s", "14:41").text))
        assertEquals(
            momentoDelSms("2026-10-25 23:47", Long.MAX_VALUE),
            horaEscritaDelSms("Bancolombia: Pagaste \$138,600.00 a Coomeva Medicina Prepagada S A desde tu producto 8133 el 25/10/2026 23:47:16."),
        )
        assertNull(horaEscritaDelSms("Compra aprobada \$28.500 en Uber BV."))
    }

    @Test
    fun `el caso del 25-oct - el SMS de Coomeva y su correo de PSE dos minutos despues son un pago, y propone el SMS`() {
        val sms = aviso(
            "sms_rt_coomeva", "85784",
            "Bancolombia: Pagaste \$138,600.00 a Coomeva Medicina Prepagada S A desde tu producto 8133 el 25/10/2026 23:47:16. ¿Dudas? Llamanos al 6045109095. Estamos cerca",
            "2026-10-25 23:47",
        )
        val correo = aviso("correo_pse", "Correo · achcolombia.com.co", textoDePse(fecha = "25/10/2026", cus = "771600001"), "2026-10-25 23:49")
        val pago = gruposDelMismoPago(listOf(correo, sms), ahora).single()
        assertEquals(setOf("sms_rt_coomeva", "correo_pse"), pago.ids.toSet())
        assertEquals("sms_rt_coomeva", propuestaDelGrupo(pago.miembros).id, "la propuesta sale del aviso que nombra la cuenta")
    }
}
