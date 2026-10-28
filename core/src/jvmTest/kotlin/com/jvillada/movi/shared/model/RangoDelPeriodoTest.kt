package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # El rango de un período, como se dice (revisión de la vuelta 40)
 *
 * Dentro de un mismo mes, el mes una vez: «Del 1 al 31 de octubre». Cuando cruza de mes, los dos:
 * «Del 25 de octubre al 24 de noviembre».
 */
class RangoDelPeriodoTest {

    @Test
    fun `dentro del mismo mes, el mes una vez`() {
        assertEquals("Del 1 al 31 de octubre", rangoEntre(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)))
        assertEquals(
            "Del 1 al 31 de octubre",
            rangoDelPeriodo(PeriodoFinanciero(2026, 10), PeriodSettings(cutoffDay = 1)),
        )
    }

    @Test
    fun `cuando cruza de mes, los dos meses`() {
        assertEquals("Del 25 de octubre al 24 de noviembre", rangoEntre(LocalDate(2026, 10, 25), LocalDate(2026, 11, 24)))
        // El mismo mes de años distintos no es el mismo mes.
        assertEquals("Del 25 de diciembre al 24 de diciembre", rangoEntre(LocalDate(2025, 12, 25), LocalDate(2026, 12, 24)))
    }
}
