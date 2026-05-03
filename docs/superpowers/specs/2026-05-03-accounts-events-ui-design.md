# Plan 5 — Accounts + Events UI Screens

## Overview

Add `AccountsScreen` + `CreateAccountSheet` to the Compose Multiplatform app, and update the three existing screens (Dashboard, Transactions, QuickAdd) to handle accounts data correctly. No new backend endpoints — all API calls already exist.

---

## Architecture

Minimal changes. Same stateless Compose pattern used throughout: `LaunchedEffect + remember { mutableStateOf }`, no ViewModel, no new shared state layer.

New files:
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt`
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt`

Modified files:
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt` — add `Screen.Accounts`
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt`

Design system components in use: `MinCard`, `CardRow`, `MinBottomNav`, `formatCOP()`, color tokens (`MinPrimary`, `MinIncome`, `MinExpense`).

---

## Section 1 — AccountsScreen

**Route:** `Screen.Accounts` (new)

**Data:** calls existing `getAccounts()` API.

**Layout:**
- Header: back arrow + "Mis cuentas" title + "+ Nueva" button (opens `CreateAccountSheet`)
- Total-assets card: sum of all account balances in `MinIncome` green
- Section label "CUENTAS (N)" + list of `CardRow` items: emoji icon + name + type + balance
- Account type icons: 💵 CASH · 🏦 SAVINGS · 💳 CHECKING · 📈 INVESTMENT

**States:**
- Loading: `LinearProgressIndicator()` at top
- Empty: centered "Sin cuentas aún" + "Crear primera cuenta" button → opens `CreateAccountSheet`
- Error: snackbar with message + "Reintentar" action that re-triggers the load

**No tap action on account rows** — AccountDetail deferred to a future plan.

---

## Section 2 — CreateAccountSheet

**Trigger:** "+ Nueva" button on AccountsScreen, "Crear primera cuenta" on Dashboard empty state.

**Signature:** `CreateAccountSheet(onDismiss: () -> Unit, onAccountCreated: () -> Unit)` — callers pass `onAccountCreated` to reload their accounts list after a successful create.

**Implementation:** `ModalBottomSheet` composable sliding up over the current screen.

**Fields:**
- Name (required) — `TextField`, placeholder "Ej: Bancolombia Ahorros"
- Type (required) — 2×2 grid of selectable chips: 💵 Efectivo (CASH) · 🏦 Ahorros (SAVINGS) · 💳 Corriente (CHECKING) · 📈 Inversión (INVESTMENT). Default: CASH selected.
- Initial balance (optional) — numeric `TextField`, default "0"

**Behavior:**
- "Crear cuenta" CTA disabled while `name.isBlank()`
- On success: sheet closes → account list reloads
- On error: snackbar with server message if available, fallback generic message

**API call:** existing `createAccount(name, type, initialBalance)`.

---

## Section 3 — DashboardScreen changes

**Current state:** already calls `getFinanceSummary(scope)` + `getAccounts()`. Balance and income/expense summary already wired.

**Changes:**
- Accounts section header: add "Ver todas +" link → `navController.navigate(Screen.Accounts)`
- Each account `CardRow`: name + type label + balance (already exists, just confirm rendering)
- **Empty state** when `accounts.isEmpty()`: inline card with "Sin cuentas aún" + "Crear primera cuenta" button that opens `CreateAccountSheet` directly (without navigating)
- Loading: `LinearProgressIndicator()` while `loading == true`
- Error: snackbar with "Reintentar" action

---

## Section 4 — TransactionsScreen changes

**Current state:** already calls `getEventsByDay()` and renders grouped event rows.

**Changes:**
- **Empty state** when list is empty: centered "Sin movimientos aún" text
- Loading: `LinearProgressIndicator()` while `loading == true`
- Error: snackbar with "Reintentar" action

No new navigation or tap actions.

---

## Section 5 — QuickAddScreen changes

**Current state:** already calls `getAccounts()` + `postEvent()`.

**Changes:**
- If `accounts.isEmpty()`: disable "Guardar" button + show inline hint "Primero creá una cuenta" with a button that opens `CreateAccountSheet`
- Error: snackbar with "Reintentar" action on account load failure

---

## Section 6 — Navigation wiring

```kotlin
// Navigation.kt — add to sealed class Screen
data object Accounts : Screen()

// NavHost addition
composable<Screen.Accounts> {
    AccountsScreen(navController = navController)
}
```

Entry points:
- Dashboard "Ver todas +" → `navController.navigate(Screen.Accounts)`
- Dashboard empty state button → opens `CreateAccountSheet` (modal, no navigation)
- AccountsScreen "+ Nueva" → opens `CreateAccountSheet` (modal, no navigation)

Bottom nav: unchanged.

---

## Section 7 — Error handling & loading

Uniform pattern across all screens:

```kotlin
var loading by remember { mutableStateOf(false) }
var error by remember { mutableStateOf<String?>(null) }
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(Unit) { loadData() }

LaunchedEffect(error) {
    error?.let {
        val result = snackbarHostState.showSnackbar(it, actionLabel = "Reintentar")
        if (result == SnackbarResult.ActionPerformed) loadData()
        error = null
    }
}
```

- Loading indicator: `LinearProgressIndicator()` at top of screen while `loading == true`
- Error source: catch block sets `error = e.message ?: "Error al cargar, intentá de nuevo"`

---

## Section 8 — Testing

Manual smoke test checklist:
1. `./gradlew :composeApp:assembleDebug` passes with no errors
2. Fresh user (no accounts): Dashboard shows empty state, QuickAdd blocks submit
3. Create account via Dashboard empty state → appears in Dashboard list and AccountsScreen
4. Navigate Dashboard "Ver todas +" → AccountsScreen shows same accounts
5. Create account via AccountsScreen "+ Nueva" → list reloads
6. Create event in QuickAdd with new account → appears in TransactionsScreen
7. With server stopped: all screens show snackbar + "Reintentar" re-triggers load

No unit tests added — no new business logic, pure UI wiring.

---

## Out of scope (future plans)

- AccountDetail screen (tap on account row)
- Edit / delete account
- Void event UI
- Reconciliation / manual balance correction
- `UiState<T>` sealed class refactor (when screen count grows)
