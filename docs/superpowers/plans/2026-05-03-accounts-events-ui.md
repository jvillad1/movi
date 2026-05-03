# Plan 5 — Accounts + Events UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add AccountsScreen + CreateAccountSheet and update Dashboard/Transactions/QuickAdd with proper loading, empty states, and error handling with retry.

**Architecture:** Stateless Compose pattern throughout — `LaunchedEffect + remember { mutableStateOf }`, no ViewModel. CreateAccountSheet is a full-screen overlay (same pattern as QuickAddScreen). All new screens integrate with existing `Repositories.wallets` singleton.

**Tech Stack:** Compose Multiplatform 1.7.3, Material3 (SnackbarHost, LinearProgressIndicator), shared `WalletRepository` (`getAccounts()`, `createAccount()`).

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `composeApp/src/commonMain/.../ui/Navigation.kt` | Modify | Add `Screen.Accounts` |
| `composeApp/src/commonMain/.../App.kt` | Modify | Wire `AccountsScreen` into the `when` block |
| `composeApp/src/commonMain/.../ui/accounts/CreateAccountSheet.kt` | Create | Bottom-sheet overlay for creating an account |
| `composeApp/src/commonMain/.../ui/accounts/AccountsScreen.kt` | Create | Full accounts list screen |
| `composeApp/src/commonMain/.../ui/dashboard/DashboardScreen.kt` | Modify | Add MIS CUENTAS section, empty state, loading/error |
| `composeApp/src/commonMain/.../ui/transactions/TransactionsScreen.kt` | Modify | Add loading/error/empty state |
| `composeApp/src/commonMain/.../ui/quickadd/QuickAddScreen.kt` | Modify | Block save when no accounts, offer create |

All paths under `composeApp/src/commonMain/kotlin/com/jvillada/movi/`.

---

## Task 1: Add Screen.Accounts to navigation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`

- [ ] **Step 1: Add Screen.Accounts**

Open `ui/Navigation.kt`. Add one line after `data object Extractos : Screen()`:

```kotlin
sealed class Screen {
    data object Login            : Screen()
    data object Register         : Screen()
    data object OnboardingWelcome : Screen()
    data object OnboardingProfile : Screen()
    data object Dashboard : Screen()
    data object Transactions : Screen()
    data object QuickAdd : Screen()
    data object Profile : Screen()
    data object AIChat : Screen()
    data object Analisis : Screen()
    data object Investments : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    data object Budgets : Screen()
    data object Recurrentes : Screen()
    data object OCRCapture : Screen()
    data object OCRConfirm : Screen()
    data object SMSInbox : Screen()
    data class SMSReconcile(val smsId: String) : Screen()
    data object Mas : Screen()
    data object Extractos : Screen()
    data object Accounts : Screen()
}
```

- [ ] **Step 2: Wire AccountsScreen in App.kt**

In `App.kt`, add the import and the `when` branch. The `AccountsScreen` class doesn't exist yet — the file won't compile until Task 3, but adding the stub now lets the plan proceed in order.

Add import after existing screen imports:
```kotlin
import com.jvillada.movi.ui.accounts.AccountsScreen
```

Add branch in the `when (currentScreen)` block, after `Screen.Extractos -> ExtractosScreen(navigate)`:
```kotlin
Screen.Accounts -> AccountsScreen(onNavigate = navigate)
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt
git commit -m "feat: add Screen.Accounts to navigation"
```

---

## Task 2: Create CreateAccountSheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt`

- [ ] **Step 1: Create the file**

Create `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt` with the following content:

