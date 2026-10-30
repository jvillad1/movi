package com.jvillada.movi.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.jvillada.movi.theme.Movimiento
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 79 · el cambio de pestaña es fade-through, sin doble exposición
 *
 * La revisión visual 7: a los 100 ms de tocar «Pagos» se veían encimados el héroe de Inicio y la lista
 * de Pagos (`r7-pestana-pagos-100.png`): el fundido de salida y el de entrada corrían a la vez. Ahora sale
 * lo viejo en [Movimiento.SALIDA_DEL_FUNDIDO_MS] y recién después entra lo nuevo. Se muestrea **cada
 * milisegundo** de la transición con los mismos specs que usa `transicionEntrePantallas`.
 */
class DobleExposicionTest {

    @Test
    fun `en ningun cuadro se ven las dos pantallas a la vez`() {
        val dobles = (0..DURACION_DEL_FUNDIDO_MS.toLong() + 16).filter { ms ->
            val (sale, entra) = opacidadesDelFundido(ms)
            sale > 0f && entra > 0f
        }
        assertTrue(dobles.isEmpty(), "doble exposición en los ms ${dobles.take(10)}")
    }

    @Test
    fun `lo viejo se va del todo antes de que entre lo nuevo, y lo nuevo llega entero`() {
        assertEquals(1f to 0f, opacidadesDelFundido(0))
        assertEquals(0f, opacidadesDelFundido(Movimiento.SALIDA_DEL_FUNDIDO_MS.toLong()).first)
        assertEquals(0f, opacidadesDelFundido(Movimiento.SALIDA_DEL_FUNDIDO_MS.toLong()).second)
        assertEquals(1f, opacidadesDelFundido(DURACION_DEL_FUNDIDO_MS.toLong()).second)
        assertTrue(DURACION_DEL_FUNDIDO_MS <= Movimiento.LARGO_MS + Movimiento.SALIDA_DEL_FUNDIDO_MS)
    }

    @Test
    fun `apilar y volver no cambian, y con menos movimiento es un corte en seco`() {
        val reducido = transicionEntrePantallas(DireccionDeTransicion.FUNDIDO, reducido = true)
        assertEquals(EnterTransition.None, reducido.targetContentEnter)
        assertEquals(ExitTransition.None, reducido.initialContentExit)
        // Apilar sigue deslizando desde la derecha con su fundido de siempre.
        val apilar = transicionEntrePantallas(DireccionDeTransicion.APILAR, reducido = false)
        assertTrue(apilar.targetContentEnter != EnterTransition.None)
    }
}
