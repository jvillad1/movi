package com.jvillada.movi.ui.plan

import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.disposicionDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** # Plan en dos columnas (Ola W3), en números: contra el panel real, ya sin el rail. */
class PlanEnColumnasTest {

    @Test
    fun `Plan es un tablero, con cualquier segmento`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Plan()))
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Plan(SEGMENTO_PRESUPUESTOS)))
        assertEquals(1_064.dp, anchoDelPanelEnLaCascara(1_280.dp, Screen.Plan()))
    }

    @Test
    fun `dos columnas en una laptop de 1280 y en 1440 y 1920`() {
        listOf(1_280, 1_440, 1_920).forEach { ventana ->
            assertTrue(planEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Plan())), "$ventana")
        }
    }

    @Test
    fun `una columna a 1024 y en toda ventana mediana`() {
        listOf(600, 768, 840, 999, 1_000, 1_024, 1_075).forEach { ventana ->
            assertFalse(planEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Plan())), "$ventana")
        }
    }

    @Test
    fun `el umbral es de 860 dp de panel`() {
        assertEquals(860.dp, UMBRAL_DE_PLAN_EN_DOS_COLUMNAS)
        assertTrue(planEnDosColumnas(860.dp))
        assertFalse(planEnDosColumnas(859.dp))
    }
}
