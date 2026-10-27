package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.PreguntaSugerida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** # Las preguntas para seguir: las que no se hicieron, de la misma fuente, con su id (vuelta 12). */
class PreguntasParaSeguirTest {

    private val deLasReglas = PreguntaParaTocar("¿Qué deuda me conviene abonar primero?", corta = "¿Qué deuda abono primero?")
    private val fuente = listOf(deLasReglas) + PREGUNTAS_PARA_TOCAR_DE_RESPALDO.take(2)

    @Test
    fun `sin nada preguntado son las de la fuente, como mucho tres`() {
        assertEquals(fuente, preguntasParaSeguir(fuente, emptyList()))
    }

    @Test
    fun `la que se toco no vuelve, y su lugar lo llena una de respaldo`() {
        val mensajes = listOf(
            ChatMessage(ChatRole.USER, PreguntaSugerida.COMO_VOY.texto, preguntaSugerida = PreguntaSugerida.COMO_VOY.id),
            ChatMessage(ChatRole.ASSISTANT, "Vas bien."),
        )
        val quedan = preguntasParaSeguir(fuente, mensajes)
        assertEquals(CUANTAS_PARA_SEGUIR, quedan.size)
        assertTrue(quedan.none { it.sugerida == PreguntaSugerida.COMO_VOY })
        assertEquals(PreguntaSugerida.QUE_REVISAR, quedan.last().sugerida, "la tercera de respaldo completa")
        assertTrue(quedan.filter { it.sugerida != null }.all { it.sugerida!!.texto == it.texto }, "con su id")
    }

    @Test
    fun `una escrita igual a la pregunta, o recargada sin id, cuenta como hecha`() {
        val mensajes = listOf(
            ChatMessage(ChatRole.USER, "  ${deLasReglas.texto} "),
            ChatMessage(ChatRole.USER, PreguntaSugerida.EN_QUE_SE_VA.texto),
        )
        val quedan = preguntasParaSeguir(fuente, mensajes)
        assertEquals(listOf(PreguntaSugerida.COMO_VOY, PreguntaSugerida.QUE_REVISAR), quedan.map { it.sugerida })
    }

    @Test
    fun `lo que dijo el asistente no cuenta como preguntado`() {
        val mensajes = listOf(ChatMessage(ChatRole.ASSISTANT, PreguntaSugerida.COMO_VOY.texto))
        assertEquals(fuente, preguntasParaSeguir(fuente, mensajes))
    }

    @Test
    fun `con todas hechas no queda ninguna`() {
        val mensajes = (fuente + PREGUNTAS_PARA_TOCAR_DE_RESPALDO).map { ChatMessage(ChatRole.USER, it.texto, preguntaSugerida = it.sugerida?.id) }
        assertEquals(emptyList(), preguntasParaSeguir(fuente, mensajes))
    }
}
