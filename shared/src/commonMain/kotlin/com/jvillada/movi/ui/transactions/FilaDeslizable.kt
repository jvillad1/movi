package com.jvillada.movi.ui.transactions

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Radio
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import kotlin.math.abs
import kotlin.math.roundToInt

/** El `testTag` del fondo que revela una fila deslizada (para medir que se ve). */
const val TAG_FONDO_DE_FILA_DESLIZADA: String = "fondo-de-fila-deslizada"

/**
 * Qué parte del ancho de la fila hay que deslizar para que, al soltar, pase algo. Un tercio: menos
 * se dispara al mover la lista con el pulgar un poco torcido; más obliga a cruzar la pantalla.
 */
internal const val UMBRAL_DE_LA_FILA_DESLIZADA: Float = 0.33f

/** Qué pasa al soltar una fila deslizada [desplazamiento] px de [ancho] px. */
internal enum class AlSoltarLaFila { Nada, Categoria, Confirmar }

/**
 * La decisión al soltar, aparte del `@Composable` para poder probarla: a la izquierda pasado el
 * umbral, «Categoría»; a la derecha pasado el umbral, «Confirmar» **solo** si la fila es un aviso
 * pendiente; lo demás vuelve a su lugar sin hacer nada.
 */
internal fun alSoltarLaFila(desplazamiento: Float, ancho: Int, puedeConfirmar: Boolean): AlSoltarLaFila {
    if (ancho <= 0) return AlSoltarLaFila.Nada
    val umbral = ancho * UMBRAL_DE_LA_FILA_DESLIZADA
    return when {
        desplazamiento <= -umbral -> AlSoltarLaFila.Categoria
        desplazamiento >= umbral && puedeConfirmar -> AlSoltarLaFila.Confirmar
        else -> AlSoltarLaFila.Nada
    }
}

/**
 * # Deslizar una fila de Movimientos (Ola 2b)
 *
 * - **A la izquierda** revela «Categoría»; soltada pasado el umbral, [onCategoria] (quien llama abre
 *   el detalle con el selector de categoría ya abierto: el mismo `recategorizar` de siempre).
 * - **A la derecha**, solo en una fila pendiente de confirmar ([puedeConfirmar]), revela
 *   «Confirmar»; soltada pasado el umbral, [onConfirmar]. En una fila que no es un aviso pendiente
 *   la fila no se mueve hacia ese lado.
 *
 * Siempre vuelve a su lugar al soltar: lo que pasa después (la hoja que se abre, la fila que se
 * relee) es lo que el dueño ve. La vuelta usa [Movimiento.alVolver], que con movimiento reducido es
 * instantánea. El arrastre sigue al dedo —es manipulación directa, no una animación—.
 *
 * **Con [habilitada] en `false` no hay gesto**: la fila es la de siempre. Movimientos lo apaga en
 * pantalla ancha (web y escritorio con mouse), donde la fila se toca con un clic y el panel de al
 * lado ya muestra el detalle. En el teléfono el gesto es horizontal y la lista vertical: no se pisan.
 * El toque no cambia, porque un arrastre que no pasa del umbral de arrastre sigue siendo un toque.
 */
@Composable
internal fun FilaDeslizable(
    habilitada: Boolean,
    puedeConfirmar: Boolean,
    onCategoria: () -> Unit,
    onConfirmar: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!habilitada) {
        Box(modifier) { content() }
        return
    }
    val reducido = LocalMovimientoReducido.current
    var desplazamiento by remember { mutableFloatStateOf(0f) }
    var ancho by remember { mutableIntStateOf(0) }
    val estado = rememberDraggableState { delta ->
        val tope = ancho.toFloat()
        desplazamiento = (desplazamiento + delta).coerceIn(-tope, if (puedeConfirmar) tope else 0f)
    }
    Box(modifier = modifier.onSizeChanged { ancho = it.width }) {
        if (desplazamiento != 0f) {
            val aLaIzquierda = desplazamiento < 0f
            val pasoElUmbral = abs(desplazamiento) >= ancho * UMBRAL_DE_LA_FILA_DESLIZADA
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .testTag(TAG_FONDO_DE_FILA_DESLIZADA)
                    .clip(RoundedCornerShape(Radio.s))
                    // Antes del umbral, la capa 2; pasado, el lavado de su color: «suelta y se hace».
                    .background(
                        if (!pasoElUmbral) Movi.colores.capa2
                        else if (aLaIzquierda) Movi.colores.lavado(Movi.colores.marca)
                        else Movi.colores.lavado(Movi.colores.entra),
                    )
                    .padding(horizontal = Medida.margenDePantalla),
                horizontalArrangement = if (aLaIzquierda) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icono, rotulo, color) = if (aLaIzquierda) {
                    Triple(Icons.Rounded.Category, "Categoría", Movi.colores.marca)
                } else {
                    Triple(Icons.Rounded.Check, "Confirmar", Movi.colores.entra)
                }
                Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.m))
                Spacer(Modifier.width(Espacio.xs))
                Text(rotulo, style = Movi.textos.cuerpo, color = color)
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(desplazamiento.roundToInt(), 0) }
                // El fondo de la tarjeta, para que lo revelado quede DETRÁS y no se lea a través.
                .background(Movi.colores.tarjeta)
                .draggable(
                    state = estado,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when (alSoltarLaFila(desplazamiento, ancho, puedeConfirmar)) {
                            AlSoltarLaFila.Categoria -> onCategoria()
                            AlSoltarLaFila.Confirmar -> onConfirmar()
                            AlSoltarLaFila.Nada -> Unit
                        }
                        animate(desplazamiento, 0f, animationSpec = Movimiento.alVolver(reducido)) { valor, _ ->
                            desplazamiento = valor
                        }
                    },
                ),
        ) { content() }
    }
}
