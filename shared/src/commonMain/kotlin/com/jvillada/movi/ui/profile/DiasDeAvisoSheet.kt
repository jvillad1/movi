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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.recurrentes.reminderLeadHint

/**
 * Con cuántos días de anticipación avisar un vencimiento.
 *
 * Existe por la regla del dueño: *«otros usuarios no van a poder gestionar desde Claude cambios en
 * la app»*. Este valor vivía **solo** como variable de entorno del server — global para todos y
 * fuera del alcance de cualquiera que use Movi.
 *
 * El **0 es una opción legítima**, no un caso de borde: hay quien quiere el aviso el mismo día y
 * no antes. Por eso la frase cambia entera en vez de decir «0 días antes».
 */
@Composable
fun DiasDeAvisoSheet(
    diasActuales: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    var dias by remember { mutableStateOf(diasActuales.coerceIn(0, 30)) }

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
                text = "¿Con cuánta anticipación te avisamos?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            Text(
                text = reminderLeadHint(dias),
                fontSize = 13.sp,
                color = MinTextMute,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 18.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0, 1, 2, 3, 5, 7).forEach { d ->
                    val elegido = d == dias
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (elegido) MinPrimary else MinSurfaceContainer)
                            .clickable(enabled = !saving) { dias = d },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = d.toString(),
                            fontSize = 14.sp,
                            fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (elegido) MinBg else MinText,
                        )
                    }
                }
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
                    .clickable(enabled = !saving) { onSave(dias) }
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
