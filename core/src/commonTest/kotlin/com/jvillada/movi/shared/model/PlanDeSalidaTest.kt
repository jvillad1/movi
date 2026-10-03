package com.jvillada.movi.shared.model

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El plan de salida de deudas** (Ola 4): la amortización mes a mes contra un caso hecho a mano,
 * las deudas sin tasa fuera del cálculo, las ajenas aparte, y avalancha contra bola de nieve.
 */
class PlanDeSalidaTest {

    /** La E.A. que da exactamente 1 % al mes: (1,01¹² − 1) × 100. */
    private val unoPorCientoMensual = (1.01.pow(12) - 1.0) * 100.0

    private fun deuda(
        nombre: String,
        saldo: Long,
        tasaEa: Double?,
        cuota: Long?,
        tipo: TipoDeDeuda = TipoDeDeuda.CREDITO,
        quienLaPaga: String? = null,
        noAmortiza: Long = 0L,
    ) = DeudaParaSalir(
        id = nombre, nombre = nombre, tipo = tipo, saldo = saldo, tasaEa = tasaEa, cuota = cuota,
        quienLaPaga = quienLaPaga, noAmortiza = noAmortiza,
    )

    /**
     * Hecho a mano, con 1 % mensual y cuota de $100.000 sobre $300.000:
     *
     * | mes | interés | capital | queda |
     * |---|---:|---:|---:|
     * | 1 | 3.000 | 97.000 | 203.000 |
     * | 2 | 2.030 | 97.970 | 105.030 |
     * | 3 | 1.050 (1.050,3) | 98.950 | 6.080 |
     * | 4 | 61 (60,8) | — | 0 |
     *
     * 4 cuotas y $6.141 de interés. Con $50.000 extra al mes:
     *
     * | mes | interés | queda tras la cuota | tras el abono |
     * |---|---:|---:|---:|
     * | 1 | 3.000 | 203.000 | 153.000 |
     * | 2 | 1.530 | 54.530 | 4.530 |
     * | 3 | 45 (45,3) | 0 | — |
     *
     * 3 cuotas y $4.575: el abono ahorra $1.566.
     */
    @Test
    fun `la amortizacion coincide con la cuenta hecha a mano`() {
        val plan = planDeSalida(
            listOf(deuda("Crédito", 300_000L, unoPorCientoMensual, 100_000L)),
            abonoMensual = 50_000L,
            estrategia = EstrategiaDeSalida.AVALANCHA,
        )
        val salida = plan.enElCalculo.single()
        assertEquals(4, salida.mesesSinAbono)
        assertEquals(6_141L, salida.interesSinAbono)
        assertEquals(3, salida.mesesConAbono)
        assertEquals(4_575L, salida.interesConAbono)
        assertEquals(1_566L, plan.interesAhorrado)
        assertEquals(3_000L, salida.deuda.interesDelMes)
    }

