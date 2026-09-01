package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.DesgloseDeCuota
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.signedDelta
import com.jvillada.movi.shared.model.validarPagoDeCuota
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

private const val MAX_ID = 50

/**
 * Pagar la cuota de un crédito, o el extracto de una tarjeta, en una sola operación.
 *
 * Escribe **dos patas enlazadas** por `transferId`, igual que un traspaso: el EXPENSE que saca la
 * plata de la cuenta y el INCOME que baja la deuda. La diferencia con un traspaso está en las
 * categorías, y el porqué vive en `:core` (ver [CreatePagoDeCuotaRequest]) porque es una regla
 * sobre la plata del dueño, no sobre esta ruta.
 *
 * Sigue el mismo orden de guardas que `POST /api/transfers`, y por los mismos motivos medidos
 * allá: ids antes de tocar la base, 404 por aislamiento, validación de dominio compartida con la
 * app, piso de año, y colisión de `transferId` preguntada ANTES en vez de deducida de una
 * excepción de SQL.
 */
fun Route.pagoDeCuotaRoutes() {
    route("/api/payments/installment") {
        post {
            val uid = call.userId()
            val body = call.receive<CreatePagoDeCuotaRequest>()

            // Los ids se validan antes del INSERT: la columna es varchar(50) y uno vacío o
            // larguísimo explota adentro con una excepción que el catch de abajo confundiría con
            // un reintento.
            listOf(
                "pago" to body.transferId,
                "movimiento de origen" to body.fromEventId,
                "movimiento de la deuda" to body.toEventId,
            ).forEach { (nombre, id) ->
                if (id.isBlank()) return@post call.respond(HttpStatusCode.UnprocessableEntity, "Falta el identificador del $nombre")
                if (id.length > MAX_ID) return@post call.respond(HttpStatusCode.UnprocessableEntity, "El identificador del $nombre es demasiado largo")
            }
            if (body.fromEventId == body.toEventId) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "Las dos patas no pueden compartir el mismo identificador")
            }

            val cuentas = dbQuery {
                val origen = Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.id eq body.fromAccountId) }
                    .firstOrNull()?.toAccount()
                val deuda = Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.id eq body.debtAccountId) }
                    .firstOrNull()?.toAccount()
                origen to deuda
            }
            val from = cuentas.first
            val debt = cuentas.second
            // 404 y no 403 si la cuenta es de otro: mismo criterio de aislamiento que el resto.
            if (from == null || debt == null) {
                return@post call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            }

            // Última línea de defensa: la hoja de Agregar ya apagó el botón con ESTA MISMA
            // función y este mismo texto —vive en `:core` justamente para eso— pero un cliente
            // viejo o un POST a mano no pasan por ahí.
            validarPagoDeCuota(body, from, debt)?.let {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, it)
            }

            // Piso de año, igual que en eventos y traspasos: un epoch roto esconde las dos patas
            // en 1970 y nadie las vuelve a ver.
            if (epochMillisToAppDate(body.timestamp).year !in 2000..2100) {
                return@post call.respond(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
            }

            // ¿Ese id ya tiene dueño? Se pregunta antes en vez de deducirlo de una excepción: un
            // deadlock cae en el mismo `catch` que un reintento, y contestar «ya está registrado»
            // a un deadlock sería un «guardado» sobre la nada.
            val patasExistentes = dbQuery {
                Events.select(Events.id)
                    .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                    .map { it[Events.id] }.toSet()
            }
            if (patasExistentes.isNotEmpty() && patasExistentes != setOf(body.fromEventId, body.toEventId)) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Ese identificador de pago ya lo usa otro movimiento.",
                )
            }

            // ── Cuánto de esta cuota baja de verdad la deuda ─────────────────────────────────
            //
            // **El server recalcula, no le cree al cliente.** La hoja de «Cuota» muestra el mismo
            // desglose antes de guardar (con la MISMA función de `:core`), pero lo hace con el
            // saldo que tenía cargado en pantalla: si el dueño dejó la hoja abierta y en el medio
            // entró un SMS o se ajustó el saldo, ese número llegó viejo. Acá se deriva contra los
            // eventos vivos, que es de donde sale la deuda de verdad.
            //
            // **Se excluyen las patas de ESTE pago**: un reintento con los mismos ids tiene que
            // calcular el mismo interés que el primer intento, no uno sobre la deuda ya bajada.
            //
            // **Y se filtra por moneda**, igual que `computeBalances`, que agrupa por ella: sumar
            // los deltas de todas las monedas daría una cifra que no es de ninguna, y esa cifra
            // entra derecho al cálculo del interés. Hoy no muerde —los créditos del dueño son COP
            // y `validarPagoDeCuota` ya exige que la cuenta y la deuda compartan moneda— pero la
            // guarda cuesta una línea y el error costaría una deuda mal calculada en silencio.
            val saldoAntesDelPago = loadNonVoidedEvents(uid, debt.id)
                .filter { it.transferId != body.transferId && it.currency == debt.currency }
                .sumOf { signedDelta(debt.type, it.type, it.amount) }
            val terms = if (debt.type == AccountType.LOAN) {
                dbQuery {
                    Credits.selectAll()
                        .where { (Credits.userId eq uid) and (Credits.accountId eq debt.id) }
                        .firstOrNull()?.toCreditTerms()
                }
            } else {
                null
            }
            val desglose = desglosarCuota(
                cuota = body.amount,
                tipoDeLaDeuda = debt.type,
                saldoDeLaDeuda = saldoAntesDelPago,
                rateEa = terms?.rateEa,
                seguroMensual = terms?.insuranceMonthly,
            )

            val (pataDelDinero, pataDeLaDeuda) = pagoDeCuotaLegs(body, from, debt, desglose)

            // Las dos inserciones en UN solo dbQuery = una sola transacción: si la segunda choca,
            // la primera se va con ella. Media operación acá sería plata que salió de la cuenta
            // sin bajar ninguna deuda.
            val ok = try {
                dbQuery {
                    insertEventRow(uid, pataDelDinero)
                    insertEventRow(uid, pataDeLaDeuda)
                }
                true
            } catch (e: ExposedSQLException) {
                call.application.environment.log.warn("[pago-cuota] INSERT falló para ${body.transferId}", e)
                false
            }

            if (!ok) {
                // El reintento de verdad —el dedo que volvió a tocar Guardar— manda los mismos
                // tres ids. Si las dos patas ya están, el pago YA ocurrió: se contesta 200 con lo
                // que de verdad quedó guardado, no un conflicto seco.
                val ahora = dbQuery {
                    Events.select(Events.id)
                        .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                        .map { it[Events.id] }.toSet()
                }
                // Si las patas NO están, el INSERT falló de verdad. **500 y no 422**: un 422 la
                // app lo lee como «tu pago está mal», y no lo está — falló el server.
                if (ahora != setOf(body.fromEventId, body.toEventId)) {
                    return@post call.respond(HttpStatusCode.InternalServerError, "No se pudo registrar el pago. Inténtalo de nuevo.")
                }
            }

            // **Se releen las patas GUARDADAS, no se devuelven las construidas.**
            //
            // Escenario que encontró la revisión: el dueño escribe 4.215.223, toca Guardar, el
            // server commitea y la respuesta se pierde. Ve el error, se da cuenta de que el monto
            // estaba mal, lo corrige a 4.500.000 y vuelve a tocar Guardar — `save()` no renueva
            // los ids ante un fallo. El server rechaza por clave repetida, relee, los ids
            // coinciden, y responde 200. Devolviendo las patas construidas, la respuesta afirmaba
            // 4.500.000 sobre un pago de 4.215.223.
            val guardadas = dbQuery {
                Events.selectAll()
                    .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                    .map { it.toFinancialEvent() }
            }

            // Se devuelve la deuda como quedó: es el número que el dueño vino a ver bajar.
            // `loadNonVoidedEvents` ya abre su propia transacción: envolverla en otra la anida.
            val eventos = loadNonVoidedEvents(uid, debt.id)
            call.respond(
                if (ok) HttpStatusCode.Created else HttpStatusCode.OK,
                PagoDeCuotaResult(
                    // Por moneda, igual que `saldoAntesDelPago` y que `computeBalances`: la deuda
                    // que se le muestra al dueño es la de ESTA moneda, no una suma de varias.
                    deudaRestante = eventos
                        .filter { it.currency == debt.currency }
                        .sumOf { signedDelta(debt.type, it.type, it.amount) },
                    patas = guardadas,
                    // **El desglose de las patas GUARDADAS, no el que se acaba de calcular**, por
                    // el mismo motivo por el que las patas se releen: en el camino del reintento
                    // las guardadas pueden ser de un pago anterior con otro monto, y devolver el
                    // desglose recién calculado afirmaría un reparto que no es el que quedó
                    // escrito. Se reconstruye de lo que hay en la base — el capital ES la pata de
                    // la deuda, y la cuota ES la del dinero.
                    desglose = desgloseDeLoGuardado(guardadas, debt.id, desglose),
                ),
            )
        }
    }
}

