package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.server.time.cutoffDayOf
import com.jvillada.movi.shared.model.CATEGORY_CATALOG_RENAME_BLOCKED
import com.jvillada.movi.shared.model.CATEGORY_MERGE_SAME
import com.jvillada.movi.shared.model.CATEGORY_NAME_MAX_LENGTH
import com.jvillada.movi.shared.model.CATEGORY_NAME_REQUIRED
import com.jvillada.movi.shared.model.CATEGORY_NAME_TOO_LONG
import com.jvillada.movi.shared.model.CATEGORY_TYPE_VALUES
import com.jvillada.movi.shared.model.CategoryPrefsRequest
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.MergeCategoryRequest
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.RenameCategoryRequest
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.categoriaDestinoInexistenteMensaje
import com.jvillada.movi.shared.model.categoriaDestinoOcupadoMensaje
import com.jvillada.movi.shared.model.categoriaReservadaMensaje
import com.jvillada.movi.shared.model.isReservedCategory
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * `/api/categories` — la pantalla «Más → Categorías».
 *
 * ## El costo que esta ruta existe para pagar
 *
 * **La categoría es texto copiado en cada fila.** No hay tabla de categorías ni id estable: el
 * nombre está repetido en `financial_events.category`, `budgets.category` y
 * `recurring_rules.category` (verificado contra el esquema real; `subscriptions` NO tiene
 * categoría — se cruza por comerciante, `merchant_key`). Ese diseño hace que renombrar no sea
 * "editar una fila": es **reescribir tres tablas a la vez**.
 *
 * Y tiene que ser atómico. Si se corta a la mitad, media historia del dueño dice «Trasnporte» y
 * la otra media «Transporte» — que es exactamente el problema que vino a arreglar, pero peor,
 * porque ahora el presupuesto cruza con una mitad. Por eso [rewriteCategory] no hace ninguna
 * llamada suelta: recibe la [Transaction] ya abierta por `dbQuery` y hace los tres UPDATE (más el
 * movimiento de la preferencia) adentro de ella. O quedan las tres tablas, o no queda ninguna.
 *
 * ## Lo que NO se puede tocar
 *
 * Las [com.jvillada.movi.shared.model.RESERVED_CATEGORIES] («Traspaso», «Saldo inicial», «Pago de
 * tarjeta», «Cuenta eliminada»). `isCashFlow` las reconoce **por su nombre exacto**: renombrar una
 * sola rompería el cálculo de ingresos y gastos de todos los meses de golpe. Se listan (para que
 * el dueño entienda de dónde salen esos movimientos) y se rechazan en toda acción, tanto como
 * origen como destino.
 */
