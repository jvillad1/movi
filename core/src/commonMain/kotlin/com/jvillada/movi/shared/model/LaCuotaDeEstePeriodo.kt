package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/*
 * # La cuota de este período (vuelta 77)
 *
 * Un crédito que ya venía, creado con la deuda de hoy y la cuota el 15, aparecía al instante en Inicio
 * con «Pago vencido · hace 15 días · $770.000», y «Te quedan» bajaba $770.000: la regla de su cuota
 * corría desde el desembolso, así que la cuota del 15 de este período existía y nadie la había pagado
 * en Movi. Pero la deuda que el dueño escribió ya tiene esa cuota descontada.
 *
 * Por eso el alta pregunta «¿Ya pagaste la cuota de este período?» (sí por defecto) cuando la de este
 * período ya venció, y con «sí» las cuotas corren desde el arranque del período siguiente
 * ([CreditTerms.cuotasDesde]). Las funciones de acá deciden cuándo se pregunta y desde cuándo corren:
 * la hoja y el server usan las mismas.
 */

/**
 * **El vencimiento del día [dia] dentro de [periodo]** (del arranque al día antes del siguiente), o
 * `null` si ningún mes del período lo trae (un período más corto que un mes que justo lo salta).
 * Con dos (un período más largo que un mes), el primero. Pura.
 */
fun vencimientoEnElPeriodo(dia: Int, periodo: PeriodoFinanciero, ajustes: PeriodSettings): LocalDate? {
    val inicio = inicioDelPeriodo(periodo, ajustes)
    val siguiente = inicioDelPeriodo(periodoSiguiente(periodo), ajustes)
    var mes = LocalDate(inicio.year, inicio.monthNumber, 1)
    var vueltas = 0
    while (mes < siguiente && vueltas < 3) {
        val vence = vencimientoDelMes(mes.year, mes.monthNumber, dia)
        if (vence >= inicio && vence < siguiente) return vence
        mes = mes.plus(1, DateTimeUnit.MONTH)
        vueltas++
    }
    return null
}

/**
 * **¿La cuota del día [dia] de este período ya venció?** Es cuando el alta de un crédito pregunta
 * «¿Ya pagaste la cuota de este período?»: si todavía no vence, no hay nada vencido que esconder y la
 * cuota queda en Pagos como cualquier otra. Pura.
 */
fun laCuotaDeEstePeriodoYaVencio(dia: Int, hoy: LocalDate, ajustes: PeriodSettings): Boolean {
    val periodo = periodoDeLaFecha(hoy.toString(), ajustes) ?: return false
    val vence = vencimientoEnElPeriodo(dia, periodo, ajustes) ?: return false
    return vence <= hoy
}

/**
 * **Desde cuándo corren las cuotas si ya pagaste la de este período**: el arranque del período
 * siguiente a [hoy], con los ajustes del dueño. Es lo que se guarda en [CreditTerms.cuotasDesde]. Pura.
 */
fun arranqueDeLasCuotasSiYaPagaste(hoy: LocalDate, ajustes: PeriodSettings): LocalDate {
    val periodo = periodoDeLaFecha(hoy.toString(), ajustes) ?: PeriodoFinanciero(hoy.year, hoy.monthNumber)
    return inicioDelPeriodo(periodoSiguiente(periodo), ajustes)
}
