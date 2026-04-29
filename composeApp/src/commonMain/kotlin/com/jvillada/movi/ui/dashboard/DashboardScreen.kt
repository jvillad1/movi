package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun DashboardScreen(
    onNavigate: (Screen) -> Unit,
) {
    var scope by remember { mutableStateOf(Scope.SELF) }
    val isFamily = scope == Scope.FAMILY

    var liveBalance by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getWallets() }
            .onSuccess { list -> liveBalance = list.sumOf { it.balance }.toLong() }
    }

    val balance   = liveBalance ?: if (isFamily) 4_870_000L else 1_840_000L
    val ingresos  = if (isFamily) 9_200_000L else 4_500_000L
    val egresos   = if (isFamily) 4_330_000L else 2_660_000L
    val flujo     = ingresos - egresos

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MinSurfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("C", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                    }
                    Text("Camilo", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
                }
                // Bell with dot
                Box {
                    Text("🔔", fontSize = 18.sp, color = MinTextDim)
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MinExpense)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            // Scope toggle
            ScopeToggle(
                value = scope,
                onChange = { scope = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // Hero card
                item {
                    MinCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text(
                            text = "Balance · abril",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinTextMute,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatCOP(balance),
                            fontSize = 44.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            color = MinText,
                            letterSpacing = (-1.6).sp,
                            lineHeight = 44.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("↑", fontSize = 13.sp, color = MinIncome, fontWeight = FontWeight.Bold)
                            Text(
                                text = "+12,4%",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MinIncome,
                                fontWeight = FontWeight.Medium,
                            )
                            Text("vs. marzo", fontSize = 13.sp, color = MinTextMute)
                        }
                        Spacer(Modifier.height(18.dp))
                        Sparkline(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            family = isFamily,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf("1 abr", "8", "15", "22", "29").forEach { d ->
                                Text(d, fontSize = 10.5.sp, color = MinTextFaint, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Hairline()
                        Spacer(Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                Triple("Ingresos", formatMillions(ingresos), "+8,1%"),
                                Triple("Egresos",  formatMillions(egresos),  "−2,4%"),
                                Triple("Flujo",    formatMillions(flujo),    "+24,8%"),
                            ).forEach { (label, value, delta) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(6.dp))
                                    Text(value, fontSize = 14.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(delta, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinIncome)
                                }
                            }
                        }
                    }
                }

                // Family aportes card
                if (isFamily) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        MinCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(18.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Aportes del mes", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
                                Text("3 miembros", fontSize = 12.sp, color = MinTextMute)
                            }
                            Spacer(Modifier.height(14.dp))
                            listOf(
                                Triple("Camilo", 1_120_000L, 0.52f),
                                Triple("Laura",    840_000L, 0.39f),
                                Triple("Mateo",    210_000L, 0.09f),
                            ).forEachIndexed { i, (name, amt, pct) ->
                                if (i > 0) Hairline()
                                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(name, fontSize = 14.sp, color = MinText)
                                        Text(formatCOP(amt), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-0.3).sp)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(MinHairline)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(pct)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(MinText.copy(alpha = 0.85f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Alertas
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Alertas", count = 2)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            CardRow(
                                left = { Text("Restaurantes al 80%", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "$640.000 de $800.000 este mes",
                                showChevron = true,
                            )
                            CardRow(
                                left = { Text("Cuota Bancolombia", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "Se descuenta el 30 de abril",
                                right = { MonoText("$580.000", 14.5f) },
                                showChevron = true,
                                isLast = true,
                            )
                        }
                    }
                }

                // Patrimonio
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Patrimonio", action = "Ver todo")
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            CardRow(
                                left = { Text("Inversiones", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "+8,4% YTD",
                                right = { MonoText("$12.480.000", 14.5f) },
                                showChevron = true,
                                onClick = { onNavigate(Screen.Investments) },
                            )
                            CardRow(
                                left = { Text("Crédito Bancolombia", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "Cuota en 5 días",
                                right = { MonoText("$4.320.000", 14.5f) },
                                showChevron = true,
                                onClick = { onNavigate(Screen.Credits) },
                            )
                            CardRow(
                                left = { Text("Meta · Cartagena", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "68% completado",
                                right = { MonoText("$3.400.000", 14.5f) },
                                showChevron = true,
                                isLast = true,
                                onClick = { onNavigate(Screen.Goals) },
                            )
                        }
                    }
                }

                // AI prompt card
                item {
                    Spacer(Modifier.height(20.dp))
                    MinCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        variant = MinCardVariant.Default,
                        padding = PaddingValues(16.dp),
                        onClick = { onNavigate(Screen.AIChat) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MinPrimaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✦", fontSize = 15.sp, color = MinOnPrimaryContainer)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pregúntale a Movi",
                                    fontSize = 13.5.sp,
                                    color = MinTextDim,
                                )
                                Text(
                                    text = "\"¿puedo comprar X?\"",
                                    fontSize = 13.5.sp,
                                    color = MinText,
                                )
                            }
                            ChevronRight()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            MinBottomNav(
                active = NavTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                        NavTab.ANALYSIS -> onNavigate(Screen.Investments)
                        NavTab.PROFILE -> onNavigate(Screen.Profile)
                        else -> {}
                    }
                },
            )
            NavPill()
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 92.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MinPrimaryContainer)
                .clickable { onNavigate(Screen.QuickAdd) },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 24.sp, color = MinOnPrimaryContainer, fontWeight = FontWeight.Light)
        }
    }
}
