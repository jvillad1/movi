package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.CuentaDelDisponible
import com.jvillada.movi.shared.model.FUENTE_CREDITO
import com.jvillada.movi.shared.model.FUENTE_SALDO_INICIAL
import com.jvillada.movi.shared.model.FuenteDePlata
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.reminders.arranqueDeLaRegla
import com.jvillada.movi.server.reminders.ocurrenciaAnteriorQuePisaElPeriodo
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.reminders.primeraOcurrenciaDesde
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.MONEDA_DE_LAS_REGLAS
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.server.reminders.ruleIsActiveOn
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDeCategoria
import com.jvillada.movi.shared.model.PAGO_FIJO_CON_DUDAS
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PAGO_FIJO_PENDIENTE
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PresupuestoDelPeriodo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/**
 * # «Tus períodos»: el server sabe contar cualquier período
 *
 * - `GET /api/periodos` → un [ResumenDePeriodo] por período, **del en curso al más viejo**: hasta el
 *   del movimiento vivo más viejo del usuario, con un tope de [MAX_PERIODOS]. Sin movimientos, solo
 *   el en curso.
 * - `GET /api/periodos/{id}` → el [DetalleDePeriodo] de uno de esos; un id que no es un período o que
 *   no está en la lista contesta 404.
 *
 * **Nada acá es una regla nueva.** Las entradas y salidas son las de «Gastos» y las barras de
 * Presupuestos ([movimientosDeFlujo]); los pagos fijos salen del mismo [resolverOcurrencias] que el
 * checklist; «Tu plata» es el mismo saldo que el Disponible cuenta al empezar el período
 * ([saldosDeTuPlataAntesDe]). Y cada ventana sale de [ventanaDe] con el corte y los inicios propios
 * del dueño: el período que se lista es el mismo que cuenta Movimientos.
 */
fun Route.periodosRoutes() {
    get("/api/periodos") {
        val uid = call.userId()
        val ajustes = ajustesDePeriodoDe(uid)
        val ahora = AppClock.now().toInstant().toEpochMilli()
        call.respond(dbQuery { resumenesDePeriodos(uid, ahora, ajustes) })
    }

    get("/api/periodos/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound)
        val ajustes = ajustesDePeriodoDe(uid)
        val ahora = AppClock.now().toInstant().toEpochMilli()
        val detalle = dbQuery { detalleDePeriodo(uid, id, ahora, ajustes) }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(detalle)
    }
}

/**
 * Cuántos períodos se listan como mucho: tres años. Más atrás, la lista deja de ser una lista que se
 * recorre con el dedo, y la consulta de flujo crece con cada período.
 */
internal const val MAX_PERIODOS: Int = 36

/** La respuesta de `GET /api/periodos`, con un «ahora» que una prueba puede fijar. */
internal fun Transaction.resumenesDePeriodos(uid: String, ahora: Long, ajustes: PeriodSettings): List<ResumenDePeriodo> {
    val anulados = anuladosDe(uid)
    val periodos = periodosDelUsuario(uid, ahora, ajustes, anulados)
    val enCurso = periodos.first()
    // **Una sola lectura de movimientos** para toda la lista, y no una por período: la franja que va
    // del arranque del más viejo al final del en curso, repartida después entre las ventanas de
    // [ventanaDe] — las mismas que usa [periodoDe] para decidir en qué período cae un movimiento.
    val movimientos = movimientosDeFlujo(
        uid = uid,
        desde = ventanaDe(periodos.last(), ajustes).first,
        hastaExclusivo = ventanaDe(enCurso, ajustes).last + 1,
        voidedIds = anulados,
        accountTypeById = accountTypesFor(uid),
    )
    val ventanas = periodos.map { it to ventanaDe(it, ajustes) }
    val porPeriodo = movimientos.groupBy { m -> ventanas.firstOrNull { (_, v) -> m.timestamp in v }?.first }
    return periodos.map { resumenDe(it, ajustes, enCurso, porPeriodo[it].orEmpty()) }
}

/**
 * La respuesta de `GET /api/periodos/{id}`, con un «ahora» que una prueba puede fijar; `null` si
 * [id] no es un período de la lista (mal escrito, futuro o más viejo que el primer movimiento).
 */
