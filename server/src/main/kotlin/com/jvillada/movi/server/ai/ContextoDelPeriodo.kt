package com.jvillada.movi.server.ai

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * # Lo que el asistente tiene que saber para contestar sobre la plata del dueño
 *
 * Hasta acá `buildUserContext` le pasaba cuatro cosas: tres cifras del mes, las cuentas, los
 * presupuestos y los documentos. Con eso contesta «¿cuánto tengo?» y poco más: no sabía en qué se
 * fue la plata, qué recurrentes tiene, cuáles ya pagó, a qué tasa está endeudado ni a qué se
 * comprometió. El dueño lo pidió así: *«el asistente de IA debería poder tener el contexto de
 * todos los datos de la app, no ser un chat de IA y ya»*.
 *
 * Esto arma la otra mitad. Tres reglas que no se negocian:
 *
 * 1. **Lo mismo que ve en la pantalla.** Las cifras salen de las mismas consultas que el Inicio:
 *    anulados afuera, «Por confirmar» afuera, el pago de tarjeta no cuenta como gasto. Si el
 *    asistente dijera otra cosa que la pantalla, el error sería peor que no tener asistente.
 * 2. **El período del dueño, no el mes de calendario.** Su corte es el 25; un contexto que hable
 *    de «septiembre» contesta sobre una ventana que él no usa.
 * 3. **Acotado.** Van los recurrentes, los créditos, las suscripciones activas y las metas — no
 *    los movimientos uno por uno. Miles de líneas de historia no caben, y lo que se cae del corte
 *    se cae en silencio: peor que no estar.
 */

/** Un recurrente del dueño, con lo único que hace falta para hablar de él. */
internal data class RecurrenteParaContexto(
    val nombre: String,
    val categoria: String,
    val monto: Long,
    val dia: Int,
    val esIngreso: Boolean,
    val yaOcurrioEnElPeriodo: Boolean,
)

/** Un crédito con sus condiciones: sin la tasa y la cuota no se puede opinar de una deuda. */
internal data class CreditoParaContexto(
    val cuenta: String,
    val banco: String,
    val tasaEa: Double,
    val cuota: Long,
    val plazoMeses: Int,
    val dia: Int,
    val seguroMensual: Long?,
    val porNomina: Boolean,
    val loPaga: String?,
)

internal data class ContextoDelPeriodo(
    val rango: String,
    val diasQueQuedan: Int,
    val corte: Int,
    val ingresos: Long,
    val gastoPorCategoria: Map<String, Long>,
    val recurrentes: List<RecurrenteParaContexto>,
    val creditos: List<CreditoParaContexto>,
    val suscripciones: List<Triple<String, Long, Int>>,
    val metas: List<Triple<String, Long, String?>>,
    val smsPorConfirmar: Int,
    val movimientosPorConfirmar: Int,
)

/** Todo lo de arriba, leído de la base en una sola pasada. */
internal suspend fun contextoDelPeriodoDe(uid: String): ContextoDelPeriodo {
    val ajustes: PeriodSettings = ajustesDePeriodoDe(uid)
    val ventana = currentPeriodWindow(ajustes)
    val periodoIso = periodoIsoDe(ventana.startMillis)

    return dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }.toSet()
        val tipoDeCuenta = accountTypesFor(uid)

        val delPeriodo = Events.selectAll()
            .where {
                (Events.userId eq uid) and
                    (Events.currency eq "COP") and
                    (Events.timestamp greaterEq ventana.startMillis) and
                    (Events.timestamp less ventana.endMillisExclusive)
            }
            .filterNot { it[Events.id] in anulados }
            .filterNot { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }
            .filter { fila ->
                val tipo = tipoDeCuenta[fila[Events.accountId]]
                tipo == null || isCashFlow(tipo, TransactionType.valueOf(fila[Events.type]), fila[Events.category])
            }

        val ingresos = delPeriodo
            .filter { it[Events.type] == TransactionType.INCOME.name }
            .sumOf { it[Events.amount] }
        val gastoPorCategoria = delPeriodo
            .filter { it[Events.type] == TransactionType.EXPENSE.name }
            .groupBy { it[Events.category] }
            .mapValues { (_, filas) -> filas.sumOf { it[Events.amount] } }

        val selladas = RecurringOccurrences.selectAll()
            .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.period eq periodoIso) }
            .map { it[RecurringOccurrences.ruleId] }
            .toSet()
        val recurrentes = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { fila ->
            RecurrenteParaContexto(
                nombre = fila[RecurringRules.name],
                categoria = fila[RecurringRules.category],
                monto = fila[RecurringRules.amount],
                dia = fila[RecurringRules.dayOfMonth],
                esIngreso = fila[RecurringRules.type] == TransactionType.INCOME.name,
                yaOcurrioEnElPeriodo = fila[RecurringRules.id] in selladas,
            )
        }

        val nombreDeCuenta = Accounts.selectAll().where { Accounts.userId eq uid }
            .associate { it[Accounts.id] to it[Accounts.name] }
        val creditos = Credits.selectAll().where { Credits.userId eq uid }.map { fila ->
            CreditoParaContexto(
                cuenta = nombreDeCuenta[fila[Credits.accountId]] ?: fila[Credits.accountId],
                banco = fila[Credits.bank],
                tasaEa = fila[Credits.rateEa],
                cuota = fila[Credits.installment],
                plazoMeses = fila[Credits.termMonths],
                dia = fila[Credits.dayOfMonth],
                seguroMensual = fila[Credits.insuranceMonthly],
                porNomina = fila[Credits.payrollDeduction] == true,
                loPaga = fila[Credits.paidBy],
            )
        }

        val suscripciones = Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .filter { it[Subscriptions.status] != "DISMISSED" }
            .map { Triple(it[Subscriptions.displayName], it[Subscriptions.amount], it[Subscriptions.dayOfMonth]) }

        val metas = Goals.selectAll().where { Goals.userId eq uid }
            .map { Triple(it[Goals.name], it[Goals.target], it[Goals.targetDate]) }

        val smsPendientes = SmsMessages.selectAll()
            .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq "pending") }
            .count().toInt()
        val porConfirmar = Events.selectAll().where { Events.userId eq uid }
            .filterNot { it[Events.id] in anulados }
            .count { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }

        ContextoDelPeriodo(
            rango = rangoLegible(ventana.startMillis, ventana.endMillisExclusive),
            diasQueQuedan = diasHasta(ventana.endMillisExclusive),
            corte = ajustes.cutoffDay,
            ingresos = ingresos,
            gastoPorCategoria = gastoPorCategoria,
            recurrentes = recurrentes,
            creditos = creditos,
            suscripciones = suscripciones,
            metas = metas,
            smsPorConfirmar = smsPendientes,
            movimientosPorConfirmar = porConfirmar,
        )
    }
}

