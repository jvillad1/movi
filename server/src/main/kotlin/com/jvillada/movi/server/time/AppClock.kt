package com.jvillada.movi.server.time

import com.jvillada.movi.shared.time.AppTimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Zona horaria civil del server, en java.time.
 *
 * El valor viene de [AppTimeZone] (:core) — Bogotá por defecto — y se puede sobreescribir con
 * la env var `APP_TIMEZONE` (un id IANA; un valor inválido cae al default). Railway corre en
 * UTC, así que NADA en el server debe usar `ZoneOffset.UTC` ni `ZoneId.systemDefault()` para
 * decidir "hoy", "este mes", el día de un evento o un vencimiento: todo pasa por acá.
 *
 * Las funciones reciben la zona como parámetro con default para poder probarlas con UTC y
 * con Bogotá lado a lado.
 */
object AppClock {
    val zone: ZoneId = ZoneId.of(AppTimeZone.resolveId(System.getenv("APP_TIMEZONE")))

    /** "Hoy" según la zona de la app. */
    fun today(zone: ZoneId = this.zone): LocalDate = LocalDate.now(zone)

    /** Ahora, con la zona de la app (para calcular ventanas de mes, etc.). */
    fun now(zone: ZoneId = this.zone): ZonedDateTime = ZonedDateTime.now(zone)
}

/** Fecha civil de un epoch-ms en la zona de la app. */
fun epochMillisToAppDate(millis: Long, zone: ZoneId = AppClock.zone): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** "YYYY-MM-DD" de un epoch-ms en la zona de la app (el formato de `EventDay.date`). */
fun epochMillisToAppDateString(millis: Long, zone: ZoneId = AppClock.zone): String =
    epochMillisToAppDate(millis, zone).format(DateTimeFormatter.ISO_LOCAL_DATE)

/** Medianoche (inicio del día civil) de una fecha en la zona de la app, en epoch-ms. */
fun appDateToEpochMillis(date: LocalDate, zone: ZoneId = AppClock.zone): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

/** Ventana [inicio, fin) en epoch-ms del mes civil que contiene a [now]. */
data class MonthWindow(val startMillis: Long, val endMillisExclusive: Long)

fun monthWindowOf(now: ZonedDateTime): MonthWindow {
    val first = now.toLocalDate().withDayOfMonth(1)
    return MonthWindow(
        startMillis = appDateToEpochMillis(first, now.zone),
        endMillisExclusive = appDateToEpochMillis(first.plusMonths(1), now.zone),
    )
}

/** Ventana del mes en curso en la zona de la app. */
fun currentMonthWindow(zone: ZoneId = AppClock.zone): MonthWindow = monthWindowOf(AppClock.now(zone))
