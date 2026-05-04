package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun TransactionsScreen(onNavigate: (Screen) -> Unit) {
    var activeFilter by remember { mutableStateOf(0) }
    val filters = listOf("Todo", "Egresos", "Ingresos", "Pendientes")

    var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess { allDays = it }
            .onFailure { e -> error = e.message ?: "Error al cargar movimientos" }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    fun signedAmount(tx: FinancialEvent): Long =
        if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount

    val visibleDays = remember(activeFilter, allDays) {
        allDays.mapNotNull { day ->
            val filtered = when (activeFilter) {
                1 -> day.items.filter { it.type == TransactionType.EXPENSE && it.reconciliationStatus != ReconciliationStatus.UNCONFIRMED }
                2 -> day.items.filter { it.type == TransactionType.INCOME }
                3 -> day.items.filter { it.reconciliationStatus == ReconciliationStatus.UNCONFIRMED }
                else -> day.items
            }
            if (filtered.isEmpty()) null
            else day.copy(items = filtered, total = filtered.sumOf { signedAmount(it) })
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Movimientos",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.8).sp,
            )
            Text("🔍", fontSize = 20.sp, color = MinTextDim)
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, f ->
                val isActive = i == activeFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { activeFilter = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActive) {
                        Text("✓", fontSize = 13.sp, color = MinPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = f,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) MinText else MinTextDim,
                    )
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            if (!loading && visibleDays.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Sin movimientos aún", fontSize = 14.sp, color = MinTextMute)
                    }
                }
            }

            visibleDays.forEach { day ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = day.date.uppercase(),
                                fontSize = 11.sp,
                                color = MinTextMute,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                            )
                            Text(
                                text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            day.items.forEachIndexed { i, tx ->
                                val isIncome = tx.type == TransactionType.INCOME
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    text = tx.description,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MinText,
                                                    letterSpacing = (-0.1).sp,
                                                )
                                                if (tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                                                    StatusDot(MinWarn)
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(tx.category, fontSize = 12.sp, color = MinTextMute)
                                                StatusDot(MinTextFaint, 2.dp)
                                                Text(
                                                    text = tx.source.name,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MinTextMute,
                                                    letterSpacing = 0.3.sp,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${if (isIncome) "+" else "−"}${formatCOP(tx.amount)}",
                                            fontSize = 14.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isIncome) MinIncome else MinText,
                                            letterSpacing = (-0.3).sp,
                                        )
                                    }
                                    if (i < day.items.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }

        MinBottomNav(active = NavTab.TRANSACTIONS) { tab ->
            when (tab) {
                NavTab.HOME    -> onNavigate(Screen.Dashboard)
                NavTab.ADD     -> onNavigate(Screen.QuickAdd)
                NavTab.BUDGETS -> onNavigate(Screen.Budgets)
                NavTab.MORE    -> onNavigate(Screen.Mas)
                else -> {}
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
    )
    }
}
