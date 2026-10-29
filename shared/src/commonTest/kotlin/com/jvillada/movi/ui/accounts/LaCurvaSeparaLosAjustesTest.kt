package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.cambioDelPatrimonio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La curva del patrimonio no confunde un ajuste con una pérdida (vuelta 60)
 *
 * El dueño registró con «Ajustar saldo» una deuda que ya existía y Patrimonio pasó a decir «Desde que
 * usas Movi (ago 2026), bajó $34,6M» sin que se hubiera gastado nada. El neto no cambia; la curva marca
 * los meses con ajustes y la nota dice cuánto del cambio son ajustes y cuánto queda sin ellos.
 */
class LaCurvaSeparaLosAjustesTest {

    // Agosto con $75,2M; en octubre se ajusta una deuda de $35,5M que ya existía; sin el ajuste, el neto
    // subió $900.000.
    private val puntos = listOf(
        PuntoDelPatrimonio("2026-08", "2026-08-31", 71_100_000, ajustes = 4_000_000),
        PuntoDelPatrimonio("2026-09", "2026-09-30", 75_200_000, ajustes = 4_000_000),
        PuntoDelPatrimonio("2026-10", "2026-10-29", 36_500_000, esHoy = true, ajustes = -31_500_000),
    )

    @Test
    fun `el cambio se parte en ajustes y lo demas, sin cifras nuevas`() {
        val cambio = cambioDelPatrimonio(puntos)!!
        assertEquals(-34_600_000, cambio.cambio)
        assertEquals(-35_500_000, cambio.porAjustes)
        assertEquals(900_000, cambio.sinAjustes)
        assertEquals(cambio.cambio, cambio.porAjustes + cambio.sinAjustes)
    }

    @Test
    fun `la nota dice cuanto son ajustes y que son`() {
        assertEquals(
            "Los ajustes suman −\$35,5M: deudas y saldos que ya tenías y anotaste después. Sin ellos, subió \$900.000.",
            notaDeLosAjustes(puntos),
        )
        // La leyenda de siempre no cambia: la cifra es el neto.
        assertEquals("Desde ago 2026, bajó \$34,6M", leyendaDeLaCurva(puntos))
    }

    @Test
    fun `sin ajustes en el tramo no hay nota, y un servidor viejo tampoco`() {
        val sinAjustes = puntos.map { it.copy(ajustes = 0) }
        assertNull(notaDeLosAjustes(sinAjustes))
        val ajustesViejos = puntos.map { it.copy(ajustes = 4_000_000) }
        assertNull(notaDeLosAjustes(ajustesViejos), "los ajustes de antes del primer punto ya estaban: no cambian nada")
    }

    @Test
    fun `cada mes dice sus ajustes, y el punto elegido los nombra`() {
        assertEquals(listOf(0L, 0L, -35_500_000L), ajustesDelMes(puntos))
        // El ajuste de este mes —el caso más común— se marca en el punto de hoy, con un aro por fuera.
        assertTrue(hoyTraeAjustes(ajustesDelMes(puntos)))
        assertFalse(hoyTraeAjustes(ajustesDelMes(puntos.map { it.copy(ajustes = 0) })))
        assertEquals("Hoy: \$36,5M · ajustes −\$35,5M", leyendaDelPunto(puntos.last(), -35_500_000))
        assertEquals("Al cierre de sep 2026: \$75.200.000", leyendaDelPunto(puntos[1]))
    }

    @Test
    fun `desde que usas Movi sale del primer movimiento real, como Tus periodos`() {
        // Movimientos desde agosto de 2025: la curva arranca en ago 2026, pero el dueño no empezó ahí.
        val conHistoria = HistoriaDelPatrimonio(puntos = puntos, primerMovimiento = "2025-08-14")
        assertFalse(arrancaConElDueno(conHistoria, puntos))
        assertEquals("Desde ago 2026, bajó \$34,6M", leyendaDeLaCurva(puntos, arrancaConElDueno(conHistoria, puntos)))

        val desdeAgosto = HistoriaDelPatrimonio(puntos = puntos, primerMovimiento = "2026-08-03")
        assertTrue(arrancaConElDueno(desdeAgosto, puntos))
        assertEquals("Desde que usas Movi (ago 2026), bajó \$34,6M", leyendaDeLaCurva(puntos, true))

        assertFalse(arrancaConElDueno(HistoriaDelPatrimonio(puntos = puntos), puntos), "sin el dato no se afirma")
    }
}
