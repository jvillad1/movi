package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.PeriodoFinanciero
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
}
