package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TRANSFER_ID_ALREADY_USED
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.model.validateTransfer
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.Transaction

/**
 * `POST /api/transfers` — mover plata entre dos cuentas propias.
 *
 * **Un solo endpoint, una sola transacción.** Un traspaso son dos eventos (EXPENSE en el origen,
 * INCOME en el destino, enlazados por `transferId`; ver
 * [com.jvillada.movi.shared.model.transferLegsFor]), y los dos tienen que existir o ninguno: con
 * dos `POST /api/events` sueltos, un corte de red entre medio dejaba plata saliendo de una cuenta
 * sin entrar a ninguna — un saldo mintiendo, en silencio y para siempre. Mismo precedente que
 * `POST /api/credits`, que crea cuenta + apertura + términos de una sola vez.
 *
 * **Los ids los trae el cliente** (los tres: el del traspaso y el de cada pata), igual que ya
 * hace para un evento suelto. Así el traspaso tiene identidad desde antes de tocar ninguna base
 * y el espejo local puede escribir exactamente las mismas filas.
 *
 * Lo que este endpoint NO hace: convertir el pago de una tarjeta en traspaso. Eso ya tiene su
 * propio camino y su propia regla de flujo de caja ([com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY]),
 * y por eso [validateTransfer] rechaza cualquier cuenta del grupo DEUDA de los dos lados.
 */
fun Route.transferRoutes() {
    route("/api/transfers") {
        post {
            val uid = call.userId()
            val body = call.receive<CreateTransferRequest>()

            // Los ids se validan ANTES de tocar la base: la columna es varchar(50) y un id vacío
            // o larguísimo explota adentro del INSERT con una ExposedSQLException que el catch de
            // más abajo reportaba, sin distinguir, como «Ese traspaso ya está registrado» — un
            // mensaje que manda al dueño a buscar un traspaso que nunca existió.
            invalidIdMessage(body)?.let { motivo ->
                return@post call.respond(HttpStatusCode.UnprocessableEntity, motivo)
            }

            val accounts = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.id eq body.fromAccountId) }
                    .firstOrNull()?.toAccount() to
                    Accounts.selectAll()
                        .where { (Accounts.userId eq uid) and (Accounts.id eq body.toAccountId) }
                        .firstOrNull()?.toAccount()
            }
            val (from, to) = accounts
            // 404 y no 403 si la cuenta es de otro usuario: mismo criterio de aislamiento que
            // el resto de las rutas — para este usuario esa cuenta sencillamente no existe.
            if (from == null || to == null) {
                return@post call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            }

            // Última línea de defensa: la hoja de Agregar ya apagó el botón con esta misma
            // función y este mismo texto (vive en :core justamente para eso), pero un cliente
            // viejo o un POST a mano no pasan por ahí.
            validateTransfer(from, to, body.amount)?.let { motivo ->
                return@post call.respond(HttpStatusCode.UnprocessableEntity, motivo)
            }

            // ¿Ese `transferId` ya tiene dueño? El esquema impide que un traspaso termine con
            // tres o cuatro patas (índice único (user_id, transfer_id, type), ver
            // `Migrations.createUniqueTransferLegIndex`), pero un índice solo sabe decir "no" con
            // una excepción de SQL, y acá abajo esa excepción se interpreta como reintento y se
            // responde 200 con las patas que ya estaban — o sea, "guardado" sobre un traspaso que
            // no es el que pidieron. Así que se pregunta antes, y se dice lo que pasa.
            //
            // El reintento de verdad —el dedo que volvió a tocar Guardar— manda EXACTAMENTE los
            // mismos tres ids: ese caso cae por el `else` y sigue su camino de siempre.
            val patasExistentes = dbQuery {
                Events.select(Events.id)
                    .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                    .map { it[Events.id] }
                    .toSet()
            }
            if (patasExistentes.isNotEmpty() && patasExistentes != setOf(body.fromEventId, body.toEventId)) {
                println("[transfers] transferId=${body.transferId} ya lo usan otras patas: $patasExistentes")
                return@post call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_ID_ALREADY_USED)
            }

            val (fromLeg, toLeg) = transferLegsFor(body, from, to)

            // Las dos inserciones adentro de un solo dbQuery = una sola transacción: si la
            // segunda choca (un id repetido, p. ej. un reintento del cliente), la primera se va
            // con ella. El test «si una pata falla no queda ni la otra» lo blinda.
            val inserted = try {
                dbQuery {
                    insertEventRow(uid, fromLeg)
                    insertEventRow(uid, toLeg)
                }
                true
            } catch (e: ExposedSQLException) {
                println("[transfers] INSERT falló para transferId=${body.transferId}: ${e.message}")
                false
            }

            if (!inserted) {
                // El INSERT chocó. **No se asume por qué.** El caso probable, con los ids ya
                // validados arriba, es el reintento del dedo: el server commiteó, la respuesta se
                // perdió y el dueño volvió a tocar Guardar. Pero un deadlock, una conexión caída o
                // un serialization failure caen exactamente en el mismo `catch`, y responderles
                // «ya está registrado» era mentir — más ahora que el cliente trata esa respuesta
                // como éxito: se convertía en un «guardado» sobre la nada.
                //
                // Así que se pregunta en vez de suponer. Si las dos patas están, el traspaso YA
                // ocurrió y esto es idempotencia de verdad: se devuelven las patas reales (200),
                // no un conflicto seco — el cliente las necesita para espejarlas en su DB local,
                // que es de donde leen Movimientos, Cuentas y el detalle en el teléfono.
                //
                // Y las patas que están tienen que ser LAS DEL BODY, no dos cualesquiera con ese
                // `transferId`. La puerta de más arriba ya rechaza reusar un id ajeno, pero eso es
                // un check-then-act en dos transacciones: si dos POST distintos con el mismo id
                // corren a la vez —o si el índice único nunca llegó a crearse por datos previos en
                // conflicto (ver `Migrations.createUniqueTransferLegIndex`)— el perdedor podría
                // leer acá las patas del ganador y responderlas como propias. Sería exactamente el
                // «200 con el traspaso de otro» que la puerta vino a matar, así que se comprueba
                // otra vez, ahora sobre lo que de verdad quedó guardado. Defensa sobre defensa:
                // desde la app real no hay forma de llegar acá.
                val existentes = dbQuery { transferLegsIn(uid, body.transferId) }
                if (existentes != null &&
                    setOf(existentes.from.id, existentes.to.id) == setOf(body.fromEventId, body.toEventId)
                ) {
                    return@post call.respond(HttpStatusCode.OK, existentes)
                }
                if (existentes != null) {
                    println(
                        "[transfers] transferId=${body.transferId} quedó con patas ajenas " +
                            "(${existentes.from.id}, ${existentes.to.id}); no se responde como idempotente",
                    )
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, TRANSFER_ID_ALREADY_USED)
                }
                // No están las dos: el traspaso no quedó. Es un error genuino, y se dice como tal.
                println("[transfers] el traspaso ${body.transferId} no quedó registrado tras el fallo del INSERT")
                return@post call.respond(HttpStatusCode.InternalServerError, "No se pudo registrar el traspaso")
            }

            // Las patas se devuelven tal como las construyó :core — ya traen la categoría
            // reservada y `countsAsCashFlow = false`, así que el cliente no tiene que esperar al
            // próximo GET para dejar de contarlas en el mes.
            call.respond(HttpStatusCode.Created, TransferResult(from = fromLeg, to = toLeg))
        }
    }
}

