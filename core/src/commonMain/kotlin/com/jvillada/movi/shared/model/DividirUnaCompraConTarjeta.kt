package com.jvillada.movi.shared.model

/*
 * # Vuelta 71 · dividir en categorías una compra con tarjeta
 *
 * «Dividir en categorías» (vuelta 27, `POST /api/events/{id}/repartir`) solo se ofrecía en lo que salió
 * de una cuenta de dinero. Una compra con tarjeta que fue mercado y farmacia quedaba en una sola
 * categoría. Desde esta vuelta también se divide, **solo en gastos con su categoría**: desde una deuda no
 * tiene sentido una cuota, un traspaso ni el pago de otra tarjeta. Cada parte es un gasto de la misma
 * tarjeta, así que el saldo de la tarjeta y el total del período no cambian; solo se reparte la
 * categoría. Lo deshace «Deshacer» como cualquier división.
 */

/** Lo que el server contesta cuando a una compra con tarjeta le llega una parte que no es un gasto. */
const val DE_UNA_TARJETA_SOLO_GASTOS: String =
    "Una compra con tarjeta se divide solo en gastos, cada uno con su categoría."

/**
 * **¿Lo que salió de una cuenta de tipo [tipo] se puede dividir?** De una cuenta de dinero, sí (como
 * siempre). De una deuda, solo una compra con tarjeta: una `CREDIT_CARD`, sin `transferId` (la pata de un
 * avance o de un desembolso no: al anularla la plata de la otra punta desaparecería) y en una categoría
 * que se elige, no un ajuste ni un saldo inicial. La usan la app (`sePuedeRepartirEnPartes`) y el server
 * (`repartirUnMovimiento`), así dicen lo mismo.
 */
fun seDivideLoQueSalioDe(tipo: AccountType, transferId: String?, categoria: String): Boolean =
    tipo.group != AccountGroup.DEUDA ||
        (tipo == AccountType.CREDIT_CARD && transferId == null && !isReservedCategory(categoria))

/** Lo que contesta el server cuando lo que se quiere dividir salió de una deuda y no es una compra con tarjeta. */
const val DE_UNA_DEUDA_SOLO_COMPRAS_CON_TARJETA: String =
    "De una deuda solo se divide una compra con tarjeta: no un desembolso, un avance ni un ajuste."

/** Los tipos de parte que se ofrecen al dividir lo que salió de una cuenta de tipo [tipo]. */
fun tiposDeParteDesde(tipo: AccountType): List<QueFueLaParte>? =
    if (tipo.group == AccountGroup.DEUDA) listOf(QueFueLaParte.GASTO) else null

/**
 * ¿Se aceptan estas [partes] al dividir algo que salió de una cuenta de tipo [tipo]? Desde una deuda,
 * solo gastos sueltos con su categoría (sin dos patas, sin pago del período, sin «a un tercero»); desde
 * lo demás, todo lo de siempre.
 */
fun partesQueSeAceptanDesde(tipo: AccountType, partes: List<ParteDelReparto>): Boolean {
    if (tipo.group != AccountGroup.DEUDA) return true
    return partes.all { parte ->
        parte.patas == null && parte.evento != null && parte.ruleId.isBlank() &&
            (parte.tipo == null || parte.tipo == QueFueLaParte.GASTO)
    }
}
