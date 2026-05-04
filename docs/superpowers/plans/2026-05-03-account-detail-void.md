# AccountDetail + VoidEvent UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `AccountDetailScreen` (account balance + event history grouped by day) and `VoidEventSheet` (bottom-sheet to annul an event with an optional reason), accessible by tapping any account row in `AccountsScreen`.

**Architecture:** A new `data class AccountDetail(accountId)` screen entry drives navigation. `AccountDetailScreen` loads `getAccount(accountId)` + `getEvents(accountId)` sequentially, groups events client-side by day using `kotlinx-datetime`, and renders them identically to `TransactionsScreen`. `VoidEventSheet` is a same-pattern overlay as `CreateAccountSheet`, triggered when `selectedEvent` is non-null.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.7.3, Ktor client, `kotlinx-datetime 0.6.1`, stateless composable pattern (`LaunchedEffect + mutableStateOf`).

---

## File Map

| File | Change |
|---|---|
| `composeApp/build.gradle.kts` | Add `kotlinx-datetime` to `commonMain.dependencies` |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt` | Add `data class AccountDetail(val accountId: String) : Screen()` |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt` | Add import + `is Screen.AccountDetail` case |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt` | Make account `CardRow`s clickable (add `showChevron` + `onClick`) |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/VoidEventSheet.kt` | **Create** — bottom sheet composable |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt` | **Create** — full sub-screen |

---

## Task 1: Navigation + dependency wiring

**Files:**
- Modify: `composeApp/build.gradle.kts:65`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt:25`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt:35,97`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt:192-216`

**Context for the implementer:**
- `sealed class Screen` uses `data object` for screens with no args and `data class` for screens that carry data (see `SMSReconcile(val smsId: String)` as the existing example).
- `composeApp/build.gradle.kts` line 47 starts `commonMain.dependencies {`; `libs.kotlinx.datetime` is already declared in the version catalog.
- `App.kt` imports each screen explicitly; add one more import for `AccountDetailScreen`.
- `AccountsScreen.kt` `CardRow` at lines 192–216 is currently not clickable. Add `showChevron = true` and `onClick`.
- `AccountsScreen.kt` does not need any other changes — `accountTypeInfo` stays `private` since it will be duplicated in `AccountDetailScreen.kt`.

- [ ] **Step 1: Add `kotlinx-datetime` to `composeApp/build.gradle.kts`**

  In `composeApp/build.gradle.kts`, inside `commonMain.dependencies { }` (which starts at line 47), add after the last `implementation(...)` line (currently line 65 `libs.multiplatform.settings.no.arg`):

  ```kotlin
  implementation(libs.kotlinx.datetime)
  ```

  The block should look like:
  ```kotlin
  commonMain.dependencies {
      // ...existing lines...
      implementation(libs.multiplatform.settings)
      implementation(libs.multiplatform.settings.no.arg)
      implementation(libs.kotlinx.datetime)
  }
  ```

- [ ] **Step 2: Add `Screen.AccountDetail` to `Navigation.kt`**

  In `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`, replace:
  ```kotlin
      data object Accounts : Screen()
  }
  ```
  With:
  ```kotlin
      data object Accounts : Screen()
      data class AccountDetail(val accountId: String) : Screen()
  }
  ```

- [ ] **Step 3: Wire `AccountDetailScreen` in `App.kt`**

  In `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`, add the import after the `AccountsScreen` import (around line 38):
  ```kotlin
  import com.jvillada.movi.ui.accounts.AccountDetailScreen
  ```

  Then in the `when (currentScreen)` block, replace:
  ```kotlin
                      Screen.Accounts         -> AccountsScreen(navigate)
                  }
  ```
  With:
  ```kotlin
                      Screen.Accounts         -> AccountsScreen(navigate)
                      is Screen.AccountDetail -> AccountDetailScreen(navigate, currentScreen.accountId)
                  }
  ```

- [ ] **Step 4: Make account rows clickable in `AccountsScreen.kt`**

  In `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt`, replace the `CardRow` block at lines 192–216:
  ```kotlin
                              CardRow(
                                  left = {
                                      Row(
                                          verticalAlignment = Alignment.CenterVertically,
                                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                                      ) {
                                          Text(text = icon, fontSize = 18.sp)
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
                                      MonoText(
                                          text = formatCOP(account.balance),
                                          fontSize = 14.5f,
                                          color = MinIncome,
                                      )
                                  },
                                  isLast = index == accounts.size - 1,
                              )
  ```
  With:
  ```kotlin
                              CardRow(
                                  left = {
                                      Row(
                                          verticalAlignment = Alignment.CenterVertically,
                                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                                      ) {
                                          Text(text = icon, fontSize = 18.sp)
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
                                      MonoText(
                                          text = formatCOP(account.balance),
                                          fontSize = 14.5f,
                                          color = MinIncome,
                                      )
                                  },
                                  isLast = index == accounts.size - 1,
                                  showChevron = true,
                                  onClick = { onNavigate(Screen.AccountDetail(account.id)) },
                              )
  ```

