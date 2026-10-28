package com.jvillada.movi.ui.quickadd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 45 · a 360 dp, ningún chip de categoría queda cortado al abrir.** La fila se corría lo justo
 * para mostrar la elegida y dejaba «+ Nueva» partida contra el borde izquierdo. Ahora, si correr lo justo
 * parte una pastilla, la fila se corre hasta esconderla entera.
 *
 * En píxeles, como a 360 dp con densidad 1: margen 16, aire 8, ventana 360. Pastillas (medidas adentro
 * del margen): «+ Nueva» 0..96, «Restaurantes» 104..226, «Mercado» 234..328, «Comida» 336..420.
 */
class LaFilaDePastillasNoCortaTest {

    private val margen = 16
    private val ventana = 360
    private val nueva = 0..96
    private val restaurantes = 104..226
    private val comida = 336..420

    private fun pastillas(mercado: IntRange = 234..328) = listOf(nueva, restaurantes, mercado, comida)

    private fun destino(lugar: IntRange, desde: Int = 0, mercado: IntRange = 234..328) =
        desplazamientoParaLaElegida(lugar, pastillas(mercado), desde, ventana, margen)

    /** Ninguna pastilla empieza antes del borde izquierdo visible y termina después. */
    private fun ningunaPartida(desde: Int, mercado: IntRange = 234..328) =
        pastillas(mercado).none { it.first + margen < desde && it.last + margen > desde }

    /** La elegida se ve entera entre los dos bordes. */
    private fun enteraALaVista(lugar: IntRange, desde: Int) =
        lugar.first + margen >= desde && lugar.last + margen <= desde + ventana

    @Test
    fun `la elegida que ya se ve entera no mueve la fila`() {
        assertNull(destino(restaurantes))
        assertNull(destino(234..328), "«Mercado» cabe con «+ Nueva» a la vista: la fila arranca en «Nueva»")
    }

    @Test
    fun `la elegida que no cabe corre la fila hasta esconder entera a Nueva`() {
        // Con «Mercado» más ancha (234..350): lo justo eran 350 + 32 − 360 = 22, que parte «+ Nueva»
        // (16..112 en el desplazamiento). Se corre hasta su final.
        val mercado = 234..350
        val d = destino(mercado, mercado = mercado)!!
        assertEquals(112, d)
        assertTrue(ningunaPartida(d, mercado))
        assertTrue(enteraALaVista(mercado, d))
    }

    @Test
    fun `con la ultima elegida, tampoco queda ninguna partida`() {
        val d = destino(comida)!!
        assertEquals(112, d)
        assertTrue(ningunaPartida(d))
        assertTrue(enteraALaVista(comida, d))
    }

    @Test
    fun `la elegida a la izquierda vuelve a arrancar en el margen`() {
        assertEquals(restaurantes.first, destino(restaurantes, desde = 200))
    }
}
