package com.jvillada.movi.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

private data class CategoryTotal(val category: String, val total: Long)

@Composable
fun AnalisisScreen(onNavigate: (Screen) -> Unit) {
    var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }
    var holdings by remember { mutableStateOf<List<Holding>>(emptyList()) }
    var credits by remember { mutableStateOf<List<Credit>>(emptyList()) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getTransactionsByDay() }.onSuccess { days = it }
        runCatching { Repositories.wallets.getHoldings() }.onSuccess { holdings = it }
        runCatching { Repositories.wallets.getCredits() }.onSuccess { credits = it }
        runCatching { Repositories.wallets.getGoals() }.onSuccess { goals = it }
    }

    val byCategory = remember(days) {
        days.flatMap { it.items }
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { (cat, txs) -> CategoryTotal(cat, txs.sumOf { it.amount.toLong() }) }
            .sortedByDescending { it.total }
    }
    val totalEgresos = byCategory.sumOf { it.total }

    val totalInvertido = holdings.sumOf { it.amount }
    val totalDeuda = credits.sumOf { it.total - it.paid }
    val totalAhorrado = goals.sumOf { it.saved }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Análisis",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.8).sp,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Egresos hero
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Text("Egresos del mes", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = formatCOP(totalEgresos),
                        fontSize = 38.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinText,
                        letterSpacing = (-1.4).sp,
                        lineHeight = 38.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${byCategory.size} categorías · ${days.flatMap { it.items }.count { it.type == TransactionType.EXPENSE }} movimientos",
                        fontSize = 12.sp,
                        color = MinTextMute,
                    )
                }
            }

            // Egresos por categoría
            if (byCategory.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Egresos por categoría", count = byCategory.size)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        ) {
                            byCategory.forEachIndexed { i, c ->
                                val pct = if (totalEgresos == 0L) 0f else (c.total.toFloat() / totalEgresos.toFloat())
                                Column(modifier = Modifier.padding(vertical = 14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(c.category, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
                                        Row {
                                            Text(formatCOP(c.total), fontSize = 13.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                            Text("  ${(pct * 100).toInt()}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
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
                                if (i < byCategory.size - 1) Hairline()
                            }
                        }
                    }
                }
            }

            // Presupuestos shortcut
            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Presupuestos")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(
                            left = { Text("Ver presupuestos", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "Control mensual por categoría",
                            showChevron = true,
                            isLast = true,
                            onClick = { onNavigate(Screen.Budgets) },
                        )
                    }
                }
            }

            // Patrimonio
            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Patrimonio")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(
                            left = { Text("Inversiones", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "${holdings.size} posiciones",
                            right = { MonoText(formatCOP(totalInvertido), 14.5f) },
                            showChevron = true,
                            onClick = { onNavigate(Screen.Investments) },
                        )
                        CardRow(
                            left = { Text("Créditos", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "Deuda pendiente · ${credits.size} obligaciones",
                            right = { MonoText(formatCOP(totalDeuda), 14.5f) },
                            showChevron = true,
                            onClick = { onNavigate(Screen.Credits) },
                        )
                        CardRow(
                            left = { Text("Metas de ahorro", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            sub = "${goals.size} metas activas",
                            right = { MonoText(formatCOP(totalAhorrado), 14.5f) },
                            showChevron = true,
                            isLast = true,
                            onClick = { onNavigate(Screen.Goals) },
                        )
                    }
                }
            }
        }

        MinBottomNav(
            active = NavTab.ANALYSIS,
            onTabSelected = { tab ->
                when (tab) {
                    NavTab.HOME -> onNavigate(Screen.Dashboard)
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.PROFILE -> onNavigate(Screen.Profile)
                    else -> {}
                }
            },
        )
        NavPill()
    }
}
