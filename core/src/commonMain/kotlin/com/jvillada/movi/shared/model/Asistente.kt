package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Lo que el asistente guarda de una conversación (Ola 3 · «Movi actúa»)
 *
 * Hasta la Ola 3 el chat vivía en un `remember` de la pantalla: salir y volver era empezar de
 * cero, aunque el server ya guardaba cada turno en `ai_turns` para diagnosticar. Ahora esa misma
 * tabla es la que devuelve la conversación al volver.
 */

/**
 * `GET /api/ai/conversacion` (la más reciente, para un APK viejo) y, desde la vuelta 117,
 * `GET /api/ai/conversaciones/{id}`: los mensajes de una conversación, del más viejo al más nuevo,
 * listos para pintar. Vacía después de «Nueva conversación».
 */
@Serializable
data class ConversacionDelAsistente(
    val mensajes: List<ChatMessage> = emptyList(),
    /**
     * Vuelta 117 · el id de esta conversación, para mandar las preguntas siguientes a ella
     * ([AiChatRequest.conversacionId]). `null` de un server anterior, o si el dueño todavía no tiene
     * ninguna.
     */
    val id: String? = null,
    /** Vuelta 117 · su título ([tituloDeLaConversacion]); vacío mientras no tiene preguntas. */
    val titulo: String = "",
    /**
     * **¿Movi AI puede contestar preguntas libres ahora?** (vuelta de diseño 30). `false` cuando el
     * server no tiene clave o la última llamada a Anthropic falló por la cuenta (sin crédito, clave
     * rechazada: ver `EstadoDeLaIa` en el server). Con `false` el chat no sugiere preguntas que irían
     * al modelo: solo las tres que Movi contesta sin él. Default `true` para un server viejo (que no
     * lo manda): el chat se ve como siempre.
     */
    val iaDisponible: Boolean = true,
)

/**
 * # Lo que el asistente PROPONE hacer (Ola 3 · «Movi actúa»)
 *
 * Movi AI ya contestaba bien; lo que no podía era hacer nada. Ahora puede **proponer** — y solo
 * proponer. La regla que ordena todo esto: **el asistente nunca escribe solo**.
 *
 * 1. El modelo pide una herramienta que propone (`proponer_movimiento`, …). Esa herramienta NO
 *    escribe nada: valida en el server —la cuenta es del dueño, el monto es mayor que 0, la
 *    categoría existe o se marca como nueva— y arma una [AccionPropuesta]. Una propuesta inválida
 *    no llega a la app: el modelo recibe el motivo y tiene que corregir o preguntar.
 * 2. La propuesta viaja en la respuesta del chat y la app la pinta como tarjeta con «Hacerlo» / «No».
 * 3. «Hacerlo» llama **al endpoint que ya existe** para esa acción, con su validación de siempre
 *    (`POST /api/events`, `PUT /api/events/category-en-lote`, `POST /api/recurring-rules`,
 *    `POST /api/recurring-rules/{id}/occurrence`). No hay una ruta paralela que escriba.
 * 4. «Hacerlo» o «No» quedan anotados en la propuesta, y el asistente lo lee en el turno siguiente.
 *
 * Los datos de cada tipo viajan **ya en la forma del endpoint que los recibe** ([movimiento] es el
 * `FinancialEvent` que se postea, [recurrente] la `RecurringRule`), para que confirmar no tenga
 * que traducir nada: lo que el dueño vio en la tarjeta es exactamente lo que se manda.
 */
@Serializable
enum class TipoDeAccion {
    /** «Hoy gasté 45 mil en almuerzo con la Nu». Se confirma con `POST /api/events`. */
    ANOTAR_MOVIMIENTO,
    /** Uno o varios movimientos parecidos a otra categoría. `PUT /api/events/{id}/category` o `/category-en-lote`. */
    CAMBIAR_CATEGORIA,
    /** Un pago o ingreso que se repite cada mes. `POST /api/recurring-rules`. */
    CREAR_RECURRENTE,
    /**
     * Un pago del período ya se hizo **con un movimiento que existe** (el «Es este» del checklist).
     * `POST /api/recurring-rules/{id}/occurrence` con el `eventId`. Nunca sin movimiento.
     */
    MARCAR_PAGO_HECHO,
    /** Algo durable que el dueño contó y Movi debería recordar. `POST /api/asistente/memoria`. */
    RECORDAR,
}

/** Qué dijo el dueño de una propuesta. */
@Serializable
enum class EstadoDePropuesta { PENDIENTE, HECHA, RECHAZADA }

