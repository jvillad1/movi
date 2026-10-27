package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/** Tag estable de una [Pildora]. */
const val TAG_PILDORA: String = "ds-pildora"

/** Tag estable de una [PildoraDeFiltro]. */
const val TAG_PILDORA_DE_FILTRO: String = "ds-pildora-de-filtro"

/** Tag estable de una [PildoraDeAccion]. */
const val TAG_PILDORA_DE_ACCION: String = "ds-pildora-de-accion"

/**
 * **Qué dice una píldora.** Los colores de estado van **solo** para estado: una píldora en `Aviso`
 * dice «por vencer» o «por confirmar», no «esto es importante». La marca va solo para lo que es de
 * Movi (una propuesta, algo nuevo).
 */
enum class TonoDePildora { Neutra, Info, Aviso, Peligro, Exito, Marca }

/** El fondo y el texto de un tono, en la paleta del tema. Pura, para probarla en los dos. */
fun coloresDeLaPildora(tono: TonoDePildora, colores: ColoresDeMovi): Pair<Color, Color> {
    val color = when (tono) {
        TonoDePildora.Neutra -> return colores.capa2 to colores.textoMedio
        TonoDePildora.Info -> colores.entreCuentas
        TonoDePildora.Aviso -> colores.aviso
        TonoDePildora.Peligro -> colores.sale
        TonoDePildora.Exito -> colores.entra
        TonoDePildora.Marca -> colores.marca
    }
    return colores.lavado(color) to color
}

/**
 * **Una píldora de estado**: «Vence en 2 días», «Pagado», «Nuevo». No se toca: para elegir está
 * [PildoraDeFiltro]. Alto [Medida.altoDePildora], radio [Radio.pill], texto en `apoyo`.
 *
 * ```
 * Pildora("Vence en 2 días", TonoDePildora.Aviso)
 * ```
 */
