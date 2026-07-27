package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab

private data class MasItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val screen: Screen,
)

private val items = listOf(
    MasItem("Inversiones",  Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFF7DDDB0), Color(0x247DDDB0), Screen.Investments),
    MasItem("Créditos",     Icons.Rounded.CreditCard,      Color(0xFFFFB4AB), Color(0x1FFFB4AB), Screen.Credits),
    MasItem("Metas",        Icons.Rounded.Flag,             Color(0xFFFFD479), Color(0x24FFD479), Screen.Goals),
    MasItem("Extractos",    Icons.Rounded.UploadFile,       Color(0xFFC7BCFF), Color(0x24C7BCFF), Screen.Extractos),
    MasItem("SMS",          Icons.Rounded.Sms,              Color(0xFF81D4FA), Color(0x2481D4FA), Screen.SMSInbox),
    MasItem("Movi AI",      Icons.Rounded.AutoAwesome,      Color(0xFFE8BBF8), Color(0x24E8BBF8), Screen.AIChat),
    MasItem("Recurrentes",  Icons.Rounded.Repeat,           Color(0xFFFFD479), Color(0x1AFFD479), Screen.Recurrentes),
    MasItem("Suscripciones", Icons.Rounded.Autorenew,       Color(0xFF81D4FA), Color(0x2481D4FA), Screen.Subscriptions),
    MasItem("Análisis",     Icons.Rounded.BarChart,         Color(0xFF7DDDB0), Color(0x1A7DDDB0), Screen.Analisis),
    MasItem("Perfil",       Icons.Rounded.ManageAccounts,   Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.Profile),
)

private val editorItem = MasItem("Editor de pantallas", Icons.Rounded.Edit, Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.ScreenEditor)

@Composable
fun MasScreen(onNavigate: (Screen) -> Unit) {
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAdmin = runCatching { Repositories.wallets.isScreenAdmin() }.getOrDefault(false)
    }
    val displayItems = if (isAdmin) items + editorItem else items

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg),
    ) {
        Text(
            text = "Más",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MinText,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(displayItems) { item ->
                MasCard(item, onNavigate)
            }
        }

        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> Unit
            }
        }
    }
}

@Composable
private fun MasCard(item: MasItem, onNavigate: (Screen) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MinSurfaceContainer)
            .clickable { onNavigate(item.screen) }
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.bg),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MinTextDim,
        )
    }
}
