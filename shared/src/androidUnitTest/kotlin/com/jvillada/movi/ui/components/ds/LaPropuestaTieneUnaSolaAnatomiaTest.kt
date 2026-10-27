package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Una propuesta, una anatomía** (vuelta de diseño 1): ícono, título, cifra opcional en su propio
 * renglón, subtítulo debajo y acciones. En producción, «Arriendo venció hace 21 días» llevaba
 * «$1.800.000» a la izquierda y «y 2 pagos más por vencer» partido en dos líneas a la derecha.
 *
 * A 360 dp —el teléfono más angosto que se mira— y con el motor de texto real (`sdk = [34]`,
 * `NATIVE`), como `NadaSeRecortaEnElInicioTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaPropuestaTieneUnaSolaAnatomiaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                // El margen de la pantalla, como en el feed del Inicio.
                Box(Modifier.fillMaxSize().padding(Medida.margenDePantalla)) {
                    TarjetaDePropuesta(
                        icono = Icons.Rounded.Schedule,
                        titulo = "Arriendo venció hace 21 días",
                        cifra = 1_800_000,
                        cuerpo = "y 2 pagos más por vencer",
                        tono = TonoDeIcono.Aviso,
                        accion = "Anotar pago",
                        onAccion = {},
                        accionSecundaria = "Ver pagos",
                        onAccionSecundaria = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun renglones(tag: String): Int {
        val resultados = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single().lineCount
    }

    @Test
    fun `titulo, cifra, subtitulo y acciones, cada uno en su renglon`() {
        montar()

        val titulo = composeRule.onNodeWithText("Arriendo venció hace 21 días", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val cifra = composeRule.onNodeWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtitulo = composeRule.onNodeWithTag(TAG_SUBTITULO_DE_PROPUESTA, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val accion = composeRule.onNodeWithText("Anotar pago", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue(titulo.bottom <= cifra.top, "la cifra va debajo del título")
        assertTrue(cifra.bottom <= subtitulo.top, "el subtítulo va debajo de la cifra, no a su lado")
        assertTrue(subtitulo.bottom <= accion.top, "las acciones van al final")
        // Las cuatro piezas arrancan en el mismo borde: una columna, no una fila partida.
        assertEquals(titulo.left, cifra.left, 0.5f)
        assertEquals(titulo.left, subtitulo.left, 0.5f)
    }

    @Test
    fun `el subtitulo corto entra en un renglon`() {
        montar()
        assertEquals(1, renglones(TAG_SUBTITULO_DE_PROPUESTA), "«y 2 pagos más por vencer» se partió")
    }
}
