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
 * ## Se reconocen por su [id], o por un texto casi idéntico
 *
 * El chip que se toca manda el [id] en [ChatMessage.preguntaSugerida]. Así una redacción nueva del
 * chip no rompe nada.
 *
 * **Vuelta 61: también lo escrito a mano, si es casi idéntico** ([delTexto]). Sin crédito en
 * Anthropic, «¿Cómo voy?» escrito a mano daba el aviso de la IA no disponible justo debajo de un chip
 * que decía lo mismo y sí contestaba. Ahora se compara lo escrito **normalizado** (sin tildes, sin
 * signos, en minúscula y con los espacios juntos) contra una lista corta y explícita por pregunta —su
 * texto, la versión corta del chip y unas pocas formas del mismo pedido—. **Nada de adivinar**:
 * «¿cómo voy con el carro?» o «¿cómo voy este mes?» no son la pregunta y van al modelo, con todo lo
 * que el modelo sabe hacer de más.
 */
enum class PreguntaSugerida(
    /** Lo que viaja. Estable: no se cambia aunque cambie el [texto]. */
    val id: String,
    /** Lo que dice el chip y lo que queda como pregunta en la conversación. */
    val texto: String,
    /**
     * Lo que, además del [texto], cuenta como esta pregunta escrita a mano ([delTexto]). Cada forma
     * ya normalizada ([normalizarPregunta]): sin tildes, sin signos, en minúscula.
     */
    private val otrasFormas: List<String>,
) {
    COMO_VOY(
        "como-voy", "¿Cómo voy este período?",
        "como voy", "como voy en este periodo", "como voy en el periodo", "como voy este periodo",
    ),
    EN_QUE_SE_VA(
        "en-que-se-va", "¿En qué se me está yendo más la plata?",
        "en que se me va la plata", "en que se me va mas la plata", "en que se me esta yendo la plata",
        "en que se va la plata", "en que se me va", "en que se va mi plata",
    ),
    QUE_REVISAR(
        "que-revisar", "¿Qué me recomiendas revisar primero?",
        "que reviso primero", "que reviso", "que me recomiendas revisar", "que debo revisar primero",
        "que deberia revisar primero",
    ),
    ;

    constructor(id: String, texto: String, vararg otrasFormas: String) : this(id, texto, otrasFormas.toList())

    companion object {
        /** La pregunta de [id], o `null` si no es ninguna (o viene vacío, o de una versión futura). */
        fun deId(id: String?): PreguntaSugerida? = id?.trim()?.let { limpio -> entries.firstOrNull { it.id == limpio } }

        /**
         * La pregunta que [texto] escrito a mano dice **casi igual** (vuelta 61), o `null`. Casi igual
         * = idéntico después de [normalizarPregunta] a su texto o a una de sus formas: «¿cómo voy?»,
         * «Como voy este periodo», «¿Qué reviso primero?». Lo demás, aunque empiece igual («cómo voy
         * con la deuda del carro»), es una pregunta libre.
         */
        fun delTexto(texto: String?): PreguntaSugerida? {
            val normal = normalizarPregunta(texto ?: return null)
            if (normal.isEmpty()) return null
            return entries.firstOrNull { p -> normal == normalizarPregunta(p.texto) || normal in p.otrasFormas }
        }
    }
}

/**
 * Una pregunta escrita, lista para compararla (vuelta 61): en minúscula, sin tildes ni diéresis, sin
 * signos («¿», «?», «¡», «!», comas, puntos) y con los espacios juntos. «¿Cómo   voy?» → «como voy».
 */
fun normalizarPregunta(texto: String): String = buildString {
    texto.lowercase().forEach { c ->
        val base = when (c) {
            'á', 'à', 'ä', 'â' -> 'a'
            'é', 'è', 'ë', 'ê' -> 'e'
            'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô' -> 'o'
            'ú', 'ù', 'ü', 'û' -> 'u'
            'ñ' -> 'n'
            else -> c
        }
        append(if (base.isLetterOrDigit()) base else ' ')
    }
}.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
