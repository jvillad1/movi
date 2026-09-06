package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PR 2 del rediseño de Recurrentes (2026-09): con «Flujo libre» y las candidatas mudadas a
 * Movimientos, y editar un recurrente existente ya resuelto desde el detalle de un movimiento
 * (PR 1), Recurrentes dejó de tener una entrada propia en el rail — la única puerta que le
 * quedaba a crear un recurrente "a ciegas", sin movimiento asociado.
 *
 * PR 4 borró `NavTab.RECURRING`, así que ya no hay forma de escribir «el rail no ofrece
 * Recurrentes»: el compilador la cierra. Lo que queda es fijar la lista COMPLETA, que dice lo
 * mismo y además protege el orden.
 */
class MinNavRailTest {

    @Test
    fun `el rail sigue con sus otros destinos, en el mismo orden`() {
        assertTrue(
            railDestinations.map { it.tab } == listOf(
                NavTab.HOME, NavTab.TRANSACTIONS, NavTab.ACCOUNTS, NavTab.CREDITS, NavTab.BUDGETS, NavTab.MORE,
            ),
        )
    }
}
