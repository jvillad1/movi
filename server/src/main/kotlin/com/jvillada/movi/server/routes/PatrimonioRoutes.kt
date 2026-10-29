package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.conSaldos
import com.jvillada.movi.server.balance.hayMovimientosEnOtraMoneda
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.MESES_DE_HISTORIA
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.signedDelta
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.time.LocalDate

/**
 * # `GET /api/patrimonio/historia`: el patrimonio en el tiempo (Ola 4 · Ver la plata)
 *
 * El patrimonio neto al cierre de cada uno de los últimos [MESES_DE_HISTORIA] meses (o desde el mes
 * del primer movimiento) y el de hoy. Ver [HistoriaDelPatrimonio] para lo que la historia no sabe y
 * no finge saber (bienes, dólares, apartados).
 *
 * **Una sola regla del patrimonio**: cada punto es [patrimonioDe] sobre las cuentas con el saldo de
 * ese instante, armadas con [conSaldos] —la misma función que arma `GET /api/accounts` y el resumen
 * del Inicio—. El último punto, entonces, es exactamente el patrimonio neto que Patrimonio dice hoy.
 *
 * **Una consulta agregada**: los movimientos del usuario (sin anulados) se suman en SQL por cuenta,
 * tipo, moneda y **tramo** —un `CASE` que dice entre qué cierres de mes cayó cada uno—, así que
 * vuelven unas pocas filas por cuenta y no la historia entera, ni doce consultas.
 */
fun Route.patrimonioRoutes() {
    get("/api/patrimonio/historia") {
        call.respond(historiaDelPatrimonioDe(call.userId(), AppClock.today()))
    }
}

/**
 * **La historia del patrimonio de [uid]** a [hoy]. Ver el KDoc del archivo.
 *
 * La tasa del dólar se pide solo si el dueño tiene algún movimiento en otra moneda (como el resumen
 * del Inicio): `FxRateService` puede pegarle a la red y sin dólares no se usa.
 */
internal suspend fun historiaDelPatrimonioDe(uid: String, hoy: LocalDate): HistoriaDelPatrimonio {
    val conDolares = dbQuery { hayMovimientosEnOtraMoneda(uid) }
    val usdToCop = if (conDolares) FxRateService.usdToCop() else 0.0
    return dbQuery { historiaDelPatrimonioSinSuspender(uid, hoy, usdToCop, conDolares) }
}

internal fun Transaction.historiaDelPatrimonioSinSuspender(
    uid: String,
    hoy: LocalDate,
    usdToCop: Double,
    conDolares: Boolean,
): HistoriaDelPatrimonio {
    val primero = Events.timestamp.min()
    val primerMovimiento = Events.select(primero)
        .where { Events.userId eq uid }
        .firstOrNull()?.get(primero)
        ?: return HistoriaDelPatrimonio(conLaTrmDeHoy = conDolares)

    // Los cierres: el arranque (en la zona de la app) del mes siguiente a cada uno de los últimos
    // doce meses cerrados, de más viejo a más nuevo. El último es el arranque del mes en curso.
    val mesEnCurso = hoy.withDayOfMonth(1)
    val meses = (MESES_DE_HISTORIA downTo 1).map { mesEnCurso.minusMonths(it.toLong()) }
    val cierres = meses.map { appDateToEpochMillis(it.plusMonths(1)) }

    val voidedIds = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
    val sumas = sumasPorTramo(uid, cierres, voidedIds)
    // Vuelta 60: lo mismo, solo los ajustes de saldo y las aperturas, para decir cuánto del neto son
    // correcciones de lo que Movi sabía (ver `PuntoDelPatrimonio.ajustes`). Una consulta más.
    val sumasDeAjustes = sumasPorTramo(uid, cierres, voidedIds, soloAjustes = true)

    val cuentas = Accounts.selectAll()
        .where { Accounts.userId eq uid }
        .mapNotNull { row -> runCatching { row.toAccount() }.getOrNull() }

    fun patrimonioHasta(tramo: Int, sinAjustes: Boolean = false): Long {
        // El saldo de cada cuenta con todo lo que cayó antes del cierre: los tramos 0..tramo-1.
        fun delta(lista: List<SumaDelTramo>, cuenta: Account): Map<String, Long> =
            lista.filter { it.accountId == cuenta.id && it.tramo < tramo }
                .groupBy { it.moneda }
                .mapValues { (_, l) -> l.sumOf { signedDelta(cuenta.type, it.tipo, it.monto) } }
        val conSaldo: List<Account> = cuentas.map { cuenta ->
            val todo = delta(sumas, cuenta)
            val saldos = if (!sinAjustes) {
                todo
            } else {
                val ajustes = delta(sumasDeAjustes, cuenta)
                (todo.keys + ajustes.keys).associateWith { (todo[it] ?: 0L) - (ajustes[it] ?: 0L) }
            }
            conSaldos(cuenta, saldos, usdToCop)
        }
        // Sin apartados: no cambian el neto (ver `Patrimonio.apartado`).
        return patrimonioDe(conSaldo).neto
    }

    // El neto y cuánto de él son ajustes: el neto con todo menos el neto sin ellos (no se supone que
    // `patrimonioDe` sea una suma: se calcula dos veces).
    fun punto(mes: String, fecha: String, tramo: Int, esHoy: Boolean = false): PuntoDelPatrimonio {
        val neto = patrimonioHasta(tramo)
        val ajustes = if (sumasDeAjustes.isEmpty()) 0L else neto - patrimonioHasta(tramo, sinAjustes = true)
        return PuntoDelPatrimonio(mes = mes, fecha = fecha, neto = neto, esHoy = esHoy, ajustes = ajustes)
    }

    val puntos = meses.mapIndexedNotNull { i, mes ->
        // Un mes que cerró antes del primer movimiento no tiene datos: no se dibuja como cero.
        if (cierres[i] <= primerMovimiento) return@mapIndexedNotNull null
        punto(mes = mes.toString().take(7), fecha = mes.plusMonths(1).minusDays(1).toString(), tramo = i + 1)
    } + punto(
        mes = hoy.toString().take(7),
        fecha = hoy.toString(),
        // Todos los tramos: lo mismo que suma `GET /api/accounts` hoy.
        tramo = cierres.size + 1,
        esHoy = true,
    )
    return HistoriaDelPatrimonio(
        puntos = puntos,
        conLaTrmDeHoy = conDolares,
        primerMovimiento = primerMovimientoReal(uid, voidedIds)?.let { epochMillisToAppDateString(it) },
        conBienes = cuentas.any { it.bien != null },
    )
}