/** El bloque de texto que se le pasa al asistente. Vacío si no hay nada que contar. */
internal fun ContextoDelPeriodo.render(): String = buildString {
    appendLine("== El período en curso ==")
    appendLine("- Va del $rango (el dueño cierra su mes el día $corte, no el 30).")
    appendLine("- Quedan $diasQueQuedan días de este período.")
    appendLine("- Ingresos del período: \$$ingresos")
    appendLine("- Gastos del período: \$${gastoPorCategoria.values.sum()}")
    appendLine()

    appendLine("== En qué se fue la plata este período ==")
    if (gastoPorCategoria.isEmpty()) {
        appendLine("- (todavía sin gastos registrados en este período)")
    } else {
        gastoPorCategoria.entries.sortedByDescending { it.value }
            .forEach { (categoria, monto) -> appendLine("- $categoria: \$$monto") }
    }
    appendLine()

    appendLine("== Pagos recurrentes ==")
    if (recurrentes.isEmpty()) {
        appendLine("- (sin recurrentes cargados)")
    } else {
        recurrentes.sortedBy { it.dia }.forEach { r ->
            val que = if (r.esIngreso) "entra" else "sale"
            val estado = if (r.yaOcurrioEnElPeriodo) "YA ocurrió en este período" else "TODAVÍA no ocurrió en este período"
            appendLine("- ${r.nombre} (${r.categoria}): \$${r.monto} $que el día ${r.dia} — $estado")
        }
    }
    appendLine()

    if (creditos.isNotEmpty()) {
        appendLine("== Créditos, con sus condiciones ==")
        creditos.forEach { c ->
            val seguro = c.seguroMensual?.let { ", incluye seguro de vida \$$it al mes" }.orEmpty()
            val nomina = if (c.porNomina) ", se descuenta de la nómina" else ""
            val paga = c.loPaga?.let { ", lo paga $it" }.orEmpty()
            appendLine(
                "- ${c.cuenta} (${c.banco}): cuota \$${c.cuota} el día ${c.dia}, " +
                    "tasa ${c.tasaEa} % EA, plazo ${c.plazoMeses} meses$seguro$nomina$paga",
            )
        }
        appendLine()
    }

    if (suscripciones.isNotEmpty()) {
        appendLine("== Suscripciones ==")
        suscripciones.sortedByDescending { it.second }.forEach { (nombre, monto, dia) ->
            appendLine("- $nombre: \$$monto el día $dia")
        }
        appendLine()
    }

    if (metas.isNotEmpty()) {
        appendLine("== Metas de ahorro ==")
        metas.forEach { (nombre, objetivo, fecha) ->
            val cuando = fecha?.let { " para el $it" }.orEmpty()
            appendLine("- $nombre: objetivo \$$objetivo$cuando")
        }
        appendLine()
    }

    if (smsPorConfirmar > 0 || movimientosPorConfirmar > 0) {
        appendLine("== Lo que está esperando al dueño ==")
        if (smsPorConfirmar > 0) {
            appendLine("- $smsPorConfirmar mensajes del banco sin confirmar (no cuentan en las cifras de arriba)")
        }
        if (movimientosPorConfirmar > 0) {
            appendLine("- $movimientosPorConfirmar movimientos en «Por confirmar» (tampoco cuentan)")
        }
        appendLine()
    }
}

// ── Ayudas ───────────────────────────────────────────────────────────────────

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

private fun rangoLegible(inicio: Long, finExclusivo: Long): String {
    val zona: ZoneId = AppClock.zone
    val d1 = Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate()
    val d2 = Instant.ofEpochMilli(finExclusivo - 1).atZone(zona).toLocalDate()
    return "${d1.dayOfMonth} de ${MESES[d1.monthValue - 1]} al ${d2.dayOfMonth} de ${MESES[d2.monthValue - 1]}"
}

private fun diasHasta(finExclusivo: Long): Int {
    val zona: ZoneId = AppClock.zone
    val hoy = AppClock.now(zona).toLocalDate()
    val ultimo = Instant.ofEpochMilli(finExclusivo - 1).atZone(zona).toLocalDate()
    return ChronoUnit.DAYS.between(hoy, ultimo).toInt().coerceAtLeast(0)
}

/** «2026-09», el período del vencimiento con el que se sellan las ocurrencias. */
private fun periodoIsoDe(inicio: Long): String {
    val fecha = Instant.ofEpochMilli(inicio).atZone(AppClock.zone).toLocalDate()
    return "%04d-%02d".format(fecha.year, fecha.monthValue)
}
