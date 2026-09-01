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

    // ── Una deuda en OTRA moneda ───────────────────────────────────────────────

    /**
     * **El camino que `saldoEnSuMoneda` existe para cubrir, y que ningún test ejercitaba.**
     *
     * `account.balance` es el componente **COP** (ver `enrichWith`), así que sobre una deuda en
     * dólares es una cifra de otra moneda: usarla daría un interés calculado sobre pesos y una
     * deuda «antes/después» que no es la de nadie. Por eso hay un `balancesByCurrency[currency]`
     * delante. Las cuentas de esta suite se construían con ese mapa **vacío**, así que todas caían
     * al respaldo y la rama real nunca corría.
     *
     * El caso llega solo por `POST /api/accounts` —la UI de Créditos crea únicamente cuentas COP—
     * pero es exactamente la cuenta cuyo monto el dueño no puede verificar de memoria.
     */
    private val tarjetaUsd = Account(
        id = "acc_usd",
        name = "AMEX USD",
        type = AccountType.CREDIT_CARD,
        balance = 0L,
        currency = "USD",
        balancesByCurrency = mapOf("USD" to 1_200L, "COP" to 0L),
    )

    private val creditoUsd = Account(
        id = "acc_loan_usd",
        name = "Crédito en dólares",
        type = AccountType.LOAN,
        // El componente COP es 0 y el saldo de verdad son 50.000 dólares: si se usara `balance`,
        // el interés daría cero y la frase diría que la cuota entera abona a capital.
        balance = 0L,
        currency = "USD",
        balancesByCurrency = mapOf("USD" to 50_000L),
    )

    @Test
    fun `el interes de una deuda en dolares sale de su saldo en dolares`() {
        val d = assertNotNull(
            desgloseDelPago(creditoUsd, terminos(12.0).copy(accountId = creditoUsd.id), 1_000L),
        )

        assertTrue(d.interes > 0L, "con el componente COP (0) el interés habría dado 0: $d")
        assertEquals(474L, d.interes, "50.000 × ((1,12)^(1/12) − 1)")
        assertEquals(526L, d.capital)
        assertEquals(d.cuota, d.interes + d.seguro + d.capital)
    }

    @Test
    fun `el renglon de la deuda en dolares parte de su saldo en dolares`() {
        val d = assertNotNull(desgloseDelPago(tarjetaUsd, null, 200L))
        val renglon = assertNotNull(deudaDespuesDelPago(tarjetaUsd, d))

        assertTrue(renglon.contains("1.200"), "el saldo en USD, no el componente COP: $renglon")
        assertTrue(renglon.contains("1.000"), "1.200 − 200: $renglon")
    }

    // ── Lo que de verdad quedó escrito ─────────────────────────────────────────

    @Test
    fun `si el server reparte igual que la hoja no se le dice nada`() {
        // El camino de todos los días. Un cartel acá enseñaría a no leer los carteles.
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))

        assertNull(avisoDeDesgloseDistinto(mostrado = d, guardado = d, moneda = "COP"))
    }

    @Test
    fun `si el server reparte distinto el dueño se entera`() {
        // El escenario: entre abrir la hoja y tocar Guardar entró un SMS del banco o se ajustó el
        // saldo, así que el server calculó el interés sobre otra deuda. La hoja le prometió un
        // reparto y se escribió otro — y hasta este arreglo, la respuesta que lo decía la
        // descartaba `save()` sin leerla.
        val mostrado = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))
        val guardado = mostrado.copy(interes = 2_600_000L, capital = 1_615_223L)

        val aviso = assertNotNull(avisoDeDesgloseDistinto(mostrado, guardado, "COP"))
        assertTrue(aviso.contains(formatMoney(1_615_223L, "COP")), "dice lo que de verdad bajó: $aviso")
        assertTrue(aviso.contains(formatMoney(mostrado.capital, "COP")), "y lo que le habíamos dicho: $aviso")
    }

    @Test
    fun `un server que todavia no manda el desglose no dispara ningun aviso`() {
        // `PagoDeCuotaResult.desglose` es nullable justamente para eso. Inventar un aviso sobre un
        // null sería afirmar algo que nadie dijo.
        val d = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))

        assertNull(avisoDeDesgloseDistinto(mostrado = d, guardado = null, moneda = "COP"))
    }

    @Test
    fun `pagar una tarjeta no dispara el aviso, porque no hay nada distinto`() {
        val d = assertNotNull(desgloseDelPago(amex, null, 1_008_902L))

        assertNull(avisoDeDesgloseDistinto(mostrado = d, guardado = d, moneda = "COP"))
    }

    // ── La limitación, dicha donde el dueño la ve ──────────────────────────────

    @Test
    fun `la frase dice sobre que deuda se calculo el interes`() {
        // El interés sale del saldo de HOY, no del que había el mes de la cuota: la deuda en Movi
        // es la suma de todos los eventos sin mirar fechas. Anotar una cuota vieja después de una
        // nueva la calcula sobre un saldo ya reducido, siempre subestimando la deuda. Estaba
        // anotado solo en un KDoc, o sea en ningún lado que el dueño pueda leer.
        val conCapital = assertNotNull(desgloseDelPago(carro, terminos(18.16), 4_215_223L))
        val sinCapital = assertNotNull(desgloseDelPago(carro, terminos(18.16), 1_000_000L))

        assertTrue(assertNotNull(textoDelDesglose(conCapital, "COP")).contains("deuda de hoy"))
        assertTrue(assertNotNull(textoDelDesglose(sinCapital, "COP")).contains("deuda de hoy"))
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
