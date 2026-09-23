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
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.server.routes.estadosDeLasOcurrenciasReales
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.planDeUnaDeuda
import com.jvillada.movi.shared.model.resumirDeudas
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
 * 3. **Lo que se repite en cada mensaje va acá; lo que se pregunta a veces, no.** Van los totales
 *    del período, los recurrentes, los créditos, las suscripciones y las metas: cabe en unas
 *    líneas y sirve para casi cualquier pregunta.
 *
 *    Los movimientos **uno por uno** estuvieron acá unas horas y se fueron: eran 126 renglones
 *    —ocho mil caracteres— viajando en CADA mensaje para responder una pregunta de cada cinco, y
 *    desde que el asistente puede consultarlos (`buscar_movimientos`) mandarlos siempre es pagar
 *    por adelantado algo que casi nunca se usa. Misma historia con los documentos. Una consulta
 *    de más cuesta una vuelta; el bloque de más costaba en cada mensaje de cada conversación.
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

/**
 * Un crédito con sus condiciones: sin la tasa y la cuota no se puede opinar de una deuda.
 *
 * **Y sin quién paga la cuota, se opina mal.** Cuatro de los doce créditos del dueño no salen de
 * su cuenta: dos libranzas las descuenta la nómina y las dos hipotecas las gira Skandia desde la
 * AFC. Un asesor que no lo sabe le recomienda «recortar gastos para cubrir la cuota de la
 * hipoteca» — plata que nunca pasa por su bolsillo. Por eso [render] dice para CADA crédito de
 * dónde sale la cuota, en palabras, y no solo cuando es un tercero.
 *
 * [saldo] es la deuda de hoy, derivada de los movimientos igual que la lista de Cuentas. No la lee
 * [contextoDelPeriodoDe] —la calcula `buildUserContext`, que ya tiene las cuentas con saldo— y
 * llega con [conSaldos]. `null` = no se sabe (o la deuda está en otra moneda) y no se estima nada
 * sobre ella: ni interés del mes ni cuánto baja.
 */
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
    val accountId: String = "",
    val otrosCargosMensuales: Long? = null,
    val sinIntereses: Boolean = false,
    val saldo: Long? = null,
    /**
     * **Quién paga, cuando es una cuenta del propio dueño.** `paid_by` es texto libre («Skandia»),
     * y dicho a secas el modelo lo lee como un tercero: el 23-sep llamó a Skandia «tu seguro» y le
     * dijo al dueño que no estaba poniendo plata suya, cuando Skandia es SU fondo de pensión
     * voluntaria —de ahí sale la plata que llega a la AFC y paga las dos hipotecas de Davibank—.
     * Si el texto coincide con el comienzo del nombre de una cuenta suya, acá va ese nombre.
     */
    val loPagaCuentaPropia: String? = null,
) {
    /** ¿La cuota sale de su cuenta? La misma regla que la pantalla de Créditos ([saleDeTuBolsillo]). */
    val saleDeSuBolsillo: Boolean get() = !porNomina && loPaga.isNullOrBlank()

    /**
     * El plan del crédito con el saldo de hoy —interés del mes, cuánto baja, si se termina—, o
     * `null` sin saldo. Es [planDeUnaDeuda], la MISMA cuenta que hace la pantalla de Créditos: si el
     * asistente dijera otro interés que la pantalla, el dueño no sabría a cuál creerle.
     */
    val plan: PlanDelCredito?
        get() = saldo?.let {
            planDeUnaDeuda(
                saldoDeLaDeuda = it,
                rateEa = tasaEa,
                cuota = cuota,
                seguroMensual = seguroMensual,
                otrosCargosMensuales = otrosCargosMensuales,
                saleDeTuBolsillo = saleDeSuBolsillo,
                sinIntereses = sinIntereses,
            )
        }
}

/**
 * Le pone a cada crédito el saldo de su cuenta. Solo el componente en pesos y solo si la deuda no
 * tiene saldo en otra moneda: con un préstamo en dólares, `balance` es la parte COP (casi siempre
 * $0) y el interés estimado sobre eso sería un cero con cara de dato — la misma guarda que
 * `planDelCredito` en `:core`.
 */
