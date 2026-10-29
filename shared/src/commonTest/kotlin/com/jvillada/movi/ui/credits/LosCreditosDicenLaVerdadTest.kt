package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import com.jvillada.movi.shared.model.FaltaParaElSaldoDelPlan
import com.jvillada.movi.shared.model.InteresDelMes
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.SaldoSegunElPlan
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.planDeSalida
import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 59: los créditos dicen la verdad**, lo que dice la pantalla. Las cuentas están en
 * `LosCreditosDicenLaVerdadTest` de `:core`; acá, las frases y lo que decide la lista.
 */
class LosCreditosDicenLaVerdadTest {

    private val hoy = LocalDate(2026, 10, 29)

    private fun credito(deuda: Long, conMovimientos: Boolean = true, pagadas: List<String>? = null) = CreditSummary(
        account = Account("acc_vehiculo", "Vehículo 4083", AccountType.LOAN, deuda, "COP"),
        terms = CreditTerms(
            accountId = "acc_vehiculo", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
            termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
        ),
        paidPct = null,
        hasMovements = conMovimientos,
        cuotasPagadas = pagadas,
    )

    private fun tarjeta(deuda: Long) = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, deuda, "COP", balancesByCurrency = mapOf("COP" to deuda)),
        terms = CardTerms(accountId = "acc_nu", bank = "Nu", paymentDay = 7),
    )

    // ── Deuda total y un solo signo ──────────────────────────────────────────

    @Test
    fun `la deuda total no resta el credito sin saldo`() {
        assertEquals(1_583_000L, totalDebtCop(listOf(credito(-980_000L)), listOf(tarjeta(1_583_000L))))
    }

    @Test
    fun `el saldo de una deuda se dice con un solo signo`() {
        assertEquals("Debes \$1.583.000", saldoDeUnaDeuda(1_583_000L).texto)
        assertEquals(SIN_SALDO_EN_MOVI, saldoDeUnaDeuda(-980_000L).texto)
        assertFalse(saldoDeUnaDeuda(-980_000L).conSaldo)
        assertEquals("\$0", saldoDeUnaDeuda(-980_000L).magnitud, "nunca la cifra con el signo al revés")
        // En cero: pagada si se llegó ahí con movimientos; sin saldo si no.
        assertEquals("Debes \$0", saldoDeUnaDeuda(0L, pagada = true).texto)
        assertEquals(SIN_SALDO_EN_MOVI, saldoDeUnaDeuda(0L, pagada = false).texto)
        assertEquals("Debes US\$181", saldoDeUnaDeuda(181L, "USD").texto)
    }

    /** La lista dice lo mismo que la ficha: la píldora «Sin saldo en Movi» y qué hacer. */
    @Test
    fun `en la lista un credito sin saldo lleva la pildora y no su cifra`() {
        val fila = prestamoEnLaLista(credito(-980_000L), plan = null, periodoActual = PeriodoFinanciero(2026, 10), hoy = hoy)
        assertTrue(fila.sinSaldo)
        assertFalse(fila.pagada)
        assertEquals(FALTA_EL_SALDO_DE_LA_DEUDA, fila.nota?.texto)
        assertNull(fila.fraccionPagada)
        assertFalse(prestamoEnLaLista(credito(30_000_000L), null, PeriodoFinanciero(2026, 10), hoy).sinSaldo)
        assertTrue(prestamoEnLaLista(credito(0L), null, PeriodoFinanciero(2026, 10), hoy).pagada)
    }

    @Test
    fun `una tarjeta en negativo tampoco dice su cifra con el signo al reves`() {
        val fila = tarjetaEnLaLista(tarjeta(-50_000L), hoy)
        assertTrue(fila.sinSaldo)
        assertEquals(FALTA_EL_SALDO_DE_LA_DEUDA, fila.nota?.texto)
        assertFalse(tarjetaEnLaLista(tarjeta(1_583_000L), hoy).sinSaldo)
    }

    @Test
    fun `la linea de las deudas sin saldo las cuenta por tipo`() {
        assertNull(textoDeLasDeudasSinSaldo(0, 0))
        assertEquals("1 crédito sin saldo", textoDeLasDeudasSinSaldo(1, 0))
        assertEquals("2 créditos sin saldo", textoDeLasDeudasSinSaldo(2, 0))
        assertEquals("1 tarjeta sin saldo", textoDeLasDeudasSinSaldo(0, 1))
        assertEquals("3 deudas sin saldo", textoDeLasDeudasSinSaldo(2, 1))
    }

    // ── El saldo que propone el plan ─────────────────────────────────────────

    @Test
    fun `el alta propone el saldo del plan o dice que dato falta`() {
        assertEquals("Por tu plan deberías unos \$34,5M", textoDelSaldoDelPlan(SaldoSegunElPlan(saldo = 34_512_345L, cuotasVencidas = 9)))
        assertEquals("Por tu plan ya lo terminaste de pagar", textoDelSaldoDelPlan(SaldoSegunElPlan(saldo = 0L)))
        assertEquals(
            "Para proponerte la deuda, falta la tasa",
            textoDelSaldoDelPlan(SaldoSegunElPlan(falta = FaltaParaElSaldoDelPlan.TASA)),
        )
    }

    // ── Un solo interés del mes ──────────────────────────────────────────────

    @Test
    fun `el rotulo del interes dice que las tarjetas estan adentro cuando cobran`() {
        assertEquals(ALCANCE_INTERES_PROPIO, alcanceDelInteresPropio(InteresDelMes(466_775L, 0L, 0L)))
        assertEquals(ALCANCE_INTERES_PROPIO_CON_TARJETAS, alcanceDelInteresPropio(InteresDelMes(466_775L, 0L, 32_902L)))
        assertEquals(
            "\$466.775 de tus créditos y \$32.902 de tus tarjetas. La misma cifra de Créditos.",
            desgloseDelInteresDelMes(InteresDelMes(466_775L, 0L, 32_902L)),
        )
    }

    // ── La última cuota ──────────────────────────────────────────────────────

    @Test
    fun `la ultima cuota de todas se nombra o se dice sin fecha`() {
        assertEquals("noviembre de 2030", textoDeLaUltimaCuota(PeriodoFinanciero(2030, 11)))
        assertEquals("Sin fecha", textoDeLaUltimaCuota(null))
    }

    @Test
    fun `cada deuda de la salida se cuenta desde su primera cuota sin pagar`() {
        val primeras = primerasCuotasDe(
            listOf(credito(30_000_000L, pagadas = listOf("2026-10"))),
            listOf(tarjeta(1_583_000L)),
            hoy,
        )
        assertEquals(PeriodoFinanciero(2026, 11), primeras["acc_vehiculo"])
        // Nu: próximo pago el 7 de noviembre.
        assertEquals(PeriodoFinanciero(2026, 11), primeras["acc_nu"])
        assertEquals(PeriodoFinanciero(2026, 10), primerasCuotasDe(listOf(credito(1L, pagadas = emptyList())), emptyList(), hoy)["acc_vehiculo"])
    }

    /** «10 cuotas, hasta julio de 2027» era agosto: contada desde la primera sin pagar. */
    @Test
    fun `nu dice hasta agosto de 2027`() {
        assertEquals("10 cuotas, hasta agosto de 2027", cuandoTermina(10, PeriodoFinanciero(2026, 11)))
    }

    @Test
    fun `el simulador sale en el mes de la ultima cuota contada desde la primera de cada deuda`() {
        val unoPorCientoMensual = (1.01.pow(12) - 1.0) * 100.0
        val plan = planDeSalida(
            listOf(DeudaParaSalir("c", "Crédito", TipoDeDeuda.CREDITO, 300_000L, unoPorCientoMensual, cuota = 100_000L)),
            50_000L,
            EstrategiaDeSalida.AVALANCHA,
        )
        // 3 cuotas con abono, 4 sin: desde noviembre, sale en enero y un mes antes que sin abono.
        val lineas = resumenDelSimulador(plan, PeriodoFinanciero(2026, 10), mapOf("c" to PeriodoFinanciero(2026, 11)))
        assertEquals("Sales de estas deudas en enero de 2027, 1 mes antes que sin abono.", lineas[1])
    }
}
