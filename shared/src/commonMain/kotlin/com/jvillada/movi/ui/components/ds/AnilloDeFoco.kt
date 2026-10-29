package com.jvillada.movi.ui.components.ds

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.LocalColores
import com.jvillada.movi.theme.Medida

/**
 * # El anillo de foco (vuelta 55, accesibilidad)
 *
 * La revisión de accesibilidad del 28-oct navegó la web con Tab: el orden era lógico, pero **el foco
 * casi no se veía** —un cuadrado gris tenue sobre el amarillo del avatar, un gris claro en las filas—.
 * Desde esta vuelta, todo lo que se enfoca en `ds/` dibuja, con el foco del **teclado**, un anillo de
 * [Medida.trazoDelAnilloDeFoco] en la tinta (`texto`, 16:1 sobre el blanco y el crema) a
 * [Medida.separacionDelAnilloDeFoco] de su borde, con la forma del componente.
 *
 * - **Solo con el teclado** (el `:focus-visible` de la web): con el dedo no aparece. Lo decide
 *   [seVeElAnilloDeFoco] con el modo de entrada de Compose (`LocalInputModeManager`), que pasa a
 *   `Keyboard` con una tecla y vuelve a `Touch` con un toque.
 * - **Va antes del `clip` y del `clickable`** en la cadena: antes del `clip` para que el anillo,
 *   que cae afuera del componente, no se recorte; antes del `clickable` porque observa el foco del
 *   primero que lo tiene debajo (como `onFocusChanged`).
 * - **[adentro]**: una [Fila] vive dentro de una `Superficie` recortada, que se comería un anillo de
 *   afuera; la fila lo dibuja hacia adentro, con la misma separación desde su borde.
 * - **[alrededorDe]**: cuando lo que se ve es más chico que lo que se toca (la [Casilla]: una caja de
 *   24 en un toque de 48), el anillo va alrededor de lo que se ve, centrado.
 * - **[deLoDeAdentro]**: un `Campo` no es el enfocable; lo es su `BasicTextField`, adentro de la caja.
 *   Con esto el anillo de la caja se prende cuando el foco está en algo de adentro.
 *
 * `ElFocoSeVeConElTecladoTest` monta los componentes y vigila que el enfocado por teclado lo lleve
 * ([AnilloDeFocoVisibleKey]); `TodoLoEnfocableDibujaElAnilloTest` vigila que ningún componente de
 * `ds/` (ni las pestañas) se toque sin él.
 */
fun Modifier.anilloDeFoco(
    forma: Shape,
    adentro: Boolean = false,
    alrededorDe: Dp? = null,
    deLoDeAdentro: Boolean = false,
): Modifier = this then AnilloDeFocoElement(forma, adentro, alrededorDe, deLoDeAdentro)

/**
 * **¿Se dibuja el anillo?** Solo si el componente tiene el foco y el foco llegó por teclado. Pura:
 * es la regla del `:focus-visible` del navegador.
 */
fun seVeElAnilloDeFoco(enfocado: Boolean, modo: InputMode): Boolean = enfocado && modo == InputMode.Keyboard

/** Si el anillo está dibujado ahora. Lo leen las pruebas; el lector de pantalla no lo dice. */
val AnilloDeFocoVisibleKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("AnilloDeFocoVisible")

/** Ver [AnilloDeFocoVisibleKey]. */
var SemanticsPropertyReceiver.anilloDeFocoVisible: Boolean by AnilloDeFocoVisibleKey

private data class AnilloDeFocoElement(
    val forma: Shape,
    val adentro: Boolean,
    val alrededorDe: Dp?,
    val deLoDeAdentro: Boolean,
) : ModifierNodeElement<AnilloDeFocoNode>() {
    override fun create(): AnilloDeFocoNode = AnilloDeFocoNode(forma, adentro, alrededorDe, deLoDeAdentro)

    override fun update(node: AnilloDeFocoNode) {
        node.forma = forma
        node.adentro = adentro
        node.alrededorDe = alrededorDe
        node.deLoDeAdentro = deLoDeAdentro
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "anilloDeFoco"
        properties["forma"] = forma
        properties["adentro"] = adentro
    }
}

private class AnilloDeFocoNode(
    var forma: Shape,
    var adentro: Boolean,
    var alrededorDe: Dp?,
    var deLoDeAdentro: Boolean,
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode, SemanticsModifierNode, CompositionLocalConsumerModifierNode {

    private var enfocado = false

    private fun visible(): Boolean =
        seVeElAnilloDeFoco(enfocado, currentValueOf(LocalInputModeManager).inputMode)

    override fun onFocusEvent(focusState: FocusState) {
        val ahora = if (deLoDeAdentro) focusState.hasFocus else focusState.isFocused
        if (ahora == enfocado) return
        enfocado = ahora
        invalidateDraw()
        invalidateSemantics()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        anilloDeFocoVisible = visible()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // Leer el modo acá hace que un cambio de teclado a dedo (o al revés) vuelva a dibujar.
        if (!visible()) return
        val trazo = Medida.trazoDelAnilloDeFoco.toPx()
        val separacion = Medida.separacionDelAnilloDeFoco.toPx()
        // El centro del trazo: afuera, a la separación más medio trazo; adentro, lo mismo hacia el centro.
        val corrimiento = (separacion + trazo / 2f) * if (adentro) -1f else 1f
        // Lo que rodea: el componente entero, o lo que se ve de él, centrado.
        val base = alrededorDe?.toPx()?.let { Size(it, it) } ?: size
        val origenX = (size.width - base.width) / 2f
        val origenY = (size.height - base.height) / 2f
        val tamano = Size(base.width + 2 * corrimiento, base.height + 2 * corrimiento)
        if (tamano.width <= 0f || tamano.height <= 0f) return
        val contorno = forma.createOutline(tamano, layoutDirection, this)
        val tinta = currentValueOf(LocalColores).texto
        translate(origenX - corrimiento, origenY - corrimiento) {
            drawOutline(contorno, tinta, style = Stroke(width = trazo))
        }
    }
}
