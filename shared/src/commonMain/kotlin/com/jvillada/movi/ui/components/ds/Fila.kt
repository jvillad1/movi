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
 * - [titulo] y [subtitulo]: un renglón cada uno, con «…» si no entran.
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
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
