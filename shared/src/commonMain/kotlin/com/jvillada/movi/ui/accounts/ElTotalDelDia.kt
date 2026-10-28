package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType

/**
 * # El total del día en el detalle de una cuenta (vuelta de diseño 32)
 *
 * En el detalle de la tarjeta «Nu» el encabezado del día decía «+$42.000» y la fila de abajo,
 * «Almuerzo con el equipo», «−$42.000». El total salía de `accountDayTotal` (la convención de la
 * deuda: una compra la **sube**, así que sumaba) y la fila pinta el signo del gasto (lo que entró va
 * con «+», lo que salió con «−»). Dos sentidos en el mismo día.
 *
 * Ahora el total es **la suma de lo que dicen sus filas**, con la convención de Movimientos en todas
 * las cuentas: un ingreso suma y todo lo demás resta. En una cuenta de dinero da lo mismo que antes
 * (es lo que movió su saldo); en una deuda, una compra es «−$42.000» y un abono «+$500.000», igual
 * que su fila. **Solo presentación**: el saldo de la cuenta sigue saliendo del server, con la
 * convención de la deuda (`signedDelta` en `:core`), y no se toca.
 *
 * Las filas de esta pantalla dicen el monto entero —también en un gasto repartido, que lleva «Tu
 * parte» debajo—, así que el total suma el monto entero. Solo pesos: un movimiento en otra moneda no
 * se suma con ellos sin una tasa.
 */
fun totalDelDiaComoSusFilas(items: List<FinancialEvent>): Long =
    items.filter { it.currency == "COP" }
        .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

/** El tag del total de un día en el detalle de una cuenta. */
const val TAG_TOTAL_DEL_DIA_DE_LA_CUENTA: String = "total-del-dia-de-la-cuenta"
