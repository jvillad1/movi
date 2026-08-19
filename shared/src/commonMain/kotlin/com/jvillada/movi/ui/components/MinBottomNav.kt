package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

/**
 * Destinos de la navegación principal. En el teléfono la barra muestra cinco
 * (Inicio · Movimientos · + · Cuentas · Más); en pantalla ancha el rail muestra además
 * Créditos y Presupuestos como entradas propias. Una pantalla declara UN destino
 * (ver `screenNavTab` en App.kt) y cada superficie decide cómo lo resalta: la barra del
 * teléfono pinta CREDITS y BUDGETS como "Más", que es por donde se llega a ellos ahí.
 */
enum class NavTab { HOME, TRANSACTIONS, ADD, ACCOUNTS, CREDITS, BUDGETS, MORE }

/**
 * Ola 4: la barra la pinta App.kt UNA sola vez, debajo de la pantalla activa (igual que el
 * rail en pantalla ancha). Este local vale `true` dentro del subárbol de las pantallas, y
 * hace que las llamadas a [MinBottomNav] que todavía queden dentro de una pantalla no
 * dibujen nada — así una pantalla que aún no se limpió no pinta una segunda barra.
 * Hoy quedan dos (Movimientos y Presupuestos, que se editan en paralelo en otra tarea);
 * cuando se borren esas llamadas, este local y su guarda se van con ellas.
 */
val LocalBottomNavAtRoot = staticCompositionLocalOf { false }

/** Qué ítem de la barra del teléfono se resalta para un destino dado. */
fun NavTab.asBottomBarTab(): NavTab = when (this) {
    NavTab.CREDITS, NavTab.BUDGETS -> NavTab.MORE
    else -> this
}

@Composable
fun MinBottomNav(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    // On wide windows the root-level MinNavRail takes over; calls inside a screen render
    // nothing because App.kt already drew the bar (ver LocalBottomNavAtRoot).
    if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) return
    if (LocalBottomNavAtRoot.current) return
    val highlighted = active?.asBottomBarTab()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinSurfaceContainer)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(NavTab.HOME, "Inicio", Icons.Rounded.Home, highlighted, onTabSelected)
            NavItem(NavTab.TRANSACTIONS, "Movs", Icons.Rounded.SwapVert, highlighted, onTabSelected)

            // Center FAB
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MinPrimary)
                    .clickable { onTabSelected(NavTab.ADD) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Agregar",
                    tint = Color(0xFF1A1040),
                    modifier = Modifier.size(26.dp),
                )
            }

            // F19: Cuentas entra a la barra — antes el único camino era un "Ver todas +" del
            // Inicio que solo aparecía con al menos una cuenta creada. Presupuestos pasó a Más.
            NavItem(NavTab.ACCOUNTS, "Cuentas", Icons.Rounded.AccountBalanceWallet, highlighted, onTabSelected)
            NavItem(NavTab.MORE, "Más", Icons.Rounded.GridView, highlighted, onTabSelected)
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    val isActive = tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isActive) MinPrimaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MinOnPrimaryContainer else MinTextMute,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MinText else MinTextMute,
            letterSpacing = 0.2.sp,
        )
    }
}
