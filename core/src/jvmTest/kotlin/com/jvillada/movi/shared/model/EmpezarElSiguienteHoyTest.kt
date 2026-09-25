package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **«Empezar un período nuevo hoy»**: cuándo se puede, y qué mapa se guarda.
 *
 * El dueño cobra antes del corte de vez en cuando (el 25 cae domingo, el sueldo entra el 20) y
 * quiere cerrar el período en curso ese mismo día desde el detalle, sin ir a buscar la hoja del
 * mes en Movimientos. Lo que se guarda es una excepción más en `iniciosPropios` —la del período
 * SIGUIENTE, que arranca hoy— y la regla de siempre ([inicioDelPeriodo]) decide si vale.
 */
class EmpezarElSiguienteHoyTest {

    /** Los ajustes reales del dueño: corte 25 y octubre ya arrancó el 24 de septiembre. */
    private val delDueno = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-24"))

    @Test
    fun `el 20 de octubre, noviembre empieza hoy y se suma a lo que ya estaba`() {
        val nuevos = empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 20), delDueno)
        assertEquals(
            mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-20"),
            nuevos?.iniciosPropios,
        )
        assertEquals(25, nuevos?.cutoffDay)
    }

    @Test
    fun `con el mapa nuevo, octubre termina ayer y noviembre es el periodo de hoy`() {
        val nuevos = empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 20), delDueno)!!
        assertEquals(PeriodoFinanciero(2026, 11), periodoDeLaFecha("2026-10-20", nuevos))
        assertEquals(PeriodoFinanciero(2026, 10), periodoDeLaFecha("2026-10-19", nuevos))
    }

    @Test
    fun `hoy en el mes equivocado no es un inicio valido para el siguiente`() {
        // El 26 de septiembre es parte de octubre, pero noviembre solo puede arrancar en octubre.
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 9, 26), delDueno))
    }

    @Test
    fun `con mes de calendario el siguiente nunca puede arrancar dentro del mes en curso`() {
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 20), PeriodSettings()))
    }

    @Test
    fun `si hoy ya es el inicio del siguiente por el corte, no hay nada que empezar`() {
        // Noviembre se corrió al 27 a mano; el 25 sigue siendo octubre y el corte dice que
        // noviembre arranca hoy.
        val corrido = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-11" to "2026-10-27"))
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 25), corrido))
    }

    @Test
    fun `si hoy no cae en el periodo que se dice en curso, no se escribe nada`() {
        // El reloj del server y el del teléfono no coinciden: el teléfono ya está en noviembre.
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 26), delDueno))
    }

    @Test
    fun `no pasa el tope de excepciones que acepta el server`() {
        val lleno = (1..MAX_INICIOS_PROPIOS).associate { i ->
            val periodo = PeriodoFinanciero(2000 + i / 12, i % 12 + 1)
            periodo.prefijo to "${periodo.year}-${periodo.month.toString().padStart(2, '0')}-01"
        }
        val ajustes = PeriodSettings(cutoffDay = 25, iniciosPropios = lleno)
        assertEquals(MAX_INICIOS_PROPIOS, lleno.size)
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10), LocalDate(2026, 10, 20), ajustes))
    }

    @Test
    fun `conInicioPropio pone y quita la excepcion de un periodo sin tocar las demas`() {
        val puesto = delDueno.conInicioPropio(PeriodoFinanciero(2026, 11), "2026-10-20")
        assertEquals(mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-20"), puesto.iniciosPropios)
        val quitado = puesto.conInicioPropio(PeriodoFinanciero(2026, 10), null)
        assertEquals(mapOf("2026-11" to "2026-10-20"), quitado.iniciosPropios)
    }

    @Test
    fun `periodoDelPrefijo lee AAAA-MM y nada mas`() {
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelPrefijo("2026-09"))
        assertNull(periodoDelPrefijo("2026-13"))
        assertNull(periodoDelPrefijo("2026-9"))
        assertNull(periodoDelPrefijo("2026-09-01"))
        assertNull(periodoDelPrefijo("hola"))
    }
}
