package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole

/**
 * # Lo que de verdad se le manda al asistente, y lo que solo se ve en pantalla
 *
 * El chat guarda una sola lista de mensajes y la usa para dos cosas distintas: pintarla y mandarla.
 * Confundirlas costó los dos errores que esta función existe para cerrar, los dos invisibles desde
 * el código de la pantalla y los dos capaces de dejar a Movi AI mudo:
 *
 * 1. **El saludo.** `messages[0]` es un mensaje del ASISTENTE que nadie pidió («¡Hola! Pregúntame
 *    lo que quieras sobre tu plata»). La Messages API exige que el primero sea del usuario, así que
 *    ese saludo hacía fallar **todos** los turnos con «Error llamando a Claude» — no el primero:
 *    todos. Acá se descarta todo lo que venga antes del primer mensaje del usuario; en pantalla el
 *    saludo se queda donde estaba.
 * 2. **Las fotos.** Cada turno mandaba el historial COMPLETO, con el base64 de todas las imágenes
 *    adjuntadas antes. Una foto de 4 MB se re-subía —y se re-cobraba— en cada pregunta siguiente, y
 *    a la cuarta o quinta la petición pasaba el techo de 32 MB de la API: de ahí en adelante
 *    fallaba todo hasta cerrar y volver a abrir la pantalla. Una imagen viaja **en el turno al que
 *    pertenece**, que es el último; las de antes se quedan en pantalla y viajan como texto.
 *
 * Y un tercer cuidado, por si el dueño deja el chat abierto un día entero: solo viajan los últimos
 * [CUANTOS_MENSAJES_VIAJAN] mensajes. Sin tope, una conversación larga crece hasta que la petición
 * se vuelve cara primero e imposible después.
 *
 * Es una función pura y `internal` a propósito: la pantalla no puede probarse, esto sí (ver
 * `MensajesQueViajanTest`).
 */

/**
 * Cuántos mensajes del historial viajan. Cuarenta son veinte preguntas con sus respuestas — mucho
 * más de lo que sostiene una conversación sobre la plata del mes, y un techo que no depende de que
 * alguien se acuerde de cerrar la pantalla.
 */
internal const val CUANTOS_MENSAJES_VIAJAN = 40

/**
 * Lo que se pone en el lugar de una imagen que ya viajó. No es cosmético: un mensaje sin texto y
 * sin imagen sería un mensaje vacío, y la API rechaza un contenido vacío. Además le dice al modelo
 * que ahí hubo una foto, en vez de dejar un hueco sin explicación.
 */
internal const val TEXTO_DE_IMAGEN_QUE_YA_VIAJO = "(aquí te envié una imagen antes en esta conversación)"

/** Ver el KDoc de arriba. */
internal fun mensajesParaEnviar(historial: List<ChatMessage>): List<ChatMessage> {
    val ultimos = historial.takeLast(CUANTOS_MENSAJES_VIAJAN)
    // El recorte por tope puede dejar una respuesta del asistente adelante, igual que el saludo:
    // por eso el descarte va DESPUÉS de recortar y no antes.
    val desdeElUsuario = ultimos.dropWhile { it.role != ChatRole.USER }
    val ultimo = desdeElUsuario.lastIndex
    return desdeElUsuario.mapIndexed { i, mensaje ->
        if (i == ultimo || mensaje.imageBase64 == null) {
            mensaje
        } else {
            mensaje.copy(
                content = mensaje.content.ifBlank { TEXTO_DE_IMAGEN_QUE_YA_VIAJO },
                imageBase64 = null,
                imageMime = null,
            )
        }
    }
}
