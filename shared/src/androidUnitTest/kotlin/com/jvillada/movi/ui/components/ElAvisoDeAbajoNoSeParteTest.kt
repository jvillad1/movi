package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_AVISO_DE_DESHACER
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_TEXTO_DEL_AVISO
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Revisión de la vuelta 74 · el aviso de abajo no parte el texto por letras
 *
 * Con «Repartir con Caro» y «Deshacer» en el mismo renglón, a 360 dp el texto quedaba de una palabra
 * por línea: nueve renglones y ~210 px. Ahora el texto va a lo ancho (dos renglones como mucho) y los
 * botones abajo, a la derecha.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ElAvisoDeAbajoNoSeParteBase {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(texto: String) {
        val aviso = AvisoDeDeshacer()
        aviso.mostrar(texto, accion = AvisoDeDeshacer.Accion("Repartir con Caro")) {}
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AnfitrionDelAvisoDeDeshacer(aviso, Modifier.align(Alignment.BottomCenter)) } }
        }
        composeRule.waitForIdle()
    }

    private fun renglones(): Int {
        val resultados = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(TAG_TEXTO_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single().lineCount
    }

    private fun alto() = with(composeRule.density) {
        composeRule.onNodeWithTag(TAG_AVISO_DE_DESHACER).fetchSemanticsNode().boundsInRoot.height.toDp()
    }

    private fun anchoDelTexto() = with(composeRule.density) {
        composeRule.onNodeWithTag(TAG_TEXTO_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width.toDp()
    }

    @Test
    fun `con Repartir con Caro y Deshacer, el texto ocupa a lo ancho y cabe en dos renglones`() {
        montar("Anotado · Crepes & Waffles Oviedo \$32.000")

        assertTrue(renglones() <= 2, "renglones: ${renglones()}")
        assertTrue(anchoDelTexto() >= 250.dp, "el texto tiene que ir a lo ancho: ${anchoDelTexto()}")
        assertTrue(alto() <= 140.dp, "el aviso mide ${alto()}")
        composeRule.onNodeWithTag(TAG_ACCION_DEL_AVISO).assertExists()
        composeRule.onNodeWithTag(TAG_DESHACER_DEL_AVISO).assertExists()
    }

    @Test
    fun `un comercio muy largo se corta con puntos, no crece`() {
        montar("Anotado · Panaderia y Pasteleria El Trigal de la Avenida Oriental Sucursal Laureles \$32.000")

        assertTrue(renglones() <= 2, "renglones: ${renglones()}")
        assertTrue(alto() <= 140.dp, "el aviso mide ${alto()}")
    }
}

@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class ElAvisoDeAbajoNoSeParteA360Test : ElAvisoDeAbajoNoSeParteBase()

@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class ElAvisoDeAbajoNoSeParteA390Test : ElAvisoDeAbajoNoSeParteBase()
