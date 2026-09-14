package com.jvillada.movi.server.balance

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A qué cuota corresponde un pago: la del vencimiento más cercano (ver [cuotaMasCercana]). */
class CuotaMasCercanaTest {
    @Test
    fun `un pago unos dias tarde es de la cuota pasada y uno unos dias antes es de la que viene`() {
        // Día de pago 30.
        assertEquals("2026-07", cuotaMasCercana(LocalDate.of(2026, 8, 2), 30), "la de julio pagada tarde")
        assertEquals("2026-08", cuotaMasCercana(LocalDate.of(2026, 8, 28), 30), "la de agosto pagada antes")
        assertEquals("2026-08", cuotaMasCercana(LocalDate.of(2026, 8, 30), 30))
        // Febrero recorta el día 30 al 28.
        assertEquals("2026-02", cuotaMasCercana(LocalDate.of(2026, 3, 1), 30))
        // Día de pago 5: el 20 queda más cerca del 5 del mes siguiente.
        assertEquals("2026-10", cuotaMasCercana(LocalDate.of(2026, 9, 21), 5))
        assertEquals("2026-09", cuotaMasCercana(LocalDate.of(2026, 9, 15), 5))
    }
}
