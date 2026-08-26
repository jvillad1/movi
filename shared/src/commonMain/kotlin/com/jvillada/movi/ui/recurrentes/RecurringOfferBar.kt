package com.jvillada.movi.ui.recurrentes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinOnPrimaryContainer
import com.jvillada.movi.theme.MinPrimaryContainer
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextMute

/**
 * Ola 9 · B — la barra que ofrece convertir en recurrente el movimiento recién guardado.
 *
 * **Es un ofrecimiento, no una pregunta**, y la forma lo dice: aparece junto al «guardado», no
 * tiene botón de «No», no tapa nada que el dueño estuviera usando y se va sola a los pocos
 * segundos (ver App.kt). No contestar ES la respuesta, y no cuesta ni un toque — que es lo que
 * hace que se pueda dejar prendida aunque el dueño anote comida cuatro veces por semana. La ✕
 * está para el impaciente, no para que el diseño funcione.
 *
 * El nombre del movimiento va entre comillas para que se entienda de qué se está hablando: la
 * barra aparece un instante después de cerrarse la hoja, y sin el nombre habría que adivinar a
 * cuál de los dos gastos que acaba de anotar se refiere.
 */
@Composable
fun RecurringOfferBar(
    prefill: RecurringPrefill,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MinSurfaceContainerHigh)
                .border(1.dp, MinBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guardado",
                        fontSize = 12.sp,
                        color = MinTextMute,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        // Una sola línea de pregunta, corta: si necesita dos renglones para
                        // entenderse, ya es una interrupción y no un ofrecimiento.
                        text = "¿\"${prefill.name}\" se repite todos los meses?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                        lineHeight = 19.sp,
                    )
                }
                // Ícono, no el carácter "✕": la fuente del canvas no trae ese glifo y salía un
                // cuadradito. Es el mismo Close que usan las hojas de la app.
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar",
                    tint = MinTextMute,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = 12.dp, top = 2.dp, bottom = 4.dp)
                        .size(18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinPrimaryContainer)
                        .clickable(onClick = onAccept)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Sí, anótalo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinOnPrimaryContainer,
                    )
                }
            }
        }
    }
}
