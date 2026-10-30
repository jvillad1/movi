package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento

/*
 * # Encabezados que se separan al hacer scroll (vuelta 79)
 *
 * La revisión visual 7: los encabezados fijos (la franja amarilla de Inicio, los chips de Actividad, el
 * título de Pagos) cortaban el contenido en seco —las filas pasaban por debajo sin nada que dijera dónde
 * termina lo fijo—. Ahora, con el contenido desplazado, lo fijo lleva el fondo de la pantalla (`fondo`)
 * y **un hilo de [Medida.trazo] abajo**, que entran con una transición corta ([Movimiento.CORTO_MS]; en
 * el acto con menos movimiento). Arriba de todo, sin hilo: nada que separar.
 *
 * La banda amarilla de Inicio usa lo mismo con `arriba = marca`: al bajar se vuelve crema. **El amarillo es
 * de arriba, no del chrome.**
 */

/** ¿El encabezado se separó porque el contenido se desplazó? En su semántica, para probarlo sin píxeles. */
val EncabezadoDesplazadoKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("EncabezadoDesplazado")

/** Ver [EncabezadoDesplazadoKey]. */
var SemanticsPropertyReceiver.encabezadoDesplazado: Boolean by EncabezadoDesplazadoKey

/**
 * **El fondo de lo fijo**: [arriba] mientras el contenido está arriba de todo (transparente: el de la
 * pantalla; la marca en la banda de Inicio) y el `fondo` de la pantalla con el contenido desplazado. Pura.
 */
fun fondoDelEncabezado(desplazado: Boolean, arriba: Color, colores: com.jvillada.movi.theme.ColoresDeMovi): Color =
    if (desplazado) colores.fondo else arriba

/**
 * **El hilo de abajo de un encabezado desplazado** (vuelta 82). Era el `hilo` (`#EFEAE1`), que sobre el
 * crema da ~1,06:1: la revisión 8 no lo vio. Ahora es el borde de los controles (`bordeDeBoton`,
 * `#D9D1C3`), que da 1,34:1 sobre el `fondo` —el piso de 1,3:1 que `ContrasteDeLosTokensTest` mide para
 * lo que separa sin ser texto—. El `borde` (`#E7E1D7`) daba 1,15:1, por debajo del piso. Pura.
 */
fun colorDelHiloAlDesplazar(colores: com.jvillada.movi.theme.ColoresDeMovi): Color = colores.bordeDeBoton

/**
 * El fondo y el hilo de abajo de un encabezado fijo según [desplazado] (ver el KDoc del archivo).
 * [arriba] es el fondo cuando no hay scroll. Sin [conHilo], solo el fondo (lo de detrás de la barra del
 * sistema, que va arriba del encabezado: el hilo es del encabezado).
 */
fun Modifier.separadoAlDesplazar(
    desplazado: Boolean,
    arriba: Color = Color.Transparent,
    conHilo: Boolean = true,
): Modifier = composed {
    val reducido = LocalMovimientoReducido.current
    val colores = Movi.colores
    val fondo by animateColorAsState(
        fondoDelEncabezado(desplazado, arriba, colores),
        Movimiento.alVolver(reducido, Movimiento.CORTO_MS),
        label = "fondo-del-encabezado",
    )
    val hilo by animateFloatAsState(
        if (desplazado && conHilo) 1f else 0f,
        Movimiento.alVolver(reducido, Movimiento.CORTO_MS),
        label = "hilo-del-encabezado",
    )
    val colorDelHilo = colorDelHiloAlDesplazar(colores)
    this
        .semantics { encabezadoDesplazado = desplazado }
        .background(fondo)
        .drawWithContent {
            drawContent()
            if (hilo > 0f) {
                val trazo = Medida.trazo.toPx()
                drawRect(
                    colorDelHilo,
                    topLeft = Offset(0f, size.height - trazo),
                    size = Size(size.width, trazo),
                    alpha = hilo,
                )
            }
        }
}

/** ¿Una lista se desplazó? (lo primero que se ve ya no es su arriba de todo). */
@Composable
fun desplazadoDe(lista: LazyListState): Boolean {
    val desplazado by remember(lista) {
        derivedStateOf { lista.firstVisibleItemIndex > 0 || lista.firstVisibleItemScrollOffset > 0 }
    }
    return desplazado
}

/** ¿Un scroll se desplazó? */
@Composable
fun desplazadoDe(scroll: ScrollState): Boolean {
    val desplazado by remember(scroll) { derivedStateOf { scroll.value > 0 } }
    return desplazado
}
