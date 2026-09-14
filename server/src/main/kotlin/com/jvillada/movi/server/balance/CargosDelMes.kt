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
 * **A qué cuota corresponde un pago hecho el [fecha]**: la del vencimiento más cercano, antes o
 * después (`"AAAA-MM"` del mes de ese vencimiento). Empate: la que viene.
 *
 * Existe porque agrupar por mes de calendario rompía el interés de los créditos que vencen cerca de
 * fin de mes o se pagan tarde: la cuota de julio (día 30) pagada el 2 de agosto quedaba «en agosto»,
 * y la de agosto pagada el 28 veía ese interés como ya cobrado — capital de más, deuda de menos. Un
 * pago entre dos vencimientos es ambiguo por naturaleza (atrasado o adelantado); el más cercano
 * acierta en los dos casos reales —unos días tarde o unos días antes— y solo duda a mitad de mes.
 */
fun cuotaMasCercana(fecha: LocalDate, diaDePago: Int): String {
    val mes = YearMonth.from(fecha)
    fun venc(m: YearMonth) = m.atDay(diaDePago.coerceIn(1, m.lengthOfMonth()))
    val candidatos = listOf(venc(mes.minusMonths(1)), venc(mes), venc(mes.plusMonths(1)))
    val masCercano = candidatos.minWith(
        compareBy<LocalDate> { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(fecha, it)) }
            .thenByDescending { it },
    )
    return YearMonth.from(masCercano).toString()
}
