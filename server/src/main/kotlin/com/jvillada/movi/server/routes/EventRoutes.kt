package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.toAccount
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.server.reminders.sostieneLaOcurrencia
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.PAYROLL_DEDUCTION_CATEGORY
import com.jvillada.movi.shared.model.THIRD_PARTY_PAYMENT_CATEGORY

/**
 * Lo que la transacción de `PUT /api/events/{id}` decidió, para que el `call.respond` viva
 * **afuera** de ella.
 *
 * Existe porque esa ruta tiene tres finales distintos —no existe, se rechaza con un código y un
 * texto propios, o se guardó— y responder desde adentro de `dbQuery` obligaría a mezclar la
 * transacción con el ciclo de vida del request. Con esto, la transacción solo decide y escribe.
 */
private sealed interface ResultadoDeEdicion {
    object NoExiste : ResultadoDeEdicion
    class Rechazado(val rechazo: RechazoDeEdicion) : ResultadoDeEdicion
    class Ok(val evento: FinancialEvent) : ResultadoDeEdicion
}

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
            // Ola 15: ni a «Cuenta eliminada» tampoco. Hasta acá esta puerta estaba abierta y no
            // escondía nada —esa categoría todavía contaba en el mes—, pero desde que `isCashFlow`
            // la excluye, escribirla en un gasto real lo haría desaparecer de «Gastos del mes» sin
            // que nada lo dijera: el mismo daño silencioso que la guarda de la ola 10 cerró en
            // `POST /api/events` para las otras reservadas.
            //
            // Y se bloquea SOLO esta, no toda categoría reservada: por esta misma ruta pasa la
            // confirmación de un pago de tarjeta (ver GET /card-payment-candidates arriba), que
            // escribe CARD_PAYMENT_CATEGORY a propósito y es correcta.
            if (category == ORPHANED_LEG_CATEGORY) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, ORPHANED_LEG_NOT_MANUAL)
            }
            // Ola 17: ni a «Descuento de nómina». Es la reservada más nueva y llegó con el mismo
            // peligro que las anteriores: `isCashFlow` la excluye del mes POR NOMBRE, así que
            // escribirla sobre un gasto real lo haría desaparecer de «Gastos del mes» sin decir
            // nada. La guarda se agrega en la misma ola que la categoría, no una ola después —
            // que es como las otras tres llegaron acá.
            if (category == PAYROLL_DEDUCTION_CATEGORY) {
                return@put call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "«Descuento de nómina» la escribe Movi cuando registras la cuota de una libranza",
                )
            }
            // Ola 18: ni a «Pago de un tercero», por lo mismo y en la misma ola en que nace la
            // categoría. Esta es la sexta reservada y la segunda que llega con su guarda puesta
            // desde el primer día; las cuatro primeras llegaron acá tarde, cada una después de
            // que el daño ya fuera posible.
            if (category == THIRD_PARTY_PAYMENT_CATEGORY) {
                return@put call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "«Pago de un tercero» la escribe Movi cuando registras la cuota de un crédito que paga otro",
                )
            }
            // Ola 16: ni a «Saldo inicial». Es la reservada que faltaba, y era la más cara de las
            // cuatro por esta puerta — ver [OPENING_CATEGORY_RESERVED], que trae la medición: un
            // gasto real de $50.000 recategorizado así contestaba 200 y bajaba «Gastos del mes» de
            // $165.289 a $115.289, sin decir nada. `POST /api/events` ya cerraba este daño desde la
            // Ola 10; `PUT` no, y la app ofrecía el camino con el botón «Usar "…"» del campo libre
            // (cerrado también en esta ola, ver `ofreceCategoriaEscritaAMano`).
            if (category == OPENING_CATEGORY) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, OPENING_CATEGORY_RESERVED)
            }
            // Y nadie sale tampoco: sacar una pata de la categoría reservada la devolvería al
            // flujo de caja del mes —el gasto fantasma que esta feature vino a matar— y dejaría
            // a su hermana adentro, contando la mitad de un movimiento que nunca ocurrió.
            //
            // Ola 16: la apertura de una cuenta tampoco sale. El sentido inverso del anterior y el
            // mismo daño al revés: sacar un «Saldo inicial» de una cuenta de activo a «Otros
            // ingresos» lo convierte en un ingreso del mes de golpe (medido: de $0 a $3.000.000).
            // Se leen las dos cosas de la MISMA fila para no pagar dos consultas por lo mismo.
            val fila = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()
                    ?.let { it[Events.transferId] to it[Events.category] }
            }
            val esPataDeTraspaso = fila != null && (fila.first != null || fila.second == TRANSFER_CATEGORY)
            if (esPataDeTraspaso) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_RECATEGORIZE_BLOCKED)
            }
            if (fila?.second == OPENING_CATEGORY) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, OPENING_RECATEGORIZE_BLOCKED)
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

        // ── Corregir el MONTO, la CUENTA y el CONCEPTO de un movimiento ya anotado ───────
        //
        // La tercera puerta de edición de un movimiento, después de la categoría y la fecha. El
        // dueño la pidió con un caso concreto: «Necesito editar el valor del movimiento de Hija
        // porque voy a pagar 3 millones desde NU y 1 millón desde Bancolombia» — el monto y la
        // cuenta, los dos únicos datos que hasta hoy solo se podían cambiar anulando el
        // movimiento y volviéndolo a crear, o sea perdiendo su id (y con él su sello de
        // recurrente y su descarte de «no es pago de tarjeta»).
        //
        // **Lo que NO hay que recalcular, y por qué se puede afirmar.** Ningún total de Movi es
        // un acumulado guardado del lado del server: el saldo de una cuenta lo deriva
        // `enrichWith`/`computeBalances` de sus eventos en cada lectura (la columna
        // `accounts.balance` no la lee nadie para derivar nada), «Gastos del mes» los suma
        // `/api/finance-summary` sobre los eventos del período, los presupuestos salen del mismo
        // lado y el Inicio también. Así que corregir la fila ES el recálculo: las dos cuentas
        // involucradas en un cambio de cuenta se mueven solas en la próxima lectura. (El espejo
        // local SÍ tiene un saldo acumulado y ahí sí hay que ajustarlo a mano — ver
        // `LocalRepository.updateEvent`.)
        //
        // **`countsAsCashFlow` se vuelve a derivar acá** (`withCashFlowFlag`), como en todas las
        // rutas que devuelven un evento: mover un gasto a una cuenta LOAN lo saca del mes por
        // regla de `isCashFlow`, y la respuesta tiene que decirlo o la pantalla se queda pintando
        // lo contrario hasta el próximo refetch. La hoja además lo **avisa antes** de guardar
        // (ver `avisoDeCambioDeCuenta`).
        //
        // Las guardas viven en `:core` (`validarEdicionDeMovimiento`) y no acá, por el mismo
        // motivo por el que viven allá las de la fecha: el espejo local tiene que rechazar
        // exactamente lo mismo, con las mismas palabras y el mismo código, cuando resuelve sin
        // red un movimiento que todavía no subió.
        put("/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val pedido = call.receive<EdicionDeMovimiento>()

            // Leer, validar y escribir en UNA transacción. Si la lectura del evento y la de la
            // cuenta destino vivieran afuera, entre la validación y el UPDATE la cuenta podría
            // borrarse (`DELETE /api/accounts/{id}` existe) y el movimiento terminaría apuntando
            // a una cuenta que ya no está — invisible en Cuentas y fuera de todo saldo.
            val salida: ResultadoDeEdicion = dbQuery {
                val fila = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                // Un evento anulado se trata como inexistente, igual que en PUT /{id}/category y
                // en PUT /{id}/timestamp: ningún GET lo vuelve a mostrar, así que lo que
                // devolviéramos acá no se vería en ninguna pantalla.
                val anulado = fila != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (fila == null || anulado) return@dbQuery ResultadoDeEdicion.NoExiste

                // Solo lo que de verdad cambia: la hoja manda los tres campos siempre, y sin esto
                // corregir el concepto de una pata cascadearía el monto a la hermana por nada —
                // y peor, mandar la misma cuenta rebotaría con PATA_NO_CAMBIA_DE_CUENTA.
                val cambios = soloLoQueCambia(fila, pedido)
                val cuentaNueva = cambios.accountId?.let { pedida ->
                    Accounts.selectAll()
                        .where { (Accounts.id eq pedida) and (Accounts.userId eq uid) }
                        .firstOrNull()?.toAccount()
                }
                val rechazo = validarEdicionDeMovimiento(
                    cambios = cambios,
                    esPataDeUnPar = fila.transferId != null,
                    cuentaActualId = fila.accountId,
                    monedaDelMovimiento = fila.currency,
                    cuentaNueva = cuentaNueva,
                )
                if (rechazo != null) return@dbQuery ResultadoDeEdicion.Rechazado(rechazo)

                val nuevoMonto = cambios.amount
                val nuevaCuenta = cambios.accountId
                val nuevoConcepto = cambios.description
                if (nuevoMonto == null && nuevaCuenta == null && nuevoConcepto == null) {
                    // Guardar sin haber cambiado nada no es un error: es un 200 con el evento tal
                    // como está. Escribir igual sería una fila tocada sin motivo.
                    return@dbQuery ResultadoDeEdicion.Ok(fila.withCashFlowFlag(accountTypesFor(uid)))
                }

                // **El monto de un par se mueve en las DOS mitades**, en la misma transacción y
                // por el mismo camino explícito que ya usan la anulación y el cambio de fecha:
                // por `transferId`, no por «el otro evento con el mismo monto». Cambiarlo en una
                // sola dejaría plata saliendo de una cuenta y entrando otra cifra en la otra —
                // el descuadre silencioso que esta ruta no puede permitir. Ver
                // `PATA_NO_CAMBIA_DE_CUENTA` para por qué la CUENTA, en cambio, se rechaza.
                //
                // **YA NO COPIA: recalcula.** Hasta la ola pasada las dos patas nacían con la
                // misma cifra y copiar era correcto. Desde que la pata de la DEUDA de una cuota
                // vale solo el **capital** (ver `DesgloseDeCuota`), copiar le bajaría a la deuda
                // los intereses también: corregir una cuota de $4.215.223 a $4.500.000 le habría
                // restado $4.500.000 a un crédito al que solo le tocaba el capital. La regla vive
                // en `:core` y la comparte con el espejo local del teléfono:
                // [montoDeLaHermanaAlCorregir], que recalcula el capital sobre el interés y el
                // seguro GUARDADOS en la pata de la deuda (`no_amortiza`) — deducirlos de la resta
                // de las dos patas mentía justo cuando el capital se había clampado a cero.
                //
                // Se escribe hermana por hermana y no con un UPDATE masivo por `transferId`,
                // porque ahora cada una tiene su propia cifra nueva. Y se lee la fila entera y no
                // solo `(id, amount)`: hace falta su `noAmortiza`.
                val transferId = fila.transferId
                if (nuevoMonto != null && transferId != null) {
                    val hermanas = Events.selectAll()
                        .where {
                            (Events.userId eq uid) and (Events.transferId eq transferId) and (Events.id neq id)
                        }
                        .map { it.toFinancialEvent() }
                    hermanas.forEach { hermana ->
                        val montoNuevoDeLaHermana = montoDeLaHermanaAlCorregir(
                            montoViejo = fila.amount,
                            montoNuevo = nuevoMonto,
                            montoDeLaHermana = hermana.amount,
                            noAmortizaDeLaHermana = hermana.noAmortiza,
                            noAmortizaDeLaPataQueSeCorrige = fila.noAmortiza,
                        )
                        Events.update({ (Events.userId eq uid) and (Events.id eq hermana.id) }) {
                            it[amount] = montoNuevoDeLaHermana
                        }
                    }
                }
                Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                    if (nuevoMonto != null) it[amount] = nuevoMonto
                    if (nuevaCuenta != null) it[accountId] = nuevaCuenta
                    if (nuevoConcepto != null) it[description] = nuevoConcepto
                }

                ResultadoDeEdicion.Ok(
                    fila.copy(
                        amount = nuevoMonto ?: fila.amount,
                        accountId = nuevaCuenta ?: fila.accountId,
                        description = nuevoConcepto ?: fila.description,
                    ).withCashFlowFlag(accountTypesFor(uid)),
                )
            }

            when (salida) {
                is ResultadoDeEdicion.NoExiste -> call.respond(HttpStatusCode.NotFound)
                is ResultadoDeEdicion.Rechazado -> call.respond(
                    HttpStatusCode.fromValue(salida.rechazo.status),
                    salida.rechazo.mensaje,
                )
                is ResultadoDeEdicion.Ok -> call.respond(salida.evento)
            }
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
