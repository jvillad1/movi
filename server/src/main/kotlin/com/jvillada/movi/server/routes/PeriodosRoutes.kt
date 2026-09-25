package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.MONEDA_DE_LAS_REGLAS
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.server.reminders.ocurrenciaEnJuego
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
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
 * ([saldoDeTuPlataAntesDe]). Y cada ventana sale de [ventanaDe] con el corte y los inicios propios
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
    // del arranque del más viejo al final del en curso, repartida después por [periodoDe] — la misma
    // función que decide en qué período cae un movimiento en Movimientos.
    val movimientos = movimientosDeFlujo(
        uid = uid,
        desde = ventanaDe(periodos.last(), ajustes).first,
        hastaExclusivo = ventanaDe(enCurso, ajustes).last + 1,
        voidedIds = anulados,
        accountTypeById = accountTypesFor(uid),
    )
    val porPeriodo = movimientos.groupBy { periodoDe(it.timestamp, ajustes) }
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
    val periodo = periodoDelId(id) ?: return null
    val anulados = anuladosDe(uid)
    val periodos = periodosDelUsuario(uid, ahora, ajustes, anulados)
    if (periodo !in periodos) return null

    val ventana = ventanaDe(periodo, ajustes)
    val desde = ventana.first
    // `ventanaDe` incluye su último milisegundo; las consultas de acá usan fin exclusivo.
    val hasta = ventana.last + 1
    val tipos = accountTypesFor(uid)
    val movimientos = movimientosDeFlujo(uid, desde, hasta, anulados, tipos)
    val (_, gastoPorCategoria) = flujoDeCaja(movimientos)
    val cuentas = cuentasDelDisponible(uid)

    return DetalleDePeriodo(
        resumen = resumenDe(periodo, ajustes, periodos.first(), movimientos),
        porCategoria = gastoPorCategoria.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { GastoDeCategoria(it.key, it.value) },
        pagosFijos = pagosFijosDelPeriodo(uid, periodo, epochMillisToAppDate(ahora), ajustes),
        // Los presupuestos de HOY: no hay historia de cuánto valía un tope antes. Lo gastado cruza por
        // nombre de categoría, como las barras de Presupuestos (`spentByCategory[category]`).
        presupuestos = Budgets.selectAll()
            .where { Budgets.userId eq uid }
            .map { PresupuestoDelPeriodo(it[Budgets.category], it[Budgets.monthlyLimit], gastoPorCategoria[it[Budgets.category]] ?: 0L) }
            .sortedBy { it.category.lowercase() },
        masGrandes = losMasGrandes(uid, movimientos, tipos),
        tuPlataAlEmpezar = saldoDeTuPlataAntesDe(uid, desde, anulados, cuentas),
        tuPlataAlCerrar = saldoDeTuPlataAntesDe(uid, hasta, anulados, cuentas),
    )
}

/**
 * **Los pagos fijos de [periodo]**: cada recurrente real con el vencimiento que cae adentro y cómo
 * quedó — sellado o emparejado por Movi ([PAGO_FIJO_LISTO]), con dos o más movimientos concluyentes
 * ([PAGO_FIJO_CON_DUDAS]), o sin nada ([PAGO_FIJO_PENDIENTE], también si todavía no llegó).
 *
 * Decide [resolverOcurrencias], el mismo que arma el checklist: no hay un tercer emparejador. Y en
 * el **período en curso** se resuelve primero exactamente lo que el checklist pregunta hoy
 * ([ocurrenciasPorPreguntar]), en su orden: así la reserva de movimientos entre reglas es la misma y
 * cada pago dice lo mismo que `/api/payments/occurrences`. Lo único que el checklist no pregunta es
 * la ocurrencia de este período de una regla cuya anterior sigue en gracia; esa todavía no llegó
 * (por eso el checklist pregunta por la otra) y se resuelve después, con la reserva ya hecha.
 *
 * La clave de cada sello es el mes del vencimiento (`periodOf`), así que un pago de un período
 * pasado se lee con los mismos sellos que puso el checklist cuando ese período estaba en curso. Lo
 * que Movi emparejó solo en ese entonces se vuelve a derivar igual, porque la evidencia es la misma.
 */
internal fun Transaction.pagosFijosDelPeriodo(
    uid: String,
    periodo: PeriodoFinanciero,
    hoy: LocalDate,
    ajustes: PeriodSettings,
): List<PagoFijoDelPeriodo> {
    val reglas = reglasRealesDe(uid)
    if (reglas.isEmpty()) return emptyList()
    val ventana = ventanaDe(periodo, ajustes)
    val dias = epochMillisToAppDate(ventana.first)..epochMillisToAppDate(ventana.last)

    val delPeriodo = reglas.mapNotNull { regla ->
        val vencimiento = ocurrenciaEnJuego(dias.start, regla.dayOfMonth, ajustes) ?: return@mapNotNull null
        if (!ruleIsActiveOn(regla, vencimiento, ajustes)) return@mapNotNull null
        regla to vencimiento
    }
    if (delPeriodo.isEmpty()) return emptyList()
    val primero = if (hoy in dias) ocurrenciasPorPreguntar(reglas, hoy, ajustes) else delPeriodo
    val resto = delPeriodo.filterNot { it in primero }

    // Solo la franja donde puede haber candidatos: las ventanas de emparejamiento de lo que se va a
    // resolver (fuera de ellas `candidatosPuntuados` no mira nada).
    val ventanas = (primero + resto).map { (_, vencimiento) -> occurrenceWindow(vencimiento, settings = ajustes) }
    val lectura = leerOcurrencias(uid, ventanas.minOf { it.start }, ventanas.maxOf { it.endInclusive })
    val resueltas = resolverOcurrencias(primero, lectura, hoy, ajustes) + resolverOcurrencias(resto, lectura, hoy, ajustes)
    val delPeriodoResueltas = resueltas.filter { it.due in dias }

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
    tipos: Map<String, com.jvillada.movi.shared.model.AccountType>,
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

/** `"2026-09"` → el período; cualquier otra cosa → `null` (y la ruta contesta 404). */
private fun periodoDelId(id: String): PeriodoFinanciero? {
    val partes = ID_DE_PERIODO.matchEntire(id)?.groupValues ?: return null
    return PeriodoFinanciero(partes[1].toInt(), partes[2].toInt())
}

private val ID_DE_PERIODO = Regex("""^(\d{4})-(0[1-9]|1[0-2])$""")
