package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.dismissedCardPaymentEventIds
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.jvillada.movi.server.time.epochMillisToAppDateString

fun Route.eventRoutes() {
    route("/api/events") {

        post {
            val body = call.receive<FinancialEvent>()
            val uid = call.userId()
            val now = System.currentTimeMillis()

            // Un traspaso son DOS patas que nacen juntas o no nacen (ver TransferRoutes.kt).
            // Aceptar acá un evento suelto con transferId —o con la categoría reservada— sería
            // dejar entrar medio traspaso: plata saliendo de una cuenta sin la pata que la
            // compensa del otro lado, y encima invisible para el mes por la regla de isCashFlow.
            if (body.transferId != null || body.category == TRANSFER_CATEGORY) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Un traspaso se crea completo con POST /api/transfers, no como un movimiento suelto",
                )
            }
            val event = body.copy(
                id        = body.id.ifBlank { "ev_${java.util.UUID.randomUUID()}" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
                // F12, capa 2: "por confirmar" es para lo que entra solo (SMS, OCR, extracto) —
                // no para lo que el usuario anotó a mano, que ya está confirmado por definición.
                // Esto es la red de seguridad del server, no solo de QuickAdd: cualquier cliente
                // (viejo, o uno que no aplique el fix del lado UI) que mande MANUAL+UNCONFIRMED
                // queda corregido acá, para que no le pase lo mismo por otra puerta.
                reconciliationStatus = if (body.source == EventSource.MANUAL && body.reconciliationStatus == ReconciliationStatus.UNCONFIRMED)
                    ReconciliationStatus.RECONCILED
                else
                    body.reconciliationStatus,
            )

            val accountExists = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .count() > 0
            }
            if (!accountExists) return@post call.respond(HttpStatusCode.NotFound, "Account not found")

            dbQuery {
                Events.insert {
                    it[id]                   = event.id
                    it[userId]               = uid
                    it[accountId]            = event.accountId
                    it[type]                 = event.type.name
                    it[amount]               = event.amount
                    it[Events.currency]      = event.currency
                    it[category]             = event.category
                    it[description]          = event.description
                    it[merchant]             = event.merchant
                    it[timestamp]            = event.timestamp
                    it[eventSource]          = event.source.name
                    it[rawPayload]           = event.rawPayload
                    it[reconciliationStatus] = event.reconciliationStatus.name
                    it[syncedAt]             = event.syncedAt
                }
            }
            // El eco lleva la bandera derivada, no la que mandó el cliente: countsAsCashFlow
            // sale del tipo de la cuenta y el cliente no tiene voz ahí. Sin esto, el POST
            // devolvía el default `true` para un evento de una cuenta de deuda y contradecía
            // a los GET, que sí la derivan.
            val accountType = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.let { runCatching { AccountType.valueOf(it[Accounts.type]) }.getOrNull() }
            }
            call.respond(
                HttpStatusCode.Created,
                accountType?.let { event.copy(countsAsCashFlow = isCashFlow(it, event.type, event.category)) } ?: event,
            )
        }

        get {
            val uid = call.userId()
            val accountId = call.request.queryParameters["accountId"]
            val result = loadNonVoidedEvents(uid, accountId).sortedByDescending { it.timestamp }
            call.respond(result)
        }

        get("/by-day") {
            val uid = call.userId()
            val result = loadNonVoidedEvents(uid)
                .groupBy { epochMillisToAppDateString(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        // El total del día es flujo de caja, igual que el del mes: los
                        // movimientos de cuentas de deuda quedan fuera (ver countsAsCashFlow).
                        // El renglón del ajuste SÍ se sigue listando —es un movimiento real de
                        // la cuenta— pero un ajuste de $60.000.000 no puede encabezar el día
                        // como "+$60.000.000", que es el mismo número engañoso del Dashboard.
                        date  = date,
                        total = items.filter { it.currency == "COP" && it.countsAsCashFlow }.sumOf { e ->
                            if (e.type == TransactionType.INCOME) e.amount else -e.amount
                        },
                        items = items,
                    )
                }
                .sortedByDescending { it.date }
            call.respond(result)
        }

        // Candidatos a pago de tarjeta ya cargados con otra categoría (Task 2 de
        // SP-ajustar-saldo). Solo LEE y PROPONE — nada se recategoriza acá; el dueño confirma
        // en un paso posterior. Por eso alcanza con reusar loadNonVoidedEventsIn +
        // accountTypesFor: los mismos que ya deciden qué es flujo de caja.
        get("/card-payment-candidates") {
            val uid = call.userId()
            val assetTypes = setOf(
                AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT,
            )
            val candidates = dbQuery {
                val accountTypes = accountTypesFor(uid)
                // Lo que descartó "No es" (ver POST /{id}/not-card-payment abajo) no se vuelve a
                // proponer — es la pieza que hace que el botón signifique algo.
                val dismissed = dismissedCardPaymentEventIds(uid)
                loadNonVoidedEventsIn(uid).filter { event ->
                    event.id !in dismissed &&
                        event.type == TransactionType.EXPENSE &&
                        accountTypes[event.accountId] in assetTypes &&
                        looksLikeCardPayment(event.description, event.category)
                }
            }
            call.respond(candidates)
        }

        // Recategorizar un movimiento (Task 3 de SP-ajustar-saldo). Es la pieza que le falta al
        // GET /card-payment-candidates de arriba: propone, esta confirma. Aislado por usuario
        // (404, no 403, si el evento es de otro) y countsAsCashFlow siempre se recalcula acá —
        // nunca se guarda ni se toma del cliente.
        put("/{id}/category") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val category = call.receive<UpdateEventCategoryRequest>().category.trim()
            if (category.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, "La categoría no puede estar vacía")
            }
            if (category.length > 60) {
                return@put call.respond(HttpStatusCode.BadRequest, "La categoría no puede superar 60 caracteres")
            }
            // Nadie entra a la categoría reservada por esta puerta: un evento recategorizado a
            // "Traspaso" sería medio traspaso — se dejaría de contar en el mes (regla de
            // isCashFlow) sin ninguna pata del otro lado que explique adónde fue la plata.
            if (category == TRANSFER_CATEGORY) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_CATEGORY_RESERVED)
            }
            // Y nadie sale tampoco: sacar una pata de la categoría reservada la devolvería al
            // flujo de caja del mes —el gasto fantasma que esta feature vino a matar— y dejaría
            // a su hermana adentro, contando la mitad de un movimiento que nunca ocurrió.
            val esPataDeTraspaso = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()
                    ?.let { it[Events.transferId] != null || it[Events.category] == TRANSFER_CATEGORY } == true
            }
            if (esPataDeTraspaso) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_RECATEGORIZE_BLOCKED)
            }

            val updated: FinancialEvent? = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                // Un evento anulado no está disponible para recategorizar: ningún GET lo vuelve a
                // mostrar (ver loadNonVoidedEventsIn), así que el countsAsCashFlow que devolviera
                // acá no se vería en ninguna pantalla — tratarlo igual que si no existiera.
                val isVoided = event != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (event != null && !isVoided) {
                    Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                        it[Events.category] = category
                    }
                }
                event?.takeIf { !isVoided }?.copy(category = category)?.withCashFlowFlag(accountTypesFor(uid))
            }
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        // "No es un pago de tarjeta": descarta el candidato de GET /card-payment-candidates de
        // forma persistente, SIN tocar su categoría — el gasto sigue contando como flujo de caja
        // del mes, que es justo lo que hay que preservar en un falso positivo (ver el KDoc de
        // looksLikeCardPayment). Solo agrega una fila a CardPaymentDismissals; nunca escribe en
        // Events. Idempotente (descartar dos veces es 204 las dos) y aislado por usuario: 404,
        // no 403, si el evento no existe o es de otro — mismo criterio que PUT /{id}/category de
        // arriba. Un evento anulado (VoidEvents) se trata como inexistente, igual que ahí.
        //
        // No hay endpoint para deshacer esto: si el dueño se equivoca, el movimiento sigue en
        // Movimientos y se recategoriza a mano desde ahí con ChangeCategorySheet — incluso a
        // "Pago de tarjeta" si en verdad lo era.
        post("/{id}/not-card-payment") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()

            val found = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()
                val isVoided = event != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (event != null && !isVoided) {
                    val alreadyDismissed = CardPaymentDismissals.selectAll()
                        .where { (CardPaymentDismissals.eventId eq id) and (CardPaymentDismissals.userId eq uid) }
                        .count() > 0
                    if (!alreadyDismissed) {
                        CardPaymentDismissals.insert {
                            it[CardPaymentDismissals.userId]  = uid
                            it[CardPaymentDismissals.eventId] = id
                        }
                    }
                    true
                } else {
                    false
                }
            }
            if (!found) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }

        post("/{id}/void") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val reason = call.request.queryParameters["reason"]

            val event = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val void: VoidEvent? = dbQuery {
                val alreadyVoided = VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (alreadyVoided) {
                    null
                } else {
                    val now = System.currentTimeMillis()
                    val voidId = "void_${java.util.UUID.randomUUID()}"
                    fun anular(eventId: String, thisVoidId: String) {
                        VoidEvents.insert {
                            it[VoidEvents.id]              = thisVoidId
                            it[VoidEvents.userId]          = uid
                            it[VoidEvents.originalEventId] = eventId
                            it[VoidEvents.reason]          = reason
                            it[VoidEvents.timestamp]       = now
                        }
                    }
                    anular(id, voidId)
                    // Anular una pata de un traspaso anula la otra, en la misma transacción. Si
                    // no, el saldo miente: la plata desaparecería de la cuenta de destino sin
                    // volver a la de origen (o al revés). Se resuelve por transferId, no por
                    // "el otro evento con el mismo monto" — el enlace es explícito justamente
                    // para que esto no sea una adivinanza.
                    val transferId = event.transferId
                    if (transferId != null) {
                        val yaAnulados = VoidEvents.selectAll()
                            .where { VoidEvents.userId eq uid }
                            .map { it[VoidEvents.originalEventId] }
                            .toSet()
                        Events.selectAll()
                            .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
                            .map { it[Events.id] }
                            .filter { it != id && it !in yaAnulados }
                            .forEach { hermana -> anular(hermana, "void_${java.util.UUID.randomUUID()}") }
                    }
                    VoidEvent(
                        id              = voidId,
                        originalEventId = id,
                        reason          = reason,
                        timestamp       = now,
                    )
                }
            }
            if (void == null) return@post call.respond(HttpStatusCode.Conflict, "Already voided")
            call.respond(HttpStatusCode.Created, void)
        }
    }
}

