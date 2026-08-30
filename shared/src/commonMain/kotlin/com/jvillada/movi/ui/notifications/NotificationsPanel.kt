package com.jvillada.movi.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.dashboard.NotificationRow

/**
 * F5: la hoja que abre la campana del Inicio. Mismo patrón que las demás hojas de la app
 * (scrim + manija con X, ver [SheetHandleWithClose]) — nada de snackbar, que era la queja
 * original («se ve feo, sobre todo la posición»).
 *
 * Es una vista puramente derivada de [rows] (ver [com.jvillada.movi.ui.dashboard.notificationRows]):
 * no hay "marcar leído" ni estado propio. Cada fila cierra el panel y navega a donde eso se
 * resuelve de verdad.
 */
@Composable
fun NotificationsPanel(
    rows: List<NotificationRow>,
    onDismiss: () -> Unit,
    onRowClick: (Screen) -> Unit,
) {
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
            SheetHandleWithClose(onClose = onDismiss)

            Text(
                text = "Notificaciones",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MinText,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (rows.isEmpty()) {
                // Estado vacío: una línea anclada acá, no un snackbar flotante — la queja
                // original de F5.
                Text(
                    text = "No tienes notificaciones por ahora",
                    fontSize = 13.5.sp,
                    color = MinTextMute,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    rows.forEachIndexed { index, row ->
                        CardRow(
                            left = { Text(row.text, fontSize = 14.sp, color = MinText) },
                            showChevron = true,
                            isLast = index == rows.lastIndex,
                            onClick = { onDismiss(); onRowClick(row.target) },
                        )
                    }
                }
            }
        }
    }
}
