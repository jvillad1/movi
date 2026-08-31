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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            // El contenido de la hoja se desplaza.
            //
            // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
            // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
            // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
            // propia hoja. Sin manera de llegar a él.
            //
            // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
            // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
            // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {

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

                // Cinco filas de siete, no un `LazyVerticalGrid`.
                //
                // El grid tenía `.height(196.dp)` fija y su contenido mide **224**: cinco filas de
                // 40 dp con 6 de separación. La revisión lo midió a 411×731, o sea con espacio de
                // sobra en la pantalla: el «31» se dibujaba solo 4 de sus 24 dp — **los días 29,
                // 30 y 31 quedaban cortados**, justo los que esta hoja se toma el trabajo de
                // explicar en el aviso de abajo.
                //
                // Y el scroll nuevo de la hoja no podía revelarlos, porque el alto del grid es
                // fijo: había que arrastrar dentro de una ventanita de 196 dp en el medio. Es el
                // mismo anti-patrón que `HojaAgregarGeometriaTest` ya documenta — dos áreas
                // desplazables anidadas peleándose el gesto del dedo.
                //
                // 31 celdas de tamaño conocido no ganan nada con ser lazy. Con filas normales el
                // calendario entero se ve, la hoja tiene un solo desplazamiento, y desaparece el
                // alto mágico que había que mantener a mano cada vez que cambiara el tamaño de una
                // celda.
                (1..31).chunked(7).forEach { fila ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        fila.forEach { d ->
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
                    Spacer(Modifier.height(6.dp))
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
}
