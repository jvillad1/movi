package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Movi AI: cuántas preguntas libres por mes, y cuánto cuesta (vuelta 106)
 *
 * El dueño quiere que Movi AI escale sin que un usuario intenso lo arruine. Cada usuario tiene **N
 * preguntas libres por mes calendario** (por defecto [TOPE_DE_PREGUNTAS_POR_DEFECTO]); solo cuentan
 * las que van al modelo —las tres sugeridas las contesta Movi sin la IA y no cuentan— y el admin no
 * tiene tope. El admin ajusta el valor global y las excepciones por usuario desde la app (Ajustes →
 * Administración → «Preguntas de Movi AI»).
 *
 * Viven en `:core` porque las frases las dicen los dos lados: el server las manda en el texto (para
 * un APK que no conoce el código) y la app las pinta.
 */

/** El código del 429 de `POST /api/ai/chat` cuando el usuario ya hizo sus preguntas del mes. */
const val IA_TOPE_DEL_MES: String = "IA_TOPE_DEL_MES"

/** Las preguntas libres por mes de quien no tiene excepción, mientras el admin no elija otro número. */
const val TOPE_DE_PREGUNTAS_POR_DEFECTO: Int = 100

/** Desde cuántas preguntas restantes se avisa «Te quedan N preguntas este mes». */
const val AVISAR_CUANDO_QUEDEN: Int = 10

/** El techo de lo que se puede elegir: más que esto ya no es un tope. */
const val TOPE_MAXIMO_DE_PREGUNTAS: Int = 100_000

/**
 * Lo que dice la app (y el texto del 429) cuando se llegó al tope. Con tope 0 no «llegó» a nada: el
 * admin le dejó solo las tres sugeridas.
 */
fun fraseDelTopeDelMes(tope: Int): String = when {
    tope <= 0 -> "Movi AI no está disponible para tu cuenta este mes"
    tope == 1 -> "Llegaste a la pregunta de este mes. Se renueva el 1.º"
    else -> "Llegaste a las ${conPuntosDeMiles(tope.toLong())} preguntas de este mes. Se renuevan el 1.º"
}

/**
 * El aviso de debajo de una respuesta cuando se acerca el tope: `null` si todavía quedan más de
 * [AVISAR_CUANDO_QUEDEN]. [quedan] es lo que queda **después** de la pregunta que se acaba de contestar.
 */
fun fraseDeLasQueQuedan(quedan: Int): String? = when {
    quedan > AVISAR_CUANDO_QUEDEN -> null
    quedan <= 0 -> "Esa fue tu última pregunta libre de este mes. Se renuevan el 1.º"
    quedan == 1 -> "Te queda 1 pregunta este mes"
    else -> "Te quedan $quedan preguntas este mes"
}

/** `null` si [tope] se puede guardar; si no, por qué. Cero vale: deja solo las tres sugeridas. */
fun problemaDelTope(tope: Int): String? =
    if (tope < 0 || tope > TOPE_MAXIMO_DE_PREGUNTAS) "El tope va de 0 a ${conPuntosDeMiles(TOPE_MAXIMO_DE_PREGUNTAS.toLong())} preguntas." else null

/** Un usuario en la pantalla del tope: cuántas hizo este mes y, si tiene excepción, su número. */
@Serializable
data class TopeDeUnUsuario(
    val usuarioId: String,
    val correo: String = "",
    val usadasEsteMes: Int = 0,
    /** Su excepción; `null` = le toca el global. */
    val tope: Int? = null,
    /** Un admin no tiene tope, tenga o no excepción. */
    val esAdmin: Boolean = false,
)

/** `GET /api/admin/ia/topes`: el valor global, las excepciones y los usuarios para elegir. */
@Serializable
data class TopesDeLaIa(
    val global: Int = TOPE_DE_PREGUNTAS_POR_DEFECTO,
    /** `false` mientras el admin no eligió: el global es [TOPE_DE_PREGUNTAS_POR_DEFECTO]. */
    val globalElegido: Boolean = false,
    val excepciones: List<TopeDeUnUsuario> = emptyList(),
    /** Todos, el que más preguntó primero (para elegir a quién darle una excepción). */
    val usuarios: List<TopeDeUnUsuario> = emptyList(),
)

/** El cuerpo de `PUT /api/admin/ia/topes/global` y `PUT /api/admin/ia/topes/usuarios/{id}`. */
@Serializable
data class GuardarTopeDeLaIa(val tope: Int)

/** Lo que gastó en IA un usuario en 30 días: sus preguntas al modelo y lo que costaron. */
@Serializable
data class GastoDeIaDeUnUsuario(
    val usuarioId: String,
    val correo: String = "",
    val preguntas: Int,
    val dolares: Double,
    val pesos: Long,
)

/**
 * `GET /api/admin/ia/costo`: lo que cuesta la IA, en pesos, para el admin («Fallos y uso» →
 * Servidor). Los totales de 7 y 30 días y el mes salen de `ia_por_dia` (toda la IA: el chat y los
 * papeles); los que más gastan, de `ai_turns` (el chat). Pesos con la TRM de hoy ([trm]).
 */
@Serializable
data class CostoDeLaIa(
    val trm: Double,
    /** La TRM no se pudo leer y se usó la constante del código: las cifras en pesos son aproximadas. */
    val trmEsRespaldo: Boolean = false,
    val dolares7: Double = 0.0,
    val dolares30: Double = 0.0,
    val pesos7: Long = 0,
    val pesos30: Long = 0,
    /** Usuarios activos de 30 días (los de la pestaña Uso). */
    val activos30: Int = 0,
    /** [pesos30] / [activos30]; `null` sin activos. */
    val pesosPorActivo30: Long? = null,
    /** Preguntas al modelo en 30 días (sin las tres sugeridas) y lo que costó cada una en promedio. */
    val preguntas30: Int = 0,
    val pesosPorPregunta30: Double? = null,
    /** Lo del mes calendario en curso y la proyección al cierre (lo de hoy / días que van × días del mes). */
    val pesosDelMes: Long = 0,
    val proyeccionDelMes: Long = 0,
    val diaDelMes: Int = 1,
    val diasDelMes: Int = 30,
    /**
     * Los días de este mes con costo medido (hasta hoy): [diaDelMes] salvo el primer mes, que empieza
     * el día del despliegue. La proyección divide por estos, no por el día del mes.
     */
    val diasMedidosDelMes: Int = 1,
    val quienesMasGastan: List<GastoDeIaDeUnUsuario> = emptyList(),
    /** El primer día con costo medido (antes de la vuelta 106 no se guardaba): `null` si ninguno. */
    val medidoDesde: String? = null,
    /** Modelos que se usaron y no están en la tabla de precios: su costo no se cuenta. */
    val sinPrecio: List<String> = emptyList(),
    /** De dónde salen los precios y de qué fecha son. */
    val precios: String = "",
)
