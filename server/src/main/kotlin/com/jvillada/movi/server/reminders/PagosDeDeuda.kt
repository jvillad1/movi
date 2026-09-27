package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * # «Esa cuota ya está pagada» — **derivado del movimiento, no sellado a mano**
 *
 * Palabras del dueño, mirando su propia plata: *«"Ya ocurrieron · 1" esto es falso, de hecho todos
 * los que registran movimientos ocurrieron»*. Tenía razón y el agujero era exactamente este: en su
 * base había cuatro pagos registrados —la cuota de Crediágil, la de Libre inversión, el pago de
 * AMEX y el de Nu— y la app seguía diciendo «Vencido hace 5 días».
 *
 * ## Por qué faltaba
 *
 * `GET /api/payments/occurrences` **excluye a propósito** las reglas sintéticas (la cuota de un
 * crédito y el pago de una tarjeta): ahí *el pago mueve la deuda, que es un hecho más fuerte que un
 * sello*, y meterlas en `recurring_occurrences` sería un segundo mecanismo compitiendo con ese.
 * El argumento sigue siendo bueno. Lo que faltaba es la otra mitad: **nadie leía ese hecho más
 * fuerte.** `dueDateFor` decidía el estado de una regla sintética solo por calendario, así que
 * gritaba «vencido» sobre una cuota que ya estaba registrada, con sus dos patas y todo.
 *
 * Esto lo lee. No escribe nada: no hay sello, no hay tabla nueva, no hay «Deshacer» (para deshacer
 * hay que borrar el movimiento, que es lo único que sostiene la afirmación).
 *
 * ## Qué cuenta como pago, y qué no
 *
 * La pata que **baja la deuda**: un movimiento sobre la cuenta LOAN / CREDIT_CARD de esa regla,
 * con la categoría que escribe `pagoDeCuotaLegs` ([CUOTA_CATEGORY] o [CARD_PAYMENT_CATEGORY]) y de
 * tipo INCOME (en una cuenta de deuda, lo que entra es lo que se abona).
 *
 * Las tres condiciones son necesarias y cada una cierra una puerta distinta:
 *
 *  - **La cuenta.** La otra pata del mismo traspaso —la plata saliendo de la cuenta de ahorros—
 *    lleva la MISMA categoría. Si la cuenta no filtrara, un pago contaría dos veces… y, peor, un
 *    gasto suelto anotado «Pago de tarjeta» en la cuenta de ahorros daría por saldada una tarjeta
 *    cuya deuda nadie tocó.
 *  - **La categoría.** Una compra con la tarjeta también vive en esa cuenta y no salda nada.
 *  - **INCOME.** Un cargo del banco sobre la cuenta de la deuda (un interés, una cuota de manejo)
 *    es EXPENSE: sube la deuda, no la baja. Categorizado a mano como «Pago de tarjeta» pasaría las
 *    otras dos puertas.
 *
 * ## El monto NO es una cuarta puerta — y por eso la fila lo dice
 *
 * Un abono de $50.000 sobre una AMEX cuyo extracto era $1.008.902 salda el periodo igual que un
 * pago completo, y apaga el recordatorio. Es una decisión, no un olvido: **movi no conoce el
 * extracto**, así que no tiene contra qué comparar. Los dos montos que sí tiene a mano no sirven:
 *
 *  - En una TARJETA el monto de la regla es el SALDO TOTAL de la cuenta (ver `virtualRuleForCard`
 *    y su `montoEsSaldo`), no lo que hay que pagar este mes: el mínimo de esa tarjeta ronda el
 *    5 %. Exigir que el pago cubra el monto de la regla dejaría TODAS las tarjetas eternamente
 *    «vencidas» — justo el reclamo que este archivo vino a arreglar, de vuelta.
 *  - En un CRÉDITO la pata que baja la deuda es el ABONO A CAPITAL, no la cuota: $12.157 de una
 *    cuota de $26.485, porque el resto se fue en intereses y seguro (ver `pagoDeCuotaLegs`).
 *    Exigirle que cubra la cuota no daría por pagada ninguna cuota nunca.
 *
 * Lo que sí se puede hacer —y se hace— es **decir cuánta plata fue**: la fila derivada viaja con
 * el monto del pago (`OccurrenceState.montoDelPago`, ver [plataQueSalio]) para que un abono de
 * $50.000 se lea como lo que es en vez de esconderse detrás de un «ya ocurrió» pelado. La app no
 * puede decidir si alcanzó; el dueño sí, mirando el número.
 *
 * ## A qué vencimiento se le atribuye un pago
 *
 * **Al que la app consideraba vigente el día en que se pagó, pero nunca a uno que todavía no
 * llegó.** Son dos mitades, y la segunda cuesta plata si falta.
 *
 * La primera es `dueDateFor(regla, fecha del pago)`, la misma función con la que se pinta
 * «Próximos»: así la atribución no puede divergir de lo que el dueño vio en la pantalla.
 *
 * La segunda es el tope. `dueDateFor` **rueda al mes siguiente** apenas pasan [DEFAULT_GRACE_DAYS]
 * días de atraso, así que sin tope un pago hecho con 6 días de retraso saldaba un vencimiento que
 * ni siquiera había llegado. El caso concreto es la Nu del dueño (día 1) pagada el 7: saldaba
 * **octubre**, y entonces septiembre seguía figurando «vencido» —el reclamo original, intacto— y
 * encima **el aviso del 1 de octubre no salía**. Los dos errores a la vez, y el segundo se paga con
 * mora e intereses. Peor: volviendo a pagar tarde, el corrimiento se acumulaba mes a mes.
 *
 * Es el mismo criterio que ya aplica el POST manual de ocurrencias («Ese vencimiento todavía no
 * llegó: no se puede dar por ocurrido»), acá del lado de la lectura.
 *
 * Con el tope, los tres pagos reales del dueño quedan así:
 *
 *  - Crediágil (día 15) pagada el 5 de septiembre → **septiembre**: el vencimiento vigente ese día
 *    era el 15 de septiembre, del mismo mes, así que el tope no lo mueve. ✔
 *  - Nu (día 1) pagada el 7 de septiembre, pasada la gracia → **septiembre**, el mes que de verdad
 *    venció. Y octubre vuelve a avisar. ✔
 *  - AMEX (día 16) pagada el 30 de agosto → **agosto** por mes de calendario; con corte, el
 *    período en que se pagó. Ver acá abajo.
 *
 * ### El 30 de agosto de la AMEX: con mes de calendario, agosto
 *
 * El mismo movimiento tiene dos lecturas —adelantó el extracto del 16 de septiembre, o pagó el del
 * 16 de agosto con dos semanas de atraso— y movi no tiene con qué distinguirlas: no conoce el
 * extracto, solo el día del mes. Con mes de calendario se elige agosto: el pago cae en agosto y
 * el vencimiento de agosto es el de su mes.
 *
 * **Con corte, decide el período del dueño, y es una decisión suya** (27-sep-2026, después de ver
 * Master Black en «Falta por pagar» con el pago hecho): *«si aparece un pago nuevo de una tarjeta va
 * a pasar lo mismo y voy a tener que esperar hasta el otro mes, eso no tiene sentido»*. Antes se
 * elegía el lado «barato» —el vencimiento viejo— salvo que hubiera un pago anterior como evidencia,
 * y una deuda sin historia en movi (Master Black: su primer pago registrado fue ese día) no tenía
 * cómo salir de «Falta por pagar» hasta el mes siguiente. Ver «Después del corte» en
 * [periodoQueSalda].
 *
 * ### Lo que este tope NO cierra
 *
 * ~~El pago que cruza el fin de mes~~ — **cerrado** desde que `dueDateFor` mira la ocurrencia del
 * período anterior que sigue en gracia ([ocurrenciaEnGracia]): una cuota del 30 de septiembre
 * pagada el 2 de octubre ya se atribuye a **septiembre**, y el 30 de octubre sigue avisando. Lo
 * mismo con corte 25 y un pago del 24 hecho el 25.
 *
 * Lo que queda: como acá `occurredPeriods` va vacío, un SEGUNDO pago dentro de esa gracia (el 30
 * de septiembre ya pagado el 29, y otro abono el 2 de octubre) también cae en septiembre y no
 * adelanta octubre. Es el lado barato: octubre avisa de más, no de menos.
 *
 * Dos pagos dentro del mismo ciclo caen en el mismo periodo y ahí el `Map` se queda con el último:
 * el periodo está saldado igual. Ver [pagosDeDeudaPorPeriodo], que ordena por fecha para que «el
 * último» sea el último de verdad y no el que la base devolvió primero.
 *
 * ### Con corte: el tope es el período del pago
 *
 * Con corte 25 el mes de calendario del pago y el período del dueño se separan del 25 a fin de mes,
 * y el tope por calendario le atribuía a septiembre la cuota de Crediágil pagada el 27-sep aunque
 * septiembre ya estuviera pagado desde el 5: la del 15-oct quedaba sin pagar para siempre. Cerrado
 * en [periodoQueSalda] («Después del corte»): pasada la gracia, un pago salda la cuota del período
 * en que se hizo.
 *
 * ## Y el riesgo de «marcar de más»
 *
 * El resto de este subsistema repite que dar por ocurrido algo que no ocurrió cuesta plata,
 * mientras que el ruido cuesta un toque. Acá la evidencia no es una heurística de nombres: es un
 * movimiento sobre la cuenta de esa deuda, con la categoría que solo escribe el pago de cuota, que
 * de verdad bajó el saldo. Es el hecho, no un parecido.
 */

