package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * # Qué modelo contesta cada pregunta
 *
 * El dueño lo pidió así: *«intentemos que sea lo más económico posible… podés sacrificar tiempo de
 * respuesta entre otras cosas»*. El modelo es, de lejos, lo que más pesa en esa cuenta: entre el
 * más chico y el más grande hay más de un orden de magnitud por ficha.
 *
 * Y la mayoría de lo que él pregunta **no necesita el grande**. «¿Cuánto gasté en Comida en
 * agosto?» la contesta una consulta a la base; el modelo solo elige la herramienta y redacta una
 * frase. Lo que sí lo necesita es cuando le pide criterio: si le conviene abonar al vehículo, qué
 * revisar primero, si le alcanza.
 *
 * Por eso esto: una regla de una línea, que se equivoca barato en los dos sentidos. Un falso
 * positivo cuesta una respuesta más cara; un falso negativo, una respuesta más simple de lo que
 * merecía. Ninguno de los dos rompe nada.
 */

/** Lo de todos los días: preguntas de datos, que es casi todo. */
const val MODELO_DE_TODOS_LOS_DIAS = "claude-haiku-4-5-20251001"

/** Cuando le pide criterio. Pensar se enciende solo por este camino. */
const val MODELO_PARA_CONSEJOS = "claude-sonnet-5"

/**
 * El que se sabe que funciona en esta cuenta hoy: es el que contestó todo hasta este cambio. Se usa
 * **solo como reintento** si el de arriba falla — un id de modelo es un texto que viaja a la API, y
 * sin este respaldo un modelo que deje de estar disponible tumbaría el asistente entero.
 */
const val MODELO_DE_RESPALDO = "claude-opus-4-7"

/**
 * **Cuántos mensajes de la conversación se le mandan.** El teléfono manda el hilo entero en cada
 * pregunta, así que sin esto una charla larga se paga completa cada vez. Ocho alcanza para que
 * entienda «¿y en julio?» después de haber hablado de agosto, que es el único caso donde el
 * historial de verdad hace falta.
 */
const val ULTIMOS_MENSAJES_QUE_VIAJAN = 8

/**
 * Señales de que la pregunta pide **criterio** y no un dato. Se comparan sin tildes ni mayúsculas
 * (ver [normalizarParaBuscar]) y como trozo, no como palabra: «recomiend» cubre «recomiendas»,
 * «recomendación» y «me recomendarías».
 */
private val PIDE_CRITERIO = listOf(
    "recomiend", "recomend", "conviene", "converia", "deberia", "debo ", "que hago", "que haria",
    "opinas", "opinion", "analiza", "analisis", "sugier", "vale la pena", "mejor opcion",
    "me alcanza", "puedo pagar", "priorizar", "prioridad", "estrategia", "plan para", "como puedo",
    "que me falta mejorar", "estoy mal", "estoy bien", "riesgo", "me sirve", "tiene sentido",
    // **«¿Por qué…?» es una pregunta de razonamiento, no de dato.** El 23-sep, «¿Por qué
    // Hipotecario 2334 no baja aunque pago la cuota?» —la primera sugerencia del Inicio— la
    // contestó el modelo chico sin pensar, y la respuesta se contradijo: dijo que la cuota no
    // cubría los intereses y en el renglón siguiente mostró que era más grande. Explicar un
    // porqué con plata es cruzar cuota, tasa, seguros y saldo, que es justo lo que el grande hace
    // bien. Se preguntan poco: el costo no se nota, una explicación falsa sí.
    "por que",
)

/**
 * ¿Esta pregunta pide criterio? Se mira **el último mensaje del dueño**, que es el que hay que
 * contestar; lo anterior ya está contestado.
 *
 * [hayImagen] escala siempre: una foto de un recibo o de una oferta del banco se manda justamente
 * para que opine sobre ella.
 *
 * La longitud también escala. No es un proxy perfecto, pero una pregunta larga en esta app casi
 * nunca es «¿cuánto gasté?»: es un párrafo contando una situación.
 */
fun laPreguntaPideCriterio(pregunta: String, hayImagen: Boolean = false): Boolean {
    if (hayImagen) return true
    val limpia = normalizarParaBuscar(pregunta)
    if (limpia.length > LARGO_QUE_YA_ES_UNA_CONSULTA) return true
    return PIDE_CRITERIO.any { it in limpia }
}

/**
 * A partir de acá una pregunta dejó de ser un dato y es una situación contada.
 *
 * El número salió de medir los dos casos: las preguntas de datos del dueño no pasan de unos 40
 * caracteres («¿cuándo vence la cuota del vehículo?»), y un párrafo contando que se le juntaron el
 * colegio y el seguro anda por los 200. Ciento ochenta separa los dos con margen de sobra.
 */
internal const val LARGO_QUE_YA_ES_UNA_CONSULTA = 180
