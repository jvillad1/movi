package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AvisosDelMismoPago
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY_NOT_MANUAL
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_LEG_NOT_STANDALONE
import com.jvillada.movi.shared.model.esCategoriaDelDesembolso
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.rechazoDeLosTextos
import com.jvillada.movi.shared.model.rechazoDelMonto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * # Un pago, una tarjeta: revisar, confirmar e ignorar los avisos de un mismo pago de una vez
 *
 * Los avisos se juntan en el server ([gruposDelMismoPago]); acá están las tres cosas que la app
 * hace con un pago entero. Separarlos («No son el mismo pago») vive en `SmsRoutes`.
 */
fun Route.elMismoPagoRoutes() {
    /**
     * **Los avisos del pago [grupoId]**, con el aviso del que sale la propuesta primero (ver
     * [propuestaDelGrupo]). 404 si ese pago ya no existe —se confirmó, se separó— o no es del usuario.
     */
    get("/api/sms/grupo/{grupoId}") {
        val uid = call.userId()
        val grupoId = call.parameters["grupoId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val miembros = dbQuery { bandejaConLosPagos(uid, ahora = System.currentTimeMillis()) }
            .filter { it.grupoId == grupoId }
        if (miembros.isEmpty()) return@get call.respond(HttpStatusCode.NotFound)
        val propuesta = propuestaDelGrupo(miembros)
        val enOrden = miembros.sortedWith(compareBy({ momentoDelSms(it.time, Long.MAX_VALUE) }, { it.id }))
        call.respond(
            GrupoDeAvisos(
                grupoId = grupoId,
                miembros = listOf(propuesta) + enOrden.filter { it.id != propuesta.id },
                propuestaDe = propuesta.id,
                yaAnotadoCon = enOrden.firstOrNull { it.state == SMS_STATE_CONFIRMED }?.id,
            ),
        )
    }

    /**
     * **Confirmar el pago: un solo movimiento para todos sus avisos.** Ver [ConfirmarElMismoPago]
     * para las tres formas. Todo pasa en **una transacción** —el movimiento y las marcas de los
     * avisos, o nada— con las filas de los avisos bloqueadas (`FOR UPDATE`): si dos toques llegan a
     * la vez, el segundo espera al primero, ve los avisos ya confirmados y no crea nada.
     *
     * - 200 con [MismoPagoConfirmado]: `creado = true` solo si este pedido creó el movimiento.
     * - 400/422 si el movimiento no pasa las mismas reglas que `POST /api/events`.
     * - 404 si algún aviso (o la cuenta, o el movimiento elegido) no es del usuario.
     * - 409 si no hay movimiento que crear ni ninguno anotado con que cerrar.
     */
    post("/api/sms/grupo/{grupoId}/confirmar") {
        val uid = call.userId()
        val grupoId = call.parameters["grupoId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val pedido = runCatching { call.receive<ConfirmarElMismoPago>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val ids = pedido.miembros.distinct()
        if (ids.isEmpty() || grupoId !in ids) return@post call.respond(HttpStatusCode.BadRequest)
        val resultado = dbQuery { confirmarElMismoPago(uid, ids, pedido, ahora = System.currentTimeMillis()) }
        when (resultado) {
            is Confirmacion.Hecha -> call.respond(resultado.respuesta)
            is Confirmacion.Rechazada -> call.respond(resultado.estado, resultado.motivo)
        }
    }

    /** **Ignorar el pago**: todos sus avisos pendientes quedan ignorados. 404 si ninguno es del usuario. */
    post("/api/sms/grupo/{grupoId}/ignorar") {
        val uid = call.userId()
        val cuerpo = runCatching { call.receive<AvisosDelMismoPago>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val ids = cuerpo.miembros.distinct()
        if (ids.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest)
        val existe = dbQuery {
            val suyos = SmsMessages.selectAll()
                .where { (SmsMessages.userId eq uid) and (SmsMessages.id inList ids) }
                .count()
            if (suyos > 0) {
                SmsMessages.update({
                    (SmsMessages.userId eq uid) and (SmsMessages.id inList ids) and (SmsMessages.state eq SMS_STATE_PENDING)
                }) { it[state] = SMS_STATE_IGNORED }
            }
            suyos > 0
        }
        call.respond(if (existe) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
}

internal sealed interface Confirmacion {
    data class Hecha(val respuesta: MismoPagoConfirmado) : Confirmacion
    data class Rechazada(val estado: HttpStatusCode, val motivo: String) : Confirmacion
}

/**
 * El corazón de `…/confirmar`, dentro de la transacción del llamador. Primero se valida todo y
 * recién después se escribe: un rechazo no deja nada a medias.
 */
internal fun confirmarElMismoPago(
    uid: String,
    ids: List<String>,
    pedido: ConfirmarElMismoPago,
    ahora: Long,
): Confirmacion {
    val filas = SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and (SmsMessages.id inList ids) }
        .forUpdate()
        .toList()
    if (filas.size != ids.size) return Confirmacion.Rechazada(HttpStatusCode.NotFound, "Alguno de esos avisos no existe.")
    val pendientes = filas.filter { it[SmsMessages.state] == SMS_STATE_PENDING }.map { it[SmsMessages.id] }
    val confirmados = filas.filter { it[SmsMessages.state] == SMS_STATE_CONFIRMED }

    fun cerrar(eventoId: String?, creado: Boolean): Confirmacion {
        if (pendientes.isNotEmpty()) {
            SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.id inList pendientes) }) {
                it[state] = SMS_STATE_CONFIRMED
                if (eventoId != null) it[SmsMessages.eventoId] = eventoId
            }
        }
        return Confirmacion.Hecha(MismoPagoConfirmado(eventoId = eventoId, creado = creado, cerrados = pendientes))
    }

    // **Ya anotado** (otro aviso del pago se confirmó antes, o este mismo pedido llegó dos veces):
    // nunca se crea otro movimiento. Se cierran los que quedaban, con el movimiento que ya tiene.
    if (confirmados.isNotEmpty()) {
        val yaTiene = confirmados.firstNotNullOfOrNull { it[SmsMessages.eventoId] } ?: pedido.eventoExistenteId
        return cerrar(yaTiene, creado = false)
    }

    // «Es este»: el movimiento ya existía; se enlaza sin crear nada.
    pedido.eventoExistenteId?.let { existente ->
        val esSuyo = Events.selectAll().where { (Events.id eq existente) and (Events.userId eq uid) }.count() > 0
        if (!esSuyo) return Confirmacion.Rechazada(HttpStatusCode.NotFound, "Ese movimiento no existe.")
        return cerrar(existente, creado = false)
    }

    val pedidoDeEvento = pedido.evento
        ?: return Confirmacion.Rechazada(HttpStatusCode.Conflict, "Este pago todavía no está anotado.")
    val evento = when (val validado = movimientoDelAviso(uid, pedidoDeEvento, ahora)) {
        is Validado.Bien -> validado.evento
        is Validado.Mal -> return Confirmacion.Rechazada(validado.estado, validado.motivo)
    }
    val existente = Events.selectAll().where { Events.id eq evento.id }.firstOrNull()
    if (existente != null && existente[Events.userId] != uid) {
        return Confirmacion.Rechazada(HttpStatusCode.Conflict, "Ese id ya existe")
    }
    // Un reintento con el mismo id (la respuesta se perdió y los avisos… ya estarían confirmados y
    // habría salido arriba; esto cubre el caso raro de un movimiento creado sin cerrar los avisos).
    if (existente == null) insertarMovimiento(uid, evento)
    return cerrar(evento.id, creado = existente == null)
}

private sealed interface Validado {
    data class Bien(val evento: FinancialEvent) : Validado
    data class Mal(val estado: HttpStatusCode, val motivo: String) : Validado
}

/**
 * **El movimiento de un aviso, con las reglas de `POST /api/events`** que aplican a lo que viene de
 * un mensaje del banco: ni medio traspaso ni un desembolso suelto, monto y textos con forma, una
 * fecha de este siglo y una cuenta del usuario. Sale como `SMS` y confirmado: lo revisó el dueño.
 */
private fun movimientoDelAviso(uid: String, pedido: FinancialEvent, ahora: Long): Validado {
    if (pedido.transferId != null || pedido.category == TRANSFER_CATEGORY) {
        return Validado.Mal(HttpStatusCode.UnprocessableEntity, TRANSFER_LEG_NOT_STANDALONE)
    }
    if (esCategoriaDelDesembolso(pedido.category)) return Validado.Mal(HttpStatusCode.BadRequest, DESEMBOLSO_CATEGORY_NOT_MANUAL)
    rechazoDelMonto(pedido.amount)?.let { return Validado.Mal(HttpStatusCode.BadRequest, it) }
    rechazoDeLosTextos(pedido.category, pedido.description)?.let { return Validado.Mal(HttpStatusCode.BadRequest, it) }
    val evento = pedido.copy(
        id = pedido.id.ifBlank { "ev_${java.util.UUID.randomUUID()}" },
        timestamp = if (pedido.timestamp == 0L) ahora else pedido.timestamp,
        createdAt = pedido.createdAt?.takeIf { epochMillisToAppDate(it).year in 2000..2100 } ?: ahora,
        source = EventSource.SMS,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
    if (epochMillisToAppDate(evento.timestamp).year !in 2000..2100) {
        return Validado.Mal(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
    }
    val cuentaEsSuya = Accounts.selectAll()
        .where { (Accounts.id eq evento.accountId) and (Accounts.userId eq uid) }
        .count() > 0
    if (!cuentaEsSuya) return Validado.Mal(HttpStatusCode.NotFound, "Account not found")
    return Validado.Bien(evento)
}

/** La misma fila que escribe `POST /api/events`. */
private fun insertarMovimiento(uid: String, event: FinancialEvent) {
    Events.insert {
        it[id] = event.id
        it[userId] = uid
        it[accountId] = event.accountId
        it[type] = event.type.name
        it[amount] = event.amount
        it[Events.currency] = event.currency
        it[category] = event.category
        it[description] = event.description
        it[merchant] = event.merchant
        it[timestamp] = event.timestamp
        it[eventSource] = event.source.name
        it[rawPayload] = event.rawPayload
        it[reconciliationStatus] = event.reconciliationStatus.name
        it[syncedAt] = event.syncedAt
        it[createdAt] = event.createdAt
        it[Events.noSeRepite] = event.noSeRepite
        it[Events.lastEditedAt] = event.lastEditedAt
    }
}