/** La cuenta de la deuda que salda [ruleId], o `null` si no es una regla sintética. */
fun cuentaDeLaDeudaDe(ruleId: String): String? = when {
    ruleId.startsWith(CREDIT_RULE_PREFIX) -> ruleId.removePrefix(CREDIT_RULE_PREFIX)
    ruleId.startsWith(CARD_RULE_PREFIX) -> ruleId.removePrefix(CARD_RULE_PREFIX)
    else -> null
}

/** La categoría con la que se registra el pago de [ruleId], o `null` si no es sintética. */
fun categoriaQueSalda(ruleId: String): String? = when {
    ruleId.startsWith(CREDIT_RULE_PREFIX) -> CUOTA_CATEGORY
    ruleId.startsWith(CARD_RULE_PREFIX) -> CARD_PAYMENT_CATEGORY
    else -> null
}

/** Las dos categorías que este archivo mira. Es lo que se filtra en SQL. */
val CATEGORIAS_QUE_SALDAN: List<String> = listOf(CUOTA_CATEGORY, CARD_PAYMENT_CATEGORY)

/**
 * **El periodo que salda un pago hecho el [fecha]**: el del vencimiento que estaba vigente ese día,
 * topeado al día del pago — porque **un pago no salda un vencimiento que todavía no llegó** — y con
 * corte, pasada la gracia, la cuota del período del dueño en que se pagó (ver «Después del corte»).
 *
 * Ver el KDoc de arriba para el porqué de cada mitad, para el caso AMEX y para lo que el tope no
 * cierra. `occurredPeriods` va vacío a propósito: acá se pregunta a qué vencimiento apuntaba el
 * calendario, no cuál quedó libre después de saldar otros.
 *
 * Se escribe como el mínimo de los dos, y no como `periodOf(fecha)` a secas, porque lo que hay que
 * fijar es la REGLA —el vencimiento vigente, nunca uno futuro— y no la coincidencia. Y eso ya pagó:
 * desde que `dueDateFor` mira el vencimiento del mes pasado que sigue en gracia, un pago del 2 de
 * octubre sobre una cuota del 30 salda septiembre sin que esta función cambiara.
 *
 * ## Después del corte: la cuota del período en que se pagó
 *
 * El tope compara con el día del pago, y `periodOf` de ese día es su **mes de calendario**. Con
 * corte 25 eso no alcanza: el 27 de septiembre es septiembre por calendario, pero es el período de
 * OCTUBRE del dueño (25-sep a 24-oct), cuya cuota es la del 15 de octubre. El dueño pagó Crediágil
 * (día 15) el 5 de septiembre y otra vez el 27; los dos pagos caían en «2026-09», el `Map` se quedaba
 * con el último, y la cuota del 15-oct no la saldaba nadie: seguía en «Falta por pagar» y el
 * Disponible restaba el pago dos veces (como fijo, y como otro pago de deuda porque nadie lo
 * reclamaba).
 *
 * La regla: si el tope movió el pago (el vencimiento vigente era posterior al pago) **y** el
 * vencimiento de su mes ya había pasado **y** el período del pago tiene una cuota posterior a esa,
 * el pago salda **la cuota de su período**. Nada más: no hace falta otro pago como evidencia.
 *
 * Hasta el mismo día se exigía esa evidencia —un pago de la misma deuda en el período anterior—
 * para no apagar el aviso de una cuota vieja pagada tarde (el lado «barato» del caso AMEX). Master
 * Black mostró el costo: su primer pago registrado en movi fue el 27-sep, no había pago anterior
 * que mirar, y la tarjeta se quedaba en «Falta por pagar» hasta el mes siguiente. El dueño decidió
 * que no: *«si aparece un pago nuevo de una tarjeta va a pasar lo mismo y voy a tener que esperar
 * hasta el otro mes, eso no tiene sentido»*. Él cuenta su plata por período, y el pago se hizo en
 * este.
 *
 * Lo que la regla conserva:
 *
 *  - **No se encadena.** Cada pago se decide con su propia fecha y la regla: la respuesta de hoy no
 *    depende de hasta dónde hacia atrás se cargaron los pagos.
 *  - **Dos partes de la misma cuota nunca se separan.** Las dos caen en el mismo período o las dos
 *    en la gracia del vencimiento anterior.
 *  - **La gracia manda primero.** Un pago dentro de los [DEFAULT_GRACE_DAYS] días de un vencimiento
 *    que ya pasó (el 24 pagado el 26, con corte 25) sigue saldando ese vencimiento: ahí el vigente
 *    todavía es el que pasó y el tope no movió nada.
 *  - **Con mes de calendario no cambia nada**: el período del pago es su mes, y su cuota es la del
 *    mes.
 *
 * El costo aceptado, por decisión del dueño: un pago hecho después del corte que en realidad
 * saldaba la cuota vieja apaga el aviso de la del período. Movi no conoce el extracto para
 * distinguirlo, y el pago se hizo en el período nuevo, que es como él cuenta su plata.
 */
