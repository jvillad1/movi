package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/** Largo máximo del propósito de un [Apartado] («Colegio», «Impuesto predial»). */
const val MAX_PROPOSITO_APARTADO = 60

/**
 * **Un apartado**: plata que sigue en una cuenta tuya pero que ya tiene dueño — «estos $8M son del
 * colegio». No mueve dinero ni crea un movimiento; solo la quita de «Tu plata» (ver [patrimonioDe]).
 *
 * @param accountId la cuenta donde está la plata.
 * @param monto lo que se apartó, en pesos.
 * @param categoria la categoría de gasto que lo consume (null = ninguna lo consume solo).
 * @param desde desde cuándo cuentan los gastos que lo consumen (epoch millis; 0 = sin límite).
 * @param restante lo que queda por gastar: [monto] menos lo consumido ([restanteDelApartado]).
 */
@Serializable
data class Apartado(
    val id: String = "",
    val accountId: String,
    val proposito: String,
    val monto: Long,
    val categoria: String? = null,
    val desde: Long = 0L,
    val restante: Long = monto,
)

/** Lo que queda de un apartado de [monto] tras gastar [consumido]; nunca baja de cero. */
fun restanteDelApartado(monto: Long, consumido: Long): Long = (monto - consumido).coerceAtLeast(0L)

/** Cuánto hay apartado en cada cuenta: la suma de [Apartado.restante] por `accountId`. */
fun apartadoPorCuenta(apartados: List<Apartado>): Map<String, Long> =
    apartados.groupBy { it.accountId }.mapValues { (_, v) -> v.sumOf { it.restante } }
