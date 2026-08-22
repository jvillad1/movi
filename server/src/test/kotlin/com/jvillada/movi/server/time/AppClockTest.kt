package com.jvillada.movi.server.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zona horaria: el server corre en UTC pero el dueño vive en Bogotá. Cada test muestra el
 * número que daba UTC (mal) y el que da la zona de la app (bien).
 */
class AppClockTest {

    private val bogota = ZoneId.of("America/Bogota")

    // 2026-08-31T23:30 en Bogotá = 2026-09-01T04:30Z
    private val lateAugust = Instant.parse("2026-09-01T04:30:00Z").toEpochMilli()

    @Test
    fun `la zona por defecto es Bogota`() {
        assertEquals(bogota, AppClock.zone)
    }

    @Test
    fun `un evento a las 11 30 pm del 31 de agosto se fecha el 31, no el 1 de septiembre`() {
        assertEquals("2026-09-01", epochMillisToAppDateString(lateAugust, ZoneOffset.UTC))
        assertEquals("2026-08-31", epochMillisToAppDateString(lateAugust, bogota))
        assertEquals("2026-08-31", epochMillisToAppDateString(lateAugust))
    }

    @Test
    fun `la ventana del mes corta a medianoche de Bogota, no de UTC`() {
        val nowUtc = Instant.ofEpochMilli(lateAugust).atZone(ZoneOffset.UTC)
        val nowBogota = Instant.ofEpochMilli(lateAugust).atZone(bogota)

        val utcWindow = monthWindowOf(nowUtc)
        val bogotaWindow = monthWindowOf(nowBogota)

        // En UTC ya es septiembre: el evento cae dentro de la ventana "de septiembre".
        assertTrue(lateAugust >= utcWindow.startMillis && lateAugust < utcWindow.endMillisExclusive)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), utcWindow.startMillis)

        // En Bogotá sigue siendo agosto: la ventana va del 1 de agosto 00:00-05 al 1 de septiembre 00:00-05.
        assertEquals(Instant.parse("2026-08-01T05:00:00Z").toEpochMilli(), bogotaWindow.startMillis)
        assertEquals(Instant.parse("2026-09-01T05:00:00Z").toEpochMilli(), bogotaWindow.endMillisExclusive)
        assertTrue(lateAugust >= bogotaWindow.startMillis && lateAugust < bogotaWindow.endMillisExclusive)
    }

    @Test
    fun `una fecha de extracto se sella a medianoche de Bogota`() {
        val date = LocalDate.of(2026, 8, 15)
        assertEquals(Instant.parse("2026-08-15T05:00:00Z").toEpochMilli(), appDateToEpochMillis(date))
        assertEquals(date, epochMillisToAppDate(appDateToEpochMillis(date)))
    }
}
