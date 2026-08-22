package com.jvillada.movi.shared.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTimeZoneTest {

    // 2026-08-31T23:30 en Bogotá = 2026-09-01T04:30Z
    private val lateAugustBogota = Instant.parse("2026-09-01T04:30:00Z")

    @Test
    fun `el default es Bogota`() {
        assertEquals("America/Bogota", AppTimeZone.zone.id)
        assertEquals(AppTimeZone.zone, AppTimeZone.resolve(null))
        assertEquals(AppTimeZone.zone, AppTimeZone.resolve("  "))
        assertEquals(AppTimeZone.zone, AppTimeZone.resolve("No/Existe"))
        assertEquals("America/Mexico_City", AppTimeZone.resolve("America/Mexico_City").id)
    }

    @Test
    fun `un movimiento a las 11 30 pm del 31 cuenta en agosto, no en septiembre`() {
        val ms = lateAugustBogota.toEpochMilliseconds()
        assertEquals(LocalDate(2026, 9, 1), epochMillisToAppDate(ms, TimeZone.UTC))
        assertEquals(LocalDate(2026, 8, 31), epochMillisToAppDate(ms))
    }

    @Test
    fun `el prefijo del mes en curso sigue la zona de la app`() {
        val clock = object : Clock { override fun now() = lateAugustBogota }
        assertEquals("2026-09", currentMonthPrefix(clock, TimeZone.UTC))
        assertEquals("2026-08", currentMonthPrefix(clock))
    }
}
