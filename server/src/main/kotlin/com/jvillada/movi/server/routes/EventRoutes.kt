package com.jvillada.movi.server.routes

import com.jvillada.movi.server.plugins.jsonDeLaApi
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import com.jvillada.movi.shared.model.MONTO_DE_UN_PAGO_ENTRE_MONEDAS
import com.jvillada.movi.shared.model.RechazoDeEdicion
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.reminders.periodoDelDueno
import org.jetbrains.exposed.sql.update
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
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


/**
 * **La categoría está mal escrita** (vacía, o más larga de lo que la columna aguanta), o `null` si
 * se puede usar. 400: es un error de forma, no una regla de negocio.
 */
/**
 * **Cuántos parecidos se ofrecen y se cambian de una.** No es una cota de rendimiento: es que un
 * lote que el dueño no puede leer de un vistazo antes de tocar no es una confirmación, es un
 * cheque en blanco sobre sus propias cifras.
 */
internal const val MAXIMO_DE_PARECIDOS = 50

internal fun categoriaMalEscrita(category: String): String? = when {
    category.isBlank() -> "La categoría no puede estar vacía"
    category.length > 60 -> "La categoría no puede superar 60 caracteres"
    else -> null
}

/**
 * **Las categorías que Movi escribe sola y que nadie pone a mano**, con el motivo que se le muestra
 * al dueño — o `null` si esta se puede usar.
 *
 * Cada una de estas guardas nació de un daño medido y silencioso: `isCashFlow` excluye estas
 * categorías del mes POR NOMBRE, así que escribir una sobre un gasto real lo hace desaparecer de
 * «Gastos del mes» contestando 200 y sin decir nada (un gasto de $50.000 recategorizado a «Saldo
 * inicial» bajaba el mes de $165.289 a $115.289). Las cuatro primeras llegaron acá **después** de
 * que el daño ya fuera posible; las siguientes, el mismo día que nació la categoría.
 *
 * Están juntas en una función y no repartidas en cada ruta porque desde hoy hay **dos** puertas
 * —una por movimiento y una por lote— y una guarda que solo cierra una de las dos no es una guarda.
 *
 * «Pago de tarjeta» es la excepción a propósito: la confirmación de un candidato la escribe por
 * esta misma puerta (ver `GET /card-payment-candidates`), y es correcta.
 */
internal fun categoriaQueMoviEscribeSola(category: String): String? = when {
    // Un evento recategorizado a "Traspaso" sería medio traspaso: se dejaría de contar en el mes
    // sin ninguna pata del otro lado que explique adónde fue la plata.
    category == TRANSFER_CATEGORY -> TRANSFER_CATEGORY_RESERVED
    category == ORPHANED_LEG_CATEGORY -> ORPHANED_LEG_NOT_MANUAL
    category == PAYROLL_DEDUCTION_CATEGORY ->
        "«Descuento de nómina» la escribe Movi cuando registras la cuota de una libranza"
    category == THIRD_PARTY_PAYMENT_CATEGORY ->
        "«Pago de un tercero» la escribe Movi cuando registras la cuota de un crédito que paga otro"
    category == OPENING_CATEGORY -> OPENING_CATEGORY_RESERVED
    // Y todas las demás, **sin distinguir mayúsculas**: comparar exacto dejaba pasar «traspaso» en
    // minúscula, y con eso un gasto real de $200.000 salía de «Gastos del mes» sin decir nada.
    category != CARD_PAYMENT_CATEGORY && isReservedCategory(category) ->
        "«$category» la escribe Movi sola: no se puede poner a mano"
    else -> null
}

/**
 * Los orígenes que, al llegar por `POST /api/events`, ya pasaron por los ojos del dueño: lo que
 * anotó a mano y lo que confirmó desde la bandeja de SMS. Llegan confirmados aunque el cliente
 * mande el default `UNCONFIRMED` del modelo. OCR y extracto sí entran solos y esperan.
 */
