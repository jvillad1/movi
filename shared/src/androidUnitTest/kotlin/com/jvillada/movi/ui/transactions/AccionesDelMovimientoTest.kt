package com.jvillada.movi.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # La fila de acciones del detalle de un movimiento (vuelta de diseño 17)
 *
 * «Repartir» pasó de ser una tarjeta grande debajo de la categoría a una acción más de la fila. La
 * fila no puede pasar de [MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO] celdas: con siete, «Categoría» se corta
 * a 360 dp. Siete solo pasa con «Tercero» y «Repartir» a la vez, y ahí «Repartir» va adentro de «Más».
 */
class AccionesDelMovimientoTest {

    @Test
    fun `Repartir va despues de Repetir y antes de Tercero y Mas`() {
        val acciones = accionesDelMovimiento(ofreceRepetir = true, ofreceRepartir = true, conTercero = false, conMas = true)
        assertEquals(
            listOf(
                AccionDelMovimiento.Categoria, AccionDelMovimiento.Nota, AccionDelMovimiento.Fecha,
                AccionDelMovimiento.Repetir, AccionDelMovimiento.Repartir, AccionDelMovimiento.Mas,
            ),
            acciones,
        )
        assertFalse(repartirVaEnMas(ofreceRepartir = true, acciones))
    }

    @Test
    fun `sin espacio adonde repartir no hay accion`() {
        val acciones = accionesDelMovimiento(ofreceRepetir = true, ofreceRepartir = false, conTercero = false, conMas = true)
        assertFalse(AccionDelMovimiento.Repartir in acciones)
        assertFalse(repartirVaEnMas(ofreceRepartir = false, acciones))
    }

    @Test
    fun `con siete acciones Repartir se muda a Mas`() {
        val acciones = accionesDelMovimiento(ofreceRepetir = true, ofreceRepartir = true, conTercero = true, conMas = true)
        assertEquals(MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO, acciones.size)
        assertFalse(AccionDelMovimiento.Repartir in acciones)
        assertTrue(AccionDelMovimiento.Tercero in acciones)
        assertTrue(repartirVaEnMas(ofreceRepartir = true, acciones), "no se pierde: queda adentro de «Más»")
    }

    @Test
    fun `nunca pasa del maximo`() {
        listOf(true, false).forEach { repetir ->
            listOf(true, false).forEach { repartir ->
                listOf(true, false).forEach { tercero ->
                    listOf(true, false).forEach { mas ->
                        val acciones = accionesDelMovimiento(repetir, repartir, tercero, mas)
                        assertTrue(acciones.size <= MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO)
                        // Si Repartir no cupo, siempre hay un «Más» que lo recibe.
                        if (repartirVaEnMas(repartir, acciones)) assertTrue(AccionDelMovimiento.Mas in acciones)
                    }
                }
            }
        }
    }
}
