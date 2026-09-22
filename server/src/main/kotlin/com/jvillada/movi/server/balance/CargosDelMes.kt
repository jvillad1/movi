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
    /**
     * El día de pago del crédito, si se conoce. Con él, «el mismo mes» pasa a ser **la misma cuota**
     * ([cuotaMasCercana]) y no el mismo mes de calendario. Sin él (un crédito sin términos), el mes.
     */
    diaDePago: Int? = null,
    montoPagadoDelPar: (FinancialEvent) -> Long?,
): Long {
    val claveDe: (LocalDate) -> String =
        if (diaDePago == null) { d -> YearMonth.from(d).toString() } else { d -> cuotaMasCercana(d, diaDePago) }
    val clave = claveDe(fecha)
    return eventosDeLaDeuda
        .filter { it.noAmortiza != null && claveDe(epochMillisToAppDate(it.timestamp)) == clave }
        .sumOf { fila ->
            val guardado = fila.noAmortiza ?: 0L
            val pagado = montoPagadoDelPar(fila)
            if (pagado == null) guardado else minOf(guardado, pagado)
        }
}

/**
 * **A qué cuota corresponde un pago hecho el [fecha]**: la del vencimiento más cercano. La regla
 * vive en `:core` ([com.jvillada.movi.shared.model.cuotaMasCercana]) porque el detalle del crédito
 * agrupa con ella las partes de una cuota pagada en dos; esto solo traduce la fecha de `java.time`.
 */
fun cuotaMasCercana(fecha: LocalDate, diaDePago: Int): String =
    com.jvillada.movi.shared.model.cuotaMasCercana(fecha.year, fecha.monthValue, fecha.dayOfMonth, diaDePago)