internal val ORIGENES_YA_REVISADOS = setOf(EventSource.MANUAL, EventSource.SMS)

/**
 * **¿El reenvío del teléfono pisa lo que el server ya tiene?** La regla entera de la carrera entre
 * los dos dispositivos, en un solo lugar y sin tocar la base, para poder probarla sola.
 *
 * Tres casos, y cada uno tiene su motivo:
 *
 * 1. **El cliente no mandó la clave** ([mandoLaEdicion] `false`) → **pisa**, igual que antes de
 *    esta ola. Es un APK anterior a este campo —el del dueño corre el 1.25— y no tiene forma de
 *    decir qué tan vieja es su copia. Tratar esa ausencia como «editado en el año 0» lo haría
 *    perder **siempre**, incluso cuando su copia es la corrección legítima que él acaba de escribir
 *    sin señal: le estaríamos borrando datos a cambio de tapar el agujero. El agujero para ese APK
 *    se cierra el día que instale el nuevo, no antes.
 * 2. **Nadie editó lo guardado** ([edicionGuardada] `null`) → **pisa**. No hay ninguna corrección
 *    que proteger: lo que está en el server es lo que este mismo teléfono subió.
 * 3. **Los dos tienen edad** → pisa solo si la que llega **no es más vieja**. El empate va para el
 *    que llega a propósito: el caso normal es el reenvío del MISMO contenido (el POST llegó y la
 *    respuesta no), y ahí escribir lo mismo encima de lo mismo no cambia nada. Con [edicionQueLlega]
 *    en `null` y algo guardado, el reenvío pierde: el teléfono está diciendo «yo no edité esto», o
 *    sea que su copia es la original y la del server es posterior.
 */
internal fun pisaElReenvio(
    mandoLaEdicion: Boolean,
    edicionQueLlega: Long?,
    edicionGuardada: Long?,
): Boolean {
    if (!mandoLaEdicion) return true
    if (edicionGuardada == null) return true
    return edicionQueLlega != null && edicionQueLlega >= edicionGuardada
}

