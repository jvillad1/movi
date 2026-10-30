package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import com.jvillada.movi.theme.Movimiento
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio

/**
 * Cuánto sale hacia los lados el resaltado de un renglón que se toca y va **alineado con el borde** de
 * lo que tiene debajo (el encabezado de un día en Movimientos, alineado con su `Superficie`).
 */
val SALIDA_DEL_RESALTADO: Dp = Espacio.xs

/** El radio del resaltado de [renglonTocable]: el de una fila destacada. */
val RADIO_DEL_RESALTADO: Dp = Radio.m

/**
 * **Un renglón que se toca y no vive dentro de una superficie** (vuelta de diseño 16): el encabezado
 * de un día en Movimientos, que pliega el día. Su texto va alineado con el borde de la `Superficie` de
 * abajo, así que un `clickable` a secas resaltaba —con el mouse encima, en escritorio— una banda gris
 * recta pegada al texto, sin aire ni radio, mientras que una [Fila] resalta dentro de su superficie.
 *
 * Con esto el resaltado sale [SALIDA_DEL_RESALTADO] hacia cada lado (dentro del margen de la pantalla,
 * que es el doble) con [RADIO_DEL_RESALTADO], y el contenido **se queda donde estaba**: el renglón
 * mide lo mismo para quien lo pone, y nada se corre.
 *
 * ```
 * Row(Modifier.fillMaxWidth().renglonTocable(onClickLabel = "Plegar el día", onClick = ::plegar)) { … }
 * ```
 */
fun Modifier.renglonTocable(onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    this
        .salirHaciaLosLados(SALIDA_DEL_RESALTADO)
        .anilloDeFoco(RoundedCornerShape(RADIO_DEL_RESALTADO))
        // Vuelta 79: sin ripple; el resaltado es el velo, con su radio, y se hunde apenas.
        .alPresionar(
            onClick = onClick,
            onClickLabel = onClickLabel,
            escala = Movimiento.ESCALA_DE_LA_FILA_AL_PRESIONAR,
            velo = true,
            forma = RoundedCornerShape(RADIO_DEL_RESALTADO),
        )
        .clip(RoundedCornerShape(RADIO_DEL_RESALTADO))
        .padding(horizontal = SALIDA_DEL_RESALTADO)

/**
 * Mide lo de adentro [salida] más ancho por cada lado y lo pone corrido hacia la izquierda, pero le
 * dice a quien lo contiene que mide lo de siempre: lo que va después (el clip, el toque) ocupa el aire
 * de los lados sin empujar a nadie.
 */
private fun Modifier.salirHaciaLosLados(salida: Dp): Modifier = layout { medible, restricciones ->
    val lado = salida.roundToPx()
    val maximo = if (restricciones.hasBoundedWidth) restricciones.maxWidth + 2 * lado else Constraints.Infinity
    val colocable = medible.measure(
        restricciones.copy(minWidth = restricciones.minWidth + 2 * lado, maxWidth = maximo),
    )
    layout((colocable.width - 2 * lado).coerceAtLeast(0), colocable.height) {
        colocable.place(-lado, 0)
    }
}
