package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * **«Este mes no empezó cuando siempre.»**
 *
 * El dueño lo pidió así: *«puede indicar el comienzo de un nuevo periodo que implícitamente indica
 * el cierre del anterior en cualquier momento, esto porque no siempre los pagos suceden misma fecha
 * y puede que un mes dure más o menos el periodo»*.
 *
 * Su corte es 25 porque ahí le suele caer el salario, pero un 25 que cae domingo se paga el 24 o el
 * 26. Con un día fijo el salario queda del lado equivocado del corte — el error que el corte vino a
 * evitar. Esta hoja es la excepción, declarada a mano y para **un solo período**.
 *
 * ### Dos cosas que la hacen entendible
 *
 * 1. **Solo se ofrecen los días del mes en que ese período puede arrancar.** Con corte 25,
 *    «septiembre» arranca en agosto: ofrecerle días de septiembre sería ofrecerle partir la línea
 *    de tiempo (ver `inicioDelPeriodo`, que ignora un valor así).
 * 2. **El rango resultante se lee en vivo, antes de guardar.** Es lo único que hace que «24»
 *    signifique algo: se ve que este mes dura un día más y que el anterior se acorta solo.
 */
@Composable
fun InicioDelPeriodoSheet(
    periodo: PeriodoFinanciero,
    ajustes: PeriodSettings,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    val natural = remember(periodo, ajustes.cutoffDay) {
        inicioDelPeriodo(periodo, ajustes.copy(iniciosPropios = emptyMap()))
    }
    val actual = remember(periodo, ajustes) { inicioDelPeriodo(periodo, ajustes) }
    var dia by remember(actual) { mutableStateOf(actual.dayOfMonth) }

    // Cuántos días tiene el mes en que este período arranca — un 31 no existe en todos, y ofrecer
    // una casilla que no se puede elegir es peor que no ofrecerla.
    val diasDelMes = remember(natural) {
        val primero = LocalDate(natural.year, natural.month.number, 1)
        val siguiente = if (natural.month.number == 12) LocalDate(natural.year + 1, 1, 1)
        else LocalDate(natural.year, natural.month.number + 1, 1)
        siguiente.toEpochDays() - primero.toEpochDays()
    }
    val elegido = remember(dia, natural, diasDelMes) {
        LocalDate(natural.year, natural.month.number, dia.coerceIn(1, diasDelMes))
    }
    /** El rango que quedaría, calculado con la misma función que después lo pinta en Movimientos. */
    val previsualizacion = remember(elegido, periodo, ajustes) {
        rangoLegibleDe(
            periodo,
            ajustes.copy(iniciosPropios = ajustes.iniciosPropios + (periodo.prefijo to elegido.toString())),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
            ) {
                Text(
                    text = "¿Cuándo empezó ${nombreDe(periodo)}?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MinText,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Normalmente arranca el ${natural.dayOfMonth}. Si este mes tu sueldo entró " +
                        "antes o después, dilo aquí: el mes anterior se cierra solo ese mismo día.",
                    fontSize = 12.5.sp,
                    color = MinTextMute,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(16.dp))

                // Solo los días del mes en que ESTE período puede arrancar. Ver el KDoc.
                (1..diasDelMes).chunked(7).forEach { fila ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        fila.forEach { d ->
                            val esElElegido = d == dia
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (esElElegido) MinPrimary else MinSurfaceContainer)
                                    .clickable(enabled = !saving) { dia = d },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = d.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (esElElegido) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (esElElegido) MinBg else MinText,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                previsualizacion?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it, fontSize = 12.5.sp, color = MinTextMute)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it, fontSize = 12.5.sp, color = MinExpense, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!saving) MinPrimaryContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !saving) { onSave(elegido.toString()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "Guardando…" else "Este mes empezó el $dia",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!saving) MinOnPrimaryContainer else MinTextFaint,
                    )
                }

                // La vuelta atrás, y solo cuando hay algo que deshacer: quitar la excepción es tan
                // necesario como ponerla —«me equivoqué, este mes sí empezó cuando siempre»— y sin
                // esta fila la única salida sería declarar a mano el día natural.
                if (periodo.prefijo in ajustes.iniciosPropios) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (saving) "Guardando…" else "Volver al día ${natural.dayOfMonth} de siempre",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (saving) MinTextFaint else MinPrimary,
                        modifier = Modifier.clickable(enabled = !saving) { onSave(null) },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
