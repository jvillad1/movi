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
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinText

/**
 * F18: el "+" chiquito arriba a la derecha era la única forma de crear en Presupuestos,
 * Recurrentes y Créditos, y no se leía como botón. Un mismo componente con dos formas:
 * [full] cuando la lista está vacía (es cuando más falta la acción, así que va abajo del
 * encabezado, a todo el ancho) y compacto (ícono + texto) arriba a la derecha cuando ya hay
 * elementos. (Metas lo recibió en F26; Inversiones dejó de existir como pantalla en la Ola 7.)
 */
@Composable
fun NewItemButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    full: Boolean = false,
) {
    if (full) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MinPrimary)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MinBg, modifier = Modifier.size(18.dp).padding(end = 6.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinBg)
        }
    } else {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinPrimary)
        }
    }
}
