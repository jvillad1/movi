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
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.server.reminders.sostieneLaOcurrencia
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
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
                return@post call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_LEG_NOT_STANDALONE)
            }

            // Ola 10: **una categoría reservada no se anota A MANO.** `isCashFlow` las excluye por
            // nombre, así que un gasto real escrito como «Pago de tarjeta» se guardaba y
            // desaparecía de «Gastos del mes» sin que nada lo dijera. El campo de categoría avisa,
            // pero un cartel no es una guarda: se podía cerrar el selector con la categoría puesta
            // y guardar igual.
            //
            // La guarda es **precisa y no un rechazo general**, porque por esta misma ruta llegan
            // dos usos legítimos de categorías reservadas y bloquearlos rompería dos flujos que
            // hoy funcionan:
            //
            // - **`OPENING_CATEGORY` con `source = MANUAL`**: es el evento de apertura que crea el
            //   propio cliente al abrir una cuenta con saldo (ver `openingEventFor`). Nace MANUAL
            //   y reservado, y es correcto. Se deja pasar por eso, no por olvido — el camino de
            //   tipeo a mano ya lo corta el cliente (ver `QuickAddScreen`).
            // - **`CARD_PAYMENT_CATEGORY` con `source = SMS`**: es el pago de tarjeta detectado en
            //   un mensaje del banco y confirmado por el dueño (ver `SmsRoutes.categoryFor` y
            //   `SMSReconcileScreen`). Ahí la categoría reservada es exactamente la correcta.
            //
            // O sea: lo que se rechaza es escribir a mano una reservada que no sea la apertura.
            if (body.source == EventSource.MANUAL &&
                isReservedCategory(body.category) &&
                body.category.trim() != OPENING_CATEGORY
            ) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, CATEGORY_RESERVED_NOT_MANUAL)
            }
            val event = body.copy(
                id        = body.id.ifBlank { "ev_${java.util.UUID.randomUUID()}" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
                // **Cuándo lo anotó** (ver FinancialEvent.createdAt): lo manda el cliente, porque
                // es el único que sabe en qué momento el dueño lo escribió — el server solo sabe
                // cuándo LLEGÓ, y con la app offline eso puede ser dos días después. Un cliente
                // que no lo mande (la web, que postea apenas se guarda) queda sellado con `now`,
                // que ahí es el mismo instante.
                //
                // El único filtro es de cordura, no de confianza: un epoch fuera del rango de
                // milisegundos plausible —un reloj sin sincronizar en 1970, o un cliente con un
                // bug— se descarta y se usa `now`. No hace falta más: esto NO decide a qué día
                // pertenece el movimiento (eso es `timestamp`) ni entra en ningún total; solo
                // desempata renglones dentro de un mismo día, así que un reloj corrido puede
                // desordenar dos líneas y nada más.
                createdAt = body.createdAt?.takeIf { epochMillisToAppDate(it).year in 2000..2100 } ?: now,
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

            // **Guarda de cordura de año — y NO la guarda de futuro.** Son dos cosas distintas y
            // conviene decirlo, porque por esta ruta entra lo que llega solo (SMS, extracto, OCR),
            // que trae su propia fecha y no se pisa: un movimiento fechado mañana por el banco es
            // dato del banco, no un error nuestro. Pero un epoch de un cliente con un bug —1000 ms,
            // o un año de tres dígitos a medio parsear— **esconde el movimiento en 1970 para
            // siempre**: no encabeza ninguna lista, no entra en ningún mes, y nadie lo va a ver
            // para arreglarlo. Rechazarlo es lo único que lo hace visible.
            //
            // El rango es el mismo 2000..2100 que aplica `PUT /{id}/timestamp`, y el mismo que el
            // selector de fecha no deja pasar por su piso. `timestamp == 0` no llega acá: la línea
            // de arriba ya lo reemplazó por `now`, que es el default histórico de un cliente que
            // no manda fecha.
            val fechaDelEvento = epochMillisToAppDate(event.timestamp)
            if (fechaDelEvento.year !in 2000..2100) {
                return@post call.respond(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
            }

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
                    it[createdAt]            = event.createdAt
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
            val result = loadNonVoidedEvents(uid, accountId).masRecientePrimero()
            call.respond(result)
        }

        get("/by-day") {
            val uid = call.userId()
            val result = loadNonVoidedEvents(uid)
                // Ordenar ANTES de agrupar: `groupBy` conserva el orden de llegada dentro de cada
                // grupo, así que una sola pasada deja los días ordenados por dentro. Antes acá no
                // había criterio ninguno y los renglones del día salían en el orden físico de la
                // tabla —el que un UPDATE o un VACUUM puede cambiar sin avisar—, mientras el
                // endpoint hermano de arriba sí ordenaba. Ver MAS_RECIENTE_PRIMERO.
                .masRecientePrimero()
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

        /**
         * **Este movimiento, esta marcado como «esto ya ocurrio» de algun recurrente?**
         *
         * Existe para que la hoja que corrige la fecha pueda **avisar antes** -mismo criterio que
         * el aviso de cambio de mes y que `avisoDeUnificacion` en Categorias- en vez de dejar que
         * el dueno descubra el efecto el dia que no le llega el recordatorio.
         *
         * Devuelve tambien la **ventana de fechas que sostiene el sello** (`validFrom`/`validTo`,
         * ver [occurrenceWindow]) en vez de hacer que el cliente recalcule la regla: la ventana es
         * logica del emparejador y tiene que vivir de un solo lado. El cliente solo compara la
         * fecha que el dueno acaba de tocar contra esos dos dias.
         *
         * 204 (y no 404) cuando no hay marca: «no hay nada que avisar» es una respuesta normal de
         * esta pregunta, no un error, y asi el cliente no tiene que distinguirla de «ese
         * movimiento no existe».
         */
        get("/{id}/occurrence") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val marca = dbQuery {
                val fila = RecurringOccurrences.selectAll()
                    .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId eq id) }
                    .firstOrNull() ?: return@dbQuery null
                val regla = RecurringRules.selectAll()
                    .where {
                        (RecurringRules.id eq fila[RecurringOccurrences.ruleId]) and
                            (RecurringRules.userId eq uid)
                    }
                    .firstOrNull() ?: return@dbQuery null
                val period = fila[RecurringOccurrences.period]
                val due = occurrenceInMonth(
                    java.time.YearMonth.parse(period),
                    regla[RecurringRules.dayOfMonth],
                )
                val ventana = occurrenceWindow(due)
                EventOccurrenceMark(
                    ruleId = regla[RecurringRules.id],
                    ruleName = regla[RecurringRules.name],
                    period = period,
                    validFrom = ventana.start.toString(),
                    validTo = ventana.endInclusive.toString(),
                )
            }
            if (marca == null) call.respond(HttpStatusCode.NoContent) else call.respond(marca)
        }

        // ── Corregir la FECHA de un movimiento ya anotado ────────────────────────────────
        //
        // Hasta acá lo único editable de un movimiento era su categoría: para arreglarle la fecha
        // había que anularlo y volver a crearlo, o sea perder su id (y con él la ocurrencia de
        // recurrente que lo señalara) para cambiar un dato que el dueño nunca eligió — porque
        // hasta esta rama la hoja de Agregar sellaba siempre `Clock.System.now()`.
        //
        // Tres guardas, y cada una tiene su motivo:
        //
        // 1. **No al futuro.** Un movimiento es plata que YA se movió; uno fechado mañana infla
        //    el saldo y las cifras del mes con algo que no pasó. Es la misma regla que este
        //    server ya aplica del otro lado en `POST /api/recurring-rules/{id}/occurrence`
        //    («Ese vencimiento todavía no llegó»). El corte es por **día civil de Bogotá**
        //    (AppClock), no por instante: así un cliente con el reloj unos minutos adelantado
        //    —o en otra zona— no se queda sin poder fechar el gasto de hoy.
        //
        //    La guarda vive acá y NO en `POST /api/events` a propósito: por el POST entran
        //    también los movimientos que llegan solos (SMS, extracto, OCR), que traen su propia
        //    fecha y no se pisan. Esta ruta, en cambio, es siempre una corrección a mano.
        //
        // 2. **Un piso y un techo de año (2000..2100).** Un epoch-ms cerca de 0 es un cliente con
        //    un bug, no una intención, y dejarlo entrar esconde el movimiento en 1970 para
        //    siempre. (Es más angosto que el 1900..2100 de `isValidCreditDate`, que tiene que
        //    admitir una fecha de nacimiento; acá no hay ningún movimiento legítimo del siglo XX.)
        //
        // 3. **Las dos patas de un traspaso se mueven juntas.** La fecha de un traspaso es UN
        //    hecho, no dos: mover solo la pata de origen dejaría la plata saliendo un día y
        //    entrando otro, y en Movimientos el traspaso se partiría en dos renglones sueltos
        //    (`collapseTransfers` agrupa dentro de un mismo día). Se cascadea por `transferId`,
        //    en la misma transacción y por el mismo camino explícito que ya usa la anulación —
        //    no por «el otro evento con el mismo monto».
        put("/{id}/timestamp") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val nuevo = call.receive<UpdateEventTimestampRequest>().timestamp
            val fecha = epochMillisToAppDate(nuevo)
            if (fecha.year !in 2000..2100) {
                return@put call.respond(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
            }
            if (fecha.isAfter(AppClock.today())) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, EVENT_DATE_IN_FUTURE)
            }

            val updated: FinancialEvent? = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                // Un evento anulado se trata como inexistente, igual que en PUT /{id}/category:
                // ningún GET lo vuelve a mostrar, así que la fecha que devolviéramos acá no se
                // vería en ninguna pantalla.
                val isVoided = event != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (event == null || isVoided) {
                    null
                } else {
                    val transferId = event.transferId
                    val idsAfectados: List<String> = if (transferId != null) {
                        Events.update({ (Events.userId eq uid) and (Events.transferId eq transferId) }) {
                            it[timestamp] = nuevo
                        }
                        Events.selectAll()
                            .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
                            .map { it[Events.id] }
                    } else {
                        Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                            it[timestamp] = nuevo
                        }
                        listOf(id)
                    }
                    // ── EL SELLO DE RECURRENTE SE SUELTA SI LA EVIDENCIA SE FUE DEL PERIODO ──
                    //
                    // `recurring_occurrences` sella un PERIODO («agosto ya ocurrió») y guarda el
                    // movimiento como **evidencia**. Mientras la evidencia siga sirviendo, el
                    // sello vale: corregir el arriendo del 5 al 12 de agosto —o pagarlo tarde, el
                    // 3 de septiembre, que sigue adentro de los 10 días— no lo suelta.
                    //
                    // Lo que no puede pasar es lo inverso, y es lo que costaba plata: el dueno
                    // sella agosto con un movimiento, despues se da cuenta de que ese movimiento
                    // era de julio y le corrige la fecha. Agosto quedaba **dado por pagado con
                    // una evidencia que el emparejador nunca habria propuesto**: el arriendo de
                    // agosto dejaba de contar en agosto Y Movi no volvia a recordarlo. Encima el
                    // movimiento quedaba quemado - sellar julio con el daba 409.
                    //
                    // Asi que se suelta, y se suelta **con el mismo criterio con el que se
                    // propone** ([sostieneLaOcurrencia]): ni un dia mas ancho, ni uno mas
                    // angosto. Es la misma decision que `loadOccurredBy` ya tomo y escribio para
                    // el caso hermano (el movimiento anulado): «volver a avisar de mas molesta un
                    // toque, callar una deuda real cuesta plata».
                    //
                    // **Por que se suelta en vez de preguntar.** Preguntar aca le exige al dueno
                    // decidir, sobre una pantalla que no esta mirando, algo cuya respuesta
                    // correcta vive en Recurrentes. Soltar es el lado barato y **ruidoso** del
                    // error: el recurrente vuelve a aparecer pendiente, con su «Ya ocurrio» a un
                    // toque, asi que se anuncia solo. Y libera el movimiento para sellar el
                    // periodo que si le corresponde. La hoja igual lo dice ANTES -el aviso del
                    // cambio de fecha nombra el recurrente y el mes (ver `GET /{id}/occurrence`)-
                    // asi que soltar no es una sorpresa: es lo que se anuncio.
                    soltarOcurrenciasSinEvidencia(uid, idsAfectados, fecha)
                    event.copy(timestamp = nuevo).withCashFlowFlag(accountTypesFor(uid))
                }
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

            // El `try` cubre la carrera real que el chequeo de `alreadyVoided` de adentro NO
            // puede cerrar: dos dispositivos anulando las dos patas del mismo traspaso a la vez.
            // Los dos leen "no está anulada", los dos cascadean, y el que commitea segundo choca
            // contra `uq_void_events_original_user`. Sin este catch eso salía como un 500 sin
            // atrapar y el cliente perdedor lo reintentaba cada 30 segundos para siempre —
            // cuando en realidad su anulación YA ocurrió, que es justo lo que quería. Un 409 dice
            // exactamente eso, y el `SyncEngine` lo sella como resuelto (ver `syncVoids`).
            val void: VoidEvent? = try {
                dbQuery {
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
            } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                // La otra punta de la carrera ya insertó esta anulación: el resultado que el
                // cliente pedía está logrado. Se responde 409, igual que el camino de arriba.
                println("[void] anulación concurrente de $id: ${e.message}")
                null
            }
            if (void == null) return@post call.respond(HttpStatusCode.Conflict, "Already voided")
            call.respond(HttpStatusCode.Created, void)
        }
    }
}

