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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * **Cuántos renglones lleva el rótulo de una [AccionRapida].**
 *
 * - [Uno] (vuelta de diseño 2): la cuadrícula del Inicio. Rótulos cortos («Gasto», «Comprobante»),
 *   todos en un renglón, para que la fila no quede despareja. Si uno no entra se achica hasta la
 *   letra del `rotulo` y recién ahí se corta; la frase entera la dice la [Accion.descripcion].
 * - [Dos]: las fichas de Ajustes, que son nombres de pantallas («Personas y comercios», «Lo que
 *   Movi sabe de ti»). Siempre con el alto de dos, para que la cuadrícula quede alineada.
 */
enum class RenglonesDelRotulo { Uno, Dos }

/**
 * **Una acción de la cuadrícula**, como dato: lo que [CuadriculaDeAcciones] pinta.
 *
 * @param rotulo lo que se lee debajo del círculo: corto («Gasto», «Preguntar»).
 * @param descripcion la frase entera para el lector de pantalla («Anotar un gasto»); sin ella dice
 *   el [rotulo].
 * @param destacada la acción de la pantalla —«Gasto»—, llena de marca. Una, o ninguna.
 * @param insignia un conteo arriba del círculo («3 por revisar»); `null` o 0 no dibuja nada.
 */
@Immutable
data class Accion(
    val rotulo: String,
    val icono: ImageVector,
    val onClick: () -> Unit,
    val destacada: Boolean = false,
    val insignia: Int? = null,
    val descripcion: String? = null,
)

/**
 * **Una acción rápida**: un ícono en un círculo de [Medida.accionRapida] y su rótulo debajo. Es la
 * pieza de la cuadrícula del camino 3 (Mercado Pago).
 *
 * El rótulo va en **un renglón** ([RenglonesDelRotulo.Uno], vuelta de diseño 2): en el Inicio
 * «Anotar ingreso» y «Subir comprobante» iban en dos y el resto en uno, y la fila quedaba despareja.
 * Si un rótulo corto igual no entra (360 dp con la letra agrandada) se achica hasta la letra del
 * `rotulo`, y recién ahí se corta con «…». Con [RenglonesDelRotulo.Dos], dos renglones como mucho y
 * siempre el alto de dos.
 *
 * El círculo va en la capa 2 con el ícono en marca; la destacada, lleno de marca con el ícono en
 * `sobreMarca`. El toque es la acción entera (círculo y rótulo), y el lector de pantalla dice la
 * [descripcion] (o el rótulo) y el conteo una sola vez.
 *
 * ```
 * AccionRapida("Gasto", Icons.Rounded.Edit, onClick = ::anotar, destacada = true, descripcion = "Anotar un gasto")
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
    descripcion: String? = null,
    renglones: RenglonesDelRotulo = RenglonesDelRotulo.Uno,
) {
    val fondo = if (destacada) Movi.colores.marca else Movi.colores.capa2
    val tinte = if (destacada) Movi.colores.sobreMarca else Movi.colores.marca
    val conInsignia = insignia != null && insignia > 0
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                val dicho = descripcion ?: rotulo
                contentDescription = if (conInsignia) "$dicho, $insignia" else dicho
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
        when (renglones) {
            RenglonesDelRotulo.Uno -> {
                val estilo = Movi.textos.apoyo.copy(
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    textAlign = TextAlign.Center,
                )
                // El renglón mide siempre el alto de un renglón de `apoyo`, aunque el rótulo se haya
                // achicado: así las cuatro de una fila quedan parejas.
                val altoDelRenglon = with(LocalDensity.current) { estilo.lineHeight.toDp() }
                Box(
                    modifier = Modifier.fillMaxWidth().height(altoDelRenglon),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = rotulo,
                        modifier = Modifier.fillMaxWidth().testTag(TAG_ROTULO_DE_ACCION),
                        style = estilo,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = Movi.textos.rotulo.fontSize, maxFontSize = estilo.fontSize),
                    )
                }
            }
            RenglonesDelRotulo.Dos -> Text(
                text = rotulo,
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.testTag(TAG_ROTULO_DE_ACCION),
            )
        }
    }
}

/** Tag estable del rótulo de una [AccionRapida]. */
const val TAG_ROTULO_DE_ACCION: String = "ds-rotulo-de-accion"

/**
 * **La cuadrícula de acciones del camino 3**: [COLUMNAS_DE_ACCIONES] columnas de [AccionRapida],
 * del ancho que haya, con los rótulos en [renglones] (uno, salvo las fichas de Ajustes). La última fila, si queda corta, deja sus huecos vacíos a la derecha (no
 * estira las que hay). No es perezosa a propósito: son ocho acciones como mucho y va dentro de una
 * pantalla que ya se desplaza.
 *
 * ```
 * CuadriculaDeAcciones(
 *     listOf(
 *         Accion("Gasto", Icons.Rounded.Edit, ::anotar, destacada = true, descripcion = "Anotar un gasto"),
 *         Accion("Traspaso", Icons.Rounded.SwapHoriz, ::traspasar, descripcion = "Anotar un traspaso"),
 *         Accion("Avisos", Icons.Rounded.Inbox, ::revisar, insignia = 3, descripcion = "Revisar los avisos"),
 *     ),
 * )
 * ```
 */
@Composable
fun CuadriculaDeAcciones(
    acciones: List<Accion>,
    modifier: Modifier = Modifier,
    renglones: RenglonesDelRotulo = RenglonesDelRotulo.Uno,
) {
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
                        descripcion = accion.descripcion,
                        renglones = renglones,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNAS_DE_ACCIONES - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
