package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.observabilidad.ToqueDeUso
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.theme.Radio

/*
 * # El encabezado de un grupo plegable (vuelta 120)
 *
 * La primera lista de Movi partida en grupos que se pliegan: «Por categoría» en Pagos. Cada grupo abre con
 * un renglón que dice qué es (su ícono y su nombre), cuánto suma (la cifra, al final) y cómo va (el
 * detalle: «Falta $2.613.714» o «Pagado ✓»), y que al tocarlo pliega o despliega las filas de abajo.
 *
 * - **Es un encabezado**: el lector de pantalla lo anuncia como tal, con una sola frase armada por quien lo
 *   usa (`descripcion`) y su estado («plegado» / «desplegado») — nunca la suma de sus textos sueltos.
 * - **Se toca entero** por [alPresionar], como una [Fila], con el chevrón al final que gira (abajo =
 *   plegado, arriba = desplegado).
 * - **El detalle no se recorta**: baja a un segundo renglón con la letra grande. La cifra se mide primero
 *   y entera, como en una [Fila].
 */

/** El tag del encabezado de un grupo plegable. */
const val TAG_ENCABEZADO_PLEGABLE: String = "ds:encabezado-plegable"

/** Lo que dice el estado de un encabezado plegable al lector de pantalla. */
const val ESTADO_PLEGADO: String = "plegado"
const val ESTADO_DESPLEGADO: String = "desplegado"

/** El tono del detalle de un [EncabezadoPlegable]. El color nunca es la única pista: la frase lo dice. */
enum class TonoDelDetalle {
    /** `textoMedio`: lo de siempre («Falta $2.613.714»). */
    Medio,

    /** `entra`: lo resuelto («Pagado ✓»). */
    Exito,

    /** `aviso`: lo que pide algo («Sin contar»). */
    Aviso,
}

/** El color del detalle con [tono]. Pura. */
fun colorDelDetalle(tono: TonoDelDetalle, colores: ColoresDeMovi): Color = when (tono) {
    TonoDelDetalle.Medio -> colores.textoMedio
    TonoDelDetalle.Exito -> colores.entra
    TonoDelDetalle.Aviso -> colores.aviso
}

/**
 * **El encabezado de un grupo que se pliega.** Va como primer renglón de una [Superficie] con
 * [RellenoDeSuperficie.Lista]: trae los márgenes de una [Fila].
 *
 * @param titulo el nombre del grupo (una categoría del dueño: el toque no lo anota, ver [ToqueDeUso.dinamico]).
 * @param detalle lo de debajo del título («Falta $2.613.714», «Pagado ✓»), en [tonoDelDetalle].
 * @param descripcion la frase para el lector, sin el estado: «Vivienda, falta 2.613.714 pesos». El estado va
 *   aparte (`stateDescription`): el lector dice «Vivienda, falta 2.613.714 pesos, plegado».
 * @param inicio el ícono, de [Medida.avatar] (un `IconoDeCategoria`).
 * @param final lo del final, antes del chevrón (la [Cifra] del total).
 */
@Composable
fun EncabezadoPlegable(
    titulo: String,
    plegado: Boolean,
    onAlternar: () -> Unit,
    descripcion: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    tonoDelDetalle: TonoDelDetalle = TonoDelDetalle.Medio,
    /** Un ícono chico después del detalle (el ✓ de «Pagado»: Figtree no dibuja «✓», va como ícono). */
    iconoDelDetalle: ImageVector? = null,
    inicio: (@Composable () -> Unit)? = null,
    final: (@Composable () -> Unit)? = null,
) {
    val giro by animateFloatAsState(
        if (plegado) 0f else GIRO_DESPLEGADO,
        Movimiento.alVolver(LocalMovimientoReducido.current, Movimiento.CORTO_MS),
        label = "chevron-del-grupo",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeFilaDoble)
            .anilloDeFoco(RoundedCornerShape(Radio.m), adentro = true)
            .alPresionar(
                onClick = onAlternar,
                toque = ToqueDeUso("encabezado-plegable", modificador = modifier, rotulo = titulo, dinamico = true),
                rol = Role.Button,
                escala = Movimiento.ESCALA_DE_LA_FILA_AL_PRESIONAR,
                velo = true,
            )
            // Un solo nodo para el lector: la frase de quien lo usa y el estado («…, plegado»). Los textos de
            // adentro quedan en el árbol sin fundir (las pruebas miden que no se corten), pero el lector dice
            // la descripción, no la suma de los textos sueltos.
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = descripcion
                stateDescription = if (plegado) ESTADO_PLEGADO else ESTADO_DESPLEGADO
            }
            .testTag(TAG_ENCABEZADO_PLEGABLE)
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inicio != null) {
            Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) { inicio() }
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_TEXTO_DE_FILA))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = Movi.textos.cuerpoFuerte,
                color = Movi.colores.texto,
                maxLines = LINEAS_DEL_TITULO_DE_FILA,
                overflow = TextOverflow.Ellipsis,
            )
            if (detalle != null) {
                val color = colorDelDetalle(tonoDelDetalle, Movi.colores)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = detalle, style = Movi.textos.apoyoMedio, color = color, modifier = Modifier.weight(1f, fill = false))
                    if (iconoDelDetalle != null) {
                        Icon(
                            imageVector = iconoDelDetalle,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.padding(start = Espacio.xxs).size(Icono.s),
                        )
                    }
                }
            }
        }
        if (final != null) {
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_FINAL_DE_FILA))
            final()
        }
        Spacer(Modifier.width(Espacio.xxs))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Movi.colores.textoMedio,
            modifier = Modifier.size(Icono.m).rotate(giro),
        )
    }
}

/** Cuánto gira el chevrón desplegado: hacia arriba. */
private const val GIRO_DESPLEGADO: Float = 180f