/**
 * Suelta los sellos de `recurring_occurrences` que apuntan a [eventIds] y que, con la fecha nueva
 * [fecha], ya no tendrian evidencia (ver [sostieneLaOcurrencia]).
 *
 * Corre **dentro de la misma transaccion** que movio el movimiento: o se mueve la fecha y se
 * suelta el sello, o no pasa ninguna de las dos. Un sello huerfano -cuya regla ya no existe- se
 * suelta tambien: no hay con que validarlo, y dejarlo puesto es exactamente el silencio que esto
 * viene a sacar.
 */
private fun soltarOcurrenciasSinEvidencia(
    uid: String,
    eventIds: List<String>,
    fecha: java.time.LocalDate,
) {
    if (eventIds.isEmpty()) return
    val filas = RecurringOccurrences.selectAll()
        .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId inList eventIds) }
        .map { it[RecurringOccurrences.ruleId] to it[RecurringOccurrences.period] }
    for ((ruleId, period) in filas) {
        val dia = RecurringRules.selectAll()
            .where { (RecurringRules.id eq ruleId) and (RecurringRules.userId eq uid) }
            .firstOrNull()?.get(RecurringRules.dayOfMonth)
        val sigueValiendo = dia != null && runCatching {
            sostieneLaOcurrencia(fecha, occurrenceInMonth(java.time.YearMonth.parse(period), dia))
        }.getOrDefault(false)
        if (!sigueValiendo) {
            RecurringOccurrences.deleteWhere {
                (RecurringOccurrences.userId eq uid) and
                    (RecurringOccurrences.ruleId eq ruleId) and
                    (RecurringOccurrences.period eq period)
            }
        }
    }
}
