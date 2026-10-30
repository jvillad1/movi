package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_AVISO_DE_DESHACER
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_TEXTO_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_RESTO_DEL_AVISO
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El aviso de abajo es **un renglón** (vuelta 79; en la 74 eran dos)
 *
 * La revisión visual 7: el aviso «Anotado» era una tarjeta blanca de dos renglones con «Repartir con
 * Caro» con borde y «Deshacer», y quedaba encima de la propuesta. Ahora es un snackbar oscuro de un
 * renglón: el texto a la izquierda, cortado con «…» si no cabe, y las acciones como texto —«Repartir» ·
 * «Deshacer»— siempre enteras y dentro del aviso, a 360 dp y a 390.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ElAvisoDeAbajoNoSeParteBase {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(texto: String) {
        val aviso = AvisoDeDeshacer()
        aviso.mostrar(texto, accion = AvisoDeDeshacer.Accion("Repartir con Caro", rotuloCorto = "Repartir")) {}
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AnfitrionDelAvisoDeDeshacer(aviso, Modifier.align(Alignment.BottomCenter)) } }
        }
        composeRule.waitForIdle()
    }

    private fun disposicion(tag: String = TAG_TEXTO_DEL_AVISO): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    private fun alto() = with(composeRule.density) {
        composeRule.onNodeWithTag(TAG_AVISO_DE_DESHACER).fetchSemanticsNode().boundsInRoot.height.toDp()
    }

    private fun bordes(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun lasAccionesSeVenEnteras() {
        val aviso = bordes(TAG_AVISO_DE_DESHACER)
        for (tag in listOf(TAG_ACCION_DEL_AVISO, TAG_DESHACER_DEL_AVISO)) {
            val b = bordes(tag)
            assertTrue(b.width > 0f && b.left >= aviso.left && b.right <= aviso.right, "$tag se sale del aviso: $b de $aviso")
        }
        assertTrue(bordes(TAG_TEXTO_DEL_AVISO).right <= bordes(TAG_ACCION_DEL_AVISO).left, "el texto se mete debajo de las acciones")
        assertTrue(bordes(TAG_RESTO_DEL_AVISO).right <= bordes(TAG_ACCION_DEL_AVISO).left, "la categoría se mete debajo de las acciones")
    }

    @Test
    fun `con Repartir y Deshacer, el aviso es de un renglon`() {
        montar("Anotado \$12.500 · Restaurantes")

        assertEquals(1, disposicion().lineCount)
        assertTrue(alto() <= 64.dp, "el aviso mide ${alto()}")
        lasAccionesSeVenEnteras()
        composeRule.onNodeWithText("Repartir", useUnmergedTree = true).assertExists()
        // El lector de pantalla dice la acción entera.
        composeRule.onNodeWithContentDescription("Repartir con Caro", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `un comercio muy largo se corta con puntos y las acciones siguen a la vista`() {
        montar("Anotado \$32.000 · Panaderia y Pasteleria El Trigal de la Avenida Oriental Sucursal Laureles")

        // Revisión de la vuelta 82: el monto va entero en su parte; lo que se corta es el comercio.
        assertFalse(disposicion(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO).hasVisualOverflow, "el monto se ve entero")
        val d = disposicion(TAG_RESTO_DEL_AVISO)
        assertEquals(1, d.lineCount)
        assertTrue(d.isLineEllipsized(0), "el texto largo se corta con «…»")
        assertTrue(alto() <= 64.dp, "el aviso mide ${alto()}")
        lasAccionesSeVenEnteras()
    }
}

@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class ElAvisoDeAbajoNoSeParteA360Test : ElAvisoDeAbajoNoSeParteBase()

@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class ElAvisoDeAbajoNoSeParteA390Test : ElAvisoDeAbajoNoSeParteBase()
