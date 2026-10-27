package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.Accion
import com.jvillada.movi.ui.components.ds.CuadriculaDeAcciones
import com.jvillada.movi.ui.components.ds.TAG_ACCION_RAPIDA
import com.jvillada.movi.ui.components.ds.TAG_ROTULO_DE_ACCION
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La cuadrícula del Inicio, con los ocho rótulos en un renglón (vuelta de diseño 2)
 *
 * En producción, a 390 dp, «Anotar ingreso», «Subir comprobante» y «Preguntar a Movi» iban en dos
 * renglones y los demás en uno: la fila quedaba despareja. Ahora los rótulos son cortos («Gasto»,
 * «Comprobante», «Preguntar») y la frase entera es la del lector de pantalla.
 *
 * Se monta la cuadrícula como la monta el Inicio (`AccionesDelInicioSection`: las acciones de
 * [accionesDelInicio], con el margen de la pantalla) a 360 dp, el teléfono más angosto, con la letra
 * que la app le pone a todo (×1,12) y con la del sistema agrandada (×1,3). Mismo método que
 * `NadaSeRecortaEnElInicioTest`: `sdk = [34]` y `NATIVE` para el motor de texto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LasAccionesDelInicioEnUnRenglonTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(escala: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth()) {
                        CuadriculaDeAcciones(
                            acciones = accionesDelInicio().map { accion ->
                                Accion(accion.rotulo, Icons.Rounded.Add, {}, descripcion = accion.descripcion)
                            },
                            modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rotulos(): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(TAG_ROTULO_DE_ACCION, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    /**
     * Un renglón, sin «…» y sin pasarse del ancho de su columna. (No se mira `hasVisualOverflow`: con
     * `softWrap = false` el párrafo se arma sin tope de ancho y lo marca aunque el renglón quepa.)
     */
    private fun enUnRenglonEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        return d.lineCount == 1 && !d.isLineEllipsized(0) &&
            d.getLineRight(0) - d.getLineLeft(0) <= nodo.size.width + 0.5f
    }

    private fun losOcho(escala: Float): List<SemanticsNode> {
        montar(escala)
        val nodos = rotulos()
        assertEquals(8, nodos.size)
        assertEquals(
            listOf("Gasto", "Ingreso", "Traspaso", "Pagar tarjeta", "Repartir", "Comprobante", "Avisos", "Preguntar"),
            nodos.map(::texto),
        )
        // La fila no queda despareja: las cuatro acciones de cada fila miden lo mismo de alto.
        composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes().chunked(4).forEach { fila ->
            assertEquals(1, fila.map { it.size.height }.distinct().size, "acciones de distinto alto (×$escala)")
        }
        return nodos
    }

    /** Con la letra de la app (×1,12), los ocho enteros: ni un segundo renglón ni «…». */
    @Test
    fun `a 360 dp los ocho rotulos van en un renglon`() {
        losOcho(1.12f).forEach { nodo -> assertTrue(enUnRenglonEntero(nodo), "«${texto(nodo)}» no entra en un renglón") }
    }

    /**
     * Con la letra del sistema agrandada (×1,3) ninguno baja a un segundo renglón: el que no entra
     * se achica hasta la letra del `rotulo` y, si ni así, se corta con «…» —la frase entera la dice
     * el lector de pantalla (ver `el lector de pantalla dice la frase entera`)—.
     */
    @Test
    fun `a 360 dp con la letra agrandada ninguno baja de renglon`() {
        losOcho(1.3f).forEach { nodo -> assertEquals(1, disposicion(nodo).lineCount, "«${texto(nodo)}» baja de renglón") }
    }

    @Test
    fun `el lector de pantalla dice la frase entera`() {
        montar(1.12f)
        listOf("Anotar un gasto", "Subir un comprobante", "Revisar los avisos", "Preguntarle a Movi").forEach {
            composeRule.onNodeWithContentDescription(it, useUnmergedTree = true).assertExists()
        }
    }
}
