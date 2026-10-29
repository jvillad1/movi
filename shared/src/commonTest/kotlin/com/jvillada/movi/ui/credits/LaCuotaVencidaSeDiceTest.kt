package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuotaSinPagar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # La cuota vencida se dice (vuelta 64)
 *
 * El detalle del crédito decía «la próxima el 5 de noviembre» con la cuota de octubre sin pagar,
 * mientras la cuenta de las cuotas que faltan ya empezaba en octubre (vuelta 59). Las dos salen ahora
 * de la misma primera cuota sin pagar.
 */
class LaCuotaVencidaSeDiceTest {

    private val hoy = LocalDate(2026, 10, 29)

    @Test
    fun `la cuota de octubre sin pagar el 29 de octubre esta vencida`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(5, hoy, pagadas = emptyList())
        assertEquals(PeriodoFinanciero(2026, 10), primera)
        assertTrue(laCuotaEstaVencida(5, primera, hoy))
        assertEquals("Cuota de octubre vencida desde el 5 oct", subtituloDeLaCuota(5, primera, hoy))
    }

    @Test
    fun `con la de octubre pagada dice la proxima de noviembre`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(5, hoy, pagadas = listOf("2026-10"))
        assertFalse(laCuotaEstaVencida(5, primera, hoy))
        assertEquals("Día 5 · la próxima el 5 de noviembre", subtituloDeLaCuota(5, primera, hoy))
    }

    @Test
    fun `el mismo dia del vencimiento todavia no esta vencida`() {
        val cinco = LocalDate(2026, 10, 5)
        assertFalse(laCuotaEstaVencida(5, PeriodoFinanciero(2026, 10), cinco))
        assertEquals("Día 5 · la próxima el 5 de octubre", subtituloDeLaCuota(5, PeriodoFinanciero(2026, 10), cinco))
    }

    @Test
    fun `una pagada por adelantado corre la proxima un mes`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(5, hoy, pagadas = listOf("2026-10", "2026-11"))
        assertEquals("Día 5 · la próxima el 5 de diciembre", subtituloDeLaCuota(5, primera, hoy))
    }

    @Test
    fun `un server viejo sin las pagadas no la da por vencida`() {
        val primera = mesDeLaPrimeraCuotaSinPagar(5, hoy, pagadas = null)
        assertEquals("Día 5 · la próxima el 5 de noviembre", subtituloDeLaCuota(5, primera, hoy))
    }

    @Test
    fun `un dia 31 vence el ultimo dia de un mes corto`() {
        assertEquals(
            "Cuota de septiembre vencida desde el 30 sep",
            subtituloDeLaCuota(31, PeriodoFinanciero(2026, 9), hoy),
        )
    }

    // ── La lista de Créditos dice lo mismo que el detalle ─────────────────────

    private fun credito(pagadas: List<String>?) = CreditSummary(
        account = Account("acc_vehiculo", "Vehículo 4083", AccountType.LOAN, 30_000_000L, "COP"),
        terms = CreditTerms(
            accountId = "acc_vehiculo", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
            termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
        ),
        paidPct = null,
        hasMovements = true,
        cuotasPagadas = pagadas,
    )

    @Test
    fun `la lista dice la cuota vencida como el detalle`() {
        val c = credito(pagadas = emptyList())
        val primera = assertNotNullPrimera(c)
        val fila = prestamoEnLaLista(c, plan = null, periodoActual = PeriodoFinanciero(2026, 10), hoy = hoy)
        assertEquals(subtituloDeLaCuota(5, primera, hoy), fila.subtitulo)
        assertEquals("Cuota de octubre vencida desde el 5 oct", fila.subtitulo)
    }

    @Test
    fun `con la cuota al dia la lista dice el monto y la fecha de la proxima`() {
        val fila = prestamoEnLaLista(credito(pagadas = listOf("2026-10")), null, PeriodoFinanciero(2026, 10), hoy)
        assertEquals("Cuota \$980.000 · 5 de noviembre", fila.subtitulo)
    }

    private fun assertNotNullPrimera(c: CreditSummary): PeriodoFinanciero =
        mesDeLaPrimeraCuota(c, hoy) ?: error("el crédito tiene términos")
}
