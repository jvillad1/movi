package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** Cuántos renglones puede ocupar el título de una [Fila] antes de cortarse con «…». */
const val LINEAS_DEL_TITULO_DE_FILA: Int = 2

/** Tag estable de una [Fila]. */
const val TAG_FILA: String = "ds-fila"

/**
 * **La fila de lista. Una sola para toda la app.**
 *
 * La auditoría contó filas tocables con nueve rellenos verticales distintos (2, 5, 6, 8, 9, 10, 12,
 * 13 y 14 dp) y sin alto mínimo. Las de Movimientos, Plan, Patrimonio, Por revisar y Ajustes
 * convergen acá en las olas siguientes.
 *
 * - **Alto**: [Medida.altoDeFila] (56) con un renglón, [Medida.altoDeFilaDoble] (64) con
 *   [subtitulo]. Es un mínimo: con la letra del sistema agrandada la fila crece en vez de cortar.
 * - **Relleno**: [Medida.margenDePantalla] a los lados, siempre. Por eso va en una [Superficie]
 *   con [RellenoDeSuperficie.Lista] o directo sobre el fondo, nunca dentro de otro relleno.
 * - **Toque**: la fila entera, de borde a borde.
 *
 * Las ranuras:
 * - [inicio]: un avatar de comercio (`AvatarDelComercio`), un [IconoEnCirculo] o el avatar de una
 *   persona. Mide [Medida.avatar].
 * - [titulo]: hasta [LINEAS_DEL_TITULO_DE_FILA] renglones, y recién ahí «…»: un nombre largo
 *   («Bancolombia Ahorros» al lado de «$13.845.800» a 360 dp) baja a un segundo renglón en vez de
 *   cortarse. La fila crece; el mínimo de alto sigue siendo el de arriba.
 * - [subtitulo]: un renglón, con «…» si no entra. Donde el subtítulo es la razón de la fila y no
 *   se puede acortar («Hace más de un mes que no cuadras Bancolombia Ahorros», en Herramientas),
 *   [lineasDelSubtitulo] = 2: la fila crece en vez de cortar la frase (vuelta de diseño 2).
 * - El [final] se mide primero y entero: un monto nunca se recorta; lo que cede es el texto.
 * - [final]: un monto ([Cifra]), una [Pildora] o un [Chevron].
 *
 * ```
 * Fila(
 *     titulo = "Rappi · Las Doce",
 *     subtitulo = "Restaurantes · Nu",
 *     inicio = { AvatarDelComercio(evento) },
 *     final = { Cifra(-27_500) },
 *     onClick = { abrir(evento) },
 * )
 * ```
 */
@Composable
fun Fila(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    inicio: (@Composable () -> Unit)? = null,
    final: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    lineasDelSubtitulo: Int = 1,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitulo != null) Medida.altoDeFilaDoble else Medida.altoDeFila)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .testTag(TAG_FILA)
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        if (inicio != null) {
            Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) { inicio() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = LINEAS_DEL_TITULO_DE_FILA,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = lineasDelSubtitulo,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (final != null) final()
    }
}

/** **El tono de un [IconoEnCirculo]**: la marca para lo que es de Movi, un estado para un estado. */
enum class TonoDeIcono { Marca, Neutro, Info, Aviso, Peligro, Exito }

/** El color de un tono, en la paleta del tema. Pura. */
fun colorDelTonoDeIcono(tono: TonoDeIcono, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeIcono.Marca -> colores.marca
    TonoDeIcono.Neutro -> colores.textoMedio
    TonoDeIcono.Info -> colores.entreCuentas
    TonoDeIcono.Aviso -> colores.aviso
    TonoDeIcono.Peligro -> colores.sale
    TonoDeIcono.Exito -> colores.entra
}

/**
 * **Un ícono en un círculo lavado de su color**: el inicio de una [Fila] que no tiene comercio, y
 * el de una [TarjetaDePropuesta]. Mide [Medida.avatar], con el ícono a [Icono.m].
 */
@Composable
fun IconoEnCirculo(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    tono: TonoDeIcono = TonoDeIcono.Marca,
) {
    val color = colorDelTonoDeIcono(tono, Movi.colores)
    Box(
        modifier = modifier
            .size(Medida.avatar)
            .background(Movi.colores.lavado(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.m))
    }
}

/** **La flecha de «esto se abre»**, al final de una [Fila]. [Icono.m], en texto apagado. */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = Movi.colores.textoApagado,
        modifier = modifier.size(Icono.m),
    )
}

/** La inicial con que [AvatarDePersona] nombra a alguien: la primera letra, en mayúscula. */
fun inicialDe(nombre: String): String = nombre.trim().firstOrNull()?.uppercase() ?: "?"

/** El `testTag` de [AvatarDePersona]. */
const val TAG_AVATAR_DE_PERSONA: String = "ds:avatar-de-persona"

/**
 * **Una persona**: su inicial en un círculo de [Medida.avatar], en el lavado de su [tono]. Para los
 * miembros de un espacio («J» y «C» en «Casa con Caro») y quien pagó un gasto.
 */
@Composable
fun AvatarDePersona(
    nombre: String,
    modifier: Modifier = Modifier,
    tono: TonoDeIcono = TonoDeIcono.Marca,
) {
    val color = colorDelTonoDeIcono(tono, Movi.colores)
    Box(
        modifier = modifier
            .size(Medida.avatar)
            .background(Movi.colores.lavado(color), CircleShape)
            .testTag(TAG_AVATAR_DE_PERSONA),
        contentAlignment = Alignment.Center,
    ) {
        Text(inicialDe(nombre), style = Movi.textos.titulo, color = color, maxLines = 1)
    }
}
