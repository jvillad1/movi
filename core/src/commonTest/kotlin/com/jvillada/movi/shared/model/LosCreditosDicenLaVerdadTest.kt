package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 59: los créditos dicen la verdad.** Las reglas puras de `:core`: la deuda total sin deudas
 * que resten, un solo interés del mes, la última cuota contada desde la primera sin pagar y el saldo
 * que propone el plan en el alta.
 */
class LosCreditosDicenLaVerdadTest {

    private val nu = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_583_000L, "COP")
    private val vehiculo = Account("acc_vehiculo", "Vehículo 4083", AccountType.LOAN, -980_000L, "COP")
    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, 5_000_000L, "COP")

    // ── 1. La deuda total ────────────────────────────────────────────────────

    /** El caso de la revisión: «Deuda total $603.000» = $1.583.000 de Nu − $980.000 del vehículo. */
    @Test
    fun `una deuda en negativo no resta de la deuda total`() {
        assertEquals(1_583_000L, deudaTotalDe(listOf(nu, vehiculo, ahorros)))
        assertEquals(listOf("acc_vehiculo"), deudasSinSaldoDe(listOf(nu, vehiculo, ahorros)).map { it.id })
        assertEquals(0L, deudaQueSumaDe(vehiculo))
    }

    @Test
    fun `una deuda en dolares suma su estimado y un bien no es deuda`() {
        val enDolares = Account("acc_usd", "Master USD", AccountType.CREDIT_CARD, 0L, "USD", estimatedTotalCop = 700_000L)
        assertEquals(2_283_000L, deudaTotalDe(listOf(nu, enDolares)))
        assertTrue(deudasSinSaldoDe(listOf(nu, enDolares)).isEmpty())
    }

    /**
     * **La decisión sobre [patrimonioDe]**: sigue sumando cada deuda con su signo. Una tarjeta
     * sobrepagada es plata a favor, y el neto la cuenta; la deuda total de Créditos no la resta.
     */
    @Test
    fun `el patrimonio sigue contando la deuda con su signo`() {
        val p = patrimonioDe(listOf(nu, vehiculo, ahorros))
        assertEquals(603_000L, p.deudas)
        assertEquals(5_000_000L - 603_000L, p.neto)
    }

    // ── 2. Un solo interés del mes ───────────────────────────────────────────

    private val unoPorCientoMensual = (1.01.pow(12) - 1.0) * 100.0

    private fun plan(saldo: Long, propio: Boolean = true) =
        planDeUnaDeuda(saldo, unoPorCientoMensual, 100_000_000L, null, null, saleDeTuBolsillo = propio)

    private fun tarjeta(saldo: Long, tasa: Double?, moneda: String = "COP") = CardSummary(
        account = Account("acc_t$saldo", "Tarjeta", AccountType.CREDIT_CARD, saldo, moneda),
        terms = CardTerms(accountId = "acc_t$saldo", bank = "Nu", paymentDay = 7, tasaEa = tasa),
    )

    @Test
    fun `el interes del mes sin tarjetas es el de los creditos`() {
        val i = interesDelMes(listOf(plan(10_000_000L), plan(5_000_000L, propio = false)), emptyList())
        assertEquals(100_000L, i.creditosPropios)
        assertEquals(50_000L, i.ajeno)
        assertEquals(0L, i.tarjetas)
        assertEquals(100_000L, i.propio)
    }

    /** El caso de la revisión: Créditos decía $466.775 y «Cómo salir…» $499.677, con la tarjeta adentro. */
    @Test
    fun `el interes del mes con tarjeta la suma a lo que pagas tu`() {
        val i = interesDelMes(listOf(plan(10_000_000L)), listOf(tarjeta(2_000_000L, unoPorCientoMensual)))
        assertEquals(20_000L, i.tarjetas)
        assertEquals(120_000L, i.propio)
        // La misma cifra que daría cada deuda en «Cómo salir de tus deudas».
        assertEquals(20_000L, deudasParaSalir(emptyList(), listOf(tarjeta(2_000_000L, unoPorCientoMensual))).single().interesDelMes)
    }

    @Test
    fun `una tarjeta sin tasa, sin deuda o en dolares no suma interes`() {
        val i = interesDelMes(
            emptyList(),
            listOf(tarjeta(2_000_000L, null), tarjeta(-50_000L, 28.0), tarjeta(300L, 28.0, moneda = "USD")),
        )
        assertEquals(0L, i.propio)
    }

    // ── 3. La última cuota ───────────────────────────────────────────────────

    private val hoy = LocalDate(2026, 10, 29)

    /** «Próxima 5 de noviembre · te faltan 49 cuotas · la última en octubre de 2030» — era noviembre. */
    @Test
    fun `con la cuota del mes pagada la primera sin pagar es la del mes que viene`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(dia = 5, hoy = hoy, pagadas = listOf("2026-09", "2026-10"))
        assertEquals(PeriodoFinanciero(2026, 11), primera)
        assertEquals(PeriodoFinanciero(2030, 11), mesDeLaUltimaCuota(primera, 49))
    }

    /** Sin pagar, la cuota de octubre sigue en el saldo: es la primera de las que faltan. */
    @Test
    fun `con la cuota del mes sin pagar la primera es la del mes`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(dia = 5, hoy = hoy, pagadas = listOf("2026-09"))
        assertEquals(PeriodoFinanciero(2026, 10), primera)
        assertEquals(PeriodoFinanciero(2030, 10), mesDeLaUltimaCuota(primera, 49))
        // Por vencer este mes y sin pagar, también.
        assertEquals(PeriodoFinanciero(2026, 10), mesDeLaPrimeraCuotaSinPagar(dia = 30, hoy = hoy, pagadas = emptyList()))
    }

    /**
     * **El caso de la revisión**: Vehículo 4083, día 5, primera cuota en febrero de 2026 y 60 cuotas. El
     * pago del 28 de octubre salda octubre (como en Pagos), la próxima es el 5 de noviembre y le faltan
     * 51: la última es la próxima + 51 − 1 = enero de 2031, no diciembre de 2030.
     */
    @Test
    fun `con el pago del 28 de octubre la ultima es la proxima mas N menos 1`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(dia = 5, hoy = hoy, pagadas = listOf("2026-10"), desde = LocalDate(2026, 1, 10))
        assertEquals(PeriodoFinanciero(2026, 11), primera)
        assertEquals(PeriodoFinanciero(proximoVencimientoDelDia(5, hoy).year, proximoVencimientoDelDia(5, hoy).monthNumber), primera)
        assertEquals(PeriodoFinanciero(2031, 1), mesDeLaUltimaCuota(primera, 51))
        // Febrero de 2026 + 60 cuotas − 1 = enero de 2031: la misma fecha contada desde el principio.
        assertEquals(PeriodoFinanciero(2031, 1), mesDeLaUltimaCuota(PeriodoFinanciero(2026, 2), 60))
    }

    /**
     * **El crédito recién creado** con la deuda de hoy: decía «diciembre de 2030» con `cuotasPagadas: []`
     * aunque su saldo ya descontaba la cuota de octubre. La que venció antes de fijar el saldo cuenta
     * como pagada.
     */
    @Test
    fun `en un credito recien creado la cuota que vencio antes del saldo cuenta como pagada`() {
        assertEquals(
            PeriodoFinanciero(2026, 11),
            mesDeLaPrimeraCuotaSinPagar(dia = 5, hoy = hoy, pagadas = emptyList(), saldoFijadoEl = hoy),
        )
        // Fijado antes del vencimiento de este mes, esa cuota sigue debiéndose.
        assertEquals(
            PeriodoFinanciero(2026, 10),
            mesDeLaPrimeraCuotaSinPagar(dia = 30, hoy = hoy, pagadas = emptyList(), saldoFijadoEl = LocalDate(2026, 10, 3)),
        )
        val recienCreado = CreditSummary(
            account = Account("acc_v", "Vehículo 4083", AccountType.LOAN, 35_830_084L, "COP"),
            terms = CreditTerms(
                accountId = "acc_v", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
                termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-02",
            ),
            paidPct = 0.1,
            cuotasPagadas = emptyList(),
            // 29-oct-2026, mediodía en Bogotá.
            saldoFijadoEn = 1_793_293_200_000L,
        )
        assertEquals(PeriodoFinanciero(2026, 11), mesDeLaPrimeraCuota(recienCreado, hoy))
    }

    @Test
    fun `una cuota pagada por adelantado corre la primera`() {
        assertEquals(
            PeriodoFinanciero(2026, 12),
            mesDeLaPrimeraCuotaSinPagar(dia = 30, hoy = hoy, pagadas = listOf("2026-10", "2026-11")),
        )
    }

    /** Nu: «10 cuotas, hasta julio de 2027» — era agosto (próximo pago el 7 de noviembre). */
    @Test
    fun `sin saber que se pago se toma el mes del proximo vencimiento`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(dia = 7, hoy = hoy, pagadas = null)
        assertEquals(PeriodoFinanciero(2026, 11), primera)
        assertEquals(PeriodoFinanciero(2027, 8), mesDeLaUltimaCuota(primera, 10))
    }

    @Test
    fun `la primera cuota de un credito nuevo va despues del desembolso`() {
        assertEquals(
            PeriodoFinanciero(2026, 11),
            mesDeLaPrimeraCuotaSinPagar(dia = 5, hoy = hoy, pagadas = emptyList(), desde = LocalDate(2026, 10, 20)),
        )
        assertEquals(
            PeriodoFinanciero(2026, 11),
            mesDeLaPrimeraCuotaSinPagar(dia = 20, hoy = LocalDate(2026, 10, 21), pagadas = emptyList(), desde = LocalDate(2026, 10, 20)),
        )
    }

    @Test
    fun `la primera cuota de un credito usa sus cuotas pagadas`() {
        val credito = CreditSummary(
            account = Account("acc_v", "Vehículo", AccountType.LOAN, 30_000_000L, "COP"),
            terms = CreditTerms(
                accountId = "acc_v", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
                termMonths = 60, installment = 1_000_000L, dayOfMonth = 5, startDate = "2026-01-10",
            ),
            paidPct = 0.25,
            cuotasPagadas = listOf("2026-10"),
        )
        assertEquals(PeriodoFinanciero(2026, 11), mesDeLaPrimeraCuota(credito, hoy))
        assertEquals(PeriodoFinanciero(2026, 10), mesDeLaPrimeraCuota(credito.copy(cuotasPagadas = emptyList()), hoy))
        assertNull(mesDeLaPrimeraCuota(credito.copy(terms = null), hoy))
    }

    @Test
    fun `la ultima de todas cuenta cada credito desde su primera y una que no se termina le gana`() {
        val corto = plan(1_000_000L) // se termina en una cuota
        val largo = planDeUnaDeuda(10_000_000L, unoPorCientoMensual, 1_000_000L, null, null, saleDeTuBolsillo = true)
        val n = largo.mesesHastaLaUltimaCuota!!
        val ultima = ultimaCuotaDeTodas(listOf(corto to PeriodoFinanciero(2026, 10), largo to PeriodoFinanciero(2026, 11)))
        assertEquals(mesDeLaUltimaCuota(PeriodoFinanciero(2026, 11), n), ultima)
        val quieto = planDeUnaDeuda(10_000_000L, unoPorCientoMensual, 50_000L, null, null, saleDeTuBolsillo = true)
        assertNull(ultimaCuotaDeTodas(listOf(largo to PeriodoFinanciero(2026, 11), quieto to PeriodoFinanciero(2026, 11))))
    }

    @Test
    fun `el pago de una cuota es la pata que baja la deuda con su categoria`() {
        fun ev(tipo: TransactionType, categoria: String) = FinancialEvent(
            id = "e", accountId = "acc_v", type = tipo, amount = 1L, currency = "COP", timestamp = 0L,
            category = categoria, description = "x",
        )
        assertTrue(esPagoDeUnaCuota(ev(TransactionType.INCOME, CUOTA_CATEGORY)))
        assertTrue(esPagoDeUnaCuota(ev(TransactionType.INCOME, PAYROLL_DEDUCTION_CATEGORY)))
        assertTrue(esPagoDeUnaCuota(ev(TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY)))
        assertTrue(!esPagoDeUnaCuota(ev(TransactionType.EXPENSE, CUOTA_CATEGORY)))
        assertTrue(!esPagoDeUnaCuota(ev(TransactionType.INCOME, "Ajuste de saldo")))
    }

    // ── 4. El saldo que propone el plan ──────────────────────────────────────

    /** Sin intereses se hace a mano: $1.200.000, cuota $100.000 el 15, desde el 10 de enero → 10 cuotas. */
    @Test
    fun `el plan propone el saldo de hoy descontando las cuotas vencidas`() {
        val r = saldoSegunElPlan(
            capital = 1_200_000L, tasaEa = null, sinIntereses = true, plazoMeses = 12, cuota = 100_000L,
            seguroMensual = null, otrosCargosMensuales = null, diaDePago = 15,
            desde = LocalDate(2026, 1, 10), hoy = hoy,
        )
        assertEquals(10, r.cuotasVencidas)
        assertEquals(200_000L, r.saldo)
        assertNull(r.falta)
    }

    /** Con tasa, cada cuota es la de [desglosarCuota], la misma que separa cada pago registrado. */
    @Test
    fun `con tasa cada cuota se desglosa como un pago registrado`() {
        val r = saldoSegunElPlan(
            capital = 40_000_000L, tasaEa = 18.0, sinIntereses = false, plazoMeses = 60, cuota = 1_000_000L,
            seguroMensual = 20_000L, otrosCargosMensuales = null, diaDePago = 5,
            desde = LocalDate(2026, 1, 2), hoy = hoy,
        )
        var saldo = 40_000_000L
        repeat(10) {
            saldo -= desglosarCuota(1_000_000L, AccountType.LOAN, saldo, 18.0, 20_000L, null, 0L).capital
        }
        assertEquals(10, r.cuotasVencidas)
        assertEquals(saldo, r.saldo)
        assertTrue(r.saldo!! in 34_000_000L..36_000_000L, "unos $34,5M: ${r.saldo}")
    }

    @Test
    fun `sin un dato el plan no supone cero, dice cual falta`() {
        fun falta(tasa: Double? = 18.0, cuota: Long? = 1_000_000L, desde: LocalDate? = LocalDate(2026, 1, 2), capital: Long? = 40_000_000L) =
            saldoSegunElPlan(capital, tasa, false, 60, cuota, null, null, 5, desde, hoy)
        assertEquals(FaltaParaElSaldoDelPlan.TASA, falta(tasa = null).falta)
        assertEquals(FaltaParaElSaldoDelPlan.CUOTA, falta(cuota = null).falta)
        assertEquals(FaltaParaElSaldoDelPlan.FECHA, falta(desde = null).falta)
        assertEquals(FaltaParaElSaldoDelPlan.CAPITAL, falta(capital = null).falta)
        assertNull(falta(tasa = null).saldo)
    }

    @Test
    fun `un plan que ya termino propone cero y no pasa del plazo`() {
        val r = saldoSegunElPlan(
            capital = 300_000L, tasaEa = null, sinIntereses = true, plazoMeses = 3, cuota = 100_000L,
            seguroMensual = null, otrosCargosMensuales = null, diaDePago = 15,
            desde = LocalDate(2026, 1, 10), hoy = hoy,
        )
        assertEquals(0L, r.saldo)
        assertEquals(3, r.cuotasVencidas)
    }
}
