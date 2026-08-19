package com.jvillada.movi.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * F22: la flecha ‹ debe volver a la pantalla anterior de verdad, no a un destino
 * fijo. `NavStack` es la lógica pura (sin Compose) detrás de `navigate`/`goBackTo`
 * en App.kt — la testeamos acá porque `SnapshotStateList` no es fácil de instanciar
 * fuera de una composición.
 */
class NavStackTest {

    @Test
    fun `shouldPush es true si la pila esta vacia`() {
        assertEquals(true, NavStack.shouldPush(emptyList(), Screen.Dashboard))
    }

    @Test
    fun `shouldPush es false si la pantalla pedida ya esta arriba de la pila`() {
        assertEquals(false, NavStack.shouldPush(listOf(Screen.Dashboard, Screen.Mas), Screen.Mas))
    }

    @Test
    fun `shouldPush es true si la pantalla pedida es distinta a la de arriba`() {
        assertEquals(true, NavStack.shouldPush(listOf(Screen.Dashboard), Screen.Mas))
    }

    @Test
    fun `back con historial hace pop, sin importar el fallback`() {
        // Inicio -> Creditos -> volver: debe sacar Creditos y quedar en Inicio,
        // no ir al fallback (Mas) aunque Creditos viva en Mas.
        val result = NavStack.back(listOf(Screen.Dashboard, Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Pop>(result)
    }

    @Test
    fun `back con Mas antes de Creditos tambien hace pop, no fallback`() {
        // Mas -> Creditos -> volver: debe sacar Creditos y quedar en Mas via pop,
        // el mismo resultado que daria el fallback, pero por historial real.
        val result = NavStack.back(listOf(Screen.Mas, Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Pop>(result)
    }

    @Test
    fun `back sin historial cae al fallback`() {
        // Entraste directo a Creditos (deep link o recarga de la web) — no hay
        // pantalla anterior a la que volver, así que cae al destino de reserva.
        val result = NavStack.back(listOf(Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Fallback>(result)
        assertEquals(Screen.Mas, (result as NavStack.BackResult.Fallback).screen)
    }

    @Test
    fun `back sin historial con fallback distinto para pantallas de primer nivel`() {
        val result = NavStack.back(listOf(Screen.Investments), fallback = Screen.Dashboard)
        assertIs<NavStack.BackResult.Fallback>(result)
        assertEquals(Screen.Dashboard, (result as NavStack.BackResult.Fallback).screen)
    }
}
