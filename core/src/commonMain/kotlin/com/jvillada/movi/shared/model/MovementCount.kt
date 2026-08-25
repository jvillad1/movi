package com.jvillada.movi.shared.model

/**
 * **Cuántos movimientos ve el dueño** en una lista de eventos ya filtrada de anulados.
 *
 * No es `events.size`. Un evento es una fila de la base; un *movimiento* es una cosa que pasó, y
 * las dos no son lo mismo en dos casos que el sistema ya conocía por separado:
 *
 * - **Un traspaso son dos eventos y una sola cosa.** Mover $1.000.000 de Bancolombia a Nequi
 *   escribe un EXPENSE en una cuenta y un INCOME en la otra, enlazados por `transferId` (ver
 *   [transferLegsFor]). Para el dueño pasó *una* cosa: por eso Movimientos ya las junta en un
 *   solo renglón (`collapseTransfers`). Contarlas de a dos era la misma contradicción que esta
 *   feature vino a matar en las cifras del mes, una pantalla más allá.
 *
 *   El criterio no es *idéntico* al de `collapseTransfers`, y conviene saber en qué difiere:
 *   aquella junta solo cuando encuentra **un EXPENSE y un INCOME**, y si no emite cada pata por
 *   separado; esta agrupa por `transferId` a secas. Coinciden en toda forma que el sistema pueda
 *   producir hoy —`validateTransfer` prohíbe las cuentas de deuda de los dos lados, así que las
 *   dos patas nunca son del mismo tipo—, y discreparían solo ante un par heredado del mismo lado:
 *   ahí Movimientos mostraría dos renglones y esto contaría uno. Son justo las filas que impiden
 *   crear el índice único de `Migrations.createUniqueTransferLegIndex`, o sea que las dos
 *   divergencias aparecerían en la misma cuenta y con la misma alarma en el log.
 *
 * - **La apertura de una cuenta no es un movimiento** (F54, categoría [OPENING_CATEGORY]): es la
 *   foto de lo que ya existía el día que la cuenta entró a la app. Sin esta regla la guía de
 *   primeros pasos se apagaba sola apenas creabas la primera cuenta con saldo, antes de que
 *   anotaras nada de verdad.
 *
 * **Por qué NO es `count { it.countsAsCashFlow }`**, que sería lo más corto de escribir: esa
 * bandera responde otra pregunta —"¿esto es ingreso o gasto **del mes**?"— y su respuesta es `false`
 * para cosas que el dueño sí anotó con sus propios dedos: el abono a la cuota de una libranza, el
 * pago del extracto de la tarjeta. Contar con ella dejaría en cero a un dueño que registró su
 * crédito y viene pagando cuotas hace meses, y la guía de primeros pasos le seguiría diciendo
 * "Registra un movimiento". Son dos preguntas distintas y tienen que seguir siéndolo; lo que sí
 * comparten es la regla del traspaso y la de la apertura, y por eso viven las dos acá arriba
 * escritas igual que en [isCashFlow].
 *
 * Vive en `:core` para que el server (que lo manda en `FinanceSummary.eventCount`) y el cliente
 * cuenten lo mismo.
 */
fun movementCount(events: List<FinancialEvent>): Int {
    val traspasos = mutableSetOf<String>()
    var sueltos = 0
    for (event in events) {
        if (event.category == OPENING_CATEGORY) continue
        val transferId = event.transferId
        // Una pata huérfana (transferId ya en null porque se borró la cuenta de la otra punta,
        // ver `DELETE /api/accounts/{id}`) cuenta como un movimiento suelto, que es exactamente
        // lo que es a esa altura: plata que salió o entró y no tiene con qué compensarse.
        if (transferId == null) sueltos++ else traspasos += transferId
    }
    return sueltos + traspasos.size
}
