package com.jvillada.movi.server.balance

import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.FinancialEvent
import java.time.LocalDate
import java.time.YearMonth

/**
 * **Cuánto interés, seguro y cargos ya CUBRIERON** los pagos de una deuda en el mes de [fecha].
 *
 * [eventosDeLaDeuda] son los movimientos vivos de la deuda, ya sin las patas del pago que se está
 * registrando. Es lo que `desglosarCuota` descuenta para no cobrar el interés del mes a cada pago
 * parcial. El mes es el de calendario (zona de la app): el banco liquida el interés una vez por cuota.
 *
 * **Cubiertos, no guardados.** Cada fila guarda en `noAmortiza` el cargo ENTERO del mes aunque el pago
 * no alcanzara a cubrirlo —a propósito: si después se corrige el monto, el capital se recalcula con
 * ese número—. Un abono de $3.000.000 contra $3.646.011 de interés cubrió $3.000.000, no $3.646.011, y
 * sumar lo guardado haría que el segundo abono bajara capital de más. Por eso cada fila aporta lo
 * menor entre su `noAmortiza` y lo que salió de la cuenta en ese pago ([montoPagadoDelPar]: la otra
 * pata del traspaso; `null` si no hay par, como la cuota que paga la nómina, y ahí vale lo guardado).
 */
fun cargosYaCobradosEnElMes(
    eventosDeLaDeuda: List<FinancialEvent>,
    fecha: LocalDate,
    montoPagadoDelPar: (FinancialEvent) -> Long?,
): Long {
    val mes = YearMonth.from(fecha)
    return eventosDeLaDeuda
        .filter { it.noAmortiza != null && YearMonth.from(epochMillisToAppDate(it.timestamp)) == mes }
        .sumOf { fila ->
            val guardado = fila.noAmortiza ?: 0L
            val pagado = montoPagadoDelPar(fila)
            if (pagado == null) guardado else minOf(guardado, pagado)
        }
}
