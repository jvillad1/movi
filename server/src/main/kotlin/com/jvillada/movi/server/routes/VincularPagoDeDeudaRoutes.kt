package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.cargosYaCobradosEnElMes
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.fx.convertirEntreMonedas
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.DesgloseDeCuota
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VINCULO_CATEGORIA_INVALIDA
import com.jvillada.movi.shared.model.VINCULO_SIN_TASA
import com.jvillada.movi.shared.model.VINCULO_YA_ES_TRASPASO
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import com.jvillada.movi.shared.model.conPuntosDeMiles
import com.jvillada.movi.shared.model.desglosarCuotaRegistrada
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.signedDelta
import com.jvillada.movi.shared.model.validarInteresReal
import com.jvillada.movi.shared.model.validarPagoDeCuota
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.math.roundToLong
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private const val MAX_ID_LEN = 50

/**
 * # Ola Y — «¿A cuál crédito o tarjeta corresponde?» arma el traspaso solo
 *
 * `PUT /api/events/{id}/vincular-deuda` completa un gasto suelto YA categorizado como
 * [CUOTA_CATEGORY] o [CARD_PAYMENT_CATEGORY] en el traspaso de dos patas que `PagosDeDeuda.kt`
 * exige para marcar la deuda pagada: una pata que sale de la cuenta del dueño (`{id}`, que ya
 * existe) y otra que ENTRA a la cuenta de la deuda (nueva, [VincularPagoDeDeudaRequest.toEventId]).
 *
 * Descubierto en vivo el 2026-09-27, cuatro veces el mismo día (Crédito Mamá, Hipoteca Papá,
 * Libranza Papá, y las dos Master Black): el dueño confirmaba el SMS con la categoría correcta y
 * el movimiento quedaba como gasto suelto — la deuda nunca bajaba, y había que corregirlo a mano
 * por SQL cada vez.
 *
 * Reusa [pagoDeCuotaLegs] — la misma función que arma un pago de cuota anotado a mano — para
 * decidir cuánto de este pago baja capital y cuánto se va en intereses, seguro y otros cargos.
 * Ningún número de plata se recalcula con una fórmula nueva; ver el KDoc de esa función y de
 * [DesgloseDeCuota]. Mismo orden de guardas y mismo criterio de reintento idempotente que
 * `PUT /api/payments/installment` (`PagoDeCuotaRoutes.kt`), del que esta ruta es hermana: la
 * diferencia de fondo es que acá la pata del dinero YA EXISTE, así que se actualiza en vez de
 * insertarse.
 *
 * ### Qué cambia y qué no del movimiento `{id}`
 *
 * Solo tres columnas: `category` (por si la cuenta elegida decide otra categoría — pagar con lo
 * que se anotó como [CUOTA_CATEGORY] una TARJETA cuenta como [CARD_PAYMENT_CATEGORY], porque es
 * [pagoDeCuotaLegs] quien decide la categoría según el TIPO de cuenta, no la categoría que el
 * dueño ya había elegido), `description` (le antepone «Cuota de X»/«Pago de X», conservando el
 * texto que ya tenía como nota) y `transferId`. El monto, la fecha, la fuente
 * (SMS/notificación/manual) y el estado de conciliación del movimiento **no se tocan**: sigue
 * siendo el mismo movimiento, ahora con su pareja.
 *
 * ### La conversión de moneda, automática y solo para tarjetas
 *
 * Pagar una tarjeta en otra moneda que la cuenta de origen (pesos pagando una Master Black en
 * dólares, el caso real de hoy) no le pide una segunda cifra al dueño —a diferencia del pago
 * manual, [CreatePagoDeCuotaRequest.montoEnLaMonedaDeLaDeuda]—: acá se pide la TRM del día a
 * [FxRateService] y se convierte sola. Ver [convertirEntreMonedas] para por qué una tasa de
 * respaldo (la constante $4.000, ver [com.jvillada.movi.server.fx.TasaUsdCop]) cuenta como «no se
 * pudo» y no como una conversión válida — mejor dejar el pago como gasto suelto que escribir una
 * deuda equivocada con pinta de exacta.
 *
 * Un crédito (LOAN) en otra moneda que la cuenta de origen no se convierte —ni acá ni en el pago
 * manual—: [validarPagoDeCuota] lo rechaza con el mismo mensaje de siempre,
 * `PAGO_MONEDAS_DISTINTAS`.
 */
