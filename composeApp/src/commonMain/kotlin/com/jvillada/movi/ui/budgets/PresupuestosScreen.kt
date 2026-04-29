package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

private data class BudgetProgress(
    val budget: Budget,
    val spent: Long,
) {
    val remaining: Long get() = budget.monthlyLimit - spent
    val pctRaw: Float get() = if (budget.monthlyLimit == 0L) 0f else spent.toFloat() / budget.monthlyLimit.toFloat()
    val pct: Int get() = (pctRaw * 100).toInt()
    val state: AlertState get() = when {
        pctRaw >= 1f -> AlertState.OVER
        pctRaw >= 0.80f -> AlertState.WARN
        else -> AlertState.OK
    }
}

private enum class AlertState { OK, WARN, OVER }

@Composable
fun PresupuestosScreen(onNavigate: (Screen) -> Unit) {
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getBudgets() }.onSuccess { budgets = it }
        runCatching { Repositories.wallets.getTransactionsByDay() }.onSuccess { days = it }
    }

    val progresses = remember(budgets, days) {
        val spentByCategory = days.flatMap { it.items }
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount.toLong() } }
        budgets.map { b -> BudgetProgress(b, spentByCategory[b.category] ?: 0L) }
            .sortedByDescending { it.pctRaw }
    }

    val totalLimit = budgets.sumOf { it.monthlyLimit }
    val totalSpent = progresses.sumOf { it.spent }
    val warnCount = progresses.count { it.state == AlertState.WARN }
    val overCount = progresses.count { it.state == AlertState.OVER }

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
                text = "Presupuestos",
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
                    Text("Gastado del mes", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = formatCOP(totalSpent),
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinText,
                        letterSpacing = (-1.4).sp,
                        lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "de ${formatCOP(totalLimit)}",
                        fontSize = 13.sp,
                        color = MinTextMute,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (warnCount + overCount > 0) {
                        Spacer(Modifier.height(14.dp))
                        Hairline()
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (overCount > 0) {
                                AlertBadge("Sobrepasados", overCount, MinExpense)
                            }
                            if (warnCount > 0) {
                                AlertBadge("Cerca del límite", warnCount, MinWarn)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Categorías", count = budgets.size)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        progresses.forEach { p ->
                            BudgetCard(p)
                        }
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
                    NavTab.ANALYSIS -> onNavigate(Screen.Analisis)
                    NavTab.PROFILE -> onNavigate(Screen.Profile)
                }
            },
        )
        NavPill()
    }
}

@Composable
private fun AlertBadge(label: String, count: Int, color: Color) {
    Column {
        Text(
            text = "$count",
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = (-0.4).sp,
        )
        Text(label, fontSize = 11.sp, color = MinTextMute)
    }
}

@Composable
private fun BudgetCard(p: BudgetProgress) {
    val barColor = when (p.state) {
        AlertState.OVER -> MinExpense
        AlertState.WARN -> MinWarn
        AlertState.OK -> MinText.copy(alpha = 0.85f)
    }
    val pctColor = when (p.state) {
        AlertState.OVER -> MinExpense
        AlertState.WARN -> MinWarn
        AlertState.OK -> MinTextMute
    }
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = p.budget.category,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Text(
                text = "${p.pct}%",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = pctColor,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(formatCOP(p.spent), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                Text(" / ${formatCOP(p.budget.monthlyLimit)}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, letterSpacing = (-0.3).sp)
            }
            val tail = when (p.state) {
                AlertState.OVER -> "Sobrepasado · ${formatCOP(-p.remaining)}"
                AlertState.WARN -> "Cerca del límite"
                AlertState.OK -> "${formatCOP(p.remaining)} disponibles"
            }
            Text(tail, fontSize = 11.sp, color = pctColor)
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
                    .fillMaxWidth(p.pctRaw.coerceAtMost(1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}
