package com.jvillada.movi.ui.components

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
    fun `las cuatro pestanas, en orden y con sus rotulos`() {
        assertEquals(
            listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.PATRIMONIO),
            destinosPrincipales.map { it.tab },
        )
        assertEquals(listOf("Hoy", "Movimientos", "Plan", "Patrimonio"), destinosPrincipales.map { it.label })
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
}
