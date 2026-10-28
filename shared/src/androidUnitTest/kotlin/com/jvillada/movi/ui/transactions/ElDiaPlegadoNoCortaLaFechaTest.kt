package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.RADIO_DEL_RESALTADO
import com.jvillada.movi.ui.components.ds.SALIDA_DEL_RESALTADO
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El día plegado no corta la fecha (vuelta de diseño 2)
 *
 * En producción, plegado, el encabezado decía «20 d… · 2 movimientos   Flujo del día −$57.400»: la
 * fecha —lo único que nombra el día— se recortaba para hacerle lugar al conteo y al rótulo. Ahora la
 * fecha, la cifra y el chevron se miden primero; si no cabe todo, se corta el conteo. Desde la vuelta
 * de diseño 14 el rótulo «Flujo del día» no va en ningún ancho: la cifra sola, con su signo, se entiende.
 *
 * A 360 dp (el teléfono más angosto) con la letra del sistema a ×1,3, con el margen de la pantalla
 * como en Movimientos. Mismo método que `NadaSeRecortaEnElInicioTest` (`sdk = [34]`, `NATIVE`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ElDiaPlegadoNoCortaLaFechaTest {

    @get:Rule val composeRule = createComposeRule()

    private var plegado by mutableStateOf(true)

    private fun montar(fecha: String, escala: Float = 1.3f, cuantos: Int = 12, total: Long = 1_257_400L) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                        EncabezadoDelDia(fecha = fecha, cifra = RotuloDelDia.GASTASTE to total, plegado = plegado, cuantos = cuantos, onAlternar = { plegado = !plegado })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun unico(tag: String): SemanticsNode =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single()

    private fun conTexto(texto: String, substring: Boolean = false): List<SemanticsNode> =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    /**
     * Entero: un renglón, sin «…», sin pasarse de su nodo. (No se mira `hasVisualOverflow`: con
     * `softWrap = false` el párrafo se arma sin tope de ancho y lo marca aunque el renglón quepa.)
     */
    private fun entero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        return d.lineCount == 1 && !d.isLineEllipsized(0) &&
            d.getLineRight(0) - d.getLineLeft(0) <= nodo.size.width + 0.5f
    }

    private fun laFechaEntera(fecha: String) {
        val nodoFecha = unico(TAG_FECHA_DEL_DIA)
        val d = disposicion(nodoFecha)
        assertTrue(
            entero(nodoFecha),
            "«$fecha» se recorta: mide ${nodoFecha.size.width}px, renglones ${d.lineCount}, «…» ${d.isLineEllipsized(0)}, " +
                "ancho del renglón ${d.getLineRight(0) - d.getLineLeft(0)}",
        )
        val cifra = unico(TAG_CIFRA)
        assertTrue(entero(cifra), "la cifra del día se recorta")
        assertTrue(nodoFecha.boundsInRoot.right <= cifra.boundsInRoot.left, "la fecha se mete debajo de la cifra")
        assertTrue(conTexto("Flujo del día", substring = true).isEmpty(), "el encabezado del día ya no dice «Flujo del día»")
    }

    @Test
    fun `a 360 dp con la letra agrandada la fecha del dia plegado se lee entera`() {
        montar("Martes 30 de septiembre")
        laFechaEntera("Martes 30 de septiembre")
    }

    @Test
    fun `si no cabe todo se corta el conteo, nunca la fecha ni la cifra`() {
        montar("Martes 30 de septiembre", cuantos = 128, total = 12_257_400L)
        laFechaEntera("Martes 30 de septiembre")
        assertTrue(conTexto("· 128 movimientos").isNotEmpty(), "el conteo sigue, aunque sea cortado")
    }

    @Test
    fun `desplegado es el mismo renglon, sin el conteo`() {
        montar("Martes 30 de septiembre")
        val plegadoArriba = unico(TAG_FECHA_DEL_DIA).boundsInRoot
        val altoPlegado = unico(TAG_ENCABEZADO_DE_DIA).size.height
        plegado = false
        composeRule.waitForIdle()
        laFechaEntera("Martes 30 de septiembre")
        assertTrue(conTexto("movimientos", substring = true).isEmpty())
        assertEquals(plegadoArriba, unico(TAG_FECHA_DEL_DIA).boundsInRoot, "la fecha no se mueve al desplegar")
        assertEquals(altoPlegado, unico(TAG_ENCABEZADO_DE_DIA).size.height, "el renglón mide lo mismo")
    }

    /**
     * Vuelta de diseño 16: en escritorio, con el mouse encima, el encabezado del día pintaba una banda
     * gris recta pegada a la fecha. Ahora lo que se toca (y se resalta) sale [SALIDA_DEL_RESALTADO]
     * hacia cada lado, dentro del margen, con el radio del sistema; la fecha sigue alineada con el
     * borde de la superficie de abajo y el renglón mide lo mismo para quien lo pone.
     */
    @Test
    fun `el resaltado del dia sale un poco hacia el margen y la fecha no se mueve`() {
        montar("Hoy · martes 28", escala = 1f, cuantos = 2, total = 57_400L)
        val densidad = composeRule.density.density
        val renglon = unico(TAG_ENCABEZADO_DE_DIA).boundsInRoot
        val toque = unico(TAG_TOQUE_DEL_DIA).boundsInRoot
        val fecha = unico(TAG_FECHA_DEL_DIA).boundsInRoot
        val salida = SALIDA_DEL_RESALTADO.value * densidad
        assertEquals(Medida.margenDePantalla.value * densidad, renglon.left, 0.5f, "el renglón va en el margen de la pantalla")
        assertEquals(renglon.left - salida, toque.left, 0.5f, "el resaltado sale hacia la izquierda")
        assertEquals(renglon.right + salida, toque.right, 0.5f, "y hacia la derecha")
        assertTrue(toque.left > 0f, "sin tocar el borde de la pantalla")
        assertEquals(renglon.left, fecha.left, 0.5f, "la fecha sigue alineada con el borde de la superficie")
        assertEquals(renglon.height, toque.height, 0.5f, "el resaltado no cambia el alto del renglón")
        assertEquals(Radio.m, RADIO_DEL_RESALTADO, "el radio de una fila destacada")
    }

    @Test
    fun `con espacio de sobra tampoco dice Flujo del dia, solo la cifra con su signo`() {
        montar("Hoy · martes 28", escala = 1f, cuantos = 2, total = 57_400L)
        laFechaEntera("Hoy · martes 28")
        // Vuelta 40: «Gastaste $57.400», la cifra en texto medio y sin signo.
        assertEquals("\$57.400", conTexto("57.400", substring = true).single().config[SemanticsProperties.Text].joinToString("") { it.text })
        assertTrue(conTexto("Gastaste").isNotEmpty())
        assertTrue(entero(conTexto("· 2 movimientos").single()))
    }
}
