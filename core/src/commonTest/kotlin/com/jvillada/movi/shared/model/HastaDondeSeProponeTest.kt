package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Vuelta 45 · el emparejamiento pregunta más, sin tildar.** Pasados los 10 días de la ventana, un pago
 * se sigue proponiendo («¿Es este?») hasta el último día del período del dueño en que vence. Lo que
 * decide solo sigue con la ventana angosta (lo prueba `ElEmparejamientoPreguntaMasTest` en `:server`).
 */
class HastaDondeSeProponeTest {

    private val mesDeCalendario = PeriodSettings()
    private val corte25 = PeriodSettings(cutoffDay = 25)

    @Test
    fun `el arriendo del 5 se sigue proponiendo hasta el fin del mes`() {
        assertEquals(LocalDate(2026, 10, 31), ultimoDiaParaProponer(LocalDate(2026, 10, 5), mesDeCalendario, 10))
    }

    @Test
    fun `cerca del cierre manda la ventana, que puede cruzarlo`() {
        // Vence el 28: la ventana llega al 7 de noviembre, más allá del cierre del 31.
        assertEquals(LocalDate(2026, 11, 7), ultimoDiaParaProponer(LocalDate(2026, 10, 28), mesDeCalendario, 10))
    }

    @Test
    fun `con corte 25 el periodo del vencimiento cierra el 24`() {
        // El 1 de noviembre es del período que va del 25 de octubre al 24 de noviembre.
        assertEquals(LocalDate(2026, 11, 24), ultimoDiaParaProponer(LocalDate(2026, 11, 1), corte25, 10))
    }

    @Test
    fun `por ISO, para el server, y nada con una fecha que no se entiende`() {
        assertEquals("2026-10-31", ultimoDiaParaProponer("2026-10-05", mesDeCalendario, 10))
        assertNull(ultimoDiaParaProponer("no-es-fecha", mesDeCalendario, 10))
    }
}
