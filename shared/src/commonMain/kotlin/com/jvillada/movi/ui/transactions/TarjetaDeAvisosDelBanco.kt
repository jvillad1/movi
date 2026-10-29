package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

/** El tag de la tarjeta de avisos del banco de Actividad (vuelta 40). */
const val TAG_TARJETA_DE_AVISOS: String = "actividad:avisos-del-banco"

/** El rótulo del botón que abre «Por revisar» junto a un «Confirmar» de varios (ver [botonDeRevisarLosAvisos]). */
const val REVISAR_UNO_POR_UNO: String = "Revisar uno por uno"

/**
 * **La tarjeta de los avisos del banco** (vuelta 40, la opción 3): blanca con la sombra suave de una
 * propuesta, la campana en su círculo amarillo, «3 avisos del banco por confirmar», «Suman $X · …» y
 * dos botones de 48: **«Confirmar los 3»** (amarillo, solo si Movi entendió alguno; ver
 * [botonDeConfirmarLosAvisos]) y **«Revisar uno por uno»** (con borde, abre «Por revisar»).
 *
 * Los botones van lado a lado si caben enteros, y si no, uno debajo del otro, cada uno a todo el
 * ancho: «Confirmar los 2 que Movi entendió» no entra al lado de «Revisar uno por uno» a 360 px, y un
 * botón nunca se corta. Vuelta 53: lo decide [LadoALadoOApilados] midiendo el ancho **entero** de cada
 * rótulo; el `FlowRow` con `weight` de antes los dejaba lado a lado porque medía la palabra más larga,
 * y a 390 «Revisar uno por uno» se cortaba en «Revisar uno por».
 */
@Composable
internal fun TarjetaDeAvisosDelBanco(
    avisos: AvisosPorConfirmar,
    onConfirmar: () -> Unit,
    /** «Revisar uno por uno» (abre Por revisar). `null` en Por revisar mismo (vuelta 44): ya está ahí. */
    onRevisar: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ocupado: Boolean = false,
) {
    Superficie(
        modifier = modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_AVISOS),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            IconoEnCirculo(Icons.Rounded.NotificationsNone, tono = TonoDeIcono.Marca)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                Text(
                    tituloDeLosAvisos(avisos.cuantos),
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    modifier = Modifier.semantics { heading() },
                )
                detalleDeLosAvisos(avisos)?.let {
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
        }
        Spacer(Modifier.height(Espacio.m))
        // Vuelta 53: si el rótulo largo («Confirmar los 2 que Movi entendió») no cabe ni solo, a todo el
        // ancho, va el corto («Confirmar los 2»). Se mide con la letra del botón y su relleno.
        val medidor = rememberTextMeasurer()
        val estilo = Movi.textos.cuerpoFuerte
        val largo = botonDeConfirmarLosAvisos(avisos)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val relleno = with(LocalDensity.current) { (Espacio.l * 2).roundToPx() }
            val cabeElLargo = largo == null ||
                medidor.measure(largo, estilo, maxLines = 1).size.width + relleno <= constraints.maxWidth
            val rotuloDeConfirmar = if (cabeElLargo) largo else botonCortoDeConfirmarLosAvisos(avisos)
        LadoALadoOApilados(
            modifier = Modifier.fillMaxWidth().testTag(TAG_BOTONES_DE_LOS_AVISOS),
            separacion = Espacio.xs,
        ) {
            rotuloDeConfirmar?.let { texto ->
                Boton(
                    texto,
                    onClick = onConfirmar,
                    variante = VarianteDeBoton.Principal,
                    cargando = ocupado,
                )
            }
            if (onRevisar != null) {
                Boton(
                    botonDeRevisarLosAvisos(avisos),
                    onClick = onRevisar,
                    variante = VarianteDeBoton.Secundario,
                    habilitado = !ocupado,
                )
            }
        }
        }
    }
}

/**
 * **Lado a lado si caben enteros; si no, uno debajo del otro** (vuelta 53). Mide el ancho que cada
 * hijo necesita para leerse entero (`maxIntrinsicWidth`: un botón no parte su rótulo) y:
 *
 * - si la suma más las separaciones cabe en el ancho, los pone en un renglón y les reparte por igual
 *   lo que sobra (así llenan el ancho, como antes con `weight`);
 * - si no, cada uno va en su renglón, a todo el ancho.
 *
 * Es la regla de la vuelta 40 dicha con la medida correcta: un `FlowRow` con `weight` decide el
 * renglón con el ancho mínimo (la palabra más larga) y después aprieta el botón contra el otro.
 */
@Composable
internal fun LadoALadoOApilados(
    modifier: Modifier = Modifier,
    separacion: Dp,
    contenido: @Composable () -> Unit,
) {
    Layout(content = contenido, modifier = modifier) { medibles, restricciones ->
        val hueco = separacion.roundToPx()
        val ancho = restricciones.maxWidth
        if (medibles.isEmpty()) return@Layout layout(restricciones.minWidth, 0) {}
        val naturales = medibles.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val juntos = naturales.sum() + hueco * (medibles.size - 1)
        if (restricciones.hasBoundedWidth && juntos <= ancho) {
            val sobra = ancho - juntos
            val porCada = sobra / medibles.size
            val colocables = medibles.mapIndexed { i, m ->
                // El último se lleva el resto de la división: el renglón mide exacto el ancho.
                val extra = if (i == medibles.lastIndex) sobra - porCada * (medibles.size - 1) else porCada
                m.measure(Constraints.fixedWidth(naturales[i] + extra))
            }
            val alto = colocables.maxOf { it.height }
            layout(ancho, alto) {
                var x = 0
                colocables.forEach { c ->
                    c.place(x, (alto - c.height) / 2)
                    x += c.width + hueco
                }
            }
        } else {
            val anchoDeCada = if (restricciones.hasBoundedWidth) ancho else naturales.max()
            val colocables = medibles.map { it.measure(Constraints.fixedWidth(anchoDeCada)) }
            val alto = colocables.sumOf { it.height } + hueco * (colocables.size - 1)
            layout(anchoDeCada, alto) {
                var y = 0
                colocables.forEach { c ->
                    c.place(0, y)
                    y += c.height + hueco
                }
            }
        }
    }
}

/** El tag de la fila de botones de la tarjeta de avisos. */
const val TAG_BOTONES_DE_LOS_AVISOS: String = "actividad:botones-de-los-avisos"
