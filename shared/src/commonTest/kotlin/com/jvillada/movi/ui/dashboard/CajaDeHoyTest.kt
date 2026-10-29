package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que dice la fila de la caja en Hoy, sin pantalla: el día más justo, o el primero en rojo. Vuelta
 * 70: la cifra es lo libre, y el rótulo dice hasta dónde mira (el salario, o 30 días).
 */
class CajaDeHoyTest {

    private val hoy = LocalDate(2026, 10, 13)

    private fun caja(vararg dias: Pair<String, Long>) =
        CajaProyectada(tuPlataHoy = dias.first().second, gastoDiario = 20_000L, dias = dias.map { DiaDeLaCaja(it.first, it.second) })

    @Test
    fun `el dia mas justo con lo que queda`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 900_000, "2026-10-27" to 180_000, "2026-10-28" to 400_000), hoy)
        assertEquals(CajaDeHoy(ROTULO_PROXIMOS_30_DIAS, "Tu día más justo es el 27 de octubre", 180_000, enRojo = false), fila)
        val conSalario = cajaDeHoy(caja("2026-10-13" to 900_000, "2026-10-27" to 180_000).copy(hastaElSalario = true), hoy)
        assertEquals(ROTULO_HASTA_QUE_TE_PAGUEN, conSalario.rotulo)
    }

    @Test
    fun `si hoy es el mas justo lo dice asi`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 100_000, "2026-10-14" to 300_000), hoy)
        assertEquals("Tu día más justo es hoy", fila.cuando)
    }

    @Test
    fun `el primer dia en rojo manda sobre el mas bajo`() {
        val fila = cajaDeHoy(caja("2026-10-13" to 100_000, "2026-10-20" to -5_000, "2026-10-24" to -90_000), hoy)
        assertEquals(CajaDeHoy(ROTULO_NO_TE_ALCANZA, "El 20 de octubre quedarías en", -5_000, enRojo = true), fila)
    }

    @Test
    fun `la cifra es lo libre, sin el ahorro del periodo`() {
        val dias = listOf(DiaDeLaCaja("2026-10-13", 9_000_000L, libre = 1_000_000L), DiaDeLaCaja("2026-10-20", 8_500_000L, libre = 500_000L))
        val fila = cajaDeHoy(CajaProyectada(9_000_000L, null, dias, ahorro = 8_000_000L), hoy)
        assertEquals(500_000L, fila.saldo)
        assertEquals(false, fila.enRojo)
    }
}
