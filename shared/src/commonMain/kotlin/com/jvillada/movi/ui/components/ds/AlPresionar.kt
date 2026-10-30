package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento

/*
 * # Un solo presionado (vuelta 79 · «que tocar se sienta bien»)
 *
 * La revisión visual 7 encontró que Movi **se sentía barata al tocarla**: al presionar una acción de
 * Inicio aparecía una caja gris de esquinas rectas encima del círculo y del rótulo (el ripple de
 * Material, que no sabe de la forma de lo que se toca), las pestañas mostraban un rectángulo en hover y
 * al presionar, y el botón amarillo presionado se volvía mostaza (el velo de tinta sobre el amarillo).
 *
 * Ahora todo lo que se toca en `ds/`, la barra de pestañas y el rail pasa por [alPresionar]:
 *
 * - **Sin ripple** (`indication = null`): nada pinta un rectángulo encima.
 * - **Se hunde**: escala [Movimiento.ESCALA_AL_PRESIONAR] (0,96) en [Movimiento.PRESIONAR_MS] (~100 ms) con
 *   la curva estándar, y vuelve igual al soltar. El `Boton` usa 0,97 y la `Fila` de lista 0,985.
 * - **El velo**: con [velo], una superficie neutra se oscurece apenas ([com.jvillada.movi.theme.ColoresDeMovi.velo],
 *   la tinta al 6 %) **recortada a su [forma]** mientras el mouse está encima (la web) y, con el sistema
 *   pidiendo menos movimiento, mientras se presiona (sin la escala, el toque tiene que verse en algo).
 *   Nunca sobre el amarillo: el `Boton` principal cambia a `marcaPresionada`, y la acción rápida pone su
 *   círculo en la capa 3 (cada uno lo decide con [MutableInteractionSource] que recibe).
 *
 * **Dónde va en la cadena**: antes del `clip` y del `background` —si no, la escala solo achica lo de
 * adentro y el fondo se queda quieto— y después del `anilloDeFoco` (el anillo escucha el foco de lo que
 * tiene debajo). `ElToqueEsDelSistemaTest` vigila que en `ds/`, la barra y el rail no vuelva un
 * `clickable` suelto, un `ripple(` ni un `LocalIndication`.
 */

/** La escala que [alPresionar] le pone al componente mientras se presiona, en su semántica. */
val EscalaAlPresionarKey: SemanticsPropertyKey<Float> = SemanticsPropertyKey("EscalaAlPresionar")

/** Ver [EscalaAlPresionarKey]. */
var SemanticsPropertyReceiver.escalaAlPresionar: Float by EscalaAlPresionarKey

/** ¿El velo de [alPresionar] está a la vista (hover, o presionado con menos movimiento)? En su semántica. */
val VeloAlPresionarKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("VeloAlPresionar")

/** Ver [VeloAlPresionarKey]. */
var SemanticsPropertyReceiver.veloAlPresionar: Boolean by VeloAlPresionarKey

/**
 * **¿Se ve el velo?** Con el mouse encima, siempre; presionado, solo con menos movimiento (con
 * movimiento, el toque lo dice la escala). Pura.
 */
fun seVeElVelo(presionado: Boolean, encima: Boolean, reducido: Boolean): Boolean =
    encima || (presionado && reducido)

/**
 * **La escala de un toque**: [escala] mientras se presiona, 1 si no o si el sistema pide menos
 * movimiento. Pura.
 */
fun escalaDelToque(presionado: Boolean, reducido: Boolean, escala: Float): Float =
    if (presionado && !reducido) escala else 1f

/**
 * **El toque de Movi**: `clickable` sin ripple, que se hunde al presionar (ver el KDoc del archivo).
 *
 * @param fuente la del componente, si necesita saber que lo presionan (el círculo de una acción que pasa
 *   a la capa 3, el botón principal que cambia de tono con menos movimiento). Sin ella, una propia.
 * @param velo si la superficie es neutra y lleva el velo en hover (no para el amarillo).
 * @param forma a la que se recorta el velo: la del componente, nunca un rectángulo que no es el suyo.
 */
