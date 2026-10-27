package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDelCredito
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Una deuda en la lista de Créditos (vuelta de diseño 4)
 *
 * Cada deuda pasó de ser una tarjeta con botones a una fila: el saldo, el próximo pago y, debajo, solo
 * lo que es verdad. «Vehículo 4083» decía «Sin términos registrados · $0 · 0% pagado».
 */
class DeudaEnLaListaTest {

    private val hoy = LocalDate(2026, 10, 27)
    private val periodo = periodoDe(1_793_000_000_000L, PeriodSettings())

    private fun prestamo(
        balance: Long,
        terms: CreditTerms? = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_250_000L, dayOfMonth = 5, startDate = "2021-06-15",
        ),
        paidPct: Double? = 0.5,
        hasMovements: Boolean = true,
    ) = CreditSummary(Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance), terms, paidPct, hasMovements = hasMovements)

    @Test
    fun `el proximo pago es el siguiente dia del mes, hoy incluido`() {
        assertEquals(LocalDate(2026, 11, 5), proximaFechaDelDia(5, hoy))
        assertEquals(LocalDate(2026, 10, 27), proximaFechaDelDia(27, hoy))
        assertEquals(LocalDate(2026, 10, 30), proximaFechaDelDia(30, hoy))
        // El 31 en noviembre cae el 30.
        assertEquals(LocalDate(2026, 11, 30), proximaFechaDelDia(31, LocalDate(2026, 11, 2)))
        assertEquals(LocalDate(2027, 1, 3), proximaFechaDelDia(3, LocalDate(2026, 12, 20)))
    }

    @Test
    fun `un prestamo dice su cuota y el dia que vence`() {
        val credito = prestamo(balance = 40_000_000L)
        val fila = prestamoEnLaLista(credito, planDelCredito(credito, hoy), periodo, hoy)
        assertEquals("Cuota \$1.250.000 · 5 de noviembre", fila.subtitulo)
        assertFalse(fila.faltanDatos)
        assertFalse(fila.pagada)
        assertEquals(0.5f, fila.fraccionPagada)
        assertEquals("50 % pagado", fila.avance)
    }

    @Test
    fun `sin terminos faltan datos, sin cero por ciento ni barra`() {
        val fila = prestamoEnLaLista(prestamo(balance = 0L, terms = null, paidPct = null), null, periodo, hoy)
        assertEquals(FALTAN_DATOS_DE_LA_DEUDA, fila.subtitulo)
        assertTrue(fila.faltanDatos)
        assertFalse(fila.pagada, "sin términos un \$0 no es «pagado»: no se sabe")
        assertNull(fila.fraccionPagada)
        assertNull(fila.avance)
    }

    @Test
    fun `en cero y con movimientos esta pagado`() {
        val fila = prestamoEnLaLista(prestamo(balance = 0L, paidPct = 1.0), null, periodo, hoy)
        assertTrue(fila.pagada)
        assertNull(fila.fraccionPagada)
    }

    @Test
    fun `en cero sin movimientos no esta pagado, le falta el desembolso`() {
        val credito = prestamo(balance = 0L, paidPct = 1.0, hasMovements = false)
        val fila = prestamoEnLaLista(credito, planDelCredito(credito, hoy), periodo, hoy)
        assertFalse(fila.pagada)
        assertNull(fila.fraccionPagada)
        assertEquals("Falta registrar el desembolso", fila.nota?.texto)
    }

    @Test
    fun `recien desembolsado no pinta una barra en cero`() {
        val fila = prestamoEnLaLista(prestamo(balance = 80_000_000L, paidPct = 0.0), null, periodo, hoy)
        assertNull(fila.fraccionPagada, "«0 % pagado» suelto no dice nada")
    }

    @Test
    fun `una deuda que crece lleva la alerta y no la barra`() {
        val hipotecario = CreditSummary(
            account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
            terms = CreditTerms(
                accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
                termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
                insuranceMonthly = 209_219L, paidBy = "Skandia",
            ),
            paidPct = 0.0,
        )
        val fila = prestamoEnLaLista(hipotecario, planDelCredito(hipotecario, hoy), periodo, hoy)
        val nota = assertNotNull(fila.nota)
        assertTrue(nota.esAlerta)
        assertTrue("tu deuda crece" in nota.texto)
        assertNull(fila.fraccionPagada)
    }

    private fun tarjeta(deuda: Long, terms: CardTerms?, available: Long? = null, moneda: String = "COP") = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, deuda, currency = moneda, balancesByCurrency = mapOf(moneda to deuda)),
        terms = terms,
        available = available,
    )

    @Test
    fun `una tarjeta dice su pago minimo y el dia, en su moneda`() {
        val terminos = CardTerms(accountId = "acc_nu", bank = "Nu", cutoffDay = 3, paymentDay = 14, pagoMinimo = 180_000L)
        assertEquals("Pago mínimo \$180.000 · 14 de noviembre", tarjetaEnLaLista(tarjeta(1_200_000L, terminos), hoy).subtitulo)
        val sinMinimo = terminos.copy(pagoMinimo = null)
        assertEquals("Fecha de pago · 14 de noviembre", tarjetaEnLaLista(tarjeta(1_200_000L, sinMinimo), hoy).subtitulo)
    }

    @Test
    fun `una tarjeta sin terminos pide los datos y una en cero esta pagada`() {
        assertTrue(tarjetaEnLaLista(tarjeta(500_000L, null), hoy).faltanDatos)
        val terminos = CardTerms(accountId = "acc_nu", bank = "Nu", paymentDay = 14)
        val alDia = tarjetaEnLaLista(tarjeta(0L, terminos, available = 8_000_000L), hoy)
        assertTrue(alDia.pagada)
        assertEquals("Disponible \$8.000.000", alDia.subtitulo)
    }
}
