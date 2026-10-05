package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.nombresDeLasCuentasDeOtros
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant

/** El tag del acceso principal a «Personas y comercios» (Movimientos). */
const val TAG_ACCESO_A_PERSONAS_Y_COMERCIOS: String = "acceso-personas-y-comercios"

/**
 * Lo que dice el acceso a la derecha: los nombres —primero a quien más se le mandó este período—
 * o, sin ninguno guardado, la invitación. Mientras no contestó, lo mismo que sin ninguno: el
 * renglón no cambia de alto por eso (es de una sola línea).
 */
internal fun resumenDelAcceso(destinos: List<DestinoConocido>?): String =
    if (destinos.isNullOrEmpty()) "Guarda a quién le envías plata" else nombresDeLasCuentasDeOtros(destinos)

/**
 * # La puerta principal a «Personas y comercios» (4-oct-2026)
 *
 * El dueño: *«que tenga un acceso muy fácil de encontrar para ver cuentas de terceros»*. Hasta acá
 * se llegaba por Patrimonio —bajando hasta debajo de Inversión— o por el avatar → Ajustes. Ninguna
 * de las dos es donde uno piensa «¿a quién le mandé plata?»: eso se piensa en **Movimientos**.
 *
 * Así que va acá, **un renglón fijo** debajo de los filtros, con los nombres a la vista («Hernán,
 * Ana, Cancha El Gol y 2 más»). Un toque abre la lista; cada ficha de la lista ya dice cuánto se le
 * mandó este período. Desde Hoy son dos toques (la pestaña y el renglón).
 *
 * ### Por qué un renglón y no un filtro ni un chip por persona
 *
 * - **No es un filtro de la lista**: la pregunta no es «muéstrame mis movimientos de otra forma»
 *   sino «¿a quién le mando y cuánto?», que se contesta por persona y por período en su ficha.
 * - **Un chip por persona** no escala y el dueño ya pidió menos filtros en esa fila (ver
 *   `CHIPS_VISIBLES`).
 * - **Siempre está, y siempre de una línea**: no aparece cuando contesta la lectura, así que no
 *   empuja la lista ni hace saltar el esqueleto. Sin nadie guardado invita a guardar.
 */
@Composable
fun RenglonDePersonasYComercios(
    destinos: List<DestinoConocido>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinCard(
        modifier = modifier.fillMaxWidth().testTag(TAG_ACCESO_A_PERSONAS_Y_COMERCIOS),
        variant = MinCardVariant.Default,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(18.dp))
            Text(
                PERSONAS_Y_COMERCIOS,
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                resumenDelAcceso(destinos),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            ChevronRight()
        }
    }
}