/** Las categorías que corrigen lo que Movi sabía: no son plata ganada ni perdida (vuelta 60). */
private val CATEGORIAS_DE_AJUSTE = listOf(ADJUSTMENT_CATEGORY, OPENING_CATEGORY)

/**
 * **El primer movimiento real** de [uid] (vuelta 60): el más viejo, sin anulados, que no es una
 * apertura ni un ajuste de saldo. Es el arranque de «Tus períodos» (sus períodos vacíos del principio
 * son los de antes del primer movimiento de flujo), no el primer punto de la curva.
 */
private fun Transaction.primerMovimientoReal(uid: String, voidedIds: List<String>): Long? {
    val primero = Events.timestamp.min()
    return Events.select(primero)
        .where {
            val base = (Events.userId eq uid) and (Events.category notInList CATEGORIAS_DE_AJUSTE)
            if (voidedIds.isEmpty()) base else base and (Events.id notInList voidedIds)
        }
        .firstOrNull()?.get(primero)
}

/** Lo que movió una cuenta en un tramo, en una moneda y un tipo. */
private class SumaDelTramo(
    val accountId: String,
    val tramo: Int,
    val tipo: TransactionType,
    val moneda: String,
    val monto: Long,
)

/**
 * Los movimientos de [uid] sin anulados, sumados por cuenta, tipo, moneda y tramo: el tramo `k` son
 * los que cayeron antes de `cierres[k]` y no antes de `cierres[k - 1]`; el último, lo posterior al
 * último cierre. **Una consulta.**
 *
 * Los cierres van como literales (no como parámetros) a propósito: el `CASE` se repite en el
 * `SELECT` y en el `GROUP BY`, y Postgres solo los reconoce como la misma expresión si el texto es
 * idéntico; con parámetros serían `$1` en uno y `$13` en el otro.
 */
private fun Transaction.sumasPorTramo(
    uid: String,
    cierres: List<Long>,
    voidedIds: List<String>,
    /** Solo los ajustes de saldo y las aperturas ([CATEGORIAS_DE_AJUSTE]). */
    soloAjustes: Boolean = false,
): List<SumaDelTramo> {
    val primerCaso = Case().When(Events.timestamp less longLiteral(cierres.first()), intLiteral(0))
    val casos = cierres.drop(1).foldIndexed(primerCaso) { i, caso, cierre ->
        caso.When(Events.timestamp less longLiteral(cierre), intLiteral(i + 1))
    }
    val tramo = casos.Else(intLiteral(cierres.size))
    val total = Events.amount.sum()
    return Events.select(Events.accountId, Events.type, Events.currency, tramo, total)
        .where {
            val delUsuario = Events.userId eq uid
            val base = if (soloAjustes) delUsuario and (Events.category inList CATEGORIAS_DE_AJUSTE) else delUsuario
            if (voidedIds.isEmpty()) base else base and (Events.id notInList voidedIds)
        }
        .groupBy(Events.accountId, Events.type, Events.currency, tramo)
        .mapNotNull { row ->
            val tipo = runCatching { TransactionType.valueOf(row[Events.type]) }.getOrNull() ?: return@mapNotNull null
            SumaDelTramo(row[Events.accountId], row[tramo], tipo, row[Events.currency], row[total] ?: 0L)
        }
}
