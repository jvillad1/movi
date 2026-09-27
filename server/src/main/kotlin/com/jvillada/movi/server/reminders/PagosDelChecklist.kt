package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.palabrasClave
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
 *    «¿Es este?» cuyo NOMBRE pega con la regla** — el mismo [candidatosPuntuados] que la pantalla
 *    de Recurrentes usa para proponer, con sus mismas cuatro puertas, pero sin bajar a la seña
 *    mínima de esa pantalla (nombre o categoría): compartir solo la categoría (y la cuenta) sirve
 *    para proponerle el movimiento al dueño, no para darlo por pagado acá. La regla «Mercado» de
 *    $2.000.000 en «Comida» absorbía así todo el gasto de Comida del período y el variable
 *    —«Período/Semana/Hoy»— se quedaba en $0. Un movimiento que solo *empieza* con el nombre
 *    («Mercado Éxito») completa el ítem únicamente si el pago ya se ve por su nombre (un sello con
 *    movimiento, o un candidato que lo dice entero): «Colegio Hija» $3.000.000 + «Colegio Hija ·
 *    parte…» $1.000.000 sí; «Mercado Éxito» contra un «Mercado» sin pagos, no. No hay un
 *    emparejador nuevo.
 *
 *    Costo aceptado: el faltante de un ítem ya sellado que se pagó con un movimiento que NO dice el
 *    nombre («Transferencia colegio») no se le propone al dueño —un sello guarda un solo
 *    movimiento— y cuenta también como variable. Va en la dirección prudente (el Disponible sale
 *    más bajo, nunca más alto).
 *
 * Cada movimiento paga **como mucho lo que le falta a la regla**: el colegio de $4.000.000 pagado
 * con $3.000.000 (sellado) + $1.000.000 saca los dos; el segundo gimnasio de $180.000, con la regla
 * ya completa, sigue siendo variable; y un pago de $200.000 para un fijo de $180.000 saca $180.000
 * y deja $20.000 como variable. Así fijos + variable suman lo que de verdad salió.
 *
 * **Salvo lo que Movi emparejó solo, que sale entero y cierra el ítem.** En esa fila el cliente
 * resta como fijo lo que de verdad se pagó (`montoPagado`, el monto del movimiento), no el de la
 * regla — el Celular del dueño no cuesta lo mismo cada mes. Así que el server saca del variable
 * exactamente ese monto: con $60.000 contra una regla de $53.077, los $6.923 de más no pueden
 * quedar además como variable (contarían dos veces); con $50.000, los $3.077 que faltan no se
 * buscan en otro candidato (no están en los fijos de nadie, y ese candidato quedaría contado en
 * ningún lado). Un sello a mano sí sigue con el monto de la regla: para esa fila el server no manda
 * `montoPagado` y el cliente resta el de la regla, que es lo que se completa acá.
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
 * - **un sello de OTRA ocurrencia** —el arriendo del 23-sep pagado tarde el 26, ya en el período
 *   que arrancó el 25— **no saca su movimiento del variable**, esté sellado a mano o emparejado
 *   solo, en la gracia o después. El Disponible arranca de lo que había en Tu plata al empezar el
 *   período, y ese saldo todavía tenía la plata del arriendo de septiembre; el checklist del cliente
 *   solo resta como fijo lo que vence DENTRO del período, así que el 23-sep no está en los fijos de
 *   nadie. Si además saliera del variable, esa plata no se contaría en ningún lado y el Disponible
 *   se vería un arriendo entero mejor de lo que es. Cada peso cuenta exactamente una vez: un
 *   movimiento solo sale del variable si su monto se resta como fijo en esta misma cuenta. (Hasta
 *   el 22-sep el Disponible era «ingresos − fijos» y ese pago sí se había contado en los fijos de
 *   su período; desde que parte del saldo al inicio, ya no.) El sello sí lo sigue reservando: no
 *   puede pasar a ser el pago de otro ítem pendiente.
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
 * @param automaticas los `(regla, período)` de [sellos] que Movi emparejó solo
 *   (`emparejadasComoSellos`): su ítem sale por el monto entero del movimiento, ver el KDoc del
 *   archivo.
 * @param rechazados los «no fue este» del dueño, como pares `(ruleId, eventId)` — mismo formato
 *   que `loadRejectedPairs`. Un candidato rechazado explícitamente para una regla no puede
 *   absorber su ítem: el dueño ya dijo que ese movimiento no es el pago, y sin este filtro salía
 *   igual por nombre, dejando el gasto variable en $0 mientras el fijo seguía restando su monto
 *   entero. El par completo importa, no el movimiento solo: un rechazo para OTRA regla no excluye
 *   nada acá (mismo criterio que protege `loadRejectedPairs`).
 */
