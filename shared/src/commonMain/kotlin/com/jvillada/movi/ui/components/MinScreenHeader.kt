package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab

/**
 * Qué va a la izquierda del título (F60):
 * - [Avatar] en las pantallas RAÍZ (las de la barra/rail: Inicio, Movimientos, Cuentas, Más,
 *   y Presupuestos) — abre Perfil, como siempre.
 * - [Back] en las SUBPANTALLAS (todo lo que se abre desde una raíz): la flecha usa la pila
 *   real (F22) y cae a [fallback] si no hay historial (deep link, recarga de la web).
 */
sealed class HeaderLeading {
    data class Avatar(val onClick: () -> Unit) : HeaderLeading()
    data class Back(val fallback: Screen) : HeaderLeading()
}

/**
 * Regla única para el leading (revisión Ola 7): una pantalla lleva avatar solo cuando ES un
 * destino que el layout actual pinta como pestaña propia — en pantalla ancha, las entradas
 * del rail ([railDestinations]); en el teléfono, las de la barra (`asBottomBarTab()` no la
 * funde en Más). Si no, flecha atrás hacia [fallback]. Así Créditos y Presupuestos llevan
 * avatar en ancho (están en el rail) y flecha a Más en el teléfono (se llega por Más).
 */
@Composable
fun leadingFor(screen: Screen, onProfile: () -> Unit, fallback: Screen): HeaderLeading {
    val tab = navTabFor(screen) ?: return HeaderLeading.Back(fallback)
    if (screenForTab(tab) != screen) return HeaderLeading.Back(fallback)
    val shownAsOwnTab = when (LocalWindowWidthClass.current) {
        WindowWidthClass.Expanded -> railDestinations.any { it.tab == tab }
        WindowWidthClass.Compact -> tab.asBottomBarTab() == tab
    }
    return if (shownAsOwnTab) HeaderLeading.Avatar(onProfile) else HeaderLeading.Back(fallback)
}

/**
 * F60: el encabezado único de TODAS las pantallas. Antes cada una armaba su propio Row
 * (26.sp acá, 17.sp allá, flecha `ArrowBack` o `ArrowBackIosNew`, con o sin avatar…) y el
 * dueño lo notó. Un solo componente: leading a la izquierda, título con el MISMO rótulo que
 * el menú, acción propia a la derecha si la hay, y una Hairline debajo.
 *
 * @param subtitle línea secundaria opcional (p.ej. «3 por confirmar» en Mensajes del banco).
 * @param action   slot a la derecha (NewItemButton, lupa, campana…); null si la pantalla no
 *                 tiene acción propia.
 */
@Composable
fun MinScreenHeader(
    title: String,
    leading: HeaderLeading,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    // La flecha lleva 11.dp de padding alrededor (área tocable ≈44dp); el padding horizontal
    // del Row se reduce en la misma medida para que el título no se corra respecto del avatar.
    val backInset = if (leading is HeaderLeading.Back) 11.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp - backInset, end = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (leading) {
            is HeaderLeading.Avatar -> AvatarButton(onClick = leading.onClick)
            is HeaderLeading.Back -> {
                val goBack = LocalGoBack.current
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = MinText,
                    modifier = Modifier
                        .clickable { goBack(leading.fallback) }
                        .padding(11.dp)
                        .size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MinTextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (action != null) action()
    }
    Hairline()
}
