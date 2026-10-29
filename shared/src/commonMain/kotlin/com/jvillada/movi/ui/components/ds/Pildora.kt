package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/**
 * **Lo elegido, en todo Movi** (vuelta 29): la tinta de fondo y la letra blanca. Lo usan el filtro
 * elegido, el segmentado, la casilla y cada selector armado en una pantalla (el día, la moneda, la
 * opción de una hoja): un solo lenguaje para «seleccionado». El amarillo es solo de la acción
 * principal y el azul, de un enlace. Pura.
 */
fun coloresDeLoElegido(colores: ColoresDeMovi): Pair<Color, Color> = colores.texto to colores.tarjeta

/**
 * **El fondo de una fila elegida en una lista** (la cuenta elegida de una lista, el movimiento abierto
 * al lado en pantalla ancha): el lavado de la tinta, con el texto en tinta encima. Una fila entera en
 * tinta pesaría demasiado. Pura.
 */
fun fondoDeLaFilaElegida(colores: ColoresDeMovi): Color = colores.lavado(colores.texto)

/** Tag estable de una [Pildora]. */
const val TAG_PILDORA: String = "ds-pildora"

/** Tag estable de una [PildoraDeFiltro]. */
const val TAG_PILDORA_DE_FILTRO: String = "ds-pildora-de-filtro"

/** Tag estable de una [PildoraDeAccion]. */
const val TAG_PILDORA_DE_ACCION: String = "ds-pildora-de-accion"

/**
 * **Qué dice una píldora.** Los colores de estado van **solo** para estado: una píldora en `Aviso`
 * dice «por vencer», «por confirmar» o «en curso», no «esto es importante».
 *
 * **No hay píldora amarilla** (vuelta 42). Había un tono `Marca`, lleno de amarillo, que terminó
 * pintando estados («En curso» en Tus períodos, «Por confirmar» en Pagos): el amarillo lleno es solo
 * de la acción principal (el `Boton` `Principal`). Lo que iba ahí va en [Aviso] (el tinte con el ocre).
 * Lo vigila `ElAmarilloEsDeLaAccionTest`.
 */
enum class TonoDePildora { Neutra, Info, Aviso, Peligro, Exito }

/**
 * El fondo y el texto de un tono, en la paleta del tema. Pura, para probarla.
 *
 * Opción 3 (vuelta 29): la neutra en la capa 3 (`#F1EDE6` en la maqueta); la de aviso en el tinte
 * con su ocre (`#FFF2C6` / `#6B4A00`, «Vence el martes»). Las demás, el color sobre su lavado.
 */
fun coloresDeLaPildora(tono: TonoDePildora, colores: ColoresDeMovi): Pair<Color, Color> {
    val color = when (tono) {
        TonoDePildora.Neutra -> return colores.capa3 to colores.textoMedio
        TonoDePildora.Info -> colores.entreCuentas
        TonoDePildora.Aviso -> colores.aviso
        TonoDePildora.Peligro -> colores.sale
        TonoDePildora.Exito -> colores.entra
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
        Text(text = texto, style = Movi.textos.apoyoMedio, color = color, maxLines = 1)
    }
}

/**
 * **El alto de un [PildoraDeFiltro]** (vuelta 29). [Normal] es [Medida.altoDeFiltro] (44, el de la
 * opción 3); [Compacto] es [Medida.altoChico] (36) para una fila de chips **dentro de una hoja anclada
 * abajo**, donde cada dp de más empuja el pie fuera de la pantalla (las categorías de Agregar, a 360 ×
 * 740). El toque llega a 48 en los dos.
 */
enum class AltoDeFiltro { Normal, Compacto }

/**
 * **Un filtro que se elige** (un chip): «Todo · Gastos · Ingresos». Opción 3 (vuelta 29): elegido
 * va **lleno de tinta con la letra blanca**; sin elegir, blanco con el borde de píldora.
 *
 * Alto [Medida.altoDeFiltro] (44, el de la maqueta), con el toque extendido a
 * [Medida.objetivoTactil]. El ancho no cambia al elegirlo (cambian los colores y el peso, y el
 * peso cambia poco el ancho de Figtree), así que una fila de filtros no baila.
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
    alto: AltoDeFiltro = AltoDeFiltro.Normal,
) {
    val fondo = if (seleccionada) Movi.colores.texto else Movi.colores.tarjeta
    // El blanco de la tarjeta sobre la tinta: `ContrasteDeLosTokensTest` mide el par.
    val color = if (seleccionada) Movi.colores.tarjeta else Movi.colores.texto
    val forma = RoundedCornerShape(Radio.pill)
    Row(
        modifier = modifier
            .height(if (alto == AltoDeFiltro.Compacto) Medida.altoChico else Medida.altoDeFiltro)
            .anilloDeFoco(forma)
            .clip(forma)
            .background(fondo)
            .then(if (seleccionada) Modifier else Modifier.border(Medida.trazo, Movi.colores.bordeDePildora, forma))
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
        Text(text = texto, style = if (seleccionada) Movi.textos.cuerpoFuerte else Movi.textos.cuerpo, color = color, maxLines = 1)
    }
}

/**
 * **Una píldora que hace algo al tocarla** (vuelta de diseño 12): las preguntas para seguir la
 * conversación debajo de una respuesta de Movi AI. Se ve como un [PildoraDeFiltro] sin elegir —blanca
 * con el borde de píldora, el texto en `cuerpo`, ícono opcional en el azul del enlace— pero es un
 * botón, no algo que se elige: no dice «elegido» ni «pestaña».
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
    val forma = RoundedCornerShape(Radio.pill)
    Row(
        modifier = modifier
            .heightIn(min = if (compacta) Medida.altoDePildora else Medida.altoChico)
            .anilloDeFoco(forma)
            .clip(forma)
            .background(Movi.colores.tarjeta)
            .border(Medida.trazo, Movi.colores.bordeDePildora, forma)
            .clickable(enabled = habilitada, role = Role.Button, onClick = onClick)
            .then(if (descripcion != null && descripcion != texto) Modifier.semantics { contentDescription = descripcion } else Modifier)
            .testTag(TAG_PILDORA_DE_ACCION)
            .padding(start = if (inicio != null) Espacio.xxs else horizontal, end = horizontal)
            .then(if (compacta) Modifier else Modifier.padding(vertical = Espacio.xxs)),
        horizontalArrangement = Arrangement.spacedBy(if (compacta) Espacio.xxs else Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inicio?.invoke()
        if (icono != null) Icon(icono, contentDescription = null, tint = Movi.colores.enlace, modifier = Modifier.size(Icono.s))
        // Sin `weight`: dentro de una fila con desplazamiento horizontal (los chips de Agregar) el
        // ancho máximo es infinito, y un hijo con peso se quedaba en cero —«+ Nueva» se veía como
        // un «+» y una «N»—. El texto mide lo suyo, como el de [PildoraDeFiltro].
        Text(
            text = texto,
            style = if (compacta) Movi.textos.apoyoMedio else Movi.textos.cuerpo,
            color = if (habilitada) Movi.colores.texto else Movi.colores.textoApagado,
            maxLines = if (compacta) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (iconoFinal != null) Icon(iconoFinal, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(Icono.s))
    }
}
