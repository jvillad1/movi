package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.rechazoDelMonto
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DeleteBudgetRequest
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.RenameBudgetRequest
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.movementCount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.server.time.cutoffDayOf
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.propuestasDePresupuesto
import com.jvillada.movi.shared.model.ventanaDe

fun Route.financeRoutes() {
    // F50: ya no tiene consumidor — Inversiones (:shared) pasó a mostrar cuentas tipo
    // INVESTMENT en vez de "posiciones" (un modelo que nunca tuvo alta). Se deja el endpoint
    // porque no rompe nada mantenerlo, pero WalletRepository (:core) ya no expone getHoldings.
    get("/api/holdings") { call.respond(emptyList<Holding>()) }
    // F26: /api/goals se mudó a GoalRoutes.kt — acá era un `[]` hardcodeado (no había ni tabla).

    // ── Budgets — per-user, DB-backed ─────────────────────────────────────────

    get("/api/budgets") {
        val uid = call.userId()
        val list = dbQuery {
            Budgets.selectAll()
                .where { Budgets.userId eq uid }
                .map { Budget(it[Budgets.category], it[Budgets.monthlyLimit]) }
        }
        call.respond(list)
    }

    // **Lo que Movi propone a quien todavía no tiene presupuestos**: las cuatro categorías de más
    // gasto del período ANTERIOR, cada una con un tope que ya la cubre (ver `propuestasDePresupuesto`
    // en :core). El anterior y no el actual porque el actual está a medias: el día 3 del período
    // proponer lo gastado sería proponer casi nada.
    //
    // El gasto se suma con `monthCashFlow`, la MISMA regla que «Gastos» y que las barras de
    // Presupuestos (anulados, «Por confirmar», traspasos y pagos de tarjeta fuera; solo pesos): la
    // propuesta dice «Gastaste $X» y la barra del presupuesto recién creado tiene que haber contado
    // igual. El período es el del dueño entero —el corte y los meses que arrancaron otro día—,
    // leído con `ajustesDePeriodoDe` como el resto del server.
    get("/api/budgets/propuestas") {
        val uid = call.userId()
        val ajustes = ajustesDePeriodoDe(uid)
        val ahora = AppClock.now().toInstant().toEpochMilli()
        val ventana = ventanaDe(periodoAnterior(periodoDe(ahora, ajustes)), ajustes)
        val propuestas = dbQuery {
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()
            // `ventanaDe` incluye su último milisegundo; `monthCashFlow` usa fin exclusivo.
            val (_, gastoPorCategoria) =
                monthCashFlow(uid, ventana.first, ventana.last + 1, voidedIds, accountTypesFor(uid))
            val conPresupuesto = Budgets.selectAll()
                .where { Budgets.userId eq uid }
                .map { it[Budgets.category] }
                .toSet()
            propuestasDePresupuesto(
                gastoPorCategoria = gastoPorCategoria,
                conPresupuesto = conPresupuesto,
                desde = epochMillisToAppDateString(ventana.first),
                hasta = epochMillisToAppDateString(ventana.last),
            )
        }
        call.respond(propuestas)
    }

    post("/api/budgets") {
        val uid = call.userId()
        // Mismas reglas que renombrar (nombre recortado y no vacío) y que un movimiento (el
        // límite, ver `rechazoDelMonto`): un límite en cero pintaba todo gasto como sobrepasado.
        val body = call.receive<Budget>().let { it.copy(category = it.category.trim()) }
        if (body.category.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta la categoría")
        rechazoDelMonto(body.monthlyLimit)?.let { motivo -> return@post call.respond(HttpStatusCode.BadRequest, motivo) }
        val exists = dbQuery {
            Budgets.selectAll()
                .where { (Budgets.userId eq uid) and (Budgets.category eq body.category) }
                .count() > 0
        }
        // En español y con el nombre, como el 409 de renombrar: el cliente muestra el cuerpo de un
        // 4xx tal cual en la hoja «Nuevo presupuesto».
        if (exists) return@post call.respond(HttpStatusCode.Conflict, "Ya existe un presupuesto llamado \"${body.category}\"")
        dbQuery {
            Budgets.insert {
                it[userId]       = uid
                it[category]     = body.category
                it[monthlyLimit] = body.monthlyLimit
            }
        }
        call.respond(HttpStatusCode.Created, body)
    }

    // ── Editar, borrar y renombrar: el nombre viaja en el CUERPO ──────────────
    //
    // Una categoría es texto libre del dueño, y meterla en un segmento de ruta la rompe: un
    // presupuesto de «Luz/Agua» se creaba bien (el POST de arriba lo manda en el cuerpo) y
    // después no se podía ni editar ni borrar — `/api/budgets/Luz/Agua` son DOS segmentos y
    // `{category}` hace coincidir uno solo, así que era 404 para siempre. Con «#» se perdía
    // todo lo que sigue y con «%» el decode del path directamente falla. Es exactamente la
    // regla que ya sigue Categorías («los nombres viajan SIEMPRE en el cuerpo»).
    //
    // Las tres rutas de abajo con `{category}` SE QUEDAN, haciendo lo mismo: el APK que el
    // dueño tiene instalado todavía las llama, y actualizarlo no es condición para desplegar
    // esto. Los clientes de hoy (:core) ya usan las nuevas.

    put("/api/budgets") {
        val body = call.receive<Budget>()
        editarPresupuesto(call, body.category, body)
    }

    post("/api/budgets/delete") {
        borrarPresupuesto(call, call.receive<DeleteBudgetRequest>().category)
    }

    put("/api/budgets/{category}") {
        val cat = call.parameters["category"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        editarPresupuesto(call, cat, call.receive())
    }

    delete("/api/budgets/{category}") {
        val cat = call.parameters["category"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        borrarPresupuesto(call, cat)
    }

    // Ola 10: este rename **rechaza con 409** si el nombre nuevo ya tiene presupuesto, mientras
    // que `POST /api/categories/merge` en esa misma colisión SUMA los dos límites. La diferencia
    // es deliberada y la regla detrás es una sola —no fundir dos presupuestos sin que el dueño lo
    // haya pedido—: acá está editando UN presupuesto y fundirlo haría desaparecer una fila que él
    // ve en Presupuestos; allá pidió juntar dos categorías y la hoja le muestra la suma antes de
    // aplicarla. Ver el KDoc de `CategoryRoutes.rewriteCategory`.
    // F17: la categoría es la PK de budgets (userId+category), así que "renombrar" no es un
    // UPDATE — es borrar la fila vieja e insertar una con el nombre nuevo, conservando el
    // límite. Todo en una transacción para que un fallo a mitad de camino no deje ni el
    // presupuesto viejo ni el nuevo. A propósito NO toca `financial_event`: el cruce entre
    // presupuesto y gasto es por nombre de categoría (ver `spentByCategoryForPeriod` del lado
    // del cliente), así que renombrar acá deja de "ver" los movimientos con el nombre viejo —
    // es la advertencia que la hoja de edición le muestra al dueño antes de guardar.
    post("/api/budgets/rename") {
        val body = call.receive<RenameBudgetRequest>()
        val cat = body.category?.trim()
        if (cat.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta la categoría")
        renombrarPresupuesto(call, cat, body.newCategory)
    }

    put("/api/budgets/{category}/rename") {
        val cat = call.parameters["category"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        renombrarPresupuesto(call, cat, call.receive<RenameBudgetRequest>().newCategory)
    }

    // ── Finance summary — computed from real Events ────────────────────────────

    get("/api/finance-summary") {
        val uid = call.userId()
        val raw = call.request.queryParameters["scope"] ?: "SELF"
        val scope = runCatching { Scope.valueOf(raw.uppercase()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope: $raw")

        // "Este mes" es el mes civil de Bogotá (AppClock), no el de UTC: un movimiento a las
        // 9 pm del 31 sigue siendo de este mes.
        // La ventana del PERÍODO del usuario (ver PeriodSettings en :core), no el mes de
        // calendario. Con corte 1 —el default— da exactamente lo mismo que antes.
        val (monthStart, monthEnd) = currentPeriodWindow(ajustesDePeriodoDe(uid))

        val rate = FxRateService.usdToCop()
        val cuentas = dbQuery {
            Accounts.selectAll().where { Accounts.userId eq uid }
                .mapNotNull { runCatching { it.toAccount() }.getOrNull() }
        }
        val nonVoidedEvents = loadNonVoidedEvents(uid)
        val eventsByAccount = nonVoidedEvents.groupBy { it.accountId }
        // **`patrimonioDe`, la regla única del patrimonio** (en :core), no una suma propia. Acá
        // vivía `netWorth`, una segunda copia que ya había tenido que arreglarse una vez (sumaba
        // las deudas en vez de restarlas) y que no sabía de bienes: con la casa cargada, este
        // campo habría seguido diciendo −$2.074M mientras el Inicio decía la verdad. Nadie lo
        // renderiza hoy, pero quien lo use primero tiene que encontrarse la misma cifra que el
        // Inicio, no una parecida.
        val derivedBalance = patrimonioDe(
            cuentas.map { enrichWith(it, eventsByAccount[it.id].orEmpty(), rate) },
        ).neto

        val summary = dbQuery {
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()

            val monthEvents = Events.selectAll().where {
                (Events.userId eq uid) and
                (Events.timestamp greaterEq monthStart) and
                (Events.timestamp less monthEnd)
            }.filterNot { it[Events.id] in voidedIds }
                // «Por confirmar» no entra en ingresos ni egresos del período: la misma regla que
                // `monthCashFlow` del Inicio y que los chips de Movimientos.
                .filterNot { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }

            val accountTypeById = accountTypesFor(uid)
            // Movimientos de cuentas de deuda NO son flujo de caja del mes (ver isCashFlow):
            // bajar la deuda de un crédito al saldo real del banco no es un ingreso, y subirla
            // no es un gasto — es la misma plata cambiando de periodo. Esto también corrige la
            // "Deuda inicial" de apertura, que venía contándose como egreso del mes.
            val cashFlow = monthEvents.filter { row ->
                val accountType = accountTypeById[row[Events.accountId]]
                accountType == null ||
                    isCashFlow(accountType, TransactionType.valueOf(row[Events.type]), row[Events.category])
            }

            val ingresos = cashFlow
                .filter { it[Events.type] == TransactionType.INCOME.name && it[Events.currency] == "COP" }
                .sumOf { it[Events.amount] }
            val egresos = cashFlow
                .filter { it[Events.type] == TransactionType.EXPENSE.name && it[Events.currency] == "COP" }
                .sumOf { it[Events.amount] }

            FinanceSummary(
                scope = scope,
                balance = derivedBalance,
                ingresos = ingresos,
                egresos = egresos,
                // Del usuario completo, no del mes ni del `scope` — ver KDoc del campo en
                // FinanceSummary. `scope` hoy no filtra nada en este endpoint (ver arriba:
                // ni las cuentas ni nonVoidedEvents lo usan).
                //
                // El criterio de qué es "un movimiento" vive en :core (ver [movementCount]) y no
                // acá: además de la apertura de cuenta (F54), un traspaso son DOS eventos y una
                // sola cosa que el dueño hizo. Mover $1.000.000 de una cuenta a otra le sumaba 2
                // a este contador, mientras Movimientos lo mostraba —correctamente— como un solo
                // renglón.
                eventCount = movementCount(nonVoidedEvents),
            )
        }
        call.respond(summary)
    }
}

// ── Presupuestos: un solo cuerpo por operación ────────────────────────────────
// Cada una de las tres operaciones tiene DOS rutas (el nombre en el cuerpo y, por el APK viejo,
// el nombre en la ruta) y un solo cuerpo, acá abajo. Así las dos no pueden divergir.

private suspend fun editarPresupuesto(call: ApplicationCall, categoria: String, body: Budget) {
    val uid = call.userId()
    val cat = categoria.trim()
    if (cat.isBlank()) return call.respond(HttpStatusCode.BadRequest, "Falta la categoría")
    rechazoDelMonto(body.monthlyLimit)?.let { motivo -> return call.respond(HttpStatusCode.BadRequest, motivo) }
    val updated = dbQuery {
        Budgets.update({ (Budgets.userId eq uid) and (Budgets.category eq cat) }) {
            it[monthlyLimit] = body.monthlyLimit
        }
    }
    if (updated == 0) call.respond(HttpStatusCode.NotFound)
    else call.respond(body.copy(category = cat))
}

private suspend fun borrarPresupuesto(call: ApplicationCall, categoria: String) {
    val uid = call.userId()
    val cat = categoria.trim()
    if (cat.isBlank()) return call.respond(HttpStatusCode.BadRequest, "Falta la categoría")
    val deleted = dbQuery {
        Budgets.deleteWhere { (Budgets.userId eq uid) and (Budgets.category eq cat) }
    }
    if (deleted == 0) call.respond(HttpStatusCode.NotFound)
    else call.respond(HttpStatusCode.NoContent)
}

private suspend fun renombrarPresupuesto(call: ApplicationCall, categoria: String, nombreNuevo: String) {
    val uid = call.userId()
    val cat = categoria.trim()
    val newCategory = nombreNuevo.trim()
    if (newCategory.isBlank()) return call.respond(HttpStatusCode.BadRequest, "Falta el nombre nuevo")

    val outcome = dbQuery<RenameOutcome> {
        val current = Budgets.selectAll()
            .where { (Budgets.userId eq uid) and (Budgets.category eq cat) }
            .firstOrNull() ?: return@dbQuery RenameOutcome.NotFound
        if (newCategory != cat) {
            val taken = Budgets.selectAll()
                .where { (Budgets.userId eq uid) and (Budgets.category eq newCategory) }
                .count() > 0
            if (taken) return@dbQuery RenameOutcome.Conflict
        }
        val limit = current[Budgets.monthlyLimit]
        Budgets.deleteWhere { (Budgets.userId eq uid) and (Budgets.category eq cat) }
        Budgets.insert {
            it[userId]       = uid
            it[category]     = newCategory
            it[monthlyLimit] = limit
        }
        RenameOutcome.Ok(Budget(newCategory, limit))
    }

    when (outcome) {
        RenameOutcome.NotFound -> call.respond(HttpStatusCode.NotFound)
        RenameOutcome.Conflict -> call.respond(HttpStatusCode.Conflict, "Ya existe un presupuesto llamado \"$newCategory\"")
        is RenameOutcome.Ok    -> call.respond(outcome.budget)
    }
}

/**
 * Resultado de renombrar un presupuesto, decidido dentro de la transacción y respondido fuera
 * — mismo idioma que `AdjustOutcome` en CreditRoutes.kt: `call.respond` es suspend y `dbQuery`
 * no lo es.
 */
private sealed interface RenameOutcome {
    data object NotFound : RenameOutcome
    data object Conflict : RenameOutcome
    data class Ok(val budget: Budget) : RenameOutcome
}