```kotlin
package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.*
import kotlinx.coroutines.launch

private val ACCOUNT_TYPE_OPTIONS = listOf(
    AccountType.CASH       to "💵 Efectivo",
    AccountType.SAVINGS    to "🏦 Ahorros",
    AccountType.CHECKING   to "💳 Corriente",
    AccountType.INVESTMENT to "📈 Inversión",
)

@Composable
fun CreateAccountSheet(onDismiss: () -> Unit, onAccountCreated: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.CASH) }
    var initialBalance by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val canCreate = name.isNotBlank() && !saving

    fun create() {
        if (!canCreate) return
        saving = true
        errorMsg = null
        coroutine.launch {
            val account = Account(
                id = "",
                name = name.trim(),
                type = selectedType,
                balance = initialBalance.toLongOrNull() ?: 0L,
            )
            runCatching { Repositories.wallets.createAccount(account) }
                .onSuccess { saving = false; onAccountCreated() }
                .onFailure { e -> saving = false; errorMsg = e.message ?: "Error al crear la cuenta" }
        }
    }

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
            // drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint),
            )

            androidx.compose.material3.Text(
                text = "Nueva cuenta",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
            )
            Spacer(Modifier.height(18.dp))

            // Name field
            androidx.compose.material3.Text("NOMBRE", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 15.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) androidx.compose.material3.Text("Ej: Bancolombia Ahorros", fontSize = 15.sp, color = MinTextMute)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(14.dp))

            // Type selector
            androidx.compose.material3.Text("TIPO", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ACCOUNT_TYPE_OPTIONS.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { (type, label) ->
                            val isSelected = type == selectedType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MinPrimaryContainer else MinSurfaceContainerLow)
                                    .then(if (!isSelected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable { selectedType = type }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MinOnPrimaryContainer else MinTextDim,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // Initial balance (optional)
            androidx.compose.material3.Text("SALDO INICIAL (opcional)", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = initialBalance,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 12) initialBalance = v },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 15.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (initialBalance.isEmpty()) androidx.compose.material3.Text("$ 0", fontSize = 15.sp, color = MinTextMute)
                        inner()
                    },
                )
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(errorMsg!!, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(18.dp))

            // CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canCreate) MinPrimaryContainer else MinSurfaceContainerLow)
                    .clickable(enabled = canCreate) { create() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = if (saving) "Creando…" else "Crear cuenta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canCreate) MinOnPrimaryContainer else MinTextFaint,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles by checking imports**

The file uses:
- `MinBorder`, `MinBorderStrong` from `theme` — verify these exist: `grep -r "val MinBorder\b" composeApp/src/commonMain/kotlin/com/jvillada/movi/theme/`
- If `MinBorder` is missing, use `MinHairline` instead
- `MinPrimaryContainer`, `MinOnPrimaryContainer` — already used in QuickAddScreen, should exist

Run: `./gradlew :composeApp:compileKotlinMetadata 2>&1 | grep -i "error" | head -20`

Fix any missing color token by checking `theme/Color.kt` and substituting the closest available token.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt
git commit -m "feat: CreateAccountSheet — bottom-sheet overlay to create accounts"
```

---

## Task 3: Create AccountsScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt`

- [ ] **Step 1: Create AccountsScreen.kt**

```kotlin
package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

private fun accountTypeIcon(type: AccountType) = when (type) {
    AccountType.CASH       -> "💵"
    AccountType.SAVINGS    -> "🏦"
    AccountType.CHECKING   -> "💳"
    AccountType.INVESTMENT -> "📈"
    AccountType.CREDIT_CARD -> "💳"
}

private fun accountTypeLabel(type: AccountType) = when (type) {
    AccountType.CASH        -> "Efectivo"
    AccountType.SAVINGS     -> "Ahorros"
    AccountType.CHECKING    -> "Corriente"
    AccountType.INVESTMENT  -> "Inversión"
    AccountType.CREDIT_CARD -> "Crédito"
}

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
            .onFailure { e -> error = e.message ?: "Error al cargar cuentas" }
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "‹",
                        fontSize = 22.sp,
                        color = MinText,
                        modifier = Modifier.clickable { onNavigate(Screen.Dashboard) },
                    )
                    Text(
                        "Mis cuentas",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                        letterSpacing = (-0.6).sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinPrimaryContainer)
                        .clickable { showCreateSheet = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("+ Nueva", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinOnPrimaryContainer)
                }
            }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, bottom = 80.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!loading && accounts.isEmpty()) {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(24.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("Sin cuentas aún", fontSize = 14.sp, color = MinTextMute)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MinPrimaryContainer)
                                        .clickable { showCreateSheet = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+ Crear primera cuenta", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinOnPrimaryContainer)
                                }
                            }
                        }
                    }
                }

                if (accounts.isNotEmpty()) {
                    val totalBalance = accounts.sumOf { it.balance }
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(18.dp),
                        ) {
                            Text("TOTAL ACTIVOS", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCOP(totalBalance),
                                fontSize = 28.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MinIncome,
                                letterSpacing = (-0.8).sp,
                            )
                        }
                    }

                    item {
                        MinSectionHeader(title = "Cuentas", count = accounts.size)
                    }

                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            accounts.forEachIndexed { i, account ->
                                CardRow(
                                    left = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(accountTypeIcon(account.type), fontSize = 18.sp)
                                            Column {
                                                Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                            }
                                        }
                                    },
                                    sub = accountTypeLabel(account.type),
                                    right = { MonoText(formatCOP(account.balance), 14.5f, color = MinIncome) },
                                    isLast = i == accounts.lastIndex,
                                )
                            }
                        }
                    }
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
```

Note: the `sub` parameter of `CardRow` shows below the left content. Since left already contains the name, the sub shows the type label below the name. This matches the design.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | grep -i "error" | head -30
```

Fix any compilation errors — common issues:
- `MinIncome` color token missing → check `theme/Color.kt`, use `MinText` as fallback
- `NavTab.HOME` active tab missing → use `NavTab.TRANSACTIONS` if HOME isn't right

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt
git commit -m "feat: AccountsScreen — accounts list with loading/empty state/error handling"
```