internal fun ContextoDelPeriodo.conSaldos(cuentas: List<Account>): ContextoDelPeriodo {
    val porId = cuentas.associateBy { it.id }
    return copy(
        creditos = creditos.map { c ->
            val cuenta = porId[c.accountId]
            val enOtraMoneda = cuenta?.balancesByCurrency.orEmpty().any { (moneda, saldo) -> moneda != "COP" && saldo != 0L }
            // Un saldo en cero se calla en vez de decirse: casi siempre es un crédito al que le
            // falta registrar el desembolso (la pantalla dice «Falta registrar el desembolso»), y
            // «debe $0» en boca del asistente sería afirmar que ya lo pagó.
            c.copy(saldo = cuenta?.takeUnless { enOtraMoneda }?.balance?.takeIf { it > 0L })
        },
    )
}

/**
 * Un gasto del período, tal como lo cuenta el Inicio (anulados, «Por confirmar» y lo que no es
 * flujo de caja ya afuera). **No viaja en el contexto** —esa decisión está arriba, en el KDoc del
 * archivo—: lo usan los hechos de una pregunta ([hechosParaLaPregunta]) para decir los tres gastos
 * más grandes de una categoría o de una cuenta SOLO cuando la pregunta la nombra.
 */
internal data class GastoDelPeriodo(
    val fecha: String,
    val nombre: String,
    val categoria: String,
    val cuenta: String,
    val monto: Long,
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
    /** Los gastos del período uno por uno. No se renderizan: ver [GastoDelPeriodo]. */
    val gastos: List<GastoDelPeriodo> = emptyList(),
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
        val nombreDeCuenta = Accounts.selectAll().where { Accounts.userId eq uid }
            .associate { it[Accounts.id] to it[Accounts.name] }
        val gastos = delPeriodo
            .filter { it[Events.type] == TransactionType.EXPENSE.name }
            .map { fila ->
                GastoDelPeriodo(
                    fecha = epochMillisToAppDateString(fila[Events.timestamp]),
                    nombre = fila[Events.description],
                    categoria = fila[Events.category],
                    cuenta = nombreDeCuenta[fila[Events.accountId]] ?: "otra cuenta",
                    monto = fila[Events.amount],
                )
            }

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
        //
        // **Y lo que Movi emparejó SOLO también cuenta como ocurrido.** Esto miraba solo los sellos
        // guardados en `recurring_occurrences`, y lo emparejado automáticamente no se escribe ahí
        // (se deriva en cada lectura). El 23-sep el asistente le dijo al dueño «todavía te faltan
        // $291.677 de recurrentes (Tía Caro y Coomeva Familiar)» con su Inicio diciendo «Pagaste los
        // 12 del período». Ahora pregunta lo mismo que la pantalla, con la misma función.
        val ocurridasEnElPeriodo: Set<String> = estadosDeLasOcurrenciasReales(uid, hoy, ajustes)
            .filter { it.occurred }
            .map { it.ruleId }
            .toSet()
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
                yaOcurrioEnElPeriodo = regla.id in ocurridasEnElPeriodo ||
                    (sello != null && sello in selladasPorRegla[regla.id].orEmpty()),
            )
        }

        val creditos = Credits.selectAll().where { Credits.userId eq uid }.map { fila ->
            CreditoParaContexto(
                accountId = fila[Credits.accountId],
                cuenta = nombreDeCuenta[fila[Credits.accountId]] ?: fila[Credits.accountId],
                banco = fila[Credits.bank],
                tasaEa = fila[Credits.rateEa],
                cuota = fila[Credits.installment],
                plazoMeses = fila[Credits.termMonths],
                dia = fila[Credits.dayOfMonth],
                seguroMensual = fila[Credits.insuranceMonthly],
                porNomina = fila[Credits.payrollDeduction] == true,
                loPaga = fila[Credits.paidBy],
                otrosCargosMensuales = fila[Credits.otrosCargosMensuales],
                sinIntereses = fila[Credits.sinIntereses] == true,
                loPagaCuentaPropia = cuentaPropiaQuePaga(fila[Credits.paidBy], nombreDeCuenta.values),
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
            gastos = gastos,
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
        // **Con su proporción ya hecha.** Sin esto el modelo estimaba a ojo y exageraba con
        // palabras: el 23-sep escribió que la cuota de crédito «ya casi iguala todo lo que entró»
        // cuando era el 58 % de los ingresos. La cifra estaba bien y el verificador no tenía qué
        // marcar; lo que mentía era el «casi». Con el porcentaje en el renglón lo dice tal cual, y
        // el verificador puede comprobarlo.
        val totalDeGastos = gastoPorCategoria.values.sum()
        gastoPorCategoria.entries.sortedByDescending { it.value }
            .forEach { (categoria, monto) ->
                val proporciones = listOfNotNull(
                    porcentajeEntero(monto, totalDeGastos)?.let { "$it % de los gastos" },
                    porcentajeEntero(monto, ingresos)?.let { "$it % de los ingresos" },
                )
                val cola = if (proporciones.isEmpty()) "" else " (${proporciones.joinToString("; ")})"
                appendLine("- $categoria: \$$monto$cola")
            }
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
        // Sumado acá por lo mismo que el total de suscripciones: «¿me alcanza?» se contesta con este
        // número, y sumar renglones es justo lo que el modelo hace mal.
        val pendientes = recurrentes.filter { !it.esIngreso && !it.yaOcurrioEnElPeriodo }
        if (pendientes.isNotEmpty()) {
            appendLine("Total de gastos recurrentes que TODAVÍA no ocurrieron en este período: \$${pendientes.sumOf { it.monto }}")
        }
    }
    appendLine()

    if (creditos.isNotEmpty()) {
        appendLine("== Créditos, con sus condiciones (de la tasa más alta a la más baja) ==")
        // **De la tasa más alta a la más baja**, y no en el orden de la base: «¿qué deuda abono
        // primero?» es la pregunta de criterio más común, y así el orden ya es la mitad de la
        // respuesta sin que el modelo tenga que comparar doce números.
        creditos.sortedByDescending { if (it.sinIntereses) 0.0 else it.tasaEa }.forEach { c ->
            appendLine(renglonDelCredito(c))
        }
        val planes = creditos.mapNotNull { it.plan }
        if (planes.isNotEmpty()) {
            // Los totales van partidos por quién paga, igual que en la pantalla de Créditos: un solo
            // total le cobraría a su bolsillo millones al mes que no salen de ahí. Ver
            // `ResumenDeDeudas`.
            val resumen = resumirDeudas(planes)
            appendLine(
                "Intereses estimados de un mes: \$${resumen.interesMensualPropio} en los créditos que " +
                    "salen de su bolsillo, y \$${resumen.interesMensualAjeno} en los que paga la nómina o un tercero.",
            )
        }
        appendLine(
            "Cuotas al mes: \$${creditos.filter { it.saleDeSuBolsillo }.sumOf { it.cuota }} salen de su bolsillo; " +
                "\$${creditos.filterNot { it.saleDeSuBolsillo }.sumOf { it.cuota }} las paga la nómina o un tercero (no salen de su cuenta).",
        )
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
        // **El total va sumado desde acá, y no lo suma el modelo.** Medido el 17-sep en el teléfono
        // del dueño: con los diez renglones correctos delante, contestó «$880.361» donde la suma es
        // $900.295 — casi $20.000 de diferencia en una cifra sobre la que él decide algo. Sumar diez
        // números es justo lo que un modelo hace mal y una función hace bien.
        appendLine("Total de suscripciones activas al mes: \$${suscripciones.sumOf { it.montoMensualCop }}")
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

/**
 * El renglón de un crédito: condiciones, **quién paga la cuota** y, si se sabe el saldo, qué hace
 * la cuota con la deuda este mes.
 *
 * Quién paga va SIEMPRE en palabras —también cuando es el dueño—, porque la ausencia de un dato no
 * es un dato para un modelo: sin «sale de su bolsillo» escrito, «no dice nada» y «lo paga él» se
 * leen igual. El desglose (interés, seguro, otros, capital) sale de [planDeUnaDeuda], la misma
 * cuenta que la pantalla de Créditos.
 */
internal fun renglonDelCredito(c: CreditoParaContexto): String = buildString {
    append("- ${c.cuenta} (${c.banco}): ")
    c.saldo?.let { append("debe \$$it; ") }
    append("cuota \$${c.cuota} el día ${c.dia}, ")
    if (c.sinIntereses) append("NO cobra intereses") else append("tasa ${c.tasaEa} % EA")
    append(", plazo ${c.plazoMeses} meses")
    // **«Seguros», no «seguro de vida».** `insurance_monthly` guarda TODOS los seguros de la cuota:
    // en el Hipotecario 2334, los $209.219 son vida ($69.600) más incendio y terremoto ($139.619).
    // Rotulado «seguro de vida», el modelo contestó «el seguro de vida de $209.219» — el único error
    // que quedó en una respuesta por lo demás correcta, y venía de este rótulo.
    c.seguroMensual?.takeIf { it > 0L }?.let { append(", incluye seguros por \$$it al mes") }
    c.otrosCargosMensuales?.takeIf { it > 0L }?.let { append(", incluye otros cargos \$$it al mes") }
    append(". ")
    append(
        when {
            c.porNomina -> "La cuota la descuenta la nómina antes de que llegue el sueldo: NO sale de su cuenta."
            c.loPagaCuentaPropia != null ->
                "La cuota se paga con plata de su propia cuenta «${c.loPagaCuentaPropia}» (no es un seguro ni un tercero): " +
                    "NO sale de su plata del día a día, pero sí es plata suya."
            !c.loPaga.isNullOrBlank() -> "La cuota la paga ${c.loPaga}: NO sale de su cuenta."
            else -> "La cuota sale de su bolsillo."
        },
    )
    val plan = c.plan ?: return@buildString
    when (plan.comoVa) {
        ComoVaLaDeuda.AMORTIZA -> {
            append(" De la cuota, unos \$${plan.interes} son interés este mes y \$${plan.capital} bajan la deuda")
            plan.mesesHastaLaUltimaCuota?.let { append("; a este ritmo le quedan $it cuotas") }
            append(".")
        }
        // **Con los cargos adentro de la cuenta.** Decía «la cuota no alcanza a cubrir los
        // intereses», y en el Hipotecario 2334 era falso: la cuota ($2.613.714) SÍ es mayor que los
        // intereses ($2.427.883); lo que no los alcanza es lo que queda después de $209.219 de
        // seguros. El modelo repitió la frase y en el renglón siguiente mostró lo contrario. Ahora
        // el renglón trae la resta hecha, que es lo único que no se puede leer al revés.
        ComoVaLaDeuda.SOLO_INTERESES -> {
            val cargos = plan.seguro + plan.otrosCargos
            if (cargos > 0L) {
                append(" Después de \$$cargos de ${nombreDeLosCargos(plan)} le quedan \$${c.cuota - cargos} de la cuota,")
                append(" y eso apenas cubre los intereses del mes (unos \$${plan.interes}): la deuda casi no baja.")
            } else {
                append(" La cuota apenas cubre los intereses del mes (unos \$${plan.interes}): la deuda casi no baja.")
            }
        }
        ComoVaLaDeuda.LA_DEUDA_CRECE -> {
            val cargos = plan.seguro + plan.otrosCargos
            val queda = c.cuota - cargos
            val crece = (plan.interes - queda).coerceAtLeast(0L)
            if (cargos > 0L) {
                append(" OJO: después de \$$cargos de ${nombreDeLosCargos(plan)} le quedan \$$queda de la cuota,")
                append(" y los intereses del mes son unos \$${plan.interes}: la deuda CRECE aunque pague (unos \$$crece al mes).")
            } else {
                append(" OJO: la cuota (\$${c.cuota}) no alcanza a cubrir los intereses del mes (unos \$${plan.interes}):")
                append(" la deuda CRECE aunque pague (unos \$$crece al mes).")
            }
        }
        else -> Unit
    }
}

/** [parte] sobre [total] en porcentaje entero (redondeo común), o `null` si no hay total. */
internal fun porcentajeEntero(parte: Long, total: Long): Long? =
    if (total <= 0L) null else Math.round(parte * 100.0 / total)

/** «seguros», «otros cargos» o «seguros y otros cargos», según qué haya en la cuota. */
private fun nombreDeLosCargos(plan: PlanDelCredito): String = when {
    plan.seguro > 0L && plan.otrosCargos > 0L -> "seguros y otros cargos"
    plan.otrosCargos > 0L -> "otros cargos"
    else -> "seguros"
}

/**
 * La cuenta del dueño que se llama como [quienPaga] («Skandia» → «Skandia pensión voluntaria»), o
 * `null` si quien paga no es una cuenta suya (la nómina, el papá, un tercero de verdad). Compara
 * el comienzo del nombre sin mayúsculas: `paid_by` lo escribe el dueño corto y la cuenta lleva el
 * nombre largo.
 */
internal fun cuentaPropiaQuePaga(quienPaga: String?, nombresDeSusCuentas: Collection<String>): String? {
    val quien = quienPaga?.trim()?.takeIf { it.length >= 3 } ?: return null
    return nombresDeSusCuentas.firstOrNull { it.trim().startsWith(quien, ignoreCase = true) }
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
