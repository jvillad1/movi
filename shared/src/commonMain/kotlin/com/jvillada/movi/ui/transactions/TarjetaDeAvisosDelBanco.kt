package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * botón nunca se corta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TarjetaDeAvisosDelBanco(
    avisos: AvisosPorConfirmar,
    onConfirmar: () -> Unit,
    onRevisar: () -> Unit,
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
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(TAG_BOTONES_DE_LOS_AVISOS),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            botonDeConfirmarLosAvisos(avisos)?.let { texto ->
                Boton(
                    texto,
                    onClick = onConfirmar,
                    modifier = Modifier.weight(1f),
                    variante = VarianteDeBoton.Principal,
                    cargando = ocupado,
                )
            }
            Boton(
                botonDeRevisarLosAvisos(avisos),
                onClick = onRevisar,
                modifier = Modifier.weight(1f),
                variante = VarianteDeBoton.Secundario,
                habilitado = !ocupado,
            )
        }
    }
}

/** El tag de la fila de botones de la tarjeta de avisos. */
const val TAG_BOTONES_DE_LOS_AVISOS: String = "actividad:botones-de-los-avisos"
