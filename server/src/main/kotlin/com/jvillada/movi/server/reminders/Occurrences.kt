package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.routes.estadosDeLasOcurrenciasReales
import com.jvillada.movi.server.routes.ocurrenciasReales
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringOccurrence
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.LocalDate

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
fun Transaction.loadOccurredBy(uid: String): Map<String, Set<String>> = loadOccurredBy(uid, loadOccurrenceRows(uid))

/** Lo mismo que [loadOccurredBy], sobre filas que quien llama ya leyó (así no se leen dos veces). */
fun Transaction.loadOccurredBy(uid: String, rows: List<RecurringOccurrence>): Map<String, Set<String>> {
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
 * Los movimientos vivos (no anulados) de [uid] en `[desde, hastaExclusivo)`, en epoch-ms.
 *
 * Existe para no traerse TODOS los movimientos del usuario en cada carga de la pantalla de
 * Recurrentes: los candidatos solo pueden estar en una franja de unas seis semanas alrededor del
 * mes en curso, y el índice `idx_events_user_ts` (user_id, timestamp) es exactamente el que hace
 * falta para acotarla. Con un par de años de movimientos anotados la diferencia deja de ser
 * teórica.
 *
 * Aplica `withCashFlowFlag` igual que [com.jvillada.movi.server.balance.loadNonVoidedEventsIn]:
 * el campo es derivado y no debería depender de por cuál consulta entró el evento.
 */
fun Transaction.loadEventsBetween(uid: String, desde: Long, hastaExclusivo: Long): List<FinancialEvent> {
    val anulados = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    val tipos = accountTypesFor(uid)
    return Events.selectAll()
        .where {
            (Events.userId eq uid) and
                (Events.timestamp greaterEq desde) and
                (Events.timestamp less hastaExclusivo)
        }
        .filterNot { it[Events.id] in anulados }
        .map { it.toFinancialEvent().withCashFlowFlag(tipos) }
}

/**
 * Los «no fue este» de [uid], como pares `(ruleId, eventId)`.
 *
 * **El par, no el movimiento solo**, y es la parte que importa: con «Agua», «Gas» e «Internet»
 * todas en «Servicios», el pago del gas se propone en las tres. Rechazarlo en la regla del agua no
 * puede quitárselo a la del gas (ver [com.jvillada.movi.server.db.OccurrenceRejections] y
 * `claveDescartada` en la pantalla, que dice lo mismo del lado del cliente).
 *
 * No se filtra por movimientos vivos a propósito: un rechazo sobre un movimiento que ya no existe
 * no le hace daño a nadie —no hay nada que excluir— y una consulta más por cada carga de la
 * pantalla, para limpiar filas que no se ven, es un costo sin contraparte.
 */
fun Transaction.loadRejectedPairs(uid: String): Set<Pair<String, String>> =
    OccurrenceRejections.selectAll()
        .where { OccurrenceRejections.userId eq uid }
        .map { it[OccurrenceRejections.ruleId] to it[OccurrenceRejections.eventId] }
        .toSet()

/**
 * **Lo que Movi emparejó solo, con la forma de un sello**: la misma respuesta del checklist
 * ([estadosDeLasOcurrenciasReales]), no una segunda pasada del emparejador.
 *
 * El emparejamiento automático se deriva en cada lectura y no escribe fila en
 * `recurring_occurrences`, así que todo lo que armaba sus períodos ocurridos solo con los sellos
 * —«Próximos», el barrido de recordatorios, la tarjeta «Disponible»— no lo veía y contradecía al
 * checklist. Pasarlo por acá hace que un emparejamiento se comporte exactamente como un sello a
 * mano con su movimiento, en todos los que lo leen.
 *
 * Solo lo concluyente: con dos candidatos el checklist pregunta (`occurred = false`) y acá no sale
 * nada. Lo ya sellado tampoco: está en [loadOccurrenceRows].
 *
 * **Y la ocurrencia anterior al período, que el checklist ya dejó atrás.** El checklist solo deriva
 * la ocurrencia por la que pregunta: pasada la gracia pasa a la siguiente, y el arriendo del 23
 * pagado el 26 —ya en el período que arrancó el 25— quedaba sin emparejar. Mientras la ventana de
 * esa ocurrencia pise el período en curso, su emparejamiento sale acá también
 * (`OcurrenciasReales.anterioresEmparejadas`), en la misma lectura y con las mismas guardas: solo
 * con un único concluyente, sin lo sellado ni lo rechazado, y con la misma reserva de movimientos.
 * Qué hace y qué no:
 *
 * - **No saca plata del gasto variable.** Esa ocurrencia no está en los fijos de este período, así
 *   que su pago sigue contando una vez, como variable (ver `PagosDelChecklist.kt`).
 * - **Reserva el movimiento**: en el Disponible no puede pasar por el pago de otro ítem pendiente
 *   —la administración, en la misma categoría—, que lo sacaría del variable mientras el fijo de
 *   ese ítem sigue esperando: el Disponible se vería mejor de lo que es.
 * - **A «Próximos» y al barrido no les cambia nada**: pasada la gracia, `dueDateFor` ya rodó ese
 *   vencimiento, pagado o no, y un período ocurrido de más atrás solo hace rodar hacia adelante.
 *
 * Por eso no tiene dónde mostrarse ni un «no fue este»: un emparejamiento equivocado acá no puede
 * callar ningún aviso (a lo sumo sería el de una ocurrencia que ya pasó, y esa ya rodó) ni mover
 * plata; lo único que hace es dejar ese movimiento como gasto variable en vez de ofrecerlo como
 * pago de otro ítem, que es el lado conservador.
 */
internal fun Transaction.emparejadasComoSellos(
    uid: String,
    hoy: LocalDate,
    periodo: PeriodSettings,
): List<RecurringOccurrence> {
    val lectura = ocurrenciasReales(uid, hoy, periodo)
    return lectura.estados
        .filter { it.occurred && it.automatica && it.eventId != null }
        .map { RecurringOccurrence(ruleId = it.ruleId, period = it.period, eventId = it.eventId, confirmedAt = it.confirmedAt) } +
        lectura.anterioresEmparejadas
}

/** regla → períodos, el mapa `occurredPeriods` con el que [dueDateFor] rueda un vencimiento. */
internal fun List<RecurringOccurrence>.periodosPorRegla(): Map<String, Set<String>> =
    groupBy({ it.ruleId }, { it.period }).mapValues { (_, periodos) -> periodos.toSet() }