- [ ] **Step 5: Build to verify compilation**

  ```bash
  PRE_COMMIT_ALLOW_NO_CONFIG=1 ./gradlew :composeApp:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`. Fix any import or type errors before proceeding.

- [ ] **Step 6: Commit**

  ```bash
  git add composeApp/build.gradle.kts \
    composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt \
    composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt \
    composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt
  PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: wire Screen.AccountDetail + clickable account rows"
  ```

---

## Task 2: VoidEventSheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/VoidEventSheet.kt`

**Context for the implementer:**
- Pattern: identical overlay structure to `CreateAccountSheet.kt` in the same package. Outer `Column(fillMaxSize + black scrim + clickable(enabled = !voiding) { onDismiss() })`, then `Box(weight(1f))`, then the sheet `Column`.
- `Repositories.wallets.voidEvent(id, reason)` is the suspend call. `reason` accepts `null` — pass `null` when the field is blank.
- Color tokens: `MinExpense = Color(0xFFFFB4AB)`, `MinExpenseContainer = Color(0x1FFFB4AB)`. Both are in `com.jvillada.movi.theme.*`.
- `formatCOP(amount: Long): String` is defined in `com.jvillada.movi.ui.components.*`.
- `MonoText` composable is in `com.jvillada.movi.ui.components.*`.
- `FinancialEvent.type` is `TransactionType` (INCOME or EXPENSE). Use it to compute the signed display amount.
- Smart-cast note: `error` is `var error by remember { mutableStateOf<String?>(null) }` (delegated property). Inside `if (error != null)` use `error!!` — Kotlin cannot smart-cast delegated properties.

- [ ] **Step 1: Create `VoidEventSheet.kt`**

  Create `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/VoidEventSheet.kt` with the following content:

  ```kotlin
  package com.jvillada.movi.ui.accounts

  import androidx.compose.foundation.background
  import androidx.compose.foundation.border
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.*
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
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.sp
  import com.jvillada.movi.data.Repositories
  import com.jvillada.movi.shared.model.FinancialEvent
  import com.jvillada.movi.shared.model.TransactionType
  import com.jvillada.movi.theme.*
  import com.jvillada.movi.ui.components.*
  import kotlinx.coroutines.launch

  @Composable
  fun VoidEventSheet(
      event: FinancialEvent,
      onDismiss: () -> Unit,
      onVoided: () -> Unit,
  ) {
      val coroutine = rememberCoroutineScope()
      var reason by remember { mutableStateOf("") }
      var voiding by remember { mutableStateOf(false) }
      var error by remember { mutableStateOf<String?>(null) }

      fun doVoid() {
          voiding = true
          error = null
          coroutine.launch {
              runCatching {
                  Repositories.wallets.voidEvent(event.id, reason.trim().ifBlank { null })
              }.onSuccess { onVoided() }
               .onFailure { error = it.message ?: "No se pudo anular" }
              voiding = false
          }
      }

      val isIncome = event.type == TransactionType.INCOME
      val signedAmount = "${if (isIncome) "+" else "−"}${formatCOP(event.amount)}"

      Column(
          modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.6f))
              .clickable(enabled = !voiding, onClick = onDismiss),
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
              // Drag handle
              Box(
                  modifier = Modifier
                      .align(Alignment.CenterHorizontally)
                      .padding(vertical = 12.dp)
                      .width(32.dp)
                      .height(4.dp)
                      .clip(RoundedCornerShape(2.dp))
                      .background(MinTextFaint),
              )

              // Event summary card
              MinCard(
                  modifier = Modifier.fillMaxWidth(),
                  variant = MinCardVariant.Elevated,
                  padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.Top,
                  ) {
                      Column(modifier = Modifier.weight(1f)) {
                          Text(
                              text = event.description,
                              fontSize = 14.sp,
                              fontWeight = FontWeight.Medium,
                              color = MinText,
                          )
                          Spacer(Modifier.height(3.dp))
                          Text(
                              text = "${event.category} · ${event.source.name}",
                              fontSize = 11.sp,
                              color = MinTextMute,
                          )
                      }
                      Spacer(Modifier.width(12.dp))
                      MonoText(
                          text = signedAmount,
                          fontSize = 14f,
                          color = if (isIncome) MinIncome else MinText,
                      )
                  }
              }

              Spacer(Modifier.height(18.dp))

              // Reason label
              Text(
                  text = "MOTIVO (OPCIONAL)",
                  fontSize = 11.sp,
                  color = MinTextMute,
                  letterSpacing = 0.4.sp,
                  fontWeight = FontWeight.Medium,
              )
              Spacer(Modifier.height(8.dp))

              // Reason input
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .background(MinSurfaceContainerLow)
                      .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                      .padding(horizontal = 14.dp, vertical = 14.dp),
              ) {
                  BasicTextField(
                      value = reason,
                      onValueChange = { reason = it },
                      cursorBrush = SolidColor(MinText),
                      textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth(),
                      decorationBox = { inner ->
                          if (reason.isEmpty()) {
                              Text("Ej: Movimiento duplicado", fontSize = 14.sp, color = MinTextMute)
                          }
                          inner()
                      },
                  )
              }

              // Inline error
              if (error != null) {
                  Spacer(Modifier.height(8.dp))
                  Text(text = error!!, fontSize = 12.sp, color = MinExpense)
              }

              Spacer(Modifier.height(20.dp))

              // CTA
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(54.dp)
                      .clip(RoundedCornerShape(999.dp))
                      .background(if (!voiding) MinExpenseContainer else MinSurfaceContainerLow)
                      .clickable(enabled = !voiding) { doVoid() },
                  contentAlignment = Alignment.Center,
              ) {
                  Text(
                      text = if (voiding) "Anulando…" else "Anular movimiento",
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Medium,
                      color = if (!voiding) MinExpense else MinTextFaint,
                  )
              }

              Spacer(Modifier.height(14.dp))
          }
      }
  }
  ```

