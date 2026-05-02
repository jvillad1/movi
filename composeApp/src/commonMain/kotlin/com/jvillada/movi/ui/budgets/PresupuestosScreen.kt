package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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

private sealed class Sheet {
    data class Edit(val current: Budget) : Sheet()
    data object Add : Sheet()
}

@Composable
fun PresupuestosScreen(onNavigate: (Screen) -> Unit) {
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { Repositories.wallets.getBudgets() }.onSuccess { budgets = it }
    }

    LaunchedEffect(Unit) {
        reload()
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

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    color = MinText,
                    modifier = Modifier.clickable { sheet = Sheet.Add },
                )
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
                                BudgetCard(p, onClick = { sheet = Sheet.Edit(p.budget) })
                            }
                        }
                    }
                }
            }

            MinBottomNav(active = NavTab.BUDGETS) { tab ->
                when (tab) {
                    NavTab.HOME         -> onNavigate(Screen.Dashboard)
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                    NavTab.MORE         -> onNavigate(Screen.Mas)
                    else -> {}
                }
            }
        }

        when (val s = sheet) {
            is Sheet.Edit -> BudgetSheet(
                title = "Editar presupuesto",
                initialCategory = s.current.category,
                categoryEditable = false,
                initialAmount = s.current.monthlyLimit,
                onDismiss = { sheet = null },
                onDelete = {
                    scope.launch {
                        runCatching { Repositories.wallets.deleteBudget(s.current.category) }
                        reload()
                        sheet = null
                    }
                },
                onSave = { _, amt ->
                    scope.launch {
                        runCatching {
                            Repositories.wallets.updateBudget(
                                s.current.category,
                                Budget(s.current.category, amt),
                            )
                        }
                        reload()
                        sheet = null
                    }
                },
            )
            Sheet.Add -> BudgetSheet(
                title = "Nuevo presupuesto",
                initialCategory = "",
                categoryEditable = true,
                initialAmount = 0,
                onDismiss = { sheet = null },
                onDelete = null,
                onSave = { cat, amt ->
                    if (cat.isBlank() || amt <= 0L) return@BudgetSheet
                    scope.launch {
                        runCatching { Repositories.wallets.createBudget(Budget(cat.trim(), amt)) }
                        reload()
                        sheet = null
                    }
                },
            )
            null -> {}
        }
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
private fun BudgetCard(p: BudgetProgress, onClick: () -> Unit) {
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
        onClick = onClick,
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

@Composable
private fun BudgetSheet(
    title: String,
    initialCategory: String,
    categoryEditable: Boolean,
    initialAmount: Long,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Long) -> Unit,
) {
    var category by remember { mutableStateOf(initialCategory) }
    var amount by remember { mutableStateOf(if (initialAmount > 0L) initialAmount.toString() else "") }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toLongOrNull() ?: 0L
    val canSave = category.isNotBlank() && parsedAmount > 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint)
            )

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            // Category
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Categoría", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(8.dp))
                if (categoryEditable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinSurfaceContainerLow)
                            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        BasicTextField(
                            value = category,
                            onValueChange = { category = it },
                            singleLine = true,
                            cursorBrush = SolidColor(MinText),
                            textStyle = TextStyle(
                                color = MinText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            decorationBox = { inner ->
                                if (category.isEmpty()) {
                                    Text("Mercado, Salud, Restaurantes…", fontSize = 15.sp, color = MinTextFaint)
                                }
                                inner()
                            },
                        )
                    }
                } else {
                    Text(
                        text = category,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Amount display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$${amount.ifEmpty { "0" }}",
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = MinText,
                    letterSpacing = (-1.8).sp,
                    lineHeight = 48.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text("Límite mensual · COP", fontSize = 12.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Numpad
            Column {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("000", "0", "⌫"),
                ).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable { onKey(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Normal,
                                    color = MinText,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Eliminar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinExpense,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(if (onDelete != null) 1.4f else 1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                        .clickable(enabled = canSave) { onSave(category, parsedAmount) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSave) MinOnPrimaryContainer else MinTextDim,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
