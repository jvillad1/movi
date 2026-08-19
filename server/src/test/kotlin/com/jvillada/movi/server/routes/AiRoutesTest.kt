package com.jvillada.movi.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cobertura de [stripEmojis] — la red de F31. El PERSONA ya le pide al modelo que no mande
 * emojis; esto es lo que filtra lo que se cuele antes de llegar al cliente.
 */
class AiRoutesTest {

    @Test
    fun `deja intacto un texto sin emojis, con tildes y ñ`() {
        val text = "Tu flujo del mes es de \$450.000. ¿Quieres que anote un ahorro pequeño?"
        assertEquals(text, stripEmojis(text))
    }

    @Test
    fun `saca emoji fuera del BMP al final de la frase`() {
        // El filtro solo saca los code points del emoji: el espacio que lo separaba del
        // texto queda tal cual (no es su trabajo recortar espacios sobrantes).
        assertEquals("Vas muy bien este mes. ", stripEmojis("Vas muy bien este mes. 🎉"))
        assertEquals("Cuidado con ese gasto. ", stripEmojis("Cuidado con ese gasto. 💰🚀"))
    }

    @Test
    fun `saca simbolos misceláneos del BMP`() {
        assertEquals("Listo, quedó anotado. ", stripEmojis("Listo, quedó anotado. ✅"))
        assertEquals("Ojo con esto ", stripEmojis("Ojo con esto ⭐⌚"))
    }

    @Test
    fun `saca emoji compuesto con selector de variacion y ZWJ`() {
        // Familia: hombre + ZWJ + mujer + ZWJ + niño, con selector de variación al final.
        val withEmoji = "Piensa en tu familia 👨‍👩‍👦️ al presupuestar."
        assertEquals("Piensa en tu familia  al presupuestar.", stripEmojis(withEmoji))
    }

    @Test
    fun `no toca flechas ni signos de puntuacion normales`() {
        val text = "Del 40% al 60% → mejora. ¡Bien! ¿Seguimos?"
        assertEquals(text, stripEmojis(text))
    }

    @Test
    fun `cadena vacia se queda vacia`() {
        assertEquals("", stripEmojis(""))
    }
}
