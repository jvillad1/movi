package com.jvillada.movi.ui.fecha

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.MinBorderStrong
import com.jvillada.movi.theme.MinOnPrimaryContainer
import com.jvillada.movi.theme.MinPrimaryContainer
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * **El selector de fecha de un movimiento: dos atajos y un calendario.**
 *
 * ## Por qué esta forma y no un campo de texto
 *
 * La única forma que había en la app de poner una fecha era escribir «AAAA-MM-DD» a mano (la
 * pestaña Traspaso), y es exactamente lo que el dueño acaba de pedir que dejara de pasar en otra
 * pantalla —«que el día del mes sea un selector y no un campo»—. Un campo de texto de fecha pide
 * tres cosas que nadie quiere darle al anotar un café: acordarse del formato, saber en qué número
 * de mes estamos, y no equivocarse. Y cuando se equivoca, lo único que pasa es que el botón de
 * guardar queda en gris.
 *
 * ## Por qué **Hoy** y **Ayer** arriba, grandes
 *
 * Porque cubren casi todo. El caso del dueño es literal: anotó el gimnasio, el mercado, un café,
 * un almuerzo y el fútbol de una sentada, y varios eran de ayer. «Hoy» ya es el valor por defecto
 * —así que quien anota en el momento no toca nada, como hasta ahora—, y «Ayer» es **un solo
 * toque** para el caso que le sigue en frecuencia. El calendario está abajo para todo lo demás,
 * que es la minoría.
 *
 * ## Por qué el calendario no cambia de alto
 *
 * Seis semanas fijas ([casillasDelMes]) y una grilla de alto constante. Este selector se dibuja
 * adentro de una hoja anclada abajo, donde **cualquier cambio de alto mueve todo bajo el dedo**
 * (ver «Ola 8 · V2» en `QuickAddScreen`): un calendario que midiera 5 filas en un mes y 6 en el
 * siguiente correría los controles justo cuando el dedo va bajando hacia un día.
 *
 * @param seleccionada la fecha que hoy tiene el movimiento (o la que se eligió recién).
 * @param hoy hoy en la zona de la app — se recibe y no se lee acá para poder fijarlo en un test
 *   y para que toda la pantalla comparta el mismo «hoy».
 * @param onPick se elige un día. **No cierra nada**: qué hacer después lo decide quien llama —
 *   la hoja de Agregar cierra el sub-picker al toque (elegir ES la acción completa), la de
 *   editar un movimiento ya guardado se queda abierta para mostrar el aviso antes de confirmar.
 */
@Composable
fun SelectorDeFecha(
    seleccionada: LocalDate,
    hoy: LocalDate,
    onPick: (LocalDate) -> Unit,
    enabled: Boolean = true,
) {
    val ayer = remember(hoy) { hoy.minus(DatePeriod(days = 1)) }
    // El mes que se está mirando arranca en el de la fecha elegida —no en el de hoy—: quien abre
    // esto para corregir un gasto de julio quiere ver julio, no volver a navegar hasta ahí.
    var mesVisible by remember(seleccionada) {
        mutableStateOf(LocalDate(seleccionada.year, seleccionada.monthNumber, 1))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AtajoDeFecha("Hoy", seleccionada == hoy, enabled, Modifier.weight(1f)) { onPick(hoy) }
            AtajoDeFecha("Ayer", seleccionada == ayer, enabled, Modifier.weight(1f)) { onPick(ayer) }
        }

        Spacer(Modifier.height(14.dp))

        // Encabezado del mes. La flecha de avanzar se apaga cuando el mes siguiente es entero
        // futuro (ver [puedeAvanzarMes]) — y se apaga, no desaparece: un control que se va deja
        // la fila con otro ancho y el ojo buscando qué se movió.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlechaDeMes(
                izquierda = true,
                enabled = enabled,
                onClick = { mesVisible = mesAnterior(mesVisible) },
            )
            Text(
                text = etiquetaDeMes(mesVisible),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FlechaDeMes(
                izquierda = false,
                enabled = enabled && puedeAvanzarMes(mesVisible, hoy),
                onClick = { mesVisible = mesSiguiente(mesVisible) },
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Lunes primero, como en Colombia. Las dos «M» son miércoles y martes: es la
            // abreviatura de una letra que usa todo el mundo acá.
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { dia ->
                Box(modifier = Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.Center) {
                    Text(dia, fontSize = 11.sp, color = MinTextFaint)
                }
            }
        }

        val casillas = remember(mesVisible) { casillasDelMes(mesVisible) }
        // `chunked(7)` sobre una lista de largo fijo 42: siempre seis filas, siempre el mismo alto.
        casillas.chunked(7).forEach { semana ->
            Row(modifier = Modifier.fillMaxWidth()) {
                semana.forEach { dia ->
                    CasillaDeDia(
                        dia = dia,
                        seleccionada = dia != null && dia == seleccionada,
                        esHoy = dia != null && dia == hoy,
                        // Un día futuro se dibuja apagado y no responde al toque. Se sigue
                        // dibujando —el mes en curso tiene días de más adelante y borrarlos
                        // rompería la grilla— pero no se puede elegir: ver [esFutura].
                        habilitado = enabled && dia != null && !esFutura(dia, hoy),
                        modifier = Modifier.weight(1f),
                        onClick = { if (dia != null) onPick(dia) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AtajoDeFecha(
    texto: String,
    activo: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (activo) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(if (activo) Modifier else Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (activo) MinOnPrimaryContainer else MinText,
        )
    }
}

@Composable
private fun FlechaDeMes(izquierda: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) MinSurfaceContainerLow else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (izquierda) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
            contentDescription = if (izquierda) "Mes anterior" else "Mes siguiente",
            tint = if (enabled) MinText else MinTextFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CasillaDeDia(
    dia: LocalDate?,
    seleccionada: Boolean,
    esHoy: Boolean,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        // Alto fijo aunque la casilla esté vacía: es lo que mantiene la grilla del mismo alto
        // en un mes de cinco semanas y en uno de seis.
        modifier = modifier.height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dia == null) return@Box
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (seleccionada) MinPrimaryContainer else Color.Transparent)
                .then(
                    if (esHoy && !seleccionada) Modifier.border(1.dp, MinBorderStrong, CircleShape)
                    else Modifier,
                )
                .clickable(enabled = habilitado, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dia.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (seleccionada) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    seleccionada -> MinOnPrimaryContainer
                    !habilitado -> MinTextFaint
                    else -> MinTextMute
                },
            )
        }
    }
}
