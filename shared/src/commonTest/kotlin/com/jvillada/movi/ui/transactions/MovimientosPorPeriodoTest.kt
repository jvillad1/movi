package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Movimientos muestra el mes que el dueño vive, no el del calendario.**
 *
 * Él: *«Como la periodicidad es mensual me debería dejar ver cada mes en las fechas que yo
 * establecí, de 25 a 25 o cuando comience el período»*.
 *
 * Su corte no es el 1 porque su plata no empieza el 1: su salario está registrado el **26 de
 * agosto** y se llama **«Salario Septiembre 2026»**. Movi ya sabía calcular eso —`PeriodSettings`
 * existe desde hace olas y Presupuestos lo respeta— pero la lista de movimientos seguía siendo una
 * tira infinita de días sin decir de qué mes hablaba.
 */
class MovimientosPorPeriodoTest {

    private fun dia(fecha: String) = EventDay(date = fecha, total = 0L, items = emptyList())

    private val corte26 = PeriodSettings(cutoffDay = 26)
    private val mesDeCalendario = PeriodSettings(cutoffDay = 1)

    private val agostoYSeptiembre = listOf(
        dia("2026-08-25"),  // último día del período «agosto»
        dia("2026-08-26"),  // el día del salario: arranca «septiembre»
        dia("2026-09-10"),
        dia("2026-09-25"),  // último día de «septiembre»
        dia("2026-09-26"),  // ya es «octubre»
    )

    @Test
    fun `con corte 26, septiembre va del 26 de agosto al 25 de septiembre`() {
        val septiembre = diasDelPeriodo(agostoYSeptiembre, PeriodoFinanciero(2026, 9), corte26)

        assertEquals(
            listOf("2026-08-26", "2026-09-10", "2026-09-25"),
            septiembre.map { it.date },
        )
    }

    @Test
    fun `el dia del salario abre el periodo, no lo cierra`() {
        // Es el caso que originó todo: el 26 de agosto la app le sumaba «gastos de agosto»
        // mientras él ya vivía septiembre.
        val agosto = diasDelPeriodo(agostoYSeptiembre, PeriodoFinanciero(2026, 8), corte26)

        assertEquals(listOf("2026-08-25"), agosto.map { it.date })
    }

    @Test
    fun `con corte 1 es exactamente el mes de calendario`() {
        // La garantía de compatibilidad: quien no toque el ajuste ve lo de siempre.
        val septiembre = diasDelPeriodo(agostoYSeptiembre, PeriodoFinanciero(2026, 9), mesDeCalendario)

        assertEquals(
            listOf("2026-09-10", "2026-09-25", "2026-09-26"),
            septiembre.map { it.date },
        )
    }

    @Test
    fun `un dia con fecha ilegible se muestra en vez de esconderse`() {
        // Esconder un movimiento porque no se entendió su fecha es peor que mostrarlo en el
        // período equivocado: uno se ve y se corrige, el otro no se ve nunca.
        val conBasura = listOf(dia("2026-09-10"), dia("vaya a saber"))

        assertEquals(2, diasDelPeriodo(conBasura, PeriodoFinanciero(2026, 9), corte26).size)
    }

    @Test
    fun `no se puede avanzar mas alla del periodo actual`() {
        val actual = PeriodoFinanciero(2026, 9)

        assertTrue(puedeAvanzarDePeriodo(PeriodoFinanciero(2026, 8), actual), "desde agosto sí")
        assertFalse(puedeAvanzarDePeriodo(actual, actual), "desde el actual no")
        // El que viene todavía no ocurrió: una lista vacía con nombre de mes futuro no dice nada.
        assertFalse(puedeAvanzarDePeriodo(PeriodoFinanciero(2026, 10), actual))
    }

    @Test
    fun `avanzar y retroceder cruzan bien el fin de ano`() {
        assertTrue(puedeAvanzarDePeriodo(PeriodoFinanciero(2025, 12), PeriodoFinanciero(2026, 1)))
        assertFalse(puedeAvanzarDePeriodo(PeriodoFinanciero(2026, 1), PeriodoFinanciero(2025, 12)))
    }
}
