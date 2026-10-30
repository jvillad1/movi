package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 77 · **un préstamo sin saldo cuenta cero en el patrimonio** (la única regla de plata nueva de la
 * vuelta). Lo que no puede moverse: un dueño con créditos sanos y una tarjeta con saldo a favor da
 * exactamente las cifras de antes (la cuenta de antes está escrita a mano acá: cada deuda con su signo).
 */
class ElPrestamoSinSaldoCuentaCeroTest {

    private val ahorros = Account("acc_ah", "Ahorros", AccountType.SAVINGS, 5_000_000L)
    private val fiducuenta = Account("acc_fi", "Fiducuenta", AccountType.INVESTMENT, 2_000_000L)
    private val hipoteca = Account("acc_hip", "Hipoteca", AccountType.LOAN, 200_000_000L)
    private val libre = Account("acc_lib", "Libre inversión", AccountType.LOAN, 18_500_000L)
    private val nuAFavor = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, -120_000L)
    private val master = Account("acc_mb", "Master", AccountType.CREDIT_CARD, 1_583_000L)
    private val vehiculoSinSaldo = Account("acc_veh", "Vehículo 4083", AccountType.LOAN, -980_000L)

    /** La regla de antes de la vuelta 77, escrita a mano: cada deuda con su signo. */
    private fun deudasConSuSigno(cuentas: List<Account>): Long =
        cuentas.filter { esCuentaDeDeuda(it.type) && !it.esBien }.sumOf { valorEnPesosDe(it) }

    @Test
    fun `datos sanos y una tarjeta a favor dan exactamente lo de antes`() {
        val sanos = listOf(ahorros, fiducuenta, hipoteca, libre, nuAFavor, master)
        val p = patrimonioDe(sanos)
        assertEquals(deudasConSuSigno(sanos), p.deudas)
        assertEquals(200_000_000L + 18_500_000L - 120_000L + 1_583_000L, p.deudas)
        assertEquals(7_000_000L - p.deudas, p.neto)
        assertTrue(prestamosSinSaldoDe(sanos).isEmpty())
    }

    @Test
    fun `la tarjeta a favor sigue con su signo y el prestamo sin saldo cuenta cero`() {
        val p = patrimonioDe(listOf(ahorros, master, nuAFavor, vehiculoSinSaldo))
        assertEquals(1_583_000L - 120_000L, p.deudas)
        assertEquals(5_000_000L - 1_463_000L, p.neto)
        assertTrue(prestamoSinSaldo(vehiculoSinSaldo))
        assertFalse(prestamoSinSaldo(nuAFavor), "una tarjeta a favor es plata real")
        assertFalse(prestamoSinSaldo(libre))
    }

    @Test
    fun `deudas dice lo mismo que la deuda total sin tarjetas a favor`() {
        val cuentas = listOf(ahorros, master, vehiculoSinSaldo)
        assertEquals(deudaTotalDe(cuentas), patrimonioDe(cuentas).deudas)
    }

    @Test
    fun `todo prestamo que creditoSinSaldo marca pesa cero`() {
        val terms = CreditTerms("acc_veh", "Occidente", 40_000_000L, 18.0, 60, 980_000L, 5, "2026-01-10")
        val enNegativo = CreditSummary(vehiculoSinSaldo, terms, null)
        val enCero = CreditSummary(vehiculoSinSaldo.copy(balance = 0L), terms, null, hasMovements = false)
        listOf(enNegativo, enCero).forEach { c ->
            assertTrue(creditoSinSaldo(c))
            assertEquals(0L, patrimonioDe(listOf(c.account)).deudas)
        }
    }

    @Test
    fun `un prestamo en dolares sin saldo tambien cuenta cero, y uno con saldo suma su estimado`() {
        val usdSinSaldo = Account("acc_u", "Crédito USD", AccountType.LOAN, 0L, "USD", mapOf("USD" to -100L), estimatedTotalCop = -400_000L)
        val usdConSaldo = usdSinSaldo.copy(id = "acc_u2", balancesByCurrency = mapOf("USD" to 100L), estimatedTotalCop = 400_000L)
        assertEquals(400_000L, patrimonioDe(listOf(usdSinSaldo, usdConSaldo)).deudas)
    }

    @Test
    fun `quien pasa los prestamos en cero decide cuales`() {
        val cuentas = listOf(ahorros, vehiculoSinSaldo)
        assertEquals(-980_000L, patrimonioDe(cuentas, prestamosEnCero = emptySet()).deudas)
        assertEquals(0L, patrimonioDe(cuentas).deudas)
    }
}
