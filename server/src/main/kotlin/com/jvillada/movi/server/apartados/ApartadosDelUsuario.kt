package com.jvillada.movi.server.apartados

import com.jvillada.movi.server.balance.cuentasConSaldo
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.espacios.loCompartidoDelUsuario
import com.jvillada.movi.server.routes.ocurrenciasDelPeriodo
import com.jvillada.movi.server.routes.proximosPagos
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.apartadoCubiertoPorLosFijos
import com.jvillada.movi.shared.model.apartadoFueraDeLosFijos
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.restanteDelApartado
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.time.LocalDate

/**
 * **Los apartados del usuario con su `restante` derivado**: `monto` menos lo gastado (EXPENSE, COP, sin
 * anulados) en la cuenta, en la categoría del apartado y desde su `desde`. Un apartado sin
 * categoría no consume nada solo: su restante es su monto. Un gasto repartido en un espacio consume
 * solo tu parte (R3, la regla de `montoContado`).
 *
 * Una suma por apartado, en SQL: el usuario tiene pocos y no vale traerse la historia entera.
 */
fun Transaction.apartadosDelUsuario(uid: String, voidedIds: Set<String>): List<Apartado> {
    val total = Events.amount.sum()
    // R3 · un gasto repartido consume solo TU parte (la regla de `montoContado`): a la suma en SQL
    // se le descuenta la parte ajena de los movimientos repartidos que entran en ese apartado.
    val partesAjenas = loCompartidoDelUsuario(uid).indice.parteAjenaPorEvento
    return Apartados.selectAll()
        .where { Apartados.userId eq uid }
        .orderBy(Apartados.createdAt)
        .map { fila ->
            val categoria = fila[Apartados.categoria]
            val consumido = if (categoria.isNullOrBlank()) 0L else {
                fun filtro() = (Events.userId eq uid) and
                    (Events.accountId eq fila[Apartados.accountId]) and
                    (Events.type eq "EXPENSE") and
                    (Events.currency eq "COP") and
                    (Events.category eq categoria) and
                    (Events.timestamp greaterEq fila[Apartados.desde])
                val entero = Events.select(total)
                    .where { if (voidedIds.isEmpty()) filtro() else filtro() and (Events.id notInList voidedIds.toList()) }
                    .firstOrNull()?.get(total) ?: 0L
                val ajena = if (partesAjenas.isEmpty()) 0L else {
                    Events.select(Events.id, Events.amount)
                        .where { filtro() and (Events.id inList partesAjenas.keys.toList()) }
                        .filterNot { it[Events.id] in voidedIds }
                        .sumOf { (partesAjenas[it[Events.id]] ?: 0L).coerceIn(0L, it[Events.amount]) }
                }
                entero - ajena
            }
            Apartado(
                id = fila[Apartados.id],
                accountId = fila[Apartados.accountId],
                proposito = fila[Apartados.proposito],
                monto = fila[Apartados.monto],
                categoria = categoria,
                desde = fila[Apartados.desde],
                restante = restanteDelApartado(fila[Apartados.monto], consumido),
                objetivo = fila[Apartados.objetivo],
                mesDelGasto = fila[Apartados.mesDelGasto],
            )
        }
}

/**
 * **El patrimonio del usuario con sus apartados**: `patrimonioDe` (la regla única de :core) sobre
 * las cuentas con su saldo derivado y los apartados con su restante. Quien necesite «Tu plata» o lo
 * apartado en el server lo pide acá, para que todos hablen de la misma cifra.
 */
fun Transaction.patrimonioDelUsuario(uid: String, voidedIds: Set<String>, usdToCop: Double): Patrimonio =
    tuPlataConApartados(uid, voidedIds, usdToCop).patrimonio

/**
 * Las cuentas y los apartados que arman el patrimonio, para quien además tenga que cruzar lo apartado
 * con los pagos del período (ver [apartadoFueraDeLosFijos]): «Te quedan» y la caja proyectada.
 */
class TuPlataConApartados(val cuentas: List<Account>, val apartados: List<Apartado>) {
    val patrimonio: Patrimonio = patrimonioDe(cuentas, apartados)

    /** Lo apartado que «Te quedan» resta: sin lo que los pagos pendientes de [checklist] ya restan. */
    fun apartadoParaElDisponible(checklist: List<PagoDelPeriodo>): Long =
        apartadoFueraDeLosFijos(cuentas, apartados, checklist)

    /** Lo apartado que el pago pendiente ya resta el día que vence: la caja proyectada lo devuelve. */
    fun apartadoCubierto(checklist: List<PagoDelPeriodo>): Long =
        apartadoCubiertoPorLosFijos(cuentas, apartados, checklist)
}

fun Transaction.tuPlataConApartados(uid: String, voidedIds: Set<String>, usdToCop: Double): TuPlataConApartados =
    TuPlataConApartados(cuentasConSaldo(uid, voidedIds, usdToCop), apartadosDelUsuario(uid, voidedIds))

/** ¿Algún apartado del usuario lo consume una categoría? Solo esos pueden cruzarse con un pago. */
fun Transaction.hayApartadosConCategoria(uid: String): Boolean =
    !Apartados.selectAll().where { (Apartados.userId eq uid) and Apartados.categoria.isNotNull() }.empty()

/**
 * **La lista «Pagos del período» de [hoy]**, para cruzarla con los apartados — o vacía si ningún
 * apartado tiene categoría, porque entonces no hay cruce posible y armarla cuesta dos lecturas en la
 * ruta más caliente (el resumen del Inicio). Es la misma lista que lee Plan y la caja proyectada.
 */
suspend fun pagosQuePuedenCruzarApartados(uid: String, hoy: LocalDate, ajustes: PeriodSettings): List<PagoDelPeriodo> {
    if (!dbQuery { hayApartadosConCategoria(uid) }) return emptyList()
    return checklistDelPeriodo(
        upcoming = proximosPagos(uid, hoy),
        ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
        periodo = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA, ajustes),
        settings = ajustes,
    )
}

/** Mediodía: un instante del día que no cae en el borde de una ventana. */
private const val MEDIO_DIA = 12L * 60 * 60 * 1000
