package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** Tag estable del [Encabezado] de una pantalla. */
const val TAG_ENCABEZADO: String = "ds-encabezado"

/** Lo que dice el lector de pantalla de la flecha de volver. */
const val ROTULO_VOLVER: String = "Volver"

/**
 * **Un ícono que se toca**: [Icono.l] en un área de [Medida.objetivoTactil]. La flecha de volver, la
 * lupa, la campana de un encabezado.
 */
@Composable
fun BotonDeIcono(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Medida.objetivoTactil)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = descripcion, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Movi.colores.texto, modifier = Modifier.size(Icono.l))
    }
}

/** Tag estable de la [AccionDelEncabezado]: la mide la prueba que compara pantallas. */
const val TAG_ACCION_DEL_ENCABEZADO: String = "ds-accion-del-encabezado"

/**
 * **La acción principal de un encabezado**: lo que se crea o se hace desde esa pantalla («Nueva
 * cuenta», «Nuevo crédito», «Subir archivo»). Viaja como dato para que [Encabezado] la pinte siempre
 * igual —texto con ícono, en la marca, [AccionDelEncabezado]— y ninguna pantalla elija otro botón
 * (vuelta de diseño 4: Patrimonio la tenía como texto morado y Créditos como botón tonal relleno).
 */
data class AccionDeEncabezado(
    val texto: String,
    val icono: ImageVector,
    val onClick: () -> Unit,
    val habilitado: Boolean = true,
)

/**
 * **La forma de la [AccionDeEncabezado]**: un [Boton] [VarianteDeBoton.Fantasma] tamaño
 * [TamanoDeBoton.S] con su ícono (texto e ícono en la marca, sin relleno; el toque llega a 48).
 * Solo la pinta [Encabezado].
 */
@Composable
internal fun AccionDelEncabezado(accion: AccionDeEncabezado, modifier: Modifier = Modifier) {
    Boton(
        texto = accion.texto,
        onClick = accion.onClick,
        modifier = modifier.testTag(TAG_ACCION_DEL_ENCABEZADO),
        variante = VarianteDeBoton.Fantasma,
        tamano = TamanoDeBoton.S,
        icono = accion.icono,
        habilitado = accion.habilitado,
    )
}

/**
 * **El encabezado de una pantalla. El mismo alto y los mismos márgenes en todas.**
 *
 * - Alto [Medida.altoDeEncabezado] (56), como mínimo: con la letra del sistema agrandada crece en
 *   vez de cortar.
 * - Margen [Medida.margenDePantalla] a los dos lados. Con [onVolver], la flecha va en un área de
 *   toque de 48 y el margen se corre lo justo para que **el dibujo** de la flecha quede en el
 *   margen, alineado con todo lo de abajo.
 * - El título en `titular`, un renglón. El subtítulo opcional, en `apoyo`.
 * - [accion] a la derecha, al final: **la** acción principal, siempre con la misma forma
 *   ([AccionDelEncabezado]: texto con ícono). Una pantalla con dos o más acciones pone las demás en
 *   [acciones], como [BotonDeIcono] con su descripción.
 * - [acciones] antes de [accion]: [BotonDeIcono]s, una [Pildora] («Beta») o «Actualizando…». Nunca
 *   un [Boton] con texto: para eso está [accion].
 * - Sin línea debajo: superficies por capas, sin bordes duros (camino 1).
 *
 * `MinScreenHeader` —el encabezado de todas las pantallas de hoy— delega acá.
 *
 * ```
 * Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", ::buscar) })
 * Encabezado("Detalle", onVolver = { volver() })
 * Encabezado("Patrimonio", accion = AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, ::crear))
 * ```
 *
 * @param inicio lo que va a la izquierda cuando no es la flecha: el avatar de una pantalla raíz.
 *   Si llegan los dos, gana [onVolver].
 */
@Composable
fun Encabezado(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    onVolver: (() -> Unit)? = null,
    inicio: (@Composable () -> Unit)? = null,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    accion: AccionDeEncabezado? = null,
) {
    // La flecha dibuja 24 en un área de 48: el margen se achica en la mitad de la diferencia para
    // que el dibujo, no el área, quede alineado con el resto de la pantalla.
    val margenDeInicio = if (onVolver != null) {
        Medida.margenDePantalla - (Medida.objetivoTactil - Icono.l) / 2
    } else {
        Medida.margenDePantalla
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeEncabezado)
            .testTag(TAG_ENCABEZADO)
            .padding(start = margenDeInicio, end = Medida.margenDePantalla),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        when {
            onVolver != null -> BotonDeIcono(Icons.AutoMirrored.Rounded.ArrowBack, ROTULO_VOLVER, onVolver)
            inicio != null -> inicio()
        }
        Column(modifier = Modifier.weight(1f).semantics { heading() }) {
            Text(
                text = titulo,
                style = Movi.textos.titular,
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
        if (acciones != null) acciones()
        if (accion != null) AccionDelEncabezado(accion)
    }
}
