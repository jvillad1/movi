package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SwapVert
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

/** Un destino del rail: pestaña + rótulo + ícono. */
data class RailDestination(val tab: NavTab, val label: String, val icon: ImageVector)

/**
 * Lo que el rail muestra, en orden. Es la ÚNICA fuente: el rail la pinta y Más (F59) la usa
 * para no repetir en pantalla ancha los destinos que ya están a la izquierda. Agregar una
 * entrada acá alcanza para que aparezca en el rail y desaparezca de Más.
 */
val railDestinations: List<RailDestination> = listOf(
    RailDestination(NavTab.HOME, "Inicio", Icons.Rounded.Home),
    RailDestination(NavTab.TRANSACTIONS, "Movimientos", Icons.Rounded.SwapVert),
    RailDestination(NavTab.ACCOUNTS, "Cuentas", Icons.Rounded.AccountBalanceWallet),
    RailDestination(NavTab.CREDITS, "Créditos", Icons.Rounded.CreditCard),
    RailDestination(NavTab.BUDGETS, "Presupuestos", Icons.Rounded.PieChart),
    RailDestination(NavTab.MORE, "Más", Icons.Rounded.GridView),
)

/**
 * Wide-window counterpart of MinBottomNav: a left rail rendered once at the
 * App root. Same active-pill language; en pantalla ancha hay lugar para mostrar
 * además Créditos y Presupuestos como entradas propias (en el teléfono viven en Más).
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
            .background(MinSurfaceContainer)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "movi",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MinPrimary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
        )

        railDestinations.forEach { dest ->
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
                .background(MinPrimary)
                .clickable { onTabSelected(NavTab.ADD) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Color(0xFF1A1040),
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Agregar",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1040),
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) MinPrimaryContainer else Color.Transparent)
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) MinOnPrimaryContainer else MinTextMute,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MinText else MinTextMute,
            letterSpacing = 0.2.sp,
        )
    }
}
