package com.jvillada.movi.shared.model

import kotlin.math.abs

/**
 * # Vuelta 53 · «¿Es la cuota de Vehículo 4083?»
 *
 * «Es la cuota de un crédito» (vuelta 51) estaba al fondo del selector de categoría, debajo de las 17
 * categorías y de «Pago de tarjeta». Quien tiene un gasto mal clasificado lo busca en el detalle, no al
 * final de la cuadrícula. Así que el detalle **lo sugiere** cuando el gasto se parece a la cuota de un
 * crédito que todavía no se pagó, y «Sí, es la cuota» hace exactamente lo mismo que esa fila con ese
 * crédito (`vincular-deuda` con `esLaCuota`). **Nunca se vincula solo.**
 *
 * ### Qué es «se parece»
 *
 * Las dos cosas a la vez:
 *
 * - **El monto** está a ±[MARGEN_DE_LA_CUOTA_QUE_SE_PARECE] % de la cuota pactada (elegido: el seguro y
 *   los cargos mueven la cuota unos pocos puntos de un mes a otro, pero un 10 % ya es otra cifra), en la
 *   moneda del crédito.
 * - **La fecha** cae en el período del dueño de un vencimiento **sin saldar** ([CuotaPorSaldar]: el
 *   vencimiento vigente que manda `GET /api/payments/upcoming`, que ya rodó al mes siguiente si la cuota
 *   del período está saldada).
 *
 * Si se parece a **varios** créditos, no sugiere ninguno: elegir entre dos parecidos es del dueño (la
 * fila «Es la cuota de un crédito» sigue ahí, con la lista entera).
 *
 * ### Por qué la palabra no cuenta
 *
 * «crédito» o «cuota» en la descripción **no alcanza sola y tampoco desempata**. «Cuota de manejo» es
 * un cobro de la tarjeta, «Cuota 3/12» una compra diferida y «Abono a crédito» un abono: la palabra
 * acierta poco justo donde haría falta, y con el monto y la fecha exigidos no agrega nada que se pueda
 * medir. Decidido así; si un día hiciera falta, entraría como desempate, no como puerta.
 */

/** Cuánto se puede alejar el gasto de la cuota pactada para parecerse, en por ciento: el 10 % (elegido). */
const val MARGEN_DE_LA_CUOTA_QUE_SE_PARECE: Int = 10

/**
 * La cuota de un crédito que **todavía no se saldó**: su vencimiento vigente ([vence], `"AAAA-MM-DD"`)
 * y la cuota pactada ([cuota], en [moneda]).
 */
data class CuotaPorSaldar(
    val creditoId: String,
    val nombre: String,
    val cuota: Long,
    val moneda: String,
    val vence: String,
)

/**
 * Las cuotas por saldar de los créditos de [cuentas]: el vencimiento vigente de cada uno ([upcoming], las
 * reglas sintéticas `credit_<id>`) **y** la cuota del período que [ocurrencias] (`GET
 * /api/payments/occurrences`) todavía da por pendiente (`occurred = false`).
 *
 * Las dos hacen falta (revisión de la vuelta 53, el mismo origen que #541): pasada la gracia, `upcoming`
 * ya rodó el crédito al mes siguiente —la cuota del 5, vista el 28, aparece el 5 de noviembre— aunque la
 * de octubre siga sin pagar; esa la dice solo `occurrences`, la fuente del checklist. Sin ella un gasto de
 * octubre se comparaba con la cuota de noviembre y no se sugería nada justo en el caso más común (el banco
 * debita el 5 y el dueño revisa días después). La cuota pactada sale de la regla de `upcoming`; una
 * ocurrencia de un crédito sin regla no entra. Un crédito que ya no está entre las cuentas, o sin cuota,
 * tampoco.
 */
fun cuotasPorSaldar(
    upcoming: List<UpcomingPayment>,
    cuentas: List<Account>,
    ocurrencias: List<OccurrenceState> = emptyList(),
): List<CuotaPorSaldar> {
    val creditos = cuentas.filter { it.type == AccountType.LOAN }.associateBy { it.id }
    val reglas = upcoming.map { it.rule }.filter { it.id.startsWith(CREDIT_RULE_PREFIX) }.associateBy { it.id }
    fun cuota(ruleId: String, vence: String): CuotaPorSaldar? {
        val regla = reglas[ruleId] ?: return null
        val credito = creditos[ruleId.removePrefix(CREDIT_RULE_PREFIX)] ?: return null
        if (regla.amount <= 0L) return null
        return CuotaPorSaldar(creditoId = credito.id, nombre = credito.name, cuota = regla.amount, moneda = credito.currency, vence = vence)
    }
    val vigentes = upcoming.mapNotNull { cuota(it.rule.id, it.dueDate) }
    val pendientes = ocurrencias
        .filter { !it.occurred && it.ruleId.startsWith(CREDIT_RULE_PREFIX) }
        .mapNotNull { cuota(it.ruleId, it.dueDate) }
    return (pendientes + vigentes).distinctBy { it.creditoId to it.vence }
}

