package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isReservedCategory

/**
 * Movimientos del período que **se llaman como la categoría** del presupuesto pero están
 * archivados en otra.
 *
 * ### De dónde sale
 *
 * El dueño creó un presupuesto de $2.000.000 en «Mercado» y tenía, ese mismo período, un gasto de
 * exactamente $2.000.000 cuya **descripción** era «Mercado» y cuya **categoría** era «Comida».
 * Pidió: *«tengo un gasto del período exactamente por el monto de todo el presupuesto del mercado,
 * debería poder permitirme asociarlo a dicha categoría de gastos»*.
 *
 * El aviso que ya existe resuelve la mitad —le dice qué categorías SÍ tienen gasto para que elija
 * una— pero no la otra: mover ese movimiento a la categoría que él quiere vigilar. Escribir
 * «Mercado» como descripción y esperar que cuente en un presupuesto de «Mercado» es exactamente lo
 * que cualquiera supondría.
 *
 * ### Por qué la descripción y no el monto
 *
 * El monto coincidía, y era tentador usarlo. Pero un gasto que casualmente vale lo mismo que el
 * límite no tiene nada que ver con él: proponer por monto acertaría en este caso y sería ruido en
 * todos los demás. El **nombre** sí es una intención — alguien que escribe «Mercado» está diciendo
 * qué es ese gasto.
 */
fun gastosQueSuenanA(
    categoria: String,
    dias: List<EventDay>,
    ventana: LongRange,
): List<FinancialEvent> {
    val buscada = categoria.trim()
    if (buscada.isEmpty() || isReservedCategory(buscada)) return emptyList()

    return dias.flatMap { it.items }
        .filter { it.timestamp in ventana }
        .filter { it.type == TransactionType.EXPENSE && it.currency == "COP" }
        // Ya está donde debería: no hay nada que proponer.
        .filterNot { it.category.equals(buscada, ignoreCase = true) }
        // Y no se toca lo que Movi gobierna: mover un «Saldo inicial» a una categoría normal lo
        // convertiría en gasto del mes.
        .filterNot { isReservedCategory(it.category) }
        .filter { it.description.trim().equals(buscada, ignoreCase = true) }
}