- [ ] **Step 2: Build to verify**

  ```bash
  PRE_COMMIT_ALLOW_NO_CONFIG=1 ./gradlew :composeApp:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

  ```bash
  git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/VoidEventSheet.kt
  PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: VoidEventSheet — bottom sheet to annul an event"
  ```

---

## Task 3: AccountDetailScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt`

**Context for the implementer:**
- `Repositories.wallets.getAccount(accountId): Account` and `Repositories.wallets.getEvents(accountId): List<FinancialEvent>` are the two suspend calls. Call them sequentially inside a single `runCatching` block.
- `EventDay` is `data class EventDay(val date: String, val total: Long, val items: List<FinancialEvent>)` in `com.jvillada.movi.shared.model`. Import it from there.
- Events must be grouped client-side using `kotlinx-datetime` (now a dependency of composeApp after Task 1). The `epochToDate` private helper converts epoch millis to an ISO date string.
- `accountTypeInfo` is `private` in `AccountsScreen.kt`. Duplicate it here as a `private` function — both files are in the same package so there is no conflict as long as both are `private`.
- Smart-cast: `account` and `selectedEvent` are delegated properties (`by remember { mutableStateOf(...) }`). Kotlin cannot smart-cast them. Use `account?.let { acc -> ... }` and `selectedEvent?.let { event -> ... }`.
- The `‹` back arrow navigates to `Screen.Accounts` (the list), not `Screen.Dashboard`.
- `MinBottomNav(active = NavTab.HOME)` — AccountDetail is reached from AccountsScreen which is under HOME.
- The bottom nav must handle all 5 tabs even though this is a sub-screen (same as AccountsScreen).
- `ReconciliationStatus` import: `com.jvillada.movi.shared.model.ReconciliationStatus`.
- Build command uses `PRE_COMMIT_ALLOW_NO_CONFIG=1` prefix because the repo has pre-commit installed but no config file.

