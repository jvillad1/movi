package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.*

@Composable
fun ScopeToggle(
    value: Scope,
    onChange: (Scope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Movi.formas.pleno))
            .background(Movi.colores.fondo)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.pleno))
            .padding(3.dp),
    ) {
        listOf(Scope.SELF to "Individual", Scope.FAMILY to "Familiar").forEach { (scope, label) ->
            val isActive = value == scope
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    // El riel va del color del FONDO y la pastilla activa del de la TARJETA: el
                    // activo sube, no cambia de gris. Antes eran dos grises de la misma familia
                    // separados por 0,12 de contraste y no se sabía cuál estaba elegido.
                    .background(if (isActive) Movi.colores.tarjeta else Color.Transparent)
                    .clickable { onChange(scope) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Movi.colores.marca,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                    )
                }
                Text(
                    text = label,
                    style = Movi.textos.cuerpo,
                    color = if (isActive) Movi.colores.texto else Movi.colores.textoMedio,
                )
            }
        }
    }
}
