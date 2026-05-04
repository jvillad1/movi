# AccountDetail + VoidEvent UI Design

## Goal

Add a drill-down screen that shows an account's event history and allows the user to void any individual event from within that screen.

## Architecture

### Files

| File | Change |
|---|---|
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt` | Add `data class AccountDetail(val accountId: String) : Screen()` |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt` | Wire `Screen.AccountDetail` |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt` | Make account rows clickable |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt` | **New** — full sub-screen |
| `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/VoidEventSheet.kt` | **New** — bottom sheet for voiding |
| `composeApp/build.gradle.kts` | Add `kotlinx-datetime` to `commonMain.dependencies` |

### Data flow

`AccountDetailScreen(accountId)` loads `getAccount(accountId)` and `getEvents(accountId)` in parallel inside `LaunchedEffect(refreshKey)`. `getEvents(accountId)` returns `List<FinancialEvent>` — events are grouped client-side by day using `kotlinx-datetime` (already in the version catalog). The resulting `List<EventDay>` is rendered identically to `TransactionsScreen`.

`var selectedEvent: FinancialEvent?` drives the `VoidEventSheet` overlay. On void success the sheet calls `onVoided()`, which sets `selectedEvent = null` and increments `refreshKey`, reloading both balance and event list.

### Navigation

`AccountDetail` is a `data class` (not `data object`) because it carries `accountId`. Entry point: tapping any account row in `AccountsScreen`. Back arrow navigates to `Screen.Accounts`.

Void is accessible **only from AccountDetail** — `TransactionsScreen` remains read-only.

---

## AccountDetailScreen

**Signature:** `fun AccountDetailScreen(onNavigate: (Screen) -> Unit, accountId: String)`

**State:**
```kotlin
var account by remember { mutableStateOf<Account?>(null) }
var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
var loading by remember { mutableStateOf(false) }
var error by remember { mutableStateOf<String?>(null) }
var refreshKey by remember { mutableStateOf(0) }
var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
val snackbarHostState = remember { SnackbarHostState() }
```

**Layout (outermost `Box`):**
1. `Column` (full screen):
   - Header row: `‹` back arrow → `onNavigate(Screen.Accounts)`, account name (centered, weight 1), type icon
   - `LinearProgressIndicator` while `loading`, else `Spacer(4.dp)`
   - `LazyColumn` (weight 1, bottom padding 80dp):
     - If `account != null`: balance hero card (`MinCard Elevated`, "SALDO ACTUAL" label, amount in `MinIncome`, "COP · {typeLabel}" subtitle)
     - `MinSectionHeader("Movimientos", count = total event count)`
     - If `!loading && days.isEmpty()`: centered empty state "Sin movimientos aún"
     - For each `EventDay`: date header row (date uppercase left, signed daily total right) + `MinCard Elevated` with one row per `FinancialEvent`
     - Each event row: `clickable { selectedEvent = event }`, description + optional `StatusDot(MinWarn)` for `UNCONFIRMED`, category + source metadata, signed amount (income = `MinIncome`)
   - `MinBottomNav(active = NavTab.HOME)` with standard tab routing
2. `SnackbarHost` at `BottomCenter`, padding 80dp
3. `if (selectedEvent != null) VoidEventSheet(event = selectedEvent!!, onDismiss = { selectedEvent = null }, onVoided = { selectedEvent = null; refreshKey++ })`

**Client-side day grouping:**
```kotlin
import kotlinx.datetime.*

private fun epochToDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.toString()

// In LaunchedEffect:
val events = Repositories.wallets.getEvents(accountId)
days = events
    .groupBy { epochToDate(it.timestamp) }
    .map { (date, items) ->
        EventDay(
            date = date,
            total = items.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount },
            items = items.sortedByDescending { it.timestamp },
        )
    }
    .sortedByDescending { it.date }
```

---

## VoidEventSheet

**Signature:** `fun VoidEventSheet(event: FinancialEvent, onDismiss: () -> Unit, onVoided: () -> Unit)`

**State:**
```kotlin
var reason by remember { mutableStateOf("") }
var voiding by remember { mutableStateOf(false) }
var error by remember { mutableStateOf<String?>(null) }
val coroutine = rememberCoroutineScope()
```

**Layout:** Same overlay pattern as `CreateAccountSheet` — full-screen `Column` with black scrim (`Color.Black.copy(alpha = 0.6f)`, `enabled = !voiding` on the outer clickable), `Box(weight(1f))`, then the sheet `Column`:
- Drag handle
- Event summary card (`MinCard Elevated`): description, signed amount, category + source + date metadata
- `SectionLabel("MOTIVO (OPCIONAL)")`
- `BasicTextField` for reason (same style as CreateAccountSheet fields)
- Inline error text in `MinExpense` if `error != null`
- CTA: full-width pill, background `MinExpenseContainer`, text `MinExpense`, label "Anular movimiento" / "Anulando…"

**Save logic:**
```kotlin
fun void() {
    voiding = true; error = null
    coroutine.launch {
        runCatching {
            Repositories.wallets.voidEvent(event.id, reason.trim().ifBlank { null })
        }.onSuccess { onVoided() }
         .onFailure { error = it.message ?: "No se pudo anular" }
        voiding = false
    }
}
```

CTA is always enabled (no required fields) — user can void without a reason.

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Load failure (`getAccount` or `getEvents`) | Snackbar with "Reintentar" → `refreshKey++` |
| Void failure (network, 500) | Inline error below CTA; button re-enables for retry |
| Already voided (409 Conflict) | Same inline error with server message |
| Empty event list | Centered "Sin movimientos aún" |

---

## Navigation Changes

**Navigation.kt** — after `Accounts`:
```kotlin
data class AccountDetail(val accountId: String) : Screen()
```

**App.kt** — in the `when` block:
```kotlin
is Screen.AccountDetail -> AccountDetailScreen(navigate, currentScreen.accountId)
```

**AccountsScreen.kt** — add to each account `CardRow`:
```kotlin
showChevron = true,
onClick = { onNavigate(Screen.AccountDetail(account.id)) },
```

---

## Out of Scope

- Editing events (amount, category, description)
- Void from TransactionsScreen
- Filtering events by type within AccountDetail
- Pagination (load all events for the account)
