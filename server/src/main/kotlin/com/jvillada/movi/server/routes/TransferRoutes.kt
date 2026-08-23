package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TransferResult
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
                false
            }
            if (!inserted) {
                return@post call.respond(HttpStatusCode.Conflict, "Ese traspaso ya está registrado")
            }

            // Las patas se devuelven tal como las construyó :core — ya traen la categoría
            // reservada y `countsAsCashFlow = false`, así que el cliente no tiene que esperar al
            // próximo GET para dejar de contarlas en el mes.
            call.respond(HttpStatusCode.Created, TransferResult(from = fromLeg, to = toLeg))
        }
    }
}
