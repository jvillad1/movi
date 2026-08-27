package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.shared.model.RecurringOccurrence
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

/** Las filas crudas de `recurring_occurrences` de un usuario, sin filtrar por nada. */
fun Transaction.loadOccurrenceRows(uid: String): List<RecurringOccurrence> =
    RecurringOccurrences.selectAll()
        .where { RecurringOccurrences.userId eq uid }
        .map {
            RecurringOccurrence(
                ruleId = it[RecurringOccurrences.ruleId],
                period = it[RecurringOccurrences.period],
                eventId = it[RecurringOccurrences.eventId],
                confirmedAt = it[RecurringOccurrences.confirmedAt],
            )
        }

/**
 * Las ocurrencias que **de verdad valen**, agrupadas como las quiere [dueDateFor]: id de regla →
 * periodos cerrados.
 *
 * ## Una ocurrencia con movimiento vale solo mientras ese movimiento viva
 *
 * Si el movimiento emparejado se **anula** (`void_events`) o **desaparece** —hoy pasa al borrar la
 * cuenta a la que pertenecía, que borra sus eventos—, la ocurrencia deja de contar acá mismo y el
 * recurrente vuelve a estar pendiente. Se decidió verificarlo en la LECTURA y no solo con un
 * gancho en cada camino de borrado: los caminos por los que un evento puede morir son varios y
 * alguno se va a agregar mañana sin acordarse de esta tabla, mientras que este chequeo cubre a
 * todos por construcción. Es también el lado seguro del error: volver a avisar de más molesta un
 * toque, callar una deuda real cuesta plata.
 *
 * Una ocurrencia **sin** movimiento (el «ya lo pagué») no depende de nada y siempre vale.
 *
 * Cuesta como mucho dos consultas chiquitas: la tabla tiene a lo sumo una fila por recurrente y
 * por mes, y solo se miran los ids que ella menciona.
 */
fun Transaction.loadOccurredBy(uid: String): Map<String, Set<String>> {
    val rows = loadOccurrenceRows(uid)
    if (rows.isEmpty()) return emptyMap()
    val ids = rows.mapNotNull { it.eventId }.toSet()
    val vivos: Set<String> = if (ids.isEmpty()) {
        emptySet()
    } else {
        val existen = Events.selectAll()
            .where { (Events.userId eq uid) and (Events.id inList ids) }
            .map { it[Events.id] }
            .toSet()
        val anulados = VoidEvents.selectAll()
            .where { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList ids) }
            .map { it[VoidEvents.originalEventId] }
            .toSet()
        existen - anulados
    }
    return rows
        .filter { it.eventId == null || it.eventId in vivos }
        .groupBy { it.ruleId }
        .mapValues { (_, v) -> v.map { it.period }.toSet() }
}

/**
 * Los movimientos que ya están sellados como ocurrencia de algún recurrente — no vuelven a
 * proponerse (cuarta puerta de [occurrenceCandidatesFor]).
 *
 * Se incluyen también los de filas cuyo evento murió: proponer un movimiento anulado no tendría
 * sentido de todas formas, y quien llama solo cruza esto contra movimientos vivos.
 */
fun Transaction.loadUsedOccurrenceEventIds(uid: String): Set<String> =
    loadOccurrenceRows(uid).mapNotNull { it.eventId }.toSet()