fun Route.categoryRoutes() {

    /**
     * La lista, **con uso real**: en qué tipos se usa cada categoría, cuántos movimientos la
     * llevan y cuánto suman — este mes y en total —, más si tiene presupuesto o recurrentes.
     * Sin esos números la pantalla sería una lista de nombres, y con una lista de nombres no se
     * puede decidir qué sobra.
     */
    get("/api/categories") {
        val uid = call.userId()
        // La ventana del PERÍODO del usuario (ver PeriodSettings en :core), no el mes de
        // calendario. Con corte 1 —el default— da exactamente lo mismo que antes.
        val (monthStart, monthEnd) = currentPeriodWindow(cutoffDayOf(uid))
        call.respond(dbQuery { categoryUsage(uid, monthStart, monthEnd) })
    }

    /**
     * Renombrar — el arreglo del error de tipeo. Solo para categorías **propias**: las del
     * catálogo son una constante de código compartida por todos los usuarios, así que renombrar
     * una dejaría los movimientos con el nombre nuevo y el catálogo sugiriendo el viejo para
     * siempre. Para esas está unificar (que además esconde la vieja).
     */
    post("/api/categories/rename") {
        val uid = call.userId()
        val body = call.receive<RenameCategoryRequest>()
        val from = body.from.trim()
        val to = body.to.trim()

        if (from.isEmpty() || to.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_REQUIRED)
        }
        if (to.length > CATEGORY_NAME_MAX_LENGTH) {
            return@post call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_TOO_LONG)
        }
        if (isReservedCategory(from)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, categoriaReservadaMensaje(from))
        }
        if (isReservedCategory(to)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, categoriaReservadaMensaje(to))
        }
        if (PREDEFINED_CATEGORIES.any { it.name == from }) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, CATEGORY_CATALOG_RENAME_BLOCKED)
        }
        if (from == to) {
            // No-op válido: guardar sin cambiar el nombre no es un error.
            return@post call.respond(CategoryRewriteResult(name = to))
        }

        // La colisión con un nombre existente y la reescritura se deciden DENTRO de la misma
        // transacción (mismo patrón que `PUT /api/budgets/{category}/rename`): preguntar afuera y
        // escribir adentro deja una ventana en la que el destino puede aparecer entre medio.
        val outcome = dbQuery<RewriteOutcome> {
            // Renombrar exige que el destino esté LIBRE. Si ya existe, la operación honesta es
            // unificar (que además esconde el origen si venía del catálogo) y el dueño tiene que
            // decidirlo: juntar dos categorías con historia no es lo mismo que corregir un tipeo.
            // El cliente ya detecta esta colisión antes de llamar y ofrece unificar; esto es la
            // guarda del server, no el camino normal.
            if (categoryExists(uid, to)) RewriteOutcome.DestinoOcupado
            else RewriteOutcome.Ok(rewriteCategory(uid, from = from, to = to, hideSource = false))
        }
        when (outcome) {
            RewriteOutcome.DestinoOcupado ->
                call.respond(HttpStatusCode.Conflict, categoriaDestinoOcupadoMensaje(to))
            is RewriteOutcome.Ok -> call.respond(outcome.result)
        }
    }

    /**
     * Unificar — el arreglo de los duplicados. Todo lo que dice [MergeCategoryRequest.from] pasa
     * a decir [MergeCategoryRequest.into], y el origen **se esconde si era del catálogo** (si era
     * propio no hace falta: se queda sin nada detrás y desaparece solo de las sugerencias).
     *
     * Es el camino para «Otros ingresos» → «Otros»: junta la idea partida en dos sin borrar un
     * solo movimiento, y deja de ofrecer la que sobra.
     */
    post("/api/categories/merge") {
        val uid = call.userId()
        val body = call.receive<MergeCategoryRequest>()
        val from = body.from.trim()
        val into = body.into.trim()

        if (from.isEmpty() || into.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_REQUIRED)
        }
        if (into.length > CATEGORY_NAME_MAX_LENGTH) {
            return@post call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_TOO_LONG)
        }
        if (isReservedCategory(from)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, categoriaReservadaMensaje(from))
        }
        if (isReservedCategory(into)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, categoriaReservadaMensaje(into))
        }
        if (from == into) {
            return@post call.respond(HttpStatusCode.BadRequest, CATEGORY_MERGE_SAME)
        }

        // El destino tiene que EXISTIR. No es ceremonia: sin esta guarda, unificar «Comida» en
        // «Alimentación» (un nombre que no existe) es exactamente un renombrado de una categoría
        // del catálogo — lo que `rename` rechaza con 422 dos rutas más arriba, y por una razón que
        // no desaparece por entrar en esta puerta: el catálogo es una constante compartida y
        // volvería a sugerir el nombre viejo. Unificar es juntar dos cosas que ya existen.
        // La comprobación y la reescritura, en la MISMA transacción: preguntar afuera y escribir
        // adentro deja una ventana en la que el destino puede desaparecer entre medio.
        val result = dbQuery<CategoryRewriteResult?> {
            if (!categoryExists(uid, into)) null
            else rewriteCategory(uid, from = from, to = into, hideSource = true)
        }
        if (result == null) call.respond(HttpStatusCode.NotFound, categoriaDestinoInexistenteMensaje(into))
        else call.respond(result)
    }

    /**
     * Esconder y fijar el tipo. Las dos son **preferencias**, no datos: no tocan ni un movimiento,
     * y por eso viven en su propia tabla y no en una reescritura.
     *
     * Esconder es lo contrario de borrar: la categoría deja de ofrecerse al escribir, pero los
     * movimientos viejos la siguen diciendo y siguen contando donde contaban. Borrar habría
     * dejado huérfana media historia.
     */
    put("/api/categories/prefs") {
        val uid = call.userId()
        val body = call.receive<CategoryPrefsRequest>()
        val name = body.name.trim()

        if (name.isEmpty()) {
            return@put call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_REQUIRED)
        }
        if (name.length > CATEGORY_NAME_MAX_LENGTH) {
            return@put call.respond(HttpStatusCode.BadRequest, CATEGORY_NAME_TOO_LONG)
        }
        if (isReservedCategory(name)) {
            return@put call.respond(HttpStatusCode.UnprocessableEntity, categoriaReservadaMensaje(name))
        }
        val pinned = body.pinnedType?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (pinned != null && pinned !in CATEGORY_TYPE_VALUES) {
            return@put call.respond(HttpStatusCode.BadRequest, "Tipo desconocido: ${body.pinnedType}")
        }

        dbQuery {
            CategoryPrefs.deleteWhere { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq name) }
            // Una fila que no dice nada distinto del default no se guarda: así esta tabla solo
            // tiene lo que el dueño cambió de verdad, y "volver al default" es borrar, no marcar.
            if (body.hidden || pinned != null) {
                CategoryPrefs.insert {
                    it[userId]     = uid
                    it[CategoryPrefs.name] = name
                    it[hidden]     = body.hidden
                    it[pinnedType] = pinned
                }
            }
        }
        // La ventana del PERÍODO del usuario (ver PeriodSettings en :core), no el mes de
        // calendario. Con corte 1 —el default— da exactamente lo mismo que antes.
        val (monthStart, monthEnd) = currentPeriodWindow(cutoffDayOf(uid))
        val actualizada = dbQuery { categoryUsage(uid, monthStart, monthEnd) }
            .firstOrNull { it.name == name }
            ?: CategoryUsage(name = name, hidden = body.hidden, pinnedType = pinned)
        call.respond(actualizada)
    }
}

