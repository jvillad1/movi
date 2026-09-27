package com.jvillada.movi.ui.transactions

import com.jvillada.movi.ui.components.formatCOP

/**
 * # «Día a día»: lo que gastaste ese día contra lo que podías gastar
 *
 * El dueño: *«En Movimientos, Flujo del día, me parecería genial que de acuerdo al disponible
 * mensual por día vayas además de anotando el valor del flujo del día contando cuánto podías gastar
 * por día, para ir viendo si te pasaste o no»*.
 *
 * Se compara el **gasto variable del día** (lo que sale del bolsillo sin contar los pagos fijos del
 * checklist, las tarjetas ni los traspasos) con la **meta diaria** de la tarjeta «Disponible» de
 * Plan: la misma cifra, fija para todo el período. No el «Flujo del día», que incluye sueldos y
 * pagos fijos y no sirve para saber si te pasaste.
 *
 * Todo puro y aparte de la pantalla, para que las pruebas lean las frases exactas.
 */
data class LineaDelDiaADia(val gastado: Long, val meta: Long) {

    /** Gastó más de lo que podía. Gastar justo la meta no es pasarse. */
    val pasada: Boolean get() = gastado > meta

    /** «Día a día: $6.700 de $41.924». */
    val base: String get() = "Día a día: ${formatCOP(gastado)} de ${formatCOP(meta)}"

    /** «te pasaste $2.076», o `null` si el día quedó dentro de la meta. */
    val aviso: String? get() = if (pasada) "te pasaste ${formatCOP(gastado - meta)}" else null

    /** La línea entera, tal como la lee TalkBack. */
    val texto: String get() = aviso?.let { "$base · $it" } ?: base
}

/**
 * La línea de un día, o `null` si no hay nada honesto que decir: sin meta (no hay disponible que
 * dividir) no se dibuja ni «$0» ni relleno.
 *
 * @param gastoDelDia el gasto variable de ese día. Un reembolso que lo dejaría negativo cuenta 0.
 */
fun lineaDelDiaADia(gastoDelDia: Long, metaPorDia: Long?): LineaDelDiaADia? {
    if (metaPorDia == null || metaPorDia <= 0L) return null
    return LineaDelDiaADia(gastado = gastoDelDia.coerceAtLeast(0L), meta = metaPorDia)
}
