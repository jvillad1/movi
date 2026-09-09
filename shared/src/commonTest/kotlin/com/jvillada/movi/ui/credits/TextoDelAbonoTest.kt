package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.QueLograElAbono
import com.jvillada.movi.shared.model.simularAbonoUnico
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Lo que la hoja del abono **dice**, que no es lo mismo que lo que calcula
 *
 * `AbonoExtraordinarioTest` (en `:core`) prueba la aritmética y
 * `SimuladorDeAbonoEnPantallaTest` prueba que el texto llegue al dibujo. Lo que se prueba acá es la
 * capa del medio —[textoDeLaSimulacion] y sus vecinas— porque es donde vivían los tres defectos que
 * la revisión de #178 encontró **sin que ninguna prueba se cayera**: una libranza a la que se le
 * decía que el ahorro no era suyo, un titular que prometía «te ahorras» mientras la nota de arriba
 * lo negaba, y una comparación de fechas que decía «en junio de 2030 en vez de junio de 2030».
 *
 * Ninguno de los tres era un error de cálculo. Los tres eran de texto, y por eso hacía falta un
 * archivo que afirme sobre el texto sin pagar Robolectric.
 */
class TextoDelAbonoTest {

    private val periodo = PeriodoFinanciero(2026, 9)

    /** Libre inversión ·9695: $40.104.518 al 11,27 %, cuota $1.204.064 con $124.800 de seguro. */
    private fun nueve695(saleDeTuBolsillo: Boolean = true, abono: Long) =
        simularAbonoUnico(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo, abono)

