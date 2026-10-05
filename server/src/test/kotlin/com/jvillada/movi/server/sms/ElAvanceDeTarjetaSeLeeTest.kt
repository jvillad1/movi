package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.AVANCE_CATEGORY
import com.jvillada.movi.server.routes.conLoQueMoviRecuerda
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **El avance de una tarjeta es plata que entra, no un gasto.** El 3-oct-2026 el dueño sacó un
 * avance de $6.200.000 de su AMEX a su cuenta de ahorros; el aviso llegó solo por correo y Movi lo
 * leía como un gasto de $6,2 M llamado «tu SUC VIRTUAL», en la tarjeta, con su propia cuenta *8133
 * ofrecida como «¿de quién es esta cuenta?». Los números de producto acá son sintéticos.
 */
class ElAvanceDeTarjetaSeLeeTest {

    private val avance = "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL el 17:43 03/10/2026 " +
        "desde tu T.Credito *2222 a la cuenta *3333."

    @Test
    fun `el avance se lee como un ingreso de la tarjeta, no como un gasto en tu SUC VIRTUAL`() {
        val p = assertNotNull(parseSms(avance, "Correo · Bancolombia"))
        assertEquals(6_200_000.0, p.amount)
        assertEquals("COP", p.currency)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("Avance de la tarjeta *2222", p.merchant)
        assertEquals(AVANCE_CATEGORY, p.category)
    }

    @Test
    fun `la cuenta a la que llega el avance es del duenio y no se ofrece como de otro`() {
        val p = assertNotNull(parseSms(avance, "Correo · Bancolombia"))
        assertNull(p.identificadorDelDestino)
    }

    @Test
    fun `con el monto pegado, como lo escribe el correo, tambien`() {
        val p = assertNotNull(parseSms("Hiciste un avance de \$6200000 en tu SUC VIRTUAL desde tu T.Credito *2222 a la cuenta *3333.", "Correo · Bancolombia"))
        assertEquals(6_200_000.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
    }

    @Test
    fun `sin el numero de la tarjeta se llama avance de tarjeta a secas`() {
        val p = assertNotNull(parseSms("Hiciste un avance de \$500.000 en el cajero."))
        assertEquals("Avance de tarjeta", p.merchant)
    }

    /**
     * La memoria reconoce movimientos por el número que nombran: un avance de la tarjeta *2222 se
     * parecería a cualquier otra cosa anotada con esa tarjeta, y le cambiaría la categoría y el
     * nombre. El avance lo dice el aviso, no la costumbre.
     */
    @Test
    fun `la memoria no le cambia la categoria ni el nombre a un avance`() {
        val memoria = MemoriaDeCategorias.de(
            List(3) {
                AnotacionPasada(comoLlego = "Compra con la tarjeta *2222", nombre = "Mercado", categoria = "Comida", cuando = 1_000L + it)
            },
        )
        val p = conLoQueMoviRecuerda(assertNotNull(parseSms(avance)), memoria)
        assertEquals(AVANCE_CATEGORY, p.category)
        assertEquals("Avance de la tarjeta *2222", p.merchant)
    }
}
