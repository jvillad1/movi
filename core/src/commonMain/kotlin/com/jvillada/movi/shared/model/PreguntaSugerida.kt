package com.jvillada.movi.shared.model

/**
 * # Las tres preguntas que Movi contesta sin la IA
 *
 * Desde el 4-oct-2026 la cuenta de Anthropic no tiene crédito y Movi AI no contestaba nada. Las tres
 * preguntas que más se tocan —las de respaldo de «Pregúntale a Movi» en Hoy y en el chat— no
 * necesitan un modelo: sus respuestas son cifras que Movi ya calcula para sus pantallas. Así que el
 * server las contesta **siempre** con esas cifras, armadas en frases, sin llamar a Anthropic: es más
 * barato, más rápido, y la respuesta no puede inventar (ver `LasPreguntasSugeridas.kt` en el server).
 *
 * ## Se reconocen por su [id], nunca por el texto
 *
 * El chip que se toca manda el [id] en [ChatMessage.preguntaSugerida]. El server no compara lo que
 * el dueño escribió: «¿cómo voy este periodo?» tecleado a mano es una pregunta libre y va al modelo,
 * con todo lo que el modelo sabe hacer de más. Así una redacción nueva del chip no rompe nada, y una
 * pregunta escrita que se parece no cae por accidente en una respuesta fija.
 *
 * Un APK viejo no manda el campo y sigue yendo al modelo, como siempre.
 */
enum class PreguntaSugerida(
    /** Lo que viaja. Estable: no se cambia aunque cambie el [texto]. */
    val id: String,
    /** Lo que dice el chip y lo que queda como pregunta en la conversación. */
    val texto: String,
) {
    COMO_VOY("como-voy", "¿Cómo voy este período?"),
    EN_QUE_SE_VA("en-que-se-va", "¿En qué se me está yendo más la plata?"),
    QUE_REVISAR("que-revisar", "¿Qué me recomiendas revisar primero?"),
    ;

    companion object {
        /** La pregunta de [id], o `null` si no es ninguna (o viene vacío, o de una versión futura). */
        fun deId(id: String?): PreguntaSugerida? = id?.trim()?.let { limpio -> entries.firstOrNull { it.id == limpio } }
    }
}