internal fun Transaction.detalleDePeriodo(
    uid: String,
    id: String,
    ahora: Long,
    ajustes: PeriodSettings,
): DetalleDePeriodo? {
    val periodo = periodoDelPrefijo(id) ?: return null
    val anulados = anuladosDe(uid)
    val periodos = periodosDelUsuario(uid, ahora, ajustes, anulados)
    if (periodo !in periodos) return null

    val hoy = epochMillisToAppDate(ahora)
    val ventana = ventanaDe(periodo, ajustes)
    val desde = ventana.first
    // `ventanaDe` incluye su último milisegundo; las consultas de acá usan fin exclusivo.
    val hasta = ventana.last + 1
    val cuentas = cuentasDelDisponible(uid)
    // Los tipos salen de la misma lectura de cuentas (`accountTypesFor` da el mismo mapa: las dos
    // descartan un tipo que no se entiende).
    val tipos = cuentas.mapValues { it.value.tipo }
    val movimientos = movimientosDeFlujo(uid, desde, hasta, anulados, tipos)
    val (_, gastoPorCategoria) = flujoDeCaja(movimientos)
    val enCurso = periodo == periodos.first()
    // «Al cerrar» en el período en curso es lo que hay hoy (hasta el final del día), no lo que habría
    // al final de una ventana que todavía no llegó.
    val cierre = if (enCurso) minOf(hasta, appDateToEpochMillis(hoy.plusDays(1))) else hasta
    val (alEmpezar, alCerrar) = saldosDeTuPlataAntesDe(uid, listOf(desde, cierre), anulados, cuentas)

    return DetalleDePeriodo(
        resumen = resumenDe(periodo, ajustes, periodos.first(), movimientos),
        porCategoria = gastoPorCategoria.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { GastoDeCategoria(it.key, it.value) },
        pagosFijos = pagosFijosDelPeriodo(
            uid = uid,
            periodo = periodo,
            hoy = hoy,
            ajustes = ajustes,
            inicioDeLaHistoria = epochMillisToAppDate(ventanaDe(periodos.last(), ajustes).first),
        ),
        // Los presupuestos de HOY: no hay historia de cuánto valía un tope antes. Lo gastado cruza por
        // nombre de categoría, como las barras de Presupuestos (`spentByCategory[category]`).
        presupuestos = Budgets.selectAll()
            .where { Budgets.userId eq uid }
            .map { PresupuestoDelPeriodo(it[Budgets.category], it[Budgets.monthlyLimit], gastoPorCategoria[it[Budgets.category]] ?: 0L) }
            .sortedBy { it.category.lowercase() },
        masGrandes = losMasGrandes(uid, movimientos, tipos),
        tuPlataAlEmpezar = alEmpezar,
        tuPlataAlCerrar = alCerrar,
        fuentesQueNoSonIngreso = fuentesQueNoSonIngreso(uid, desde, hasta, anulados, cuentas),
    )
}

/**
 * **La plata que entró a tus cuentas en `[desde, hastaExclusivo)` y no es ingreso** — lo que explica
 * un período que salió más de lo que entró sin que se haya gastado de más:
 *
 * - [FUENTE_CREDITO]: los desembolsos. La pata que ENTRA (INCOME, categoría [TRANSFER_CATEGORY]) a
 *   una cuenta que no es deuda, cuya hermana por `transfer_id` sale de una cuenta LOAN. Un traspaso
 *   entre cuentas propias (ahorros ↔ CDT) no es: esa plata ya estaba en tus cuentas.
 * - [FUENTE_SALDO_INICIAL]: el «Saldo inicial» ([OPENING_CATEGORY], INCOME) de una cuenta de dinero
 *   —ni deuda, ni inversión, ni un bien— que Movi conoció en el período. Condicionada o no: el AFC
 *   también paga gastos. Una inversión no, porque su saldo (Skandia, $106M) taparía todo lo demás y
 *   no es plata con la que se paga el mes.
 *
 * Solo en pesos y sin anulados, como toda suma de Movi. Una lectura de los eventos del período con
 * esas dos categorías; las hermanas de los traspasos vienen en la misma lectura (las dos patas nacen
 * en el mismo instante), y solo si alguna quedó afuera (una pata que se movió de fecha) se busca aparte.
 * El [FuenteDePlata.detalle] nombra las cuentas de la más grande a la más chica.
 */