/**
 * El desglose que corresponde a **las patas que de verdad quedaron en la base**.
 *
 * En el camino feliz es exactamente [calculado]. Existe para el otro camino: el reintento cuyo
 * INSERT chocó porque las patas ya estaban de un intento anterior (posiblemente con otro monto, ver
 * el comentario de la relectura). Ahí, devolver [calculado] afirmaría un reparto que no es el que
 * quedó escrito — el mismo error que la relectura de las patas vino a cerrar, por la otra puerta.
 *
 * `interes` y `seguro` no se pueden separar mirando las filas —el par guarda su suma, no cada uno—
 * así que se devuelven como un solo bloque en `interes` y `seguro` en 0. Es honesto: la cifra que
 * el dueño va a comparar es cuánto bajó la deuda, y esa sale exacta.
 */
private fun desgloseDeLoGuardado(
    guardadas: List<FinancialEvent>,
    debtAccountId: String,
    calculado: DesgloseDeCuota,
): DesgloseDeCuota {
    val pataDeLaDeuda = guardadas.firstOrNull { it.accountId == debtAccountId }
    val pataDelDinero = guardadas.firstOrNull { it.accountId != debtAccountId }
    if (pataDeLaDeuda == null || pataDelDinero == null) return calculado
    if (pataDelDinero.amount == calculado.cuota && pataDeLaDeuda.amount == calculado.capital) return calculado
    return DesgloseDeCuota(
        cuota = pataDelDinero.amount,
        interes = pataDelDinero.amount - pataDeLaDeuda.amount,
        seguro = 0L,
        capital = pataDeLaDeuda.amount,
        motivo = calculado.motivo,
    )
}
