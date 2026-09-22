package com.jvillada.movi.ui.dashboard

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # «Disponible»: cuánto queda para gastar en el período, esta semana y hoy
 *
 * El dueño: *«En inicio sería genial algo tipo: Disponible en el periodo · Disponible por semana ·
 * Disponible por día. Que puedes ir viendo cómo van tus movimientos respecto a ese disponible en
 * cada uno de estos universos temporales»*. De las definiciones posibles eligió **«ingresos menos
 * fijos»**:
 *
 * - **Ingresos** = lo que ya entró en el período (la misma cifra «Ingresos» del Inicio) más los
 *   ingresos del checklist que todavía no llegan.
 * - **Fijos** = todo lo que el checklist del período pide pagar, pagado o no — las mismas filas de
 *   «Falta por pagar», sin una segunda cuenta.
 * - **Disponible** = ingresos − fijos.
 *
 * Contra eso se mide el **gasto variable** (ver `gastoVariablePorDia` en `:core`): lo que cuenta en
 * «Gastos» menos los pagos del checklist, que ya están restados como fijos.
 *
 * Todo es puro: el «hoy» y los bordes del período entran por parámetro.
 */

/** Cómo va una ventana contra su disponible. Decide el color de la barra y nada más. */
enum class NivelDelGasto { BIEN, CERCA, PASADO }

/** Desde qué parte del disponible gastado la barra avisa: el 85 %. */
private const val PORCENTAJE_DE_AVISO = 85L

/**
 * Una de las tres ventanas: lo gastado y lo que se podía gastar en ella.
 *
 * @param disponible puede ser cero o negativo: con los fijos por encima de los ingresos no hay
 *   margen, y ahí la tarjeta no dibuja barras (ver [fraccion]).
 */
data class VentanaDelDisponible(val gastado: Long, val disponible: Long) {
    /** Lo que sobra; negativo = se pasó. */
    val teQuedan: Long get() = disponible - gastado

    /**
     * El largo de la barra, de 0 a 1, o `null` cuando no hay disponible contra el cual medir.
     * Nunca divide por cero ni da un largo negativo.
     */
    val fraccion: Float? get() =
        if (disponible <= 0L) null else (gastado.toDouble() / disponible).toFloat().coerceIn(0f, 1f)

    val nivel: NivelDelGasto get() = when {
        disponible <= 0L -> if (gastado > 0L) NivelDelGasto.PASADO else NivelDelGasto.BIEN
        gastado > disponible -> NivelDelGasto.PASADO
        gastado * 100 >= disponible * PORCENTAJE_DE_AVISO -> NivelDelGasto.CERCA
        else -> NivelDelGasto.BIEN
    }
}

/** Todo lo que pinta la tarjeta «Disponible». */
data class DisponibleDelPeriodo(
    val ingresosRecibidos: Long,
    val ingresosPorRecibir: Long,
    val fijos: Long,
    /** Días del período, contando el primero y el último. */
    val diasDelPeriodo: Int,
    /** Días que quedan, **contando hoy**. En el último día del período vale 1. */
    val diasQueQuedan: Int,
    /** Días de esta semana (lunes a domingo) que caen dentro del período. */
    val diasDeLaSemana: Int,
    val periodo: VentanaDelDisponible,
    val semana: VentanaDelDisponible,
    val hoy: VentanaDelDisponible,
) {
    val ingresos: Long get() = ingresosRecibidos + ingresosPorRecibir
    val disponible: Long get() = ingresos - fijos
    val hayMargen: Boolean get() = disponible > 0L

    /**
     * **«Para lo que queda: $X por día»**: lo que falta gastar del disponible, repartido entre los
     * días que quedan (hoy incluido). `null` cuando ya no queda nada que repartir.
     */
    val porDiaParaLoQueQueda: Long? get() {
        val resto = disponible - periodo.gastado
        if (resto <= 0L || diasQueQuedan <= 0) return null
        return resto / diasQueQuedan
    }
}

/**
 * **Los fijos del período**: cada pago del checklist, pagado o pendiente, por lo que de verdad
 * salió si se sabe ([PagoDelPeriodo.montoPagado]) y si no por lo esperado.
 *
 * Afuera: los ingresos (no se pagan), el SALDO de una tarjeta (es la deuda entera, no lo de este
 * mes — el mismo criterio que [faltaPorPagar]) y lo que no está en pesos, porque el resto de la
 * cuenta está en pesos. Una compra con tarjeta ya entra como gasto variable el día que se hace.
 */
