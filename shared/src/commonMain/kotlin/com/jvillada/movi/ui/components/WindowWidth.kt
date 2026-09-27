package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La clase de ancho de la ventana, decidida UNA vez por [CascaraDeAncho] (en `App.kt`) y entregada
 * a toda la app por [LocalWindowWidthClass]. Ver [claseDeAncho] para los bordes.
 *
 * - [Compact] (< 600 dp): el teléfono. Una sola barra inferior ([MinBottomNav]) y el contenido de
 *   borde a borde.
 * - [Medium] (600–999 dp): tablet vertical, teléfono horizontal, ventana angosta de escritorio. Rail
 *   compacto a la izquierda ([MinNavRail] con `compacto = true`), sin barra inferior.
 * - [Expanded] (≥ 1000 dp): escritorio. El rail ancho de siempre.
 *
 * Cuánto mide el contenido en cada clase ya no lo decide la clase sola: lo decide junto con la
 * [Disposicion] de la pantalla (ver [anchoMaximoDe]).
 */
enum class WindowWidthClass { Compact, Medium, Expanded }

/**
 * El valor por defecto sigue siendo [WindowWidthClass.Compact] a propósito: las pruebas Robolectric
 * montan pantallas sueltas, sin la cáscara, y tienen que seguir viendo el teléfono de siempre. Para
 * montar con la clase real de un ancho, las pruebas usan [CascaraDeAncho].
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }

/** Por debajo de este ancho la ventana es un teléfono. */
val ANCHO_DEL_MEDIANO: Dp = 600.dp

/** Desde este ancho la ventana es un escritorio. */
val ANCHO_DEL_EXPANDIDO: Dp = 1_000.dp

/**
 * La clase de ancho de una ventana de [ancho]: menos de 600 dp, [WindowWidthClass.Compact]; menos
 * de 1000, [WindowWidthClass.Medium]; si no, [WindowWidthClass.Expanded].
 *
 * Hasta la Ola W1 había dos clases con el corte en 840 dp: entre 600 y 840 (una tablet vertical,
 * una ventana de escritorio a medio ancho) se veía el teléfono estirado, con la barra inferior
 * cruzando 800 dp de pantalla.
 */
fun claseDeAncho(ancho: Dp): WindowWidthClass = when {
    ancho < ANCHO_DEL_MEDIANO -> WindowWidthClass.Compact
    ancho < ANCHO_DEL_EXPANDIDO -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

/**
 * Mide el ancho que le dan, decide la clase ([claseDeAncho]) y se la entrega a [content] por
 * [LocalWindowWidthClass]. Es la raíz de `App.kt`; las pruebas la usan para montar una pantalla con
 * la clase que le correspondería de verdad a `w768dp`, `w1280dp` o `w1920dp`.
 */
@Composable
fun CascaraDeAncho(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val clase = claseDeAncho(maxWidth)
        CompositionLocalProvider(LocalWindowWidthClass provides clase) { content() }
    }
}
