package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith

/**
 * # Ola B, tarea 4: la cuadrícula de categorías nunca corta una palabra a la mitad
 *
 * En el teléfono del dueño «Entretenimiento» se partía en dos renglones a mitad de palabra
 * («Entretenimient» arriba, «o» solo abajo): el `Text` de la celda ajustaba a dos renglones sin
 * mirar si la palabra más larga entraba en el ancho real de la celda. La corrección —en
 * [CeldaDeLaCuadricula]— mide la palabra más larga contra el ancho de la celda y, si no entra al
 * tamaño normal, la achica (`autoSize` de `BasicText`) o, si ni achicada entra, la muestra en un
 * solo renglón con «…» — nunca la parte.
 *
 * Como «Entretenimiento» no tiene espacios, la única forma válida de que el resultado use más de
 * un renglón sería partiéndola: esta prueba se apoya en eso y solo pide `lineCount == 1`.
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: hace falta el motor de texto real para medir anchos de
 * palabra contra el ancho de la celda (ver el KDoc de `Esqueleto.kt`); a 390 dp, el ancho de
 * teléfono que reportó el dueño.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class RotuloDeCeldaNoSePartTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Entretenimiento no se corta a mitad de palabra en la cuadricula`() {
        composeRule.setContent {
            MoviTheme {
                // 16 dp de margen a cada lado, como una hoja de verdad — ver el KDoc de
                // `ANCHO_MINIMO_DE_CELDA`.
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SelectorDeCategoria(
                        elegida = "",
                        onElegir = {},
                        tipo = TransactionType.EXPENSE,
                        usadas = emptyMap(),
                        prefs = emptyMap(),
                        usos = emptyMap(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val nodos = composeRule.onAllNodesWithText("Entretenimiento", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("«Entretenimiento» no se encontró en la cuadrícula", nodos.isNotEmpty())

        val layout = layoutDe(nodos.first())
        assertTrue(
            "«Entretenimiento» quedó en ${layout.lineCount} renglones — sin espacios en la " +
                "palabra, más de uno solo puede salir de cortarla a la mitad",
            layout.lineCount == 1,
        )
    }

    /**
     * **Fix round 1, hallazgo 1.** La medición usaba siempre el peso NORMAL, pero la celda
     * ELEGIDA (y «Crear»/«Usar») se dibuja en Medium — más ancha. La categoría puesta es
     * justo la que el dueño ve cada vez que reabre el selector, así que si algo se prueba con
     * peso equivocado es esto.
     */
    @Test
    fun `Entretenimiento elegida (peso Medium) tampoco se corta a mitad de palabra`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SelectorDeCategoria(
                        elegida = "Entretenimiento",
                        onElegir = {},
                        tipo = TransactionType.EXPENSE,
                        usadas = emptyMap(),
                        prefs = emptyMap(),
                        usos = emptyMap(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val nodos = composeRule.onAllNodesWithText("Entretenimiento", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("«Entretenimiento» no se encontró en la cuadrícula", nodos.isNotEmpty())

        val layout = layoutDe(nodos.first())
        assertTrue(
            "«Entretenimiento» ELEGIDA (Medium) quedó en ${layout.lineCount} renglones — sin " +
                "espacios en la palabra, más de uno solo puede salir de cortarla a la mitad",
            layout.lineCount == 1,
        )
    }

    /**
     * **Fix round 1, hallazgo 2.** El modo ACHICADO (un renglón, `autoSize`) no reservaba el
     * mismo alto que el modo NORMAL (`minLines = 2`) — una fila con las dos («Entretenimiento»
     * achicada junto a «Comida» normal) quedaba con celdas de alto distinto. Se arma la fila a
     * mano con [CuadriculaDeCategorias] (dos celdas, así entran las dos en la misma fila sin
     * depender de cuántas columnas caben) y se compara el alto de las dos.
     */
    @Test
    fun `una fila con una celda achicada y una normal miden el mismo alto`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    CuadriculaDeCategorias(
                        celdas = listOf(
                            CeldaDeCategoria.Existente("Entretenimiento"),
                            CeldaDeCategoria.Existente("Comida"),
                        ),
                        elegida = "",
                        prefs = emptyMap(),
                        onElegir = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val altoEntretenimiento = composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Entretenimiento"))
            .getUnclippedBoundsInRoot().height.value
        val altoComida = composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida"))
            .getUnclippedBoundsInRoot().height.value

        val diferencia = abs(altoEntretenimiento - altoComida)
        assertTrue(
            "«Entretenimiento» (achicada) mide $altoEntretenimiento dp y «Comida» (normal) mide " +
                "$altoComida dp — diferencia de $diferencia dp, el máximo es 1 dp",
            diferencia <= 1f,
        )
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }
}
