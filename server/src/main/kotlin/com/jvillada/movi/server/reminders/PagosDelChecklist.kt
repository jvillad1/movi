package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaComoGastoVariable
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import java.time.LocalDate
import java.time.ZoneId

/**
 * # Qué parte de cada movimiento paga un fijo del checklist del período
 *
 * La tarjeta «Disponible» del Inicio resta como **fijos** todo el checklist del período, pagado o
 * pendiente, por el monto de cada regla (`fijosDelPeriodo` en la UI). Contra eso mide el **gasto
 * variable** (`gastoVariablePorDia` en `:core`). Si el pago de un fijo cuenta además como variable,
 * se descuenta dos veces.
 *
 * Hasta acá solo se sacaban del variable los movimientos atados a un sello (`event_id`). En el uso
 * real eso se quedaba corto: el dueño marca «Ya lo pagué» sin elegir el movimiento, no marca nada
 * (el pago está anotado pero el ítem sigue pendiente), o paga en dos partes. Con su período de
 * septiembre la tarjeta decía «$21,9M de $8,5M» y casi $7,8M de eso eran pagos de fijos.
 *
 * ## La regla: lo que está en los fijos no está en el variable, y viceversa
 *
 * Para cada ítem de gasto del checklist (una regla real, con su vencimiento dentro del período)
 * se busca **quién lo pagó**, hasta completar el monto de la regla — que es lo que los fijos ya
 * restaron:
 *
 * 1. **el movimiento de su sello**, si lo tiene — y lo que Movi emparejó solo cuenta como un sello
 *    (`emparejadasComoSellos`): el checklist ya lo da por pagado;
 * 2. si falta monto (sellado sin movimiento, sin sellar, o pagado en partes), **los candidatos del
 *    «¿Es este?»** — el mismo [candidatosPuntuados] que la pantalla de Recurrentes usa para
 *    proponer, con sus mismas cuatro puertas y la misma seña mínima (nombre o categoría). No hay
 *    un emparejador nuevo.
 *
 * Cada movimiento paga **como mucho lo que le falta a la regla**: el colegio de $4.000.000 pagado
 * con $3.000.000 (sellado) + $1.000.000 saca los dos; el segundo gimnasio de $180.000, con la regla
 * ya completa, sigue siendo variable; y un pago de $200.000 para un fijo de $180.000 saca $180.000
 * y deja $20.000 como variable. Así fijos + variable suman lo que de verdad salió.
 *
 * Un ítem **pendiente** también reclama su pago: los fijos ya lo cuentan por su monto entero, así
 * que un pago anotado sin marcar es exactamente el caso del doble descuento.
 *
 * Entre reglas, los candidatos se reparten de una vez y por fuerza de la seña, no regla por regla:
 * el «Crédito Papá» dicho por su nombre gana su movimiento antes de que el «Crédito Mamá», que solo
 * comparte la categoría «Crédito», lo pueda tomar. Un movimiento paga a una sola regla.
 *
 * ## El error contrario, cerrado también
 *
 * Sacar del variable algo que **no** está en los fijos haría ver el disponible mejor de lo que es.
 * Por eso:
 *
 * - solo reclaman las reglas cuyo vencimiento cae en el período (las mismas filas del checklist,
 *   ver [vencimientoEnElChecklist]), y nunca más que su monto;
 * - un sello de una regla que **ya no existe** (quedó huérfano) no saca nada por sí solo: esa regla
 *   no está en los fijos. Su movimiento queda libre para que lo reclame una regla viva;
 * - un sello de una regla viva pero de OTRO período (el pago tardío del período anterior, anotado en
 *   este) sigue fuera del variable, como siempre: ya se contó en los fijos de su período.
 *
 * Todo en memoria sobre lo que la ruta ya leyó: ninguna consulta por regla.
 */