fun Route.vincularPagoDeDeudaRoutes() {
    route("/api/events/{id}/vincular-deuda") {
        put {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val body = call.receive<VincularPagoDeDeudaRequest>()

            listOf(
                "pago" to body.transferId,
                "movimiento de la deuda" to body.toEventId,
            ).forEach { (nombre, valorId) ->
                if (valorId.isBlank()) return@put call.respond(HttpStatusCode.UnprocessableEntity, "Falta el identificador del $nombre")
                if (valorId.length > MAX_ID_LEN) return@put call.respond(HttpStatusCode.UnprocessableEntity, "El identificador del $nombre es demasiado largo")
            }
            if (body.toEventId == id) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Las dos patas no pueden compartir el mismo identificador")
            }

            val cargado = dbQuery {
                val fila = Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()
                val evento = fila?.toFinancialEvent()
                val voided = evento != null && VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
                    .count() > 0
                val origen = evento?.let {
                    Accounts.selectAll()
                        .where { (Accounts.userId eq uid) and (Accounts.id eq it.accountId) }
                        .firstOrNull()?.toAccount()
                }
                val deuda = Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.id eq body.debtAccountId) }
                    .firstOrNull()?.toAccount()
                Triple(evento?.takeIf { !voided }, origen, deuda)
            }
            val evento = cargado.first
            val fromAccount = cargado.second
            val debtAccount = cargado.third
            if (evento == null) {
                return@put call.respond(HttpStatusCode.NotFound, "Movimiento no encontrado")
            }
            if (fromAccount == null || debtAccount == null) {
                return@put call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            }

            // Un reintento de verdad trae el MISMO transferId que ya quedó escrito la vez
            // anterior — eso lo distingue la colisión de más abajo. Cualquier OTRO transferId no
            // nulo es un movimiento que ya es la mitad de OTRO traspaso, y ese sí se rechaza acá.
            if (evento.transferId != null && evento.transferId != body.transferId) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, VINCULO_YA_ES_TRASPASO)
            }
            if (evento.type != TransactionType.EXPENSE || (evento.category != CUOTA_CATEGORY && evento.category != CARD_PAYMENT_CATEGORY)) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, VINCULO_CATEGORIA_INVALIDA)
            }

            // ¿Ese id de pago ya tiene dueño? Se pregunta ANTES, igual que en el pago manual: un
            // deadlock cae en el mismo catch que un reintento, y contestar "colisión" a un
            // deadlock sería un "no se pudo" sobre la nada.
            val patasExistentes = dbQuery {
                Events.select(Events.id)
                    .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                    .map { it[Events.id] }.toSet()
            }
            if (patasExistentes.isNotEmpty() && patasExistentes != setOf(evento.id, body.toEventId)) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Ese identificador de pago ya lo usa otro movimiento.")
            }

            // ── La conversión de moneda, si hace falta ───────────────────────────────────────
            var montoConvertido: Long? = null
            var notaTrm: String? = null
            if (fromAccount.currency != debtAccount.currency && debtAccount.type == AccountType.CREDIT_CARD) {
                val tasa = FxRateService.tasaUsdCop()
                montoConvertido = convertirEntreMonedas(evento.amount, fromAccount.currency, debtAccount.currency, tasa)
                if (montoConvertido == null) {
                    return@put call.respond(HttpStatusCode.UnprocessableEntity, VINCULO_SIN_TASA)
                }
                notaTrm = "a TRM $" + formatearTasa(tasa.valor)
            }

            val nota = listOfNotNull(evento.description.trim().ifBlank { null }, notaTrm)
                .joinToString(" · ")
                .ifBlank { null }
            val pseudoRequest = CreatePagoDeCuotaRequest(
                fromAccountId = fromAccount.id,
                debtAccountId = debtAccount.id,
                amount = evento.amount,
                timestamp = evento.timestamp,
                note = nota,
                transferId = body.transferId,
                fromEventId = evento.id,
                toEventId = body.toEventId,
                montoEnLaMonedaDeLaDeuda = montoConvertido,
                interesReal = body.interesReal,
            )
            validarPagoDeCuota(pseudoRequest, fromAccount, debtAccount)?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, it)
            }

            // ── Cuánto de este pago baja de verdad la deuda — MISMA cuenta que el pago manual ──
            // Se recalcula siempre, incluso en un reintento: es barato (son lecturas) y así la
            // respuesta describe lo mismo que la primera vez, sin una segunda rama de código.
            val saldoAntesDelPago = loadNonVoidedEvents(uid, debtAccount.id)
                .filter { it.transferId != body.transferId && it.currency == debtAccount.currency }
                .sumOf { signedDelta(debtAccount.type, it.type, it.amount) }
            val terms = if (debtAccount.type == AccountType.LOAN) {
                dbQuery {
                    Credits.selectAll()
                        .where { (Credits.userId eq uid) and (Credits.accountId eq debtAccount.id) }
                        .firstOrNull()?.toCreditTerms()
                }
            } else {
                null
            }
            val yaCobradoEnElMes = run {
                val delMes = loadNonVoidedEvents(uid, debtAccount.id)
                    .filter { it.transferId != body.transferId && it.currency == debtAccount.currency && it.noAmortiza != null }
                val pares = delMes.mapNotNull { it.transferId }.toSet()
                val pagadoPorPar = if (pares.isEmpty()) emptyMap() else dbQuery {
                    Events.selectAll()
                        .where { (Events.userId eq uid) and (Events.transferId inList pares) and (Events.accountId neq debtAccount.id) }
                        .associate { it[Events.transferId]!! to it[Events.amount] }
                }
                cargosYaCobradosEnElMes(delMes, epochMillisToAppDate(evento.timestamp), terms?.dayOfMonth) { fila ->
                    fila.transferId?.let { pagadoPorPar[it] }
                }
            }
            val cuota = if (fromAccount.currency != debtAccount.currency) montoConvertido ?: evento.amount else evento.amount
            validarInteresReal(
                body.interesReal, cuota, debtAccount.type,
                terms?.insuranceMonthly, terms?.otrosCargosMensuales, yaCobradoEnElMes,
            )?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, it)
            }
            val desglose = desglosarCuotaRegistrada(
                cuota = cuota,
                tipoDeLaDeuda = debtAccount.type,
                saldoDeLaDeuda = saldoAntesDelPago,
                rateEa = terms?.rateEa,
                seguroMensual = terms?.insuranceMonthly,
                otrosCargosMensuales = terms?.otrosCargosMensuales,
                sinIntereses = terms?.sinIntereses ?: false,
                interesReal = body.interesReal,
                yaCobradoEnElMes = yaCobradoEnElMes,
            )
            val (pataDelDinero, pataDeLaDeuda) = pagoDeCuotaLegs(pseudoRequest, fromAccount, debtAccount, desglose)

            // La pata del dinero YA EXISTE: se ACTUALIZA, no se inserta. Las dos escrituras
            // viven en la MISMA transacción: si la segunda choca, la primera se va con ella —
            // media operación acá dejaría la categoría cambiada sin que la deuda bajara nada.
            val ok = try {
                dbQuery {
                    val ahora = System.currentTimeMillis()
                    Events.update({ (Events.id eq evento.id) and (Events.userId eq uid) }) {
                        it[Events.category] = pataDelDinero.category
                        it[Events.description] = pataDelDinero.description
                        it[Events.transferId] = pataDelDinero.transferId
                        it[Events.lastEditedAt] = ahora
                    }
                    insertEventRow(uid, pataDeLaDeuda)
                }
                true
            } catch (e: ExposedSQLException) {
                call.application.environment.log.warn("[vincular-deuda] escritura falló para ${body.transferId}", e)
                false
            }

            if (!ok) {
                // El reintento de verdad manda los mismos ids. Si las dos patas ya están, el
                // vínculo YA ocurrió: se contesta 200 con lo que de verdad quedó guardado.
                val ahoraExistentes = dbQuery {
                    Events.select(Events.id)
                        .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                        .map { it[Events.id] }.toSet()
                }
                if (ahoraExistentes != setOf(evento.id, body.toEventId)) {
                    return@put call.respond(HttpStatusCode.InternalServerError, "No se pudo vincular el pago. Inténtalo de nuevo.")
                }
            }

            val guardadas = dbQuery {
                Events.selectAll()
                    .where { (Events.userId eq uid) and (Events.transferId eq body.transferId) }
                    .map { it.toFinancialEvent() }
            }
            val eventos = loadNonVoidedEvents(uid, debtAccount.id)
            call.respond(
                if (ok) HttpStatusCode.OK else HttpStatusCode.OK,
                PagoDeCuotaResult(
                    deudaRestante = eventos
                        .filter { it.currency == debtAccount.currency }
                        .sumOf { signedDelta(debtAccount.type, it.type, it.amount) },
                    patas = guardadas,
                    desglose = desgloseDeLoVinculado(guardadas, debtAccount.id, desglose),
                ),
            )
        }
    }
}

/** «4012.5» → «4.012,50»: la TRM del día, para la descripción de la pata convertida. */
private fun formatearTasa(valor: Double): String {
    val entero = valor.toLong()
    val centavos = ((valor - entero) * 100).roundToLong().coerceAtLeast(0L)
    val enteroFormateado = conPuntosDeMiles(entero)
    return if (centavos == 0L) enteroFormateado else "$enteroFormateado,${centavos.toString().padStart(2, '0')}"
}

/**
 * El desglose que corresponde a las patas que de verdad quedaron en la base. En el camino feliz
 * es exactamente [calculado]; existe para el reintento cuya escritura chocó porque las patas ya
 * estaban de un intento anterior (posiblemente con otro monto en el medio) — mismo criterio que
 * `desgloseDeLoGuardado` en `PagoDeCuotaRoutes.kt`, del que esta es la copia local: vive acá y no
 * se comparte porque es puro formato de respuesta, no una regla de plata.
 */
private fun desgloseDeLoVinculado(
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
        otrosCargos = 0L,
        capital = pataDeLaDeuda.amount,
        motivo = calculado.motivo,
    )
}
