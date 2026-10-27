package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **La cifra diaria del héroe** (vuelta de diseño 1): junto a «para N días» va lo que queda del
 * período entre los días que faltan, hoy incluido —la misma cuenta de días que «para N días»—, y no
 * la meta diaria del período entero, que se leía como «puedes gastar esto por día».
 */
class PorDiaParaLoQueQuedaTest {

    /** Disponible $12.900.000 en un período de 30 días; gastado [gastado]; quedan [dias] días. */
    private fun disponible(gastado: Long, dias: Int, fijos: Long = 0L) = DisponibleDelPeriodo(
        ingresosRecibidos = 12_900_000L,
        ingresosPorRecibir = 0L,
        fijos = fijos,
        diasDelPeriodo = 30,
        diasQueQuedan = dias,
        diasDeLaSemana = 7,
        diasCorridosDeLaSemana = 3,
        periodo = VentanaDelDisponible(
            gastado = gastado,
            meta = (12_900_000L - fijos).coerceAtLeast(0L),
            esperadoAHoy = 0L,
        ),
        semana = VentanaDelDisponible(gastado = 0L, meta = 0L, esperadoAHoy = 0L),
        hoy = VentanaDelDisponible(gastado = 0L, meta = 0L, esperadoAHoy = 0L),
    )

    @Test
    fun `lo que queda entre los dias que faltan, no la meta del periodo entero`() {
        // La escena de producción: «Te quedan $10.869.550 · para 6 días».
        val d = disponible(gastado = 2_030_450L, dias = 6)
        assertEquals(10_869_550L, d.periodo.teQuedan)
        assertEquals(430_000L, d.metaPorDia, "la meta diaria: el disponible entre los 30 días")
        assertEquals(1_811_591L, d.porDiaParaLoQueQueda, "$10.869.550 ÷ 6, unos $1,8M por día")
    }

    @Test
    fun `el ultimo dia es todo lo que queda`() {
        assertEquals(10_869_550L, disponible(gastado = 2_030_450L, dias = 1).porDiaParaLoQueQueda)
    }

    @Test
    fun `sin nada por gastar o sin margen no hay cifra diaria`() {
        assertNull(disponible(gastado = 12_900_000L, dias = 6).porDiaParaLoQueQueda, "se gastó todo")
        assertNull(disponible(gastado = 13_000_000L, dias = 6).porDiaParaLoQueQueda, "se pasó")
        assertNull(disponible(gastado = 0L, dias = 6, fijos = 13_000_000L).porDiaParaLoQueQueda, "sin margen")
        assertNull(disponible(gastado = 0L, dias = 0).porDiaParaLoQueQueda, "el período ya cerró")
    }
}
