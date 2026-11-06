package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 133 · los créditos cuya cuota cambia
 *
 * La cuota de este mes sale de UNA función ([cuotaDeEsteMes]); el plan sabe de abono constante, de UVR y
 * de tasa variable; y un crédito viejo, sin los campos, da exactamente lo de antes.
 */
class LaCuotaQueCambiaTest {

    private val terms = CreditTerms(
        accountId = "acc_libre", bank = "Bancolombia", principal = 12_000_000, rateEa = 12.0, termMonths = 12,
        installment = 1_065_000, dayOfMonth = 5, startDate = "2026-01-10",
    )
    private val pagadaEnOctubre = CuotaPagada(1_080_500, "2026-10")

    private fun credito(t: CreditTerms, saldo: Long = 12_000_000, ultima: CuotaPagada? = null) = CreditSummary(
        account = Account("acc_libre", "Libre inversión", AccountType.LOAN, balance = saldo),
        terms = t,
        paidPct = null,
        ultimaCuotaPagada = ultima,
    )

    // ── La cuota de este mes ────────────────────────────────────────────────────────────────────

    @Test
    fun `fija, o sin el campo, es la de las condiciones con o sin pagos`() {
        assertEquals(1_065_000L, cuotaDeEsteMes(terms, null))
        assertEquals(1_065_000L, cuotaDeEsteMes(terms, pagadaEnOctubre), "una cuota fija no se mueve por un pago")
        assertEquals(1_065_000L, cuotaDeEsteMes(terms.copy(tipoDeCuota = TipoDeCuota.FIJA), pagadaEnOctubre))
    }

    @Test
    fun `la que cambia es la ultima pagada de verdad, y sin pagos la de las condiciones`() {
        for (tipo in listOf(TipoDeCuota.TASA_VARIABLE, TipoDeCuota.UVR, TipoDeCuota.ABONO_CONSTANTE)) {
            val t = terms.copy(tipoDeCuota = tipo)
            assertEquals(1_080_500L, cuotaDeEsteMes(t, pagadaEnOctubre), "$tipo con un pago")
            assertEquals(1_065_000L, cuotaDeEsteMes(t, null), "$tipo sin pagos")
            assertEquals(1_065_000L, cuotaDeEsteMes(t, CuotaPagada(0, "2026-10")), "$tipo con un pago en cero")
        }
    }

    @Test
    fun `la cuota que cambia se dice con unos y la del mes pasado`() {
        assertEquals("unos \$1.080.500, como la de octubre", textoDeLaCuotaQueCambia(1_080_500, pagadaEnOctubre), "la estimación es la anterior: no se repite la cifra")
        assertEquals("unos \$1.065.000 (la de octubre fue \$1.080.500)", textoDeLaCuotaQueCambia(1_065_000, pagadaEnOctubre))
        assertEquals("unos \$1.065.000", textoDeLaCuotaQueCambia(1_065_000, null))
        assertEquals("\$1.065.000", cuotaEnPalabras(terms, pagadaEnOctubre))
        assertEquals("unos \$1.080.500", cuotaEnPalabras(terms.copy(tipoDeCuota = TipoDeCuota.UVR), pagadaEnOctubre))
    }