/**
 * El vencimiento con el que [rule] aparece en el checklist del período que contiene [hoy], o
 * `null` si no aparece.
 *
 * Replica `checklistDelPeriodo` del cliente sobre las dos respuestas de las que sale: primero la
 * ocurrencia que emite `/api/payments/occurrences` (sellada, o abierta si su día ya llegó) si cae
 * en el período; si no, el vencimiento vigente de `/api/payments/upcoming` si cae en el período.
 */
fun vencimientoEnElChecklist(
    rule: RecurringRule,
    hoy: LocalDate,
    settings: PeriodSettings,
    periodosSellados: Set<String>,
    zone: ZoneId = AppClock.zone,
): LocalDate? {
    val dias = diasDelPeriodo(hoy, settings, zone)
    val emitida = ocurrenciaPorPreguntar(hoy, rule, settings, zone = zone)
        ?.takeIf { ruleIsActiveOn(rule, it, settings, zone) }
        ?.takeIf { periodOf(it) in periodosSellados || !it.isAfter(hoy) }
    if (emitida != null && emitida in dias) return emitida
    return dueDateFor(rule, hoy, DEFAULT_GRACE_DAYS, periodosSellados, settings).takeIf { it in dias }
}

/**
 * **id de movimiento → la parte de su monto que paga un fijo del checklist.** Ver el KDoc del
 * archivo.
 *
 * @param reglas las reglas recurrentes REALES del usuario (las cuotas de crédito ya salen del
 *   variable enteras por su categoría).
 * @param sellos las filas crudas de `recurring_occurrences`.
 * @param ocurridos regla → períodos que de verdad valen (`loadOccurredBy`: un sello cuyo
 *   movimiento murió no cuenta).
 * @param eventos los movimientos vivos del período.
 */
fun parteFijaDelChecklist(
    reglas: List<RecurringRule>,
    sellos: List<RecurringOccurrence>,
    ocurridos: Map<String, Set<String>>,
    eventos: List<FinancialEvent>,
    hoy: LocalDate,
    settings: PeriodSettings,
    zone: ZoneId = AppClock.zone,
): Map<String, Long> {
    val porId = eventos.associateBy { it.id }
    val reglaPorId = reglas.associateBy { it.id }
    val parte = mutableMapOf<String, Long>()

    // Los sellos con movimiento de reglas VIVAS salen del variable hasta el monto de su regla. Los
    // de una regla borrada no: esa regla no está en los fijos de nadie.
    val sellosVivos = sellos.filter { it.eventId != null && it.ruleId in reglaPorId }
    sellosVivos.forEach { sello ->
        val evento = porId[sello.eventId] ?: return@forEach
        val regla = reglaPorId.getValue(sello.ruleId)
        parte[evento.id] = minOf(evento.amount, regla.amount)
    }
    // La cuarta puerta del emparejador: lo sellado a una regla viva no se vuelve a proponer.
    val usados = sellosVivos.mapNotNull { it.eventId }.toSet()

    // Lo que le falta a cada ítem de gasto del checklist para completar su monto.
    val falta = mutableMapOf<String, Long>()
    val vencimientos = mutableMapOf<String, LocalDate>()
    reglas.filter { it.type == TransactionType.EXPENSE }.forEach { regla ->
        val sellados = ocurridos[regla.id].orEmpty()
        val vence = vencimientoEnElChecklist(regla, hoy, settings, sellados, zone) ?: return@forEach
        val sello = sellos.firstOrNull { it.ruleId == regla.id && it.period == periodOf(vence) }
            ?.takeIf { periodOf(vence) in sellados }
        val yaPagado = when {
            sello?.eventId == null -> 0L
            // Sellado con un movimiento vivo que cae fuera del período: el pago no está acá, y no
            // hay nada más que buscarle en este período.
            sello.eventId !in porId -> regla.amount
            else -> parte[sello.eventId] ?: 0L
        }
        val resto = regla.amount - yaPagado
        if (resto > 0L) {
            falta[regla.id] = resto
            vencimientos[regla.id] = vence
        }
    }
    if (falta.isEmpty()) return parte

    // Solo lo que de verdad cuenta como gasto variable puede pagar un fijo acá: un movimiento en
    // «Por confirmar» no suma en el variable, y si reclamara la regla dejaría afuera al real.
    //
    // Y además un gasto con la categoría de cuota: no suma en el variable (sale entero por su
    // categoría), pero si un recurrente real —«Crédito Papá»— lo paga, esa plata ya está en los
    // fijos y no puede volver a restarse como «otro pago de deuda» (ver
    // `pagosDeDeudaFueraDelChecklist` en :core). Su parte fija no cambia el variable: ya era cero.
    val elegibles = eventos.filter { (cuentaComoGastoVariable(it) || esCuotaQueSaleDelBolsillo(it)) && it.id !in parte }
    val pares = falta.keys.flatMap { ruleId ->
        val regla = reglaPorId.getValue(ruleId)
        candidatosPuntuados(regla, vencimientos.getValue(ruleId), elegibles, usados, zone, settings = settings)
            .map { ruleId to it }
    }
    val orden = compareBy(ORDEN_DE_CANDIDATOS) { par: Pair<String, CandidatoPuntuado> -> par.second }
        .thenBy { it.first }
    for ((ruleId, candidato) in pares.sortedWith(orden)) {
        val resto = falta.getValue(ruleId)
        val id = candidato.event.id
        if (resto <= 0L || id in parte) continue
        val pagado = minOf(candidato.event.amount, resto)
        parte[id] = pagado
        falta[ruleId] = resto - pagado
    }
    return parte
}