private fun Transaction.fuentesQueNoSonIngreso(
    uid: String,
    desde: Long,
    hastaExclusivo: Long,
    anulados: Set<String>,
    cuentas: Map<String, CuentaDelDisponible>,
): List<FuenteDePlata> {
    val filas = Events.select(Events.id, Events.accountId, Events.type, Events.amount, Events.category, Events.transferId)
        .where {
            (Events.userId eq uid) and
                (Events.currency eq "COP") and
                (Events.timestamp greaterEq desde) and
                (Events.timestamp less hastaExclusivo) and
                (Events.category inList listOf(TRANSFER_CATEGORY, OPENING_CATEGORY))
        }
        .filterNot { it[Events.id] in anulados }
        .map { PataSinIngreso(it[Events.accountId], it[Events.type], it[Events.amount], it[Events.category], it[Events.transferId]) }
    if (filas.isEmpty()) return emptyList()

    fun esDeDinero(cuenta: CuentaDelDisponible?) =
        cuenta != null && !cuenta.esDeuda && !cuenta.esBien && cuenta.tipo != AccountType.INVESTMENT
    val entradas = filas.filter { it.tipo == TransactionType.INCOME.name }
    val saldosIniciales = entradas.filter { it.categoria == OPENING_CATEGORY && esDeDinero(cuentas[it.cuenta]) }
    val entradasDeTraspaso = entradas.filter {
        it.categoria == TRANSFER_CATEGORY && it.traspaso != null && cuentas[it.cuenta]?.esDeuda == false
    }

    // De qué cuenta salió cada traspaso: casi siempre en la misma lectura; si no, por `transfer_id`.
    val origenDe: MutableMap<String, String> = filas
        .filter { it.tipo == TransactionType.EXPENSE.name && it.categoria == TRANSFER_CATEGORY && it.traspaso != null }
        .associateTo(mutableMapOf()) { it.traspaso!! to it.cuenta }
    val faltan = entradasDeTraspaso.mapNotNull { it.traspaso }.filterNot { it in origenDe }.distinct()
    if (faltan.isNotEmpty()) {
        Events.select(Events.id, Events.accountId, Events.transferId)
            .where {
                (Events.userId eq uid) and (Events.transferId inList faltan) and
                    (Events.type eq TransactionType.EXPENSE.name) and (Events.category eq TRANSFER_CATEGORY)
            }
            .filterNot { it[Events.id] in anulados }
            .forEach { fila -> fila[Events.transferId]?.let { origenDe[it] = fila[Events.accountId] } }
    }
    // Cada desembolso con la cuenta de crédito de la que salió.
    val desembolsos = entradasDeTraspaso.mapNotNull { entrada ->
        val origen = origenDe[entrada.traspaso] ?: return@mapNotNull null
        if (cuentas[origen]?.tipo == AccountType.LOAN) origen to entrada.monto else null
    }

    val nombres: Map<String, String> by lazy {
        Accounts.select(Accounts.id, Accounts.name)
            .where { Accounts.userId eq uid }
            .associate { it[Accounts.id] to it[Accounts.name] }
    }
    fun fuente(tipo: String, porCuenta: List<Pair<String, Long>>): FuenteDePlata? {
        val monto = porCuenta.sumOf { it.second }
        if (monto <= 0) return null
        val detalle = porCuenta.groupBy({ it.first }, { it.second })
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Long>>> { it.value.sum() }.thenBy { it.key })
            .mapNotNull { nombres[it.key] }
        return FuenteDePlata(tipo, monto, detalle)
    }
    return listOfNotNull(
        fuente(FUENTE_CREDITO, desembolsos),
        fuente(FUENTE_SALDO_INICIAL, saldosIniciales.map { it.cuenta to it.monto }),
    )
}

