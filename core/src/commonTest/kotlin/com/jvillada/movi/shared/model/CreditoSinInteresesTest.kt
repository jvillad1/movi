package com.jvillada.movi.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Este crédito no cobra intereses»
 *
 * Tasa 0 **sin** la casilla sigue siendo «no sabemos la tasa» (el Crédito Techo Gardenera del
 * dueño, ver `PlanDelCreditoTest`); tasa 0 **con** la casilla es un préstamo de la familia que sí
 * se proyecta: la cuota entera menos seguro y otros cargos baja la deuda, y hay fecha de fin.
 */
class CreditoSinInteresesTest {

    // ── El plan ─────────────────────────────────────────────────────────────────

    @Test
    fun `sin intereses proyecta saldo entre cuota, redondeado para arriba`() {
        // $10.000.000 a $1.500.000 al mes: seis cuotas llenas y una séptima de $1.000.000.
        val plan = planDeUnaDeuda(10_000_000L, rateEa = 0.0, cuota = 1_500_000L, seguroMensual = null, otrosCargosMensuales = null, saleDeTuBolsillo = true, sinIntereses = true)

        assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa)
        assertEquals(0L, plan.interes)
        assertEquals(1_500_000L, plan.capital)
        assertEquals(7, plan.mesesHastaLaUltimaCuota)
        assertEquals(0L, plan.interesPorPagar)
        assertTrue(plan.sinIntereses)
        assertFalse(plan.noSeTermina)
    }

    @Test
    fun `el seguro y los otros cargos no bajan la deuda tampoco sin intereses`() {
        // Capital por mes: 1.000.000 − 100.000 − 50.000 = 850.000 → 12 cuotas para $10.000.000.
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 1_000_000L, 100_000L, 50_000L, saleDeTuBolsillo = true, sinIntereses = true)

        assertEquals(850_000L, plan.capital)
        assertEquals(12, plan.mesesHastaLaUltimaCuota)
    }

    @Test
    fun `tasa en cero sin la casilla sigue siendo sin tasa`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 1_500_000L, null, null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.SIN_TASA, plan.comoVa)
        assertNull(plan.mesesHastaLaUltimaCuota)
        assertFalse(plan.sinIntereses)
    }

    @Test
    fun `si la cuota no alcanza ni para el seguro no se inventa una fecha`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 100_000L, 100_000L, null, saleDeTuBolsillo = true, sinIntereses = true)

        assertNull(plan.mesesHastaLaUltimaCuota)
        assertTrue(plan.comoVa != ComoVaLaDeuda.AMORTIZA && plan.comoVa != ComoVaLaDeuda.SIN_TASA, "${plan.comoVa}")
    }

    @Test
    fun `sin deuda sigue siendo sin deuda y sin cuota sigue siendo sin cuota`() {
        assertEquals(ComoVaLaDeuda.SIN_DEUDA, planDeUnaDeuda(0L, 0.0, 1_000_000L, null, null, true, sinIntereses = true).comoVa)
        val sinCuota = planDeUnaDeuda(5_000_000L, 0.0, 0L, null, null, true, sinIntereses = true)
        assertEquals(ComoVaLaDeuda.SIN_CUOTA, sinCuota.comoVa)
        assertEquals(0L, sinCuota.interes)
    }

    @Test
    fun `el plan de un credito cargado lee la casilla de sus condiciones`() {
        val credito = CreditSummary(
            account = Account("acc_papa", "Préstamo papá", AccountType.LOAN, 10_000_000L),
            terms = condiciones(sinIntereses = true),
            paidPct = null,
        )
        val plan = assertNotNull(planDelCredito(credito))
        assertEquals(7, plan.mesesHastaLaUltimaCuota)

        val sinCasilla = assertNotNull(planDelCredito(credito.copy(terms = condiciones(sinIntereses = false))))
        assertEquals(ComoVaLaDeuda.SIN_TASA, sinCasilla.comoVa)
    }

    @Test
    fun `el simulador de abono y el abono minimo tambien leen la casilla`() {
        val credito = CreditSummary(
            account = Account("acc_papa", "Préstamo papá", AccountType.LOAN, 10_000_000L),
            terms = condiciones(sinIntereses = true),
            paidPct = null,
        )
        val sim = assertNotNull(simularAbonoUnico(credito, 3_000_000L))
        assertEquals(QueLograElAbono.ACORTA_EL_PLAZO, sim.logro)
        assertEquals(2, sim.cuotasQueSeAhorra)
        assertEquals(0L, sim.interesQueSeAhorra)
        // Ya se termina sin abono: no hay mínimo que preguntar.
        assertNull(abonoMinimoParaQueSeTermine(credito))
    }

    // ── El desglose de una cuota ────────────────────────────────────────────────

    @Test
    fun `sin intereses toda la cuota menos seguro y otros va a capital`() {
        val d = desglosarCuota(1_000_000L, AccountType.LOAN, 10_000_000L, 0.0, 100_000L, 50_000L, yaCobradoEnElMes = 0L, sinIntereses = true)

        assertEquals(MotivoDelDesglose.AMORTIZA, d.motivo)
        assertEquals(0L, d.interes)
        assertEquals(100_000L, d.seguro)
        assertEquals(50_000L, d.otrosCargos)
        assertEquals(850_000L, d.capital)
    }

    @Test
    fun `sin intereses y sin cargos la cuota entera baja la deuda, pero no como sin tasa`() {
        val d = desglosarCuotaRegistrada(1_000_000L, AccountType.LOAN, 10_000_000L, 0.0, null, null, interesReal = null, yaCobradoEnElMes = 0L, sinIntereses = true)

        assertEquals(MotivoDelDesglose.AMORTIZA, d.motivo)
        assertEquals(1_000_000L, d.capital)
    }

    @Test
    fun `la casilla no toca a una tarjeta`() {
        val d = desglosarCuota(500_000L, AccountType.CREDIT_CARD, 2_000_000L, 0.0, null, null, yaCobradoEnElMes = 0L, sinIntereses = true)
        assertEquals(MotivoDelDesglose.TARJETA, d.motivo)
    }

    // ── La validación y el wire ─────────────────────────────────────────────────

    @Test
    fun `una tasa positiva con la casilla marcada se rechaza, y en cero pasa`() {
        assertEquals(TASA_EN_CREDITO_SIN_INTERESES, validarTasaDelCredito(condiciones(sinIntereses = true, rateEa = 12.0)))
        assertNull(validarTasaDelCredito(condiciones(sinIntereses = true, rateEa = 0.0)))
        assertNull(validarTasaDelCredito(condiciones(sinIntereses = false, rateEa = 12.0)))
    }

    @Test
    fun `la casilla viaja siempre y un cuerpo viejo sin ella se lee como false`() {
        val encoded = Json.encodeToString(condiciones(sinIntereses = false))
        assertTrue("\"sinIntereses\":false" in encoded, "sin la clave, desmarcar no apagaría la casilla: $encoded")

        val viejo = """{"accountId":"a","bank":"X","principal":1,"rateEa":0.0,"termMonths":1,
            "installment":1,"dayOfMonth":1,"startDate":"2026-01-01"}"""
        assertFalse(Json { ignoreUnknownKeys = true }.decodeFromString<CreditTerms>(viejo).sinIntereses)
    }

    private fun condiciones(sinIntereses: Boolean, rateEa: Double = 0.0) = CreditTerms(
        accountId = "acc_papa",
        bank = "Papá",
        principal = 10_000_000L,
        rateEa = rateEa,
        termMonths = 7,
        installment = 1_500_000L,
        dayOfMonth = 5,
        startDate = "2026-01-05",
        sinIntereses = sinIntereses,
    )
}
