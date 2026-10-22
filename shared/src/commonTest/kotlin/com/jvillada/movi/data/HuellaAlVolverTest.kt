package com.jvillada.movi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** **La huella se vuelve a pedir al volver tras más de 5 minutos afuera**, y solo entonces. */
class HuellaAlVolverTest {

    private val salio = 1_700_000_000_000L
    private val cincoMinutos = 5 * 60 * 1000L

    private fun pide(fuera: Long?, ahora: Long, sesion: Boolean = true, huella: Boolean = true) =
        pedirLaHuellaAlVolver(fueraDesde = fuera, ahora = ahora, haySesion = sesion, huellaActivada = huella)

    @Test
    fun `el umbral son cinco minutos`() {
        assertEquals(cincoMinutos, MS_EN_SEGUNDO_PLANO_PARA_PEDIR_LA_HUELLA)
    }

    @Test
    fun `mas de cinco minutos afuera la pide`() {
        assertTrue(pide(salio, salio + cincoMinutos + 1))
        assertTrue(pide(salio, salio + 3 * 60 * 60 * 1000L))
    }

    @Test
    fun `cinco minutos o menos no la pide`() {
        assertFalse(pide(salio, salio + 30_000L), "contestar un mensaje no cuesta el dedo")
        assertFalse(pide(salio, salio + cincoMinutos), "«más de» cinco minutos, no cinco justos")
    }

    @Test
    fun `sin puerta o sin sesion no la pide`() {
        assertFalse(pide(salio, salio + 2 * cincoMinutos, huella = false))
        assertFalse(pide(salio, salio + 2 * cincoMinutos, sesion = false))
    }

    @Test
    fun `sin saber cuando se fue, o con el reloj para atras, no la pide`() {
        assertFalse(pide(null, salio))
        assertFalse(pide(salio, salio - 2 * cincoMinutos))
    }

    @Test
    fun `el umbral se puede elegir`() {
        assertTrue(pedirLaHuellaAlVolver(salio, salio + 2_000L, haySesion = true, huellaActivada = true, umbralMs = 1_000L))
    }
}
