package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

/** Tag estable de una [VistaPrevia]. */
const val TAG_VISTA_PREVIA: String = "ds-vista-previa"

/** Los renglones que dice una [VistaPrevia], como mucho: lo principal y un detalle. */
const val RENGLONES_DE_LA_VISTA_PREVIA: Int = 2

/**
 * **Lo que va a pasar si guardas**, en una hoja (vuelta 41). Dos renglones como mucho: [principal] en
 * `cuerpo` y tinta («Tu período: del 25 de octubre al 24 de noviembre») y, si hace falta, un [detalle]
 * en `apoyo` y `textoMedio` («El 25 de octubre es domingo: empieza el 23»). Va en una [Superficie] de
 * capa 2, que se aparta del blanco de la hoja sin ser otra tarjeta.
 *
 * Reemplaza a las vistas previas que cada hoja armaba a mano: la de «Inicio del período» decía lo mismo
 * en tres renglones («Hoy estarías en octubre…», «Del 1 al último día…», «Es el mes de calendario…»),
 * el primero en un estilo más grande que el título de la hoja.
 *
 * Cambia mientras se elige: el lector de pantalla la lee de nuevo (región viva, cortés).
 *
 * ```
 * VistaPrevia("Tu período: del 25 de octubre al 24 de noviembre", detalle = "El 25 de octubre es domingo: empieza el 23.")
 * ```
 */
@Composable
fun VistaPrevia(
    principal: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
) {
    Superficie(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_VISTA_PREVIA)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        nivel = NivelDeSuperficie.Capa2,
    ) {
        Text(principal, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        if (detalle != null) {
            Spacer(Modifier.height(Espacio.xxs))
            Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}
