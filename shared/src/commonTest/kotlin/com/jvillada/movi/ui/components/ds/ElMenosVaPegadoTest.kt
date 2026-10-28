package com.jvillada.movi.ui.components.ds

import com.jvillada.movi.theme.TextosDeMovi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **El menos de una cifra es el «−» (U+2212) y va pegado al «$»** (vuelta 29). El `−` de Figtree es
 * del ancho de un dígito con el trazo centrado (632/1000, 123 de aire a cada lado), y «−$85.000» se
 * leía «- $85.000». `conElMenosPegado` le pone `menosPegado` (−0,12 em) solo al menos; el texto no
 * cambia, así que todo lo que busca «−$27.500» lo sigue encontrando.
 */
class ElMenosVaPegadoTest {

    private val menos = TextosDeMovi().menosPegado

    @Test
    fun `el gasto se escribe con el menos tipografico, no con guion`() {
        val texto = textoDeLaCifra(-85_000, "COP", TonoDeCifra.Neutro, ocultos = false)
        assertEquals("−\$85.000", texto)
        assertEquals(SIGNO_MENOS, texto.first())
        assertTrue('-' !in texto, "un guion en vez del menos: $texto")
    }

    @Test
    fun `el menos lleva el ajuste y el resto de la cifra no`() {
        val cifra = conElMenosPegado("−\$85.000", menos)
        assertEquals("−\$85.000", cifra.text)
        assertEquals(1, cifra.spanStyles.size)
        val span = cifra.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(1, span.end)
        assertTrue(span.item.letterSpacing.value < 0f, "el menos se acerca al «$»")
    }

    @Test
    fun `sin menos no hay ajuste`() {
        assertTrue(conElMenosPegado("+\$4.500.000", menos).spanStyles.isEmpty())
        assertTrue(conElMenosPegado("\$85.000", menos).spanStyles.isEmpty())
    }
}
