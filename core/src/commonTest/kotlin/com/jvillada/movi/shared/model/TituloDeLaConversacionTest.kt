package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El título de una conversación y lo que la lista dice de la retención (vuelta 117)
 *
 * El título es la primera pregunta del dueño, recortada a ~40 caracteres sin partir una palabra, y
 * sin llamar a la IA.
 */
class TituloDeLaConversacionTest {

    @Test
    fun `una pregunta corta queda entera, en un renglon`() {
        assertEquals("¿Cómo voy este período?", tituloDeLaConversacion("  ¿Cómo voy\neste   período?  "))
    }

    @Test
    fun `una larga se corta en la ultima palabra entera, con puntos suspensivos`() {
        val titulo = tituloDeLaConversacion("¿Cuánto gasté en restaurantes el mes pasado con la tarjeta?")
        assertEquals("¿Cuánto gasté en restaurantes el mes…", titulo)
        assertTrue(titulo.length <= LARGO_DEL_TITULO_DE_UNA_CONVERSACION + 1)
    }

    @Test
    fun `no deja una coma colgando antes de los puntos`() {
        assertEquals("Hola Movi, quiero saber cuánto llevo…", tituloDeLaConversacion("Hola Movi, quiero saber cuánto llevo, gastado en total"))
    }

    @Test
    fun `una palabra sola mas larga que el tope se corta ahi`() {
        val larga = "a".repeat(60)
        assertEquals("a".repeat(LARGO_DEL_TITULO_DE_UNA_CONVERSACION) + "…", tituloDeLaConversacion(larga))
    }

    @Test
    fun `solo una foto, o nada`() {
        assertEquals(TITULO_DE_UNA_FOTO, tituloDeLaConversacion("", teniaImagen = true))
        assertEquals(TITULO_SIN_TEXTO, tituloDeLaConversacion("   "))
    }

    @Test
    fun `la retencion se dice siempre, y avisa cerca del tope`() {
        assertEquals("Movi guarda tus últimas 50 conversaciones.", textoDeLaRetencion(3))
        assertEquals("Movi guarda tus últimas 50 conversaciones.", textoDeLaRetencion(AVISAR_DESDE_CONVERSACIONES - 1))
        val cerca = textoDeLaRetencion(47)
        assertTrue(cerca.startsWith("Tienes 47 de 50 conversaciones guardadas."), cerca)
        assertTrue("borra" in cerca, cerca)
    }
}
