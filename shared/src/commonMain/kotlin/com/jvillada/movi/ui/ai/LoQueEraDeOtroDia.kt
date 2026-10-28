package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.fecha.etiquetaCortaDeFecha
import com.jvillada.movi.ui.fecha.fechaCortaDelDia
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * # Lo que Movi dijo otro día (vuelta de diseño 30)
 *
 * Al abrir el chat se veía un turno de ayer sin fecha: «Te quedan $10.869.550 para 5 días». La misma
 * pregunta hoy decía «$2.784.550 para 4 días». Una respuesta vieja que se lee como de hoy es una cifra
 * que miente, aunque fuera verdad el día que se dijo.
 *
 * Ahora cada mensaje trae cuándo se dijo ([ChatMessage.enviadoEn]) y la pantalla:
 *
 * - **separa los días** («Hoy», «Ayer», «2 sep»: [etiquetaCortaDeFecha]) cuando alguno no es de hoy;
 * - **atenúa la respuesta de un día anterior** y le pone debajo «Estas cifras eran de ayer» con
 *   «Preguntar de nuevo», que manda la misma pregunta (con su id si era una de las tres sugeridas:
 *   la sigue contestando Movi sin la IA);
 * - si **la conversación entera es de otro día**, ofrece arriba «Empezar una conversación nueva».
 *   No la borra sola: lo decide el dueño.
 *
 * Todo puro y `internal`: lo prueba `LoQueEraDeOtroDiaTest` con días fijos.
 */

/** Una pieza de la lista del chat: un separador de día o un mensaje. */
internal sealed interface PiezaDelChat {
    /** «Hoy», «Ayer», «2 sep». */
    data class Dia(val rotulo: String) : PiezaDelChat

    /**
     * Un mensaje. [deOtroDia] es la respuesta del asistente de un día anterior a hoy, con
     * [cifrasDe] («de ayer», «del 2 sep») y la pregunta que la pidió ([pregunta]) para volver a hacerla.
     */
    data class Mensaje(
        val mensaje: ChatMessage,
        val deOtroDia: Boolean = false,
        val cifrasDe: String? = null,
        val pregunta: ChatMessage? = null,
    ) : PiezaDelChat
}

/** El día de [mensaje] en la zona de la app, o `null` si no trae fecha. */
internal fun diaDelMensaje(mensaje: ChatMessage, zona: TimeZone = AppTimeZone.zone): LocalDate? =
    mensaje.enviadoEn?.let { fechaDeEpoch(it, zona) }

/** «de ayer», «del 2 sep»: lo que va después de «Estas cifras eran». */
internal fun deCuandoEranLasCifras(dia: LocalDate, hoy: LocalDate): String =
    if (dia == hoy.minus(DatePeriod(days = 1))) "de ayer" else "del ${fechaCortaDelDia(dia, hoy)}"

/** «Estas cifras eran de ayer». */
internal fun textoDeLasCifrasViejas(deCuando: String): String = "Estas cifras eran $deCuando"

/** La acción que vuelve a hacer la pregunta de una respuesta vieja. */
internal const val PREGUNTAR_DE_NUEVO: String = "Preguntar de nuevo"

/**
 * **Las piezas de la lista**, en orden. Los separadores van solo si algún mensaje con fecha no es de
 * [hoy] (una conversación de hoy no necesita decir «Hoy» arriba), antes del primer mensaje de cada
 * día. Un mensaje sin fecha (un server viejo) no abre día ni se atenúa.
 */
