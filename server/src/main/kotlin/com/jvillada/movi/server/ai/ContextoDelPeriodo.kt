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
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.montoMensualEquivalente
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

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
 * 3. **Acotado, y cuando no alcanza lo DICE.** Van los recurrentes, los créditos, las
 *    suscripciones activas, las metas y —desde la ola 23— los movimientos del período **uno por
 *    uno**. Los movimientos entraron porque sin ellos el asistente contesta totales y nada más:
 *    no puede decir en qué restaurante, ni si un gasto se repitió, ni qué hay detrás de
 *    «Comida: \$1.200.000», que es justo lo que el dueño pregunta. Lo que no entra es la
 *    HISTORIA: solo el período en curso, y con tope ([CUANTOS_MOVIMIENTOS_CABEN]).
 *
 *    El tope se anuncia en el texto («y N más que no caben»). Un corte silencioso es peor que no
 *    tener el dato: el asistente sumaría lo que ve y contestaría una cifra que no coincide con la
 *    pantalla, sin que nada lo delate.
 */

/**
 * **Cuántos movimientos del período entran en el contexto.** Con los datos del dueño (unos 40 por
 * período) no se alcanza; existe para el día que sí, y para que ese día el texto lo diga en vez de
 * cortar callado.
 */
internal const val CUANTOS_MOVIMIENTOS_CABEN = 150

/** Un movimiento del período, como lo lee el asistente. */
internal data class MovimientoParaContexto(
    val fecha: String,
    val nombre: String,
    val categoria: String,
    val monto: Long,
    val esIngreso: Boolean,
    val cuenta: String,
)

/** Un recurrente del dueño, con lo único que hace falta para hablar de él. */
internal data class RecurrenteParaContexto(
    val nombre: String,
    val categoria: String,
    val monto: Long,
    val dia: Int,
    val esIngreso: Boolean,
    val yaOcurrioEnElPeriodo: Boolean,
)

/**
 * Una suscripción **activa**, ya traída a pesos y al mes.
 *
 * Las tres cosas que este tipo existe para que no se pierdan —y que se perdían cuando acá viajaba
 * un `Triple(nombre, amount, dia)` crudo—:
 *
 *  - **La moneda.** `amount` está en la moneda nativa: un Spotify de US11,99 llegaba como «11» y
 *    el asistente lo leía como once pesos.
 *  - **La periodicidad.** Un cobro ANUAL llegaba como si fuera del mes, así que el asistente
 *    contaba doce veces al año lo que se cobra una.
 *  - **El estado.** Una CANDIDATE es una sospecha del detector que el dueño todavía no aceptó;
 *    contarla como un hecho le pone en la boca al asistente un gasto que quizá no existe.
 *
 * [montoMensualCop] se arma como en `SubscriptionRoutes.resultFor`: se prorratea PRIMERO en la
 * moneda nativa ([montoMensualEquivalente]) y se convierte DESPUÉS con la TRM. Al revés, el
 * redondeo del medio separaría por pesos lo que dice el asistente de lo que dice la pantalla.
 */
internal data class SuscripcionParaContexto(
    val nombre: String,
    val montoMensualCop: Long,
    val dia: Int,
    val moneda: String,
    val montoNativo: Long,
    val esAnual: Boolean,
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
    val suscripciones: List<SuscripcionParaContexto>,
    val metas: List<Triple<String, Long, String?>>,
    val smsPorConfirmar: Int,
    val movimientosPorConfirmar: Int,
    /** Los del período, del más nuevo al más viejo, ya recortados a [CUANTOS_MOVIMIENTOS_CABEN]. */
    val movimientos: List<MovimientoParaContexto> = emptyList(),
    /** Cuántos quedaron afuera por el tope. Se dice en el texto; nunca se calla. */
    val movimientosQueNoCaben: Int = 0,
)