fun periodoQueSalda(
    rule: RecurringRule,
    fecha: LocalDate,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    settings: PeriodSettings = PeriodSettings(),
    zone: ZoneId = AppClock.zone,
): String {
    val vigente = dueDateFor(rule, fecha, graceDays, settings = settings)
    val topeado = periodOf(minOf(vigente, fecha))
    // El tope no movió nada: el vencimiento vigente ya había llegado (en su día o en la gracia).
    if (!vigente.isAfter(fecha)) return topeado
    // El vencimiento del mes del pago todavía no llegó: es el que este pago adelanta, sin ambigüedad.
    val delMes = occurrenceInMonth(YearMonth.from(fecha), rule.dayOfMonth)
    if (delMes.isAfter(fecha)) return topeado
    // La cuota del período en que se pagó. Con calendario es `delMes`, y acá termina.
    val delPeriodo = ocurrenciaEnJuego(fecha, rule.dayOfMonth, settings, zone) ?: return topeado
    if (!delPeriodo.isAfter(delMes) || !ruleIsActiveOn(rule, delPeriodo, settings, zone)) return topeado
    return periodOf(delPeriodo)
}

/**
 * Para cada regla sintética de [rules], **qué movimiento saldó cada periodo**.
 *
 * Función pura: recibe los movimientos ya leídos (ver [cargarPagosDeDeuda]) para poder probarse sin
 * base de datos — y porque los dos endpoints y el barrido de avisos necesitan exactamente esta
 * cuenta, y tres copias de una regla con plata adentro es cómo se llega a que digan cosas
 * distintas.
 *
 * Las reglas reales se ignoran: no tienen cuenta de deuda ni categoría propia, y su «ya ocurrió»
 * ya tiene su mecanismo (`recurring_occurrences`).
 */
