package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/** Tag estable: encuentra cualquier vacío que enseña en una prueba sin acoplarse al texto. */
const val TAG_VACIO_QUE_ENSENA: String = "vacio-que-ensena"

/**
 * # Ola D: el vacío que enseña
 *
 * Una regla de toda la app, cumplida acá una sola vez: **un vacío dice qué va a
 * aparecer ahí y trae el botón que lo llena**, nunca una pantalla en blanco ni un «$0» presentado
 * como un hecho para quien todavía no tiene nada. Antes cada pantalla escribía su propia versión
 * —con su propio tono, su propia tarjeta (o ninguna) y su propio botón—; esto es esa tarjeta,
 * escrita una vez.
 *
 * [accion]/[onAccion] van juntos o ninguno de los dos: un vacío sin acción (un chip que filtró
 * todo, una búsqueda sin resultados) solo explica, no ofrece nada que no exista. El botón reusa
 * [NewItemButton] en su forma `full = true` — la misma acción principal que ya usa el resto de la
 * app — en vez de inventar un estilo nuevo de botón acá.
 *
 * [detalle] es nullable a propósito: Movimientos tiene un vacío («Sin movimientos aún») que no
 * trae explicación —nunca la tuvo— y forzar un texto ahí sería inventar una frase que no existía
 * solo para poder usar este componente. Ver el KDoc de `vacioDeMovimientos` en
 * `TransactionsScreen.kt`.
 *
 * [icono] también es opcional: no todo vacío tenía uno (Movimientos no lo traía), y pasar un vacío
 * existente a esta tarjeta no tiene por qué cambiar cómo se ve más allá de la tarjeta misma.
 *
 * [contenido] va entre el detalle y el botón, adentro de la misma tarjeta: para el vacío que además
 * de explicar ya trae algo armado (Presupuestos propone categorías con lo que el dueño gastó) y
 * cuyas acciones van después de eso. Quien lo usa suele pasar `accion = null` y poner sus botones
 * ahí adentro.
 */
@Composable
fun VacioQueEnsena(
    titulo: String,
    detalle: String?,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    contenido: (@Composable () -> Unit)? = null,
) {
    MinCard(
        modifier = modifier.fillMaxWidth().testTag(TAG_VACIO_QUE_ENSENA),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icono != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Movi.colores.marca.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icono, contentDescription = null, tint = Movi.colores.marca)
                }
                Spacer(Modifier.height(Movi.espacios.medio))
            }
            Text(
                text = titulo,
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                textAlign = TextAlign.Center,
            )
            if (detalle != null) {
                Spacer(Modifier.height(Movi.espacios.corto))
                Text(
                    text = detalle,
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                )
            }
            if (contenido != null) {
                Spacer(Modifier.height(Movi.espacios.amplio))
                contenido()
            }
            if (accion != null && onAccion != null) {
                Spacer(Modifier.height(Movi.espacios.amplio))
                NewItemButton(label = accion, onClick = onAccion, full = true)
            }
        }
    }
}