fun Modifier.alPresionar(
    onClick: () -> Unit,
    habilitado: Boolean = true,
    rol: Role? = null,
    onClickLabel: String? = null,
    escala: Float = Movimiento.ESCALA_AL_PRESIONAR,
    fuente: MutableInteractionSource? = null,
    velo: Boolean = false,
    forma: Shape = RectangleShape,
): Modifier = composed {
    val interacciones = fuente ?: remember { MutableInteractionSource() }
    this
        .hundimiento(interacciones, escala, velo, forma, habilitado)
        .clickable(
            interactionSource = interacciones,
            indication = null,
            enabled = habilitado,
            onClickLabel = onClickLabel,
            role = rol,
            onClick = onClick,
        )
}

/**
 * **Lo mismo que [alPresionar], para lo que se prende y se apaga** (una [Casilla]): `toggleable` sin ripple.
 */
fun Modifier.alAlternar(
    valor: Boolean,
    onCambiar: (Boolean) -> Unit,
    habilitado: Boolean = true,
    rol: Role? = null,
    escala: Float = Movimiento.ESCALA_AL_PRESIONAR,
    velo: Boolean = false,
    forma: Shape = RectangleShape,
): Modifier = composed {
    val interacciones = remember { MutableInteractionSource() }
    this
        .hundimiento(interacciones, escala, velo, forma, habilitado)
        .toggleable(
            value = valor,
            interactionSource = interacciones,
            indication = null,
            enabled = habilitado,
            role = rol,
            onValueChange = onCambiar,
        )
}

/**
 * **Solo lo que se ve de un toque** (la escala y el velo), para un gesto que no es `clickable` —el
 * `combinedClickable` del «+» del rail, que también se mantiene presionado—. Quien lo usa pasa la MISMA
 * [fuente] a su gesto, con `indication = null`.
 */
fun Modifier.hundimientoAlPresionar(
    fuente: MutableInteractionSource,
    escala: Float = Movimiento.ESCALA_AL_PRESIONAR,
    velo: Boolean = false,
    forma: Shape = RectangleShape,
): Modifier = hundimiento(fuente, escala, velo, forma, habilitado = true)

/**
 * **Solo el toque, sin ripple ni lo que se ve**: para quien pone [hundimientoAlPresionar] más afuera en
 * la cadena (una [Superficie] se hunde con su sombra, y la sombra va antes del anillo de foco). Misma
 * [fuente] en los dos.
 */
fun Modifier.toqueSinRipple(
    fuente: MutableInteractionSource,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    rol: Role? = null,
    onClickLabel: String? = null,
): Modifier = clickable(
    interactionSource = fuente,
    indication = null,
    enabled = habilitado,
    onClickLabel = onClickLabel,
    role = rol,
    onClick = onClick,
)

private fun Modifier.hundimiento(
    fuente: MutableInteractionSource,
    escala: Float,
    velo: Boolean,
    forma: Shape,
    habilitado: Boolean,
): Modifier = composed {
    val reducido = LocalMovimientoReducido.current
    val presionado by fuente.collectIsPressedAsState()
    val encima by fuente.collectIsHoveredAsState()
    val objetivo = if (habilitado) escalaDelToque(presionado, reducido, escala) else 1f
    val actual by animateFloatAsState(objetivo, Movimiento.alPresionar(reducido), label = "presionado")
    val conVelo = velo && habilitado && seVeElVelo(presionado, encima, reducido)
    val colorDelVelo = Movi.colores.velo
    this
        .semantics {
            escalaAlPresionar = objetivo
            veloAlPresionar = conVelo
        }
        .graphicsLayer {
            scaleX = actual
            scaleY = actual
        }
        .then(
            if (conVelo) {
                Modifier.drawWithContent {
                    drawContent()
                    drawOutline(forma.createOutline(size, layoutDirection, this), colorDelVelo)
                }
            } else {
                Modifier
            },
        )
}

/** ¿Lo están presionando? Para el componente que cambia de color al presionar (con su [MutableInteractionSource]). */
@Composable
fun MutableInteractionSource.estaPresionado(): Boolean {
    val presionado by collectIsPressedAsState()
    return presionado
}

/** ¿El mouse está encima? (La web y el escritorio: en el teléfono nunca.) */
@Composable
fun MutableInteractionSource.estaEncima(): Boolean {
    val encima by collectIsHoveredAsState()
    return encima
}
