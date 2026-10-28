package com.jvillada.movi.ui.profile

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.ui.components.inicialesDelUsuario
import com.jvillada.movi.ui.dashboard.seVeVolverAlOrdenDeMovi
import com.jvillada.movi.ui.sdui.anchoDeLaBanda
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 41, lo puro: las vistas previas de las hojas de «Tú», «Volver al orden de Movi», la banda
 * del Inicio a lo ancho y las iniciales del usuario.
 */
class LasHojasDeTuDicenLoQueVaAPasarTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val corte25HabilAnterior = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    // ── «Inicio del período»: un renglón con el período, y otro solo si hace falta ─────────────

    @Test
    fun `la vista previa del periodo dice el rango de hoy con lo elegido`() {
        assertEquals("Tu período: del 25 de octubre al 24 de noviembre", vistaPreviaDelPeriodo(PeriodoFinanciero(2026, 11), corte25))
        // El 25 de octubre de 2026 es domingo: con «el día hábil anterior» arranca el viernes 23.
        assertEquals("Tu período: del 23 de octubre al 24 de noviembre", vistaPreviaDelPeriodo(PeriodoFinanciero(2026, 11), corte25HabilAnterior))
    }

    @Test
    fun `con el mes de calendario tambien dice el rango, no un renglon aparte`() {
        assertEquals("Tu período: del 1 al 31 de octubre", vistaPreviaDelPeriodo(PeriodoFinanciero(2026, 10), PeriodSettings()))
    }

    @Test
    fun `el ejemplo del corte corrido cabe en un renglon`() {
        assertEquals("El 25 de octubre es domingo: empieza el 23.", ejemploCortoDelCorte(PeriodoFinanciero(2026, 11), corte25HabilAnterior))
        // Si el corrido cae en el mes anterior, se dice el mes.
        val corte1 = PeriodSettings(cutoffDay = 1, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        assertEquals("El 1 de noviembre es domingo: empieza el 30 de octubre.", ejemploCortoDelCorte(PeriodoFinanciero(2026, 11), corte1))
        assertNull(ejemploCortoDelCorte(PeriodoFinanciero(2026, 11), corte25), "«ese mismo día» no corre nada")
        // La guía de primer uso sigue con su frase larga.
        assertEquals(
            "Por ejemplo, el 25 de octubre es domingo: ese período empieza el 23 de octubre.",
            proximoCorteCorrido(PeriodoFinanciero(2026, 11), corte25HabilAnterior),
        )
    }

    @Test
    fun `el segundo renglon es el dia que no todos los meses tienen, o el ejemplo, o nada`() {
        assertEquals("En los meses sin día 30, empieza el último día del mes.", detalleDelInicioDelPeriodo(30, "El 25 de octubre es domingo: empieza el 23."))
        assertEquals("El 25 de octubre es domingo: empieza el 23.", detalleDelInicioDelPeriodo(25, "El 25 de octubre es domingo: empieza el 23."))
        assertNull(detalleDelInicioDelPeriodo(25, null))
    }

    @Test
    fun `la vista previa de cuando empezo un periodo dice su rango con el dia elegido`() {
        assertEquals(
            "Octubre: del 24 de septiembre al 24 de octubre",
            vistaPreviaDelInicio(PeriodoFinanciero(2026, 10), corte25, LocalDate(2026, 9, 24)),
        )
    }

    // ── «Editar inicio»: «Volver al orden de Movi» solo con algo que deshacer ──────────────────

    @Test
    fun `Volver al orden de Movi solo se ofrece con algo cambiado`() {
        assertFalse(seVeVolverAlOrdenDeMovi(PreferenciasDelInicio()))
        assertFalse(seVeVolverAlOrdenDeMovi(PreferenciasDelInicio(ordenDeMovi = listOf("CUENTAS", "GASTO_POR_CATEGORIA"))))
        assertTrue(seVeVolverAlOrdenDeMovi(PreferenciasDelInicio(ocultas = listOf("CUENTAS"))))
        assertTrue(seVeVolverAlOrdenDeMovi(PreferenciasDelInicio(orden = listOf("GASTO_POR_CATEGORIA", "CUENTAS"))))
    }

    // ── El Inicio a 1.280: la banda solo detrás del héroe ──────────────────────────────────────

    @Test
    fun `en dos columnas la banda va solo detras de la del heroe`() {
        assertEquals(532.dp, anchoDeLaBanda(1064.dp, sonDos = true))
        assertEquals(390.dp, anchoDeLaBanda(390.dp, sonDos = false))
    }

    // ── Una sola función de iniciales para los avatares del usuario ────────────────────────────

    @Test
    fun `las iniciales del usuario son las mismas para el mismo nombre en cualquier avatar`() {
        assertEquals("DM", inicialesDelUsuario("Demo Movi"))
        assertEquals("D", inicialesDelUsuario("Demo"))
        assertEquals("JV", inicialesDelUsuario("  juan   villada restrepo "))
        assertEquals("?", inicialesDelUsuario(null))
        assertEquals("?", inicialesDelUsuario("   "))
    }
}
