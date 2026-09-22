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
 * ([cuentaEnGastosEIngresos], solo pesos, solo egresos) **menos los pagos del checklist del
 * período**. Un movimiento es el pago de un ítem del checklist por uno de dos vínculos que ya
 * existen, y no se inventa ningún otro:
 *
 * - **el sello de «ya ocurrió» con movimiento** (`recurring_occurrences.event_id`): cuando el
 *   dueño dice «sí, fue este» sobre un recurrente, ese movimiento queda atado a la regla;
 * - **la categoría [CUOTA_CATEGORY]**: es la que escribe el pago de una cuota (ver
 *   `pagoDeCuotaLegs`), y la misma con la que el server deduce que una cuota quedó pagada
 *   (`PagosDeDeuda.kt`). El pago de tarjeta no hace falta nombrarlo: ya no es flujo de caja.
 *
 * Lo que NO se puede ver: un recurrente sellado a mano sin decir con qué movimiento. Ese pago
 * sigue contando como variable y además como fijo. Es el lado ruidoso de equivocarse —el
 * disponible sale más chico, no más grande— y se arregla eligiendo el movimiento al marcar.
 */

/** ¿[evento] es el pago de un ítem fijo del período? Ver el KDoc del archivo por los dos vínculos. */
fun esPagoDeUnFijo(evento: FinancialEvent, idsSellados: Set<String>): Boolean =
    evento.id in idsSellados || evento.category == CUOTA_CATEGORY

/**
 * ¿[evento] suma al gasto variable? Egreso en pesos que cuenta en «Gastos» (sin traspasos, sin
 * «Por confirmar»; los anulados ya no llegan) y que no es el pago de un fijo.
 */
fun cuentaComoGastoVariable(evento: FinancialEvent, idsSellados: Set<String>): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        cuentaEnGastosEIngresos(evento) &&
        !esPagoDeUnFijo(evento, idsSellados)

/**
 * **El gasto variable de cada día**, `"YYYY-MM-DD"` → pesos.
 *
 * Viaja por día y no ya sumado por ventana porque las ventanas (el período, la semana, hoy) las
 * arma el cliente con su propio «hoy», y así el server no tiene que saber qué semana mira nadie.
 *
 * @param eventos los movimientos vivos (no anulados) de la ventana del período, con
 *   `countsAsCashFlow` ya derivado.
 * @param idsSellados los movimientos atados a un sello de «ya ocurrió».
 * @param diaDe la fecha civil de un instante, en la zona de la app. Entra por parámetro para que
 *   el server use su propia zona configurada y esto no toque ninguna tabla de zonas.
 */
fun gastoVariablePorDia(
    eventos: List<FinancialEvent>,
    idsSellados: Set<String>,
    diaDe: (Long) -> String,
): Map<String, Long> =
    eventos
        .filter { cuentaComoGastoVariable(it, idsSellados) }
        .groupBy { diaDe(it.timestamp) }
        .mapValues { (_, delDia) -> delDia.sumOf { it.amount } }
