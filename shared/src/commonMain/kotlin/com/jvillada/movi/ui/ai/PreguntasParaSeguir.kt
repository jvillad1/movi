package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.ui.components.ds.PildoraDeAccion

/**
 * # Seguir la conversación (vuelta de diseño 12)
 *
 * Después de una respuesta no había nada que invitara a seguir: las preguntas sugeridas solo se
 * veían con el chat vacío. Ahora, debajo de la última respuesta, van como píldoras las que **no** se
 * hicieron todavía en esta conversación.
 *
 * - **La misma fuente que el chat vacío**: las de [preguntasParaTocar] (las de las reglas con las
 *   cifras del dueño y las tres de respaldo) y, para completar, las tres de respaldo. No se inventa
 *   ninguna.
 * - **Con su id**: una de las tres de respaldo viaja con su [com.jvillada.movi.shared.model.PreguntaSugerida]
 *   y la sigue contestando el server sin la IA.
 * - **Ya hecha** es la que el dueño mandó en esta conversación: por su id, o por su texto (lo escrito
 *   a mano igual que la pregunta, o una conversación recargada).
 * - Como mucho [CUANTAS_PARA_SEGUIR].
 */

/** Cuántas preguntas para seguir, como mucho. */
const val CUANTAS_PARA_SEGUIR: Int = 3

/** Tag estable del renglón de las preguntas para seguir. */
const val TAG_PREGUNTAS_PARA_SEGUIR: String = "ai-preguntas-para-seguir"

/** **Las que quedan por hacer**, de [fuente] (y las de respaldo), sin las de [mensajes]. Pura. */
fun preguntasParaSeguir(fuente: List<PreguntaParaTocar>, mensajes: List<ChatMessage>): List<PreguntaParaTocar> {
    val delDueno = mensajes.filter { it.role == ChatRole.USER }
    val ids = delDueno.mapNotNull { it.preguntaSugerida?.trim() }.toSet()
    val textos = delDueno.map { it.content.trim() }.toSet()
    return (fuente + PREGUNTAS_PARA_TOCAR_DE_RESPALDO)
        .distinctBy { it.texto }
        .filterNot { p -> p.texto.trim() in textos || (p.sugerida != null && p.sugerida.id in ids) }
        .take(CUANTAS_PARA_SEGUIR)
}

/**
 * Las píldoras, alineadas con el texto de la respuesta (después del ícono del asistente). Dicen la
 * versión corta de cada pregunta ([PreguntaParaTocar.corta]); lo que se manda, y lo que oye el lector
 * de pantalla, es la completa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreguntasParaSeguir(preguntas: List<PreguntaParaTocar>, onPregunta: (PreguntaParaTocar) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Icono.l + Espacio.s, bottom = Espacio.s)
            .testTag(TAG_PREGUNTAS_PARA_SEGUIR),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        preguntas.forEach { pregunta ->
            PildoraDeAccion(
                texto = pregunta.corta,
                onClick = { onPregunta(pregunta) },
                icono = Icons.Rounded.AutoAwesome,
                descripcion = pregunta.texto,
            )
        }
    }
}
