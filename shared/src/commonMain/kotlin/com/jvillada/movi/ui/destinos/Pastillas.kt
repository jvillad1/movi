package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/**
 * **La pastilla de acción de «Cuentas de otros»**: la misma forma que «Guardar como…» de la fila
 * (marca sobre marca al 16 %), para que «Guardar», «Es mía» e «Ignorar» se lean como botones del
 * mismo juego. [principal] = la acción que se espera; las demás van en gris.
 */
@Composable
internal fun Pastilla(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    enabled: Boolean = true,
) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = when {
            !enabled -> Movi.colores.textoApagado
            principal -> Movi.colores.marca
            else -> Movi.colores.textoMedio
        },
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (principal) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.fondo)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Un enlace de texto (marca, sin fondo), como «Agregar una nota». */
@Composable
internal fun Enlace(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = if (enabled) Movi.colores.marca else Movi.colores.textoApagado,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
