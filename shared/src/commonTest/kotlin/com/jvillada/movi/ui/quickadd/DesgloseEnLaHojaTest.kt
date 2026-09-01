package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.MotivoDelDesglose
import com.jvillada.movi.ui.components.formatMoney
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que la hoja de «Cuota» le muestra al dueño antes de guardar.**
 *
 * Es plata suya: tiene que poder *verificar* el número, no confiar en él. Desde que la deuda baja
 * solo por el capital, Movi le resta a la deuda una cifra distinta de la que él escribió — sin
 * estas frases no habría en toda la app dónde enterarse de por qué.
 *
 * El cálculo en sí vive en `:core` (`DesgloseDeCuotaTest`) y no se repite acá: lo que se prueba
 * acá es que la pantalla lo **use** y que diga la verdad en cada caso, incluido el que no puede
 * calcular.
 */
class DesgloseEnLaHojaTest {

    private val ahorros = Account("acc_ahorros", "Bancolombia", AccountType.SAVINGS, 20_000_000L)
    private val carro = Account("acc_carro", "Vehículo 4083", AccountType.LOAN, 177_200_000L)
    private val amex = Account("acc_amex", "AMEX 9208", AccountType.CREDIT_CARD, 19_818_701L)

    private fun terminos(rateEa: Double, seguro: Long? = null) = CreditTerms(
        accountId = carro.id,
        bank = "Bancolombia",
        principal = 200_000_000L,
        rateEa = rateEa,
        termMonths = 72,
        installment = 4_215_223L,
        dayOfMonth = 5,
        startDate = "2024-01-15",
        insuranceMonthly = seguro,
    )

    // ── El desglose ────────────────────────────────────────────────────────────

    @Test
    fun `la hoja calcula el mismo capital que el server`() {
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))

        assertEquals(2_481_318L, d.interes)
        assertEquals(1_733_905L, d.capital)
    }

    @Test
    fun `sin monto o sin deuda elegida no hay nada que mostrar`() {
        assertNull(desgloseDelPago(carro, terminos(18.16), null))
        assertNull(desgloseDelPago(carro, terminos(18.16), 0L))
        assertNull(desgloseDelPago(null, terminos(18.16), 4_215_223L))
        // Y sobre una cuenta que no es deuda tampoco: no es un pago de cuota.
        assertNull(desgloseDelPago(ahorros, null, 4_215_223L))
    }

    // ── Las frases ─────────────────────────────────────────────────────────────

    @Test
    fun `la frase nombra las tres partes de la cuota`() {
        // El ejemplo del pedido, con el libre inversión ·9695: «De tus $1.286.548, $363.905 son
        // intereses, $108.800 el seguro, y $813.843 bajan la deuda».
        val nueveSeisNueveCinco = Account("acc_9695", "Libre inversión", AccountType.LOAN, 41_093_905L)
        val d = assertNotNull(
            desgloseDelPago(nueveSeisNueveCinco, terminos(11.27, seguro = 108_800L), 1_286_548L),
        )
        val texto = assertNotNull(textoDelDesglose(d, "COP"))

        assertTrue(texto.contains(formatMoney(d.cuota, "COP")), texto)
        assertTrue(texto.contains(formatMoney(d.interes, "COP")), texto)
        assertTrue(texto.contains(formatMoney(d.seguro, "COP")), texto)
        // Y —lo que de verdad importa— el capital que se va a escribir en la pata de la deuda.
        assertTrue(texto.contains(formatMoney(d.capital, "COP")), texto)
        assertEquals(108_800L, d.seguro)
        assertEquals(d.cuota, d.interes + d.seguro + d.capital)
    }

    @Test
    fun `sin seguro la frase no lo menciona`() {
        // Un renglón que dijera «$0 el seguro» sobre un crédito sin seguro es ruido que enseña a
        // no leer los avisos.
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))

        assertTrue(!assertNotNull(textoDelDesglose(d, "COP")).contains("seguro"))
    }

    @Test
    fun `sin tasa la hoja LO DICE en vez de callarse`() {
        // El requisito explícito: cuando no se puede calcular, no se inventa — y el dueño tiene
        // que poder ver por qué, y qué hacer al respecto.
        val d = assertNotNull(desgloseDelPago(carro, terms = null, monto = 4_215_223L))
        val texto = assertNotNull(textoDelDesglose(d, "COP"))

        assertEquals(MotivoDelDesglose.SIN_TASA, d.motivo)
        assertTrue(texto.contains("tasa"), texto)
        assertTrue(texto.contains("4.215.223"), "dice cuánto va a bajar la deuda: $texto")
    }

    @Test
    fun `pagar una tarjeta no muestra ningun desglose`() {
        // Baja la deuda por todo lo pagado, que es lo que cualquiera espera. Explicarlo sería ruido.
        val d = assertNotNull(desgloseDelPago(amex, null, 1_008_902L))

        assertEquals(MotivoDelDesglose.TARJETA, d.motivo)
        assertNull(textoDelDesglose(d, "COP"))
    }

    @Test
    fun `una cuota que no alcanza a cubrir el interes se dice sin rodeos`() {
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 1_000_000L))
        val texto = assertNotNull(textoDelDesglose(d, "COP"))

        assertEquals(0L, d.capital)
        assertTrue(texto.contains("nada de este pago baja la deuda"), texto)
    }

    // ── La deuda antes y después ───────────────────────────────────────────────

    @Test
    fun `el renglon de la deuda resta el capital, no la cuota`() {
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))
        val renglon = assertNotNull(deudaDespuesDelPago(carro, d))

        assertTrue(renglon.contains("177.200.000"), renglon)
        assertTrue(renglon.contains("175.466.095"), "177.200.000 − 1.733.905: $renglon")
    }

    /** «pasa a» y no «→»: la flecha sale como ▯ en wasm (la fuente del canvas no trae el glifo). */
    @Test
    fun `ningun texto usa un glifo que la web no sepa dibujar`() {
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16, seguro = 108_800L), 4_215_223L))
        val textos = listOfNotNull(textoDelDesglose(d, "COP"), deudaDespuesDelPago(carro, d))

        textos.forEach { texto ->
            listOf('→', '›', '⟶', '▶', '✓').forEach {
                assertTrue(!texto.contains(it), "'$it' no se dibuja en wasm: $texto")
            }
        }
    }
}
