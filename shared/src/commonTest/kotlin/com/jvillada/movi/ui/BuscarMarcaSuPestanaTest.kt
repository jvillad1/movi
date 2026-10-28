package com.jvillada.movi.ui

import com.jvillada.movi.ui.components.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vuelta de diseño 30: Buscar marca la pestaña de donde se abrió, no siempre «Inicio». */
class BuscarMarcaSuPestanaTest {

    @Test
    fun `abierta desde Movimientos marca Movimientos`() {
        assertEquals(NavTab.MOVIMIENTOS, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.Transactions(), Screen.Buscar)))
    }

    @Test
    fun `abierta desde Hoy o sin nada debajo marca Hoy`() {
        assertEquals(NavTab.HOY, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.Buscar)))
        assertEquals(NavTab.HOY, destinoResaltadoDeLaPila(listOf(Screen.Buscar)))
    }

    @Test
    fun `lo que no es Buscar marca lo de siempre`() {
        assertEquals(NavTab.PLAN, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.Plan())))
        assertEquals(NavTab.MOVI_AI, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.AIChat())))
        assertEquals(null, destinoResaltadoDeLaPila(emptyList()))
    }
}
