package com.jvillada.movi.shared.model

/**
 * # «N veces este período»: una sola cuenta (vuelta 66)
 *
 * El detalle de un movimiento decía «5 veces este período · $66.800» de Juan Valdez y Buscar decía
 * «4 veces · $60.800» del mismo comercio. Cada pantalla agrupaba con su clave (el detalle por la marca,
 * Buscar por el texto) y contaba movimientos, no compras: las partes de una compra repartida en partes
 * («Repartir en partes», vuelta 27) sumaban una visita cada una. Ahora las dos llaman acá.
 *
 * - **El mismo comercio** es [claveDelComercio]: la marca del catálogo ([marcaDe]) si la hay —«Tinto y
 *   pan» en Juan Valdez es Juan Valdez—, y si no la clave de comercio de siempre ([claveDeComercio]).
 * - **Una compra** es [claveDeLaCompra]: las partes de un reparto salen de la misma cuenta, con la
 *   misma fecha y se anotaron en el mismo instante (el server las escribe juntas); dos cafés de ayer
 *   anotados a mano comparten el mediodía de relleno pero no el instante en que se anotaron. Sin ese
 *   instante (lo muy viejo), cada movimiento es su propia compra.
 * - El total es lo que cuenta en tus gastos ([montoContado]), en la moneda del movimiento.
 */

/** La clave de «el mismo comercio»: la marca primero, la clave de comercio después; `null` sin ninguna. */
fun claveDelComercio(evento: FinancialEvent): String? =
    marcaDe(evento)?.let { "marca:${it.id}" } ?: claveDeComercio(evento)?.let { "comercio:$it" }

/** La clave de la compra de [evento]: sus partes (si se repartió) comparten esta clave. */
fun claveDeLaCompra(evento: FinancialEvent): String =
    evento.createdAt?.let { "${evento.accountId}|${evento.timestamp}|$it" } ?: "id:${evento.id}"

/** Cuántas compras y cuánto, de un comercio en un período. */
data class VecesEnElComercio(val veces: Int, val total: Long, val movimientos: List<FinancialEvent>)

/**
 * ¿[evento] entra en la cuenta de un comercio? Ni la mitad de un traspaso, ni un saldo inicial, ni tu
 * parte de lo que pagó otro (no es una visita tuya).
 */
fun cuentaComoVisita(evento: FinancialEvent): Boolean =
    evento.transferId == null && !isOpeningBalance(evento) && !esTuParteDeOtro(evento)

/**
 * **Las veces de [movimientos] en el comercio de [clave]**, del tipo y la moneda de [comoEste] (si se
 * da): cada compra una vez, aunque se haya repartido en partes. Sin repetir ids.
 */
fun vecesEnElComercio(
    movimientos: List<FinancialEvent>,
    clave: String,
    comoEste: FinancialEvent? = null,
): VecesEnElComercio {
    val mismos = movimientos
        .filter { cuentaComoVisita(it) && claveDelComercio(it) == clave }
        .filter { comoEste == null || (it.type == comoEste.type && it.currency == comoEste.currency) }
        .distinctBy { it.id }
    return VecesEnElComercio(
        veces = mismos.map { claveDeLaCompra(it) }.distinct().size,
        total = mismos.sumOf { montoContado(it) },
        movimientos = mismos,
    )
}