/** Un gasto en pesos, que cuenta en «Gastos», con la categoría de la cuota de un crédito. */
private fun esCuotaQueSaleDelBolsillo(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        evento.category == CUOTA_CATEGORY &&
        cuentaEnGastosEIngresos(evento)

/**
 * **La plata que pagó la cuota de un crédito del checklist del período**: id del movimiento que
 * salió de la cuenta → su monto.
 *
 * Es la misma fila que `GET /api/payments/occurrences` deriva para la cuota de un crédito (ver
 * `ReminderRoutes.kt` y [pagosDeDeudaPorPeriodo]): el vencimiento por preguntar, el pago que salda
 * su período y la plata que de verdad salió ([plataQueSalio]), que es el `montoPagado` con el que
 * el cliente suma esa cuota en los fijos. Y solo si el vencimiento cae en el período, igual que el
 * checklist del cliente.
 *
 * Lo usa `pagosDeDeudaFueraDelChecklist` (en :core) para no restar dos veces una cuota que ya está
 * en los fijos. Todo en memoria sobre los movimientos del período que la ruta ya leyó.
 */
fun cuotasDelChecklistPagadas(
    reglasDeCredito: List<RecurringRule>,
    eventos: List<FinancialEvent>,
    hoy: LocalDate,
    settings: PeriodSettings,
    zone: ZoneId = AppClock.zone,
): Map<String, Long> {
    if (reglasDeCredito.isEmpty()) return emptyMap()
    val pagos = eventos.filter { it.category in CATEGORIAS_QUE_SALDAN }
    val dias = diasDelPeriodo(hoy, settings, zone)
    return pagosDeDeudaPorPeriodo(reglasDeCredito, pagos, zone = zone, settings = settings)
        .mapNotNull { (ruleId, porPeriodo) ->
            val regla = reglasDeCredito.first { it.id == ruleId }
            val vence = ocurrenciaPorPreguntar(hoy, regla, settings, zone = zone) ?: return@mapNotNull null
            if (vence !in dias) return@mapNotNull null
            val pago = porPeriodo[periodOf(vence)] ?: return@mapNotNull null
            val salida = plataQueSalio(pago, pagos)
            salida.id to salida.amount
        }
        .toMap()
}
