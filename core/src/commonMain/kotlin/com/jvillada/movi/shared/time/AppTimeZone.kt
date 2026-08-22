package com.jvillada.movi.shared.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Única fuente de verdad de la zona horaria "civil" de Movi.
 *
 * El server corre en UTC (Railway) pero el dueño vive en Bogotá (UTC-5, sin horario de verano).
 * Todo cálculo de "hoy", "este mes", corte de mes, día de un evento o vencimiento tiene que
 * hacerse en ESTA zona y no en la del sistema: si no, un movimiento a las 9 pm del 31 cae en el
 * mes siguiente y un recordatorio "del día" sale a las 7 pm del día anterior.
 *
 * El almacenamiento no cambia (timestamps epoch-ms); solo cambia la conversión a fecha civil.
 * En el server la zona se puede sobreescribir con la env var `APP_TIMEZONE` (ver
 * `com.jvillada.movi.server.time.AppClock`); el cliente usa el default, que es la misma zona
 * con la que el server fecha `EventDay.date`, así Inicio y Presupuestos ven el mismo mes.
 */
object AppTimeZone {
    const val DEFAULT_ID: String = "America/Bogota"

    /** Zona por defecto (Bogotá). */
    val zone: TimeZone = TimeZone.of(DEFAULT_ID)

    /**
     * Resuelve un id de zona (p.ej. el valor de `APP_TIMEZONE`). Un id vacío, nulo o inválido
     * cae al default en vez de tumbar el arranque.
     */
    fun resolve(id: String?): TimeZone {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return zone
        return runCatching { TimeZone.of(trimmed) }.getOrElse { zone }
    }
}

/** Fecha civil de un epoch-ms en la zona de la app. */
fun epochMillisToAppDate(millis: Long, zone: TimeZone = AppTimeZone.zone): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

/** "YYYY-MM" de una fecha: la unidad con la que se agrupa "este mes". */
fun monthPrefixOf(date: LocalDate): String =
    "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"

/** "YYYY-MM" de hoy en la zona de la app. */
fun currentMonthPrefix(clock: Clock = Clock.System, zone: TimeZone = AppTimeZone.zone): String =
    monthPrefixOf(clock.now().toLocalDateTime(zone).date)
