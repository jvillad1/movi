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
 * `0` cuando coinciden, que es el caso normal. Se dice en una línea en vez de dejar que el dueño
 * reste dos números y desconfíe de los dos: una lista que no suma lo que dice el título de
 * arriba, sin explicación, es peor que no tener lista.
 *
 * **Esta función mide la diferencia; no sabe por qué la hay.** Antes esta doc afirmaba que era
 * plata que «este dispositivo todavía no bajó (otro teléfono, un SMS, una importación)», y la
 * pantalla lo repetía al dueño. Fue falso justo cuando más importaba: con su corte de período en
 * el día 25, el resumen del server seguía sumando el mes civil (ver `DashboardRoutes`) y las dos
 * mitades de la pantalla miraban meses distintos — ninguna desincronización de por medio. Esa
 * causa ya está arreglada, y un desacuerdo real sigue siendo posible, pero de acá no se puede
 * distinguir uno del otro: la UI nombra la diferencia y no la explica.
 *
 * Negativo también es posible —la lista local con algo que el server no cuenta— y por eso el
 * resultado es un `Long` con signo y no un booleano.
 *
 * **[gastadoDeLaBarra] no es nullable, y eso importa.** La primera versión lo era y devolvía `0`
 * ante un `null`, «porque sin cifra de barra no hay nada que comparar». Es falso: el mapa que la
 * alimenta nunca es nulo, solo puede faltarle la clave, y una clave ausente significa **$0** —
 * así la leen las barras de progreso (`gastoPorCategoria[b.category] ?: 0L`) y el aviso de la
 * hoja. Con la versión vieja, una categoría que el server todavía no conoce y este teléfono sí
 * dejaba la hoja diciendo dos cosas opuestas una debajo de la otra: «No tienes gastos en
 * "Mercado" este mes» en rojo, y justo abajo «3 movimientos · \$300.000». Sin la línea que lo
 * explica, que es exactamente el caso para el que existe.
 */
fun faltanMovimientosPorVer(gastadoDeLaBarra: Long, sumaDeLaLista: Long): Long =
    gastadoDeLaBarra - sumaDeLaLista
