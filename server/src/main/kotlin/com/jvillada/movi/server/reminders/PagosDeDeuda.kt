package com.jvillada.movi.server.reminders

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
 * ## A qué vencimiento se le atribuye un pago
 *
 * **Al que la app consideraba vigente el día en que se pagó** — literalmente
 * `dueDateFor(regla, fecha del pago)`, la misma función con la que se pinta «Próximos». No es una
 * ventana nueva inventada para esto, y por eso no puede divergir de lo que el dueño vio:
 *
 *  - La cuota de Crediágil (día 15) pagada el 5 de septiembre: el 5, el vencimiento vigente era el
 *    15 de septiembre → salda septiembre. ✔
 *  - El pago de AMEX (día 16) hecho el 30 de agosto: para entonces el 16 de agosto ya había pasado
 *    la ventana de gracia, así que la app misma anunciaba «vence el 16 de septiembre» → salda
 *    septiembre. ✔ Una ventana centrada en el vencimiento habría dejado ese pago sin dueño, que es
 *    justo el caso que el dueño reclamó.
 *  - El pago de Nu (día 1) hecho el 5 de septiembre, dentro de la gracia: salda septiembre, no
 *    octubre. ✔
 *
 * La propiedad que se gana: un pago se atribuye **al vencimiento que la app le estaba mostrando
 * cuando lo hizo**. Y como el mismo `dueDateFor` rueda pasada la gracia, ningún pago queda
 * huérfano ni dos pagos consecutivos caen en el mismo periodo salvo que de verdad se hayan hecho
 * dentro del mismo ciclo (y ahí el `Map` se queda con el último, que es lo correcto: el periodo
 * está saldado igual).
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
 * **El periodo que salda un pago hecho el [fecha]**: el del vencimiento que estaba vigente ese día.
 *
 * Ver el KDoc de arriba para el porqué. `occurredPeriods` va vacío a propósito: acá se pregunta a
 * qué vencimiento apuntaba el calendario, no cuál quedó libre después de saldar otros.
 */
fun periodoQueSalda(rule: RecurringRule, fecha: LocalDate, graceDays: Int = DEFAULT_GRACE_DAYS): String =
    periodOf(dueDateFor(rule, fecha, graceDays))

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
): Map<String, Map<String, FinancialEvent>> = rules
    .mapNotNull { rule ->
        val cuenta = cuentaDeLaDeudaDe(rule.id) ?: return@mapNotNull null
        val categoria = categoriaQueSalda(rule.id) ?: return@mapNotNull null
        val porPeriodo = pagos
            .asSequence()
            // Las tres puertas del KDoc: la cuenta de la deuda, la categoría del pago, y que de
            // verdad BAJE la deuda (en una cuenta de deuda eso es INCOME).
            .filter { it.accountId == cuenta }
            .filter { it.category == categoria }
            .filter { it.type == TransactionType.INCOME }
            .sortedBy { it.timestamp }
            .associateBy { periodoQueSalda(rule, epochMillisToAppDate(it.timestamp, zone), graceDays) }
        if (porPeriodo.isEmpty()) null else rule.id to porPeriodo
    }
    .toMap()

/** Lo mismo que [pagosDeDeudaPorPeriodo] pero en la forma que espera `dueDateFor`: solo periodos. */
fun periodosSaldados(
    rules: List<RecurringRule>,
    pagos: List<FinancialEvent>,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    zone: ZoneId = AppClock.zone,
): Map<String, Set<String>> =
    pagosDeDeudaPorPeriodo(rules, pagos, graceDays, zone).mapValues { (_, v) -> v.keys }

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
 * Dos para cada lado, y no «este mes»: el pago de AMEX del 30 de agosto salda el vencimiento del
 * 16 de septiembre (ver arriba), así que un piso en el primero del mes en curso lo habría dejado
 * afuera — el caso concreto que el dueño reclamó. Hacia adelante, por un pago anotado con fecha
 * futura. Es una franja acotada para que el índice `(user_id, timestamp)` la resuelva, no un
 * criterio de negocio: quien decide a qué periodo va cada pago es [periodoQueSalda].
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
    loadPagosDeDeudaEntre(
        uid = uid,
        desde = appDateToEpochMillis(hoy.minusMonths(MESES_DE_PAGOS).withDayOfMonth(1)),
        hastaExclusivo = appDateToEpochMillis(hoy.plusMonths(MESES_DE_PAGOS).withDayOfMonth(1)),
    )
}

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
