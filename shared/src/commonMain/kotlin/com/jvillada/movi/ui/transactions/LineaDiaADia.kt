package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/**
 * El tag de la línea «Día a día» de un día de Movimientos: para que las pruebas cuenten cuántas se
 * dibujan y midan el alto que se reserva.
 */
const val TAG_DIA_A_DIA: String = "dia-a-dia"

/**
 * La segunda línea del encabezado de un día, debajo del «Flujo del día»: lo que gastaste ese día
 * contra lo que podías (ver [LineaDelDiaADia]).
 *
 * @param linea lo que se dice; `null` = **reserva el alto de la línea sin dibujar nada**, para
 *   que cuando la lectura de la que sale la meta llegue la lista de abajo no salte.
 *
 * Es UN solo texto, y TalkBack lo lee entero: los colores son de vista, la frase es la misma.
 */
@Composable
internal fun LineaDelDiaADiaEnElDia(linea: LineaDelDiaADia?) {
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    if (linea == null) {
        // Un espacio mide exactamente una línea de la misma talla, sin dibujar nada.
        Text(" ", modifier = modifier, style = Movi.textos.apoyo, maxLines = 1)
        return
    }
    val colores = Movi.colores
    val dentro = !linea.pasada
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = if (dentro) colores.entra else colores.textoApagado)) { append(linea.base) }
            linea.aviso?.let { aviso ->
                withStyle(SpanStyle(color = colores.textoApagado)) { append(" · ") }
                withStyle(SpanStyle(color = colores.sale)) { append(aviso) }
            }
        },
        modifier = modifier
            .testTag(TAG_DIA_A_DIA)
            .semantics { contentDescription = linea.texto },
        style = Movi.textos.apoyo,
        textAlign = TextAlign.End,
    )
}
