package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # El motivo de cada envío a una persona
 *
 * A Caro el dueño le gira por tres motivos: el mercado, la cuota de un crédito compartido y la
 * mesada de la tía. Hasta acá todo quedaba bajo «Transferencia a Caro», sin motivo, y la ficha de
 * Caro decía cuánto le mandó pero no para qué.
 *
 * ## Dónde vive el motivo: en la categoría, no en un campo nuevo
 *
 * El motivo **es** la categoría del movimiento. «Mercado», «Cuota de crédito», «Tía» ya son
 * categorías suyas, y son exactamente la respuesta a «¿para qué fue esta plata?», que es la pregunta
 * que la categoría contesta en todo el resto de la app (Gastos, Presupuestos, Movi AI). Un campo
 * aparte duplicaría esa respuesta en cada envío —y cuando las dos dijeran cosas distintas, ¿cuál
 * manda en el presupuesto de Mercado?—. La nota (`description`) sigue siendo texto libre; el nombre
 * («Transferencia a Caro») dice a quién, la categoría dice para qué.
 *
 * ## Cómo lo aprende Movi
 *
 * La memoria de categorías ya aprende por la huella (la cuenta \*0756 de Caro), pero con tres motivos
 * para la misma cuenta la huella sola elige el más usado, y se equivoca dos de cada tres veces. Lo que
 * distingue los motivos es **el monto**: la cuota es siempre $1.300.000, la mesada siempre la misma.
 * Por eso, para un envío a alguien guardado, Movi busca **el último envío a esa persona por el mismo
 * monto exacto** y propone su categoría: «La última vez que le mandaste $1.300.000 a Caro fue Cuota
 * de crédito». Sin uno igual, manda la memoria de siempre.
 */

/** Lo que Movi recuerda del último envío a una persona por el mismo monto. */
@Serializable
data class MotivoAprendido(
    val categoria: String,
    val monto: Long,
    val moneda: String,
    val timestamp: Long,
)

/**
 * **El motivo del envío de [monto] a [destino]**: la categoría del último envío a esa persona por el
 * **mismo monto exacto**, o `null` si nunca le mandó ese monto (o lo que le mandó no tiene una
 * categoría que se aprenda: «Traspaso», «Otros»).
 *
 * Solo gastos —lo que se le mandó—, y en la misma moneda: un envío en dólares no explica uno en pesos.
 */
fun motivoDelEnvio(
    monto: Long,
    moneda: String,
    destino: DestinoConocido,
    eventos: List<FinancialEvent>,
): MotivoAprendido? {
    if (monto <= 0L) return null
    return eventos
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.amount == monto && it.currency == moneda }
        .filter { it.category.isNotBlank() && it.category !in CATEGORIAS_QUE_NO_SE_APRENDEN }
        .filter { claveComparableDeNombre(it.category) !in setOf("otros", "otro") }
        .filter { vaHaciaElDestino(it, destino) }
        .maxByOrNull { it.timestamp }
        ?.let { MotivoAprendido(it.category, it.amount, it.currency, it.timestamp) }
}

/** «La última vez que le mandaste $1.300.000 a Caro fue Cuota de crédito». */
fun textoDelMotivoAprendido(motivo: MotivoAprendido, destino: DestinoConocido): String =
    "La última vez que le mandaste ${montoDelDebito(motivo.monto, motivo.moneda)} a ${destino.nombre} fue ${motivo.categoria}"

/**
 * **El aviso leído, con el motivo que Movi aprendió** si va a alguien guardado y ya le mandó ese
 * mismo monto: la categoría de esa vez, y el porqué en `aprendidoDe`. Le gana a la memoria de la
 * huella (que con tres motivos para la misma cuenta elige el más usado), pero no toca lo que dice el
 * aviso: [categoriaDelBanco] es la que leyó el parser, y si es el pago de una tarjeta, una cuota, un
 * traspaso o un avance, esa manda.
 */
fun conElMotivoDelEnvio(
    parsed: ParsedSms,
    categoriaDelBanco: String,
    textoDelMensaje: String,
    destinos: List<DestinoConocido>,
    eventos: List<FinancialEvent>,
): ParsedSms {
    if (parsed.type != TransactionType.EXPENSE) return parsed
    if (categoriaDelBanco in CATEGORIAS_DE_DOS_PATAS) return parsed
    val destino = destinoQueNombra(textoDelMensaje, destinos) ?: return parsed
    val motivo = motivoDelEnvio(kotlin.math.round(parsed.amount).toLong(), parsed.currency, destino, eventos) ?: return parsed
    return parsed.copy(category = motivo.categoria, aprendidoDe = textoDelMotivoAprendido(motivo, destino))
}

/** Lo que se le mandó a alguien por un motivo en el período: la fila de su ficha. */
@Serializable
data class EnvioPorMotivo(
    val motivo: String,
    val monto: Long,
    val moneda: String = "COP",
    val cuantos: Int = 1,
)

/**
 * **Lo que se le mandó por motivo**, del más grande al más chico: «Mercado $2.000.000 (2) · Cuota de
 * crédito $1.300.000». Sobre los movimientos que ya fueron a esa persona ([movimientosHaciaElDestino]).
 * Un envío sin categoría que diga algo va como «Sin motivo».
 */
fun enviadoPorMotivo(movimientos: List<FinancialEvent>): List<EnvioPorMotivo> =
    movimientos
        .groupBy { (it.category.takeIf { c -> c.isNotBlank() && c !in CATEGORIAS_QUE_NO_SE_APRENDEN } ?: SIN_MOTIVO) to it.currency }
        .map { (clave, del) -> EnvioPorMotivo(clave.first, del.sumOf { it.amount }, clave.second, del.size) }
        .sortedWith(compareBy<EnvioPorMotivo> { it.moneda != "COP" }.thenByDescending { it.monto })

/** El motivo de un envío sin una categoría que diga para qué fue. */
const val SIN_MOTIVO: String = "Sin motivo"
