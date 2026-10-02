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
 * `GET /api/ai/conversacion`: los últimos mensajes de la conversación en curso, del más viejo al
 * más nuevo, listos para pintar. Vacía después de «Nueva conversación».
 */
@Serializable
data class ConversacionDelAsistente(
    val mensajes: List<ChatMessage> = emptyList(),
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
)

/** `POST /api/ai/propuestas/{id}/estado`: lo que el dueño decidió. */
@Serializable
data class ResolverPropuestaRequest(val estado: EstadoDePropuesta)
