package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.dashboard.deCuandoEs
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
        /**
         * Vuelta 45: lo que va debajo de una respuesta de HOY que ya no vale —«Esto era a las 9:40; ahora
         * te quedan $773.950»—, o `null`. Con él, [deOtroDia] también es `true` (se atenúa igual).
         */
        val vieja: String? = null,
    ) : PiezaDelChat
}

// ── Vuelta 45: una respuesta de hoy también se pone vieja ─────────────────────

/** Pasada esta hora, una respuesta de hoy que cita cifras ya no se muestra como actual. */
internal const val VIGENCIA_DE_UNA_RESPUESTA_MS: Long = 60L * 60L * 1000L

/** ¿El texto cita alguna cifra en pesos? «$773.950», «$1,8M». */
internal fun citaCifras(texto: String): Boolean = Regex("""\$\s?\d""").containsMatchIn(texto)

/**
 * La cifra de «Te quedan $2.784.550» en una respuesta (la de «¿Cómo voy?» y la que copia al héroe), en
 * pesos, o `null` si no la dice. Solo la forma entera con puntos de miles: la que escribe el server.
 */
internal fun teQuedanCitado(texto: String): Long? =
    Regex("""[Tt]e quedan \$(\d{1,3}(?:\.\d{3})*)(?![\d,])""").find(texto)
        ?.groupValues?.get(1)?.replace(".", "")?.toLongOrNull()

/**
 * **¿Esta respuesta de hoy ya no vale?** (vuelta 45) Al abrir el chat a las 3 p. m. se leía, como de
 * hoy, la respuesta de las 9:40 —«Te quedan $2.784.550»— cuando ya quedaban $773.950, y el chip de abajo
 * decía otra cifra. Se pone vieja si cita cifras y pasó más de [VIGENCIA_DE_UNA_RESPUESTA_MS], o si su
 * «Te quedan» ya no es el de ahora ([teQuedanAhora], el del Inicio). `null` = sigue valiendo.
 *
 * El «Te quedan» de ahora solo cuenta si se leyó **después** de la respuesta ([datosDe], cuándo cargó
 * el Inicio): una lectura de la mañana no puede desmentir una respuesta del mediodía. Una respuesta
 * dicha después de [ahora] (recién llegada) nunca es vieja.
 *
 * @return «Esto era a las 9:40», y si cambió lo que queda, «…; ahora te quedan $773.950».
 */
internal fun loQueDiceLaRespuestaVieja(
    mensaje: ChatMessage,
    ahora: Long,
    teQuedanAhora: Long?,
    datosDe: Long?,
    cifra: (Long) -> String,
): String? {
    val dicha = mensaje.enviadoEn ?: return null
    if (dicha >= ahora || !citaCifras(mensaje.content)) return null
    val citado = teQuedanCitado(mensaje.content)
    val actual = teQuedanAhora?.takeIf { datosDe != null && datosDe > dicha }
    val cambio = citado != null && actual != null && citado != actual
    if (ahora - dicha <= VIGENCIA_DE_UNA_RESPUESTA_MS && !cambio) return null
    val cuando = "Esto era ${deCuandoEs(dicha, ahora)}"
    return if (cambio && actual != null && actual >= 0L) "$cuando; ahora te quedan ${cifra(actual)}" else cuando
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
    /** Vuelta 45: el momento de ahora, para saber si una respuesta de HOY ya no vale. `null` no mira. */
    ahora: Long? = null,
    /** El «Te quedan» de ahora (el del Inicio), si se sabe, y cuándo se leyó. */
    teQuedanAhora: Long? = null,
    datosDe: Long? = null,
    cifra: (Long) -> String = { it.toString() },
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
                val deOtroDia = dia != null && dia < hoy
                // Vuelta 45: una de hoy también se pone vieja (ver [loQueDiceLaRespuestaVieja]).
                val viejaDeHoy = if (!deOtroDia && ahora != null) {
                    loQueDiceLaRespuestaVieja(mensaje, ahora, teQuedanAhora, datosDe, cifra)
                } else {
                    null
                }
                val vieja = deOtroDia || viejaDeHoy != null
                add(
                    PiezaDelChat.Mensaje(
                        mensaje = mensaje,
                        deOtroDia = vieja,
                        cifrasDe = if (deOtroDia && dia != null) deCuandoEranLasCifras(dia, hoy) else null,
                        pregunta = if (vieja) ultimaPregunta?.takeIf { it.content.isNotBlank() } else null,
                        vieja = viejaDeHoy,
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

/** Vuelta 77: cuántas de las que arman las reglas (van al modelo) se ofrecen, después de las tres gratis. */
internal const val PREGUNTAS_DEL_MODELO_EN_EL_ARRANQUE: Int = 2

/**
 * **Las preguntas de una conversación nueva** (vuelta 77): las tres que Movi contesta sin el modelo
 * ([PREGUNTAS_PARA_TOCAR_DE_RESPALDO], gratis) van **siempre primero**; después, hasta
 * [PREGUNTAS_DEL_MODELO_EN_EL_ARRANQUE] de las que arman las reglas con las cifras del dueño (esas van
 * al modelo). Antes el arranque solo ofrecía las de las reglas: con tres señales, las gratis no se veían
 * nunca. Sin la IA, solo las tres ([preguntasQueSePuedenHacer]).
 */
internal fun preguntasDelArranque(fuente: List<PreguntaParaTocar>, iaDisponible: Boolean): List<PreguntaParaTocar> {
    if (!iaDisponible) return preguntasQueSePuedenHacer(fuente, iaDisponible = false)
    val delModelo = fuente.filter { it.sugerida == null }.take(PREGUNTAS_DEL_MODELO_EN_EL_ARRANQUE)
    return (PREGUNTAS_PARA_TOCAR_DE_RESPALDO + delModelo).distinctBy { it.texto }
}
