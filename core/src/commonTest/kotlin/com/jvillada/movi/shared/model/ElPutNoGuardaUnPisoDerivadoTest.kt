package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Vuelta 125 · un PUT con una copia vieja de la regla no congela su piso ([arranqueQueGuardaUnPut]).
 *
 * El GET devuelve el arranque derivado (la creación, o el primer sello si es anterior). La 121 solo
 * reconocía el eco del derivado de HOY; una copia leída antes de que cambiara el primer sello traía otro, y
 * el PUT lo guardaba como arranque explícito.
 */
class ElPutNoGuardaUnPisoDerivadoTest {

    private val creada = "2026-11-02"

    @Test
    fun `el eco del derivado de hoy no se guarda`() {
        assertNull(arranqueQueGuardaUnPut(pedido = "2026-11-02", guardado = null, creadaEl = creada))
    }

    @Test
    fun `el eco de un derivado viejo tampoco`() {
        // Leída cuando el primer sello era el 28 de octubre; después el dueño le quitó la marca.
        assertNull(arranqueQueGuardaUnPut(pedido = "2026-10-28", guardado = null, creadaEl = creada))
        // Leída cuando todavía no había sello (derivado = la creación); después el dueño selló octubre.
        assertNull(arranqueQueGuardaUnPut(pedido = "2026-11-02", guardado = null, creadaEl = creada))
    }

    @Test
    fun `una fecha posterior a la creacion no puede ser un derivado y se guarda`() {
        assertEquals("2026-12-01", arranqueQueGuardaUnPut(pedido = "2026-12-01", guardado = null, creadaEl = creada))
    }

    @Test
    fun `sin pedido o con uno ilegible queda lo que habia`() {
        assertNull(arranqueQueGuardaUnPut(pedido = null, guardado = null, creadaEl = creada))
        assertEquals("2026-10-15", arranqueQueGuardaUnPut(pedido = null, guardado = "2026-10-15", creadaEl = creada))
        assertEquals("2026-10-15", arranqueQueGuardaUnPut(pedido = "28/10/2026", guardado = "2026-10-15", creadaEl = creada))
    }

    @Test
    fun `con un arranque guardado manda el pedido`() {
        assertEquals("2026-10-15", arranqueQueGuardaUnPut(pedido = "2026-10-15", guardado = "2026-10-15", creadaEl = creada))
        assertEquals("2026-09-01", arranqueQueGuardaUnPut(pedido = "2026-09-01", guardado = "2026-10-15", creadaEl = creada))
    }

    @Test
    fun `una regla de antes de created_at no tiene derivado y guarda lo pedido`() {
        assertEquals("2026-10-28", arranqueQueGuardaUnPut(pedido = "2026-10-28", guardado = null, creadaEl = null))
    }
}
