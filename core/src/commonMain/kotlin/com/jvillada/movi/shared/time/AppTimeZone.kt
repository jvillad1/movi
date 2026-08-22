package com.jvillada.movi.shared.time

import kotlinx.datetime.Clock
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
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

    /**
     * Bogotá como offset fijo: UTC-5 todo el año (Colombia no tiene horario de verano), así que
     * es exacto. Es el plan B cuando la plataforma no tiene la base de datos de zonas IANA —
     * en wasmJs kotlinx-datetime delega a js-joda y el bundle solo trae `@js-joda/core`
     * (sin `@js-joda/timezone`, que pesa ~1 MB), donde `TimeZone.of("America/Bogota")` lanza.
     */
    val fixedBogota: TimeZone = FixedOffsetTimeZone(UtcOffset(hours = -5))

    /**
     * Zona por defecto (Bogotá). La inicialización NUNCA lanza: si la plataforma no conoce el
     * id IANA cae a [fixedBogota]. Un `object` cuyo init falla deja Inicio y Presupuestos
     * muertos en la PWA, así que esto no puede depender de la tabla de zonas.
     */
    val zone: TimeZone = zoneOrFixed(DEFAULT_ID)

    /**
     * `TimeZone.of(id)` o, si la plataforma no lo resuelve, [fixedBogota]. Nunca lanza.
     */
    fun zoneOrFixed(id: String): TimeZone =
        runCatching { TimeZone.of(id) }.getOrElse { fixedBogota }

    /**
     * Resuelve un id de zona (p.ej. el valor de `APP_TIMEZONE`). Un id vacío, nulo o inválido
     * cae al default en vez de tumbar el arranque.
     */
    fun resolve(id: String?): TimeZone {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return zone
        return runCatching { TimeZone.of(trimmed) }.getOrElse { zone }
    }

    /** Igual que [resolve] pero devuelve el id IANA — para quien no tiene kotlinx-datetime (el server usa java.time). */
    fun resolveId(id: String?): String = resolve(id).id
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