- [ ] **Step 1: Create `AccountDetailScreen.kt`**

  Create `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt`:

  ```kotlin
  package com.jvillada.movi.ui.accounts

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.material3.LinearProgressIndicator
  import androidx.compose.material3.SnackbarHost
  import androidx.compose.material3.SnackbarHostState
  import androidx.compose.material3.SnackbarResult
  import androidx.compose.material3.Text
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.text.font.FontFamily
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.sp
  import com.jvillada.movi.data.Repositories
  import com.jvillada.movi.shared.model.Account
  import com.jvillada.movi.shared.model.AccountType
  import com.jvillada.movi.shared.model.EventDay
  import com.jvillada.movi.shared.model.FinancialEvent
  import com.jvillada.movi.shared.model.ReconciliationStatus
  import com.jvillada.movi.shared.model.TransactionType
  import com.jvillada.movi.theme.*
  import com.jvillada.movi.ui.Screen
  import com.jvillada.movi.ui.components.*
  import kotlinx.datetime.Instant
  import kotlinx.datetime.TimeZone
  import kotlinx.datetime.toLocalDateTime

  @Composable
  fun AccountDetailScreen(onNavigate: (Screen) -> Unit, accountId: String) {
      var account by remember { mutableStateOf<Account?>(null) }
      var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
      var loading by remember { mutableStateOf(false) }
      var error by remember { mutableStateOf<String?>(null) }
      var refreshKey by remember { mutableStateOf(0) }
      var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
      val snackbarHostState = remember { SnackbarHostState() }

      LaunchedEffect(refreshKey) {
          loading = true
          error = null
          runCatching {
              val acc = Repositories.wallets.getAccount(accountId)
              val events = Repositories.wallets.getEvents(accountId)
              val grouped = events
                  .groupBy { epochToDate(it.timestamp) }
                  .map { (date, items) ->
                      EventDay(
                          date  = date,
                          total = items.sumOf {
                              if (it.type == TransactionType.INCOME) it.amount else -it.amount
                          },
                          items = items.sortedByDescending { it.timestamp },
                      )
                  }
                  .sortedByDescending { it.date }
              acc to grouped
          }.onSuccess { (acc, grouped) ->
              account = acc
              days = grouped
          }.onFailure { e ->
              error = e.message ?: "Error al cargar la cuenta"
          }
          loading = false
      }

      LaunchedEffect(error) {
          val msg = error ?: return@LaunchedEffect
          val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
          error = null
          if (result == SnackbarResult.ActionPerformed) refreshKey++
      }

      val totalEvents = days.sumOf { it.items.size }

      Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
          Column(modifier = Modifier.fillMaxSize()) {

              // Header
              val (typeIcon, typeLabel) = account?.type?.let { accountTypeIcon(it) } ?: ("" to "")
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 20.dp)
                      .padding(top = 8.dp, bottom = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                  Text(
                      text = "‹",
                      fontSize = 22.sp,
                      color = MinText,
                      modifier = Modifier.clickable { onNavigate(Screen.Accounts) },
                  )
                  Text(
                      text = account?.name ?: "",
                      fontSize = 22.sp,
                      fontWeight = FontWeight.Medium,
                      color = MinText,
                      letterSpacing = (-0.4).sp,
                      modifier = Modifier.weight(1f),
                  )
                  if (typeIcon.isNotEmpty()) {
                      Text(text = typeIcon, fontSize = 20.sp)
                  }
              }

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
                      start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp,
                  ),
              ) {
                  // Balance hero card
                  account?.let { acc ->
                      item {
                          MinCard(
                              modifier = Modifier.fillMaxWidth(),
                              variant = MinCardVariant.Elevated,
                              padding = PaddingValues(20.dp),
                          ) {
                              Text(
                                  text = "SALDO ACTUAL",
                                  fontSize = 11.sp,
                                  color = MinTextMute,
                                  letterSpacing = 0.4.sp,
                                  fontWeight = FontWeight.Medium,
                              )
                              Spacer(Modifier.height(8.dp))
                              MonoText(
                                  text = formatCOP(acc.balance),
                                  fontSize = 28f,
                                  color = MinIncome,
                                  fontWeight = FontWeight.Medium,
                              )
                              if (typeLabel.isNotEmpty()) {
                                  Spacer(Modifier.height(4.dp))
                                  Text(
                                      text = "COP · $typeLabel",
                                      fontSize = 11.sp,
                                      color = MinTextMute,
                                  )
                              }
                          }
                          Spacer(Modifier.height(20.dp))
                      }
                  }

                  // Section header
                  item {
                      MinSectionHeader(title = "Movimientos", count = totalEvents.takeIf { it > 0 })
                  }

                  // Empty state
                  if (!loading && days.isEmpty()) {
                      item {
                          Box(
                              modifier = Modifier
                                  .fillParentMaxWidth()
                                  .padding(top = 60.dp),
                              contentAlignment = Alignment.Center,
                          ) {
                              Text("Sin movimientos aún", fontSize = 14.sp, color = MinTextMute)
                          }
                      }
                  }

                  // Day groups
                  days.forEach { day ->
                      item {
                          Column(modifier = Modifier.padding(top = 20.dp)) {
                              Row(
                                  modifier = Modifier
                                      .fillMaxWidth()
                                      .padding(horizontal = 4.dp, vertical = 8.dp),
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
                                  day.items.forEachIndexed { i, event ->
                                      val isIncome = event.type == TransactionType.INCOME
                                      Column {
                                          Row(
                                              modifier = Modifier
                                                  .fillMaxWidth()
                                                  .clickable { selectedEvent = event }
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
                                                          text = event.description,
                                                          fontSize = 14.5.sp,
                                                          fontWeight = FontWeight.Medium,
                                                          color = MinText,
                                                          letterSpacing = (-0.1).sp,
                                                      )
                                                      if (event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                                                          StatusDot(MinWarn)
                                                      }
                                                  }
                                                  Spacer(Modifier.height(2.dp))
                                                  Row(
                                                      verticalAlignment = Alignment.CenterVertically,
                                                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                  ) {
                                                      Text(event.category, fontSize = 12.sp, color = MinTextMute)
                                                      StatusDot(MinTextFaint, 2.dp)
                                                      Text(
                                                          text = event.source.name,
                                                          fontSize = 11.sp,
                                                          fontFamily = FontFamily.Monospace,
                                                          color = MinTextMute,
                                                          letterSpacing = 0.3.sp,
                                                      )
                                                  }
                                              }
                                              Text(
                                                  text = "${if (isIncome) "+" else "−"}${formatCOP(event.amount)}",
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

              MinBottomNav(active = NavTab.HOME) { tab ->
                  when (tab) {
                      NavTab.HOME         -> onNavigate(Screen.Dashboard)
                      NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                      NavTab.ADD          -> onNavigate(Screen.QuickAdd)
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

          selectedEvent?.let { event ->
              VoidEventSheet(
                  event = event,
                  onDismiss = { selectedEvent = null },
                  onVoided = {
                      selectedEvent = null
                      refreshKey++
                  },
              )
          }
      }
  }

  private fun epochToDate(millis: Long): String =
      Instant.fromEpochMilliseconds(millis)
          .toLocalDateTime(TimeZone.currentSystemDefault())
          .date
          .toString()

  private fun accountTypeIcon(type: AccountType): Pair<String, String> = when (type) {
      AccountType.CASH        -> "💵" to "Efectivo"
      AccountType.SAVINGS     -> "🏦" to "Ahorros"
      AccountType.CHECKING    -> "💳" to "Corriente"
      AccountType.INVESTMENT  -> "📈" to "Inversión"
      AccountType.CREDIT_CARD -> "💳" to "Crédito"
  }
  ```

