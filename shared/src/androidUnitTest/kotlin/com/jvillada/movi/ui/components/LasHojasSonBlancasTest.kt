package com.jvillada.movi.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import com.jvillada.movi.ConClaseDeAncho
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Las hojas son blancas en la web (vuelta 61)
 *
 * La revisión midió en la web el panel de toda hoja en `#EBEBEB` y su botón principal en `#EBB318`:
 * un 8 % de negro encima. Era la capa de estado del *ripple* del `clickable` del velo, que contiene al
 * panel: con el mouse sobre el panel el velo también estaba en *hover* y su indicación se pintaba
 * encima de todo lo de adentro (ver `veloSinIndicacion` en [MarcoDeHoja]).
 *
 * `captureToImage` se cuelga en Robolectric, así que no se mide el píxel: se mide **la causa**. El
 * tema se cambia por una indicación espía que anota cada hover y cada foco que le llega a quien la
 * use; el panel lleva solo un texto (nada de adentro se puede tocar), así que lo único que podía
 * recibir un hover sobre el panel era el velo. Con la hoja abierta, el mouse sobre el panel y sobre el
 * velo, y el foco del teclado en el velo (con teclado físico, la web), la espía no ve nada.
 * Sin el arreglo, la espía veía el `HoverInteraction.Enter` del velo.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LasHojasSonBlancasTest {

    @get:Rule val composeRule = createComposeRule()

    /** Anota cada interacción de hover o de foco que le llega a una indicación del tema. */
    private class IndicacionEspia : IndicationNodeFactory {
        val vistas = mutableListOf<Interaction>()

        override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {
            override fun onAttach() {
                coroutineScope.launch {
                    interactionSource.interactions.collect {
                        if (it is HoverInteraction || it is FocusInteraction) vistas += it
                    }
                }
            }
        }

        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private val espia = IndicacionEspia()

    private fun abrirLaHoja() {
        composeRule.setContent {
            ConClaseDeAncho {
                // Con teclado físico, como en la web: el velo es un destino de foco y lo pide al abrir.
                CompositionLocalProvider(LocalIndication provides espia, LocalHayTecladoFisico provides true) {
                    Box(Modifier.fillMaxSize())
                    MarcoDeHoja(onDismiss = {}) { Text("Ajustar saldo") }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun pasarElMouse() {
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).performMouseInput {
            enter(center)
            moveTo(center + Offset(4f, 4f))
        }
        composeRule.waitForIdle()
        // Y sobre el velo, fuera del panel (la esquina de arriba a la izquierda).
        composeRule.onRoot().performMouseInput { moveTo(Offset(2f, 2f)) }
        composeRule.waitForIdle()
    }

    private fun nadaSePintaEncima(donde: String) {
        assertTrue(
            espia.vistas.isEmpty(),
            "$donde: el velo contiene al panel, y una indicación suya en hover o foco se pinta encima de " +
                "toda la hoja (el gris de la web). Llegaron: ${espia.vistas}",
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `la hoja desde abajo no pinta indicacion sobre el panel`() {
        abrirLaHoja()
        pasarElMouse()
        nadaSePintaEncima("La hoja desde abajo (la web angosta)")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h900dp-mdpi")
    fun `la hoja centrada no pinta indicacion sobre el panel`() {
        abrirLaHoja()
        pasarElMouse()
        nadaSePintaEncima("La hoja centrada (la web ancha)")
    }
}
