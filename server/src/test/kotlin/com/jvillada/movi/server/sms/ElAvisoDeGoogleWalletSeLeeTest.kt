package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # El aviso de Google Wallet dice el comercio antes de los dos puntos
 *
 * El 25-sep el dueño pagó $15.100 en TOSTAO CAFE Y PAN con su tarjeta Glim y el aviso de Google
 * Wallet se anotó como «Movimiento»: `COMERCIO: COP15,100 with TARJETA ••3037` no tiene ninguna de
 * las frases que el lector conocía («compra en», «en …», «a … desde»). Los textos son los reales.
 */
class ElAvisoDeGoogleWalletSeLeeTest {

    private val WALLET = "Notificación · Google Wallet"

    @Test
    fun `el pago con la Glim por Wallet se lee con el comercio`() {
        val parsed = assertNotNull(parseSms("TOSTAO CAFE Y PAN VISC: COP15,100 with Glim ••3037", WALLET))
        assertEquals(15_100.0, parsed.amount)
        assertEquals("COP", parsed.currency)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("TOSTAO CAFE Y PAN VISC", parsed.merchant)
    }

    @Test
    fun `el comercio va entero, sin quitarle palabras`() {
        val parsed = assertNotNull(parseSms("WOMPI SAS: COP14,641 with Debito Mastercard ••4057", WALLET))
        assertEquals(14_641.0, parsed.amount)
        assertEquals("WOMPI SAS", parsed.merchant)

        val nu = assertNotNull(parseSms("CREPES Y WAFFLES LEMON: COP130,200 with Nu Mastercard Gold ••1336", WALLET))
        assertEquals(130_200.0, nu.amount)
        assertEquals("CREPES Y WAFFLES LEMON", nu.merchant)
    }

    @Test
    fun `un pago en dolares por Wallet se lee en dolares`() {
        val parsed = assertNotNull(parseSms("GOOGLE *YouTube: USD13.99 with Nu Mastercard Gold ••1336", WALLET))
        assertEquals(13.99, parsed.amount)
        assertEquals("USD", parsed.currency)
        assertEquals("GOOGLE *YouTube", parsed.merchant)
    }

    @Test
    fun `sin el origen tambien se lee`() {
        assertEquals("WOMPI SAS", parseSms("WOMPI SAS: COP14,641 with Debito Mastercard ••4057")?.merchant)
    }

    @Test
    fun `el aviso de configurar el atajo no es un movimiento`() {
        assertNull(
            parseSms(
                "Set up a shortcut to pay: Now you can double press the power button to open Google Wallet.",
                WALLET,
            ),
        )
    }

    /** El otro aviso del mismo pago: el de la app de Glim, que ya se leía bien. */
    @Test
    fun `el aviso de la app de Glim sigue leyendo el comercio`() {
        val parsed = assertNotNull(
            parseSms(
                "¡Usaste tus beneficios!: Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim " +
                    "el 25/09/2026 a las 14:15 en TOSTAO CAFE Y PAN.",
                "Notificación · Glim",
            ),
        )
        assertEquals(15_100.0, parsed.amount)
        assertEquals("COP", parsed.currency)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("TOSTAO CAFE Y PAN", parsed.merchant)
    }
}