@Serializable
data class AccionPropuesta(
    val id: String,
    val tipo: TipoDeAccion,
    /** Lo que el dueño lee en la tarjeta, con las cifras ya formateadas: «Anotar un gasto de $45.000 en Comida, desde Nu, hoy». */
    val frase: String,
    val estado: EstadoDePropuesta = EstadoDePropuesta.PENDIENTE,
    /** [TipoDeAccion.ANOTAR_MOVIMIENTO]: el movimiento tal cual se postea, con su id ya puesto (un doble toque no lo duplica). */
    val movimiento: FinancialEvent? = null,
    /** [TipoDeAccion.CAMBIAR_CATEGORIA]: los movimientos que cambian. */
    val idsDeMovimientos: List<String> = emptyList(),
    /** [TipoDeAccion.CAMBIAR_CATEGORIA]: a qué categoría. */
    val categoria: String? = null,
    /** La categoría de la propuesta todavía no existe: se crea al confirmar. La tarjeta lo dice. */
    val categoriaNueva: Boolean = false,
    /** [TipoDeAccion.CREAR_RECURRENTE]: la regla tal cual se postea. */
    val recurrente: RecurringRule? = null,
    /** [TipoDeAccion.MARCAR_PAGO_HECHO]: la regla, el período (`"2026-09"`) y el movimiento que lo prueba. */
    val reglaId: String? = null,
    val periodo: String? = null,
    val eventId: String? = null,
    /** [TipoDeAccion.RECORDAR]: el texto que se guardaría en «Lo que Movi sabe de ti». */
    val recuerdo: String? = null,
)

/** `POST /api/ai/propuestas/{id}/estado`: lo que el dueño decidió. */
@Serializable
data class ResolverPropuestaRequest(val estado: EstadoDePropuesta)

/**
 * # «Lo que Movi sabe de ti» (Ola 3)
 *
 * Lo que el dueño le contó al asistente y él mismo confirmó que se guarde: «Caro es mi esposa», «el
 * bono de Glim de $55.500 no es mensual», «pago el colegio de mi hija el 25». Entra al contexto de
 * cada conversación (con tope) y se ve, se corrige y se borra en Ajustes. **Nunca se guarda en
 * silencio**: llega por una propuesta del asistente ([TipoDeAccion.RECORDAR]) que el dueño confirma.
 */
@Serializable
data class RecuerdoDelAsistente(
    val id: String,
    val texto: String,
    /** Cuándo se guardó (epoch ms). */
    val creadoEn: Long,
    /** De dónde salió: ver [OrigenDelRecuerdo]. */
    val origen: OrigenDelRecuerdo = OrigenDelRecuerdo.CONVERSACION,
    /** Cuándo lo corrigió el dueño por última vez; `null` si nunca. */
    val editadoEn: Long? = null,
)

@Serializable
enum class OrigenDelRecuerdo {
    /** Lo propuso el asistente en una conversación y el dueño tocó «Hacerlo». */
    CONVERSACION,
    /** Lo escribió el dueño en «Lo que Movi sabe de ti». */
    A_MANO,
}

/** `POST /api/asistente/memoria` y `PUT /api/asistente/memoria/{id}`. */
@Serializable
data class GuardarRecuerdoRequest(
    val texto: String,
    val origen: OrigenDelRecuerdo = OrigenDelRecuerdo.CONVERSACION,
    /** La propuesta que lo originó, si vino de una. Solo para saber de dónde salió. */
    val propuestaId: String? = null,
)

/** Cuánto puede medir un recuerdo. Una frase, no un diario: lo que no entra en esto no es un dato durable. */
const val LARGO_MAXIMO_DE_UN_RECUERDO: Int = 300

/**
 * # Varias conversaciones (vuelta 117)
 *
 * Hasta la vuelta 117 el dueño tenía UNA conversación: «Nueva conversación» movía una marca y lo de
 * antes quedaba en el server sin forma de verlo. Ahora cada turno pertenece a una conversación con
 * id, título y fechas, y la app las lista, las abre y las borra.
 *
 * `GET /api/ai/conversaciones`: una fila de la lista. Solo las que tienen al menos una pregunta: una
 * conversación vacía no se lista.
 */
@Serializable
data class ConversacionEnLaLista(
    val id: String,
    /** La primera pregunta del dueño, recortada ([tituloDeLaConversacion]). */
    val titulo: String,
    /** Cuándo empezó (epoch ms). */
    val creadaEn: Long,
    /** La última pregunta (epoch ms): la lista va de la más reciente a la más vieja por esto. */
    val ultimaEn: Long,
    /** Cuántos mensajes tiene: cada pregunta y su respuesta son dos. */
    val mensajes: Int,
)

