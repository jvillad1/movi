package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun AccountsScreen(onNavigate: (Screen) -> Unit) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row: back arrow + title + "+ Nueva" pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = MinText,
                    // F22 (pila de navegación) es Ola 2 — hasta entonces, hardcodeado a Más.
                    // Ojo: acá también se llega desde el Inicio («Ver todas +» y las filas de
                    // cuentas), y en ese caso la flecha te deja en Más en vez de en Inicio.
                    // Es el defecto conocido que F22 cierra; se prefirió Más porque es el
                    // acceso permanente (F19).
                    modifier = Modifier.size(22.dp).clickable { onNavigate(Screen.Mas) },
                )
                Text(
                    text = "Mis cuentas",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinPrimaryContainer)
                        .clickable { showCreateSheet = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+ Nueva",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinOnPrimaryContainer,
                    )
                }
            }

            // Linear progress indicator below header while loading
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MinPrimaryContainer,
                    trackColor = MinSurfaceContainerHigh,
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 80.dp,
                ),
            ) {
                if (accounts.isEmpty() && !loading) {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(32.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "Sin cuentas aún",
                                    fontSize = 15.sp,
                                    color = MinTextDim,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MinPrimaryContainer)
                                        .clickable { showCreateSheet = true }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Crear primera cuenta",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MinOnPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                } else if (accounts.isNotEmpty()) {
                    // Total assets card
                    item {
                        val (activos, deudas, neto) = assetsDebtsNet(accounts)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(20.dp),
                        ) {
                            Text(
                                text = "PATRIMONIO NETO",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            MonoText(
                                text = formatCOP(neto), // formatCOP ya trae el signo (F36) — no duplicarlo acá
                                fontSize = 28f,
                                color = if (neto >= 0) MinIncome else MinExpense,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Activos", fontSize = 12.sp, color = MinTextMute)
                                MonoText(formatCOP(activos), 12f, color = MinIncome)
                            }
                            if (deudas > 0) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Deudas", fontSize = 12.sp, color = MinTextMute)
                                    MonoText("−${formatCOP(deudas)}", 12f, color = MinExpense)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Section header
                    item {
                        MinSectionHeader(title = "Cuentas", count = accounts.size)
                    }

                    // Accounts card
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            accounts.forEachIndexed { index, account ->
                                val (icon, typeLabel) = accountTypeInfo(account.type)
                                CardRow(
                                    left = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(imageVector = icon, contentDescription = typeLabel, tint = MinTextDim, modifier = Modifier.size(20.dp))
                                            Text(
                                                text = account.name,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                            )
                                        }
                                    },
                                    sub = typeLabel,
                                    right = {
                                        if (isDebtAccount(account.type)) {
                                            val (debt, isEstimate) = cardDebt(account)
                                            MonoText(
                                                // debt < 0 es saldo a favor: signo invertido a propósito, así
                                                // que se le pasa el valor absoluto — si no, formatCOP (F36) le
                                                // pondría su propio "−" encima del "+" de acá.
                                                text = "${if (debt < 0) "+" else "−"}${if (isEstimate) "≈" else ""}${formatCOP(kotlin.math.abs(debt))}",
                                                fontSize = 14.5f,
                                                color = if (debt < 0) MinIncome else MinExpense,
                                            )
                                        } else {
                                            MonoText(
                                                text = formatCOP(account.balance),
                                                fontSize = 14.5f,
                                                color = MinIncome,
                                            )
                                        }
                                    },
                                    isLast = index == accounts.size - 1,
                                    showChevron = true,
                                    onClick = { onNavigate(Screen.AccountDetail(account.id)) },
                                )
                            }
                        }
                    }
                }
            }

            MinBottomNav(active = NavTab.HOME) { tab ->
                when (tab) {
                    NavTab.HOME         -> onNavigate(Screen.Dashboard)
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.ADD          -> onNavigate(Screen.QuickAdd())
                    NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                    NavTab.MORE         -> onNavigate(Screen.Mas)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = {
                    showCreateSheet = false
                    refreshKey++
                },
            )
        }
    }
}

private fun accountTypeInfo(type: AccountType): Pair<ImageVector, String> = when (type) {
    AccountType.CASH        -> Icons.Filled.Payments to "Efectivo"
    AccountType.SAVINGS     -> Icons.Filled.AccountBalance to "Ahorros"
    AccountType.CHECKING    -> Icons.Filled.AccountBalanceWallet to "Corriente"
    AccountType.INVESTMENT  -> Icons.AutoMirrored.Filled.TrendingUp to "Inversión"
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to "Crédito"
    AccountType.LOAN        -> Icons.Filled.RequestQuote to "Préstamo"
}
