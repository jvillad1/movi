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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi

/**
 * **El selector segmentado de Movi**: una fila de pastillas a todo el ancho, una elegida.
 *
 * Nació como el selector de arriba de la hoja de «Agregar» (Gasto · Ingreso · Traspaso · Cuota), donde
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
            .clip(RoundedCornerShape(999.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isActive = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    // El elegido es una pastilla llena de marca, no solo otro color de letra: en
                    // Plan es el control principal, y con el fondo igual al del contenedor no se
                    // veía cuál estaba elegido. `sobreMarca` sobre `marca` y `marca` sobre
                    // `tarjeta` ya los vigila `ContrasteDeLosTokensTest` en los dos temas. Solo
                    // cambian colores: el alto es el mismo, nada salta al elegir.
                    .background(if (isActive) Movi.colores.marca else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) Movi.colores.sobreMarca else Movi.colores.textoMedio,
                    letterSpacing = 0.1.sp,
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
