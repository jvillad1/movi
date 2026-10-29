package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MOTIVO_DEL_REPARTO_DESHECHO
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.shared.model.RepartoDeUnMovimiento
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/*
 * # Vuelta 67 · restaurar un anulado y deshacer una compra dividida
 *
 * Ver `RestaurarYDeshacer.kt` en `:core`. **Sin reglas de plata nuevas**: anular es una fila en
 * `void_events` y toda suma la excluye (memoria `movi-leer-saldos-sin-anulados`); restaurar es borrar
 * esa fila. Deshacer un reparto es anular las partes (el mecanismo de siempre) y restaurar el original.
 * Todo por usuario: lo de otro contesta 404, como si no existiera.
 */

/** Lo que pasó al restaurar o deshacer. */
internal sealed interface Restaurado {
    data class Hecho(val original: FinancialEvent) : Restaurado
    data class Rechazado(val estado: HttpStatusCode, val motivo: String) : Restaurado
}

private fun Transaction.anuladosDe(uid: String, ids: Collection<String>): Set<String> =
    if (ids.isEmpty()) emptySet()
    else VoidEvents.selectAll()
        .where { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList ids) }
        .map { it[VoidEvents.originalEventId] }.toSet()

private fun Transaction.movimientoDe(uid: String, id: String): FinancialEvent? =
    Events.selectAll().where { (Events.id eq id) and (Events.userId eq uid) }.forUpdate().firstOrNull()?.toFinancialEvent()

/** El movimiento y su otra pata (si es un traspaso o una cuota), bloqueados. */
private fun Transaction.conSuPar(uid: String, evento: FinancialEvent): List<FinancialEvent> =
    evento.transferId?.let { tr ->
        Events.selectAll().where { (Events.userId eq uid) and (Events.transferId eq tr) }.forUpdate().map { it.toFinancialEvent() }
    } ?: listOf(evento)

/** Las patas de las partes de la compra [originalId], bloqueadas. */
private fun Transaction.partesDe(uid: String, originalId: String): List<FinancialEvent> =
    Events.selectAll().where { (Events.userId eq uid) and (Events.repartoDe eq originalId) }.forUpdate().map { it.toFinancialEvent() }

/**
 * **Deshacer la compra dividida [originalId]**: anula las partes que siguen vivas (con
 * [MOTIVO_DEL_REPARTO_DESHECHO]), suelta los sellos de recurrentes que apuntaban a ellas, restaura el
 * original (y su otra pata) y le devuelve el aviso que se había pasado a la primera parte. 409 si el
 * original ya está vivo (ya se deshizo); 404 si no es una compra dividida del dueño.
 */
internal fun Transaction.deshacerElReparto(uid: String, originalId: String, ahora: Long): Restaurado {
    val original = movimientoDe(uid, originalId) ?: return Restaurado.Rechazado(HttpStatusCode.NotFound, "Movimiento no encontrado")
    val delOriginal = conSuPar(uid, original)
    val partes = partesDe(uid, originalId)
    if (partes.isEmpty()) return Restaurado.Rechazado(HttpStatusCode.NotFound, "Ese movimiento no está dividido.")
    if (anuladosDe(uid, delOriginal.map { it.id }).isEmpty()) {
        return Restaurado.Rechazado(HttpStatusCode.Conflict, "Esa compra ya es un solo movimiento.")
    }
    val idsDeLasPartes = partes.map { it.id }
    val yaAnuladas = anuladosDe(uid, idsDeLasPartes)
    idsDeLasPartes.filterNot { it in yaAnuladas }.forEach { id ->
        VoidEvents.insert {
            it[VoidEvents.id] = "void_${java.util.UUID.randomUUID()}"
            it[VoidEvents.userId] = uid
            it[VoidEvents.originalEventId] = id
            it[VoidEvents.reason] = MOTIVO_DEL_REPARTO_DESHECHO
            it[VoidEvents.timestamp] = ahora
        }
    }
    RecurringOccurrences.deleteWhere { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId inList idsDeLasPartes) }
    val idsDelOriginal = delOriginal.map { it.id }
    VoidEvents.deleteWhere { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList idsDelOriginal) }
    // El aviso que había anotado el original vuelve a él (al dividirlo se pasó a la primera parte).
    SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.eventoId inList idsDeLasPartes) }) {
        it[SmsMessages.eventoId] = original.id
        it[SmsMessages.partesEventos] = null
    }
    return Restaurado.Hecho(original.withCashFlowFlag(accountTypesFor(uid)))
}

/**
 * **Restaurar el anulado [id]**: borra su marca y la de su otra pata. Si es el original de una compra
 * dividida con partes vivas, deshace el reparto ([deshacerElReparto]); si es una parte de una compra
 * que ya volvió a ser un solo movimiento, 409 (restaurarla contaría la plata dos veces). 404 si no es
 * del dueño o no está anulado.
 */