/** Lo que [fuentesQueNoSonIngreso] necesita de cada evento. */
private class PataSinIngreso(
    val cuenta: String,
    val tipo: String,
    val monto: Long,
    val categoria: String,
    val traspaso: String?,
)

/**
 * **Los pagos fijos de [periodo]**: cada ocurrencia de cada recurrente real que vence adentro —con el
 * corte 25 y octubre arrancando el 24-sep, una regla de día 24 vence dos veces en octubre: el 24-sep
 * y el 24-oct— y cómo quedó: sellada o emparejada por Movi ([PAGO_FIJO_LISTO]), con dos o más
 * movimientos concluyentes ([PAGO_FIJO_CON_DUDAS]), o sin nada ([PAGO_FIJO_PENDIENTE], también si
 * todavía no llegó). Cada ocurrencia cae en exactamente un período.
 *
 * Decide [resolverOcurrencias], el mismo que arma el checklist: no hay un tercer emparejador. Lo que
 * cambia es qué se resuelve PRIMERO, porque eso decide la reserva de movimientos entre reglas:
 *
 * - **En el período en curso**, exactamente lo que el checklist pregunta hoy
 *   ([ocurrenciasPorPreguntar]), en su orden: así cada pago dice lo mismo que
 *   `/api/payments/occurrences`. Lo que el checklist no pregunta —la ocurrencia de este período de
 *   una regla cuya anterior sigue en gracia, o la segunda del período— todavía no llegó y se resuelve
 *   después. Hereda del checklist su debilidad pasada la gracia (un pago tardío de la ocurrencia
 *   anterior se ofrece a otra regla), a propósito: acá no puede decir otra cosa que el checklist.
 * - **En un período cerrado**, las ocurrencias ANTERIORES cuya ventana pisa este período
 *   (`ocurrenciaAnteriorQuePisaElPeriodo` desde su arranque): el arriendo del 23-sep pagado el 25-sep
 *   es del arriendo, y la administración del 25 —misma categoría, cuenta y monto— no puede darse por
 *   pagada con ese mismo movimiento.
 *
 * En un período cerrado, además, **una regla no aparece antes de existir** ([existiaEnElPeriodo]).
 *
 * La clave de cada sello es el mes del vencimiento (`periodOf`), así que un pago de un período
 * pasado se lee con los mismos sellos que puso el checklist cuando ese período estaba en curso.
 */
internal fun Transaction.pagosFijosDelPeriodo(
    uid: String,
    periodo: PeriodoFinanciero,
    hoy: LocalDate,
    ajustes: PeriodSettings,
    inicioDeLaHistoria: LocalDate,
): List<PagoFijoDelPeriodo> {
    val reglas = reglasRealesDe(uid)
    if (reglas.isEmpty()) return emptyList()
    val ventana = ventanaDe(periodo, ajustes)
    val dias = epochMillisToAppDate(ventana.first)..epochMillisToAppDate(ventana.last)
    val enCurso = hoy in dias

    val delPeriodo = reglas.flatMap { regla -> ocurrenciasEntre(regla, dias, ajustes).map { regla to it } }
    if (delPeriodo.isEmpty()) return emptyList()
    val primero = if (enCurso) {
        ocurrenciasPorPreguntar(reglas, hoy, ajustes)
    } else {
        reglas.mapNotNull { regla -> ocurrenciaAnteriorQuePisaElPeriodo(dias.start, regla, ajustes)?.let { regla to it } }
    }
    val resto = delPeriodo.filterNot { it in primero }

    val lectura = leerOcurrenciasDe(uid, primero + resto, ajustes)
    val resueltas = resolverOcurrencias(primero, lectura, hoy, ajustes) + resolverOcurrencias(resto, lectura, hoy, ajustes)
    val delPeriodoResueltas = resueltas.filter { it.due in dias }
        .let { if (enCurso) it else existiaEnElPeriodo(uid, it, lectura, inicioDeLaHistoria, dias.start, hoy, ajustes) }

    // Lo que se movió de verdad en un pago sellado a mano con su movimiento: ese movimiento puede
    // estar fuera de la franja leída (el dueño eligió uno a mano), así que se busca por id.
    val selladosConMovimiento = delPeriodoResueltas
        .mapNotNull { (it.resolucion as? Resolucion.Sellada)?.fila?.eventId }
    val montoDe: Map<String, Long> = if (selladosConMovimiento.isEmpty()) emptyMap() else {
        Events.select(Events.id, Events.amount, Events.currency)
            .where { (Events.userId eq uid) and (Events.id inList selladosConMovimiento) }
            // En pesos o nada: un US$12 dicho como «$12» al lado de una regla de $53.077 mentiría.
            .filter { it[Events.currency] == MONEDA_DE_LAS_REGLAS }
            .associate { it[Events.id] to it[Events.amount] }
    }

    return delPeriodoResueltas
        .map { (regla, vencimiento, resolucion) -> pagoFijo(regla, vencimiento, resolucion, montoDe) }
        .sortedWith(compareBy({ it.vencimiento }, { it.nombre.lowercase() }, { it.ruleId }))
}

