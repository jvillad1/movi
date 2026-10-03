package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.cuentasConSaldo
import com.jvillada.movi.server.balance.hayMovimientosEnOtraMoneda
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoVariableDeUnPeriodo
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.cajaProyectada
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.gastoDelDiaADia
import com.jvillada.movi.shared.model.gastoVariablePorDia
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * # La caja proyectada (Ola 4 · «Movi mira adelante»)
 *
 * La proyección en sí es pura y vive en `:core` ([cajaProyectada]). Plan la calcula en el cliente
 * sobre su propia lista «Pagos del período» —la que tiene en pantalla, sin pedirla otra vez—, y lo
 * único que no puede saber solo es **el supuesto del gasto del día a día**: el promedio de los
 * períodos cerrados, que necesita su historia. Eso es lo que sirve esta ruta.
 *
 * Movi AI arma la caja entera de este lado con [cajaProyectadaDe]: las mismas funciones y la misma
 * lista (las mismas respuestas de `/api/payments/upcoming` y `/api/payments/occurrences`, pasadas
 * por el mismo [checklistDelPeriodo]).
 */
fun Route.cajaRoutes() {
    get("/api/caja-proyectada/gasto-del-dia-a-dia") {
        call.respond(gastoDelDiaADiaDe(call.userId(), AppClock.today()))
    }
}

/**
 * **El gasto del día a día del dueño**: el promedio diario de su gasto variable en los últimos
 * períodos cerrados (ver [gastoDelDiaADia]).
 *
 * Un período cuenta solo si **Movi ya llevaba sus cuentas cuando empezó**: el primer movimiento del
 * dueño es anterior a su arranque. Sin esa guarda, el período en el que se registró —con dos semanas
 * anotadas de cuatro— bajaría el promedio y la caja saldría más holgada de lo que es.
 *
 * El gasto variable de cada período es el de la tarjeta «Disponible» ([gastoVariablePorDia], con la
 * parte de cada movimiento que pagó un fijo del checklist afuera), calculado como si «hoy» fuera el
 * último día de ese período.
 */
internal suspend fun gastoDelDiaADiaDe(uid: String, hoy: LocalDate): GastoDelDiaADia {
    val ajustes = ajustesDePeriodoDe(uid)
    return dbQuery { gastoDelDiaADiaSinSuspender(uid, hoy, ajustes) }
}

internal fun Transaction.gastoDelDiaADiaSinSuspender(uid: String, hoy: LocalDate, ajustes: PeriodSettings): GastoDelDiaADia {
    val primerMovimiento: Long = Events.select(Events.timestamp.min())
        .where { Events.userId eq uid }
        .firstOrNull()?.get(Events.timestamp.min())
        ?: return GastoDelDiaADia()
    val actual = periodoDe(appDateToEpochMillis(hoy), ajustes)
    val cerrados = mutableListOf<GastoVariableDeUnPeriodo>()
    var periodo: PeriodoFinanciero = actual
    repeat(PERIODOS_HACIA_ATRAS) {
        periodo = periodoAnterior(periodo)
        val ventana = ventanaDe(periodo, ajustes)
        // Del más reciente al más viejo: el primero que Movi no cubre entero corta la búsqueda, los
        // de antes tampoco los cubre.
        val arranque: Long = ventana.first
        if (primerMovimiento > arranque) return@repeat
        val finExclusivo = ventana.last + 1
        val eventos = loadEventsBetween(uid, ventana.first, finExclusivo)
        val ultimoDia = epochMillisToAppDate(ventana.last)
        val parteFija = parteFijaDelDisponible(uid, ultimoDia, ajustes, eventos)
        val total = gastoVariablePorDia(eventos, parteFija) { epochMillisToAppDateString(it) }.values.sum()
        val dias = ChronoUnit.DAYS.between(epochMillisToAppDate(ventana.first), epochMillisToAppDate(finExclusivo)).toInt()
        cerrados += GastoVariableDeUnPeriodo(periodo = periodo.prefijo, total = total, dias = dias)
    }
    return gastoDelDiaADia(cerrados)
}

/** Cuántos períodos hacia atrás se miran: los mismos que se promedian como mucho. */
private const val PERIODOS_HACIA_ATRAS = 3

/**
 * **La caja proyectada del dueño, armada del lado del server** para Movi AI: Tu plata de
 * [patrimonioDe], la lista del período de [checklistDelPeriodo] sobre las mismas respuestas que lee
 * Plan, y el gasto del día a día de [gastoDelDiaADiaDe]. Devuelve también la lista y el supuesto,
 * que el asistente necesita para decir de dónde sale cada cifra.
 */
internal data class CajaDelDueno(
    val caja: CajaProyectada?,
    val pagos: List<PagoDelPeriodo>,
    val gasto: GastoDelDiaADia,
)

internal suspend fun cajaProyectadaDe(uid: String, hoy: LocalDate = AppClock.today()): CajaDelDueno {
    val ajustes = ajustesDePeriodoDe(uid)
    val periodo = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA, ajustes)
    val pagos = checklistDelPeriodo(
        upcoming = proximosPagos(uid, hoy),
        ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
        periodo = periodo,
        settings = ajustes,
    )
    // La tasa solo si hace falta, igual que el resumen del Inicio: sin un movimiento en otra moneda
    // no se usa, y pedirla puede esperar un timeout.
    val usdToCop = if (dbQuery { hayMovimientosEnOtraMoneda(uid) }) FxRateService.usdToCop() else 0.0
    val tuPlata = dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }.toSet()
        patrimonioDe(cuentasConSaldo(uid, anulados, usdToCop)).tuPlata
    }
    val gasto = gastoDelDiaADiaDe(uid, hoy)
    val ultimoDia = epochMillisToAppDate(ventanaDe(periodo, ajustes).last)
    return CajaDelDueno(
        caja = cajaProyectada(tuPlata, pagos, gasto.porDia, hoy.toString(), ultimoDia.toString()),
        pagos = pagos,
        gasto = gasto,
    )
}

/** Mediodía: un instante cualquiera del día que no cae en el borde de una ventana. */
private const val MEDIO_DIA = 12L * 60 * 60 * 1000
