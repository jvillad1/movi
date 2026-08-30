package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * El **período financiero** del dueño: la ventana sobre la que Movi cuenta ingresos, gastos y
 * presupuestos.
 *
 * ### De dónde sale
 *
 * El dueño pidió que la app tenga «una fecha de inicio y corte periódicas» en vez del mes de
 * calendario, y la evidencia estaba en sus propios datos: su salario está registrado el **26 de
 * agosto** y se llama **«Salario Septiembre 2026»**. Para él septiembre ya empezó mientras la app
 * le sumaba «gastos de agosto». Con meses de calendario esa cuenta no cuadra nunca — el mes que
 * él vive arranca el día que le pagan.
 *
 * ### Cómo se nombra
 *
 * Un período **se llama por el mes en el que termina**, que es el mes que el dueño está viviendo.
 * Con corte 26, la ventana del 26 de agosto al 25 de septiembre es **«septiembre»** — igual que su
 * «Salario Septiembre» cobrado en agosto.
 *
 * Con corte 1 (el valor por defecto) la ventana va del 1 al último día del mes y se llama por ese
 * mismo mes: **es exactamente el mes de calendario**. Eso no es casualidad, es la garantía de
 * compatibilidad: mientras nadie cambie el corte, todas las cifras de la app siguen dando lo
 * mismo que antes.
 */
@Serializable
data class PeriodSettings(
    /**
     * Día del mes en que arranca cada período, de 1 a 31.
     *
     * Se guarda **por usuario en el server** para que el teléfono y la web digan el mismo mes: si
     * viviera solo en el dispositivo, el dueño vería un corte en su celular y otro en el
     * navegador, que es la clase de contradicción que Movi viene eliminando.
     *
     * Por defecto **1** — o sea, mes de calendario, el comportamiento de siempre. Un usuario que
     * no lo toque no ve ningún cambio.
     */
    val cutoffDay: Int = 1,
) {
    init {
        require(cutoffDay in 1..31) { "El día de corte va de 1 a 31" }
    }

    /** `true` cuando el corte es el default y todo se comporta como el mes de calendario. */
    val esMesDeCalendario: Boolean get() = cutoffDay == 1
}

/**
 * Un período concreto, identificado por el mes que le da nombre (el mes en que **termina**).
 *
 * `year`/`month` son los del nombre, no los del arranque: con corte 26, la ventana que empieza el
 * 26 de agosto de 2026 es `PeriodoFinanciero(2026, 9)`.
 */
@Serializable
data class PeriodoFinanciero(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "Mes fuera de rango" }
    }

    /** «2026-09» — la misma forma que ya usaban las funciones de mes, para poder convivir. */
    val prefijo: String get() = "$year-" + month.toString().padStart(2, '0')
}

/** Cuántos días tiene un mes, contando bisiestos. */
private fun diasDelMes(year: Int, month: Int): Int {
    val siguiente = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return siguiente.toEpochDays() - LocalDate(year, month, 1).toEpochDays()
}

/**
 * El día en que arranca el período `(year, month)`, con el corte **recortado al último día del mes**
 * cuando no existe.
 *
 * Un corte 31 en febrero no puede ser el 31: se usa el 28 (o el 29). Sin este recorte, el dueño
 * que elige 31 se queda sin período en cuatro meses del año.
 */
private fun inicioDe(year: Int, month: Int, cutoffDay: Int): LocalDate =
    LocalDate(year, month, minOf(cutoffDay, diasDelMes(year, month)))

/**
 * La ventana `[inicio, fin)` del período, en instantes epoch-ms.
 *
 * El fin es **exclusivo** a propósito: el instante que abre el período siguiente no pertenece a
 * este. Con un fin inclusivo, un movimiento del último milisegundo del día de corte caería en los
 * dos períodos y se contaría dos veces.
 */
fun ventanaDe(periodo: PeriodoFinanciero, settings: PeriodSettings): LongRange {
    val zona = AppTimeZone.zone
    val (year, month) = periodo.year to periodo.month
    // Con corte 1 el período se llama por su propio mes; con cualquier otro corte arranca en el
    // mes ANTERIOR al que le da nombre (26 de agosto → «septiembre»).
    val (yInicio, mInicio) = if (settings.esMesDeCalendario) {
        year to month
    } else {
        if (month == 1) (year - 1) to 12 else year to (month - 1)
    }
    val (yFin, mFin) = if (settings.esMesDeCalendario) {
        if (month == 12) (year + 1) to 1 else year to (month + 1)
    } else {
        year to month
    }
    val inicio = inicioDe(yInicio, mInicio, settings.cutoffDay).atStartOfDayIn(zona)
    val fin = inicioDe(yFin, mFin, settings.cutoffDay).atStartOfDayIn(zona)
    return inicio.toEpochMilliseconds() until fin.toEpochMilliseconds()
}

/**
 * En qué período cae un instante.
 *
 * Es la operación que usa todo lo que hoy pregunta «¿esto es de este mes?».
 */
fun periodoDe(epochMillis: Long, settings: PeriodSettings): PeriodoFinanciero {
    val fecha = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(AppTimeZone.zone).date
    if (settings.esMesDeCalendario) return PeriodoFinanciero(fecha.year, fecha.month.number)

    // Antes del corte todavía se está en el período que arrancó el mes pasado, y ese período se
    // llama por el mes en curso. A partir del corte empieza el que se llama por el mes siguiente.
    val corteDeEsteMes = inicioDe(fecha.year, fecha.month.number, settings.cutoffDay)
    return if (fecha < corteDeEsteMes) {
        PeriodoFinanciero(fecha.year, fecha.month.number)
    } else {
        if (fecha.month.number == 12) PeriodoFinanciero(fecha.year + 1, 1)
        else PeriodoFinanciero(fecha.year, fecha.month.number + 1)
    }
}

/** El período en curso. */
fun periodoActual(ahoraMillis: Long, settings: PeriodSettings): PeriodoFinanciero =
    periodoDe(ahoraMillis, settings)

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** «septiembre de 2026» — para encabezados. */
fun nombreDe(periodo: PeriodoFinanciero): String =
    "${MESES[periodo.month - 1]} de ${periodo.year}"

/**
 * «Del 26 de agosto al 25 de septiembre» — la explicación que hace entendible un corte que no es
 * el 1. Con corte 1 devuelve `null`: no hay nada que aclarar sobre un mes de calendario.
 */
fun rangoLegibleDe(periodo: PeriodoFinanciero, settings: PeriodSettings): String? {
    if (settings.esMesDeCalendario) return null
    val zona = AppTimeZone.zone
    val v = ventanaDe(periodo, settings)
    val inicio = Instant.fromEpochMilliseconds(v.first).toLocalDateTime(zona).date
    // El último día incluido es el anterior al arranque del siguiente.
    val ultimo = Instant.fromEpochMilliseconds(v.last).toLocalDateTime(zona).date
    return "Del ${inicio.dayOfMonth} de ${MESES[inicio.month.number - 1]} " +
        "al ${ultimo.dayOfMonth} de ${MESES[ultimo.month.number - 1]}"
}
