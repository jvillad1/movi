package com.jvillada.movi.ui.transactions

import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.NavStack
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PR 3 del rediseño de Recurrentes (2026-09): el chip con el que arranca Movimientos cuando
 * alguien la abre pidiendo uno.
 *
 * Lo que protege no es un `if`: es que los enlaces que antes iban a la pantalla de Recurrentes
 * —el «Ver todos» de Próximos pagos, la campana, «Anota tus gastos recurrentes», los targets
 * SDUI— aterricen en algo que hable de lo que se tocó. Sin el parámetro, todos caían en la lista
 * completa de movimientos.
 */
class ChipInicialDeMovimientosTest {

    @Test fun `sin pedido arranca en Todo`() {
        assertEquals(CHIP_TODO, chipInicialDeMovimientos(null))
    }

    @Test fun `el chip pedido es el que arranca`() {
        assertEquals(CHIP_RECURRENTES, chipInicialDeMovimientos(CHIP_RECURRENTES))
        assertEquals(CHIP_GASTOS, chipInicialDeMovimientos(CHIP_GASTOS))
        assertEquals(CHIP_ENTRE_CUENTAS, chipInicialDeMovimientos(CHIP_ENTRE_CUENTAS))
    }

    /**
     * El valor viaja adentro de `Screen.Transactions`, así que una pila restaurada o una
     * definición SDUI vieja podrían traer un índice que hoy no existe. Cae en «Todo» —la pantalla
     * completa— y no en un filtro que esconda cosas sin decirlo, ni en un crash.
     */
    @Test fun `un indice fuera de rango cae en Todo, no explota`() {
        assertEquals(CHIP_TODO, chipInicialDeMovimientos(-1))
        assertEquals(CHIP_TODO, chipInicialDeMovimientos(CHIPS_DE_MOVIMIENTOS.size))
        assertEquals(CHIP_TODO, chipInicialDeMovimientos(99))
    }

    /** Y el chip que se pide de verdad existe: si alguien reordena la lista, esto se entera. */
    @Test fun `el chip de recurrentes sigue siendo el ultimo de la lista`() {
        assertEquals("Recurrentes", CHIPS_DE_MOVIMIENTOS[CHIP_RECURRENTES])
    }

    // ── La plomería del parámetro por la navegación ───────────────────────────────

    @Test fun `Movimientos con un chip puesto sigue marcando la pestana Movimientos`() {
        assertEquals(NavTab.TRANSACTIONS, navTabFor(Screen.Transactions(CHIP_RECURRENTES)))
        assertEquals(NavTab.TRANSACTIONS, navTabFor(Screen.Transactions()))
    }

    /** Tocar la pestaña siempre da la pantalla completa: la pestaña no hereda ningún filtro. */
    @Test fun `la pestana Movimientos abre sin filtro`() {
        assertEquals(Screen.Transactions(), screenForTab(NavTab.TRANSACTIONS))
    }

    /**
     * Estando en Movimientos sin filtro, pedir Movimientos-con-chip **sí** se apila: son dos
     * pantallas distintas para el dueño. Si `Screen.Transactions` hubiera quedado como `data
     * object`, `shouldPush` las habría visto iguales y tocar «Ver todos» no habría hecho nada.
     */
    @Test fun `ir de Movimientos sin filtro a Movimientos con el chip puesto se apila`() {
        val pila = listOf<Screen>(Screen.Dashboard, Screen.Transactions())
        assertTrue(NavStack.shouldPush(pila, Screen.Transactions(CHIP_RECURRENTES)))
        // Y volver a pedir exactamente lo mismo que ya está arriba, no.
        assertTrue(!NavStack.shouldPush(pila, Screen.Transactions()))
    }
}
