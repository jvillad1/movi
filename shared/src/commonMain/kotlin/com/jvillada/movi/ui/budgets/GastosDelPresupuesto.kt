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
 * ### Por qué la suma puede no dar igual que la barra — y por qué se dice
 *
 * Esta función replica **exactamente** el filtro de
 * [com.jvillada.movi.ui.dashboard.spentByCategoryForPeriod], `countsAsCashFlow` incluido, y hay
 * un test que la ata a esa función en vez de a un número escrito a mano.
 *
 * Eso **no alcanza** para prometer que la suma de la lista sea el número de la barra, y la
 * primera versión de este KDoc lo prometía. La barra usa `serverSpent ?: local`: online el
 * número lo calcula el server con todo lo que sabe —todos los dispositivos, SMS, importaciones—
 * mientras esta lista sale de `getEventsByDay`. Cuando las dos cosas no coinciden, la respuesta
 * correcta no es esconder la diferencia sino **nombrarla**: ver [faltanMovimientosPorVer].
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

/**
 * Cuánto del gastado de la categoría **no** está en la lista de arriba.
 *
 * `0` cuando coinciden, que es el caso normal. Distinto de cero significa que el server contó
 * plata que este dispositivo todavía no bajó (otro teléfono, un SMS, una importación). Se dice
 * en una línea en vez de dejar que el dueño reste dos números y desconfíe de los dos: una lista
 * que no suma lo que dice el título de arriba, sin explicación, es peor que no tener lista.
 *
 * Negativo también es posible —la lista local con algo que el server no cuenta— y por eso el
 * resultado es un `Long` con signo y no un booleano.
 */
fun faltanMovimientosPorVer(gastadoDeLaBarra: Long?, sumaDeLaLista: Long): Long =
    if (gastadoDeLaBarra == null) 0L else gastadoDeLaBarra - sumaDeLaLista
