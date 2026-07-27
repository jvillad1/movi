package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
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
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.sdui.SduiRenderer
import kotlinx.coroutines.launch

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
    var screenDef by remember { mutableStateOf<ScreenDefinition?>(ScreenDefCache.dashboard) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutine = rememberCoroutineScope()

    LaunchedEffect(scope, refreshKey) {
        loading = true
        error = null
        // SDUI: server-driven definition for this screen, fetched FIRST so the SDUI
        // dashboard (when available) is already in place before the fallback would
        // otherwise render — avoids a visible fallback→SDUI flip on every cold load.
        // Silent on failure — anti-rotura layer 2 (ScreenDefCache) keeps the last
        // valid one; layer 3 (DashboardFallback) covers a cold start with no cache
        // and no successful fetch yet.
        runCatching { Repositories.wallets.getScreen("dashboard", screenDef?.version) }
            .onSuccess {
                // Capa 4 anti-rotura: una definición que no renderiza nada equivale a
                // no tener definición — evita un dashboard en blanco por typos en los
                // section types.
                it?.takeIf { d -> renderableSections(d).isNotEmpty() }?.let { d -> screenDef = d; ScreenDefCache.dashboard = d }
            }
        runCatching { Repositories.wallets.getFinanceSummary(scope) }
            .onSuccess { summary = it }
            .onFailure { e -> error = e.toUserMessage() }
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
            .onFailure { e -> if (error == null) error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    val (_, _, totalBalance) = assetsDebtsNet(accounts)
    val ingresos = summary?.ingresos ?: 0L
    val egresos  = summary?.egresos  ?: 0L
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable { onNavigate(Screen.Profile) },
                ) {
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
                Box(
                    modifier = Modifier.clickable {
                        coroutine.launch { snackbarHostState.showSnackbar("Sin notificaciones por ahora") }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notificaciones",
                        tint = MinTextDim,
                        modifier = Modifier.size(22.dp),
                    )
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

            // SDUI: render from the server-provided definition when we have one; otherwise
            // fall back to the hardcoded, byte-identical body (anti-rotura layer 3).
            screenDef?.let { def ->
                SduiRenderer(
                    definition = def,
                    summary = summary,
                    accounts = accounts,
                    isFamily = isFamily,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onNavigate = onNavigate,
                    onShowCreateSheet = { showCreateSheet = true },
                )
            } ?: DashboardFallback(
                totalBalance = totalBalance,
                ingresos = ingresos,
                egresos = egresos,
                flujo = flujo,
                isFamily = isFamily,
                accounts = accounts,
                onNavigate = onNavigate,
                onShowCreateSheet = { showCreateSheet = true },
            )

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

/**
 * The Dashboard body as it was hardcoded before SDUI (Task 3) — renamed, byte-identical,
 * no refactor, and shares nothing with `SduiRenderer`: this is the anti-rotura insurance
 * policy for when there is no server definition (cold start, no cache, failed fetch).
 * Declared as a `ColumnScope` extension so the `Modifier.weight(1f)` below — same as
 * the original inline `LazyColumn` — keeps working unchanged inside DashboardScreen's
 * chrome `Column`. Top bar, snackbar, bottom nav and sheets stay outside, as chrome,
 * in both the SDUI and fallback paths.
 */
@Composable
private fun ColumnScope.DashboardFallback(
    totalBalance: Long,
    ingresos: Long,
    egresos: Long,
    flujo: Long,
    isFamily: Boolean,
    accounts: List<Account>,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit,
) {
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
                    text = "Balance",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinTextMute,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${if (totalBalance < 0) "−" else ""}${formatCOP(totalBalance)}",
                    fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = if (totalBalance < 0) MinExpense else MinText,
                    letterSpacing = (-1.6).sp,
                    lineHeight = 44.sp,
                )
                Spacer(Modifier.height(18.dp))
                Sparkline(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    family = isFamily,
                    hasData = totalBalance != 0L || ingresos != 0L || egresos != 0L,
                )
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        Pair("Ingresos", formatMillions(ingresos)),
                        Pair("Egresos",  formatMillions(egresos)),
                        Pair("Flujo",    formatMillions(flujo)),
                    ).forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(value, fontSize = 14.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
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
                                    .clickable { onShowCreateSheet() },
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
                        val typeLabel: (AccountType) -> String = { type ->
                            when (type) {
                                AccountType.CASH        -> "Efectivo"
                                AccountType.SAVINGS     -> "Ahorros"
                                AccountType.CHECKING    -> "Corriente"
                                AccountType.INVESTMENT  -> "Inversión"
                                AccountType.CREDIT_CARD -> "Crédito"
                                AccountType.LOAN        -> "Préstamo"
                            }
                        }
                        accounts.take(3).forEachIndexed { i, account ->
                            CardRow(
                                left = { Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = typeLabel(account.type),
                                right = {
                                    if (isDebtAccount(account.type)) {
                                        val (debt, isEstimate) = cardDebt(account)
                                        MonoText(
                                            "${if (debt < 0) "+" else "−"}${if (isEstimate) "≈" else ""}${formatCOP(debt)}",
                                            14.5f,
                                            color = if (debt < 0) MinIncome else MinExpense,
                                        )
                                    } else {
                                        MonoText(formatCOP(account.balance), 14.5f)
                                    }
                                },
                                isLast = i == minOf(accounts.size, 3) - 1,
                                onClick = { onNavigate(Screen.Accounts) },
                            )
                        }
                    }
                }
            }
        }


        // Alertas
        item {
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MinSectionHeader(title = "Alertas")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text("Sin alertas por ahora", fontSize = 14.sp, color = MinTextMute)
                }
            }
        }

        // Patrimonio
        item {
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MinSectionHeader(
                    title = if (isFamily) "Patrimonio familiar" else "Patrimonio",
                    action = "Ver todo",
                    onAction = { onNavigate(Screen.Mas) },
                )
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    CardRow(
                        left = { Text("Inversiones", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                        showChevron = true,
                        isLast = false,
                        onClick = { onNavigate(Screen.Investments) },
                    )
                    CardRow(
                        left = { Text("Créditos", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                        showChevron = true,
                        isLast = false,
                        onClick = { onNavigate(Screen.Credits) },
                    )
                    CardRow(
                        left = { Text("Metas", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
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
}