fun fijosDelPeriodo(checklist: List<PagoDelPeriodo>): Long =
    checklist
        .filter { !it.esIngreso && !it.montoEsSaldo && it.moneda == "COP" }
        .sumOf { if (it.pagado) it.montoPagado ?: it.monto else it.monto }

/** Los ingresos del checklist que todavía no llegan: el sueldo que falta, un arriendo que se cobra. */
fun ingresosPorRecibirDelPeriodo(checklist: List<PagoDelPeriodo>): Long =
    checklist
        .filter { it.esIngreso && !it.pagado && !it.montoEsSaldo && it.moneda == "COP" }
        .sumOf { it.monto }

/**
 * La tarjeta entera, o `null` si no hay nada honesto que decir: sin ingresos (un usuario que recién
 * empieza, o que todavía no anotó su sueldo) «ingresos menos fijos» no significa nada, y un
 * «disponible −$1.850.000» a quien solo anotó el arriendo lo asustaría sin razón.
 *
 * @param ingresosRecibidos la cifra «Ingresos» del Inicio: lo que ya entró en el período, con la
 *   regla de siempre (sin traspasos, sin «Por confirmar», sin anulados).
 * @param gastoVariablePorDia `"YYYY-MM-DD"` → pesos, como lo manda el server.
 * @param inicio primer día del período.
 * @param finExclusivo primer día del período siguiente.
 * @param hoy la fecha de hoy en Bogotá. Fuera del período devuelve `null`: la tarjeta habla del
 *   período en curso y nada más.
 */
fun disponibleDelPeriodo(
    ingresosRecibidos: Long,
    checklist: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    inicio: LocalDate,
    finExclusivo: LocalDate,
    hoy: LocalDate,
): DisponibleDelPeriodo? {
    val diasDelPeriodo = inicio.daysUntil(finExclusivo)
    if (diasDelPeriodo <= 0 || hoy < inicio || hoy >= finExclusivo) return null

    val porRecibir = ingresosPorRecibirDelPeriodo(checklist)
    val recibidos = ingresosRecibidos.coerceAtLeast(0L)
    if (recibidos + porRecibir <= 0L) return null
    val fijos = fijosDelPeriodo(checklist)
    val disponible = recibidos + porRecibir - fijos

    val ultimoDia = finExclusivo.minus(1, DateTimeUnit.DAY)
    val gastoPorFecha: Map<LocalDate, Long> = gastoVariablePorDia.mapNotNull { (dia, monto) ->
        runCatching { LocalDate.parse(dia) }.getOrNull()?.let { it to monto }
    }.groupBy({ it.first }, { it.second }).mapValues { (_, montos) -> montos.sum() }
    fun gastadoEntre(desde: LocalDate, hasta: LocalDate): Long =
        gastoPorFecha.filterKeys { it in desde..hasta }.values.sum()

    // La semana va de lunes a domingo, recortada a los bordes del período: una semana que empezó
    // en el período anterior solo cuenta sus días de este, y su parte del disponible también.
    val lunes = hoy.minus(hoy.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber, DateTimeUnit.DAY)
    val domingo = lunes.plus(6, DateTimeUnit.DAY)
    val desdeSemana = maxOf(lunes, inicio)
    val hastaSemana = minOf(domingo, ultimoDia)
    val diasDeLaSemana = desdeSemana.daysUntil(hastaSemana) + 1

    return DisponibleDelPeriodo(
        ingresosRecibidos = recibidos,
        ingresosPorRecibir = porRecibir,
        fijos = fijos,
        diasDelPeriodo = diasDelPeriodo,
        diasQueQuedan = hoy.daysUntil(finExclusivo),
        diasDeLaSemana = diasDeLaSemana,
        periodo = VentanaDelDisponible(gastadoEntre(inicio, ultimoDia), disponible),
        semana = VentanaDelDisponible(
            gastadoEntre(desdeSemana, hastaSemana),
            disponible * diasDeLaSemana / diasDelPeriodo,
        ),
        hoy = VentanaDelDisponible(gastadoEntre(hoy, hoy), disponible / diasDelPeriodo),
    )
}
