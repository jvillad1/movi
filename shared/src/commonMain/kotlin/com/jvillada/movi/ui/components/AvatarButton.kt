package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.theme.MinBorderStrong
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinText

/**
 * F41: el avatar con la inicial ya abría Perfil desde el encabezado del Dashboard, pero nada
 * insinuaba que fuera tocable. Un solo componente — el borde sutil es la única diferencia
 * visual con el círculo de antes — para que Movimientos y Presupuestos lo reciban igual y
 * Perfil quede alcanzable desde ahí también, no solo desde Más.
 */
@Composable
fun AvatarButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MinSurfaceContainerHigh)
            .border(1.dp, MinBorderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = SessionManager.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MinText,
        )
    }
}
