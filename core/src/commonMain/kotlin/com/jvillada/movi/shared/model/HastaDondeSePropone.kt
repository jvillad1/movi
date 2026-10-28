package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # Hasta dónde se propone el pago de una factura (vuelta 45)
 *
 * El emparejamiento automático («el checklist lo tilda el movimiento») mira los 10 días alrededor del
 * vencimiento (`OCCURRENCE_WINDOW_DAYS` en `:server`). Ahí es donde decide SOLO —un único candidato
 * concluyente se tilda—, y eso no cambia.
 *
 * Lo que cambia es hasta dónde **pregunta**. El arriendo del 5 pagado el 28 —23 días tarde— caía fuera
 * de la ventana y la fila decía «Movi no encontró el movimiento de este pago» con el movimiento a la
 * vista. Ahora, pasada la ventana, se siguen buscando candidatos **hasta el último día del período del
 * dueño** en que vence, y la fila los ofrece con «¿Es este?». Nunca se tilda nada fuera de la ventana:
 * no es una regla de plata nueva, es una pregunta más.
 *
 * El borde de atrás sigue siendo el de siempre (el arranque del período del vencimiento): un pago del
 * período anterior no puede ser el de este.
 */

/**
 * **El último día en que un movimiento todavía se PROPONE como el pago de lo que vence [vence]**: el
 * mayor entre el fin de la ventana (`vence` + [ventanaDias]) y el último día del período del dueño que
 * contiene [vence] (con su corte, sus festivos y sus inicios propios: [inicioDelPeriodo]). Puro.
 */
fun ultimoDiaParaProponer(vence: LocalDate, settings: PeriodSettings, ventanaDias: Int): LocalDate {
    val finDeLaVentana = vence.plus(ventanaDias, DateTimeUnit.DAY)
    val periodo = periodoDeLaFecha(vence.toString(), settings) ?: return finDeLaVentana
    val finDelPeriodo = inicioDelPeriodo(periodoSiguiente(periodo), settings).minus(1, DateTimeUnit.DAY)
    return maxOf(finDeLaVentana, finDelPeriodo)
}

/**
 * [ultimoDiaParaProponer] por ISO («2026-10-05» → «2026-10-31»), para el server, que no ve los tipos de
 * `kotlinx-datetime` (como `fechaEsperadaDelIngreso`). `null` si [venceIso] no es una fecha.
 */
fun ultimoDiaParaProponer(venceIso: String, settings: PeriodSettings, ventanaDias: Int): String? {
    val vence = runCatching { LocalDate.parse(venceIso) }.getOrNull() ?: return null
    return ultimoDiaParaProponer(vence, settings, ventanaDias).toString()
}
