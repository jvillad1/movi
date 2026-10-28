package com.jvillada.movi.ui.components

import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.destinoResaltado
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vuelta 38 (la opción 3): **cuatro pestañas** —Inicio · Actividad · Pagos · Tú—, las mismas en el
 * teléfono y en la web. Ola C (2026-09) fijó la regla: cuatro pestañas, las mismas en los dos. La barra y el rail
 * leen la misma lista ([destinosPrincipales]); fijarla entera protege el orden y los rótulos, y que
 * no vuelvan Créditos, Presupuestos ni Más. Que las dos superficies la pinten de verdad lo prueba
 * `LasCuatroPestanasTest`, montándolas.
 */
class MinNavRailTest {

    @Test
    fun `las cuatro pestanas, en orden y con sus rotulos`() {
        assertEquals(
            listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.TU),
            destinosPrincipales.map { it.tab },
        )
        assertEquals(listOf("Inicio", "Actividad", "Pagos", "Tú"), destinosPrincipales.map { it.label })
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
    fun `el rail tiene las cuatro y Movi AI al final, la barra solo las cuatro`() {
        assertEquals(
            listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.TU, NavTab.MOVI_AI),
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
        listOf(Screen.Dashboard, Screen.Transactions(), Screen.Plan(), Screen.Tu, Screen.Espacios, Screen.Accounts, Screen.Mas).forEach {
            assertEquals(navTabFor(it), destinoResaltado(it), "$it")
        }
    }

    // ── Vuelta 38: un solo amarillo lleno en el rail ───────────────────────────

    @Test
    fun `en el rail ancho la activa va en el amarillo suave, y el lleno es solo de Agregar`() {
        val colores = com.jvillada.movi.theme.COLORES_CLAROS
        assertEquals(colores.tinte, fondoDelDestinoDelRailAncho(activo = true, colores))
        assertEquals(androidx.compose.ui.graphics.Color.Transparent, fondoDelDestinoDelRailAncho(activo = false, colores))
        kotlin.test.assertNotEquals(colores.marca, fondoDelDestinoDelRailAncho(activo = true, colores))
    }
}