    @Test
    fun `sin abono el plan dice lo mismo que la pantalla de creditos`() {
        // El Libre inversión 9695 del dueño: saldo, tasa, cuota y seguro reales.
        val d = deuda("Libre inversión 9695", 40_104_518L, 11.27, 1_204_064L, noAmortiza = 124_800L)
        val salida = planDeSalida(listOf(d), 0L, EstrategiaDeSalida.AVALANCHA).enElCalculo.single()
        val oficial = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)
        assertEquals(oficial.mesesHastaLaUltimaCuota, salida.mesesSinAbono)
        assertEquals(oficial.interesPorPagar, salida.interesSinAbono)
        assertEquals(salida.mesesSinAbono, salida.mesesConAbono, "con $0 de abono no cambia nada")
        assertEquals(0L, salida.interesAhorrado)
    }

    @Test
    fun `una deuda sin tasa no entra al calculo y se dice por que`() {
        val plan = planDeSalida(
            listOf(
                deuda("Crediágil", 507_553L, 29.64, 60_000L),
                deuda("Master Black", 27_000_000L, tasaEa = null, cuota = 1_843_014L, tipo = TipoDeDeuda.TARJETA),
                deuda("AMEX", 19_000_000L, tasaEa = 29.6, cuota = null, tipo = TipoDeDeuda.TARJETA),
            ),
            abonoMensual = 100_000L,
            estrategia = EstrategiaDeSalida.AVALANCHA,
        )
        assertEquals(listOf("Crediágil"), plan.enElCalculo.map { it.deuda.nombre })
        assertEquals(
            listOf(PorQueNoEntraAlPlan.FALTA_LA_TASA, PorQueNoEntraAlPlan.FALTA_EL_PAGO_MINIMO),
            plan.faltanDatos.map { it.porQueNoEntra },
        )
        assertNull(plan.faltanDatos.first().interesDelMes, "sin tasa no se estima el interés")
    }

    @Test
    fun `lo que paga la nomina o un tercero va aparte y no recibe el abono`() {
        val plan = planDeSalida(
            listOf(
                deuda("Libranza 4818", 262_386_162L, 18.01, 6_040_259L, quienLaPaga = "tu nómina"),
                deuda("Hipoteca 1254", 768_430_394L, 10.98, 9_147_408L, quienLaPaga = "Skandia"),
                deuda("Crediágil", 507_553L, 29.64, 60_000L),
            ),
            abonoMensual = 100_000L,
            estrategia = EstrategiaDeSalida.AVALANCHA,
        )
        assertEquals(listOf("Crediágil"), plan.enElCalculo.map { it.deuda.nombre })
        assertEquals(listOf("Libranza 4818", "Hipoteca 1254"), plan.ajenas.map { it.nombre })
    }

    @Test
    fun `avalancha va por la tasa, bola de nieve por el saldo, y la avalancha ahorra mas interes`() {
        val cara = deuda("Cara y grande", 5_000_000L, 30.0, 200_000L)
        val barata = deuda("Barata y chica", 1_000_000L, 12.0, 100_000L)
        val avalancha = planDeSalida(listOf(barata, cara), 300_000L, EstrategiaDeSalida.AVALANCHA)
        val bola = planDeSalida(listOf(barata, cara), 300_000L, EstrategiaDeSalida.BOLA_DE_NIEVE)
        assertEquals(listOf("Cara y grande", "Barata y chica"), avalancha.enElCalculo.map { it.deuda.nombre })
        assertEquals(listOf("Barata y chica", "Cara y grande"), bola.enElCalculo.map { it.deuda.nombre })
        assertTrue(avalancha.interesAhorrado > bola.interesAhorrado, "${avalancha.interesAhorrado} vs ${bola.interesAhorrado}")
        // La bola de nieve termina antes la chica: es lo que la hace sentir que avanza.
        val chicaEnBola = bola.enElCalculo.first { it.deuda.nombre == "Barata y chica" }.mesesConAbono!!
        val chicaEnAvalancha = avalancha.enElCalculo.first { it.deuda.nombre == "Barata y chica" }.mesesConAbono!!
        assertTrue(chicaEnBola < chicaEnAvalancha)
    }

    @Test
    fun `una deuda que solo paga intereses no se termina sin abono, y con abono si`() {
        // El Crédito Mamá: la cuota es justo el interés del mes.
        val mama = deuda("Crédito Mamá", 100_000_000L, 16.7652, 1_300_000L)
        val salida = planDeSalida(listOf(mama), 2_000_000L, EstrategiaDeSalida.AVALANCHA).enElCalculo.single()
        assertNull(salida.mesesSinAbono)
        assertNull(salida.interesAhorrado)
        assertTrue((salida.mesesConAbono ?: 0) > 0)
        assertEquals(1, planDeSalida(listOf(mama), 2_000_000L, EstrategiaDeSalida.AVALANCHA).seTerminanSoloConAbono.size)
    }

    @Test
    fun `las deudas salen de creditos y tarjetas, sin saldo no estan`() {
        val credito = CreditSummary(
            account = Account("acc_crediagil", "Crediágil 3090", AccountType.LOAN, 507_553L, "COP"),
            terms = CreditTerms(
                accountId = "acc_crediagil", bank = "Bancolombia", principal = 600_000L, rateEa = 29.64,
                termMonths = 12, installment = 60_000L, dayOfMonth = 15, startDate = "2026-01-01",
                insuranceMonthly = 1_960L,
            ),
            paidPct = 0.15,
            hasMovements = true,
        )
        val pagado = credito.copy(account = credito.account.copy(id = "acc_pagado", balance = 0L))
        val tarjeta = CardSummary(
            account = Account("acc_master", "Master Black", AccountType.CREDIT_CARD, 27_000_000L, "COP"),
            terms = CardTerms(accountId = "acc_master", bank = "Bancolombia", paymentDay = 2, pagoMinimo = 1_843_014L, tasaEa = 29.6),
        )
        val deudas = deudasParaSalir(listOf(credito, pagado), listOf(tarjeta))
        assertEquals(listOf("Crediágil 3090", "Master Black"), deudas.map { it.nombre })
        assertEquals(1_960L, deudas.first().noAmortiza)
        assertEquals(1_843_014L, deudas.last().cuota)
        assertEquals(29.6, deudas.last().tasaEa)
    }
}