fun pagosDeDeudaPorPeriodo(
    rules: List<RecurringRule>,
    pagos: List<FinancialEvent>,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    zone: ZoneId = AppClock.zone,
    settings: PeriodSettings = PeriodSettings(),
): Map<String, Map<String, FinancialEvent>> = rules
    .mapNotNull { rule ->
        val cuenta = cuentaDeLaDeudaDe(rule.id) ?: return@mapNotNull null
        val categoria = categoriaQueSalda(rule.id) ?: return@mapNotNull null
        val deEstaDeuda = pagos
            // Las tres puertas del KDoc: la cuenta de la deuda, la categoría del pago, y que de
            // verdad BAJE la deuda (en una cuenta de deuda eso es INCOME).
            .filter { it.accountId == cuenta }
            .filter { it.category == categoria }
            .filter { it.type == TransactionType.INCOME }
            .sortedBy { it.timestamp }
        val porPeriodo = deEstaDeuda.associateBy {
            periodoQueSalda(rule, epochMillisToAppDate(it.timestamp, zone), graceDays, settings, zone)
        }
        if (porPeriodo.isEmpty()) null else rule.id to porPeriodo
    }
    .toMap()

/**
 * **La plata que de verdad salió de la cuenta** por [pagoDeLaDeuda]: la otra pata del traspaso que
 * escribió `pagoDeCuotaLegs`, buscada dentro de los mismos [pagos] ya cargados (las dos patas
 * llevan la misma categoría, así que la otra ya está en la lista — no hay consulta nueva).
 *
 * No es un detalle de presentación. En una CUOTA las dos patas tienen montos distintos a
 * propósito: la deuda baja por el capital y no por la cuota, así que mostrar la pata de la deuda
 * le diría al dueño «pagaste $12.157» de una cuota de $26.485. En una TARJETA las dos coinciden.
 *
 * Si no hay traspaso —un pago anotado suelto, o importado— se devuelve el mismo movimiento: es lo
 * único que se sabe, y sigue siendo más que no decir nada.
 */
