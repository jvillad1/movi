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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.PAYROLL_DEDUCTION_CATEGORY
import com.jvillada.movi.shared.model.THIRD_PARTY_PAYMENT_CATEGORY
import com.jvillada.movi.server.time.AppClock

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
            // Se recibe el JSON CRUDO además del objeto, para poder distinguir «el cliente mandó
            // este campo» de «el cliente no lo conoce».
            //
            // `fillTerms` sobrescribe TODAS las columnas, y `paidBy`/`payrollDeduction` tienen
            // default, así que un APK anterior que edite cualquier cosa del crédito —la nota, el
            // día de pago— manda un cuerpo sin esos campos y los DEJA EN NULL. Consecuencia
            // medida por la revisión: los tres créditos que paga otro vuelven al barrido de
            // avisos y el dueño empieza a recibir recordatorios de $13,1M/mes que nadie le debe,
            // y el botón «Registrar pago de Skandia» desaparece.
            //
            // La distinción no se puede hacer con el objeto deserializado —ahí «ausente» y
            // «null» son lo mismo— ni con un valor centinela, que sería un valor legítimo el día
            // que alguien lo escriba. Mirar las claves del JSON es exacto y no inventa nada.
            //
            // Aplica también a `payrollDeduction`, que arrastra este mismo agujero desde la ola
            // 17 sin que nadie lo hubiera nombrado.
            val crudo = call.receive<JsonObject>()
            val recibido = Json.decodeFromJsonElement<CreditTerms>(crudo)
            val previo = dbQuery {
                Credits.selectAll()
                    .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
                    .firstOrNull()?.toCreditTerms()
            }
            val body = recibido
                .copy(accountId = accountId)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
                .let { if ("paidBy" in crudo) it else it.copy(paidBy = previo?.paidBy) }
                .let { if ("payrollDeduction" in crudo) it else it.copy(payrollDeduction = previo?.payrollDeduction ?: false) }
                // El seguro entra al mismo club, y por el mismo agujero exacto: un APK anterior a
                // esta ola que edite la nota o el día de pago manda un cuerpo SIN `insuranceMonthly`
                // y lo dejaría en null. Consecuencia: la cuota del ·9695 volvería a abonar $108.800
                // de más a capital cada mes, en silencio y con el número plausible.
                .let { if ("insuranceMonthly" in crudo) it else it.copy(insuranceMonthly = previo?.insuranceMonthly) }
                // El tope de la columna es varchar(60): un nombre más largo hacía fallar el
                // INSERT en Postgres y se caía el guardado ENTERO del crédito con un 500 sin
                // mensaje, porque no hay StatusPages. Se recorta acá en vez de rechazar: nadie
                // pierde un crédito por haber escrito de más en un rótulo.
                .let { it.copy(paidBy = it.paidBy?.trim()?.take(60)?.takeIf { v -> v.isNotBlank() }) }
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

        /**
         * Registra el **descuento de nómina** del mes: la cuota de una libranza que el empleador
         * ya retuvo del sueldo.
         *
         * Es un INCOME sobre la cuenta del PRÉSTAMO, no un gasto de una cuenta de dinero. Esa
         * elección es todo el punto:
         *
         * - `signedDelta` sobre un LOAN lee un INCOME como «la deuda baja» — que es lo que pasó.
         * - `isCashFlow` deja fuera del mes todo lo que ocurre en una cuenta LOAN, así que **no**
         *   se suma como ingreso ni como gasto. Si se registrara como gasto de la cuenta de
         *   ahorros, la plata se descontaría dos veces: el salario que el dueño ve ya viene neto.
         * - Ninguna cuenta de dinero se toca, porque esa plata nunca llegó a ninguna.
         *
         * **Idempotente por período**: dos toques en el mismo mes no bajan la deuda dos veces. El
         * id del evento lleva el período, así que el segundo INSERT choca contra la clave y no
         * escribe nada.
         */
        post("/{accountId}/payroll-deduction") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing accountId")

            val cuenta = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            if (cuenta.type != AccountType.LOAN) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "Solo un crédito tiene cuotas que paga otro")
            }

            val terms = dbQuery {
                Credits.selectAll()
                    .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
                    .firstOrNull()?.toCreditTerms()
            } ?: return@post call.respond(HttpStatusCode.UnprocessableEntity, "Este crédito no tiene términos cargados")
            // Este endpoint cubre las DOS formas de «esta cuota no sale de mi cuenta»: la
            // libranza (la retiene el empleador) y el tercero que paga (Skandia, la esposa, un
            // papá). El movimiento que escribe es idéntico salvo la categoría y el texto — lo
            // que importa en ambos casos es que la deuda baje sin inventar un gasto. Duplicar la
            // ruta habría dejado dos idempotencias que mantener en paralelo.
            val quienPaga = terms.paidBy
            if (!terms.payrollDeduction && quienPaga == null) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Este crédito lo pagas tú: regístralo como un pago normal",
                )
            }

            val ahora = System.currentTimeMillis()
            // El período en la zona de la app (Bogotá): un descuento de las 9 pm del 31 no puede
            // caer en el mes siguiente y volver a bajar la deuda.
            val periodo = java.time.Instant.ofEpochMilli(ahora)
                .atZone(AppClock.zone)
                .toLocalDate()
                .toString()
                .take(7)
            // **Un id, no dos.** La primera versión de esto usaba un prefijo distinto según el
            // caso (`ev_nomina_` vs `ev_tercero_`) con el argumento de que un crédito que cambia
            // de esquema no podía quedar con el pago del mes bloqueado por la idempotencia del
            // otro. El argumento estaba invertido, y lo midió la revisión:
            //
            // 5 de agosto, la hipoteca 1254 marcada libranza → «Registrar descuento» inserta
            // `ev_nomina_acc-1254_2026-08` y la deuda baja $9.147.408. El 10 el dueño corrige y
            // pone «la paga Skandia». La tarjeta ahora ofrece «Registrar pago de Skandia», él lo
            // toca, y como la clave es otra el INSERT no choca: la deuda baja OTROS $9.147.408.
            // Agosto queda $9.147.408 por debajo de lo real, en silencio.
            //
            // La cuota de agosto es UNA sola, y quién la pagó no cambia eso. El costo de
            // unificar es un 4xx honesto en el mes en que se corrige el esquema; el de separar
            // era una deuda mal contada. La categoría y el texto sí siguen dependiendo del flag
            // —eso describe el movimiento— pero el id describe la CUOTA.
            //
            // Cambiar el prefijo no rompe idempotencia de lo ya registrado: se consultó la base
            // de producción antes de tocarlo y no existía ningún `ev_nomina_%` (el dueño nunca
            // había usado «Registrar descuento»). Si algún día hubiera datos viejos con ese
            // prefijo, habría que mirar los dos ids antes de insertar.
            val evento = FinancialEvent(
                id = "ev_cuota_${accountId}_$periodo",
                accountId = accountId,
                type = TransactionType.INCOME,
                amount = terms.installment,
                category = if (terms.payrollDeduction) PAYROLL_DEDUCTION_CATEGORY else THIRD_PARTY_PAYMENT_CATEGORY,
                description = if (terms.payrollDeduction) "Cuota descontada de la nómina" else "Cuota pagada por $quienPaga",
                timestamp = ahora,
                source = EventSource.MANUAL,
                reconciliationStatus = ReconciliationStatus.RECONCILED,
                createdAt = ahora,
            )
            dbQuery { insertEventRow(uid, evento) }

            val rate = FxRateService.usdToCop()
            call.respond(summaryFor(cuenta, terms, loadNonVoidedEvents(uid, accountId), rate, evento))
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
    it[Credits.payrollDeduction] = terms.payrollDeduction
    it[Credits.paidBy] = terms.paidBy?.trim()?.takeIf { v -> v.isNotBlank() }
    it[Credits.insuranceMonthly] = terms.insuranceMonthly?.takeIf { v -> v > 0L }
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