    /** Hipotecario ·2334: la deuda crece $21.894 al mes, y la cuota la gira Skandia. */
    private fun dos334(abono: Long) =
        simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false, abono)

    private val terminosDelDosMilTresCientos = CreditTerms(
        accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
        termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
        insuranceMonthly = 209_219L,
    )

    private fun credito(terms: CreditTerms, saldo: Long, id: String) = CreditSummary(
        account = Account(id, "Crédito de prueba", AccountType.LOAN, balance = saldo),
        terms = terms,
        paidPct = 0.0,
    )

    // ------------------------------------------------------- de quién es la plata: dos casos

    /**
     * **Una libranza la paga él.** [saleDeTuBolsillo] contesta `false` para las dos —libranza y
     * tercero— porque su pregunta es de flujo de caja: si la cuota aparece como salida de la
     * cuenta. La del abono es otra, la de **de quién es la plata**, y ahí se separan: el sueldo le
     * llega neto, o sea que esa cuota salió de lo suyo.
     *
     * Colapsarlas le decía a la Libranza ·4818 —$255.677.421 al 18,01 %, su segundo crédito más
     * grande— que abonarle no le ahorraba a él. Es lo contrario de lo cierto, y en el crédito donde
     * el ahorro es más grande.
     */
    @Test
    fun `a una libranza se le dice que el ahorro SI es suyo`() {
        val libranza = terminosDelDosMilTresCientos.copy(payrollDeduction = true)

        val aviso = avisoDeQuienPagaLaCuota(libranza)

        assertEquals(AVISO_DE_ABONO_POR_LIBRANZA, aviso?.texto)
        assertEquals(false, aviso?.esAdvertencia, "una aclaración no se pinta de advertencia")
        assertTrue(elAhorroSeriaTuyo(libranza), "la retienen de SU sueldo")
        assertTrue(AVISO_DE_ABONO_POR_LIBRANZA.contains("es tuya"), "lo dice, no lo insinúa")
    }

    /** Y a una cuota que gira un tercero, lo contrario: ni la cuota ni el ahorro son suyos. */
    @Test
    fun `a una cuota que paga un tercero se le dice que el ahorro no es suyo`() {
        val skandia = terminosDelDosMilTresCientos.copy(paidBy = "Skandia")

        val aviso = avisoDeQuienPagaLaCuota(skandia)

        assertEquals(AVISO_DE_ABONO_AJENO, aviso?.texto)
        assertEquals(true, aviso?.esAdvertencia)
        assertEquals(false, elAhorroSeriaTuyo(skandia))
    }

    /** Y a la mayoría, nada: una advertencia sobre nada es ruido. */
    @Test
    fun `a un credito normal no se le aclara nada`() {
        assertNull(avisoDeQuienPagaLaCuota(terminosDelDosMilTresCientos))
        assertTrue(elAhorroSeriaTuyo(terminosDelDosMilTresCientos))
    }

    /**
     * Con los dos marcados —que hoy no pasa en ningún crédito— gana el tercero nombrado: de los dos
     * errores posibles, aclarar de más sobre plata que sí es suya es el barato.
     */
    @Test
    fun `con los dos marcados gana el tercero`() {
        val ambos = terminosDelDosMilTresCientos.copy(payrollDeduction = true, paidBy = "Skandia")

        assertEquals(AVISO_DE_ABONO_AJENO, avisoDeQuienPagaLaCuota(ambos)?.texto)
        assertEquals(false, elAhorroSeriaTuyo(ambos))
    }

    // ------------------------------------------------- el titular no promete lo que la nota niega

    /**
     * **La cifra grande no puede prometer lo que la nota chica niega.** En un crédito que paga un
     * tercero, «Te ahorras $X» a 14sp/Medium contra «el ahorro no sería tuyo» a 11,5sp: gana la
     * cifra grande, siempre. El titular dice lo mismo sin dueño.
     */
    @Test
    fun `el titular no dice te ahorras cuando el ahorro no es tuyo`() {
        val sim = nueve695(saleDeTuBolsillo = false, abono = 2_000_000L)

        val ajeno = textoDeLaSimulacion(sim, periodo, minimo = null, elAhorroSeriaTuyo = false)!!
        val propio = textoDeLaSimulacion(sim, periodo, minimo = null, elAhorroSeriaTuyo = true)!!

        assertEquals("Esta deuda pagaría unos $970.000 menos en intereses", ajeno.titular)
        assertEquals("Te ahorras unos $970.000 en intereses", propio.titular)
    }

    /** Lo mismo cuando el abono salda la deuda: ahí el titular también decía «te ahorras». */
    @Test
    fun `saldar un credito ajeno tampoco promete un ahorro tuyo`() {
        val sim = simularAbonoUnico(507_553L, 29.64, 26_485L, null, null, false, 1_000_000L)

        val ajeno = textoDeLaSimulacion(sim, periodo, minimo = null, elAhorroSeriaTuyo = false)!!

        assertEquals("Con esto la saldas hoy y esta deuda pagaría unos $160.000 menos en intereses", ajeno.titular)
        assertTrue(ajeno.detalle!!.contains("Te sobran $492.447"), "lo que sobra sigue siendo suyo, y exacto")
    }

    // ------------------------------------------------------ el abono que no mueve el plazo

    /**
     * **«Me sobraron cien mil»: el caso más común, y el que se degeneraba.** $100.000 al ·9695
     * ahorran $50.585 y **no adelantan ninguna cuota** — siguen siendo 46. La plantilla de las dos
     * fechas contestaba «en junio de 2030 en vez de junio de 2030»: la misma fecha dos veces.
     *
     * Y el arreglo no es cosmético. Si el plazo no se mueve, el ahorro sale de que la **última
     * cuota queda más pequeña** (de $519.503 a $368.918 en este mismo crédito), que es justo lo que
     * [SUPUESTO_DEL_ABONO] supone que no pasa. La hoja lo dice en vez de repetir un mes.
     */
    @Test
    fun `cien mil al 9695 no repiten la misma fecha dos veces`() {
        val sim = nueve695(abono = 100_000L)
        assertEquals(0, sim.cuotasQueSeAhorra, "el plazo no se mueve: es el caso que se degeneraba")
        assertEquals(50_585L, sim.interesQueSeAhorra)

        val detalle = textoDeLaSimulacion(sim, periodo, minimo = null, elAhorroSeriaTuyo = true)!!.detalle!!

        assertTrue(detalle.startsWith("No te adelanta ninguna cuota: la última sigue siendo la de "), detalle)
        assertTrue(detalle.endsWith(", solo que más pequeña. De ahí sale el ahorro."), detalle)
        assertEquals(false, detalle.contains(" en vez de "), "no hay dos fechas que comparar: son la misma")
    }

    /** Y cuando sí se mueve, las dos fechas siguen ahí, que es lo que hace legible al titular. */
    @Test
    fun `cuando el plazo si se mueve se muestran las dos fechas`() {
        val detalle = textoDeLaSimulacion(
            nueve695(abono = 2_000_000L), periodo, minimo = null, elAhorroSeriaTuyo = true,
        )!!.detalle!!

        assertEquals("Terminas 3 cuotas antes: en marzo de 2030 en vez de junio de 2030.", detalle)
    }

    // ------------------------------------------------------------- el mínimo, nunca sin su fecha

    /**
     * **$2.549.401 solos parecen alcanzables.** Lo que compran son 479 cuotas: hasta 2066. La rama
     * de «no alcanza» —que es la ruta real: teclear un millón en el ·2334— mostraba el número
     * desnudo, y la fecha solo aparecía si el dueño tocaba el chip.
     */
    @Test
    fun `cuando no alcanza, el minimo va con su fecha en la misma frase`() {
        val sim = dos334(abono = 1_000_000L)
        assertEquals(QueLograElAbono.NO_ALCANZA, sim.logro)

        val resultado = textoDeLaSimulacion(sim, periodo, MinimoConSuFecha(2_549_401L, 479), false)!!

        assertEquals(
            "Con ese abono seguiría creciendo $10.011 cada mes. Harían falta $2.549.401, " +
                "y aun así te faltarían 479 cuotas: hasta julio de 2066.",
            resultado.detalle,
        )
    }

    /** El mínimo y su fecha salen juntos de una sola función, para que no puedan separarse. */
    @Test
    fun `el minimo viene con la fecha que compra`() {
        val eterno = credito(terminosDelDosMilTresCientos, 204_183_376L, "acc_2334")

        assertEquals(MinimoConSuFecha(2_549_401L, 479), minimoConSuFecha(eterno))
    }

    /** Y en un crédito que ya se termina no hay mínimo que ofrecer: la pregunta no aplica. */
    @Test
    fun `un credito que ya se termina no tiene minimo que mostrar`() {
        val terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        )

        assertNull(minimoConSuFecha(credito(terms, 40_104_518L, "acc_9695")))
    }

    // -------------------------------------------------------------------------- la alerta

    /**
     * **El color de alerta es de un solo caso**: el abono no alcanzó **y** la deuda sigue
     * creciendo. Mismo criterio que la tarjeta de atrás, para que las dos partes de la pantalla no
     * llamen alerta a cosas distintas.
     *
     * El contracaso existe de verdad y es exactamente el peso de al lado del mínimo: con
     * $2.549.400 la deuda deja de crecer pero todavía no se termina. Eso no es una alerta, es una
     * deuda quieta.
     */
    @Test
    fun `solo es alerta cuando la deuda sigue creciendo`() {
        fun alerta(abono: Long) =
            textoDeLaSimulacion(dos334(abono), periodo, minimo = null, elAhorroSeriaTuyo = false)!!.esAlerta

        assertEquals(true, alerta(1_000_000L), "sigue creciendo $10.011 al mes")
        assertEquals(false, alerta(2_549_400L), "un peso menos que el mínimo: quieta, no creciendo")
        assertEquals(false, alerta(2_549_401L), "el mínimo ya le pone fecha")
        assertEquals(
            false,
            textoDeLaSimulacion(nueve695(abono = 2_000_000L), periodo, null, true)!!.esAlerta,
            "un crédito que amortiza nunca es alerta",
        )
    }

    // ------------------------------------------------------------------ la precisión que hay

    /**
     * **El ahorro se dice con la precisión que tiene, y no es al peso.** El interés sale de
     * `saldo × tasa mensual`, que contra el extracto del ·9695 se quedó corto un 30 % «siempre en
     * la misma dirección». Ese sesgo, apilado sobre cuarenta y seis o cuatrocientas setenta y nueve
     * cuotas, no deja en pie el séptimo dígito. Ver [SUPUESTO_DE_LA_ESTIMACION].
     */
    @Test
    fun `el ahorro se redondea a dos cifras y se dice unos`() {
        assertEquals("unos $970.000", montoAproximado(971_366L))
        assertEquals("unos $51.000", montoAproximado(50_585L))
        assertEquals("unos $8.000.000", montoAproximado(8_009_748L))
        // Por debajo de dos cifras no hay nada que redondear, y la escala sube sola cuando el
        // redondeo se lleva la cifra al orden siguiente.
        assertEquals(0L, redondeadoADosCifras(0L))
        assertEquals(99L, redondeadoADosCifras(99L))
        assertEquals(100L, redondeadoADosCifras(99L + 1L))
        assertEquals(1_000_000L, redondeadoADosCifras(995_000L))
    }

    /** Y el supuesto que lo explica está escrito, no solo aplicado. */
    @Test
    fun `la hoja declara que la estimacion del interes se queda corta`() {
        assertTrue(SUPUESTO_DE_LA_ESTIMACION.contains("se queda corto"), SUPUESTO_DE_LA_ESTIMACION)
        assertTrue(SUPUESTO_DE_LA_ESTIMACION.contains("30 %"), SUPUESTO_DE_LA_ESTIMACION)
    }

    // ------------------------------------------------------------------- los montos que se ofrecen

    /**
     * **Ningún chip por encima de la deuda.** Es la mitad de la razón de ser de esta lista: ofrecer
     * «tres cuotas» sobre un crédito al que le quedan $50.000 sería ofrecer pagar de más, y
     * «Saldarla» ya cubre ese caso con el número correcto.
     */
    @Test
    fun `no se ofrece un abono mas grande que la deuda`() {
        val sugeridos = montosSugeridosDeAbono(saldo = 50_000L, cuota = 26_485L, abonoMinimo = null)

        assertEquals(listOf("Una cuota más", "Saldarla"), sugeridos.map { it.etiqueta })
        assertEquals(listOf(26_485L, 50_000L), sugeridos.map { it.monto })
    }

    /** Y ningún monto dos veces con dos nombres: al crédito al que le queda una cuota, un chip. */
    @Test
    fun `el mismo monto no se ofrece dos veces con dos nombres`() {
        val sugeridos = montosSugeridosDeAbono(saldo = 26_485L, cuota = 26_485L, abonoMinimo = null)

        assertEquals(listOf(MontoSugerido("Una cuota más", 26_485L)), sugeridos)
    }

    /** El caso completo: las cuatro, con el mínimo en el medio. */
    @Test
    fun `en la deuda que no se termina se ofrecen las cuatro`() {
        val sugeridos = montosSugeridosDeAbono(
            saldo = 204_183_376L, cuota = 2_613_714L, abonoMinimo = 2_549_401L,
        )

        assertEquals(
            listOf("Una cuota más", "Tres cuotas", "Lo mínimo para que se termine", "Saldarla"),
            sugeridos.map { it.etiqueta },
        )
        assertEquals(listOf(2_613_714L, 7_841_142L, 2_549_401L, 204_183_376L), sugeridos.map { it.monto })
    }

    /** Sin deuda no hay nada que ofrecer, y un chip de «$0» sería una pregunta sin sentido. */
    @Test
    fun `sin deuda no hay montos que ofrecer`() {
        assertEquals(emptyList<MontoSugerido>(), montosSugeridosDeAbono(saldo = 0L, cuota = 26_485L, abonoMinimo = null))
    }
}