/** Todas las ocurrencias de [regla] en [dias] en las que ya corría, de la más vieja a la más nueva. */
private fun ocurrenciasEntre(regla: RecurringRule, dias: ClosedRange<LocalDate>, ajustes: PeriodSettings): List<LocalDate> {
    val ocurrencias = mutableListOf<LocalDate>()
    var vencimiento = primeraOcurrenciaDesde(dias.start, regla.dayOfMonth)
    while (vencimiento in dias) {
        if (ruleIsActiveOn(regla, vencimiento, ajustes)) ocurrencias += vencimiento
        vencimiento = primeraOcurrenciaDesde(vencimiento.plusDays(1), regla.dayOfMonth)
    }
    return ocurrencias
}

/**
 * La [LecturaDeOcurrencias] justa para resolver [pares]: los movimientos de la unión de sus ventanas
 * de emparejamiento (fuera de ellas `candidatosPuntuados` no mira nada).
 */
private fun Transaction.leerOcurrenciasDe(
    uid: String,
    pares: List<Pair<RecurringRule, LocalDate>>,
    ajustes: PeriodSettings,
): LecturaDeOcurrencias {
    val ventanas = pares.map { (_, vencimiento) -> occurrenceWindow(vencimiento, settings = ajustes) }
    return leerOcurrencias(uid, ventanas.minOf { it.start }, ventanas.maxOf { it.endInclusive })
}

/**
 * **En un período cerrado, solo las reglas que ya existían.** Un «pendiente» en un período cerrado
 * afirma «no lo pagaste»; afirmarlo de un mes en que la regla no existía es falso, y el dueño
 * escribió casi todas sus reglas después de tener meses de movimientos (SMS, extractos).
 *
 * - Con fecha de creación (`recurring_rules.created_at`): se mira **cada ocurrencia**, no el
 *   período. Una que vence antes del día en que la regla nació se calla, salvo que deje evidencia
 *   (LISTO o CON DUDAS): la regla creada el 20-sep que vence el 10 no debe el 10-sep, pero la creada
 *   el 2-oct a partir del sueldo de septiembre sí muestra ese sueldo como su septiembre.
 * - Con un arranque declarado (`activeFrom`): ya lo respeta `ruleIsActiveOn`, así que sale.
 * - Sin ninguna de las dos (las reglas de antes de la columna), sale solo con **evidencia de vida**
 *   hasta este período, y si no, se calla — el lado seguro es el silencio, no un hecho falso:
 *   1. un sello de la regla en este período o antes (aunque su movimiento haya muerto: prueba que
 *      la regla existía, no que se pagó);
 *   2. algo en este período: una ocurrencia LISTO o CON DUDAS;
 *   3. una ocurrencia anterior (desde [inicioDeLaHistoria]) que Movi emparejó solo. Sin esto, una
 *      regla que nadie tilda a mano —casi todas, desde que Movi empareja solo— escondería justo el
 *      mes que se saltó entre dos pagados, que es lo más útil que esta pantalla puede decir.
 *
 * Lo que queda escondido es un primer mes de verdad sin pagar, antes de cualquier pago registrado.
 */