    // ── El plan ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un credito viejo sin los campos da exactamente el plan de antes`() {
        val antes = planDeUnaDeuda(
            saldoDeLaDeuda = 12_000_000, rateEa = 12.0, cuota = 1_065_000, seguroMensual = null,
            otrosCargosMensuales = null, saleDeTuBolsillo = true,
        )
        assertEquals(antes, planDelCredito(credito(terms)))
        assertEquals(antes, planDelCredito(credito(terms, ultima = pagadaEnOctubre)), "el pago no mueve un plan fijo")
        assertNull(antes.tipoDeCuota)
    }

    /**
     * Vuelta 134 (M-9) · **con cifras fijas, no la función contra sí misma.** La prueba de arriba compara
     * `planDelCredito` con `planDeUnaDeuda`: una regresión dentro de `planDeUnaDeuda` con `tipo = null` le
     * pasaría a los dos lados. Estas cifras salen de la amortización francesa a mano: 12 % EA es
     * 0,9488793 % al mes; el primer mes, 12.000.000 × eso = $113.866 de interés y $951.134 a capital, y
     * con $1.065.000 al mes la deuda se acaba en 12 cuotas con $751.481 de intereses.
     */
    @Test
    fun `un credito viejo sin los campos da el plan de antes, con cifras fijas`() {
        for (t in listOf(terms, terms.copy(tipoDeCuota = TipoDeCuota.FIJA))) {
            val plan = assertNotNull(planDelCredito(credito(t, ultima = pagadaEnOctubre)))
            assertEquals(1_065_000L, plan.cuota)
            assertEquals(113_866L, plan.interes)
            assertEquals(951_134L, plan.capital)
            assertEquals(12, plan.mesesHastaLaUltimaCuota)
            assertEquals(751_481L, plan.interesPorPagar)
            assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa)
            assertNull(plan.cuotaAlFinal, "una cuota fija no dice hacia dónde va")
            assertNull(plan.cuotaEnUnAno)
        }
    }

    @Test
    fun `abono constante · la cuota baja cada mes y los capitales suman lo prestado`() {
        val t = terms.copy(tipoDeCuota = TipoDeCuota.ABONO_CONSTANTE)
        assertEquals(1_000_000L, capitalFijoDe(t))
        val plan = assertNotNull(planDelCredito(credito(t)))
        assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa)
        assertEquals(12, plan.mesesHastaLaUltimaCuota, "lo prestado entre el plazo, doce veces")
        assertEquals(1_000_000L, plan.capital)
        val alFinal = assertNotNull(plan.cuotaAlFinal)
        assertTrue(alFinal < plan.cuota, "la última ($alFinal) es menor que la de hoy (${plan.cuota})")

        val calendario = calendarioDeAbonoConstante(12_000_000, tasaMensualDeUnaEA(12.0), 1_000_000, 50_000)
        assertEquals(12_000_000L, calendario.sumOf { it.capital }, "los capitales suman el principal")
        assertTrue(calendario.zipWithNext().all { (a, b) -> b.total < a.total }, "cada cuota es menor que la anterior")
        assertEquals(calendario.first().interes + 1_000_000 + 50_000, calendario.first().total)
    }

    @Test
    fun `abono constante sin plazo cae a la cuota escrita`() {
        val t = terms.copy(tipoDeCuota = TipoDeCuota.ABONO_CONSTANTE, termMonths = 0)
        assertNull(capitalFijoDe(t))
        assertNull(assertNotNull(planDelCredito(credito(t))).cuotaAlFinal)
    }

    @Test
    fun `uvr · la cuota sube con la inflacion supuesta y el plazo no cambia`() {
        val fijo = assertNotNull(planDelCredito(credito(terms)))
        val uvr = assertNotNull(planDelCredito(credito(terms.copy(tipoDeCuota = TipoDeCuota.UVR))))
        assertEquals(TipoDeCuota.UVR, uvr.tipoDeCuota)
        assertEquals(fijo.mesesHastaLaUltimaCuota, uvr.mesesHastaLaUltimaCuota, "en pesos de hoy, la cuota en UVR es constante")
        val enUnAno = assertNotNull(uvr.cuotaEnUnAno)
        // 3 % al año: unos $31.950 más sobre $1.065.000.
        assertEquals(kotlin.math.round(1_065_000 * 1.03).toLong(), enUnAno)
        assertTrue(assertNotNull(uvr.interesPorPagar) > assertNotNull(fijo.interesPorPagar), "en pesos corrientes se paga más interés")
        assertTrue(loQueSuponeElPlan(TipoDeCuota.UVR, 12.0)!!.contains("3 % al año"))
        // Los seguros se liquidan en pesos: no suben con la UVR.
        assertEquals(kotlin.math.round(965_000 * 1.03).toLong() + 100_000, cuotaEnUvrDentroDe(12, 1_065_000, 100_000, inflacionMensualDe(3.0)))
    }

    @Test
    fun `tasa variable · proyecta con la tasa de hoy y la cuota de este mes`() {
        val t = terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE, indiceDeLaTasa = IndiceDeLaTasa.IBR, puntosSobreElIndice = 6.5)
        val variable = assertNotNull(planDelCredito(credito(t, ultima = pagadaEnOctubre)))
        val conLaMismaCuota = planDeUnaDeuda(
            saldoDeLaDeuda = 12_000_000, rateEa = 12.0, cuota = 1_080_500, seguroMensual = null,
            otrosCargosMensuales = null, saleDeTuBolsillo = true,
        )
        assertEquals(1_080_500L, variable.cuota)
        assertEquals(conLaMismaCuota.mesesHastaLaUltimaCuota, variable.mesesHastaLaUltimaCuota)
        assertEquals(conLaMismaCuota.interesPorPagar, variable.interesPorPagar, "sin pronosticar el IBR")
        assertTrue(loQueSuponeElPlan(TipoDeCuota.TASA_VARIABLE, 12.0)!!.contains("tasa de hoy (12 % EA)"))
    }

    @Test
    fun `las condiciones se guardan limpias`() {
        val limpia = terms.copy(tipoDeCuota = TipoDeCuota.UVR, puntosSobreElIndice = 4.0, indiceDeLaTasa = IndiceDeLaTasa.IBR).conLaCuotaQueCambiaLimpia()
        assertNull(limpia.puntosSobreElIndice)
        assertNull(limpia.indiceDeLaTasa)
        assertNull(terms.copy(tipoDeCuota = TipoDeCuota.FIJA).conLaCuotaQueCambiaLimpia().tipoDeCuota)
        assertEquals(PUNTOS_FUERA_DE_RANGO, validarLaCuotaQueCambia(terms.copy(puntosSobreElIndice = -1.0)))
        assertNull(validarLaCuotaQueCambia(terms.copy(puntosSobreElIndice = 8.5)))
    }

    // ── Cómo salir de tus deudas ────────────────────────────────────────────────────────────────

    @Test
    fun `el plan de salida abona el capital fijo de un abono constante`() {
        val t = terms.copy(tipoDeCuota = TipoDeCuota.ABONO_CONSTANTE)
        val deuda = deudasParaSalir(listOf(credito(t)), emptyList()).single()
        assertEquals(1_000_000L, deuda.capitalFijo)
        val plan = planDeSalida(listOf(deuda), 0L, EstrategiaDeSalida.AVALANCHA)
        assertEquals(12, plan.enElCalculo.single().mesesSinAbono)
        // Uno de tasa variable sale con la cuota de este mes.
        val variable = deudasParaSalir(listOf(credito(terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE), ultima = pagadaEnOctubre)), emptyList()).single()
        assertEquals(1_080_500L, variable.cuota)
        assertNull(variable.capitalFijo)
    }

    // ── Revisión de la vuelta 133 ──────────────────────────────────────────────────────────────

    @Test
    fun `el simulador de abono parte del mismo plan que la ficha en un abono constante`() {
        val t = terms.copy(tipoDeCuota = TipoDeCuota.ABONO_CONSTANTE)
        val ficha = assertNotNull(planDelCredito(credito(t)))
        val simulacion = assertNotNull(simularAbonoUnico(credito(t), 2_000_000))
        assertEquals(ficha, simulacion.antes, "«sin abono» es la ficha")
        assertEquals(12, simulacion.antes.mesesHastaLaUltimaCuota)
        assertEquals(10, simulacion.despues.mesesHastaLaUltimaCuota, "dos capitales fijos menos")
        // Y en tasa variable, con la cuota de este mes, como la ficha.
        val variable = credito(terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE), ultima = pagadaEnOctubre)
        assertEquals(planDelCredito(variable), assertNotNull(simularAbonoUnico(variable, 500_000)).antes)
    }

    @Test
    fun `segun tu plan, un abono constante baja su capital fijo por cuota vencida`() {
        val plan = saldoSegunElPlan(
            capital = 12_000_000, tasaEa = 12.0, sinIntereses = false, plazoMeses = 12, cuota = 1_065_000,
            seguroMensual = null, otrosCargosMensuales = null, diaDePago = 5,
            desde = kotlinx.datetime.LocalDate(2026, 1, 10), hoy = kotlinx.datetime.LocalDate(2026, 4, 20),
            capitalFijo = 1_000_000,
        )
        assertEquals(9_000_000L, plan.saldo, "febrero, marzo y abril: tres capitales fijos")
    }

    @Test
    fun `uvr · Como salir de tus deudas sin abono dice el mismo interes que la ficha`() {
        val c = credito(terms.copy(tipoDeCuota = TipoDeCuota.UVR))
        val ficha = assertNotNull(planDelCredito(c))
        val salida = planDeSalida(deudasParaSalir(listOf(c), emptyList()), 0L, EstrategiaDeSalida.AVALANCHA).enElCalculo.single()
        assertEquals(ficha.mesesHastaLaUltimaCuota, salida.mesesSinAbono)
        assertEquals(ficha.interesPorPagar, salida.interesSinAbono, "la misma inflación supuesta en las dos pantallas")
    }

    @Test
    fun `un ajuste de saldo o un cargo del banco en la tarjeta no lleva cuotas`() {
        val compra = FinancialEvent(id = "e", accountId = "t", type = TransactionType.EXPENSE, amount = 100_000, category = "Hogar", description = "Alkosto", timestamp = 0L)
        assertTrue(llevaCuotas(compra, AccountType.CREDIT_CARD))
        assertTrue(!llevaCuotas(compra.copy(category = ADJUSTMENT_CATEGORY), AccountType.CREDIT_CARD))
        assertTrue(!llevaCuotas(compra.copy(category = CATEGORIA_DE_COMISIONES_DEL_BANCO, description = "CUOTA DE MANEJO"), AccountType.CREDIT_CARD))
        // Revisión 2: la categoría sola no decide; un predial pagado con la tarjeta en «Impuestos» es una compra.
        assertTrue(llevaCuotas(compra.copy(category = CATEGORIA_DE_IMPUESTOS, description = "Predial Medellín"), AccountType.CREDIT_CARD))
        assertTrue(!llevaCuotas(compra.copy(category = "Otros", description = "INTERESES CORRIENTES"), AccountType.CREDIT_CARD))
    }

    @Test
    fun `un pago solo se compara contra la cuota pactada si la cuota es fija`() {
        assertTrue(esCuotaQueSeComparaConLaPactada(terms))
        assertTrue(!esCuotaQueSeComparaConLaPactada(terms.copy(tipoDeCuota = TipoDeCuota.UVR)))
        assertTrue(!esCuotaQueSeComparaConLaPactada(terms.copy(tipoDeCuota = TipoDeCuota.ABONO_CONSTANTE)))
    }

    // ── Vuelta 134 (M-2) · una sola regla para decir «unos» ─────────────────────────────────────

    @Test
    fun `la cifra de un pago es estimada solo si es la cuota pendiente de un credito que cambia`() {
        val cuota = PagoDelPeriodo(
            ruleId = "credit_acc_libre", nombre = "Cuota Libre inversión", monto = 1_080_500, pagado = false,
            diasParaVencer = 3, cuotaQueCambia = LaCuotaQueCambia(pagadaEnOctubre),
        )
        assertTrue(laCifraDelPagoEsEstimada(cuota))
        assertTrue(!laCifraDelPagoEsEstimada(cuota.copy(pagado = true)), "pagada, la cifra es lo que salió de verdad")
        assertTrue(!laCifraDelPagoEsEstimada(cuota.copy(cuotaQueCambia = null)), "una cuota fija se dice tal cual")
        assertEquals("unos \$1.080.500", conUnosSiEsEstimada("\$1.080.500", estimada = true))
        assertEquals("\$1.080.500", conUnosSiEsEstimada("\$1.080.500", estimada = false))
        assertEquals("unos \$1.080.500", cuotaEnPalabras(terms.copy(tipoDeCuota = TipoDeCuota.UVR), pagadaEnOctubre))
    }
}