fun parteFijaDelChecklist(
    reglas: List<RecurringRule>,
    sellos: List<RecurringOccurrence>,
    ocurridos: Map<String, Set<String>>,
    eventos: List<FinancialEvent>,
    hoy: LocalDate,
    settings: PeriodSettings,
    zone: ZoneId = AppClock.zone,
    automaticas: Set<Pair<String, String>> = emptySet(),
    rechazados: Set<Pair<String, String>> = emptySet(),
): Map<String, Long> {
    val porId = eventos.associateBy { it.id }
    val reglaPorId = reglas.associateBy { it.id }
    val parte = mutableMapOf<String, Long>()

    // La cuarta puerta del emparejador: lo sellado a una regla viva —a cualquier ocurrencia suya,
    // de este período o de otro— no se vuelve a proponer como pago de otro ítem. Lo de una regla
    // borrada sí queda libre: esa regla no está en los fijos de nadie.
    val usados = sellos.filter { it.eventId != null && it.ruleId in reglaPorId }.mapNotNull { it.eventId }.toSet()

    // Lo que le falta a cada ítem de gasto del checklist para completar su monto.
    val falta = mutableMapOf<String, Long>()
    val vencimientos = mutableMapOf<String, LocalDate>()
    // Las reglas cuyo ítem ya tiene un pago con movimiento vivo en el período: evidencia por nombre
    // (o por emparejamiento) de que lo que sigue sin dicho nombre puede ser otra parte de ese pago.
    val conPagoYaVisto = mutableSetOf<String>()
    reglas.filter { it.type == TransactionType.EXPENSE }.forEach { regla ->
        val sellados = ocurridos[regla.id].orEmpty()
        val vence = vencimientoEnElChecklist(regla, hoy, settings, sellados, zone) ?: return@forEach
        // Un sello a mano cuyo movimiento se anuló sigue en la tabla, y Movi pudo emparejar solo el
        // pago que lo reemplazó para el mismo ítem: gana la fila cuyo movimiento está vivo en el
        // período. Si ganara el sello muerto, el ítem quedaría completo sin sacar el pago del
        // variable, y el cliente lo restaría además como fijo.
        val delItem = sellos.filter { it.ruleId == regla.id && it.period == periodOf(vence) }
        val sello = (delItem.firstOrNull { it.eventId != null && it.eventId in porId } ?: delItem.firstOrNull())
            ?.takeIf { periodOf(vence) in sellados }
        val eventoDelSello = sello?.eventId
        val yaPagado = when {
            eventoDelSello == null -> 0L
            // Sellado con un movimiento vivo que cae fuera del período: el pago no está acá, y no
            // hay nada más que buscarle en este período.
            eventoDelSello !in porId -> regla.amount
            // Emparejado solo: el cliente resta el monto del movimiento, así que sale entero y el
            // ítem queda completo — ni el excedente vuelve al variable ni el faltante se busca en
            // otro movimiento.
            (regla.id to periodOf(vence)) in automaticas -> {
                parte[eventoDelSello] = porId.getValue(eventoDelSello).amount
                regla.amount
            }
            // El sello de ESTE ítem: su movimiento sale del variable hasta el monto de la regla,
            // que es lo que los fijos ya restaron. Solo este: el sello de otra ocurrencia de la
            // misma regla no está en los fijos de este período (ver el KDoc del archivo).
            else -> minOf(porId.getValue(eventoDelSello).amount, regla.amount).also { parte[eventoDelSello] = it }
        }
        if (eventoDelSello != null && eventoDelSello in porId) conPagoYaVisto += regla.id
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
        val candidatos = candidatosPuntuados(regla, vencimientos.getValue(ruleId), elegibles, usados, zone, settings = settings)
            // El dueño ya dijo «no fue este» para este par exacto: no puede absorber el ítem, ni
            // siquiera por nombre. Antes del filtro de SENA_DEL_NOMBRE para que tampoco cuente como
            // evidencia de que el pago «ya se ve» (yaSeVeElPago, más abajo).
            .filterNot { (ruleId to it.event.id) in rechazados }
        // Solo el NOMBRE absorbe. Un movimiento que comparte apenas la categoría (y la cuenta) no es
        // evidencia de que pague este fijo: si lo absorbiera, «Mercado» ($2.000.000, sin pagar) se
        // comería los gastos de Comida y el variable quedaría en $0 mientras el fijo sigue restando
        // sus $2.000.000 enteros. Ese movimiento sigue siendo variable y el checklist se lo propone
        // al dueño; al confirmarlo queda sellado y ahí sí sale.
        //
        // Un movimiento que solo EMPIEZA con el nombre («Mercado Éxito» contra «Mercado») tampoco
        // basta por sí solo: es otra compra que nombra el mismo lugar. Completa el ítem únicamente
        // si el pago ya está a la vista por su nombre: un sello con movimiento, o un candidato que
        // dice el nombre entero («Colegio Hija» $3.000.000 + «Colegio Hija · parte desde
        // Bancolombia» $1.000.000).
        val yaSeVeElPago = ruleId in conPagoYaVisto || candidatos.any { it.senas >= SENA_DEL_NOMBRE }
        candidatos
            .filter { it.senas >= SENA_DEL_NOMBRE || (yaSeVeElPago && empiezaConElNombre(regla, it.event)) }
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

/**
 * El movimiento **empieza con el nombre de la regla**, palabra por palabra, con cualquier cola
 * («Colegio Hija · parte desde Bancolombia» contra «Colegio Hija»). Más suelto que
 * `nombreDeMovimientoPegaConRegla` —que solo perdona una cola de fecha—, por eso nunca absorbe solo:
 * ver el filtro en [parteFijaDelChecklist].
 */
private fun empiezaConElNombre(regla: RecurringRule, evento: FinancialEvent): Boolean {
    val delaRegla = palabrasClave(regla.name)
    if (delaRegla.isEmpty()) return false
    return listOf(evento.description, evento.merchant.orEmpty())
        .any { palabrasClave(it).take(delaRegla.size) == delaRegla }
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
