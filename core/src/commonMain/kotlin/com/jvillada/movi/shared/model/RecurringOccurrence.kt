package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **«Esto ya ocurrió»**: el sello de que el recurrente [ruleId] efectivamente pasó en el periodo
 * [period].
 *
 * ## Por qué existe
 *
 * Hasta hoy `upcomingPayments` decidía el estado de un pago **solo con el calendario**: comparaba
 * el día de la regla con hoy y nada más. Nunca miraba los movimientos, y no había ningún vínculo
 * entre un movimiento y la regla que lo originó. El resultado, con la plata real del dueño: su
 * «Salario» del 25 aparecía «Vencido hace 1 día» mientras el ingreso ya estaba anotado ahí abajo,
 * en la misma pantalla.
 *
 * ## La unidad es el PERIODO DE VENCIMIENTO, no el mes de hoy
 *
 * [period] es `"YYYY-MM"` y es el periodo de la **fecha de vencimiento vigente** de la regla — el
 * mismo que ya calcula `reminderKeyFor` para deduplicar los avisos, y por la misma razón: cerca
 * de fin de mes el vencimiento vigente puede caer en el mes siguiente, y usar el mes de hoy haría
 * que las dos mitades del sistema (el aviso y la ocurrencia) discreparan sobre de qué mes se está
 * hablando. Se sigue el criterio que ya estaba en vez de inventar uno nuevo.
 *
 * Como consecuencia, «ya ocurrió» es por periodo: cerrar agosto no dice nada de septiembre, y al
 * mes siguiente el recurrente vuelve a estar pendiente solo.
 *
 * ## [eventId] puede ser null, y la diferencia importa
 *
 * - **Con movimiento**: el dueño confirmó *cuál* movimiento fue. Es un hecho exacto, no una
 *   adivinanza, y por eso vale más que cualquier heurística: la app propone, él decide.
 * - **Sin movimiento** (`null`): el «Ya lo pagué» / «Ya me llegó» para cerrar el periodo cuando
 *   no hay nada que emparejar (lo pagó en efectivo, todavía no lo anotó, lo anotó en otra cuenta).
 *   Cierra el periodo igual, pero deja constancia de que no está respaldado por un movimiento.
 *
 * Una ocurrencia **con** movimiento solo vale mientras ese movimiento siga vivo y sin anular: si
 * se anula o desaparece, la ocurrencia deja de contar y el pago vuelve a estar pendiente. Es el
 * lado seguro del error — volver a avisar de más molesta, callar una deuda real cuesta plata.
 */
@Serializable
data class RecurringOccurrence(
    val ruleId: String,
    /** `"YYYY-MM"` — el periodo del **vencimiento**, ver el KDoc de arriba. */
    val period: String,
    /** El movimiento que fue esta ocurrencia, o `null` si el dueño la cerró sin emparejar nada. */
    val eventId: String? = null,
    val confirmedAt: Long = 0L,
)

/** Body de `POST /api/recurring-rules/{id}/occurrence`. */
@Serializable
data class MarkOccurrenceRequest(
    val period: String,
    /** `null` = «ya lo pagué / ya me llegó», sin movimiento que emparejar. */
    val eventId: String? = null,
)

/**
 * Lo que `GET /api/payments/occurrences` le cuenta a la pantalla sobre **el periodo que está en
 * juego** de un recurrente: si ya se dio por ocurrido, y si no, qué movimientos podrían serlo.
 *
 * Va en una respuesta aparte de `/api/payments/upcoming` a propósito: ese endpoint lo consume el
 * APK que el dueño ya tiene instalado, y agregarle campos (o peor, un valor nuevo al enum
 * `PaymentStatus`) rompería su deserialización. Un endpoint nuevo lo ignora quien no lo conoce.
 *
 * @param period    el periodo del vencimiento **natural** — el que se calcula sin tener en cuenta
 *                  lo ya ocurrido. Es de lo que habla esta entrada; `/api/payments/upcoming`, en
 *                  cambio, ya rodó al periodo siguiente cuando este está cerrado.
 * @param dueDate   la fecha de ese vencimiento natural, ISO `"2026-08-25"`.
 * @param occurred  `true` = el dueño ya lo dio por ocurrido; entonces [candidates] va vacío.
 * @param eventId   con qué movimiento quedó emparejado, si quedó con alguno.
 * @param candidates lo que la app **propone** cuando todavía no está cerrado, del más probable al
 *                  menos. Nunca se marca solo: siempre confirma el dueño (ver
 *                  `occurrenceCandidatesFor` en el server para por qué el monto ordena y no
 *                  filtra).
 */
@Serializable
data class OccurrenceState(
    val ruleId: String,
    val period: String,
    val dueDate: String,
    val occurred: Boolean,
    val eventId: String? = null,
    val confirmedAt: Long = 0L,
    val candidates: List<FinancialEvent> = emptyList(),
)
