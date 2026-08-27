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
 * ## La unidad es la ocurrencia del MES CALENDARIO — y esto ya se decidió dos veces
 *
 * [period] es `"YYYY-MM"` y es el mes al que pertenece la ocurrencia: el «arriendo de agosto», el
 * «salario de agosto». Un recurrente cierra el mes que se está viviendo, y solo una vez que su día
 * llegó (ver `GET /api/payments/occurrences`).
 *
 * **El primer intento usó el «vencimiento vigente» —la fecha que devuelve `dueDateFor`— y eso fue
 * un bug con plata adentro.** El razonamiento parecía sólido: es el mismo criterio con el que
 * `reminderKeyFor` deduplica los avisos, así que las dos mitades del sistema hablarían del mismo
 * mes. Lo que ese razonamiento pasaba por alto es que `dueDateFor` **rueda con la ventana de
 * gracia**: para una regla de día 1 o 2, durante la última semana del mes el vencimiento vigente
 * ya es el del mes SIGUIENTE. La app terminaba preguntando «¿ya pagaste el arriendo?» sobre
 * septiembre el 27 de agosto y ofreciendo, como respuesta, el pago de agosto — con el monto
 * exacto, así que ni siquiera saltaba el aviso de monto distinto. Confirmarlo apagaba el arriendo
 * de septiembre: fuera de «Próximos», fuera del barrido, sin correo.
 *
 * Las dos nociones son distintas y cada una sirve para lo suyo. El **aviso** pregunta «¿qué es lo
 * próximo que vence?», y ahí rodar es correcto. La **ocurrencia** pregunta «¿el de este mes ya
 * pasó?», y ahí rodar es exactamente el error. Que coincidan casi siempre es lo que hizo que el
 * primer intento se viera bien.
 *
 * Si alguna vez te dan ganas de «unificar» esto de vuelta contra `dueDateFor`: es este bug.
 *
 * Como consecuencia, «ya ocurrió» es por mes: cerrar agosto no dice nada de septiembre, y al mes
 * siguiente el recurrente vuelve a estar pendiente solo.
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
 * @param period    el **mes en curso**, `"YYYY-MM"` — de lo que habla esta entrada. Nunca es el
 *                  mes siguiente: ver arriba por qué usar el vencimiento vigente fue un bug.
 *                  (`/api/payments/upcoming` sí rueda, y ahí corresponde.)
 * @param dueDate   la fecha en que ese mes vence para esta regla, ISO `"2026-08-25"`, recortada al
 *                  largo del mes.
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
