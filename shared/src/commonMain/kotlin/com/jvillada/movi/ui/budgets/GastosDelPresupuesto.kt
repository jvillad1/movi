package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType

/**
 * Los movimientos que **componen** el gastado de un presupuesto, en el período que se está
 * mirando.
 *
 * El dueño: *«En presupuestos yo debería ver cada uno de los movimientos asociados a ese
 * presupuesto»*. Hasta acá la pantalla mostraba una barra y dos números —«$2.000.000 de
 * $2.000.000»— sin manera de contestar la pregunta obvia que sigue: **¿en qué?**. Sin eso, un
 * presupuesto excedido es una acusación sin pruebas: no se puede saber si sobra un gasto mal
 * archivado, si hay un duplicado, o si de verdad se gastó.
 *
 * ### Los mismos gastos que suma la barra, no unos parecidos
 *
 * Esta función replica **exactamente** el filtro de
 * [com.jvillada.movi.ui.dashboard.spentByCategoryForPeriod]: gasto, COP, dentro de la ventana,
 * categoría exacta y ninguna reservada. Si divergiera, la lista y el número de arriba dirían
 * cosas distintas sobre la misma plata — el modo de falla más caro que tiene esta pantalla, y
 * el que ya se pagó una vez cuando el Inicio y Créditos sumaban la deuda con dos criterios.
 *
 * La suma de lo que devuelve esta función tiene que dar el gastado de la categoría. Hay un test
 * que lo comprueba contra la función del Inicio, no contra un número escrito a mano.
 */
fun gastosDelPresupuesto(
    categoria: String,
    dias: List<EventDay>,
    ventana: LongRange,
): List<FinancialEvent> {
    val buscada = categoria.trim()
    if (buscada.isEmpty()) return emptyList()

    // Filtro CALCADO de spentByCategoryForPeriod, `countsAsCashFlow` incluido — es esa bandera
    // (no una lista de nombres reservados) la que decide si un movimiento entra en las cifras
    // del período, y la calcula el server. Copiarla mal acá haría que la lista y el número de
    // arriba hablaran de plata distinta.
    return dias.flatMap { it.items }
        .filter { it.timestamp in ventana }
        .filter { it.type == TransactionType.EXPENSE && it.countsAsCashFlow && it.currency == "COP" }
        .filter { it.category == buscada }
        // Lo más reciente primero: es el orden en que uno reconoce sus propios gastos.
        .sortedByDescending { it.timestamp }
}