@Composable
fun Pildora(
    texto: String,
    tono: TonoDePildora = TonoDePildora.Neutra,
    modifier: Modifier = Modifier,
) {
    val (fondo, color) = coloresDeLaPildora(tono, Movi.colores)
    Row(
        modifier = modifier
            .height(Medida.altoDePildora)
            .clip(RoundedCornerShape(Radio.pill))
            .background(fondo)
            .testTag(TAG_PILDORA)
            .padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = texto, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

/**
 * **Un filtro que se elige** (un chip): «Todo · Gastos · Ingresos». Elegido va lleno de marca
 * —es una acción, y la marca es el color de la acción—; sin elegir, en la capa 2.
 *
 * Alto [Medida.altoChico], con el toque extendido a [Medida.objetivoTactil]. El ancho no cambia al
 * elegirlo (solo cambian los colores), así que una fila de filtros no baila.
 *
 * ```
 * PildoraDeFiltro("Gastos", seleccionada = filtro == Gastos, onClick = { filtro = Gastos })
 * ```
 *
 * [inicio] (vuelta de diseño 3) es lo que va antes del texto cuando un ícono no alcanza: el
 * avatar de la marca de un banco (`CirculoDeMarca` en `TamanoDeAvatar.Linea`, 20 dp) en la guía de
 * primer uso. Con él la píldora arranca a [Espacio.xs] del borde —un círculo pegado a la curva de
 * la píldora— en vez de [Espacio.m]; el alto no cambia.
 */
@Composable
fun PildoraDeFiltro(
    texto: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    inicio: (@Composable () -> Unit)? = null,
) {
    val fondo = if (seleccionada) Movi.colores.marca else Movi.colores.capa2
    val color = if (seleccionada) Movi.colores.sobreMarca else Movi.colores.texto
    Row(
        modifier = modifier
            .height(Medida.altoChico)
            .clip(RoundedCornerShape(Radio.pill))
            .background(fondo)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = seleccionada }
            .testTag(TAG_PILDORA_DE_FILTRO)
            .padding(start = if (inicio != null) Espacio.xs else Espacio.m, end = Espacio.m),
        // Centrado: una píldora que ocupa más que su texto (los atajos «Hoy · Ayer» del selector de
        // fecha, a medias del ancho) lo lleva al medio. Del ancho de su texto, no cambia nada.
        horizontalArrangement = Arrangement.spacedBy(if (inicio != null) Espacio.xs else Espacio.xxs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inicio?.invoke()
        if (icono != null) Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.s))
        Text(text = texto, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

/**
 * **Una píldora que hace algo al tocarla** (vuelta de diseño 12): las preguntas para seguir la
 * conversación debajo de una respuesta de Movi AI. Se ve como un [PildoraDeFiltro] sin elegir —capa
 * 2, el texto en `cuerpo`, ícono opcional en la marca— pero es un botón, no algo que se elige: no
 * dice «elegido» ni «pestaña».
 *
 * Alto mínimo [Medida.altoChico]; un texto largo baja a un segundo renglón en vez de cortarse (la
 * píldora crece). [descripcion] es lo que oye el lector de pantalla cuando el texto es una versión
 * corta («¿Cómo voy?» por «¿Cómo voy este período?»).
 *
 * ```
 * PildoraDeAccion("¿Cómo voy?", onClick = ::preguntar, icono = Icons.Rounded.AutoAwesome)
 * ```
 *
 * Vuelta de diseño 20: [inicio] es lo que va antes del texto cuando un ícono no alcanza —el ícono
 * de una categoría, en su color, en la hoja de un movimiento—, como en [PildoraDeFiltro]: con él la
 * píldora arranca a [Espacio.xxs] del borde. [iconoFinal] es la flecha de algo que se despliega
 * («▾» al lado de la categoría), en texto medio. [habilitada] en `false` apaga el texto y el toque.
 * Un texto que no entra en dos renglones se corta con «…».
 */
/**
 * **El alto de una [PildoraDeAccion]** (vuelta de diseño 20). [Normal] es [Medida.altoChico] con el
 * texto en `cuerpo`; [Compacta] es [Medida.altoDePildora] con el texto en `apoyo`, para una píldora
 * que va pegada a un renglón de texto y no puede agrandarlo —la fecha al lado del monto en Agregar,
 * una hoja anclada abajo donde cada dp de más mueve el teclado—. El toque igual llega a 48: Compose
 * extiende el área de un elemento chico sin ocupar lugar.
 */
enum class AltoDePildora { Normal, Compacta }

@Composable
fun PildoraDeAccion(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    descripcion: String? = null,
    inicio: (@Composable () -> Unit)? = null,
    iconoFinal: ImageVector? = null,
    habilitada: Boolean = true,
    alto: AltoDePildora = AltoDePildora.Normal,
) {
    val compacta = alto == AltoDePildora.Compacta
    val horizontal = if (compacta) Espacio.s else Espacio.m
    Row(
        modifier = modifier
            .heightIn(min = if (compacta) Medida.altoDePildora else Medida.altoChico)
            .clip(RoundedCornerShape(Radio.pill))
            .background(Movi.colores.capa2)
            .clickable(enabled = habilitada, role = Role.Button, onClick = onClick)
            .then(if (descripcion != null && descripcion != texto) Modifier.semantics { contentDescription = descripcion } else Modifier)
            .testTag(TAG_PILDORA_DE_ACCION)
            .padding(start = if (inicio != null) Espacio.xxs else horizontal, end = horizontal)
            .then(if (compacta) Modifier else Modifier.padding(vertical = Espacio.xxs)),
        horizontalArrangement = Arrangement.spacedBy(if (compacta) Espacio.xxs else Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inicio?.invoke()
        if (icono != null) Icon(icono, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(Icono.s))
        // Sin `weight`: dentro de una fila con desplazamiento horizontal (los chips de Agregar) el
        // ancho máximo es infinito, y un hijo con peso se quedaba en cero —«+ Nueva» se veía como
        // un «+» y una «N»—. El texto mide lo suyo, como el de [PildoraDeFiltro].
        Text(
            text = texto,
            style = if (compacta) Movi.textos.apoyo else Movi.textos.cuerpo,
            fontWeight = FontWeight.Medium,
            color = if (habilitada) Movi.colores.texto else Movi.colores.textoApagado,
            maxLines = if (compacta) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (iconoFinal != null) Icon(iconoFinal, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(Icono.s))
    }
}
