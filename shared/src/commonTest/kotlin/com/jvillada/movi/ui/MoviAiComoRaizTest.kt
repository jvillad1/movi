package com.jvillada.movi.ui

import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Movi AI en el rail es una raíz (vuelta 33)
 *
 * A 1.280 px Movi AI está en el rail como cualquier pestaña, pero su encabezado tenía «←»: tocarla
 * la apilaba encima de lo que hubiera y la flecha no llevaba a ningún lado que el rail no mostrara.
 * Tocada en el rail, la pila queda como la de cualquier otra pestaña y el encabezado lleva el avatar;
 * abierta encima de otra pantalla (desde Hoy en el teléfono, desde Ajustes) conserva la flecha.
 */
class MoviAiComoRaizTest {

    @Test
    fun `tocar Movi AI en el rail deja la pila como la de una pestana`() {
        val pila = mutableListOf<Screen>(Screen.Dashboard, Screen.Tu, Screen.Categorias)
        NavStack.navegarALaPestana(pila, NavTab.MOVI_AI)
        assertEquals(listOf(Screen.Dashboard, Screen.AIChat()), pila)
    }

    @Test
    fun `las otras pestanas se navegan como siempre`() {
        val pila = mutableListOf<Screen>(Screen.Dashboard, Screen.AIChat())
        NavStack.navegarALaPestana(pila, NavTab.TU)
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), pila)
    }

    @Test
    fun `desde Tu Movi AI se sigue apilando`() {
        val pila = mutableListOf<Screen>(Screen.Dashboard, Screen.Tu)
        NavStack.navegar(pila, Screen.AIChat())
        assertEquals(listOf(Screen.Dashboard, Screen.Tu, Screen.AIChat()), pila, "volver regresa a Tú")
    }

    @Test
    fun `con el rail, Movi AI sobre Hoy es la raiz y lleva avatar`() {
        val pila = listOf(Screen.Dashboard, Screen.AIChat())
        assertTrue(moviAiEsLaRaizDeSuPestana(pila, WindowWidthClass.Expanded))
        assertTrue(moviAiEsLaRaizDeSuPestana(pila, WindowWidthClass.Medium))
        assertTrue(moviAiEsLaRaizDeSuPestana(listOf(Screen.AIChat()), WindowWidthClass.Expanded), "sola en la pila (la web recargada)")
    }

    @Test
    fun `en el telefono Movi AI siempre se abre encima de otra pantalla y lleva flecha`() {
        assertFalse(moviAiEsLaRaizDeSuPestana(listOf(Screen.Dashboard, Screen.AIChat()), WindowWidthClass.Compact))
    }

    @Test
    fun `con el rail, apilada encima de Ajustes conserva la flecha`() {
        assertFalse(moviAiEsLaRaizDeSuPestana(listOf(Screen.Dashboard, Screen.Tu, Screen.AIChat()), WindowWidthClass.Expanded))
        assertFalse(moviAiEsLaRaizDeSuPestana(listOf(Screen.Dashboard, Screen.Accounts), WindowWidthClass.Expanded), "no es Movi AI")
    }
}
