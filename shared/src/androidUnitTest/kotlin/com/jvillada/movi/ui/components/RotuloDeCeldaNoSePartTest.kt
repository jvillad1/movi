package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
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

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }
}
