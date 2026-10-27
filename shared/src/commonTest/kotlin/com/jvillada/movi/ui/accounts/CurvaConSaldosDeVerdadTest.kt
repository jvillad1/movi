package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.MESES_DE_HISTORIA
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # La curva del patrimonio no celebra el arranque (vuelta de diseño 23)
 *
 * En la cuenta demo decía «Desde oct 2025, subió $10,9M»: diez meses planos (movimientos viejos, sin
 * las cuentas cargadas) y un salto en agosto, el día que se cargaron los saldos. Ese salto no es una
 * ganancia. La curva y la frase arrancan en el primer punto con saldos de verdad.
 */
class CurvaConSaldosDeVerdadTest {

    /** Doce cierres desde nov 2025 y hoy, como los manda el server con la ventana entera. */
    private fun ventana(netos: List<Long>): List<PuntoDelPatrimonio> {
        require(netos.size == MESES_DE_HISTORIA + 1)
        val meses = (0 until MESES_DE_HISTORIA).map { i ->
            val total = 2025 * 12 + 10 + i // nov 2025 = índice 10 de 2025
            "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
        }
        return meses.mapIndexed { i, mes -> PuntoDelPatrimonio(mes, "$mes-28", netos[i]) } +
            PuntoDelPatrimonio("2026-10", "2026-10-27", netos.last(), esHoy = true)
    }

    @Test
    fun `un saldo cargado de golpe no cuenta como subida`() {
        // Plano en $1,4M de nov a jul (movimientos viejos), y en agosto se cargan las cuentas: +$10,9M.
        val netos = List(9) { 1_400_000L } + listOf(12_300_000L, 12_100_000L, 12_400_000L, 12_500_000L)
        val historia = HistoriaDelPatrimonio(puntos = ventana(netos))
        val puntos = puntosConSaldos(historia.puntos)

        assertEquals("2026-08", puntos.first().mes, "arranca el mes en que se cargaron los saldos")
        val leyenda = leyendaDeLaCurva(puntos, desdeQueUsasMovi = arrancaConElDueno(historia, puntos))
        assertEquals("Desde que usas Movi (ago 2026), subió \$200.000", leyenda)
        assertFalse(leyenda.contains("10,9"), "el salto de la carga no es una ganancia")
    }

    @Test
    fun `los ceros del principio tampoco cuentan`() {
        val puntos = puntosConSaldos(
            listOf(
                PuntoDelPatrimonio("2026-08", "2026-08-31", 0),
                PuntoDelPatrimonio("2026-09", "2026-09-30", 8_000_000),
                PuntoDelPatrimonio("2026-10", "2026-10-27", 8_500_000, esHoy = true),
            ),
        )
        assertEquals(listOf("2026-09", "2026-10"), puntos.map { it.mes })
    }

    @Test
    fun `un saldo que ya se movia desde el primer mes se queda entero`() {
        val netos = (0..MESES_DE_HISTORIA).map { 5_000_000L + it * 100_000L }
        val historia = HistoriaDelPatrimonio(puntos = ventana(netos))
        val puntos = puntosConSaldos(historia.puntos)
        assertEquals(historia.puntos, puntos)
        assertFalse(arrancaConElDueno(historia, puntos), "con la ventana entera no se sabe cuándo empezó")
        assertTrue(leyendaDeLaCurva(puntos, arrancaConElDueno(historia, puntos)).startsWith("Desde nov 2025, subió"))
    }

    @Test
    fun `todo plano sigue igual y si solo queda hoy no hay curva`() {
        val plano = listOf(
            PuntoDelPatrimonio("2026-09", "2026-09-30", 3_000_000),
            PuntoDelPatrimonio("2026-10", "2026-10-27", 3_000_000, esHoy = true),
        )
        assertEquals(plano, puntosConSaldos(plano))
        assertEquals("Desde sep 2026, sigue igual", leyendaDeLaCurva(plano))
        val soloElSalto = HistoriaDelPatrimonio(
            puntos = listOf(
                PuntoDelPatrimonio("2026-08", "2026-08-31", 1_000_000),
                PuntoDelPatrimonio("2026-09", "2026-09-30", 1_000_000),
                PuntoDelPatrimonio("2026-10", "2026-10-27", 9_000_000, esHoy = true),
            ),
        )
        assertFalse(hayCurva(soloElSalto), "un salto solo, sin nada después, no es una historia")
    }
}
