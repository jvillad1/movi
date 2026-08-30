package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import kotlinx.datetime.Clock

/**
 * Elegir el día en que arranca el período financiero.
 *
 * Existe porque el dueño lo pidió como ajuste, no como constante: *«otros usuarios no van a poder
 * gestionar desde Claude cambios en la app»*. Un valor por defecto sensato está bien; un valor
 * **solo** por defecto deja a todos los demás sin la función.
 *
 * La hoja muestra en vivo qué período sería hoy con el día elegido — es la única forma de que
 * «26» signifique algo antes de guardarlo.
 */
@Composable
fun PeriodoSheet(
    cutoffActual: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    var dia by remember { mutableStateOf(cutoffActual.coerceIn(1, 31)) }
    val settings = remember(dia) { PeriodSettings(cutoffDay = dia) }
    val hoy = remember(dia) { periodoDe(Clock.System.now().toEpochMilliseconds(), settings) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

            Text(
                text = "¿Qué día empieza tu mes?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            Text(
                text = "Movi cuenta tus ingresos, gastos y presupuestos sobre esta ventana. " +
                    "Si te pagan el 26, elige 26 y el mes te va a cuadrar con el sueldo.",
                fontSize = 13.sp,
                color = MinTextMute,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // La consecuencia, en vivo: sin esto «26» es un número sin significado.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MinSurfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Hoy estarías en ${nombreDe(hoy)}",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rangoLegibleDe(hoy, settings) ?: "Del 1 al último día del mes",
                    fontSize = 12.5.sp,
                    color = MinTextMute,
                )
                if (settings.esMesDeCalendario) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Es el mes de calendario, como viene por defecto.",
                        fontSize = 11.5.sp,
                        color = MinTextFaint,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(196.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items((1..31).toList()) { d ->
                    val elegido = d == dia
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (elegido) MinPrimary else MinSurfaceContainer)
                            .clickable(enabled = !saving) { dia = d },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = d.toString(),
                            fontSize = 13.sp,
                            fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (elegido) MinBg else MinText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Un 29, 30 o 31 no existe en todos los meses. Se dice ANTES de guardar, porque es la
            // duda que aparece justo al tocar ese número.
            if (dia > 28) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "En los meses que no tienen día $dia, el corte cae el último día del mes.",
                    fontSize = 12.sp,
                    color = MinTextMute,
                    lineHeight = 16.sp,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = error, fontSize = 12.5.sp, color = MinExpense, lineHeight = 17.sp)
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (saving) MinSurfaceContainerHighest else MinPrimary)
                    .clickable(enabled = !saving) { onSave(dia) }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Guardando…" else "Guardar",
                    color = if (saving) MinTextMute else MinBg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
