package com.jvillada.movi.ui.components

import com.jvillada.movi.data.SaldosOcultosStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Con los saldos ocultos, toda cifra de plata se pinta «$•••••»; al mostrar, todo vuelve
 * exactamente como estaba.** Las variantes `…SinOcultar` no cambian nunca: son las de lo que no
 * se pinta.
 */
class PlataOcultaTest {

    @AfterTest
    fun limpiar() = SaldosOcultosStore.clear()

    @Test
    fun `a la vista los formatos son los de siempre`() {
        assertEquals("\$1.234.567", formatCOP(1_234_567))
        assertEquals("−\$1.500", formatCOP(-1_500))
        assertEquals("US\$181", formatMoney(181, "USD"))
        assertEquals("\$1,7M", formatMoneyCompact(1_700_000))
        assertEquals("−US\$181", signedMoney(-181, "USD"))
    }

    @Test
    fun `ocultos todo es la misma plata oculta, sin signo`() {
        SaldosOcultosStore.poner(true)
        assertEquals(PLATA_OCULTA, formatCOP(1_234_567))
        assertEquals(PLATA_OCULTA, formatCOP(-1_500))
        assertEquals(PLATA_OCULTA, formatMoney(181, "USD"))
        assertEquals(PLATA_OCULTA, formatMoney(50, "EUR"))
        assertEquals(PLATA_OCULTA, formatMoneyCompact(1_700_000))
        assertEquals(PLATA_OCULTA, formatMoneyCompact(250_000))
        assertEquals(PLATA_OCULTA, signedMoney(-181, "USD"))
        assertEquals("\$•••••", PLATA_OCULTA)
    }

    @Test
    fun `al mostrar vuelve exactamente como estaba`() {
        SaldosOcultosStore.poner(true)
        SaldosOcultosStore.poner(false)
        assertEquals("\$1.234.567", formatCOP(1_234_567))
        assertEquals("\$1,7M", formatMoneyCompact(1_700_000))
    }

    @Test
    fun `las variantes sin ocultar no cambian nunca`() {
        SaldosOcultosStore.poner(true)
        assertEquals("\$1.234.567", formatCOPSinOcultar(1_234_567))
        assertEquals("US\$181", formatMoneySinOcultar(181, "USD"))
        assertEquals("\$1,7M", formatMoneyCompactSinOcultar(1_700_000))
        assertEquals("−\$1.500", signedMoneySinOcultar(-1_500, "COP"))
    }

    @Test
    fun `en un texto armado se tapan las cifras y nada mas`() {
        assertEquals(
            "Cerró tu período: entró $PLATA_OCULTA, salió $PLATA_OCULTA",
            ocultarCifras("Cerró tu período: entró \$22.500.000, salió \$18.200.000"),
        )
        assertEquals("Te pasaste por $PLATA_OCULTA", ocultarCifras("Te pasaste por −\$100.000"))
        assertEquals("Pagaste $PLATA_OCULTA y $PLATA_OCULTA", ocultarCifras("Pagaste US\$19,99 y \$1,7M"))
        assertEquals("Cuota $PLATA_OCULTA · seguro $PLATA_OCULTA", ocultarCifras("Cuota USD 20 · seguro 1.234 USD"))
        // Lo que no es plata se queda: porcentajes, fechas, conteos.
        val sinPlata = "3 cobros el 28 de septiembre, 12 % más que el 2026-08-25"
        assertEquals(sinPlata, ocultarCifras(sinPlata))
    }
}
