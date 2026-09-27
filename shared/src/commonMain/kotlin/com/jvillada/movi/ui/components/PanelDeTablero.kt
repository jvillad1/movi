package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * # El panel de una pantalla-tablero (Ola W3)
 *
 * Patrimonio, Créditos y Plan son [Disposicion.Tablero]: la cáscara les da hasta 840 dp en una
 * ventana mediana y hasta 1.280 en escritorio. Este envoltorio **mide el ancho que de verdad les
 * quedó** —con `BoxWithConstraints`, ya descontado el rail, nunca calculado a mano— y le dice a
 * [contenido] si va en dos columnas, según [enDosColumnas] de ese ancho.
 *
 * - **Teléfono**: [contenido] con `false`, sin medir nada (no agrega un `SubcomposeLayout` a la
 *   pantalla que más se usa, y el teléfono nunca tiene lugar para dos columnas).
 * - **Dos columnas**: [contenido] llena el panel.
 * - **Una columna fuera del teléfono** (una ventana mediana, o una laptop angosta): [contenido]
 *   va centrado en la columna de lectura ([ANCHO_DE_LECTURA], 720 dp), como iba antes de esta ola.
 *   Una columna estirada a 840 o 1.000 dp no se lee mejor, se lee peor (ver [Disposicion]).
 *
 * Cambiar de una a dos columnas llama a [contenido] en el mismo lugar de la composición: el estado
 * de la pantalla sobrevive a que el usuario agrande la ventana.
 */
@Composable
fun PanelDeTablero(
    enDosColumnas: (anchoDelPanel: Dp) -> Boolean,
    contenido: @Composable (dosColumnas: Boolean) -> Unit,
) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        contenido(false)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val dos = enDosColumnas(maxWidth)
        val ancho = if (dos) Modifier.fillMaxSize() else Modifier.widthIn(max = ANCHO_DE_LECTURA).fillMaxSize()
        Box(modifier = ancho) { contenido(dos) }
    }
}