/** Cómo terminó un renombrado — el rechazo por nombre ocupado se decide adentro de la transacción. */
private sealed interface RewriteOutcome {
    /** Renombrar hacia un nombre que ya existe: eso es unificar, y lo decide el dueño. */
    data object DestinoOcupado : RewriteOutcome
    data class Ok(val result: CategoryRewriteResult) : RewriteOutcome
}

/** ¿Movi ya conoce esta categoría para este usuario — por datos, por preferencia o por catálogo? */
internal fun Transaction.categoryExists(uid: String, name: String): Boolean {
    if (PREDEFINED_CATEGORIES.any { it.name == name }) return true
    val enEventos = Events.selectAll()
        .where { (Events.userId eq uid) and (Events.category eq name) }.limit(1).any()
    if (enEventos) return true
    val enPresupuestos = Budgets.selectAll()
        .where { (Budgets.userId eq uid) and (Budgets.category eq name) }.limit(1).any()
    if (enPresupuestos) return true
    val enRecurrentes = RecurringRules.selectAll()
        .where { (RecurringRules.userId eq uid) and (RecurringRules.category eq name) }.limit(1).any()
    if (enRecurrentes) return true
    return CategoryPrefs.selectAll()
        .where { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq name) }.limit(1).any()
}

/**
 * **La reescritura, entera, adentro de una sola transacción.**
 *
 * Renombrar y unificar son la misma operación —mover todo lo que dice [from] a [to]— y por eso
 * comparten esta función: dos implementaciones paralelas de algo que toca tres tablas se
 * desincronizan a la primera de cambio. Lo único que cambia entre las dos es [hideSource].
 *
 * Las tablas que toca, y por qué cada una:
 *
 * - **`financial_events`** — la historia. Es el UPDATE grande y el que le importa al dueño.
 * - **`budgets`** — el presupuesto cruza con el gasto **por nombre** (no hay id), así que un
 *   rename que no lo mueva deja el presupuesto mirando a una categoría que ya no existe.
 *   Ojo: la categoría es parte de la PK `(user_id, category)`, así que si el destino YA tiene
 *   presupuesto no se puede simplemente actualizar el nombre — chocaría. En ese caso los dos
 *   límites **se suman** en el del destino y el de origen se borra: el caso real es el dueño
 *   juntando dos categorías que en su cabeza siempre fueron una, y ahí el límite de la categoría
 *   unificada es lo que tenía repartido. **Es irreversible**: los dos límites originales dejan de
 *   existir. Por eso la hoja lo dice ANTES (ver `avisoDeUnificacion`, que recibe el destino
 *   completo justamente para poder nombrar la suma) y [CategoryRewriteResult.budgetsMerged] lo
 *   repite DESPUÉS. Cambiarle en silencio un número que puso a propósito no es una opción.
 *
 *   **Por qué acá se suma y `PUT /api/budgets/{category}/rename` rechaza con 409** (dos
 *   comportamientos distintos para la misma colisión, a propósito): son dos intenciones
 *   distintas. Allá el dueño está editando UN presupuesto y le cambia el nombre; si ese nombre ya
 *   tiene presupuesto, fundirlos sería hacer desaparecer una fila que él ve en Presupuestos sin
 *   haberlo pedido — el 409 es correcto. Acá pidió explícitamente **juntar dos categorías**, con
 *   el aviso de la suma delante, y rechazarlo lo dejaría sin ninguna forma de completar lo que
 *   pidió. La regla es la misma en las dos: no fundir presupuestos sin que lo haya pedido.
 * - **`recurring_rules`** — lo que se repite cada mes también lleva el nombre copiado.
 * - **`category_prefs`** — la preferencia viaja con el nombre (si no, esconder + renombrar
 *   dejaría escondida una categoría que ya no existe y visible la nueva).
 *
 * `subscriptions` no aparece porque **no tiene columna de categoría**: una suscripción se
 * identifica por comerciante (`merchant_key`), no por categoría. Verificado en el esquema, no
 * asumido.
 *
 * **El destino nunca queda escondido.** Acaba de recibir datos: dejarlo escondido sería mover la
 * historia del dueño a una categoría que la app no le va a volver a ofrecer nunca.
 */
