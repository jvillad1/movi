package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** Tag estable de una [AccionRapida]. */
const val TAG_ACCION_RAPIDA: String = "ds-accion-rapida"

/** Tag estable de una [CuadriculaDeAcciones]. */
const val TAG_CUADRICULA_DE_ACCIONES: String = "ds-cuadricula-de-acciones"

/** Cuántas columnas tiene la cuadrícula de acciones del camino 3. Siempre cuatro. */
const val COLUMNAS_DE_ACCIONES: Int = 4

/**
 * **Una acción de la cuadrícula**, como dato: lo que [CuadriculaDeAcciones] pinta.
 *
 * @param destacada la acción de la pantalla —«Anotar gasto»—, llena de marca. Una, o ninguna.
 * @param insignia un conteo arriba del círculo («3 por revisar»); `null` o 0 no dibuja nada.
 */
@Immutable
data class Accion(
    val rotulo: String,
    val icono: ImageVector,
    val onClick: () -> Unit,
    val destacada: Boolean = false,
    val insignia: Int? = null,
)

/**
 * **Una acción rápida**: un ícono en un círculo de [Medida.accionRapida] y su rótulo debajo, en dos
 * renglones como mucho (y siempre el alto de dos, para que una cuadrícula quede alineada aunque un
 * rótulo sea corto). Es la pieza de la cuadrícula del camino 3 (Mercado Pago).
 *
 * El círculo va en la capa 2 con el ícono en marca; la destacada, lleno de marca con el ícono en
 * `sobreMarca`. El toque es la acción entera (círculo y rótulo), y el lector de pantalla dice el
 * rótulo y el conteo una sola vez.
 *
 * ```
 * AccionRapida("Anotar gasto", Icons.Rounded.Edit, onClick = ::anotar, destacada = true)
 * ```
 */
@Composable
fun AccionRapida(
    rotulo: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destacada: Boolean = false,
    insignia: Int? = null,
) {
    val fondo = if (destacada) Movi.colores.marca else Movi.colores.capa2
    val tinte = if (destacada) Movi.colores.sobreMarca else Movi.colores.marca
    val conInsignia = insignia != null && insignia > 0
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (conInsignia) "$rotulo, $insignia" else rotulo
            }
            .testTag(TAG_ACCION_RAPIDA)
            .padding(vertical = Espacio.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Box {
            Box(
                modifier = Modifier.size(Medida.accionRapida).background(fondo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = tinte, modifier = Modifier.size(Icono.l))
            }
            if (conInsignia) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = Medida.insignia, minHeight = Medida.insignia)
                        .background(Movi.colores.marca, CircleShape)
                        .padding(horizontal = Espacio.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = insignia.toString(),
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.SemiBold,
                        color = Movi.colores.sobreMarca,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = rotulo,
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.texto,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
    }
}

/**
 * **La cuadrícula de acciones del camino 3**: [COLUMNAS_DE_ACCIONES] columnas de [AccionRapida],
 * del ancho que haya. La última fila, si queda corta, deja sus huecos vacíos a la derecha (no
 * estira las que hay). No es perezosa a propósito: son ocho acciones como mucho y va dentro de una
 * pantalla que ya se desplaza.
 *
 * ```
 * CuadriculaDeAcciones(
 *     listOf(
 *         Accion("Anotar gasto", Icons.Rounded.Edit, ::anotar, destacada = true),
 *         Accion("Traspasar", Icons.Rounded.SwapHoriz, ::traspasar),
 *         Accion("Por revisar", Icons.Rounded.Inbox, ::revisar, insignia = 3),
 *     ),
 * )
 * ```
 */
@Composable
fun CuadriculaDeAcciones(acciones: List<Accion>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_CUADRICULA_DE_ACCIONES),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        acciones.chunked(COLUMNAS_DE_ACCIONES).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xxs),
            ) {
                fila.forEach { accion ->
                    AccionRapida(
                        rotulo = accion.rotulo,
                        icono = accion.icono,
                        onClick = accion.onClick,
                        destacada = accion.destacada,
                        insignia = accion.insignia,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNAS_DE_ACCIONES - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
