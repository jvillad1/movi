package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun RecurrentesScreen(onNavigate: (Screen) -> Unit) {
    var rules by remember { mutableStateOf<List<RecurringRule>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getRecurringRules() }
            .onSuccess { rules = it }
    }

    val ingresosFijos = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val egresosFijos  = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val flujoLibre    = ingresosFijos - egresosFijos

    val ordered = remember(rules) { rules.sortedBy { it.dayOfMonth } }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "‹",
                fontSize = 22.sp,
                color = MinText,
                modifier = Modifier.clickable { onNavigate(Screen.Analisis) },
            )
            Text(
                text = "Recurrentes",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.weight(1f),
            )
            Text("+", fontSize = 22.sp, color = MinTextDim)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Text("Flujo libre", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = formatCOP(flujoLibre),
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinText,
                        letterSpacing = (-1.4).sp,
                        lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Ingresos fijos − egresos fijos",
                        fontSize = 12.sp,
                        color = MinTextMute,
                    )
                    Spacer(Modifier.height(18.dp))
                    Hairline()
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ingresos fijos", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatCOP(ingresosFijos),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MinIncome,
                                letterSpacing = (-0.3).sp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Egresos fijos", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatCOP(egresosFijos),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MinText,
                                letterSpacing = (-0.3).sp,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Por día del mes", count = rules.size)
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        ordered.forEachIndexed { i, r ->
                            val isIncome = r.type == TransactionType.INCOME
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MinSurfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${r.dayOfMonth}",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = MinText,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = r.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MinText,
                                        letterSpacing = (-0.1).sp,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(r.category, fontSize = 12.sp, color = MinTextMute)
                                }
                                Text(
                                    text = "${if (isIncome) "+" else "−"}${formatCOP(r.amount)}",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isIncome) MinIncome else MinText,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                            if (i < ordered.size - 1) Hairline()
                        }
                    }
                }
            }
        }

        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> onNavigate(Screen.Mas)
            }
        }
    }
}