---

## Task 4: Update DashboardScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`

The current Dashboard fetches accounts but never displays them as a section. This task adds the "MIS CUENTAS" section, loading/error handling, and CreateAccountSheet integration.

- [ ] **Step 1: Add new state variables and update LaunchedEffect**

In `DashboardScreen.kt`, replace the current state declarations and `LaunchedEffect(scope)` block:

Current code to replace (lines ~30-41):
```kotlin
var summary by remember { mutableStateOf<FinanceSummary?>(null) }
var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
LaunchedEffect(scope) {
    runCatching { Repositories.wallets.getFinanceSummary(scope) }
        .onSuccess { summary = it }
    runCatching { Repositories.wallets.getAccounts() }
        .onSuccess { accounts = it }
        .onFailure { it.printStackTrace() }
}
```

Replace with:
```kotlin
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
```

- [ ] **Step 2: Add new imports**

Add to the imports block at the top of `DashboardScreen.kt`:
```kotlin
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.jvillada.movi.ui.accounts.CreateAccountSheet
```

- [ ] **Step 3: Wrap content in Box, add SnackbarHost and CreateAccountSheet overlay**

The outermost composable is currently:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(MinBg)
) {
    Column(modifier = Modifier.fillMaxSize()) {
```

This is already a `Box` wrapping a `Column` — good. Add `SnackbarHost` and `CreateAccountSheet` as siblings of the `Column`, inside the Box, after the Column's closing brace:

```kotlin
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
```

- [ ] **Step 4: Add LinearProgressIndicator and MIS CUENTAS section**

In the `LazyColumn`, add a new `item` block **after the hero card item** (after the `item { MinCard(... hero card ...) }` closing brace), and **before the `if (isFamily)` aportes item**:

```kotlin
// Loading indicator above the LazyColumn (add inside the Column, just before LazyColumn):
if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
```

Add as a new item in the LazyColumn, between the hero card and the `if (isFamily)` aportes block:

```kotlin
// MIS CUENTAS section
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
                    androidx.compose.material3.Text("Sin cuentas aún", fontSize = 14.sp, color = MinTextMute)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                            .background(MinPrimaryContainer)
                            .clickable { showCreateSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Text(
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
                accounts.take(3).forEachIndexed { i, account ->
                    val typeLabel = when (account.type) {
                        com.jvillada.movi.shared.model.AccountType.CASH        -> "Efectivo"
                        com.jvillada.movi.shared.model.AccountType.SAVINGS     -> "Ahorros"
                        com.jvillada.movi.shared.model.AccountType.CHECKING    -> "Corriente"
                        com.jvillada.movi.shared.model.AccountType.INVESTMENT  -> "Inversión"
                        com.jvillada.movi.shared.model.AccountType.CREDIT_CARD -> "Crédito"
                    }
                    CardRow(
                        left = { Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                        sub = typeLabel,
                        right = { MonoText(formatCOP(account.balance), 14.5f) },
                        isLast = i == minOf(accounts.size, 3) - 1,
                        onClick = { onNavigate(Screen.Accounts) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | grep -i "error" | head -30
```

Fix any issues with missing imports or type references.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt
git commit -m "feat: dashboard — MIS CUENTAS section, empty state, loading/error with retry"
```

---

## Task 5: Update TransactionsScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`

- [ ] **Step 1: Add state and update LaunchedEffect**

In `TransactionsScreen.kt`, replace the current state + LaunchedEffect:

Current:
```kotlin
var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
LaunchedEffect(Unit) {
    runCatching { Repositories.wallets.getEventsByDay() }
        .onSuccess { allDays = it }
}
```

Replace with:
```kotlin
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
```

- [ ] **Step 2: Add new imports**

Add to the imports block:
```kotlin
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
```

- [ ] **Step 3: Wrap in Box, add loading indicator, empty state, and SnackbarHost**

The current outermost is `Column`. Wrap it in a `Box`:

Replace:
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MinBg)
) {
```

With:
```kotlin
Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
Column(
    modifier = Modifier.fillMaxSize()
) {
```

And close the extra Box before the final function closing brace:
```kotlin
    } // end Column
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
    )
} // end Box
```

Add `LinearProgressIndicator` inside the Column, **between the filter chips Row and the LazyColumn**:
```kotlin
if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
```

Add an empty state inside `LazyColumn`, before the `visibleDays.forEach` loop:
```kotlin
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
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | grep -i "error" | head -30
```

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt
git commit -m "feat: transactions — loading indicator, empty state, error snackbar with retry"
```

---

## Task 6: Update QuickAddScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt`

- [ ] **Step 1: Add showCreateSheet state and accountsRefreshKey**

In `QuickAddScreen`, add two new state variables after the existing ones:

After `var error by remember { mutableStateOf<String?>(null) }`, add:
```kotlin
var showCreateSheet by remember { mutableStateOf(false) }
var accountsRefreshKey by remember { mutableStateOf(0) }
```

- [ ] **Step 2: Change LaunchedEffect(Unit) to LaunchedEffect(accountsRefreshKey)**

Replace:
```kotlin
LaunchedEffect(Unit) {
    runCatching { Repositories.wallets.getAccounts() }
        .onSuccess { list ->
            accounts = list
            if (selectedAccountId == null) selectedAccountId = list.firstOrNull()?.id
        }
}
```

With:
```kotlin
LaunchedEffect(accountsRefreshKey) {
    runCatching { Repositories.wallets.getAccounts() }
        .onSuccess { list ->
            accounts = list
            if (selectedAccountId == null || list.none { it.id == selectedAccountId }) {
                selectedAccountId = list.firstOrNull()?.id
            }
        }
}
```

- [ ] **Step 3: Add hasNoAccounts and onCreateAccount to EditorBody**

Add two parameters to the `EditorBody` composable signature (after `onSave: () -> Unit`):
```kotlin
hasNoAccounts: Boolean = false,
onCreateAccount: () -> Unit = {},
```

Inside `EditorBody`, replace the current save button block (the `Row` with camera + save button) with a version that shows the no-accounts hint when `hasNoAccounts == true`:

```kotlin
Spacer(Modifier.height(12.dp))

if (hasNoAccounts) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Primero creá una cuenta",
            fontSize = 13.sp,
            color = MinTextMute,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MinPrimaryContainer)
                .clickable { onCreateAccount() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+ Crear cuenta",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MinOnPrimaryContainer,
            )
        }
    }
} else {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp, 54.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                .clickable { onOcr() },
            contentAlignment = Alignment.Center,
        ) {
            Text("📷", fontSize = 20.sp)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                .clickable(enabled = canSave) { onSave() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (saving) "Guardando…" else "Guardar movimiento",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
            )
        }
    }
}
```

- [ ] **Step 4: Pass hasNoAccounts and onCreateAccount when calling EditorBody**

In `QuickAddScreen`, in the `Picker.None ->` branch where `EditorBody(...)` is called, add the two new parameters:

```kotlin
Picker.None -> EditorBody(
    typeIndex = typeIndex,
    onTypeChange = { typeIndex = it },
    amount = amount,
    onKey = ::onKey,
    category = category,
    walletLabel = selectedAccount?.name ?: "Seleccionar cuenta",
    note = note,
    onPickCategory = { picker = Picker.Category },
    onPickWallet = { picker = Picker.Wallet },
    onEditNote = { picker = Picker.Note },
    onOcr = { onNavigate(Screen.OCRCapture) },
    canSave = canSave,
    saving = saving,
    error = error,
    onSave = ::save,
    hasNoAccounts = accounts.isEmpty(),
    onCreateAccount = { showCreateSheet = true },
)
```

- [ ] **Step 5: Add CreateAccountSheet overlay at the end of QuickAddScreen**

At the end of `QuickAddScreen`, after the closing brace of the outer `Column`, add:

```kotlin
if (showCreateSheet) {
    com.jvillada.movi.ui.accounts.CreateAccountSheet(
        onDismiss = { showCreateSheet = false },
        onAccountCreated = { showCreateSheet = false; accountsRefreshKey++ },
    )
}
```

Note: use the fully qualified name or add an import at the top:
```kotlin
import com.jvillada.movi.ui.accounts.CreateAccountSheet
```

If using import, call it as `CreateAccountSheet(...)` instead.

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | grep -i "error" | head -30
```

- [ ] **Step 7: Full build to catch all platforms**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

If there are errors, fix them before committing.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt
git commit -m "feat: quickadd — block save when no accounts, offer create account"
```

---

## Smoke Test Checklist

After all 6 tasks complete, verify manually on device/emulator:

1. `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL
2. Fresh install with no accounts → Dashboard shows "Sin cuentas aún" + create button
3. Tap "Crear primera cuenta" → CreateAccountSheet slides up → enter name → tap "Crear cuenta" → sheet closes → account appears in Dashboard
4. Tap "Ver todas +" → AccountsScreen opens → shows account
5. Tap "+ Nueva" in AccountsScreen → CreateAccountSheet → create second account → list reloads
6. Open QuickAdd with no accounts → "Primero creá una cuenta" hint visible, save disabled
7. Create account from QuickAdd → QuickAdd now shows the account in wallet picker
8. Add a transaction → appears in TransactionsScreen
9. Start app with no network → all screens show snackbar "Reintentar" that re-triggers load
