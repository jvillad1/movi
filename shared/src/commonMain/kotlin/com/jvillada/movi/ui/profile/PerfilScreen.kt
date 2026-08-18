package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun PerfilScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Perfil", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.8).sp)
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Ajustes", tint = MinTextDim, modifier = Modifier.size(22.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Identity card
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val displayName = SessionManager.userName ?: "Usuario"
                        val initials = displayName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MinSurfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials.ifEmpty { "U" }, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.5).sp)
                        }
                        Column {
                            Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                            Text(SessionManager.userEmail ?: "", fontSize = 12.5.sp, color = MinTextMute)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "PREMIUM · FAMILIAR",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                            )
                        }
                    }
                }
            }

            // Archetype card
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Mi perfil financiero")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("TU ARQUETIPO", fontSize = 11.sp, color = MinTextMute, letterSpacing = 1.4.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Por definir", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = MinTextMute, letterSpacing = (-0.7).sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Completa el cuestionario financiero para descubrir tu arquetipo.",
                            fontSize = 13.sp,
                            color = MinTextDim,
                            lineHeight = 19.sp,
                        )
                    }

                    if (PushOptIn.supported) {
                        Spacer(Modifier.height(14.dp))
                        var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
                        var refreshTick by remember { mutableStateOf(0) }
                        LaunchedEffect(refreshTick) {
                            // el flujo JS es async: refrescar unas veces tras cada acción
                            repeat(20) {
                                kotlinx.coroutines.delay(600)
                                pushStatus = PushOptIn.status()
                            }
                        }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Notificaciones push", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(
                                        when (pushStatus) {
                                            "enabled" -> "Activadas en este dispositivo"
                                            "denied" -> "Bloqueadas por el navegador"
                                            else -> "Recibe tus pagos próximos"
                                        },
                                        fontSize = 12.sp, color = MinTextMute,
                                    )
                                }
                                Text(
                                    if (pushStatus == "enabled") "Desactivar" else "Activar",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = if (pushStatus == "denied") MinTextFaint else MinText,
                                    modifier = Modifier.clickable(enabled = pushStatus != "denied") {
                                        if (pushStatus == "enabled") PushOptIn.disable() else PushOptIn.enable()
                                        refreshTick++
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Meta principal
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Meta principal", action = "Editar")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(
                            left = { Text("Sin meta definida", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinTextMute) },
                            sub = "Ve a Metas para crear tu primera meta",
                            isLast = true,
                            onClick = { onNavigate(Screen.Goals) },
                        )
                    }
                }
            }

            // Cuenta
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Cuenta")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(
                            left = { Text("Familia", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            showChevron = true,
                        )
                        CardRow(
                            left = { Text("Privacidad y datos", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "SMS y extractos cifrados",
                            showChevron = true,
                            onClick = { onNavigate(Screen.SMSInbox) },
                        )
                        CardRow(
                            left = { Text("Notificaciones", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "Alertas inteligentes activas",
                            showChevron = true,
                            isLast = true,
                        )
                    }
                }
            }

            // Logout button
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainer)
                        .clickable { onLogout() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cerrar sesión", fontSize = 14.sp, color = MinExpense, fontWeight = FontWeight.Medium)
                }
            }
        }

        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd())
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> onNavigate(Screen.Mas)
            }
        }
    }
}

@Composable
private fun ScaleRow(label: String, value: Int, hint: String, max: Int = 5) {
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MinText)
            Text(hint, fontSize = 12.sp, color = MinTextMute, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (0 until max).forEach { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < value) MinPrimary else MinSurfaceContainerHigh)
                )
            }
        }
    }
}
