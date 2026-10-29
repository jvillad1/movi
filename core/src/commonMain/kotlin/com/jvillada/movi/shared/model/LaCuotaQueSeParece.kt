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
 * Las cuotas por saldar de los créditos de [cuentas], sacadas de los vencimientos vigentes
 * ([upcoming], las reglas sintéticas `credit_<id>`). Un crédito que ya no está entre las cuentas, o sin
 * cuota, no entra.
 */
fun cuotasPorSaldar(upcoming: List<UpcomingPayment>, cuentas: List<Account>): List<CuotaPorSaldar> {
    val creditos = cuentas.filter { it.type == AccountType.LOAN }.associateBy { it.id }
    return upcoming.mapNotNull { pago ->
        if (!pago.rule.id.startsWith(CREDIT_RULE_PREFIX)) return@mapNotNull null
        val credito = creditos[pago.rule.id.removePrefix(CREDIT_RULE_PREFIX)] ?: return@mapNotNull null
        if (pago.rule.amount <= 0L) return@mapNotNull null
        CuotaPorSaldar(
            creditoId = credito.id,
            nombre = credito.name,
            cuota = pago.rule.amount,
            moneda = credito.currency,
            vence = pago.dueDate,
        )
    }
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