/** ¿[monto] está a ±[MARGEN_DE_LA_CUOTA_QUE_SE_PARECE] % de [cuota]? Con aritmética entera: sin redondeos. */
fun montoSeAcercaALaCuota(monto: Long, cuota: Long): Boolean =
    cuota > 0L && monto > 0L && abs(monto - cuota) * 100L <= cuota * MARGEN_DE_LA_CUOTA_QUE_SE_PARECE

/**
 * **El crédito cuya cuota se parece a [gasto]**, o `null`: ninguno, o más de uno. Solo un gasto suelto
 * (sin `transferId`) con una categoría que el dueño elige —una cuota, un pago de tarjeta o un traspaso
 * ya son lo que son—. Ver el KDoc del archivo para qué es «se parece».
 */
fun cuotaQueSeParece(
    gasto: FinancialEvent,
    cuotas: List<CuotaPorSaldar>,
    settings: PeriodSettings,
): CuotaPorSaldar? {
    if (gasto.type != TransactionType.EXPENSE || gasto.transferId != null) return null
    if (esCategoriaQueNoSeElige(gasto.category)) return null
    val periodo = periodoDe(gasto.timestamp, settings)
    return cuotas
        .filter { it.moneda == gasto.currency }
        .filter { montoSeAcercaALaCuota(gasto.amount, it.cuota) }
        .filter { periodoDeLaFecha(it.vence, settings) == periodo }
        .distinctBy { it.creditoId }
        .singleOrNull()
}

/** «¿Es la cuota de Vehículo 4083?» */
fun preguntaDeLaCuotaQueSeParece(cuota: CuotaPorSaldar): String = "¿Es la cuota de ${cuota.nombre}?"

/** El botón de la sugerencia: hace lo mismo que «Es la cuota de un crédito» con ese crédito. */
const val SI_ES_LA_CUOTA: String = "Sí, es la cuota"

// ── Vuelta 57 · la fila de Pagos también ofrece el gasto parecido ─────────────────────────────────

/*
 * En Pagos, la cuota pendiente del Vehículo 4083 solo decía «¿Se cobró?», aunque el detalle del gasto
 * de $980.000 en «Otros» ya sugería «¿Es la cuota de Vehículo 4083?». La fila usa ahora **la misma
 * regla** ([cuotaQueSeParece]: el monto a ±10 %, el período del vencimiento sin saldar y un solo crédito
 * posible) y ofrece «¿Es el de $980.000?» · «Sí, es la cuota», que hace lo mismo que la sugerencia del
 * detalle (`vincular-deuda` con `esLaCuota`). Nunca se vincula solo.
 */

/** Un gasto suelto que se parece a la cuota [cuota] sin saldar. */
data class GastoParecidoALaCuota(val cuota: CuotaPorSaldar, val gasto: FinancialEvent)

/** La clave de un vencimiento de un crédito: el crédito y el día que vence. */
fun claveDeLaCuotaPorSaldar(creditoId: String, vence: String): String = "$creditoId@$vence"

/**
 * **Por cada cuota sin saldar, el gasto suelto que se le parece**, con [cuotaQueSeParece] (la regla del
 * detalle, entera: si un gasto se parece a dos créditos no se ofrece en ninguno). Si varios gastos se
 * parecen a la misma cuota, el de monto más cercano; si empatan, el más reciente. La clave es
 * [claveDeLaCuotaPorSaldar]. Pura.
 */
fun gastosParecidosALasCuotas(
    gastos: List<FinancialEvent>,
    cuotas: List<CuotaPorSaldar>,
    settings: PeriodSettings,
): Map<String, GastoParecidoALaCuota> =
    gastos
        .mapNotNull { gasto -> cuotaQueSeParece(gasto, cuotas, settings)?.let { cuota -> GastoParecidoALaCuota(cuota, gasto) } }
        .groupBy { claveDeLaCuotaPorSaldar(it.cuota.creditoId, it.cuota.vence) }
        .mapValues { (_, parecidos) ->
            parecidos.minWith(
                compareBy<GastoParecidoALaCuota> { abs(it.gasto.amount - it.cuota.cuota) }.thenByDescending { it.gasto.timestamp },
            )
        }
