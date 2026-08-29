package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.debtAdjustmentEventFor
import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AdjustCreditBalanceRequest
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DISBURSEMENT_WITH_INITIAL_DEBT
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MAX_CREDIT_DEBT_COP
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.aperturaDeCreditoDesembolsado
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.openingEventFor
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.model.validateCreditDisbursement
import com.jvillada.movi.server.time.appDateToEpochMillis
import java.time.LocalDate
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.vendors.ForUpdateOption

/**
 * Medio día en milisegundos. El desembolso se sella al mediodía de la zona de la app y no a la
 * medianoche, por el mismo motivo que `epochAlMediodia` en el cliente: la medianoche está a un
 * desfase de distancia de caer en el día anterior, y ahí el movimiento aparece un día antes de
 * lo que dice el crédito.
 */
private const val MEDIODIA_MILLIS = 12L * 60 * 60 * 1000

fun Route.creditRoutes() {
    route("/api/credits") {
        get {
            val uid = call.userId()
            val loans = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.LOAN.name) }
                    .map { it.toAccount() }
            }
            if (loans.isEmpty()) return@get call.respond(emptyList<CreditSummary>())
            val rate = FxRateService.usdToCop()
            val termsByAccount = dbQuery {
                Credits.selectAll().where { Credits.userId eq uid }
                    .associate { it[Credits.accountId] to it.toCreditTerms() }
            }
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            call.respond(loans.map { acc ->
                summaryFor(acc, termsByAccount[acc.id], eventsByAccount[acc.id] ?: emptyList(), rate)
            })
        }

        // Alta atómica: cuenta LOAN + evento de apertura + términos en UNA transacción.
        // Evita el flujo cliente en dos pasos (crear cuenta y luego PUT términos), que
        // dejaba cuentas huérfanas/duplicadas ante fallos parciales y no funcionaba en
        // móvil (LocalRepository crea cuentas solo localmente).
        post {
            val uid = call.userId()
            val body = call.receive<CreateCreditRequest>()
            val name = body.name.trim()
            if (name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Nombre de cuenta requerido")
            // Ola 14 — la deuda inicial puede ser CERO, y eso es lo que hace usable el desembolso.
            //
            // Antes el mínimo era 1: había que declarar la deuda al crear el crédito. Para un
            // crédito viejo eso está bien (es la foto de lo que ya se debía cuando entró a Movi),
            // pero para uno que **acaban de desembolsar** era una trampa: la deuda quedaba
            // registrada por la apertura y, si además se anotaba el desembolso como traspaso —que
            // es lo que pone la plata en la cuenta corriente—, la misma deuda quedaba contada dos
            // veces. Los $257.000.000 de la libranza se veían como $514.000.000.
            //
            // Con cero permitido las dos formas son limpias y no se pisan: un crédito viejo se
            // crea con lo que se debe hoy y no lleva traspaso; uno recién desembolsado se crea en
            // $0 y la deuda la crea el desembolso, junto con la plata que entró. Un `initialDebt`
            // de 0 no genera evento de apertura (ver `openingEventFor`), así que la cuenta arranca
            // sin ninguna fila que después haya que corregir. Mismo criterio que ya usaba
            // `POST /api/cards`, que acepta 0 desde siempre.
            if (body.initialDebt < 0L) return@post call.respond(HttpStatusCode.BadRequest, "La deuda no puede ser negativa")

            // ── Ola 16 — el desembolso nace CON el crédito, o no nace ─────────────────────
            //
            // La ola 14 dejó las dos formas posibles pero le pedía al dueño entender el
            // mecanismo: «si te acaban de desembolsar, deja la deuda en blanco y después anota
            // el traspaso». Dos pasos, y el intermedio miente: un crédito de $257.000.000 en $0
            // que la tarjeta anunciaba como «100% pagado». Ahora la hoja PREGUNTA («¿acabas de
            // recibir esta plata?») y, si la respuesta es que sí, el desembolso llega acá en el
            // mismo cuerpo y se escribe en la MISMA transacción. La ventana desapareció.
            //
            // El KDoc de `CreateCreditRequest.disbursement` tiene el porqué de las dos reglas
            // que se validan a continuación; acá va solo lo que hace falta para leer el código.
            val disbursement = body.disbursement
            if (disbursement != null && body.initialDebt != 0L) {
                return@post call.respond(HttpStatusCode.BadRequest, DISBURSEMENT_WITH_INITIAL_DEBT)
            }
            // La cuenta destino se lee ANTES de escribir nada: si no existe (o es de otro
            // usuario) no se crea el crédito tampoco. Un crédito a medias es exactamente lo que
            // esta rama vino a evitar; devolver 404 con la base intacta es la única respuesta
            // honesta. 404 y no 403, mismo criterio de aislamiento que el resto de las rutas.
            val destino = if (disbursement == null) null else dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq disbursement.toAccountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            }
            if (disbursement != null && destino == null) {
                return@post call.respond(HttpStatusCode.NotFound, "Cuenta no encontrada")
            }
            // Última línea de defensa: la hoja ya apagó el botón con esta misma función y este
            // mismo texto (vive en :core justamente para eso). Un cliente viejo o un POST a mano
            // no pasan por ahí.
            if (disbursement != null) {
                validateCreditDisbursement(body.terms.principal, destino, disbursement.amount)?.let { motivo ->
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, motivo)
                }
            }
            // La fecha del desembolso es `startDate`, el campo que la hoja YA pide («Desembolso
            // AAAA-MM-DD»): preguntarla dos veces sería preguntar dos veces lo mismo, y usar
            // «hoy» pondría el movimiento en un día en que no pasó nada. Se sella al mediodía de
            // la zona de la app, igual que cualquier movimiento fechado en otro día
            // (`epochAlMediodia`): la medianoche se corre de día con cualquier desfase.
            val desembolsoMillis = if (disbursement == null) null else {
                val fecha = runCatching { LocalDate.parse(body.terms.startDate.trim()) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "La fecha de desembolso tiene que ser AAAA-MM-DD")
                // Misma guarda de cordura de año que POST /api/events y POST /api/transfers: un
                // desembolso fechado en 1970 se esconde al fondo de Movimientos y no se ve nunca más.
                if (fecha.year !in 2000..2100) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
                }
                appDateToEpochMillis(fecha) + MEDIODIA_MILLIS
            }

            // La apertura NO es `initialDebt` cuando hay desembolso: es el pedazo del capital que
            // nunca se volvió plata (costos financiados). Sumada a la pata del desembolso, la
            // deuda arranca valiendo exactamente el capital. Ver `aperturaDeCreditoDesembolsado`.
            val deudaDeApertura = if (disbursement == null) body.initialDebt
                else aperturaDeCreditoDesembolsado(body.terms.principal, disbursement.amount)
            val accountId = "acc_${System.currentTimeMillis()}"
            val cuentaAlAbrir = Account(
                id       = accountId,
                name     = name,
                type     = AccountType.LOAN,
                balance  = deudaDeApertura,
                currency = "COP",
            )
            val terms = body.terms
                .copy(accountId = accountId)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
            val opening = openingEventFor(cuentaAlAbrir, now = System.currentTimeMillis())
            // Las patas se construyen con `transferLegsFor`, la MISMA función que usa
            // `POST /api/transfers` (vive en :core justamente para eso): misma categoría
            // reservada, mismo `countsAsCashFlow = false`, y el encabezado «Desembolso a
            // Bancolombia» / «Desembolso desde Libranza» que ya sabe poner cuando una punta es un
            // préstamo. Un desembolso no es un ingreso, y eso lo garantiza esa función, no esta.
            val legs = if (disbursement == null || destino == null || desembolsoMillis == null) null else
                transferLegsFor(
                    CreateTransferRequest(
                        transferId    = newId("tr"),
                        fromEventId   = newId("ev"),
                        toEventId     = newId("ev"),
                        fromAccountId = accountId,
                        toAccountId   = destino.id,
                        amount        = disbursement.amount,
                        timestamp     = desembolsoMillis,
                    ),
                    from = cuentaAlAbrir,
                    to   = destino,
                )
            // El saldo que se escribe en la fila `accounts` incluye las dos cosas: la apertura y
            // el desembolso. (El GET no lo lee —la deuda se deriva de los eventos, ver
            // `enrichWith`— pero el espejo local del teléfono sí, y ahí tiene que estar completo.)
            val account = cuentaAlAbrir.copy(balance = deudaDeApertura + (disbursement?.amount ?: 0L))

            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[Accounts.name] = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
                if (opening != null) insertEventRow(uid, opening)
                if (legs != null) {
                    insertEventRow(uid, legs.first)
                    insertEventRow(uid, legs.second)
                }
                Credits.insert { fillTerms(it, uid, terms) }
            }
            // Solo la pata del PRÉSTAMO entra al cálculo del resumen: `summaryFor` deriva el
            // saldo de ESTA cuenta, y la otra pata es de la cuenta corriente.
            val eventosDelCredito = listOfNotNull(opening, legs?.first)
            call.respond(
                HttpStatusCode.Created,
                summaryFor(account, terms, eventosDelCredito, FxRateService.usdToCop())
                    .copy(disbursement = legs?.let { TransferResult(from = it.first, to = it.second) }),
            )
        }

        put("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            if (account.type != AccountType.LOAN) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Solo cuentas LOAN llevan términos de crédito")
            }
            val body = call.receive<CreditTerms>()
                .copy(accountId = accountId)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
            // upsert atómico por PK (accountId): elimina la carrera check-then-insert.
            // lastRemindedPeriod no está en el body del upsert, así que se conserva
            // a propósito: un cambio de día aplica desde el mes siguiente (v1).
            dbQuery {
                Credits.upsert { fillTerms(it, uid, body) }
            }
            call.respond(summaryFor(account, body, loadNonVoidedEvents(uid, accountId), FxRateService.usdToCop()))
        }

        // Ajuste de la deuda al saldo real del banco. Recibe el saldo OBJETIVO, no el delta:
        // la deuda de un crédito se mueve a diario por intereses causados, así que un delta
        // calculado sobre la vista del cliente puede llegar viejo. Acá se resta contra los
        // eventos vigentes y se registra un movimiento real — la deuda sigue derivándose de
        // los eventos (ver computeBalances), nunca se sobrescribe.
        post("/{accountId}/balance-adjustment") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val target = call.receive<AdjustCreditBalanceRequest>().targetBalance
            if (target < 0L) {
                return@post call.respond(HttpStatusCode.BadRequest, "La deuda no puede ser negativa")
            }
            if (target > MAX_CREDIT_DEBT_COP) {
                return@post call.respond(HttpStatusCode.BadRequest, "Saldo fuera de rango — revisa el monto")
            }
            // Fuera de la transacción a propósito: pega contra la red y no debe alargar el lock.
            val rate = FxRateService.usdToCop()

            // Leer el saldo, escribir el ajuste y releer ocurren en UNA sola transacción, con la
            // fila de la cuenta bloqueada (.forUpdate, mismo idioma que ScreenRoutes). Antes eran
            // tres transacciones sueltas: dos ajustes solapados leían la misma deuda, escribían
            // ambos el mismo delta y la deuda se componía, mientras a los dos se les respondía
            // que había quedado exactamente en el objetivo.
            val outcome = dbQuery<AdjustOutcome> {
                val account = Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .firstOrNull()?.toAccount()
                    ?: return@dbQuery AdjustOutcome.NotFound
                if (account.type != AccountType.LOAN) {
                    return@dbQuery AdjustOutcome.NotLoan
                }
                // La hoja del cliente rotula el campo "(COP)" y formatea con formatCOP, y
                // AccountEnrichment pone balances["COP"] en el wire: para una cuenta en otra
                // moneda el usuario vería 0 y compararía contra una cifra que no es la suya.
                // Se rechaza acá en vez de volver la hoja multimoneda — la UI de créditos solo
                // crea cuentas COP; el caso llega únicamente vía POST /api/accounts.
                if (account.currency != "COP") {
                    return@dbQuery AdjustOutcome.NotCop
                }

                val current = computeBalances(account.type, loadNonVoidedEventsIn(uid, accountId))["COP"] ?: 0L
                // Sin diferencia no se registra nada: un evento de $0 sería ruido en el listado
                // y no movería el saldo. Eso además hace el endpoint idempotente si se repite.
                val adjustment = debtAdjustmentEventFor(account, current, target, now = System.currentTimeMillis())
                if (adjustment != null) insertEventRow(uid, adjustment)

                val terms = Credits.selectAll()
                    .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
                    .firstOrNull()?.toCreditTerms()
                // Relectura DESPUÉS del insert: la respuesta describe el estado que quedó, no la
                // foto previa a escribir.
                AdjustOutcome.Ok(
                    summaryFor(account, terms, loadNonVoidedEventsIn(uid, accountId), rate, adjustment),
                )
            }

            when (outcome) {
                AdjustOutcome.NotFound -> call.respond(HttpStatusCode.NotFound)
                AdjustOutcome.NotLoan  ->
                    call.respond(HttpStatusCode.UnprocessableEntity, "Solo cuentas LOAN llevan deuda de crédito")
                AdjustOutcome.NotCop   ->
                    call.respond(HttpStatusCode.UnprocessableEntity, "Solo se puede ajustar el saldo de créditos en COP")
                is AdjustOutcome.Ok    -> call.respond(outcome.summary)
            }
        }

        delete("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val deleted = dbQuery {
                Credits.deleteWhere { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun fillTerms(
    it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>,
    uid: String,
    terms: CreditTerms,
) {
    it[Credits.accountId]   = terms.accountId
    it[Credits.userId]      = uid
    it[Credits.bank]        = terms.bank
    it[Credits.principal]   = terms.principal
    it[Credits.rateEa]      = terms.rateEa
    it[Credits.termMonths]  = terms.termMonths
    it[Credits.installment] = terms.installment
    it[Credits.dayOfMonth]  = terms.dayOfMonth
    it[Credits.startDate]   = terms.startDate
    it[Credits.notes]       = terms.notes
    it[Credits.remindMe]    = terms.remindMe
}

private fun summaryFor(
    base: Account,
    terms: CreditTerms?,
    events: List<FinancialEvent>,
    rate: Double,
    adjustment: FinancialEvent? = null,
): CreditSummary {
    val account = enrichWith(base, events, rate)
    return CreditSummary(
        account         = account,
        terms           = terms,
        paidPct         = terms?.let { paidPctFor(it.principal, account.balance) },
        adjustmentEvent = adjustment,
        // Los mismos eventos de los que sale la deuda: si no hay ninguno, el $0 de esta cuenta
        // significa «todavía no se registró nada», no «pagado». Ver [CreditSummary.hasMovements].
        hasMovements    = events.isNotEmpty(),
    )
}

/**
 * Resultado del ajuste, decidido dentro de la transacción y respondido fuera.
 *
 * `call.respond` es suspend y el bloque de `dbQuery` no lo es: sin esto habría que salirse de la
 * transacción para validar, que es justo lo que abría la carrera.
 */
private sealed interface AdjustOutcome {
    data object NotFound : AdjustOutcome
    data object NotLoan : AdjustOutcome
    data object NotCop : AdjustOutcome
    data class Ok(val summary: CreditSummary) : AdjustOutcome
}
