package com.jvillada.movi.ui.components.ds

import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.transactions.TonoDelMonto
import com.jvillada.movi.ui.transactions.colorDelTono
import com.jvillada.movi.ui.transactions.montoParaLaCifra
import com.jvillada.movi.ui.transactions.tonoDeCifraDelTono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * # La cifra de un traspaso (ola R4)
 *
 * `TonoDeCifra.EntreCuentas`: plata que cambió de cuenta. Va en el color `entreCuentas` de cada tema
 * y **sin signo**, llegue como llegue el monto; y las filas de Movimientos la eligen por su tono, sin
 * colores a mano.
 */
class LaCifraEntreCuentasTest {

    @Test
    fun `entre cuentas va en su color en los dos temas, distinto del gasto, el ingreso y lo apagado`() {
        for (colores in listOf(COLORES_CLAROS)) {
            val entreCuentas = colorDeLaCifra(TonoDeCifra.EntreCuentas, colores)
            assertEquals(colores.entreCuentas, entreCuentas)
            for (otro in listOf(TonoDeCifra.Neutro, TonoDeCifra.Ingreso, TonoDeCifra.Apagado, TonoDeCifra.Alerta)) {
                assertNotEquals(colorDeLaCifra(otro, colores), entreCuentas, "$otro y entre cuentas no pueden ser el mismo color")
            }
        }
    }

    @Test
    fun `entre cuentas no lleva signo, ni positivo ni negativo`() {
        assertEquals("\$500.000", textoDeLaCifra(500_000, "COP", TonoDeCifra.EntreCuentas, ocultos = false))
        assertEquals("\$500.000", textoDeLaCifra(-500_000, "COP", TonoDeCifra.EntreCuentas, ocultos = false))
        assertEquals(PLATA_OCULTA, textoDeLaCifra(500_000, "COP", TonoDeCifra.EntreCuentas, ocultos = true))
    }

    @Test
    fun `cada tono de un movimiento tiene su tono del sistema y su signo`() {
        assertEquals(TonoDeCifra.Neutro, tonoDeCifraDelTono(TonoDelMonto.GASTO))
        assertEquals(TonoDeCifra.Ingreso, tonoDeCifraDelTono(TonoDelMonto.INGRESO))
        assertEquals(TonoDeCifra.EntreCuentas, tonoDeCifraDelTono(TonoDelMonto.ENTRE_CUENTAS))
        assertEquals(TonoDeCifra.Apagado, tonoDeCifraDelTono(TonoDelMonto.NEUTRO))

        assertEquals("−\$27.500", textoDeLaCifra(montoParaLaCifra(TonoDelMonto.GASTO, 27_500), "COP", TonoDeCifra.Neutro, false))
        assertEquals("+\$27.500", textoDeLaCifra(montoParaLaCifra(TonoDelMonto.INGRESO, 27_500), "COP", TonoDeCifra.Ingreso, false))
        // Y el color de una fila sale de la Cifra del sistema, no de una tabla aparte.
        for (tono in TonoDelMonto.entries) {
            assertEquals(colorDeLaCifra(tonoDeCifraDelTono(tono), COLORES_CLAROS), colorDelTono(tono, COLORES_CLAROS))
        }
    }
}
