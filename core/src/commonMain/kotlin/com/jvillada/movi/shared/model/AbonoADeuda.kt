package com.jvillada.movi.shared.model

import kotlin.math.abs

/**
 * # Vuelta 63 · el abono mensual a una deuda, un recordatorio que se tilda
 *
 * El dueño eligió (29-oct) que el abono extra del plan de salida («Abono extra a Vehículo 4083 ·
 * $500.000») sea **un recordatorio en Pagos que se marca solo cuando anotas ese traspaso**. Mientras
 * está pendiente **no** se resta de «Te quedan»: no es un fijo, es plata que mueves a tu deuda. Cuando
 * de verdad lo haces, el traspaso baja «Te quedan» con la regla de siempre
 * (`pagosDeDeudaFueraDelChecklist`), una sola vez.
 *
 * Es una [RecurringRule] con [RecurringRule.haciaDeuda]. Lo que la distingue de las demás vive acá:
 *
 * - **No es un fijo** ([PagoDelPeriodo.esAbono]): fuera de [fijosDelPeriodo], [faltaPorPagar], la caja
 *   proyectada y lo apartado. Se dice aparte («y un abono planeado de $500.000», [abonosPlaneados]).
 * - **Su ocurrencia es un traspaso** ([esElAbonoALaDeuda]): la pata que sale de una cuenta del dueño
 *   cuando la otra pata entra a esa deuda. No la pata de una cuota (esa salda la cuota del crédito, no
 *   el abono), ni la de un desembolso.
 * - **Se tilda solo con el monto a ±[TOLERANCIA_DEL_ABONO_PORCIENTO] %** ([montoDelAbonoPega]): un
 *   abono de $480.000 contra uno planeado de $500.000 es ese abono; uno de $300.000 se pregunta.
 */

/** ¿Esta regla es el abono mensual a una deuda? */
val RecurringRule.esAbonoADeuda: Boolean get() = !haciaDeuda.isNullOrBlank()

/** Cuánto puede diferir el traspaso del abono planeado para que Movi lo tilde solo: 5 %. Elegido. */
const val TOLERANCIA_DEL_ABONO_PORCIENTO: Long = 5L

/** ¿[real] está a ±5 % de [esperado]? Sin un monto planeado positivo, nunca. */
fun montoDelAbonoPega(esperado: Long, real: Long): Boolean =
    esperado > 0L && real > 0L && abs(real - esperado) * 100 <= esperado * TOLERANCIA_DEL_ABONO_PORCIENTO

/**
 * Las categorías con que una pata puede entrar a la deuda y ser un abono: el traspaso de siempre a un
 * crédito ([TRANSFER_CATEGORY], «Abono extraordinario») y el pago de una tarjeta
 * ([CARD_PAYMENT_CATEGORY]: a una tarjeta no se le hace un traspaso, se le paga). La de la cuota
 * ([CUOTA_CATEGORY]) y la del desembolso quedan afuera.
 */
private val CATEGORIAS_DEL_ABONO = setOf(TRANSFER_CATEGORY, CARD_PAYMENT_CATEGORY)

/**
 * **La pata que entra a [deudaId]** si [evento] es la pata que sale de una cuenta del dueño en un
 * traspaso (o pago de tarjeta) hacia esa deuda, o `null`. La otra pata se busca en [eventos] por el
 * `transferId`. No mira el monto: eso es [montoDelAbonoPega].
 */
fun pataQueEntraALaDeuda(deudaId: String, evento: FinancialEvent, eventos: List<FinancialEvent>): FinancialEvent? {
    if (evento.type != TransactionType.EXPENSE || evento.accountId == deudaId) return null
    if (evento.category !in CATEGORIAS_DEL_ABONO) return null
    val traspaso = evento.transferId ?: return null
    return eventos.firstOrNull {
        it.transferId == traspaso && it.id != evento.id && it.accountId == deudaId &&
            it.type == TransactionType.INCOME && it.category in CATEGORIAS_DEL_ABONO
    }
}

/**
 * **¿[evento] es el abono de [regla]?** La pata que sale hacia su deuda ([pataQueEntraALaDeuda]), en la
 * moneda de la regla. Con [conElMonto], además, a ±5 % del monto planeado: es lo que deja tildar solo.
 * Sin él, es lo que puede proponerse o confirmarse a mano («Sí, fue este», «Anotar este abono»).
 */
fun esElAbonoALaDeuda(
    regla: RecurringRule,
    evento: FinancialEvent,
    eventos: List<FinancialEvent>,
    conElMonto: Boolean = true,
): Boolean {
    val deuda = regla.haciaDeuda?.takeIf { it.isNotBlank() } ?: return false
    if (evento.currency != regla.currency) return false
    pataQueEntraALaDeuda(deuda, evento, eventos) ?: return false
    return !conElMonto || montoDelAbonoPega(regla.amount, evento.amount)
}

/** El nombre de la regla del abono: «Abono extra a Vehículo 4083». */
fun nombreDelAbonoA(deuda: String): String = "Abono extra a $deuda"

/**
 * **La regla del abono mensual** a [deudaId] por [monto], el día [dia], saliendo de [desdeCuentaId]
 * (si se sabe). Es la que crea «Recordármelo cada mes». Un gasto en la categoría «Traspaso»: no suma en
 * ningún presupuesto ni categoría, y lo que la hace un abono es [RecurringRule.haciaDeuda].
 */
fun reglaDelAbono(
    id: String,
    deudaId: String,
    nombreDeLaDeuda: String,
    monto: Long,
    dia: Int,
    desdeCuentaId: String?,
): RecurringRule = RecurringRule(
    id = id,
    name = nombreDelAbonoA(nombreDeLaDeuda),
    category = TRANSFER_CATEGORY,
    amount = monto,
    dayOfMonth = dia.coerceIn(1, 31),
    type = TransactionType.EXPENSE,
    accountId = desdeCuentaId,
    haciaDeuda = deudaId,
)

/** Los abonos planeados que todavía no se hicieron en el período: lo que se dice aparte de «Falta». */
fun abonosPlaneados(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { it.esAbono && !it.pagado && it.moneda == "COP" }.sumOf { it.monto }
