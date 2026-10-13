package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.anteriorAMismaAltura
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.min

/**
 * **El gasto por categoría del período pasado, a la misma altura que hoy** («vs. período pasado» de
 * «¿En qué se va?»), con cuántos días se compararon. La franja la decide [anteriorAMismaAltura] (en
 * `:core`, con la zona de la app) y la suma es la de «Gastos» ([movimientosDeFlujo] + [flujoDeCaja]):
 * la misma que [DashboardSummary.spentByCategory], así que las dos cifras de cada fila se cuentan
 * igual.
 *
 * `null` si Movi no cubrió entero el período pasado: el primer movimiento del dueño es posterior a su
 * arranque. Es la misma guarda del gasto del día a día de la caja y de «lo normal» de las anomalías —
 * un período a medio anotar haría ver todo «por encima».
 */
internal fun Transaction.gastoDelAnteriorAMismaAltura(
    uid: String,
    ahora: Long,
    ajustes: PeriodSettings,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Pair<Map<String, Long>, Int>? {
    val franja = anteriorAMismaAltura(ahora, ajustes)
    val primero = Events.timestamp.min()
    val primerMovimiento = Events.select(primero)
        .where { Events.userId eq uid }
        .firstOrNull()?.get(primero)
        ?: return null
    if (primerMovimiento > franja.ventana.first) return null
    val (_, porCategoria) = flujoDeCaja(
        movimientosDeFlujo(uid, franja.ventana.first, franja.ventana.last + 1, voidedIds, accountTypeById),
    )
    return porCategoria to franja.dias
}
