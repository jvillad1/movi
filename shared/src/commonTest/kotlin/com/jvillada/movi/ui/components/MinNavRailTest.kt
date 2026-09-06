package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PR 2 del rediseño de Recurrentes (2026-09): con «Flujo libre» y las candidatas mudadas a
 * Movimientos, y editar un recurrente existente ya resuelto desde el detalle de un movimiento
 * (PR 1), Recurrentes deja de tener una entrada propia en el rail — la única puerta que le
 * quedaba a crear un recurrente "a ciegas", sin movimiento asociado.
 *
 * `NavTab.RECURRING` y `Screen.Recurrentes` se quedan sin borrar (código muerto a propósito, ver
 * el KDoc en `MinNavRail.kt`) hasta la PR de limpieza, así que este test solo cubre la lista
 * VISIBLE — no toca `Navigation.kt`.
 */
class MinNavRailTest {

    @Test
    fun `el rail ya no ofrece Recurrentes como destino`() {
        assertFalse(railDestinations.any { it.tab == NavTab.RECURRING }, "Recurrentes tenía que salir del rail")
    }

    @Test
    fun `el rail sigue con sus otros destinos, en el mismo orden`() {
        assertTrue(
            railDestinations.map { it.tab } == listOf(
                NavTab.HOME, NavTab.TRANSACTIONS, NavTab.ACCOUNTS, NavTab.CREDITS, NavTab.BUDGETS, NavTab.MORE,
            ),
        )
    }
}
