package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lo que dice la fila de la caja en Hoy, sin pantalla: el día más justo, o el primero en rojo. */
class CajaDeHoyTest {

    private val hoy = LocalDate(2026, 10, 13)

    private fun caja(vararg dias: Pair<String, Long>) =
        CajaProyectada(tuPlataHoy = dias.first().second, gastoDiario = 20_000L, dias = dias.map { DiaDeLaCaja(it.first, it.second) })

    @Test
    fun `el dia mas justo con lo que queda`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 900_000, "2026-10-27" to 180_000, "2026-10-28" to 400_000), hoy)
        assertEquals(CajaDeHoy("Tu día más justo del período", "El 27 de octubre quedas en", 180_000, enRojo = false), fila)
    }

    @Test
    fun `si hoy es el mas justo lo dice asi`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 100_000, "2026-10-14" to 300_000), hoy)
        assertEquals("Hoy tienes", fila.cuando)
    }

    @Test
    fun `el primer dia en rojo manda sobre el mas bajo`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 100_000, "2026-10-20" to -5_000, "2026-10-24" to -90_000), hoy)
        assertEquals(CajaDeHoy("Quedarías en rojo", "El 20 de octubre quedarías en", -5_000, enRojo = true), fila)
    }
}
