package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import com.jvillada.movi.ui.components.ds.seAchicaHastaLaBase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.anilloDeFoco

/**
 * **Los colores de un segmento** (vuelta 29): elegido, la tinta con la letra blanca —el único
 * «seleccionado» de Movi—; sin elegir, transparente sobre el riel con el texto medio. Pura.
 */
fun colorDelSegmento(elegido: Boolean, colores: ColoresDeMovi): Pair<Color, Color> =
    if (elegido) colores.texto to colores.tarjeta else Color.Transparent to colores.textoMedio

/**
 * **El selector segmentado de Movi**: una fila de pastillas a todo el ancho, una elegida.
 *
 * Nació como el selector de arriba de la hoja de «Agregar» (Gasto · Ingreso · Traspaso · Pagar), donde
 * elige entre formularios distintos —por eso lo dibuja quien decide cuál se muestra, afuera del
 * cuerpo de la hoja—. Ola C: la pestaña Plan elige con el mismo entre «Pagos del mes» y
 * «Presupuestos», así que se mudó acá en vez de copiarse.
 */
@Composable
fun SelectorSegmentado(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radio.pill))
            .background(Movi.colores.tarjeta)
            .border(Medida.trazo, Movi.colores.borde, RoundedCornerShape(Radio.pill))
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isActive = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .anilloDeFoco(RoundedCornerShape(Radio.pill), adentro = true)
                    .clip(RoundedCornerShape(Radio.pill))
                    // El elegido es una pastilla llena, no solo otro color de letra: en Plan es el
                    // control principal. Vuelta 29: **en tinta con la letra blanca**, el mismo
                    // «seleccionado» del filtro elegido y de la casilla; el amarillo queda solo para la
                    // acción principal. El blanco sobre la tinta lo mide `ContrasteDeLosTokensTest`.
                    // Solo cambian colores: el alto es el mismo, nada salta al elegir.
                    .background(colorDelSegmento(isActive, Movi.colores).first)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = Espacio.xs),
                contentAlignment = Alignment.Center,
            ) {
                // Vuelta 56: con la letra al 130 % de verdad (×1,456), «Traspaso» no cabía en su
                // cuarto: se achica hasta la letra de siempre ([seAchicaHastaLaBase]), sin partirse.
                val estilo = Movi.textos.cuerpo.copy(color = colorDelSegmento(isActive, Movi.colores).second, letterSpacing = 0.1.sp)
                BasicText(
                    text = label,
                    style = estilo,
                    autoSize = seAchicaHastaLaBase(estilo),
                    // Una sola línea SIEMPRE. Con cuatro segmentos, cada uno se queda con ~82 dp
                    // en un teléfono de 375 px: «Traspaso» a 13 sp mide ~55, pero con la escala de
                    // fuente del sistema al 2× se pasa y envuelve, lo que crece la fila del
                    // selector y corre el formulario entero hacia abajo. Con tres segmentos el
                    // umbral estaba más lejos; con cuatro, no.
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
