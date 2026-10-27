package com.jvillada.movi.ui.components.ds

import com.jvillada.movi.ui.components.PLATA_OCULTA
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # La cifra compacta (vuelta de diseño 19)
 *
 * `FormaDeCifra.Compacta` es la excepción de las columnas de tres (el Disponible, Tus períodos): de un
 * millón para arriba en millones, por debajo los pesos. El signo, el «+» del ingreso, el «sin signo»
 * de un traspaso y «Ocultar los saldos» son los mismos de la entera; y en otra moneda no se compacta.
 */
class LaCifraCompactaTest {

    @Test
    fun `la entera es la de siempre, la compacta dice millones de un millon para arriba`() {
        assertEquals("\$15.600.000", textoDeLaCifra(15_600_000, "COP", TonoDeCifra.Neutro, ocultos = false))
        assertEquals("\$15,6M", textoDeLaCifra(15_600_000, "COP", TonoDeCifra.Neutro, ocultos = false, forma = FormaDeCifra.Compacta))
        assertEquals("\$999.999", textoDeLaCifra(999_999, "COP", TonoDeCifra.Neutro, ocultos = false, forma = FormaDeCifra.Compacta))
    }

    @Test
    fun `la compacta lleva el signo y el tono como la entera`() {
        assertEquals("−\$1,2M", textoDeLaCifra(-1_200_000, "COP", TonoDeCifra.Alerta, ocultos = false, forma = FormaDeCifra.Compacta))
        assertEquals("+\$1,2M", textoDeLaCifra(1_200_000, "COP", TonoDeCifra.Ingreso, ocultos = false, forma = FormaDeCifra.Compacta))
        assertEquals("\$1,2M", textoDeLaCifra(-1_200_000, "COP", TonoDeCifra.EntreCuentas, ocultos = false, forma = FormaDeCifra.Compacta))
    }

    @Test
    fun `oculta es oculta, compacta o no`() {
        assertEquals(PLATA_OCULTA, textoDeLaCifra(15_600_000, "COP", TonoDeCifra.Neutro, ocultos = true, forma = FormaDeCifra.Compacta))
    }

    @Test
    fun `en otra moneda no se compacta`() {
        assertEquals("US\$1.500.000", textoDeLaCifra(1_500_000, "USD", TonoDeCifra.Neutro, ocultos = false, forma = FormaDeCifra.Compacta))
    }
}