- [ ] **Step 2: Build to verify**

  ```bash
  PRE_COMMIT_ALLOW_NO_CONFIG=1 ./gradlew :composeApp:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`. Common issues to fix:
  - Missing import: check all `com.jvillada.movi.shared.model.*` types are imported individually.
  - `takeIf` on `Int`: `totalEvents.takeIf { it > 0 }` returns `Int?`, which `MinSectionHeader(count: Int?)` accepts.
  - If `account?.type?.let { accountTypeIcon(it) }` causes a type issue, replace with `account?.let { accountTypeIcon(it.type) }`.

- [ ] **Step 3: Commit**

  ```bash
  git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt
  PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: AccountDetailScreen — account balance + event history + void"
  ```

---

## Manual Verification Checklist

After all tasks are committed, install on a device/emulator and verify:

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

- [ ] **AccountsScreen rows have chevron** — each account row shows `›` on the right
- [ ] **Tap account row → AccountDetailScreen opens** — shows account name, type icon, current balance
- [ ] **Balance card** — "SALDO ACTUAL" label, amount in green `MinIncome`
- [ ] **Event list grouped by day** — date headers with signed daily totals, events below in cards
- [ ] **Empty state** — if account has no events: "Sin movimientos aún" centered
- [ ] **Loading indicator** — `LinearProgressIndicator` visible during data load
- [ ] **Tap event row → VoidEventSheet opens** — shows event summary card (description, amount, category, source)
- [ ] **Reason field optional** — can tap "Anular movimiento" with empty reason
- [ ] **Void success** — sheet closes, event list reloads (event disappears), balance updates
- [ ] **Void already-voided event** — sheet shows error message inline, stays open
- [ ] **Network error in AccountDetail** — snackbar appears with "Reintentar" action
- [ ] **Back arrow** — navigates to AccountsScreen (not Dashboard)
