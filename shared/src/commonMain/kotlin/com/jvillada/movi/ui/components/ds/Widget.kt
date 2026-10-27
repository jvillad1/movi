package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** Tag estable de un [Widget]. */
const val TAG_WIDGET: String = "ds-widget"

/**
 * **Un widget del Inicio**: un bloque compacto que **resume** una cifra que vive en otra pantalla y
 * lleva allá (camino 1: «Falta por pagar» lleva a Plan, «En qué se va» a su pantalla). Es una
 * [Superficie] tocable entera con su título arriba, un [detalle] opcional a la derecha («4 de 7»,
 * «período») y lo que el widget resume debajo. **El título nunca se pierde**: se mide primero, y el
 * detalle se queda con lo que sobra (y se corta con «…» si no cabe).
 *
 * El título no es una [Seccion]: el widget es una tarjeta dentro de la sección, y dos van lado a lado
 * ([ParDeWidgets]). La regla de la casa sigue: el widget no repite la cifra con otra suma al lado, la
 * nombra y lleva a su casa.
 *
 * ```
 * Widget(titulo = "Falta por pagar", detalle = "4 de 7", onClick = { abrirPlan() }) {
 *     Cifra(4_312_000, VarianteDeCifra.Grande)
 * }
 * ```
 */
@Composable
fun Widget(
    titulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Superficie(
        modifier = modifier.testTag(TAG_WIDGET),
        onClick = onClick,
        onClickLabel = "Ver $titulo",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            // El título se mide primero y el detalle se queda con lo que sobra (vuelta de diseño 7): a
            // media pantalla, «vs. período pasado» a la derecha se comía «En qué se va» entero.
            Text(
                text = titulo,
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            content = contenido,
        )
    }
}

/**
 * **Dos widgets lado a lado, del mismo alto.** El alto lo pone el más alto de los dos; el otro se
 * estira, así la fila no queda escalonada. Entre los dos, [Medida.dentroDeSeccion].
 *
 * Si uno solo tiene algo que mostrar, quien llama lo pinta solo y a todo el ancho: un hueco al lado no
 * es un widget.
 */
@Composable
fun ParDeWidgets(
    izquierdo: @Composable (Modifier) -> Unit,
    derecho: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
    ) {
        izquierdo(Modifier.weight(1f).fillMaxHeight())
        derecho(Modifier.weight(1f).fillMaxHeight())
    }
}
