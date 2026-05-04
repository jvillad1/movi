package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*

@Composable
fun DashboardScreen(
    onNavigate: (Screen) -> Unit,
) {
    var scope by remember { mutableStateOf(Scope.SELF) }
    val isFamily = scope == Scope.FAMILY

    var summary by remember { mutableStateOf<FinanceSummary?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(scope, refreshKey) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getFinanceSummary(scope) }
            .onSuccess { summary = it }
            .onFailure { e -> error = e.message ?: "Error al cargar" }
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
            .onFailure { e -> if (error == null) error = e.message ?: "Error al cargar cuentas" }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    val totalBalance = accounts.sumOf { it.balance }
    val ingresos = summary?.ingresos ?: if (isFamily) 9_200_000L else 4_500_000L
    val egresos  = summary?.egresos  ?: if (isFamily) 4_330_000L else 2_660_000L
    val flujo    = ingresos - egresos

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
                        Text(SessionManager.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                    }
                    Text(SessionManager.userName?.substringBefore(" ") ?: "Usuario", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
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

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

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
                            text = formatCOP(totalBalance),
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

                // Mis cuentas section
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(
                            title = "Mis cuentas",
                            count = if (accounts.isNotEmpty()) accounts.size else null,
                            action = if (accounts.isNotEmpty()) "Ver todas +" else null,
                            onAction = if (accounts.isNotEmpty()) { { onNavigate(Screen.Accounts) } } else null,
                        )
                        if (accounts.isEmpty()) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(18.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("Sin cuentas aún", fontSize = 14.sp, color = MinTextMute)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MinPrimaryContainer)
                                            .clickable { showCreateSheet = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "+ Crear primera cuenta",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MinOnPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                val typeLabel: (com.jvillada.movi.shared.model.AccountType) -> String = { type ->
                                    when (type) {
                                        com.jvillada.movi.shared.model.AccountType.CASH        -> "Efectivo"
                                        com.jvillada.movi.shared.model.AccountType.SAVINGS     -> "Ahorros"
                                        com.jvillada.movi.shared.model.AccountType.CHECKING    -> "Corriente"
                                        com.jvillada.movi.shared.model.AccountType.INVESTMENT  -> "Inversión"
                                        com.jvillada.movi.shared.model.AccountType.CREDIT_CARD -> "Crédito"
                                    }
                                }
                                accounts.take(3).forEachIndexed { i, account ->
                                    CardRow(
                                        left = { Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                        sub = typeLabel(account.type),
                                        right = { MonoText(formatCOP(account.balance), 14.5f) },
                                        isLast = i == minOf(accounts.size, 3) - 1,
                                        onClick = { onNavigate(Screen.Accounts) },
                                    )
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
                        MinSectionHeader(title = "Alertas", count = if (isFamily) 3 else 2)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            if (isFamily) {
                                CardRow(
                                    left = { Text("Mercado familiar al 92%", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                    sub = "$1.290.000 de $1.400.000 este mes",
                                    showChevron = true,
                                )
                                CardRow(
                                    left = { Text("Cuota Bancolombia", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                    sub = "Se descuenta el 30 de abril",
                                    right = { MonoText("$580.000", 14.5f) },
                                    showChevron = true,
                                )
                                CardRow(
                                    left = { Text("Cumpleaños Mateo en 14 días", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                    sub = "Falta $580.000 de la meta",
                                    showChevron = true,
                                    isLast = true,
                                )
                            } else {
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
                }

                // Patrimonio
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = if (isFamily) "Patrimonio familiar" else "Patrimonio", action = "Ver todo")
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            CardRow(
                                left = { Text("Inversiones", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = if (isFamily) "+9,1% YTD · 2 cuentas" else "+8,4% YTD",
                                right = { MonoText(if (isFamily) "$24.860.000" else "$12.480.000", 14.5f) },
                                showChevron = true,
                                onClick = { onNavigate(Screen.Investments) },
                            )
                            CardRow(
                                left = { Text(if (isFamily) "Créditos del hogar" else "Crédito Bancolombia", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = if (isFamily) "3 productos · próxima cuota 30 abr" else "Cuota en 5 días",
                                right = { MonoText(if (isFamily) "$159.040.000" else "$4.320.000", 14.5f) },
                                showChevron = true,
                                onClick = { onNavigate(Screen.Credits) },
                            )
                            CardRow(
                                left = { Text(if (isFamily) "Metas compartidas" else "Meta · Cartagena", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = if (isFamily) "Cartagena · Apto · Mateo · Emergencia" else "68% completado",
                                right = { MonoText(if (isFamily) "$24.220.000" else "$3.400.000", 14.5f) },
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

            MinBottomNav(active = NavTab.HOME) { tab ->
                when (tab) {
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                    NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                    NavTab.MORE         -> onNavigate(Screen.Mas)
                    else -> {}
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = { showCreateSheet = false; refreshKey++ },
            )
        }
    }
}
