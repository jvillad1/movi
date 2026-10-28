package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import com.jvillada.movi.shared.time.AppTimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«¿Se repite todos los meses?» solo cuando ya se repitió** (vuelta 44): el mismo comercio con un
 * monto parecido en dos períodos anteriores distintos, y nunca después de repartir, de un efectivo
 * chico ni de un pago que saldó una ocurrencia.
 */
class ElRecurrenteQueSeRepiteTest {

    private val ajustes = PeriodSettings(cutoffDay = 25)

    private fun el(dia: String): Long =
        LocalDate.parse(dia).atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun gasto(id: String, dia: String, monto: Long, titulo: String = "Gimnasio Bodytech", categoria: String = "Deporte") =
        FinancialEvent(
            id = id, accountId = "c1", type = TransactionType.EXPENSE, amount = monto,
            category = categoria, description = titulo, timestamp = el(dia),
        )

    private val hoy = gasto("nuevo", "2026-10-28", 120_000)

    @Test
    fun `se propone con el mismo gasto en dos periodos anteriores`() {
        val historia = listOf(gasto("a", "2026-08-30", 118_000), gasto("b", "2026-09-28", 125_000))
        assertTrue(seRepiteParaProponer(hoy, historia, ajustes))
    }

    @Test
    fun `una sola vez antes no alcanza`() {
        assertFalse(seRepiteParaProponer(hoy, listOf(gasto("b", "2026-09-28", 120_000)), ajustes))
    }

    @Test
    fun `dos veces en el mismo periodo anterior cuentan uno`() {
        // El 26 y el 30 de septiembre caen en el período que arrancó el 25.
        val historia = listOf(gasto("a", "2026-09-26", 120_000), gasto("b", "2026-09-30", 120_000))
        assertFalse(seRepiteParaProponer(hoy, historia, ajustes))
    }

    @Test
    fun `lo del mismo periodo no cuenta como anterior`() {
        val historia = listOf(gasto("a", "2026-09-28", 120_000), gasto("b", "2026-10-26", 120_000))
        assertFalse(seRepiteParaProponer(hoy, historia, ajustes))
    }

    @Test
    fun `un monto que se aleja mas del 15 por ciento es otro gasto`() {
        val historia = listOf(gasto("a", "2026-08-30", 60_000), gasto("b", "2026-09-28", 120_000))
        assertFalse(seRepiteParaProponer(hoy, historia, ajustes))
        // Justo en el borde (±15 %), sí.
        val enElBorde = listOf(gasto("a", "2026-08-30", 102_000), gasto("b", "2026-09-28", 138_000))
        assertTrue(seRepiteParaProponer(hoy, enElBorde, ajustes))
    }

    @Test
    fun `otro comercio no cuenta`() {
        val historia = listOf(gasto("a", "2026-08-30", 120_000, titulo = "Smart Fit"), gasto("b", "2026-09-28", 120_000))
        assertFalse(seRepiteParaProponer(hoy, historia, ajustes))
    }

    private val historiaQueSeRepite = listOf(gasto("a", "2026-08-30", 120_000), gasto("b", "2026-09-28", 120_000))

    @Test
    fun `nunca despues de repartir`() {
        assertFalse(seRepiteParaProponer(hoy, historiaQueSeRepite, ajustes, ComoSeGuardo(seReparte = true)))
        assertFalse(seRepiteParaProponer(hoy.copy(espacioId = "casa"), historiaQueSeRepite, ajustes))
    }

    @Test
    fun `nunca despues de un efectivo de menos de 50 mil`() {
        val almuerzo = gasto("nuevo", "2026-10-28", 15_000, titulo = "Restaurante", categoria = "Restaurantes")
        val antes = listOf(
            gasto("a", "2026-08-30", 15_000, titulo = "Restaurante", categoria = "Restaurantes"),
            gasto("b", "2026-09-28", 15_000, titulo = "Restaurante", categoria = "Restaurantes"),
        )
        assertFalse(seRepiteParaProponer(almuerzo, antes, ajustes, ComoSeGuardo(esEfectivo = true)))
        // El mismo con tarjeta sí se propone; y un efectivo grande (el arriendo en billetes), también.
        assertTrue(seRepiteParaProponer(almuerzo, antes, ajustes))
        assertTrue(seRepiteParaProponer(hoy, historiaQueSeRepite, ajustes, ComoSeGuardo(esEfectivo = true)))
    }

    @Test
    fun `nunca despues de un pago que saldo una ocurrencia`() {
        assertFalse(seRepiteParaProponer(hoy, historiaQueSeRepite, ajustes, ComoSeGuardo(saldoUnaOcurrencia = true)))
    }

    @Test
    fun `nunca si ya hay una regla o una suscripcion`() {
        assertFalse(seRepiteParaProponer(hoy, historiaQueSeRepite, ajustes, yaHayReglaOSuscripcion = true))
    }
}
