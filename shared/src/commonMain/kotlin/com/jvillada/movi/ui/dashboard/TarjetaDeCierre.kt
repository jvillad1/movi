package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.comparacionDelCierre
import com.jvillada.movi.shared.model.disponibleDelCierre
import com.jvillada.movi.shared.model.fraseDelCierre
import com.jvillada.movi.shared.model.pagosDelCierre
import com.jvillada.movi.shared.model.seMuestraEnHoy
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant

/**
 * # «Cerró tu período» en Hoy
 *
 * Los primeros días del período nuevo ([seMuestraEnHoy]), arriba de todo: «Cerró tu período: entró
 * $X, salió $Y» y dos renglones de lo que más sirve —lo que quedó del disponible y contra el período
 * anterior—. Tocarla abre el detalle del período cerrado; «Ya lo vi» la cierra en todos los aparatos
 * (`POST /api/cierre-del-periodo/descartar`).
 *
 * El resumen lo arma el server ([CierreDelPeriodo]) y el texto sale de `:core`: la tarjeta, el aviso
 * del teléfono y el correo dicen lo mismo con las mismas palabras.
 */

/** El tag de la tarjeta, para las pruebas. */
const val TAG_TARJETA_DE_CIERRE: String = "tarjeta-de-cierre"

/**
 * Los renglones de apoyo de la tarjeta, como mucho dos: lo que quedó del disponible, contra el
 * período anterior y, si falta lugar, los pagos. El resto del resumen está en el detalle.
 */
fun renglonesDeLaTarjetaDeCierre(c: CierreDelPeriodo): List<String> =
    listOfNotNull(disponibleDelCierre(c), comparacionDelCierre(c), pagosDelCierre(c)).take(2)

/** El cierre que Hoy pinta, o `null`: el que llegó, mientras se muestre (ver [seMuestraEnHoy]). */
fun cierreParaHoy(data: DashboardData): CierreDelPeriodo? = data.cierre?.takeIf { seMuestraEnHoy(it) }

@Composable
internal fun TarjetaDeCierre(
    cierre: CierreDelPeriodo,
    onNavigate: (Screen) -> Unit,
    onDescartar: () -> Unit,
) {
    val abrir = { onNavigate(Screen.DetalleDePeriodo(cierre.periodo)) }
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinCard(
            modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_CIERRE),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.amplio),
            onClick = abrir,
            onClickLabel = "Ver el período ${cierre.nombre}",
        ) {
            Text(
                text = "Resumen de ${cierre.nombre.replaceFirstChar { it.lowercase() }}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Movi.espacios.minimo))
            Text(
                text = fraseDelCierre(cierre),
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
            )
            renglonesDeLaTarjetaDeCierre(cierre).forEach { renglon ->
                Spacer(Modifier.height(Movi.espacios.minimo))
                Text(text = renglon, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            Spacer(Modifier.height(Movi.espacios.corto))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDescartar) {
                    Text("Ya lo vi", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                }
                TextButton(onClick = abrir) {
                    Text("Ver el período", style = Movi.textos.cuerpo, color = Movi.colores.marca)
                }
            }
        }
        Spacer(Modifier.height(Movi.espacios.amplio))
    }
}
