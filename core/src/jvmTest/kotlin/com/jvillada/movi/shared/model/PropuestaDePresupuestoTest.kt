package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Las propuestas de presupuesto salen del gasto del período anterior: las cuatro categorías de más
 * gasto, con un tope redondeado hacia arriba. Acá la regla pura; la lectura de la base (anulados,
 * traspasos, «Por confirmar», el período del dueño) la prueba `PropuestasDePresupuestoRoutesTest`.
 */
class PropuestaDePresupuestoTest {

    @Test
    fun `el tope redondea hacia arriba a 10 mil bajo el millon y a 50 mil desde el millon`() {
        assertEquals(100_000L, topeSugeridoPara(99_001L))
        assertEquals(100_000L, topeSugeridoPara(100_000L))
        assertEquals(10_000L, topeSugeridoPara(1L))
        assertEquals(1_000_000L, topeSugeridoPara(995_000L))
        assertEquals(1_000_000L, topeSugeridoPara(1_000_000L))
        assertEquals(1_050_000L, topeSugeridoPara(1_020_000L))
        assertEquals(1_050_000L, topeSugeridoPara(1_000_001L))
    }

    @Test
    fun `toma las cuatro de mas gasto, en orden, y deja fuera lo que ya tiene presupuesto`() {
        val gasto = mapOf(
            "Comida" to 820_000L,
            "Mercado" to 1_020_000L,
            "Fútbol" to 99_001L,
            "Hija" to 400_000L,
            "Ropa" to 50_000L,
            "Gasolina" to 300_000L,
        )

        val propuestas = propuestasDePresupuesto(gasto, conPresupuesto = setOf("gasolina"), desde = "2026-07-25", hasta = "2026-08-24")

        assertEquals(listOf("Mercado", "Comida", "Hija", "Fútbol"), propuestas.map { it.category })
        assertEquals(listOf(1_050_000.0, 820_000.0, 400_000.0, 100_000.0), propuestas.map { it.amount })
        assertEquals(1_020_000.0, propuestas.first().gastado)
        assertEquals("2026-07-25", propuestas.first().desde)
        assertEquals("2026-08-24", propuestas.first().hasta)
    }

    @Test
    fun `sin gasto, o con categorias reservadas, en blanco o de cuota, no propone nada`() {
        val gasto = mapOf(
            CUOTA_CATEGORY to 900_000L,
            TRANSFER_CATEGORY to 5_000_000L,
            ADJUSTMENT_CATEGORY to 70_000L,
            OPENING_CATEGORY to 1_000_000L,
            "  " to 20_000L,
            "Nada" to 0L,
        )

        assertEquals(emptyList(), propuestasDePresupuesto(gasto, emptySet(), "2026-07-25", "2026-08-24"))
        assertEquals(emptyList(), propuestasDePresupuesto(emptyMap(), emptySet(), "2026-07-25", "2026-08-24"))
    }

    @Test
    fun `las fechas de la propuesta se leen como el rango del periodo`() {
        assertEquals("25 de julio", diaLegible("2026-07-25"))
        assertEquals("24 de agosto", diaLegible("2026-08-24"))
        assertEquals(null, diaLegible("no es fecha"))
    }
}
