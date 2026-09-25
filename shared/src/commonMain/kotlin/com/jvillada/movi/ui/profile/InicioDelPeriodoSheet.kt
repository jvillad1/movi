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
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.conInicioPropio
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * **La única escritura de los arranques propios**: vuelve a leer el perfil, le aplica [cambio] a
 * sus ajustes **recién leídos** y manda el mapa entero con `PUT /api/users/me` — así es cómo la
 * ruta distingue «no tocar» (no mandarlo) de «ninguno» (mandarlo vacío).
 *
 * Por qué se relee justo antes de escribir: el mapa viaja entero y reemplaza al que había. Armado
 * sobre los ajustes que la pantalla tenía —leídos hace rato en otro aparato, o que no se pudieron
 * leer y quedaron vacíos— borraría las excepciones que la pantalla no conocía, y `periodStarts`
 * decide la ventana de todo (el Inicio, Movimientos, el Disponible, «Tus períodos»). Si esa lectura
 * falla, la excepción sube y no se escribe nada. Si [cambio] ya no acepta lo recién leído
 * (devuelve `null`), tampoco: se lanza [LosPeriodosCambiaron].
 *
 * La usan la hoja de abajo (desde Movimientos) y «Empezar un período nuevo hoy» del detalle de un
 * período: son la misma decisión tomada desde dos lugares.
 */
suspend fun cambiarLosIniciosPropios(cambio: (PeriodSettings) -> PeriodSettings?): UserProfile {
    val recienLeidos = Repositories.wallets.getUserProfile().ajustesDelPeriodo()
    val nuevos = cambio(recienLeidos) ?: throw LosPeriodosCambiaron()
    return Repositories.wallets.updateUserProfile(UpdateProfileRequest(periodStarts = nuevos.iniciosPropios))
}

/** Declara (o quita, con [inicio] `null`) el arranque de [periodo]. Ver [cambiarLosIniciosPropios]. */
suspend fun guardarInicioDelPeriodo(periodo: PeriodoFinanciero, inicio: String?): UserProfile =
    cambiarLosIniciosPropios { it.conInicioPropio(periodo, inicio) }

/** Lo recién leído ya no admite el cambio que se pidió (otro aparato escribió en el medio). */
class LosPeriodosCambiaron : IllegalStateException("Tus períodos cambiaron mientras tanto. Revísalos y vuelve a intentarlo.")

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
    // `null` cuando quien monta la hoja no ofrece la puerta a «Tus períodos» — hoy
    // solo Movimientos la pasa. Sin ella, esta hoja sigue siendo la de siempre.
    onVerPeriodos: (() -> Unit)? = null,
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
        rangoLegibleDe(periodo, ajustes.conInicioPropio(periodo, elegido.toString()))
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
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
            ) {
                Text(
                    text = "¿Cuándo empezó ${nombreDe(periodo)}?",
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Normalmente arranca el ${natural.dayOfMonth}. Si este mes tu sueldo entró " +
                        "antes o después, dilo aquí: el mes anterior se cierra solo ese mismo día.",
                    // Párrafo, no rótulo de fila: la talla del cuerpo con el peso normal de la prosa.
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Normal,
                    color = Movi.colores.textoMedio,
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
                                    .background(if (esElElegido) Movi.colores.marca else Movi.colores.tarjeta)
                                    .clickable(enabled = !saving) { dia = d },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = d.toString(),
                                    style = Movi.textos.cuerpo,
                                    fontWeight = if (esElElegido) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (esElElegido) Movi.colores.fondo else Movi.colores.texto,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                previsualizacion?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!saving) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
                        .clickable(enabled = !saving) { onSave(elegido.toString()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "Guardando…" else "Este mes empezó el $dia",
                        style = Movi.textos.cuerpo,
                        color = if (!saving) Movi.colores.marca else Movi.colores.textoApagado,
                    )
                }

                // La vuelta atrás, y solo cuando hay algo que deshacer: quitar la excepción es tan
                // necesario como ponerla —«me equivoqué, este mes sí empezó cuando siempre»— y sin
                // esta fila la única salida sería declarar a mano el día natural.
                if (periodo.prefijo in ajustes.iniciosPropios) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (saving) "Guardando…" else "Volver al día ${natural.dayOfMonth} de siempre",
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = if (saving) Movi.colores.textoApagado else Movi.colores.marca,
                        modifier = Modifier.clickable(enabled = !saving) { onSave(null) },
                    )
                }

                // Quien se pregunta «¿de cuándo a cuándo va este mes?» —la
                // pregunta que abrió esta hoja— es quien más puede querer comparar con los
                // anteriores.
                if (onVerPeriodos != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Ver tus períodos",
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.marca,
                        modifier = Modifier.clickable(enabled = !saving, onClick = onVerPeriodos),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
