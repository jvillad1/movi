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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.theme.MinBorderStrong

/**
 * F41: el avatar con la inicial ya abría Perfil desde el encabezado del Dashboard, pero nada
 * insinuaba que fuera tocable. Un solo componente — el borde sutil es la única diferencia
 * visual con el círculo de antes — para que Movimientos y Presupuestos lo reciban igual y
 * Perfil quede alcanzable desde ahí también, no solo desde Más.
 *
 * F42 · F46: el círculo gris fijo se reemplaza por [SessionManager.avatarColor] — el mismo
 * color que se ve en el encabezado de Perfil, elegido de [AvatarPalette]. Iniciales siempre en
 * blanco: la paleta entera está pensada para ese contraste (ver AvatarPalette.kt), así que no
 * hace falta calcular nada por color.
 */
@Composable
fun AvatarButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(avatarColorOrDefault(SessionManager.avatarColor))
            .border(1.dp, MinBorderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = SessionManager.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/**
 * Convierte un hex `"#RRGGBB"` de [AvatarPalette] a [Color]. `null` (todavía sin cargar el
 * perfil) o cualquier cosa que no parsee cae a [AvatarPalette.DEFAULT] — nunca a un color
 * inventado: la paleta es la única fuente de colores válidos (ver AvatarPalette.kt).
 * `internal`: se usa también desde `ui.profile` (PerfilScreen y sus hojas), en el mismo módulo.
 */
internal fun avatarColorOrDefault(hex: String?): Color {
    val safe = hex?.takeIf { AvatarPalette.isValid(it) } ?: AvatarPalette.DEFAULT
    return runCatching {
        Color(("FF" + safe.removePrefix("#")).toLong(16))
    }.getOrElse { Color(("FF" + AvatarPalette.DEFAULT.removePrefix("#")).toLong(16)) }
}