private fun Transaction.existiaEnElPeriodo(
    uid: String,
    resueltas: List<OcurrenciaResuelta>,
    lectura: LecturaDeOcurrencias,
    inicioDeLaHistoria: LocalDate,
    inicioDelPeriodo: LocalDate,
    hoy: LocalDate,
    ajustes: PeriodSettings,
): List<OcurrenciaResuelta> {
    if (resueltas.isEmpty()) return resueltas
    val creadas: Map<String, LocalDate?> = RecurringRules.select(RecurringRules.id, RecurringRules.createdAt)
        .where { RecurringRules.userId eq uid }
        .associate { it[RecurringRules.id] to it[RecurringRules.createdAt]?.let(::epochMillisToAppDate) }
    val porRegla = resueltas.groupBy { it.rule.id }

    val sinDecidir = mutableListOf<RecurringRule>()
    val existian = mutableSetOf<String>()
    porRegla.forEach { (ruleId, ocurrencias) ->
        val regla = ocurrencias.first().rule
        val creada = creadas[ruleId]
        val ultimaClave = ocurrencias.maxOf { periodOf(it.due) }
        when {
            // Se decide por ocurrencia, abajo.
            creada != null -> existian += ruleId
            arranqueDeLaRegla(regla, ajustes) != null -> existian += ruleId
            lectura.sellos.keys.any { (r, clave) -> r == ruleId && clave <= ultimaClave } -> existian += ruleId
            ocurrencias.any { it.resolucion.dejaEvidencia() } -> existian += ruleId
            else -> sinDecidir += regla
        }
    }
    if (sinDecidir.isNotEmpty()) existian += conPagoAutomaticoAntes(uid, sinDecidir, inicioDeLaHistoria, inicioDelPeriodo, hoy, ajustes)
    return resueltas.filter { ocurrencia ->
        val creada = creadas[ocurrencia.rule.id]
        ocurrencia.rule.id in existian &&
            (creada == null || !ocurrencia.due.isBefore(creada) || ocurrencia.resolucion.dejaEvidencia())
    }
}

/** LISTO (sellada o emparejada) o CON DUDAS: algo en la base dice que la regla estaba viva. */
private fun Resolucion.dejaEvidencia(): Boolean = when (this) {
    is Resolucion.Sellada, is Resolucion.Emparejada -> true
    is Resolucion.Abierta -> concluyentes >= 2
    Resolucion.PorLlegar -> false
}

/**
 * Las [reglas] con alguna ocurrencia entre [desde] y el día antes de [antesDe] que Movi emparejó solo.
 * Con el mismo [resolverOcurrencias] y una lectura propia, en orden de vencimiento: esto no decide el
 * estado de nada que se muestre, solo si la regla ya estaba viva.
 */
private fun Transaction.conPagoAutomaticoAntes(
    uid: String,
    reglas: List<RecurringRule>,
    desde: LocalDate,
    antesDe: LocalDate,
    hoy: LocalDate,
    ajustes: PeriodSettings,
): Set<String> {
    if (!desde.isBefore(antesDe)) return emptySet()
    val pares = reglas
        .flatMap { regla -> ocurrenciasEntre(regla, desde..antesDe.minusDays(1), ajustes).map { regla to it } }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
    if (pares.isEmpty()) return emptySet()
    return resolverOcurrencias(pares, leerOcurrenciasDe(uid, pares, ajustes), hoy, ajustes)
        .filter { it.resolucion is Resolucion.Emparejada }
        .mapTo(mutableSetOf()) { it.rule.id }
}

private fun pagoFijo(
    regla: RecurringRule,
    vencimiento: LocalDate,
    resolucion: Resolucion,
    montoDe: Map<String, Long>,
): PagoFijoDelPeriodo {
    val base = PagoFijoDelPeriodo(
        ruleId = regla.id,
        nombre = regla.name,
        monto = regla.amount,
        esIngreso = regla.type == TransactionType.INCOME,
        vencimiento = vencimiento.toString(),
        estado = PAGO_FIJO_PENDIENTE,
    )
    return when (resolucion) {
        is Resolucion.Sellada -> {
            val eventId = resolucion.fila?.eventId
            base.copy(estado = PAGO_FIJO_LISTO, eventId = eventId, montoReal = eventId?.let { montoDe[it] })
        }
        // `resolverOcurrencias` solo empareja movimientos en pesos (ver `candidatosPuntuados`).
        is Resolucion.Emparejada ->
            base.copy(estado = PAGO_FIJO_LISTO, eventId = resolucion.evento.id, montoReal = resolucion.evento.amount)
        is Resolucion.Abierta -> if (resolucion.concluyentes >= 2) base.copy(estado = PAGO_FIJO_CON_DUDAS) else base
        Resolucion.PorLlegar -> base
    }
}

