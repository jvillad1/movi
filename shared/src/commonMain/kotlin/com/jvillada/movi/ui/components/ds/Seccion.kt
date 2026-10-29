package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import androidx.compose.foundation.shape.RoundedCornerShape

/** Tag estable del título de una sección. */
const val TAG_SECCION: String = "ds-seccion"

/**
 * **El título de una sección. La única forma de titular una.**
 *
 * La auditoría encontró cinco estilos de título de sección: el rótulo en mayúsculas de
 * `MinSectionHeader` (10,5 sp, espaciado 1,7), otro en mayúsculas con `apoyo` y espaciado 0,5
 * (Cuentas), otro con 0,3 (los datos de un SMS), otro con 0,4 (los días de Movimientos) y títulos
 * en `cuerpo` o `titulo` armados a mano (Plan). Ahora es uno: el del camino 1, en minúsculas como
 * se escribe, en [com.jvillada.movi.theme.TextosDeMovi.titulo], con el conteo opcional en gris
 * («Tarjetas · 3») y una acción opcional a la derecha («Ver todo»).
 *
 * Lleva [Medida.dentroDeSeccion] debajo, hasta lo que titula: la pantalla no agrega nada entre el
 * título y su contenido. Entre una sección y la siguiente va [Medida.entreSecciones], y eso sí lo
 * pone la pantalla (es su lista, no la sección).
 *
 * El toque de la acción no agranda la fila: Compose extiende el área de toque de un elemento
 * chico hasta [Medida.objetivoTactil] sin ocupar lugar, así que el título mide lo mismo con
 * acción o sin ella y nada salta cuando la acción aparece.
 *
 * ```
 * Seccion(titulo = "Tarjetas", conteo = 3, accion = "Ver todo", onAccion = { … })
 * ```
 */
@Composable
fun Seccion(
    titulo: String,
    modifier: Modifier = Modifier,
    conteo: Int? = null,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_SECCION)
            .padding(bottom = Medida.dentroDeSeccion),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titulo,
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (conteo != null) {
                Text(
                    text = " · $conteo",
                    style = Movi.textos.tituloRegular,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                )
            }
        }
        if (accion != null) {
            Text(
                text = accion,
                style = Movi.textos.cuerpo,
                // El azul del enlace: el amarillo de la marca no se lee como letra (vuelta 29).
                color = Movi.colores.enlace,
                maxLines = 1,
                modifier = if (onAccion != null) {
                    Modifier
                        .anilloDeFoco(RoundedCornerShape(Radio.s))
                        .clickable(role = Role.Button, onClick = onAccion)
                } else {
                    Modifier
                },
            )
        }
    }
}
