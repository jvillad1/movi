package com.jvillada.movi.ui

import com.jvillada.movi.avisos.textoDeVencimientos
import com.jvillada.movi.shared.model.CuotaPagada
import com.jvillada.movi.shared.model.LaCuotaQueCambia
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.notificationRows
import com.jvillada.movi.ui.dashboard.proximoPagoDelInicio
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 134 (M-2) · ningún lugar afirma como exacta una cuota estimada
 *
 * La cuota de un crédito que cambia es la de este mes (`cuotaDeEsteMes`: la última pagada) y la fila de
 * Pagos decía «unos $X» desde la 133. «Próximo pago», el widget «Falta por pagar», «Movi propone hoy», la
 * campana y el aviso del teléfono daban la misma cifra como exacta. Ahora todos preguntan a
 * `laCifraDelPagoEsEstimada` (una sola regla, en `:core`), y un pago de cuota fija no cambia.
 */
class Vuelta134Test {

    private val septiembre = PeriodoFinanciero(2026, 9)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int, cambia: Boolean) = UpcomingPayment(
        rule = RecurringRule(
            id = id, name = nombre, category = "Cuota de crédito", amount = monto, dayOfMonth = 10 + dias,
            type = TransactionType.EXPENSE,
            cuotaQueCambia = if (cambia) LaCuotaQueCambia(CuotaPagada(monto, "2026-08")) else null,
        ),
        dueDate = "2026-09-" + (10 + dias).toString().padStart(2, '0'),
        daysUntil = dias,
        status = PaymentStatus.DUE_TODAY,
    )

    private fun inicio(vararg pagos: UpcomingPayment) = DashboardData(
        upcoming = pagos.toList(),
        ocurrencias = emptyList(),
        periodoActual = septiembre,
        ajustesDePeriodo = PeriodSettings(),
    )

    private val hipotecario = pago("credit_hip", "Cuota Hipotecario 2334", 2_613_714, 0, cambia = true)
    private val carro = pago("credit_carro", "Cuota Vehículo 8761", 4_101_123, 0, cambia = false)

    @Test
    fun `proximo pago, la propuesta y la campana dicen unos solo en la cuota que cambia`() {
        val conLaQueCambia = inicio(hipotecario)
        assertTrue(com.jvillada.movi.shared.model.laCifraDelPagoEsEstimada(proximoPagoDelInicio(conLaQueCambia)!!.pago))
        assertTrue(propuestasDelInicio(conLaQueCambia).single { it.tipo == TipoDePropuesta.PAGO }.cifraEstimada)
        assertTrue(notificationRows(conLaQueCambia).first { it.monto == 2_613_714L }.montoEstimado)

        val fija = inicio(carro)
        assertFalse(com.jvillada.movi.shared.model.laCifraDelPagoEsEstimada(proximoPagoDelInicio(fija)!!.pago))
        assertFalse(propuestasDelInicio(fija).single { it.tipo == TipoDePropuesta.PAGO }.cifraEstimada)
        assertFalse(notificationRows(fija).first { it.monto == 4_101_123L }.montoEstimado)
    }

    @Test
    fun `el aviso del telefono dice unos en la cuota que cambia`() {
        val cambia = PagoDelPeriodo(
            ruleId = "credit_hip", nombre = "Cuota Hipotecario 2334", monto = 2_613_714, pagado = false, diasParaVencer = 1,
            cuotaQueCambia = LaCuotaQueCambia(CuotaPagada(2_613_714, "2026-10")),
        )
        assertEquals("Mañana vence Cuota Hipotecario 2334 unos \$2.613.714", textoDeVencimientos(listOf(cambia))!!.titulo)
        assertEquals("Mañana vence Cuota Hipotecario 2334 \$2.613.714", textoDeVencimientos(listOf(cambia.copy(cuotaQueCambia = null)))!!.titulo)
    }
}
