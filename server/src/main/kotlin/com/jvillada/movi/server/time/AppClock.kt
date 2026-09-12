package com.jvillada.movi.server.time

import com.jvillada.movi.shared.time.AppTimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer

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

/**
 * La ventana del **período financiero** del usuario, que reemplaza al mes de calendario en todo
 * lo que dice «del mes».
 *
 * Con `cutoffDay = 1` devuelve exactamente lo mismo que [currentMonthWindow] — es la garantía que
 * hace seguro adoptarlo: quien no cambió el ajuste no ve ninguna cifra distinta.
 *
 * El cálculo vive en `:core` ([ventanaDe]) para que el server y los tres clientes usen **la misma
 * función**. Que Inicio diga un mes y Presupuestos otro es la contradicción que esto viene a
 * eliminar, y tener dos implementaciones es cómo se llega ahí.
 */
fun currentPeriodWindow(settings: PeriodSettings, zone: ZoneId = AppClock.zone): MonthWindow {
    val ahora = AppClock.now(zone).toInstant().toEpochMilli()
    val ventana = ventanaDe(periodoDe(ahora, settings), settings)
    return MonthWindow(
        startMillis = ventana.first,
        // `ventanaDe` devuelve un rango con el último milisegundo INCLUIDO; `MonthWindow` usa fin
        // exclusivo. El +1 es la traducción entre las dos convenciones, y omitirlo dejaría el
        // primer milisegundo del período siguiente fuera de los dos.
        endMillisExclusive = ventana.last + 1,
    )
}

/** El día de corte del usuario, o 1 (mes de calendario) si nunca lo eligió. */
suspend fun cutoffDayOf(uid: String): Int = dbQuery {
    Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.periodCutoffDay) ?: 1
}

/**
 * **El período del usuario completo**: su día de corte y los meses que arrancaron otro día.
 *
 * Existe porque [cutoffDayOf] sola dejó de alcanzar. Cuando un período puede empezar antes o
 * después del corte (ver `PeriodSettings.iniciosPropios`), leer solo el día deja a cada pantalla
 * calculando una ventana distinta: Movimientos respetando la excepción y el Inicio ignorándola,
 * sobre la misma plata y el mismo mes. Esa es exactamente la contradicción que el período vino a
 * eliminar, reaparecida un nivel más abajo.
 *
 * **Una sola consulta**, y la fila trae las dos columnas: pedir el corte por un lado y las
 * excepciones por otro es cómo se termina mezclando el corte de hoy con las excepciones de hace
 * un segundo.
 *
 * Un JSON que no se entienda se lee como «ninguna excepción». Ver `leerIniciosPropios` en
 * `UserRoutes`: el mismo criterio, y por el mismo motivo — un dato roto no puede dejar sin
 * contestar una pantalla entera.
 */
suspend fun ajustesDePeriodoDe(uid: String): PeriodSettings = dbQuery {
    val fila = Users.selectAll().where { Users.id eq uid }.firstOrNull()
    PeriodSettings(
        cutoffDay = (fila?.get(Users.periodCutoffDay) ?: 1).coerceIn(1, 31),
        iniciosPropios = fila?.get(Users.periodStarts)?.let { json ->
            runCatching {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
            }.getOrElse { emptyMap() }
        } ?: emptyMap(),
    )
}
