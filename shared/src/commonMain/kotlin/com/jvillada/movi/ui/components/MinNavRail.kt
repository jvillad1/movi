package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

/**
 * Contraparte de [MinBottomNav] en pantalla ancha: un rail a la izquierda, pintado una vez en la
 * raíz de App.kt. Ola C: muestra **las mismas cuatro pestañas** que la barra del teléfono
 * ([destinosPrincipales]) y debajo «Agregar» — sin Créditos, Presupuestos ni Más, que ahora viven
 * adentro de Patrimonio, de Plan y del avatar.
 */
@Composable
fun MinNavRail(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(216.dp)
            .fillMaxHeight()
            .background(Movi.colores.tarjeta)
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.margen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "movi",
            // Tamaño suelto a propósito: es el logotipo del riel, no un título; ningún estilo de la escala es ese papel.
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Movi.colores.marca,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
        )

        destinosPrincipales.forEach { dest ->
            RailItem(dest.tab, dest.label, dest.icon, active, onTabSelected)
        }

        Spacer(Modifier.height(12.dp))

        // Primary action — mirrors the bottom-nav center FAB
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Movi.colores.marca)
                .clickable { onTabSelected(NavTab.ADD) }
                .padding(horizontal = Movi.espacios.amplio, vertical = 10.dp),
        ) {
            // El mismo `Color(0xFF1A1040)` a mano que estaba en la barra del teléfono, repetido
            // dos veces más acá. Tres copias de un color sin nombre: ahora es `sobreMarca`.
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Movi.colores.sobreMarca,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Agregar",
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.SemiBold,
                color = Movi.colores.sobreMarca,
            )
        }
    }
}

@Composable
private fun RailItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    val isActive = tab == active
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.normal))
            .background(if (isActive) Movi.colores.marca.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Movi.colores.marca else Movi.colores.textoApagado,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = Movi.textos.cuerpo,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
        )
    }
}
