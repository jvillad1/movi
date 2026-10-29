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

/** Vuelta 61: «Mira en qué se fue», abierto desde Pagos, marca Pagos (marcaba Inicio). */
class EnQueSeVaMarcaSuPestanaTest {

    @Test
    fun `abierta desde Pagos marca Pagos`() {
        assertEquals(NavTab.PLAN, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.Plan(), Screen.EnQueSeVa())))
    }

    @Test
    fun `abierta desde Inicio o sin nada debajo marca Inicio`() {
        assertEquals(NavTab.HOY, destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.EnQueSeVa())))
        assertEquals(NavTab.HOY, destinoResaltadoDeLaPila(listOf(Screen.EnQueSeVa("2026-10"))))
    }

    @Test
    fun `Buscar abierta encima de En que se va abierta desde Pagos sigue marcando Pagos`() {
        assertEquals(
            NavTab.PLAN,
            destinoResaltadoDeLaPila(listOf(Screen.Dashboard, Screen.Plan(), Screen.EnQueSeVa(), Screen.Buscar)),
        )
    }
}
