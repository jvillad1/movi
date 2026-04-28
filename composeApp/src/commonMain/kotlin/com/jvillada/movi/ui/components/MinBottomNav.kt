package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

enum class NavTab { HOME, TRANSACTIONS, ANALYSIS, PROFILE }

@Composable
fun MinBottomNav(
    active: NavTab,
    onTabSelected: (NavTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinSurfaceContainer)
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        NavItem(
            tab = NavTab.HOME,
            label = "Inicio",
            icon = "⌂",
            active = active,
            onTabSelected = onTabSelected,
        )
        NavItem(
            tab = NavTab.TRANSACTIONS,
            label = "Movs",
            icon = "≡",
            active = active,
            onTabSelected = onTabSelected,
        )
        NavItem(
            tab = NavTab.ANALYSIS,
            label = "Análisis",
            icon = "↗",
            active = active,
            onTabSelected = onTabSelected,
        )
        NavItem(
            tab = NavTab.PROFILE,
            label = "Perfil",
            icon = "◎",
            active = active,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    label: String,
    icon: String,
    active: NavTab,
    onTabSelected: (NavTab) -> Unit,
) {
    val isActive = tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onTabSelected(tab) },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isActive) MinPrimaryContainer else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
                color = if (isActive) MinOnPrimaryContainer else MinTextMute,
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) MinText else MinTextMute,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun NavPill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinBg)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(134.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MinText.copy(alpha = 0.85f))
        )
    }
}
