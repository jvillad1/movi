package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.ui.components.ds.TonoDeCifra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # El widget «En qué se va» dice cuánto más o menos (vuelta de diseño 7)
 *
 * [diferenciaDeLaCategoria] sale de [contraElAnterior] (la misma cuenta de siempre) y la dice en
 * palabras, sin «+» (que en Movi es plata que entra): en `sale` solo si sube mucho —con los umbrales
 * de «categoría muy por encima»—, en el color del texto si sube, apagada si baja o queda igual.
 */
class LaDiferenciaDelWidgetTest {

    private fun categoria(gastado: Long, anterior: Long?) =
        CategoriaDelPeriodo(nombre = "Mercado", gastado = gastado, fraccion = 1f, anterior = anterior)

    @Test
    fun `sin periodo pasado no hay diferencia`() {
        assertNull(diferenciaDeLaCategoria(categoria(300_000, null)))
    }

    @Test
    fun `sube un poco en el color del texto`() {
        val d = diferenciaDeLaCategoria(categoria(300_000, 180_000))!!
        assertEquals("\$120.000 más", d.texto)
        assertEquals(TonoDeCifra.Neutro, d.tono)
        assertEquals("\$120.000 más que a esta altura del período pasado", d.descripcion)
    }

    @Test
    fun `sube mucho en alerta`() {
        val d = diferenciaDeLaCategoria(categoria(900_000, 400_000))!!
        assertEquals("\$500.000 más", d.texto)
        assertEquals(TonoDeCifra.Alerta, d.tono)
        // Mucho en plata pero poco en proporción: no es alerta (1,4M contra 1M).
        assertEquals(TonoDeCifra.Neutro, diferenciaDeLaCategoria(categoria(1_400_000, 1_000_000))!!.tono)
    }

    @Test
    fun `baja o queda igual apagada`() {
        val baja = diferenciaDeLaCategoria(categoria(100_000, 150_000))!!
        assertEquals("\$50.000 menos", baja.texto)
        assertEquals(TonoDeCifra.Apagado, baja.tono)
        val igual = diferenciaDeLaCategoria(categoria(100_000, 100_500))!!
        assertEquals("igual", igual.texto)
        assertEquals(TonoDeCifra.Apagado, igual.tono)
    }

    @Test
    fun `una categoria sin gasto el periodo pasado es nueva`() {
        val chica = diferenciaDeLaCategoria(categoria(80_000, 0))!!
        assertEquals(CATEGORIA_NUEVA, chica.texto)
        assertEquals(TonoDeCifra.Neutro, chica.tono)
        // Vuelta 76: «nuevo» va siempre en un solo tono; antes una nueva grande iba en rojo.
        assertEquals(TonoDeCifra.Neutro, diferenciaDeLaCategoria(categoria(450_000, 0))!!.tono)
    }
}