internal fun Transaction.rewriteCategory(
    uid: String,
    from: String,
    to: String,
    hideSource: Boolean,
): CategoryRewriteResult {
    val movements = Events.update({ (Events.userId eq uid) and (Events.category eq from) }) {
        it[category] = to
    }

    var budgets = 0
    var budgetsMerged = false
    val presupuestoOrigen = Budgets.selectAll()
        .where { (Budgets.userId eq uid) and (Budgets.category eq from) }
        .firstOrNull()
    if (presupuestoOrigen != null) {
        val limiteOrigen = presupuestoOrigen[Budgets.monthlyLimit]
        val presupuestoDestino = Budgets.selectAll()
            .where { (Budgets.userId eq uid) and (Budgets.category eq to) }
            .firstOrNull()
        Budgets.deleteWhere { (Budgets.userId eq uid) and (Budgets.category eq from) }
        if (presupuestoDestino != null) {
            val suma = presupuestoDestino[Budgets.monthlyLimit] + limiteOrigen
            Budgets.update({ (Budgets.userId eq uid) and (Budgets.category eq to) }) {
                it[monthlyLimit] = suma
            }
            budgetsMerged = true
        } else {
            Budgets.insert {
                it[userId]       = uid
                it[category]     = to
                it[monthlyLimit] = limiteOrigen
            }
        }
        budgets = 1
    }

    val recurrentes = RecurringRules.update({
        (RecurringRules.userId eq uid) and (RecurringRules.category eq from)
    }) {
        it[category] = to
    }

    // Preferencias: la del destino manda; si no tenía, hereda el tipo fijado del origen. El
    // destino nunca queda escondido (ver KDoc), y la fila del origen se borra siempre — para
    // volver a "esconder el origen" hay una sola línea, la de abajo, y solo si vale la pena.
    val prefOrigen = CategoryPrefs.selectAll()
        .where { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq from) }
        .firstOrNull()
    val prefDestino = CategoryPrefs.selectAll()
        .where { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq to) }
        .firstOrNull()
    val tipoFijadoDestino = prefDestino?.get(CategoryPrefs.pinnedType)
        ?: prefOrigen?.get(CategoryPrefs.pinnedType)
    CategoryPrefs.deleteWhere { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq from) }
    CategoryPrefs.deleteWhere { (CategoryPrefs.userId eq uid) and (CategoryPrefs.name eq to) }
    if (tipoFijadoDestino != null) {
        CategoryPrefs.insert {
            it[userId]     = uid
            it[name]       = to
            it[hidden]     = false
            it[pinnedType] = tipoFijadoDestino
        }
    }
    // Unificar una del CATÁLOGO necesita además esconderla: quedó sin nada detrás, pero el
    // catálogo la sigue ofreciendo al escribir (es una constante de código, la misma para todos
    // los usuarios). Una categoría propia no necesita esto — sin datos ni preferencia, deja de
    // existir sola, y guardarle una fila la dejaría en la lista para siempre.
    if (hideSource && PREDEFINED_CATEGORIES.any { it.name == from }) {
        CategoryPrefs.insert {
            it[userId]     = uid
            it[name]       = from
            it[hidden]     = true
            it[pinnedType] = null
        }
    }

    return CategoryRewriteResult(
        name = to,
        movements = movements,
        budgets = budgets,
        budgetsMerged = budgetsMerged,
        recurringRules = recurrentes,
    )
}

