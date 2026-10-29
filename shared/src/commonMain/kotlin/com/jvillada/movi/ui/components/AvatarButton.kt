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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.anilloDeFoco

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
            // Vuelta 55: con Tab, el avatar mostraba un cuadrado gris tenue; ahora el anillo de tinta.
            .anilloDeFoco(CircleShape)
            .clip(CircleShape)
            .background(avatarColorOrDefault(SessionManager.avatarColor))
            .border(Medida.trazo, Movi.colores.borde, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = inicialesDelUsuario(SessionManager.userName),
            // Blanco en los dos temas: la paleta de avatares está construida entera para contrastar
            // contra blanco (ver AvatarPalette.kt), y el color del círculo lo elige el dueño, no el
            // tema. Un token de texto acá se rompería con la mitad de los colores de la paleta; por
            // eso es `sobreAvatar` y no `sobreMarca` (vuelta de diseño 18).
            style = Movi.textos.cuerpoFuerte,
            color = Movi.colores.sobreAvatar,
        )
    }
}

/**
 * Convierte un hex `"#RRGGBB"` de [AvatarPalette] a [Color]. `null` (todavía sin cargar el
 * perfil) o cualquier cosa que no parsee cae a [AvatarPalette.DEFAULT] — nunca a un color
 * inventado: la paleta es la única fuente de colores válidos (ver AvatarPalette.kt).
 * `internal`: se usa también desde Ajustes (`ui.mas.MasScreen`) y las hojas de `ui.profile`, en el mismo módulo.
 */
internal fun avatarColorOrDefault(hex: String?): Color {
    val safe = hex?.takeIf { AvatarPalette.isValid(it) } ?: AvatarPalette.DEFAULT
    return runCatching {
        Color(("FF" + safe.removePrefix("#")).toLong(16))
    }.getOrElse { Color(("FF" + AvatarPalette.DEFAULT.removePrefix("#")).toLong(16)) }
}

/**
 * **Las iniciales del avatar del usuario** —la única función para todos sus avatares (vuelta 41): el
 * del encabezado del Inicio, la tarjeta del perfil en «Tú» y [AvatarButton]—: las del primer nombre y
 * el primer apellido («Juan Villada» → «JV»), o la del nombre solo; «?» sin nombre. Antes cada avatar
 * tenía la suya y para el mismo usuario el Inicio decía «D» y «Tú» «DM». Pura.
 */
fun inicialesDelUsuario(nombre: String?): String {
    val palabras = nombre?.trim()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
    if (palabras.isEmpty()) return "?"
    return palabras.take(2).joinToString("") { it.first().uppercase() }
}
