package com.jvillada.movi.ui.analisis

import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RangoDelAnalisis
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.ui.Screen
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

    @Test
    fun `desde otra pantalla se respeta lo elegido en la sesion, y otra pantalla no lo toca`() {
        assertEquals(RangoDelAnalisis.TRES_MESES, rangoAlAbrir(Screen.EnQueSeVa(), Screen.Plan(), RangoDelAnalisis.TRES_MESES))
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