/** Largo máximo de `financial_events.id` y de `transfer_id` (ver `Tables.kt`). */
private const val MAX_ID_LENGTH = 50

/**
 * ¿Alguno de los tres ids que mandó el cliente no sirve? Devuelve el motivo; `null` si están bien.
 *
 * En blanco no vale (una fila sin identidad, y del lado del espejo local `INSERT OR REPLACE`
 * haría que una pata pisara a la otra) y más largo que la columna tampoco. Sin esta validación
 * los dos casos terminaban en el `catch` de abajo y salían como «Ese traspaso ya está
 * registrado»: un mensaje que manda al dueño a buscar un traspaso que nunca existió.
 */
private fun invalidIdMessage(body: CreateTransferRequest): String? {
    val ids = listOf(
        "traspaso" to body.transferId,
        "movimiento de origen" to body.fromEventId,
        "movimiento de destino" to body.toEventId,
    )
    ids.forEach { (nombre, id) ->
        if (id.isBlank()) return "Falta el identificador del $nombre"
        if (id.length > MAX_ID_LENGTH) return "El identificador del $nombre es demasiado largo"
    }
    if (body.fromEventId == body.toEventId) {
        return "Las dos patas del traspaso no pueden compartir el mismo identificador"
    }
    return null
}

/**
 * Las dos patas ya guardadas de [transferId], o `null` si no están las dos.
 *
 * "Las dos" es la condición, no "alguna": media transferencia no es un traspaso registrado, y
 * devolverla como si lo fuera sería justo el saldo mintiendo que toda esta feature evita. La de
 * origen es el EXPENSE y la de destino el INCOME — el mismo criterio con el que
 * [transferLegsFor] las construye.
 */
private fun Transaction.transferLegsIn(uid: String, transferId: String): TransferResult? {
    val patas = Events.selectAll()
        .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
        .map { it.toFinancialEvent() }
    if (patas.size != 2) return null
    val from = patas.firstOrNull { it.type == TransactionType.EXPENSE } ?: return null
    val to = patas.firstOrNull { it.type == TransactionType.INCOME } ?: return null
    return TransferResult(from = from, to = to)
}
