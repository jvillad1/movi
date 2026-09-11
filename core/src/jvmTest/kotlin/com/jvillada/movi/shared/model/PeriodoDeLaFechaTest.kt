package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [periodoDeLaFecha] — en qué período cae un día civil `"AAAA-MM-DD"`, que es la forma en que
 * Movimientos tiene sus fechas.
 *
 * Delega en [periodoDe] a propósito, en vez de repetir la regla del corte: dos copias de una regla
 * de fechas es exactamente cómo nacen los desacuerdos de un día. Lo que se prueba acá es que la
 * conversión de cadena a instante no corre nada de lugar — el riesgo real, porque el día arranca a
 * medianoche en Bogotá y no en UTC.
 */
class PeriodoDeLaFechaTest {

    private val corte26 = PeriodSettings(cutoffDay = 26)

    @Test
    fun `el dia del corte abre el periodo siguiente`() {
        assertEquals(PeriodoFinanciero(2026, 9), periodoDeLaFecha("2026-08-26", corte26))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDeLaFecha("2026-08-25", corte26))
    }

    @Test
    fun `la medianoche de Bogota no se corre a otro dia`() {
        // El riesgo concreto: convertir a UTC movería el 26 de agosto a las 00:00 de Bogotá al 26
        // a las 05:00 UTC —mismo día— pero al revés (UTC→Bogotá) caería el 25 a las 19:00 y el día
        // del salario abriría el período equivocado.
        assertEquals(PeriodoFinanciero(2026, 9), periodoDeLaFecha("2026-08-26", corte26))
        assertEquals(PeriodoFinanciero(2026, 10), periodoDeLaFecha("2026-09-26", corte26))
    }

    @Test
    fun `con corte 1 es el mes de calendario`() {
        val mes = PeriodSettings(cutoffDay = 1)
        assertEquals(PeriodoFinanciero(2026, 8), periodoDeLaFecha("2026-08-26", mes))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDeLaFecha("2026-09-01", mes))
    }

    @Test
    fun `cruza el fin de ano`() {
        assertEquals(PeriodoFinanciero(2027, 1), periodoDeLaFecha("2026-12-26", corte26))
        assertEquals(PeriodoFinanciero(2026, 12), periodoDeLaFecha("2026-12-25", corte26))
    }

    @Test
    fun `un corte 31 en febrero no deja al mes sin arranque`() {
        // Sin el recorte al último día, quien elige 31 se queda sin período en cuatro meses.
        val corte31 = PeriodSettings(cutoffDay = 31)
        assertEquals(PeriodoFinanciero(2026, 3), periodoDeLaFecha("2026-02-28", corte31))
        assertEquals(PeriodoFinanciero(2026, 2), periodoDeLaFecha("2026-02-27", corte31))
    }

    @Test
    fun `una cadena que no es fecha devuelve null`() {
        // No se inventa un período para un dato que no se entiende.
        assertNull(periodoDeLaFecha("vaya a saber", corte26))
        assertNull(periodoDeLaFecha("", corte26))
    }

    @Test
    fun `anterior y siguiente cruzan el ano`() {
        assertEquals(PeriodoFinanciero(2025, 12), periodoAnterior(PeriodoFinanciero(2026, 1)))
        assertEquals(PeriodoFinanciero(2026, 1), periodoSiguiente(PeriodoFinanciero(2025, 12)))
        assertEquals(PeriodoFinanciero(2026, 8), periodoAnterior(PeriodoFinanciero(2026, 9)))
    }
}