/** `GET /api/ai/conversaciones`: la lista, la más reciente primero, y cuántas se guardan como mucho. */
@Serializable
data class ListaDeConversaciones(
    val conversaciones: List<ConversacionEnLaLista> = emptyList(),
    /** [CONVERSACIONES_QUE_SE_GUARDAN], dicho por el server para que la app no prometa otro número. */
    val tope: Int = CONVERSACIONES_QUE_SE_GUARDAN,
    /** Revisión de la vuelta 117 · [TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION], dicho por el server. */
    val turnosPorConversacion: Int = TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION,
)

/**
 * **La retención** (vuelta 117): Movi guarda las últimas 50 conversaciones de cada dueño. Al empezar
 * la 51 (cuando recibe su primera pregunta), se borra la que lleva más tiempo sin usarse, con sus
 * turnos y sus propuestas. Antes se podaban turnos sueltos en silencio, que con la lista a la vista le
 * habría borrado chats sin decirle; ahora la regla se dice en la lista ([textoDeLaRetencion]).
 */
const val CONVERSACIONES_QUE_SE_GUARDAN: Int = 50

/**
 * Revisión de la vuelta 117 · **cuántos turnos (pregunta y respuesta) guarda cada conversación**: los
 * últimos 200, el mismo número que el tope por dueño de antes; los más viejos se van al llegar otro.
 */
const val TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION: Int = 200

/** Desde cuántas guardadas la lista avisa que se acerca al tope. */
const val AVISAR_DESDE_CONVERSACIONES: Int = 45

/** Cuántos caracteres puede medir el título de una conversación (~40, sin cortar una palabra). */
const val LARGO_DEL_TITULO_DE_UNA_CONVERSACION: Int = 40

/** El título de una conversación cuya primera pregunta fue solo una foto. */
const val TITULO_DE_UNA_FOTO: String = "Foto adjunta"

/** El título de una conversación sin texto (no debería pasar: una pregunta siempre trae algo). */
const val TITULO_SIN_TEXTO: String = "Conversación"

/**
 * **El título de una conversación**: la primera pregunta del dueño, en un renglón y recortada a
 * [LARGO_DEL_TITULO_DE_UNA_CONVERSACION] caracteres **sin partir una palabra** («¿Cuánto gasté en
 * restaurantes el mes…»). Sin llamar a la IA: es lo que él escribió. Solo una foto: [TITULO_DE_UNA_FOTO].
 * Una palabra sola más larga que el tope se corta ahí (no hay dónde más).
 */
fun tituloDeLaConversacion(primeraPregunta: String, teniaImagen: Boolean = false): String {
    val limpia = primeraPregunta.trim().replace(Regex("""\s+"""), " ")
    if (limpia.isEmpty()) return if (teniaImagen) TITULO_DE_UNA_FOTO else TITULO_SIN_TEXTO
    if (limpia.length <= LARGO_DEL_TITULO_DE_UNA_CONVERSACION) return limpia
    val corte = limpia.lastIndexOf(' ', startIndex = LARGO_DEL_TITULO_DE_UNA_CONVERSACION)
    val cabeza = if (corte > 0) limpia.substring(0, corte) else limpia.substring(0, LARGO_DEL_TITULO_DE_UNA_CONVERSACION)
    return cabeza.trimEnd(' ', ',', '.', ';', ':', '¿', '¡') + "…"
}

/**
 * **Lo que la lista dice de la retención**, siempre debajo de las conversaciones: la regla a secas, o,
 * desde [AVISAR_DESDE_CONVERSACIONES], cuántas lleva y qué pasa al pasar el tope.
 */
fun textoDeLaRetencion(
    guardadas: Int,
    tope: Int = CONVERSACIONES_QUE_SE_GUARDAN,
    preguntasPorConversacion: Int = TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION,
): String {
    val porConversacion = "De cada una guarda las últimas $preguntasPorConversacion preguntas."
    return if (guardadas >= minOf(AVISAR_DESDE_CONVERSACIONES, tope)) {
        "Tienes $guardadas de $tope conversaciones guardadas. Al empezar otra después de $tope, Movi borra la que llevas más tiempo sin usar. $porConversacion"
    } else {
        "Movi guarda tus últimas $tope conversaciones. $porConversacion"
    }
}
