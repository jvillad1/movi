package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Lo escrito a mano casi igual que una de las tres preguntas (vuelta 61)
 *
 * «¿Cómo voy?» escrito a mano iba al modelo y, sin crédito, daba el aviso de la IA debajo de un chip
 * que decía lo mismo y sí contestaba. [PreguntaSugerida.delTexto] la reconoce después de normalizar
 * tildes, signos, mayúsculas y espacios, contra una lista corta y explícita. Conservador: lo que solo
 * se parece, o empieza igual, sigue siendo una pregunta libre.
 */
class PreguntaSugeridaDelTextoTest {

    @Test
    fun `normalizar quita tildes, signos y mayusculas y junta los espacios`() {
        assertEquals("como voy este periodo", normalizarPregunta("¿Cómo   voy este PERÍODO?"))
        assertEquals("en que se me esta yendo mas la plata", normalizarPregunta("¿En qué se me está yendo más la plata?!"))
        assertEquals("que reviso primero", normalizarPregunta(" ¿qué reviso, primero? "))
        assertEquals("", normalizarPregunta("¿¡ ?!"))
    }

    @Test
    fun `el texto de cada una se reconoce con o sin tildes y signos`() {
        PreguntaSugerida.entries.forEach { p ->
            assertEquals(p, PreguntaSugerida.delTexto(p.texto))
            assertEquals(p, PreguntaSugerida.delTexto(normalizarPregunta(p.texto)))
            assertEquals(p, PreguntaSugerida.delTexto(p.texto.uppercase()))
        }
    }

    @Test
    fun `los prefijos claros y la version corta del chip tambien`() {
        assertEquals(PreguntaSugerida.COMO_VOY, PreguntaSugerida.delTexto("¿Cómo voy?"))
        assertEquals(PreguntaSugerida.COMO_VOY, PreguntaSugerida.delTexto("como voy"))
        assertEquals(PreguntaSugerida.COMO_VOY, PreguntaSugerida.delTexto("Cómo voy en este período"))
        assertEquals(PreguntaSugerida.EN_QUE_SE_VA, PreguntaSugerida.delTexto("¿En qué se me va la plata?"))
        assertEquals(PreguntaSugerida.QUE_REVISAR, PreguntaSugerida.delTexto("¿Qué reviso primero?"))
        assertEquals(PreguntaSugerida.QUE_REVISAR, PreguntaSugerida.delTexto("que reviso"))
    }

    @Test
    fun `nada de adivinar`() {
        listOf(
            "¿Cómo voy con la deuda del carro?",
            "¿Cómo voy este mes?",
            "como",
            "voy",
            "¿En qué se me fue la plata en agosto?",
            "¿Qué reviso de la tarjeta?",
            "Hola, ¿cómo voy?",
            "",
            "   ",
        ).forEach { assertNull(PreguntaSugerida.delTexto(it), "«$it» es una pregunta libre") }
        assertNull(PreguntaSugerida.delTexto(null))
    }

    @Test
    fun `el id sigue mandando y no es un texto`() {
        assertEquals(PreguntaSugerida.COMO_VOY, PreguntaSugerida.deId("como-voy"))
        assertNull(PreguntaSugerida.deId("¿Cómo voy?"))
    }
}