/** Acumulador mutable del recorrido — solo vive dentro de [categoryUsage]. */
private class UsageAcc {
    val tipos = mutableSetOf<TransactionType>()
    var movimientos = 0
    /** Gastos e ingresos **separados**: sumarlos en un solo número no significa nada. Ver [CategoryUsage.total]. */
    var gastado = 0L
    var recibido = 0L
    var movimientosMes = 0
    var gastadoMes = 0L
    var recibidoMes = 0L
    var otraMoneda = 0
    var presupuestos = 0
    var limitePresupuesto = 0L
    var recurrentes = 0
}

/**
 * Arma la lista con el uso real. Una sola pasada por los movimientos del usuario (no anulados),
 * más un conteo de presupuestos y recurrentes, más el catálogo y las preferencias — **todo en
 * una lista**, con el origen (catálogo / propia) como una etiqueta y no como dos pantallas.
 *
 * **Los totales suman solo COP.** Mezclar dólares y pesos en un mismo número sería mentir; los
 * movimientos en otra moneda se cuentan aparte ([CategoryUsage.otherCurrencyMovements]) para que
 * la pantalla pueda decir que existen sin sumarlos.
 *
 * Las categorías se agrupan por su nombre **exacto** (recortado). Que «carro» y «Carro» aparezcan
 * como dos filas no es un defecto: son dos categorías distintas en los datos —dos presupuestos
 * distintos, dos cruces distintos— y verlas separadas es justo lo que le permite al dueño
 * unificarlas.
 */
