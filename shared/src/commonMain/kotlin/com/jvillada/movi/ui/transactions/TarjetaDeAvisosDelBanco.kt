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
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.CirculoEsqueleto
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.porrevisar.desgloseDePorRevisar
import com.jvillada.movi.ui.porrevisar.textoDePorRevisar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding

/** El tag de la tarjeta de avisos del banco de Actividad (vuelta 40). */
const val TAG_TARJETA_DE_AVISOS: String = "actividad:avisos-del-banco"

/** El rótulo del botón que abre «Por revisar» junto a un «Confirmar» de varios (ver [botonDeRevisarLosAvisos]). */
const val REVISAR_UNO_POR_UNO: String = "Revisar uno por uno"

/**
 * **La tarjeta de los avisos del banco**: una [TarjetaDePropuesta] de un renglón (vuelta 80).
 *
 * La revisión 7 la vio dominar Actividad: 210 px, dos botones a lo ancho uno encima del otro
 * («Confirmar los 2 que Movi entendió», «Revisar uno por uno») y una línea de detalle. Ahora es la
 * propuesta de siempre: la campana, «5 avisos del banco por confirmar» en un renglón, y debajo
 * **«Confirmar 2»** en un [Boton] chico ([botonCompactoDeConfirmar]) con **«Revisar»** como enlace
 * (abre «Por revisar»). Sin nada que Movi haya entendido, «Revisar» es la acción.
 *
 * Vuelta 82 · **el mismo conteo en todos lados**: con [conteo] (Actividad), el título es el de la
 * insignia del Inicio y de la campana —«6 por revisar», [textoDePorRevisar] sobre
 * [ConteoPorRevisar.total]— y debajo de qué es («5 avisos · 1 movimiento», [desgloseDePorRevisar]).
 * Antes Actividad decía «5 avisos del banco» mientras el Inicio decía 6, y en frío pasaba de una
 * tarjeta a la otra con un salto. Sin [avisos] todavía (o sin nada que Movi entendiera), la acción es
 * «Revisar»; al llegar, «Confirmar 2 · Revisar»: el mismo alto. Sin [conteo] (el lote de Por revisar),
 * el título de los avisos. **El círculo va en el tono de aviso**, no en el amarillo: el único amarillo
 * de la tarjeta es «Confirmar N», la acción de la vista. «Revisar» solo, que navega, va secundario.
 */
@Composable
internal fun TarjetaDeAvisosDelBanco(
    avisos: AvisosPorConfirmar?,
    onConfirmar: () -> Unit,
    /** «Revisar» (abre Por revisar). `null` en Por revisar mismo (vuelta 44): ya está ahí. */
    onRevisar: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ocupado: Boolean = false,
    /** Vuelta 82: lo que dice la insignia del Inicio (Actividad). `null`: el título de los avisos. */
    conteo: ConteoPorRevisar? = null,
) {
    val confirmar = avisos?.let(::botonCompactoDeConfirmar)
    val alConfirmar = { if (!ocupado) onConfirmar() }
    val (accion, onAccion) = when {
        confirmar != null -> confirmar to alConfirmar
        onRevisar != null -> REVISAR_LOS_AVISOS to onRevisar
        else -> return
    }
    val titulo = when {
        conteo != null -> textoDePorRevisar(conteo.total)
        avisos != null -> tituloDeLosAvisos(avisos.cuantos)
        else -> return
    }
    // Tres nodos: el tag de quien la monta, el de esta tarjeta y el de la propuesta, sin pisarse.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_AVISOS)) {
            TarjetaDePropuesta(
                icono = Icons.Rounded.NotificationsNone,
                titulo = titulo,
                cuerpo = conteo?.let { desgloseDePorRevisar(it) },
                accion = accion,
                onAccion = onAccion,
                tono = TonoDeIcono.Aviso,
                // Vuelta 82: «Confirmar N» es el amarillo de la vista; «Revisar» solo navega.
                varianteDeLaAccion = if (confirmar != null) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                accionSecundaria = REVISAR_LOS_AVISOS.takeIf { confirmar != null && onRevisar != null },
                onAccionSecundaria = onRevisar.takeIf { confirmar != null },
            )
        }
    }
}

/** Vuelta 82: el tag del esqueleto de la tarjeta de avisos de Actividad (con la forma recordada). */
const val TAG_ESQUELETO_DE_LA_TARJETA_DE_AVISOS: String = "actividad:avisos-del-banco:esqueleto"

/**
 * **La tarjeta de avisos mientras se cuenta** (vuelta 82): la misma anatomía de [TarjetaDePropuesta]
 * con [conteo] —el círculo, el título, el desglose y un botón chico— sobre la misma superficie, solo si
 * la última visita la tenía (`FormaDeMovimientos.tarjetaPorRevisar`). Sin esto, en frío la tarjeta
 * aparecía después de la lista y la empujaba.
 */
@Composable
internal fun EsqueletoDeLaTarjetaDeAvisos(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Superficie(
            modifier = Modifier.fillMaxWidth().testTag(TAG_ESQUELETO_DE_LA_TARJETA_DE_AVISOS),
            nivel = NivelDeSuperficie.Propuesta,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                CirculoEsqueleto(diametro = Medida.avatar)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                    BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.titulo))
                    BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.cuerpo))
                    Box(modifier = Modifier.fillMaxWidth(FRACCION_DEL_BOTON_ESQUELETO).padding(top = Espacio.xs)) {
                        BloqueEsqueleto(alto = Medida.altoChico)
                    }
                }
            }
        }
    }
}

/** El ancho del botón que todavía no llegó, como fracción del texto de la tarjeta. */
private const val FRACCION_DEL_BOTON_ESQUELETO = 0.3f

/** «Revisar»: el enlace de la tarjeta de avisos, que abre «Por revisar» (vuelta 80). */
const val REVISAR_LOS_AVISOS: String = "Revisar"

/**
 * **El botón chico de la tarjeta de avisos** (vuelta 80): «Confirmar 2» —los que Movi entendió—, o
 * «Confirmar» con un solo aviso; `null` si no entendió ninguno. Lo que queda para revisar lo dice la
 * hoja de confirmar. Pura.
 */
fun botonCompactoDeConfirmar(avisos: AvisosPorConfirmar): String? {
    val m = avisos.confirmables.size
    return when {
        m == 0 -> null
        avisos.cuantos == 1 -> "Confirmar"
        else -> "Confirmar $m"
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