fun plataQueSalio(pagoDeLaDeuda: FinancialEvent, pagos: List<FinancialEvent>): FinancialEvent {
    val traspaso = pagoDeLaDeuda.transferId ?: return pagoDeLaDeuda
    return pagos.firstOrNull {
        it.transferId == traspaso && it.id != pagoDeLaDeuda.id && it.type == TransactionType.EXPENSE
    } ?: pagoDeLaDeuda
}

/** Lo mismo que [pagosDeDeudaPorPeriodo] pero en la forma que espera `dueDateFor`: solo periodos. */
fun periodosSaldados(
    rules: List<RecurringRule>,
    pagos: List<FinancialEvent>,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    zone: ZoneId = AppClock.zone,
    settings: PeriodSettings = PeriodSettings(),
): Map<String, Set<String>> =
    pagosDeDeudaPorPeriodo(rules, pagos, graceDays, zone, settings).mapValues { (_, v) -> v.keys }

/**
 * Une lo sellado a mano con lo derivado de los movimientos, en el mapa que consumen [dueDateFor],
 * [upcomingPayments] y [selectDueForReminder].
 *
 * Hoy las claves no se pisan —una regla sintética nunca tiene fila en `recurring_occurrences`, el
 * POST la rechaza con 400— pero se unen los conjuntos igual: si alguna vez se pisaran, quedarse
 * con uno solo perdería periodos en silencio, y este mapa decide si un pago avisa o no.
 */
