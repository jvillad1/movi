package com.jvillada.movi.ui.components.ds

import com.jvillada.movi.ui.components.PLATA_OCULTA
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Las cifras de un renglón hablan igual (vuelta de diseño 32)
 *
 * La franja de Movimientos decía «Entró +$850.000 · Salió $1,8M · Neto −$902.250»: una sola de las tres
 * iba compacta. Ahora las tres van enteras si caben, y abreviadas las tres si una no cabe.
 */
class LasCifrasDeUnRenglonHablanIgualTest {

    private fun abreviada(monto: Long, tono: TonoDeCifra = TonoDeCifra.Neutro) =
        textoDeLaCifra(monto, "COP", tono, ocultos = false, forma = FormaDeCifra.Abreviada)

    @Test
    fun `la abreviada abrevia en todas las escalas`() {
        assertEquals("+\$850 mil", abreviada(850_000, TonoDeCifra.Ingreso))
        assertEquals("\$1,8M", abreviada(1_752_250))
        assertEquals("−\$902 mil", abreviada(-902_250, TonoDeCifra.Alerta))
        assertEquals("\$500", abreviada(500))
        assertEquals("\$1M", abreviada(999_600), "lo que redondea a mil miles pasa a millones")
        assertEquals("\$15,6M", abreviada(15_600_000))
    }

    @Test
    fun `la abreviada respeta la otra moneda y los saldos ocultos`() {
        assertEquals("US\$850.000", textoDeLaCifra(850_000, "USD", TonoDeCifra.Neutro, ocultos = false, forma = FormaDeCifra.Abreviada))
        assertEquals(PLATA_OCULTA, textoDeLaCifra(850_000, "COP", TonoDeCifra.Neutro, ocultos = true, forma = FormaDeCifra.Abreviada))
    }

    @Test
    fun `si todas caben van enteras, si una no cabe van abreviadas todas`() {
        assertEquals(FormaDeCifra.Entera, formaComunDeLasCifras(listOf(90, 95, 100), anchoDeCadaUna = 100))
        assertEquals(FormaDeCifra.Abreviada, formaComunDeLasCifras(listOf(90, 101, 80), anchoDeCadaUna = 100))
    }
}
