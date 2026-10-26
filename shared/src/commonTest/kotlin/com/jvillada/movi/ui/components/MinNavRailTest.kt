package com.jvillada.movi.ui.components

import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.destinoResaltado
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ola C (2026-09): **cuatro pestañas**, las mismas en el teléfono y en la web. La barra y el rail
 * leen la misma lista ([destinosPrincipales]); fijarla entera protege el orden y los rótulos, y que
 * no vuelvan Créditos, Presupuestos ni Más. Que las dos superficies la pinten de verdad lo prueba
 * `LasCuatroPestanasTest`, montándolas.
 */
class MinNavRailTest {

    @Test
    fun `las cinco pestanas, en orden y con sus rotulos`() {
        // R3: Espacios entra entre Plan y Patrimonio.
        assertEquals(
            listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.ESPACIOS, NavTab.PATRIMONIO),
            destinosPrincipales.map { it.tab },
        )
        assertEquals(listOf("Hoy", "Movimientos", "Plan", "Espacios", "Patrimonio"), destinosPrincipales.map { it.label })
    }

    @Test
    fun `Agregar no es una pestana de la lista, es el boton`() {
        assertEquals(false, destinosPrincipales.any { it.tab == NavTab.ADD })
    }

    @Test
    fun `cada pestana vuelve a su pantalla principal`() {
        // La lista y `screenForTab` no pueden desalinearse: si una pestaña no resolviera a una
        // pantalla que declara ESA pestaña, el ítem quedaría sin resaltar.
        destinosPrincipales.forEach { dest ->
            assertEquals(dest.tab, navTabFor(screenForTab(dest.tab)), dest.label)
        }
    }

    // ── Movi AI en el rail ─────────────────────────────────────────────────────────────

    @Test
    fun `el rail tiene las cinco y Movi AI al final, la barra solo las cinco`() {
        assertEquals(
            listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.ESPACIOS, NavTab.PATRIMONIO, NavTab.MOVI_AI),
            destinosDelRail.map { it.tab },
        )
        assertEquals(false, destinosPrincipales.any { it.tab == NavTab.MOVI_AI }, "la barra del teléfono no la lleva")
        assertEquals("Movi AI", destinoMoviAi.label)
    }

    @Test
    fun `Movi AI abre el chat y el chat la resalta en el rail`() {
        assertEquals(Screen.AIChat(), screenForTab(NavTab.MOVI_AI))
        assertEquals(NavTab.MOVI_AI, destinoResaltado(Screen.AIChat()))
        assertEquals(NavTab.MOVI_AI, destinoResaltado(Screen.AIChat(preguntaInicial = "¿Cuánto me queda?")))
        // El encabezado no cambia: en el teléfono el chat se abre desde Hoy y lleva flecha.
        assertEquals(null, navTabFor(Screen.AIChat()))
        // Y el resto resalta lo de siempre.
        listOf(Screen.Dashboard, Screen.Transactions(), Screen.Plan(), Screen.Espacios, Screen.Accounts, Screen.Mas).forEach {
            assertEquals(navTabFor(it), destinoResaltado(it), "$it")
        }
    }
}
