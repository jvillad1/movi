package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi

/**
 * F18: el "+" chiquito arriba a la derecha era la única forma de crear en Presupuestos,
 * Recurrentes y Créditos, y no se leía como botón. Un mismo componente con dos formas:
 * [full] cuando la lista está vacía (es cuando más falta la acción, así que va abajo del
 * encabezado, a todo el ancho) y compacto (ícono + texto) arriba a la derecha cuando ya hay
 * elementos. (Metas lo recibió en F26; Inversiones dejó de existir como pantalla en la Ola 7.)
 *
 * [enabled] apaga el botón cuando la acción no tiene sobre qué obrar (Presupuestos vacío: «Crear
 * estos 0» con todas las propuestas desmarcadas). Apagado pierde el lavanda —fondo de tarjeta y
 * texto apagado, como el botón de las hojas de Categorías— para que no se lea como tocable.
 */
@Composable
fun NewItemButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    enabled: Boolean = true,
) {
    if (full) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Movi.formas.amplia))
                .background(if (enabled) Movi.colores.marca else Movi.colores.tarjeta)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El ícono y el rótulo iban en `Movi.colores.fondo` —el color del FONDO de la app— usado como
            // «lo que va encima del lavanda». Funcionaba de casualidad: nadie había nombrado ese
            // rol. Ahora se llama `sobreMarca` y la prueba de contraste lo vigila.
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = if (enabled) Movi.colores.sobreMarca else Movi.colores.textoApagado,
                modifier = Modifier.size(18.dp).padding(end = Movi.espacios.minimo + 2.dp),
            )
            Text(label, style = Movi.textos.cuerpo, color = if (enabled) Movi.colores.sobreMarca else Movi.colores.textoApagado)
        }
    } else {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(Movi.formas.pleno))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Movi.espacios.minimo, vertical = Movi.espacios.minimo),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.minimo),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (enabled) Movi.colores.marca else Movi.colores.textoApagado
            Icon(Icons.Rounded.Add, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, style = Movi.textos.cuerpo, color = color)
        }
    }
}
