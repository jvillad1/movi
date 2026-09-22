package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs

/**
 * **A qué cuota corresponde un pago hecho el [anio]-[mes]-[dia]**: la del vencimiento más cercano,
 * antes o después (`"AAAA-MM"` del mes de ese vencimiento). Empate: la que viene.
 *
 * Existe porque agrupar por mes de calendario rompía el interés de los créditos que vencen cerca de
 * fin de mes o se pagan tarde: la cuota de julio (día 30) pagada el 2 de agosto quedaba «en agosto»,
 * y la de agosto pagada el 28 veía ese interés como ya cobrado — capital de más, deuda de menos. Un
 * pago entre dos vencimientos es ambiguo por naturaleza (atrasado o adelantado); el más cercano
 * acierta en los dos casos reales —unos días tarde o unos días antes— y solo duda a mitad de mes.
 *
 * **Vive en `:core` para que haya una sola regla.** El server la usa para saber qué interés ya
 * cubrieron los pagos anteriores de la misma cuota (`cargosYaCobradosEnElMes`), y el detalle del
 * crédito para juntar las partes de una cuota pagada en dos (`textoDelPagoQueNoFueLaCuota`). Si
 * cada lado agrupara a su manera, la pantalla explicaría un reparto distinto del que se guardó.
 *
 * Recibe la fecha en enteros y no como `LocalDate` porque el server trabaja con `java.time` y no
 * tiene kotlinx-datetime en su classpath de compilación.
 */
fun cuotaMasCercana(anio: Int, mes: Int, dia: Int, diaDePago: Int): String {
    val fecha = LocalDate(anio, mes, dia)
    val primeroDelMes = LocalDate(anio, mes, 1)
    fun vencimiento(primero: LocalDate): LocalDate {
        val largo = primero.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return LocalDate(primero.year, primero.monthNumber, diaDePago.coerceIn(1, largo))
    }
    val candidatos = listOf(
        vencimiento(primeroDelMes.minus(1, DateTimeUnit.MONTH)),
        vencimiento(primeroDelMes),
        vencimiento(primeroDelMes.plus(1, DateTimeUnit.MONTH)),
    )
    val masCercano = candidatos.minWith(
        compareBy<LocalDate> { abs(fecha.daysUntil(it)) }.thenByDescending { it },
    )
    return masCercano.year.toString().padStart(4, '0') + "-" + masCercano.monthNumber.toString().padStart(2, '0')
}