internal fun Transaction.restaurarElMovimiento(uid: String, id: String, ahora: Long): Restaurado {
    val evento = movimientoDe(uid, id) ?: return Restaurado.Rechazado(HttpStatusCode.NotFound, "Movimiento no encontrado")
    val par = conSuPar(uid, evento)
    val ids = par.map { it.id }
    if (anuladosDe(uid, ids).isEmpty()) return Restaurado.Rechazado(HttpStatusCode.NotFound, "Ese movimiento no está anulado.")
    // El original de una compra dividida: restaurarlo es deshacer el reparto.
    val conPartes = ids.firstOrNull { originalId ->
        val partes = Events.selectAll().where { (Events.userId eq uid) and (Events.repartoDe eq originalId) }.map { it[Events.id] }
        partes.isNotEmpty() && (partes - anuladosDe(uid, partes)).isNotEmpty()
    }
    if (conPartes != null) return deshacerElReparto(uid, conPartes, ahora)
    // Una parte de una compra que ya volvió a ser una sola.
    evento.repartoDe?.let { originalId ->
        if (anuladosDe(uid, listOf(originalId)).isEmpty() && Events.selectAll().where { (Events.userId eq uid) and (Events.id eq originalId) }.any()) {
            return Restaurado.Rechazado(HttpStatusCode.Conflict, "Esta parte es de una compra que volvió a ser un solo movimiento.")
        }
    }
    VoidEvents.deleteWhere { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList ids) }
    return Restaurado.Hecho(evento.withCashFlowFlag(accountTypesFor(uid)))
}

/** Los anulados del dueño, el último anulado primero. Las partes de una compra que volvió a ser una no van. */
internal fun Transaction.anuladosDelUsuario(uid: String): List<MovimientoAnulado> {
    val marcas = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.associate { it[VoidEvents.originalEventId] to it }
    if (marcas.isEmpty()) return emptyList()
    val tipos = accountTypesFor(uid)
    val eventos = Events.selectAll().where { (Events.userId eq uid) and (Events.id inList marcas.keys) }.map { it.toFinancialEvent() }
    val vivos = { id: String -> id !in marcas }
    return eventos
        .filterNot { e -> e.repartoDe != null && vivos(e.repartoDe!!) }
        .map { e ->
            val marca = marcas.getValue(e.id)
            MovimientoAnulado(
                evento = e.withCashFlowFlag(tipos),
                motivo = marca[VoidEvents.reason]?.trim()?.ifEmpty { null },
                anuladoEn = marca[VoidEvents.timestamp],
            )
        }
        .sortedWith(compareByDescending<MovimientoAnulado> { it.anuladoEn }.thenByDescending { it.evento.timestamp })
}

/** La compra dividida de la que [id] es parte (o que [id] es), con todas sus partes; `null` si no hay. */
internal fun Transaction.repartoDelMovimiento(uid: String, id: String): RepartoDeUnMovimiento? {
    val evento = Events.selectAll().where { (Events.id eq id) and (Events.userId eq uid) }.firstOrNull()?.toFinancialEvent() ?: return null
    val originalId = evento.repartoDe ?: id
    val original = if (originalId == id) evento
    else Events.selectAll().where { (Events.id eq originalId) and (Events.userId eq uid) }.firstOrNull()?.toFinancialEvent() ?: return null
    val partes = Events.selectAll().where { (Events.userId eq uid) and (Events.repartoDe eq originalId) }.map { it.toFinancialEvent() }
    if (partes.isEmpty()) return null
    val tipos = accountTypesFor(uid)
    return RepartoDeUnMovimiento(original.withCashFlowFlag(tipos), partes.sortedBy { it.id }.map { it.withCashFlowFlag(tipos) })
}

fun Route.restaurarYDeshacerRoutes() {
    /** `GET /api/events/anulados`: [MovimientoAnulado], el último anulado primero. */
    get("/api/events/anulados") {
        val uid = call.userId()
        call.respond(dbQuery { anuladosDelUsuario(uid) })
    }

    /** `DELETE /api/events/{id}/void`: restaurar (ver [restaurarElMovimiento]). 200 con el movimiento. */
    delete("/api/events/{id}/void") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        when (val r = dbQuery { restaurarElMovimiento(uid, id, System.currentTimeMillis()) }) {
            is Restaurado.Hecho -> call.respond(r.original)
            is Restaurado.Rechazado -> call.respond(r.estado, r.motivo)
        }
    }

    /** `GET /api/events/{id}/reparto`: [RepartoDeUnMovimiento], o 404. */
    get("/api/events/{id}/reparto") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val reparto = dbQuery { repartoDelMovimiento(uid, id) } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(reparto)
    }

    /** `POST /api/events/{id}/deshacer-reparto` ([id] una parte o el original): 200 con el original. */
    post("/api/events/{id}/deshacer-reparto") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val r = dbQuery {
            val evento = Events.selectAll().where { (Events.id eq id) and (Events.userId eq uid) }.firstOrNull()?.toFinancialEvent()
                ?: return@dbQuery Restaurado.Rechazado(HttpStatusCode.NotFound, "Movimiento no encontrado")
            deshacerElReparto(uid, evento.repartoDe ?: id, System.currentTimeMillis())
        }
        when (r) {
            is Restaurado.Hecho -> call.respond(r.original)
            is Restaurado.Rechazado -> call.respond(r.estado, r.motivo)
        }
    }
}
