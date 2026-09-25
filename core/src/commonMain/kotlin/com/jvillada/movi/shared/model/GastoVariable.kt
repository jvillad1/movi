package com.jvillada.movi.shared.model

/**
 * # El gasto **variable**: lo que sale del bolsillo y no es un pago fijo del período
 *
 * El dueño pidió ver, en el Inicio, cuánto le queda para gastar en el período, en la semana y hoy
 * («Disponible por período · por semana · por día»), y eligió la definición «ingresos menos
 * fijos». Contra ese disponible se mide lo que se va gastando — pero no TODO lo que se gasta: el
 * arriendo o la cuota del carro ya están restados del disponible como fijos, y contarlos otra vez
 * como gasto los descontaría dos veces.
 *
 * Así que el gasto variable es **lo que cuenta como «Gastos» en el Inicio**
 * ([cuentaEnGastosEIngresos], solo pesos, solo egresos) **menos la parte de cada movimiento que
 * paga un ítem del checklist del período**:
 *
 * - **la categoría [CUOTA_CATEGORY]** sale entera: es la que escribe el pago de una cuota (ver
 *   `pagoDeCuotaLegs`), y la misma con la que el server deduce que una cuota quedó pagada
 *   (`PagosDeDeuda.kt`). El pago de tarjeta no hace falta nombrarlo: ya no es flujo de caja.
 * - **lo que paga un recurrente del checklist** sale en la parte que diga [parteFija], que arma
 *   el server (`PagosDelChecklist.kt`) con el mismo emparejador que la pantalla usa para el «¿Es
 *   este?». Es una parte y no un sí/no porque la regla tiene un monto y el movimiento otro: un
 *   colegio de $4.000.000 pagado con $3.000.000 + $1.000.000 saca los dos; un gimnasio de
 *   $180.000 pagado con un movimiento de $200.000 saca $180.000 y deja $20.000 como variable. Así
 *   fijos + variable suman siempre lo que de verdad salió, sin contar nada dos veces ni perderlo.
 *   Lo que Movi emparejó solo sale por el monto entero del movimiento: esa fila resta como fijo
 *   lo que de verdad se pagó (`montoPagado`), no el monto de la regla.
 *
 * **Fijos contra variable, la regla de oro:** los fijos del período son el monto de cada ítem del
 * checklist, pagado o pendiente (ver `fijosDelPeriodo` en la UI). Todo lo que acá se saca del
 * variable tiene que estar sumado allá; lo contrario haría ver el disponible mejor de lo que es.
 */

/**
 * ¿[evento] suma al gasto variable, en principio? Egreso en pesos que cuenta en «Gastos» (sin
 * traspasos, sin «Por confirmar»; los anulados ya no llegan) y que no es la cuota de un crédito.
 * Cuánto suma lo decide [parteVariable].
 */
fun cuentaComoGastoVariable(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        cuentaEnGastosEIngresos(evento) &&
        evento.category != CUOTA_CATEGORY

/**
 * Cuánto de [evento] es gasto variable: su monto menos la parte que paga un fijo del checklist,
 * nunca menos de cero.
 */
fun parteVariable(evento: FinancialEvent, parteFija: Map<String, Long>): Long =
    if (!cuentaComoGastoVariable(evento)) 0L
    else (evento.amount - (parteFija[evento.id] ?: 0L)).coerceAtLeast(0L)

/**
 * **El gasto variable de cada día**, `"YYYY-MM-DD"` → pesos.
 *
 * Viaja por día y no ya sumado por ventana porque las ventanas (el período, la semana, hoy) las
 * arma el cliente con su propio «hoy», y así el server no tiene que saber qué semana mira nadie.
 * Un día cuyo gasto quedó entero como fijo no aparece.
 *
 * @param eventos los movimientos vivos (no anulados) de la ventana del período, con
 *   `countsAsCashFlow` ya derivado.
 * @param parteFija id de movimiento → la parte de su monto que paga un ítem del checklist.
 * @param diaDe la fecha civil de un instante, en la zona de la app. Entra por parámetro para que
 *   el server use su propia zona configurada y esto no toque ninguna tabla de zonas.
 */
fun gastoVariablePorDia(
    eventos: List<FinancialEvent>,
    parteFija: Map<String, Long>,
    diaDe: (Long) -> String,
): Map<String, Long> =
    eventos
        .map { it to parteVariable(it, parteFija) }
        .filter { (_, monto) -> monto > 0L }
        .groupBy({ diaDe(it.first.timestamp) }, { it.second })
        .mapValues { (_, montos) -> montos.sum() }
