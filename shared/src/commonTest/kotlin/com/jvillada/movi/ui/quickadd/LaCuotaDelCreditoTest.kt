package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vuelta 48 · las piezas puras de «Cuota de X · mes» en Agregar → Cuota. */
class LaCuotaDelCreditoTest {

    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)
    private val nu = Account("nt", "Nu", AccountType.CREDIT_CARD, 1_683_000)
    private val ahorros = Account("a1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val terms = CreditTerms(
        accountId = libre.id, bank = "Bancolombia", principal = 50_000_000, rateEa = 11.27, termMonths = 60,
        installment = 1_204_064, dayOfMonth = 5, startDate = "2024-06-05", debitoAutomaticoDesde = ahorros.id,
    )

    @Test
    fun `el encabezado dice que se anota`() {
        assertEquals("Cuota de Libre inversión 9695 · octubre", encabezadoDeLaCuota(libre, abonoExtra = false, mes = "octubre"))
        assertEquals("Abono extra a Libre inversión 9695", encabezadoDeLaCuota(libre, abonoExtra = true, mes = "octubre"))
        assertEquals("Pago de Nu · octubre", encabezadoDeLaCuota(nu, abonoExtra = false, mes = "octubre"))
        assertEquals("Cuota de Libre inversión 9695", encabezadoDeLaCuota(libre, abonoExtra = false, mes = ""))
        assertEquals(CUOTA_SIN_ELEGIR, encabezadoDeLaCuota(null, abonoExtra = false, mes = "octubre"))
    }

    @Test
    fun `la cuota propuesta es la de las condiciones, solo en un credito`() {
        assertEquals(1_204_064L, cuotaPropuesta(libre, terms))
        assertNull(cuotaPropuesta(nu, terms), "una tarjeta no propone: su monto es el saldo")
        assertNull(cuotaPropuesta(libre, null))
        assertNull(cuotaPropuesta(libre, terms.copy(installment = 0)))
    }

    @Test
    fun `sale de la cuenta del debito automatico si se puede elegir`() {
        assertEquals(ahorros.id, cuentaDelDebitoAutomatico(terms, listOf(ahorros)))
        assertNull(cuentaDelDebitoAutomatico(terms, emptyList()))
        assertNull(cuentaDelDebitoAutomatico(terms.copy(debitoAutomaticoDesde = null), listOf(ahorros)))
    }

    @Test
    fun `el mes es el del periodo del duenno`() {
        val elVeintisiete = LocalDateTime(2026, 10, 27, 12, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()
        assertEquals("octubre", mesDelPeriodoDeHoy(null, elVeintisiete))
        // Con corte 25, el 27 de octubre ya es el período de noviembre.
        assertEquals("noviembre", mesDelPeriodoDeHoy(PeriodSettings(cutoffDay = 25), elVeintisiete))
    }

    @Test
    fun `la deuda de hoy, antes del monto`() {
        assertEquals("Deuda de Nu: \$1.683.000", deudaDeHoy(nu))
        assertNull(deudaDeHoy(null))
    }
}
