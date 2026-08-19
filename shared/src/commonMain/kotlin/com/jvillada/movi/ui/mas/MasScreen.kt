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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen

private data class MasItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val screen: Screen,
)

private val items = listOf(
    // F19: Cuentas era invisible sin al menos una cuenta ya creada (el "Ver todas +" del Inicio
    // solo aparece con la lista no vacía) — entra acá como primer acceso, incondicional.
    MasItem("Cuentas",      Icons.Rounded.AccountBalanceWallet, Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.Accounts),
    // Ola 4: Presupuestos dejó su lugar en la barra inferior a Cuentas y entra acá, justo después.
    MasItem("Presupuestos", Icons.Rounded.PieChart,         Color(0xFF7DDDB0), Color(0x1A7DDDB0), Screen.Budgets),
    MasItem("Inversiones",  Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFF7DDDB0), Color(0x247DDDB0), Screen.Investments),
    MasItem("Créditos",     Icons.Rounded.CreditCard,      Color(0xFFFFB4AB), Color(0x1FFFB4AB), Screen.Credits),
    MasItem("Metas",        Icons.Rounded.Flag,             Color(0xFFFFD479), Color(0x24FFD479), Screen.Goals),
    MasItem("Extractos",    Icons.Rounded.UploadFile,       Color(0xFFC7BCFF), Color(0x24C7BCFF), Screen.Extractos),
    MasItem("SMS",          Icons.Rounded.Sms,              Color(0xFF81D4FA), Color(0x2481D4FA), Screen.SMSInbox),
    MasItem("Movi AI",      Icons.Rounded.AutoAwesome,      Color(0xFFE8BBF8), Color(0x24E8BBF8), Screen.AIChat),
    MasItem("Recurrentes",  Icons.Rounded.Repeat,           Color(0xFFFFD479), Color(0x1AFFD479), Screen.Recurrentes),
    MasItem("Suscripciones", Icons.Rounded.Autorenew,       Color(0xFF81D4FA), Color(0x2481D4FA), Screen.Subscriptions),
    // F40: "Análisis" no analizaba — era un índice con cifras, y eso ahora es el Inicio.
    MasItem("Perfil",       Icons.Rounded.ManageAccounts,   Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.Profile),
)

@Composable
fun MasScreen(onNavigate: (Screen) -> Unit) {
    // F47 · F48: "Editor de pantallas" vivía acá, agregado a la grilla después de que
    // isScreenAdmin() resolvía — eso hacía que la grilla "saltara" al cargar, y además era
    // una herramienta de administración mezclada con Créditos y Metas. Se mudó al final de
    // Perfil, en una sección "Administración" visible solo para quien administra el Inicio.

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
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(items) { item ->
                MasCard(item, onNavigate)
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