/** Todo lo de arriba, leído de la base en una sola pasada. */
internal suspend fun contextoDelPeriodoDe(uid: String): ContextoDelPeriodo {
    val ajustes: PeriodSettings = ajustesDePeriodoDe(uid)
    val ventana = currentPeriodWindow(ajustes)
    val hoy = AppClock.today()
    // La TRM, antes de abrir la transacción: es una llamada de red (cacheada por día) y adentro de
    // `dbQuery` no se puede suspender. No cuesta una llamada extra — `buildUserContext` ya la pide
    // para valuar las cuentas y las dos leen la misma caché.
    val tasa = FxRateService.usdToCop()

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

        // **El sello se calcula POR REGLA, no una sola vez para todo el período.**
        //
        // `recurring_occurrences.period` es el mes de calendario del VENCIMIENTO (ver `periodOf`),
        // y ese mes no tiene por qué ser el del arranque del período: con corte 25, el período va
        // del 25 de agosto al 24 de septiembre, así que un arriendo del día 5 vence el 5 de
        // septiembre y se sella «2026-09» mientras el arranque dice «2026-08». Preguntando por el
        // mes del arranque, el asistente afirmaba que el arriendo no está pagado cuando sí lo
        // está —o al revés—, que es peor que no tener el dato.
        //
        // La derivación buena es la misma que usa `/api/payments/occurrences`:
        // `periodOf(ocurrenciaPorPreguntar(hoy, regla, ajustes))`. Una sola forma de nombrar la
        // cuota en juego, para que las dos pantallas no puedan contestar distinto sobre el mismo
        // pago.
        val selladasPorRegla: Map<String, Set<String>> = RecurringOccurrences.selectAll()
            .where { RecurringOccurrences.userId eq uid }
            .groupBy({ it[RecurringOccurrences.ruleId] }, { it[RecurringOccurrences.period] })
            .mapValues { (_, periodos) -> periodos.toSet() }
        val recurrentes = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { fila ->
            val regla = RecurringRule(
                id = fila[RecurringRules.id],
                name = fila[RecurringRules.name],
                category = fila[RecurringRules.category],
                amount = fila[RecurringRules.amount],
                dayOfMonth = fila[RecurringRules.dayOfMonth],
                type = TransactionType.valueOf(fila[RecurringRules.type]),
                activeFrom = fila[RecurringRules.activeFrom],
            )
            // `null` = este período no tiene ninguna ocurrencia que preguntar (un período acortado
            // a mano que no alcanza a contener el día de la regla). Ahí no hay sello que mirar.
            val sello = ocurrenciaPorPreguntar(hoy, regla, ajustes)?.let(::periodOf)
            RecurrenteParaContexto(
                nombre = regla.name,
                categoria = regla.category,
                monto = regla.amount,
                dia = regla.dayOfMonth,
                esIngreso = regla.type == TransactionType.INCOME,
                yaOcurrioEnElPeriodo = sello != null && sello in selladasPorRegla[regla.id].orEmpty(),
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

        // Solo AUTO y CONFIRMED, igual que `SubscriptionRoutes.resultFor`: son las que el dueño
        // ve como suyas en Recurrentes. Una CANDIDATE sigue siendo una sospecha del detector, y
        // una sospecha dicha como un hecho es justo lo que el asistente no puede hacer con su
        // plata.
        val suscripciones = Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .filter { it[Subscriptions.status] == "AUTO" || it[Subscriptions.status] == "CONFIRMED" }
            .map { fila ->
                // Un valor imposible en la columna cae en MENSUAL, como en `toSubscription()`: es
                // lo que era todo antes de que la columna existiera.
                val periodicidad = runCatching {
                    PeriodicidadDeCobro.valueOf(fila[Subscriptions.periodicidad])
                }.getOrDefault(PeriodicidadDeCobro.MENSUAL)
                val moneda = fila[Subscriptions.currency]
                val nativo = fila[Subscriptions.amount]
                // Prorratear primero, convertir después. Ver el KDoc de [SuscripcionParaContexto].
                val mensualNativo = montoMensualEquivalente(nativo, periodicidad)
                SuscripcionParaContexto(
                    nombre = fila[Subscriptions.displayName],
                    montoMensualCop = when (moneda) {
                        "COP" -> mensualNativo
                        "USD" -> (mensualNativo * tasa).roundToLong()
                        else -> 0L
                    },
                    dia = fila[Subscriptions.dayOfMonth],
                    moneda = moneda,
                    montoNativo = nativo,
                    esAnual = periodicidad == PeriodicidadDeCobro.ANUAL,
                )
            }

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
            // Del más nuevo al más viejo: si algo se cae por el tope, que sea lo más viejo — y
            // cuántos se cayeron se dice abajo, en el texto.
            movimientos = delPeriodo
                .sortedByDescending { it[Events.timestamp] }
                .take(CUANTOS_MOVIMIENTOS_CABEN)
                .map { fila ->
                    MovimientoParaContexto(
                        fecha = fechaLegible(fila[Events.timestamp]),
                        nombre = fila[Events.description],
                        categoria = fila[Events.category],
                        monto = fila[Events.amount],
                        esIngreso = fila[Events.type] == TransactionType.INCOME.name,
                        cuenta = nombreDeCuenta[fila[Events.accountId]] ?: "otra cuenta",
                    )
                },
            movimientosQueNoCaben = (delPeriodo.size - CUANTOS_MOVIMIENTOS_CABEN).coerceAtLeast(0),
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

    if (movimientos.isNotEmpty()) {
        appendLine("== Los movimientos de este período, uno por uno ==")
        movimientos.forEach { m ->
            val signo = if (m.esIngreso) "+" else "-"
            appendLine("- ${m.fecha} · ${m.nombre} (${m.categoria}, ${m.cuenta}): $signo\$${m.monto}")
        }
        if (movimientosQueNoCaben > 0) {
            // Se dice, siempre. Un corte callado haría que el asistente sumara lo que ve y
            // contestara una cifra que no coincide con la pantalla, sin nada que lo delate.
            appendLine("- (y $movimientosQueNoCaben movimientos más de este período que no caben aquí: para esos, usa los totales por categoría de arriba)")
        }
        appendLine()
    }

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
        // «activas» en el título, y no «Suscripciones» a secas: acá van las que el dueño tiene por
        // suyas, no las que el detector todavía propone.
        appendLine("== Suscripciones activas ==")
        suscripciones.sortedByDescending { it.montoMensualCop }.forEach { s ->
            // El cobro real se dice aparte cuando no coincide con el equivalente mensual en pesos.
            // Si no, un Spotify en dólares y un cobro anual quedarían indistinguibles de un cargo
            // mensual en pesos, y el asistente contestaría sobre un número que el dueño no
            // reconoce en su extracto.
            val cobroReal = when {
                s.esAnual && s.moneda != "COP" ->
                    " (el cobro real es ${s.moneda} \$${s.montoNativo} una vez al año)"
                s.esAnual -> " (el cobro real es \$${s.montoNativo} una vez al año)"
                s.moneda != "COP" ->
                    " (el cobro real es ${s.moneda} \$${s.montoNativo} al mes, convertido a la TRM de hoy)"
                else -> ""
            }
            appendLine("- ${s.nombre}: \$${s.montoMensualCop} al mes, el día ${s.dia}" + cobroReal)
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

/** «14 de septiembre», que es como el dueño la lee — y en su zona horaria, no en UTC. */
private fun fechaLegible(momento: Long): String {
    val fecha = Instant.ofEpochMilli(momento).atZone(AppClock.zone).toLocalDate()
    return "${fecha.dayOfMonth} de ${MESES[fecha.monthValue - 1]}"
}

private fun diasHasta(finExclusivo: Long): Int {
    val zona: ZoneId = AppClock.zone
    val hoy = AppClock.now(zona).toLocalDate()
    val ultimo = Instant.ofEpochMilli(finExclusivo - 1).atZone(zona).toLocalDate()
    return ChronoUnit.DAYS.between(hoy, ultimo).toInt().coerceAtLeast(0)
}