fun unirOcurridos(
    selladas: Map<String, Set<String>>,
    derivadas: Map<String, Set<String>>,
): Map<String, Set<String>> {
    if (derivadas.isEmpty()) return selladas
    val out = selladas.toMutableMap()
    derivadas.forEach { (ruleId, periodos) ->
        out[ruleId] = out[ruleId].orEmpty() + periodos
    }
    return out
}

/**
 * Cuántos meses hacia atrás y hacia adelante se buscan pagos.
 *
 * Dos para cada lado. Con el tope de [periodoQueSalda] un pago salda el vencimiento de su mes o, con
 * corte, el de su período, así que para lo que hoy se lee —el período en curso y el anterior que
 * siga en gracia— alcanzaría con mucho menos; la franja sobra a propósito, porque es una guarda para
 * que el índice `(user_id, timestamp)` resuelva la consulta y no un criterio de negocio. Hacia
 * adelante, por un pago anotado con fecha futura.
 */
private const val MESES_DE_PAGOS: Long = 2

/**
 * Los pagos de deuda (no anulados) de [uid] en la franja que puede saldar el vencimiento vigente
 * alrededor de [hoy].
 *
 * Filtra por categoría en SQL: son dos categorías reservadas y en una base con años de movimientos
 * anotados esto es un puñado de filas por mes, no la vida entera del usuario.
 */
suspend fun cargarPagosDeDeuda(uid: String, hoy: LocalDate): List<FinancialEvent> = dbQuery {
    pagosDeDeudaAlrededorDe(uid, hoy)
}

/**
 * Lo mismo que [cargarPagosDeDeuda], dentro de un `dbQuery` ya abierto — y con la MISMA franja, que
 * es lo que importa: el Disponible del Inicio decide qué cuota del checklist está pagada con esto, y
 * `/api/payments/occurrences` con aquello. Si cada uno mirara una franja distinta, «Falta por pagar»
 * y «Disponible» podrían atribuirle el mismo pago a vencimientos distintos.
 */
fun Transaction.pagosDeDeudaAlrededorDe(uid: String, hoy: LocalDate): List<FinancialEvent> =
    loadPagosDeDeudaEntre(
        uid = uid,
        desde = appDateToEpochMillis(hoy.minusMonths(MESES_DE_PAGOS).withDayOfMonth(1)),
        hastaExclusivo = appDateToEpochMillis(hoy.plusMonths(MESES_DE_PAGOS).withDayOfMonth(1)),
    )

/**
 * La consulta de [cargarPagosDeDeuda], como extensión de `Transaction` para poder llamarla desde
 * un `dbQuery` que ya está abierto.
 *
 * No aplica `withCashFlowFlag` (a diferencia de `loadEventsBetween`): de estos eventos solo se leen
 * la cuenta, la categoría, el tipo y la fecha para decidir a qué vencimiento apuntan, y ninguno de
 * esos campos es derivado. Traerse los tipos de cuenta sería una consulta más para un campo que
 * nadie va a mirar.
 */
fun Transaction.loadPagosDeDeudaEntre(uid: String, desde: Long, hastaExclusivo: Long): List<FinancialEvent> {
    val anulados = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    return Events.selectAll()
        .where {
            (Events.userId eq uid) and
                (Events.category inList CATEGORIAS_QUE_SALDAN) and
                (Events.timestamp greaterEq desde) and
                (Events.timestamp less hastaExclusivo)
        }
        // Un pago anulado no pagó nada. Mismo criterio que `loadOccurredBy` con los sellos: se
        // verifica en la LECTURA, porque los caminos por los que un movimiento puede morir son
        // varios y alguno se va a agregar mañana sin acordarse de este archivo.
        .filterNot { it[Events.id] in anulados }
        .map { it.toFinancialEvent() }
}
