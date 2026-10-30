package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento

/*
 * # El interruptor de Movi (vuelta 82)
 *
 * La revisión 8 («Ocultar los saldos» en Tú): el interruptor apagado **parecía deshabilitado** —el riel
 * blanco con un borde casi invisible y la perilla en el texto apagado, el gris de lo que no se puede
 * tocar—. Ahora, apagado, es **un control que espera**: el riel en la capa 3 con su borde en el gris de
 * los controles (`neutro`, ≥ 3:1 sobre la tarjeta y el crema, el piso de WCAG para el contorno de un
 * control) y la perilla **blanca** con una sombra de 1 dp. Encendido, el riel en la tinta (el
 * «seleccionado» de la app, vuelta 29) y la perilla blanca. Disabled se dice aparte: todo a media tinta.
 * `ContrasteDeLosTokensTest` mide los pares.
 *
 * Es **el** interruptor de la app: Tú (Ajustes) y «Editar inicio» lo usan. Se toca con
 * [alAlternar] (sin ripple, se hunde), con el rol de interruptor para el lector de pantalla.
 */

/** El tag del interruptor. */
const val TAG_INTERRUPTOR: String = "ds:interruptor"

/** ¿Está encendido? En su semántica, además del estado de `toggleable`, para leerlo sin píxeles. */
val InterruptorEncendidoKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("InterruptorEncendido")

/** Ver [InterruptorEncendidoKey]. */
var SemanticsPropertyReceiver.interruptorEncendido: Boolean by InterruptorEncendidoKey

/** Los colores de un interruptor: el riel, su borde y la perilla. */
data class ColoresDelInterruptor(val riel: Color, val borde: Color, val perilla: Color)

/**
 * **Los colores del interruptor** en [encendido] o apagado. Apagado: el riel en la capa 3, el borde en
 * `neutro` y la perilla blanca (la tarjeta). Encendido: el riel y su borde en la tinta, la perilla blanca.
 * Pura.
 */
fun coloresDelInterruptor(encendido: Boolean, colores: ColoresDeMovi): ColoresDelInterruptor =
    if (encendido) ColoresDelInterruptor(riel = colores.texto, borde = colores.texto, perilla = colores.tarjeta)
    else ColoresDelInterruptor(riel = colores.capa3, borde = colores.neutro, perilla = colores.tarjeta)

/** La opacidad de un interruptor que no se puede tocar. */
const val ALFA_DEL_INTERRUPTOR_DESHABILITADO: Float = 0.38f

/**
 * **El interruptor** (ver el KDoc del archivo). [descripcion] es lo que dice el lector de pantalla cuando
 * el interruptor no va dentro de una fila que ya lo nombra («Mostrar Disponible» en «Editar inicio»).
 */
@Composable
fun Interruptor(
    encendido: Boolean,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    descripcion: String? = null,
) {
    val reducido = LocalMovimientoReducido.current
    val objetivo = coloresDelInterruptor(encendido, Movi.colores)
    val riel by animateColorAsState(objetivo.riel, Movimiento.alVolver(reducido, Movimiento.CORTO_MS), label = "riel")
    val borde by animateColorAsState(objetivo.borde, Movimiento.alVolver(reducido, Movimiento.CORTO_MS), label = "borde")
    val hueco = (Medida.altoDelInterruptor - Medida.perillaDelInterruptor) / 2
    val recorrido = Medida.anchoDelInterruptor - Medida.perillaDelInterruptor - hueco * 2
    val x by animateDpAsState(if (encendido) recorrido else Dp.Hairline, Movimiento.alVolver(reducido, Movimiento.CORTO_MS), label = "perilla")
    val forma = CircleShape
    // Revisión de la vuelta 82: el riel mide 52 × 32, pero lo que se toca mide al menos 48 de alto (el
    // objetivo táctil de la app): en «Editar inicio» va suelto junto a las flechas. Lo que se ve no cambia.
    Box(
        modifier = modifier
            // El anillo va alrededor del riel (52 × 32), no de la caja del toque (52 × 48).
            .anilloDeFoco(forma, alrededorDeLaCaja = DpSize(Medida.anchoDelInterruptor, Medida.altoDelInterruptor))
            .alAlternar(valor = encendido, onCambiar = onCambio, habilitado = habilitado, rol = Role.Switch, forma = forma)
            .semantics {
                interruptorEncendido = encendido
                descripcion?.let { contentDescription = it }
            }
            .testTag(TAG_INTERRUPTOR)
            .sizeIn(minWidth = Medida.objetivoTactil, minHeight = Medida.objetivoTactil),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = Medida.anchoDelInterruptor, height = Medida.altoDelInterruptor)
                .alpha(if (habilitado) 1f else ALFA_DEL_INTERRUPTOR_DESHABILITADO)
                .background(riel, forma)
                .border(Medida.trazoDeBoton, borde, forma)
                .padding(hueco),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .size(Medida.perillaDelInterruptor)
                    .shadow(Medida.sombraDeLaPerilla, forma, clip = false, ambientColor = Movi.colores.sombra, spotColor = Movi.colores.sombra)
                    .background(objetivo.perilla, forma),
            )
        }
    }
}
