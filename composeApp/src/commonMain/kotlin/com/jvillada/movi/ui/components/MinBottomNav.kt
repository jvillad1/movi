package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

enum class NavTab { HOME, TRANSACTIONS, ADD, BUDGETS, MORE }

@Composable
fun MinBottomNav(
    active: NavTab,
    onTabSelected: (NavTab) -> Unit,
) {
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
            NavItem(NavTab.HOME, "Inicio", Icons.Rounded.Home, active, onTabSelected)
            NavItem(NavTab.TRANSACTIONS, "Movs", Icons.Rounded.SwapVert, active, onTabSelected)

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

            NavItem(NavTab.BUDGETS, "Presupuesto", Icons.Rounded.PieChart, active, onTabSelected)
            NavItem(NavTab.MORE, "Más", Icons.Rounded.GridView, active, onTabSelected)
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab,
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
