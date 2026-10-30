package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 75 · un solo nombre del período
 *
 * [nombreDelPeriodo] es lo que dicen Inicio, Actividad, Pagos, Tus períodos y En qué se va: el mes, si
 * el período es el mes de calendario; si no, sus fechas cortas. El año solo si no es el de hoy.
 */
class NombreDelPeriodoTest {

    private val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    @Test
    fun `con corte 1 el periodo es el mes`() {
        val calendario = PeriodSettings(cutoffDay = 1)
        assertEquals("Octubre", nombreDelPeriodo(PeriodoFinanciero(2026, 10), calendario, anioDeHoy = 2026))
        assertEquals("Febrero", nombreDelPeriodo(PeriodoFinanciero(2028, 2), calendario, anioDeHoy = 2028), "el bisiesto también")
    }

    @Test
    fun `con corte 25 y el fin de semana el periodo se nombra por sus fechas`() {
        // El 25-oct-2026 es domingo: noviembre arranca el viernes 23 de octubre y cierra el 24 de noviembre.
        assertEquals("23 oct – 24 nov", nombreDelPeriodo(PeriodoFinanciero(2026, 11), delDueno, anioDeHoy = 2026))
        // El 25-dic es festivo: enero arranca el 24 de diciembre.
        assertEquals("25 nov – 23 dic", nombreDelPeriodo(PeriodoFinanciero(2026, 12), delDueno, anioDeHoy = 2026))
    }

    @Test
    fun `un periodo excepcional se nombra por sus fechas, aunque el corte sea 1`() {
        // Corte 1, pero noviembre empezó a mano el 3 (el sueldo llegó tarde): octubre ya no es el mes.
        val conExcepcion = PeriodSettings(cutoffDay = 1).conInicioPropio(PeriodoFinanciero(2026, 11), "2026-11-03")
        assertEquals("1 oct – 2 nov", nombreDelPeriodo(PeriodoFinanciero(2026, 10), conExcepcion, anioDeHoy = 2026))
        assertEquals("3 – 30 nov", nombreDelPeriodo(PeriodoFinanciero(2026, 11), conExcepcion, anioDeHoy = 2026))
        assertEquals("Diciembre", nombreDelPeriodo(PeriodoFinanciero(2026, 12), conExcepcion, anioDeHoy = 2026))
    }

    @Test
    fun `el anio solo si el periodo no es de este anio`() {
        val calendario = PeriodSettings(cutoffDay = 1)
        assertEquals("Noviembre 2025", nombreDelPeriodo(PeriodoFinanciero(2025, 11), calendario, anioDeHoy = 2026))
        assertEquals("25 oct – 24 nov 2025", nombreDelPeriodo(PeriodoFinanciero(2025, 11), PeriodSettings(cutoffDay = 25), anioDeHoy = 2026))
        assertEquals("Noviembre", nombreDelPeriodo(PeriodoFinanciero(2025, 11), calendario, anioDeHoy = null), "sin año de hoy, nunca")
    }
}