fun Route.eventRoutes() {
    route("/api/events") {

        post {
            // El JSON crudo además del objeto, para saber si el cliente MANDÓ la moneda: un APK
            // anterior al arreglo no la guardaba en el teléfono, así que sus movimientos llegan sin
            // la clave (el default "COP" no se serializa) aunque sean de una cuenta en dólares.
            val crudo = call.receive<JsonObject>()
            val body = jsonDeLaApi.decodeFromJsonElement<FinancialEvent>(crudo)
            val mandoLaMoneda = "currency" in crudo
            // Y si el cliente sabe de ediciones, por el mismo camino y por un motivo parecido: un
            // `lastEditedAt` ausente es un APK que no conoce el campo, y uno presente en `null` es
            // uno que sí lo conoce y está diciendo «esta copia no la editó nadie». Distinguirlos es
            // lo que deja pisar al primero (como hasta hoy, para no perderle nada al teléfono que
            // ya está instalado) y hacer perder al segundo contra una corrección más nueva de la
            // web. Ver `FinancialEvent.lastEditedAt` y `pisaElReenvio` más abajo.
            val mandoLaEdicion = "lastEditedAt" in crudo
            val uid = call.userId()
            val now = System.currentTimeMillis()

            // Un traspaso son DOS patas que nacen juntas o no nacen (ver TransferRoutes.kt).
            // Aceptar acá un evento suelto con transferId —o con la categoría reservada— sería
            // dejar entrar medio traspaso: plata saliendo de una cuenta sin la pata que la
            // compensa del otro lado, y encima invisible para el mes por la regla de isCashFlow.
            if (body.transferId != null || body.category == TRANSFER_CATEGORY) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_LEG_NOT_STANDALONE)
            }

            // La forma del dato, antes que cualquier otra cosa: la misma regla que ya aplicaba la
            // edición (ver `rechazoDelMonto`). Sin esto se podía CREAR en $0 lo que no se podía
            // CORREGIR a $0.
            rechazoDelMonto(body.amount)?.let { motivo ->
                return@post call.respond(HttpStatusCode.BadRequest, motivo)
            }

            // Y el largo de los dos textos, por el mismo argumento: la corrección ya lo validaba y
            // el alta no, así que una nota de 400 caracteres escrita en «Agregar» no se rechazaba
            // — se estrellaba contra el `varchar(255)` y salía por el 500 genérico. Ver
            // [rechazoDeLosTextos], que explica por qué en el teléfono eso era un reintento eterno
            // y silencioso en vez de un error.
            rechazoDeLosTextos(body.category, body.description)?.let { motivo ->
                return@post call.respond(HttpStatusCode.BadRequest, motivo)
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
            //
            // Y una tercera, más angosta: **Pago de tarjeta sobre un movimiento que el server YA
            // tiene.** «Marcar» un candidato a pago de tarjeta (que siempre viene del server) sobre
            // un movimiento que el teléfono todavía no selló lo escribe en local, y el reenvío llega
            // MANUAL con esa categoría. Es exactamente lo que `PUT /{id}/category` deja hacer; por
            // esta puerta rebotaba con 422 en cada ciclo de sync. Un alta NUEVA en «Pago de tarjeta»
            // escrita a mano se sigue rechazando.
            val yaExiste = body.id.isNotBlank() && dbQuery {
                Events.selectAll().where { (Events.id eq body.id) and (Events.userId eq uid) }.count() > 0
            }
            if (body.source == EventSource.MANUAL &&
                isReservedCategory(body.category) &&
                body.category.trim() != OPENING_CATEGORY &&
                !(yaExiste && body.category.trim() == CARD_PAYMENT_CATEGORY)
            ) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, CATEGORY_RESERVED_NOT_MANUAL)
            }
            var event = body.copy(
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
                //
                // **Un SMS tampoco**: por esta ruta un `source = SMS` solo llega desde la bandeja de
                // mensajes, cuando el dueño leyó el aviso del banco y tocó «Confirmar» (la captura
                // del teléfono sube MENSAJES a `/api/sms/sync`, no movimientos). Dejarlo en «por
                // confirmar» lo sacaba de «Gastos» después de que el dueño lo confirmó con sus
                // propios dedos, y hasta el APK 1.22 el cliente no mandaba el estado.
                reconciliationStatus = if (body.source in ORIGENES_YA_REVISADOS && body.reconciliationStatus == ReconciliationStatus.UNCONFIRMED)
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

            val monedaDeLaCuenta = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.get(Accounts.currency)
            } ?: return@post call.respond(HttpStatusCode.NotFound, "Account not found")
            // Sin moneda en el pedido, la de la cuenta: es la única que un movimiento de esa cuenta
            // puede tener si nadie dijo otra cosa. Un US$120 anotado en el teléfono sobre la Master
            // Black USD se guardaba como $120 pesos.
            if (!mandoLaMoneda) event = event.copy(currency = monedaDeLaCuenta)

            // **Un id que ya existe no es un error: es el mismo movimiento que vuelve.**
            //
            // El teléfono sube cada movimiento pendiente y lo sella solo si nadie lo tocó mientras
            // el POST viajaba (`SyncEngine.syncEvents`, `markSyncedIfUnchanged`). Si el dueño lo
            // corrige en esa ventana —de $50.000 a $5.000—, el POST ya insertó $50.000 pero la fila
            // local queda sin sellar, y el ciclo siguiente reenvía el MISMO id con $5.000. Antes eso
            // chocaba contra la clave primaria (500) en cada ciclo, para siempre: la web y el Inicio
            // decían $50.000 y el teléfono $5.000.
            //
            // Ahora el reenvío se toma como la versión vigente de lo que el dueño escribió: se
            // actualizan los campos que el teléfono deja corregir mientras está pendiente (los
            // mismos que compara `markSyncedIfUnchanged`, más «no se repite»). Solo para un
            // movimiento suelto del mismo dueño: una pata de traspaso o de cuota nunca sale por
            // esta ruta (ver arriba), y un id de otro usuario es un choque real (409).
            //
            // **Pero «el reenvío manda» no puede ser incondicional, y ahí estaba el agujero.** El
            // POST puede haber LLEGADO sin que el teléfono viera la respuesta (se cortó la señal a
            // mitad, se murió el proceso): la fila local se queda sin sellar aunque el server ya la
            // tenga. Si en esa ventana el dueño corrige el movimiento **en la web** —el monto, la
            // categoría, la fecha, el concepto, la cuenta— el ciclo siguiente reenviaba la copia
            // vieja del teléfono y este UPDATE pisaba la corrección sin decir nada.
            //
            // Ahora el reenvío pierde contra una edición más nueva: [pisaElReenvio] compara la edad
            // de las dos versiones (ver `FinancialEvent.lastEditedAt`). Cuando pierde no se escribe
            // nada y se contesta **200 con lo guardado** — no un error: para el teléfono el
            // movimiento efectivamente llegó, así que sellarlo y dejar de reenviarlo es la verdad,
            // y la próxima lectura de `getEvents` (server primero) le baja la versión buena.
            val reenvio = dbQuery {
                val existente = Events.selectAll().where { Events.id eq event.id }.firstOrNull()
                    ?: return@dbQuery null
                if (existente[Events.userId] != uid) return@dbQuery HttpStatusCode.Conflict to null
                if (existente[Events.transferId] == null && pisaElReenvio(
                        mandoLaEdicion = mandoLaEdicion,
                        edicionQueLlega = event.lastEditedAt,
                        edicionGuardada = existente[Events.lastEditedAt],
                    )
                ) {
                    val cambioLaFecha = existente[Events.timestamp] != event.timestamp
                    Events.update({ (Events.id eq event.id) and (Events.userId eq uid) }) {
                        it[accountId]   = event.accountId
                        it[amount]      = event.amount
                        it[category]    = event.category
                        it[description] = event.description
                        it[merchant]    = event.merchant
                        it[timestamp]   = event.timestamp
                        it[Events.noSeRepite] = event.noSeRepite
                        // La edad de la versión que acaba de ganar, para que la próxima se compare
                        // contra ella y no contra la que había. Solo si vino: un APK viejo no tiene
                        // ninguna que ofrecer y la guardada se deja como está.
                        if (event.lastEditedAt != null) it[Events.lastEditedAt] = event.lastEditedAt
                        // Confirmado en el teléfono (`confirmEvent` sin señal) → confirmado acá.
                        // Solo en esa dirección: un reenvío viejo no puede devolver a «por
                        // confirmar» algo que ya se confirmó en la web.
                        if (event.reconciliationStatus == ReconciliationStatus.RECONCILED) {
                            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
                        }
                    }
                    // Mismo criterio que `PUT /{id}/timestamp`: si la fecha se movió, un «ya
                    // ocurrió» sellado con este movimiento se suelta cuando ya no le corresponde.
                    if (cambioLaFecha) soltarOcurrenciasSinEvidencia(uid, listOf(event.id), fechaDelEvento)
                }
                HttpStatusCode.OK to Events.selectAll().where { Events.id eq event.id }.first()
                    .toFinancialEvent().withCashFlowFlag(accountTypesFor(uid))
            }
            if (reenvio != null) {
                val (estado, guardado) = reenvio
                return@post if (guardado == null) call.respond(estado, "Ese id ya existe")
                else call.respond(estado, guardado)
            }

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
                    // Faltaba: el teléfono guarda «no se repite» en un movimiento pendiente y el
                    // POST que lo sube lo perdía, así que volvía a aparecer en Recurrentes.
                    it[Events.noSeRepite]    = event.noSeRepite
                    // Casi siempre null: un alta no tiene versión anterior a la que ganarle. No lo
                    // es cuando el movimiento se anotó Y se corrigió sin señal, y ahí importa que
                    // la edición viaje con él — si no, el server lo guardaría como «nunca editado»
                    // y la comparación del próximo reenvío arrancaría de cero.
                    it[Events.lastEditedAt]  = event.lastEditedAt
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
                        // La misma función que usa el cliente: ver `aporteAlFlujoDelDia`.
                        total = items.sumOf { aporteAlFlujoDelDia(it) },
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
            categoriaMalEscrita(category)?.let {
                return@put call.respond(HttpStatusCode.BadRequest, it)
            }
            categoriaQueMoviEscribeSola(category)?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, it)
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
                val ahora = System.currentTimeMillis()
                if (event != null && !isVoided) {
                    Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                        it[Events.category] = category
                        // **La edad de esta corrección** (ver `FinancialEvent.lastEditedAt`): sin
                        // esto, un reenvío del teléfono con la categoría vieja la pisaría en
                        // silencio. Toda escritura que corrija un movimiento existente lo sella.
                        it[Events.lastEditedAt] = ahora
                    }
                }
                event?.takeIf { !isVoided }
                    ?.copy(category = category, lastEditedAt = ahora)
                    ?.withCashFlowFlag(accountTypesFor(uid))
            }
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        /**
         * **Los otros movimientos del mismo destinatario**, para poder arreglarlos todos de una.
         *
         * Corregir la categoría de a uno es justo lo que nadie hace, y por eso «Otros» se queda
         * ahí. Cuando Movi ya sabe que cinco movimientos son del mismo lugar (ver
         * [com.jvillada.movi.shared.model.huellaDeUnMovimiento]), ofrecer el lote es la diferencia
         * entre un toque y cinco.
         *
         * Devuelve los del mismo destinatario **con su categoría actual**, sin este mismo, sin los
         * anulados y sin las patas de traspaso ni las aperturas —que esta puerta no puede mover de
         * todos modos—. Quién queda por cambiar lo decide el cliente contra la categoría que el
         * dueño acaba de elegir, que es un dato que todavía no existe cuando se pide esta lista.
         * Lista vacía es la respuesta más común y no es un error.
         *
         * Un movimiento cuyo texto **no identifica a nadie** («Pago QR» a secas) no tiene
         * parecidos: la huella es `null` y la respuesta es vacía. Es a propósito — juntar todos los
         * pagos por QR del mes bajo una categoría sería peor que dejarlos sin categoría.
         */
        get("/{id}/parecidos") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val parecidos = dbQuery {
                val evento = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                    ?: return@dbQuery null
                val huella = huellaDeUnMovimiento(evento.merchant?.takeIf { it.isNotBlank() } ?: evento.description)
                    ?: return@dbQuery emptyList()
                val anulados = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                Events.selectAll()
                    .where { (Events.userId eq uid) and (Events.id neq id) }
                    .orderBy(Events.timestamp to SortOrder.DESC)
                    .filterNot { it[Events.id] in anulados }
                    .filter { it[Events.transferId] == null && it[Events.category] != OPENING_CATEGORY }
                    .map { it.toFinancialEvent() }
                    .filter { otro ->
                        huellaDeUnMovimiento(otro.merchant?.takeIf { it.isNotBlank() } ?: otro.description) == huella
                    }
                    .take(MAXIMO_DE_PARECIDOS)
            }
            if (parecidos == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(parecidos)
        }

        /**
         * **Arreglar uno arregla los parecidos.** Recategoriza varios movimientos de una,
         * normalmente los que propuso `GET /{id}/parecidos` y el dueño confirmó.
         *
         * Cada id se vuelve a validar con las mismas reglas de `PUT /{id}/category`: un lote no es
         * una puerta de atrás a las categorías reservadas. Lo que no se puede mover —una pata de
         * traspaso, una apertura, un anulado, algo de otro usuario— se **omite** y se cuenta, en
         * vez de tumbar el lote entero: el dueño quiso arreglar cinco movimientos, y que uno de
         * ellos resulte ser media transferencia no es motivo para no arreglarle los otros cuatro.
         */
        put("/category-en-lote") {
            val uid = call.userId()
            val body = call.receive<RecategorizarEnLoteRequest>()
            val category = body.category.trim()
            categoriaMalEscrita(category)?.let {
                return@put call.respond(HttpStatusCode.BadRequest, it)
            }
            categoriaQueMoviEscribeSola(category)?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, it)
            }
            val ids = body.ids.distinct()
            if (ids.isEmpty()) {
                return@put call.respond(HttpStatusCode.BadRequest, "No hay movimientos para cambiar")
            }
            if (ids.size > MAXIMO_DE_PARECIDOS) {
                return@put call.respond(HttpStatusCode.BadRequest, "Son demasiados movimientos de una sola vez")
            }
            val resultado = dbQuery {
                val anulados = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                val movibles = Events.selectAll()
                    .where { (Events.userId eq uid) and (Events.id inList ids) }
                    .filterNot { it[Events.id] in anulados }
                    .filter { it[Events.transferId] == null && it[Events.category] != TRANSFER_CATEGORY }
                    .filter { it[Events.category] != OPENING_CATEGORY }
                    .map { it[Events.id] }
                if (movibles.isNotEmpty()) {
                    Events.update({ (Events.userId eq uid) and (Events.id inList movibles) }) {
                        it[Events.category] = category
                    }
                }
                RecategorizarEnLoteResponse(cambiados = movibles, omitidos = ids.size - movibles.size)
            }
            call.respond(resultado)
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
                val periodo = ajustesDelPeriodoSinSuspender(uid)
                val ventana = occurrenceWindow(due, settings = periodo)
                EventOccurrenceMark(
                    ruleId = regla[RecurringRules.id],
                    ruleName = regla[RecurringRules.name],
                    period = period,
                    periodoDelDueno = periodoDelDueno(due, periodo),
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
                val ahora = System.currentTimeMillis()
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
                    // Un pago entre monedas (tarjeta en dólares pagada desde pesos): las dos cifras no
                    // se deducen una de la otra —el tipo de cambio lo puso el banco—, así que corregir
                    // una no puede recalcular la otra. Se rechaza en vez de inventarla.
                    if (hermanas.any { it.currency != fila.currency }) {
                        return@dbQuery ResultadoDeEdicion.Rechazado(RechazoDeEdicion(422, MONTO_DE_UN_PAGO_ENTRE_MONEDAS))
                    }
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
                            // La hermana también queda editada: si no, un reenvío del teléfono
                            // podría devolverle su cifra vieja y partir el par.
                            it[Events.lastEditedAt] = ahora
                        }
                    }
                }
                Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                    if (nuevoMonto != null) it[amount] = nuevoMonto
                    if (nuevaCuenta != null) it[accountId] = nuevaCuenta
                    if (nuevoConcepto != null) it[description] = nuevoConcepto
                    // **La edad de esta corrección** (ver `FinancialEvent.lastEditedAt`). Es la
                    // ruta que más importa de las cinco: el monto, la cuenta y el concepto son
                    // justo lo que el dueño corrige desde la web sobre un movimiento que el
                    // teléfono todavía cree pendiente.
                    it[Events.lastEditedAt] = ahora
                }

                ResultadoDeEdicion.Ok(
                    fila.copy(
                        amount = nuevoMonto ?: fila.amount,
                        accountId = nuevaCuenta ?: fila.accountId,
                        description = nuevoConcepto ?: fila.description,
                        lastEditedAt = ahora,
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
        /**
         * **«Este no se repite» / «sí se repite»**, sobre un movimiento concreto.
         *
         * Movimientos reconoce recurrentes **por nombre**, así que un gasto suelto que se llama
         * igual que una suscripción quedaba marcado como recurrente y no había forma de
         * desmarcarlo: la app solo ofrecía *«Sí, se repite todos los meses»*. Ver
         * [com.jvillada.movi.shared.model.FinancialEvent.noSeRepite].
         *
         * **No toca ninguna regla ni ninguna suscripción**, a propósito: el cobro mensual sigue
         * existiendo y sigue teniendo que aparecer. Esto es una anotación sobre UNA fila.
         *
         * El cuerpo habla en positivo (`repeats`) y la columna en negativo (`no_se_repite`): la
         * traducción vive acá, en un solo lugar, y el mismo endpoint sirve para marcar y para
         * arrepentirse.
         *
         * Un evento anulado se trata como inexistente, igual que en `PUT /{id}/category` y
         * `PUT /{id}/timestamp`: ningún GET lo vuelve a mostrar, así que lo que devolviéramos acá
         * no se vería en ninguna pantalla.
         */
        /**
         * **Confirmar un movimiento que entró solo** —de «Por confirmar» a confirmado—.
         *
         * No existía: un movimiento `UNCONFIRMED` no tenía ninguna salida y quedaba fuera de
         * «Gastos» e «Ingresos» para siempre. Idempotente. Si es una pata de un traspaso se confirma
         * el par entero, dentro de la misma transacción: medio traspaso confirmado contaría la plata
         * de un lado y no del otro. 404 si no existe, es de otro o está anulado.
         */
        put("/{id}/confirm") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val updated: FinancialEvent? = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                val isVoided = event != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (event == null || isVoided) {
                    null
                } else {
                    val par = event.transferId
                    val ahora = System.currentTimeMillis()
                    Events.update({
                        (Events.userId eq uid) and
                            (if (par != null) (Events.transferId eq par) else (Events.id eq id))
                    }) {
                        it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
                        it[Events.lastEditedAt] = ahora
                    }
                    event.copy(reconciliationStatus = ReconciliationStatus.RECONCILED, lastEditedAt = ahora)
                        .withCashFlowFlag(accountTypesFor(uid))
                }
            }
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        put("/{id}/repeats") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val seRepite = call.receive<UpdateEventRepeatsRequest>().repeats

            val updated: FinancialEvent? = dbQuery {
                val event = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toFinancialEvent()
                val isVoided = event != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                if (event == null || isVoided) {
                    null
                } else {
                    val ahora = System.currentTimeMillis()
                    Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                        it[noSeRepite] = !seRepite
                        it[Events.lastEditedAt] = ahora
                    }
                    event.copy(noSeRepite = !seRepite, lastEditedAt = ahora)
                        .withCashFlowFlag(accountTypesFor(uid))
                }
            }
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

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
                    val ahora = System.currentTimeMillis()
                    val idsAfectados: List<String> = if (transferId != null) {
                        Events.update({ (Events.userId eq uid) and (Events.transferId eq transferId) }) {
                            it[timestamp] = nuevo
                            it[Events.lastEditedAt] = ahora
                        }
                        Events.selectAll()
                            .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
                            .map { it[Events.id] }
                    } else {
                        Events.update({ (Events.id eq id) and (Events.userId eq uid) }) {
                            it[timestamp] = nuevo
                            // Sin esto, un reenvío del teléfono con la fecha vieja la devolvía —y
                            // con ella el movimiento a otro mes— sin decir nada.
                            it[Events.lastEditedAt] = ahora
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
                    event.copy(timestamp = nuevo, lastEditedAt = ahora).withCashFlowFlag(accountTypesFor(uid))
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
    val periodo = ajustesDelPeriodoSinSuspender(uid)
    val filas = RecurringOccurrences.selectAll()
        .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId inList eventIds) }
        .map { it[RecurringOccurrences.ruleId] to it[RecurringOccurrences.period] }
    for ((ruleId, period) in filas) {
        val dia = RecurringRules.selectAll()
            .where { (RecurringRules.id eq ruleId) and (RecurringRules.userId eq uid) }
            .firstOrNull()?.get(RecurringRules.dayOfMonth)
        val sigueValiendo = dia != null && runCatching {
            sostieneLaOcurrencia(fecha, occurrenceInMonth(java.time.YearMonth.parse(period), dia), settings = periodo)
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