internal fun Transaction.categoryUsage(uid: String, monthStart: Long, monthEnd: Long): List<CategoryUsage> {
    val anulados = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()

    val acc = linkedMapOf<String, UsageAcc>()

    Events
        .select(Events.id, Events.category, Events.type, Events.amount, Events.currency, Events.timestamp)
        .where { Events.userId eq uid }
        .forEach { row ->
            if (row[Events.id] in anulados) return@forEach
            val nombre = row[Events.category].trim()
            if (nombre.isEmpty()) return@forEach
            val a = acc.getOrPut(nombre) { UsageAcc() }
            runCatching { TransactionType.valueOf(row[Events.type]) }.getOrNull()?.let { a.tipos += it }
            if (row[Events.currency] != "COP") {
                a.otraMoneda++
                return@forEach
            }
            val monto = row[Events.amount]
            val esIngreso = row[Events.type] == TransactionType.INCOME.name
            a.movimientos++
            if (esIngreso) a.recibido += monto else a.gastado += monto
            val ts = row[Events.timestamp]
            if (ts >= monthStart && ts < monthEnd) {
                a.movimientosMes++
                if (esIngreso) a.recibidoMes += monto else a.gastadoMes += monto
            }
        }

    Budgets.selectAll().where { Budgets.userId eq uid }.forEach { row ->
        val nombre = row[Budgets.category].trim()
        if (nombre.isEmpty()) return@forEach
        val a = acc.getOrPut(nombre) { UsageAcc() }
        a.presupuestos++
        // El límite viaja para que la hoja pueda decir en cuánto va a QUEDAR al unificar, no
        // apenas que los dos se suman. Ver [CategoryUsage.budgetLimit].
        a.limitePresupuesto += row[Budgets.monthlyLimit]
    }
    RecurringRules.selectAll().where { RecurringRules.userId eq uid }.forEach { row ->
        val nombre = row[RecurringRules.category].trim()
        if (nombre.isNotEmpty()) acc.getOrPut(nombre) { UsageAcc() }.recurrentes++
    }

    val prefs = CategoryPrefs.selectAll()
        .where { CategoryPrefs.userId eq uid }
        .associate { it[CategoryPrefs.name] to (it[CategoryPrefs.hidden] to it[CategoryPrefs.pinnedType]) }

    // El catálogo entra completo aunque no se haya usado nunca: esconder «Freelance» sin haberla
    // usado es un caso normal, y para eso tiene que estar en la lista.
    PREDEFINED_CATEGORIES.forEach { acc.getOrPut(it.name) { UsageAcc() } }
    prefs.keys.forEach { acc.getOrPut(it) { UsageAcc() } }

    return acc.map { (nombre, a) ->
        val pref = prefs[nombre]
        CategoryUsage(
            name = nombre,
            scope = if (PREDEFINED_CATEGORIES.any { it.name == nombre }) CategoryScope.PREDEFINED
                    else CategoryScope.CUSTOM,
            reserved = isReservedCategory(nombre),
            usedTypes = a.tipos.sortedBy { it.name },
            pinnedType = pref?.second,
            hidden = pref?.first ?: false,
            movements = a.movimientos,
            total = a.gastado,
            incomeTotal = a.recibido,
            monthMovements = a.movimientosMes,
            monthTotal = a.gastadoMes,
            monthIncomeTotal = a.recibidoMes,
            otherCurrencyMovements = a.otraMoneda,
            budgets = a.presupuestos,
            budgetLimit = a.limitePresupuesto,
            recurringRules = a.recurrentes,
        )
    }.sortedWith(
        // Lo más usado primero — la pregunta que trae al dueño acá es "¿qué sobra?", y lo que
        // sobra se reconoce por contraste con lo que de verdad usa. Las reservadas al final:
        // no se pueden tocar, así que no compiten por la atención.
        compareBy<CategoryUsage> { it.reserved }
            .thenByDescending { it.movements + it.otherCurrencyMovements }
            .thenBy { it.name.lowercase() },
    )
}
