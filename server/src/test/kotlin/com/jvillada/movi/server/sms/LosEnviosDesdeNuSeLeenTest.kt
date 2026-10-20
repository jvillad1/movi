package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Lo que se envía desde la cuenta de Nu a otra persona es un gasto** (20-oct-2026).
 *
 * Con Nu solo se acepta lo que tiene «la forma de un movimiento», y los dos avisos de un envío no
 * la tenían: Reconciliar decía «Este mensaje no trae un movimiento para anotar» y no dejaba
 * confirmar. Los textos son los reales de producción (17, 18 y 20 de octubre).
 */
class LosEnviosDesdeNuSeLeenTest {

    private val deNu = "Notificación · Nu"

    @Test
    fun `acabas de enviar se lee con su monto y a quien`() {
        val texto = "Nu: Acabas de enviar \$1.000,00 💰 \nSe los enviaste a Carolina R. Si no reconoces este envío, no dudes en contactarte con nosotros."
        val leido = assertNotNull(parseSms(texto, deNu))
        assertEquals(1_000.0, leido.amount)
        assertEquals("COP", leido.currency)
        assertEquals(TransactionType.EXPENSE, leido.type)
        assertEquals("Carolina R", leido.merchant)
        assertNotEquals(CARD_PAYMENT_CATEGORY, leido.category)
    }

    @Test
    fun `enviaste le enviaste a se lee con su monto y a quien`() {
        val uno = assertNotNull(
            parseSms("Enviaste \$1.200.000,00: Le enviaste a HEN**** JOS*** ROJ**** ART****** en su cuenta de Bancolombia.", deNu),
        )
        assertEquals(1_200_000.0, uno.amount)
        assertEquals(TransactionType.EXPENSE, uno.type)
        assertEquals("HEN**** JOS*** ROJ**** ART******", uno.merchant)

        val dos = assertNotNull(
            parseSms("Enviaste \$1.000.000,00: Le enviaste a JOS*** TOM**** MON****** BET******* en su cuenta de Bancolombia.", deNu),
        )
        assertEquals(1_000_000.0, dos.amount)
        assertEquals("JOS*** TOM**** MON****** BET*******", dos.merchant)
    }

    @Test
    fun `lo que no es un envio de Nu sigue sin serlo`() {
        assertNull(parseSms("Juan Camilo, tu árbol sigue sin adornos: Invita a alguien a Nu y empieza a ganar \$50.000", deNu))
        assertNull(parseSms("Tienes un pago por \$450.000,00 de Coomeva Medicina Prepagada S.A.: Completa tu pago antes del 5", deNu))
    }
}