internal fun piezasDelChat(
    mensajes: List<ChatMessage>,
    hoy: LocalDate,
    zona: TimeZone = AppTimeZone.zone,
): List<PiezaDelChat> {
    val dias = mensajes.map { diaDelMensaje(it, zona) }
    val conSeparadores = dias.any { it != null && it != hoy }
    var diaAnterior: LocalDate? = null
    var ultimaPregunta: ChatMessage? = null
    return buildList {
        mensajes.forEachIndexed { i, mensaje ->
            val dia = dias[i]
            if (conSeparadores && dia != null && dia != diaAnterior) {
                add(PiezaDelChat.Dia(etiquetaCortaDeFecha(dia, hoy)))
                diaAnterior = dia
            }
            if (mensaje.role == ChatRole.USER) {
                ultimaPregunta = mensaje
                add(PiezaDelChat.Mensaje(mensaje))
            } else {
                val vieja = dia != null && dia < hoy
                add(
                    PiezaDelChat.Mensaje(
                        mensaje = mensaje,
                        deOtroDia = vieja,
                        cifrasDe = if (vieja && dia != null) deCuandoEranLasCifras(dia, hoy) else null,
                        pregunta = if (vieja) ultimaPregunta?.takeIf { it.content.isNotBlank() } else null,
                    ),
                )
            }
        }
    }
}

/**
 * **¿La conversación guardada es de otro día?** Sí si su último mensaje con fecha es de antes de
 * [hoy]. Es cuando el chat ofrece arriba «Empezar una conversación nueva».
 */
internal fun laConversacionEsDeOtroDia(mensajes: List<ChatMessage>, hoy: LocalDate, zona: TimeZone = AppTimeZone.zone): Boolean {
    val ultimo = mensajes.asReversed().firstNotNullOfOrNull { diaDelMensaje(it, zona) } ?: return false
    return ultimo < hoy
}

/** «Esta conversación es de ayer», «… es del 2 sep». `null` si no es de otro día. */
internal fun tituloDeLaConversacionVieja(mensajes: List<ChatMessage>, hoy: LocalDate, zona: TimeZone = AppTimeZone.zone): String? {
    if (!laConversacionEsDeOtroDia(mensajes, hoy, zona)) return null
    val ultimo = mensajes.asReversed().firstNotNullOf { diaDelMensaje(it, zona) }
    return "Esta conversación es ${deCuandoEranLasCifras(ultimo, hoy)}"
}

/** El cuerpo de la tarjeta de la conversación vieja. */
internal const val CUERPO_DE_LA_CONVERSACION_VIEJA: String =
    "Sus cifras eran las de ese día. Lo que ya hablamos queda guardado."

/** La acción de la tarjeta: empieza otra en el server (`POST /api/ai/conversacion/nueva`). */
internal const val EMPEZAR_UNA_CONVERSACION_NUEVA: String = "Empezar una conversación nueva"

/** La secundaria: la tarjeta se va y la conversación sigue. */
internal const val SEGUIR_ESTA: String = "Seguir con esta"

/** La pregunta de [pregunta] como se vuelve a mandar: su texto y, si era una de las tres, su id. */
internal fun preguntaParaRepetir(pregunta: ChatMessage): PreguntaParaTocar =
    PreguntaParaTocar(texto = pregunta.content, sugerida = PreguntaSugerida.deId(pregunta.preguntaSugerida))

// ── Sin la IA, solo lo que se puede contestar ────────────────────────────────

/**
 * **Las preguntas que el chat ofrece** (vuelta de diseño 30). Con la IA disponible, [fuente] tal
 * cual. Sin ella, solo las que Movi contesta sin el modelo —las que llevan su id—, completadas con
 * las tres de respaldo: el chip «¿Por qué salieron $902.250 de más?» llevaba al aviso de «no está
 * disponible», así que no se ofrece.
 */
internal fun preguntasQueSePuedenHacer(fuente: List<PreguntaParaTocar>, iaDisponible: Boolean): List<PreguntaParaTocar> =
    if (iaDisponible) fuente
    else (fuente.filter { it.sugerida != null } + PREGUNTAS_PARA_TOCAR_DE_RESPALDO)
        .distinctBy { it.sugerida }
        .take(CUANTAS_PREGUNTAS_SUGERIDAS)
