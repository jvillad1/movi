package com.jvillada.movi.ui.analisis

import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RangoDelAnalisis
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Cuándo «En qué se va» arranca en «Este período», y Movimientos con un rango (vuelta 140)
 *
 * Sin esta vuelta no existían [rangoAlAbrir] ni `FiltroDeMovimientos.cruzaElPeriodo`: el archivo no
 * compila. Lo que se cuida: el «Ver todas» del Inicio abre siempre en «Este período» aunque en la sesión
 * se haya elegido «12 meses», y un filtro con los días de un rango hace que Movimientos no recorte al período.
 */
class RangoDeEnQueSeVaTest {

    @Test
    fun `desde el Inicio y pidiendo un periodo arranca en Este periodo`() {
        assertEquals(RangoDelAnalisis.PERIODO, rangoAlAbrir(Screen.EnQueSeVa(), Screen.Dashboard, RangoDelAnalisis.DOCE_MESES))
        assertEquals(RangoDelAnalisis.PERIODO, rangoAlAbrir(Screen.EnQueSeVa("2026-10"), Screen.Transactions(), RangoDelAnalisis.TRES_MESES))
    }

    /** Revisión de la vuelta 140: «Mira en qué se fue ›» de Pagos habla de este período; abría en el rango recordado. */
    @Test
    fun `desde Pagos tambien arranca en Este periodo, y apilar otra pantalla no lo toca`() {
        assertEquals(RangoDelAnalisis.PERIODO, rangoAlAbrir(Screen.EnQueSeVa(), Screen.Plan(), RangoDelAnalisis.TRES_MESES))
        assertEquals(RangoDelAnalisis.DOCE_MESES, rangoAlAbrir(Screen.Transactions(), Screen.EnQueSeVa(), RangoDelAnalisis.DOCE_MESES))
    }

    @Test
    fun `un filtro con los dias de un rango cruza el periodo, una barra del periodo no`() {
        val caro = PeriodSettings(cutoffDay = 30, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        val enCurso = periodoDeLaFecha("2026-11-06", caro)!!
        assertTrue(FiltroDeMovimientos("Comida", categoria = "Comida", desde = "2026-08-28", hasta = "2026-11-29").cruzaElPeriodo(enCurso, caro))
        assertFalse(FiltroDeMovimientos("2 nov", desde = "2026-11-02", hasta = "2026-11-02").cruzaElPeriodo(enCurso, caro))
        assertFalse(FiltroDeMovimientos("Comida", categoria = "Comida").cruzaElPeriodo(enCurso, caro))
    }
}

/**
 * Revisión de la vuelta 140 · lo que dice el lector de pantalla del gráfico de un rango: contra los 3
 * meses anteriores, no contra «el período pasado».
 */
class LaDescripcionDelGraficoDeUnRangoTest {
    @Test
    fun `en un rango la descripcion habla del rango anterior`() {
        val ajustes = PeriodSettings(cutoffDay = 1)
        val hoy = kotlinx.datetime.LocalDate(2026, 10, 15)
        fun mov(dia: String, monto: Long) = com.jvillada.movi.shared.model.FinancialEvent(
            id = dia, accountId = "a", type = com.jvillada.movi.shared.model.TransactionType.EXPENSE, amount = monto,
            category = "Comida", description = "x",
            timestamp = kotlinx.datetime.LocalDate.parse(dia).atStartOfDayIn(com.jvillada.movi.shared.time.AppTimeZone.zone)
                .toEpochMilliseconds() + 12 * 3_600_000L,
            reconciliationStatus = com.jvillada.movi.shared.model.ReconciliationStatus.RECONCILED,
        )
        // Un movimiento en cada mes desde abril: los 3 anteriores a ago–oct (may–jul) están cubiertos.
        val eventos = listOf("2026-04-01", "2026-05-10", "2026-06-10", "2026-07-10", "2026-08-10", "2026-09-10", "2026-10-02").map { mov(it, 10_000L) }
        val rango = com.jvillada.movi.shared.model.analisisDelRango(
            eventos, com.jvillada.movi.shared.model.PeriodoFinanciero(2026, 10), RangoDelAnalisis.TRES_MESES, ajustes, hoy,
        )
        val d = descripcionDelGrafico(rango, RangoDelAnalisis.TRES_MESES, acumulado = false)
        assertTrue("los 3 meses anteriores a esta altura" in d, d)
        assertFalse("período pasado" in d, d)
        val periodo = com.jvillada.movi.shared.model.analisisDelPeriodo(eventos, com.jvillada.movi.shared.model.PeriodoFinanciero(2026, 10), ajustes, hoy)
        assertTrue("el período pasado a esta altura" in descripcionDelGrafico(periodo, RangoDelAnalisis.PERIODO, acumulado = false))
    }
}
