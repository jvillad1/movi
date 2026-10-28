package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.ui.fecha.epochAlMediodia
import com.jvillada.movi.ui.fecha.etiquetaCortaDeFecha
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta de diseño 30: el chat separa los días, atenúa lo de otro día con «Estas cifras eran de
 * ayer» y «Preguntar de nuevo», y ofrece otra conversación si la guardada es de otro día. Sin la IA,
 * solo ofrece las tres que Movi contesta sin ella.
 */
class LoQueEraDeOtroDiaTest {

    private val hoy = LocalDate(2026, 10, 28)
    private val ayer = LocalDate(2026, 10, 27)
    private val dosDeSeptiembre = LocalDate(2026, 9, 2)

    private fun del(dia: LocalDate) = epochAlMediodia(dia)
    private fun pregunta(texto: String, dia: LocalDate?, sugerida: PreguntaSugerida? = null) =
        ChatMessage(ChatRole.USER, texto, preguntaSugerida = sugerida?.id, enviadoEn = dia?.let(::del))
    private fun respuesta(texto: String, dia: LocalDate?) = ChatMessage(ChatRole.ASSISTANT, texto, enviadoEn = dia?.let(::del))

    @Test
    fun `el separador dice Hoy, Ayer o la fecha corta`() {
        assertEquals("Hoy", etiquetaCortaDeFecha(hoy, hoy))
        assertEquals("Ayer", etiquetaCortaDeFecha(ayer, hoy))
        assertEquals("2 sep", etiquetaCortaDeFecha(dosDeSeptiembre, hoy))
        assertEquals("2 sep 2025", etiquetaCortaDeFecha(LocalDate(2025, 9, 2), hoy))
    }

    @Test
    fun `una conversacion de hoy no lleva separadores ni se atenua`() {
        val piezas = piezasDelChat(listOf(pregunta("¿Cómo voy?", hoy), respuesta("Te quedan \$2.784.550", hoy)), hoy)
        assertTrue(piezas.none { it is PiezaDelChat.Dia })
        assertTrue(piezas.filterIsInstance<PiezaDelChat.Mensaje>().none { it.deOtroDia })
    }

    @Test
    fun `los dias se separan y la respuesta de ayer dice de cuando eran sus cifras`() {
        val vieja = pregunta(PreguntaSugerida.COMO_VOY.texto, ayer, PreguntaSugerida.COMO_VOY)
        val piezas = piezasDelChat(
            listOf(
                pregunta("¿Y en septiembre?", dosDeSeptiembre), respuesta("Gastaste \$1.000.000", dosDeSeptiembre),
                vieja, respuesta("Te quedan \$10.869.550 para 5 días", ayer),
                pregunta("¿Cómo voy?", hoy), respuesta("Te quedan \$2.784.550 para 4 días", hoy),
            ),
            hoy,
        )
        assertEquals(listOf("2 sep", "Ayer", "Hoy"), piezas.filterIsInstance<PiezaDelChat.Dia>().map { it.rotulo })
        val respuestas = piezas.filterIsInstance<PiezaDelChat.Mensaje>().filter { it.mensaje.role == ChatRole.ASSISTANT }
        assertEquals(listOf(true, true, false), respuestas.map { it.deOtroDia })
        assertEquals(listOf("del 2 sep", "de ayer", null), respuestas.map { it.cifrasDe })
        assertEquals("Estas cifras eran de ayer", textoDeLasCifrasViejas(respuestas[1].cifrasDe!!))
        // «Preguntar de nuevo» manda la misma pregunta, con su id si era una de las tres.
        val repetida = preguntaParaRepetir(respuestas[1].pregunta!!)
        assertEquals(PreguntaSugerida.COMO_VOY.texto, repetida.texto)
        assertEquals(PreguntaSugerida.COMO_VOY, repetida.sugerida)
        assertNull(preguntaParaRepetir(respuestas[0].pregunta!!).sugerida, "lo escrito a mano va sin id")
    }

    @Test
    fun `sin fecha se pinta como siempre`() {
        val piezas = piezasDelChat(listOf(pregunta("Hola", null), respuesta("Hola", null)), hoy)
        assertEquals(2, piezas.size)
        assertTrue(piezas.filterIsInstance<PiezaDelChat.Mensaje>().none { it.deOtroDia })
        assertFalse(laConversacionEsDeOtroDia(listOf(respuesta("Hola", null)), hoy))
    }

    @Test
    fun `se ofrece otra conversacion solo si la ultima respuesta es de otro dia`() {
        val deAyer = listOf(pregunta("¿Cómo voy?", ayer), respuesta("Te quedan \$10.869.550", ayer))
        assertTrue(laConversacionEsDeOtroDia(deAyer, hoy))
        assertEquals("Esta conversación es de ayer", tituloDeLaConversacionVieja(deAyer, hoy))
        assertEquals(
            "Esta conversación es del 2 sep",
            tituloDeLaConversacionVieja(listOf(respuesta("x", dosDeSeptiembre)), hoy),
        )
        val sigueHoy = deAyer + pregunta("¿Y hoy?", hoy) + respuesta("Hoy…", hoy)
        assertFalse(laConversacionEsDeOtroDia(sigueHoy, hoy))
        assertNull(tituloDeLaConversacionVieja(sigueHoy, hoy))
    }

    @Test
    fun `sin la IA solo se ofrecen las tres que se contestan sin ella`() {
        val fuente = listOf(
            PreguntaParaTocar("¿Por qué salieron \$902.250 de más?"),
            PreguntaParaTocar("¿Qué deuda me conviene abonar primero?"),
            PREGUNTAS_PARA_TOCAR_DE_RESPALDO.first(),
        )
        assertEquals(fuente, preguntasQueSePuedenHacer(fuente, iaDisponible = true))
        val sinIa = preguntasQueSePuedenHacer(fuente, iaDisponible = false)
        assertEquals(PREGUNTAS_PARA_TOCAR_DE_RESPALDO, sinIa)
        assertTrue(sinIa.all { it.sugerida != null }, "todas llevan su id: las contesta Movi sin el modelo")
    }
}