/**
 * Los períodos de la lista, **del en curso al más viejo**: hasta el del movimiento vivo más viejo del
 * usuario, con un tope de [MAX_PERIODOS]. Sin movimientos, solo el en curso — nunca una lista vacía.
 */
private fun Transaction.periodosDelUsuario(
    uid: String,
    ahora: Long,
    ajustes: PeriodSettings,
    anulados: Set<String>,
): List<PeriodoFinanciero> {
    val enCurso = periodoDe(ahora, ajustes)
    val primero = Events.timestamp.min()
    val masViejo = Events.select(primero)
        .where {
            val base = Events.userId eq uid
            if (anulados.isEmpty()) base else base and (Events.id notInList anulados.toList())
        }
        .firstOrNull()?.get(primero)
        ?.let { periodoDe(it, ajustes) }
        ?: return listOf(enCurso)
    val periodos = mutableListOf(enCurso)
    // `prefijo` («2026-09») ordena igual que el tiempo: año de cuatro cifras y mes con cero.
    while (periodos.size < MAX_PERIODOS && periodos.last().prefijo > masViejo.prefijo) {
        periodos += periodoAnterior(periodos.last())
    }
    return periodos
}

private fun resumenDe(
    periodo: PeriodoFinanciero,
    ajustes: PeriodSettings,
    enCurso: PeriodoFinanciero,
    movimientos: List<MovimientoDeFlujo>,
): ResumenDePeriodo {
    val ventana = ventanaDe(periodo, ajustes)
    val (entradas, gastoPorCategoria) = flujoDeCaja(movimientos)
    return ResumenDePeriodo(
        id = periodo.prefijo,
        nombre = tituloDelPeriodo(periodo),
        desde = epochMillisToAppDateString(ventana.first),
        hasta = epochMillisToAppDateString(ventana.last),
        // Comparado contra el arranque que le daría el corte solo: un inicio propio que
        // `inicioDelPeriodo` ignora por imposible, o que cae justo en el día del corte, no es propio.
        // Y solo el arranque: que el SIGUIENTE haya empezado antes acorta este, pero no lo hace propio.
        inicioPropio = ventana.first != ventanaDe(periodo, ajustes.copy(iniciosPropios = emptyMap())).first,
        enCurso = periodo == enCurso,
        entradas = entradas,
        salidas = gastoPorCategoria.values.sum(),
        movimientos = movimientos.size,
    )
}

/**
 * Los cinco gastos de flujo más grandes de [movimientos] (ya vivos, confirmados y en pesos), como
 * movimientos completos para que la app los abra igual que en Movimientos.
 */
private fun Transaction.losMasGrandes(
    uid: String,
    movimientos: List<MovimientoDeFlujo>,
    tipos: Map<String, AccountType>,
): List<FinancialEvent> {
    val ids = movimientos
        .filter { it.type == TransactionType.EXPENSE }
        .sortedWith(compareByDescending<MovimientoDeFlujo> { it.amount }.thenByDescending { it.timestamp }.thenBy { it.id })
        .take(MAX_MAS_GRANDES)
        .map { it.id }
    if (ids.isEmpty()) return emptyList()
    val porId = Events.selectAll()
        .where { (Events.userId eq uid) and (Events.id inList ids) }
        .associate { it[Events.id] to it.toFinancialEvent().withCashFlowFlag(tipos) }
    return ids.mapNotNull { porId[it] }
}

private const val MAX_MAS_GRANDES = 5

private fun Transaction.anuladosDe(uid: String): Set<String> =
    VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()

