# LOAN Account Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `AccountType.LOAN` so the user can seed real bank loans (consumo, libranza, vehículo) as standing debt accounts, reusing the credit-card opening-debt machinery.

**Architecture:** `LOAN` joins `CREDIT_CARD` as a "debt" account — balance is positive debt, opening balance is an EXPENSE "Deuda inicial", payments (INCOME) lower it. One enum case added to `:core`; the compiler then forces every exhaustive `when` over `AccountType` to handle it. Manual entry via the existing `CreateAccountSheet`; COP-only (no currency selector). No DB, repository, or statement-pipeline changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`:shared`), Ktor + Exposed (`:server`), kotlin.test. Build with JBR 21 (`./gradlew`).

**Spec:** `docs/superpowers/specs/2026-06-13-loan-account-type-design.md`

---

## File map

- **Modify** `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt` — add `LOAN` to enum.
- **Modify** `server/.../balance/Balances.kt` — `signedDelta` debt branch covers `LOAN`.
- **Modify** `server/.../balance/OpeningBalance.kt` — `isCard` → `isDebt` (CREDIT_CARD || LOAN).
- **Modify** `server/.../test/.../balance/BalancesTest.kt` — LOAN debt test.
- **Modify** `server/.../test/.../balance/OpeningBalanceTest.kt` — LOAN opening-debt test.
- **Modify** `shared/.../ui/components/MoneyDisplay.kt` — `isDebtAccount` covers `LOAN`.
- **Modify** `shared/.../ui/accounts/AccountsScreen.kt` — icon/label `when` branch.
- **Modify** `shared/.../ui/accounts/AccountDetailScreen.kt` — icon/label `when` branch.
- **Modify** `shared/.../ui/dashboard/DashboardScreen.kt` — label `when` branch.
- **Modify** `shared/.../ui/accounts/CreateAccountSheet.kt` — LOAN chip, debt-based label, debt-based boundary clear.

> **Note on test infra:** `:server` and `:core` have JVM unit tests (`kotlin.test`). The `:shared` UI changes are verified by compilation only — there is no Compose UI test harness in this repo. The compiler enforces `when` exhaustiveness, so a missing branch fails the build (our safety net for UI tasks).

---

## Task 1: Add LOAN to the AccountType enum

**Files:**
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt:6`

This is the root change. It will (intentionally) break compilation of the three exhaustive `when` blocks until Tasks 5-7 fix them — that is the safety net, not a problem.

- [ ] **Step 1: Add the enum case**

Change line 6 from:

```kotlin
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT }
```

to:

```kotlin
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, LOAN, INVESTMENT }
```

- [ ] **Step 2: Commit**

```bash
git add core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt
git commit -m "feat(core): add LOAN account type"
```

> Do not build yet — the UI `when` blocks won't compile until Tasks 5-7. Server tasks (2-4) only depend on `:core` + `:server`, which stay consistent within each task.

---

## Task 2: signedDelta treats LOAN as debt (TDD)

**Files:**
- Test: `server/src/test/kotlin/com/jvillada/movi/server/balance/BalancesTest.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/balance/Balances.kt:12-16`

- [ ] **Step 1: Write the failing test**

Add this test method inside `class BalancesTest` (after the existing `signedDelta on credit card...` test):

```kotlin
    @Test
    fun `signedDelta on loan purchase raises debt payment lowers it`() {
        assertEquals(100, signedDelta(AccountType.LOAN, TransactionType.EXPENSE, 100)) // desembolso/deuda
        assertEquals(-100, signedDelta(AccountType.LOAN, TransactionType.INCOME, 100)) // pago cuota
    }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.BalancesTest"`
Expected: FAIL — `signedDelta(LOAN, INCOME, 100)` returns `100` (asset branch), not `-100`.

- [ ] **Step 3: Add LOAN to the debt branch**

In `Balances.kt`, change the `signedDelta` function (lines 12-16) from:

```kotlin
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD -> if (type == TransactionType.EXPENSE) amount else -amount
        else                    -> if (type == TransactionType.INCOME) amount else -amount
    }
```

to:

```kotlin
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD,
        AccountType.LOAN -> if (type == TransactionType.EXPENSE) amount else -amount
        else             -> if (type == TransactionType.INCOME) amount else -amount
    }
```

Also update the KDoc on lines 9-11 to name both debt types — change line 10 from:

```kotlin
 * CREDIT_CARD (balance = positive debt): EXPENSE (purchase) raises debt, INCOME (payment) lowers it.
```

to:

```kotlin
 * CREDIT_CARD / LOAN (balance = positive debt): EXPENSE raises debt, INCOME (payment) lowers it.
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.BalancesTest"`
Expected: PASS (all tests in the class).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/balance/Balances.kt \
        server/src/test/kotlin/com/jvillada/movi/server/balance/BalancesTest.kt
git commit -m "feat(server): LOAN balance behaves as debt"
```

---

## Task 3: openingEventFor emits Deuda inicial for LOAN (TDD)

**Files:**
- Test: `server/src/test/kotlin/com/jvillada/movi/server/balance/OpeningBalanceTest.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/balance/OpeningBalance.kt:18-33`

- [ ] **Step 1: Write the failing test**

Add this test method inside `class OpeningBalanceTest` (after the `credit card opening balance...` test). It covers the spec's exact numbers (open 540_786, pay 40_786 → 500_000):

```kotlin
    @Test
    fun `loan opening balance is EXPENSE Deuda inicial and a payment lowers it`() {
        val acc = Account(id = "l", name = "Crédito Vehículo", type = AccountType.LOAN, balance = 540_786, currency = "COP")
        val opening = assertNotNull(openingEventFor(acc, now = 1000L))
        assertEquals(TransactionType.EXPENSE, opening.type)
        assertEquals("Deuda inicial", opening.description)
        assertEquals("Otros", opening.category)
        assertEquals(540_786, opening.amount)
        // opening debt derives correctly
        assertEquals(540_786, computeBalances(acc.type, listOf(opening))["COP"])
        // an INCOME payment lowers the debt
        val payment = opening.copy(id = "pay", type = TransactionType.INCOME, amount = 40_786)
        assertEquals(500_000, computeBalances(acc.type, listOf(opening, payment))["COP"])
    }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.OpeningBalanceTest"`
Expected: FAIL — for `LOAN`, `isCard` is `false`, so the event is INCOME "Saldo inicial"; assertions on EXPENSE / "Deuda inicial" fail.

- [ ] **Step 3: Generalize isCard → isDebt**

In `OpeningBalance.kt`, change line 20 from:

```kotlin
    val isCard = account.type == AccountType.CREDIT_CARD
```

to:

```kotlin
    val isDebt = account.type == AccountType.CREDIT_CARD || account.type == AccountType.LOAN
```

Then replace the three `isCard` references on lines 24, 27, 28 with `isDebt`:

```kotlin
        type                 = if (isDebt) TransactionType.EXPENSE else TransactionType.INCOME,
        amount               = abs(account.balance),
        currency             = account.currency,
        category             = if (isDebt) "Otros" else "Otros ingresos",
        description          = if (isDebt) "Deuda inicial" else "Saldo inicial",
```

Also update the KDoc on line 15 — change:

```kotlin
 * assets open with an INCOME ("Saldo inicial"), credit cards with an EXPENSE ("Deuda inicial" —
```

to:

```kotlin
 * assets open with an INCOME ("Saldo inicial"), debt accounts (credit card / loan) with an
 * EXPENSE ("Deuda inicial" —
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.OpeningBalanceTest"`
Expected: PASS (all tests in the class).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/balance/OpeningBalance.kt \
        server/src/test/kotlin/com/jvillada/movi/server/balance/OpeningBalanceTest.kt
git commit -m "feat(server): LOAN opening balance is Deuda inicial"
```

---

## Task 4: Verify the full server test suite

**Files:** none (verification only)

- [ ] **Step 1: Run all server tests**

Run: `./gradlew :server:test`
Expected: PASS — all tests, including the two new ones. No compilation errors in `:server` or `:core`.

> If anything else in `:server` matched `AccountType` exhaustively it would fail here. `FinanceRoutes.kt:111` and `AccountRoutes.kt:104` use `AccountType.valueOf(...)` (string → enum), which is unaffected by a new case.

---

## Task 5: isDebtAccount covers LOAN

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MoneyDisplay.kt:28`

This is the central UI debt predicate (drives red/debt styling and the assets-vs-debts split).

- [ ] **Step 1: Update the predicate**

Change line 28 from:

```kotlin
fun isDebtAccount(type: AccountType): Boolean = type == AccountType.CREDIT_CARD
```

to:

```kotlin
fun isDebtAccount(type: AccountType): Boolean =
    type == AccountType.CREDIT_CARD || type == AccountType.LOAN
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MoneyDisplay.kt
git commit -m "feat(ui): treat LOAN as a debt account in money display"
```

---

## Task 6: Add LOAN branch to the two icon/label maps

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt:282-288`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt:328-334`

Both are identical `when (type)` maps with no `else`, so each needs the LOAN case to compile.

- [ ] **Step 1: AccountsScreen — add the branch**

In `accountTypeInfo`, add a `LOAN` line so the `when` reads:

```kotlin
private fun accountTypeInfo(type: AccountType): Pair<String, String> = when (type) {
    AccountType.CASH        -> "💵" to "Efectivo"
    AccountType.SAVINGS     -> "🏦" to "Ahorros"
    AccountType.CHECKING    -> "💳" to "Corriente"
    AccountType.INVESTMENT  -> "📈" to "Inversión"
    AccountType.CREDIT_CARD -> "💳" to "Crédito"
    AccountType.LOAN        -> "💸" to "Préstamo"
}
```

- [ ] **Step 2: AccountDetailScreen — add the branch**

In `accountTypeIcon`, add the same `LOAN` line so the `when` reads:

```kotlin
private fun accountTypeIcon(type: AccountType): Pair<String, String> = when (type) {
    AccountType.CASH        -> "💵" to "Efectivo"
    AccountType.SAVINGS     -> "🏦" to "Ahorros"
    AccountType.CHECKING    -> "💳" to "Corriente"
    AccountType.INVESTMENT  -> "📈" to "Inversión"
    AccountType.CREDIT_CARD -> "💳" to "Crédito"
    AccountType.LOAN        -> "💸" to "Préstamo"
}
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt \
        shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt
git commit -m "feat(ui): LOAN icon and label in account lists"
```

---

## Task 7: Add LOAN branch to the dashboard type label

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt:236-243`

- [ ] **Step 1: Add the branch**

In the `typeLabel` lambda, add a `LOAN` line so the `when` reads:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt
git commit -m "feat(ui): LOAN label on dashboard account list"
```

---

## Task 8: Expose LOAN in the create-account form

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt`

Three precise edits: add the chip; make the amount label debt-based (so LOAN shows "DEUDA INICIAL"); make the balance-clear-on-type-change debt-based (so switching asset↔LOAN clears the amount, not just asset↔card). The COP/USD selector stays card-only.

- [ ] **Step 1: Add the LOAN type option**

In `TYPE_OPTIONS` (lines 31-37), add a LOAN entry after the credit-card one:

```kotlin
private val TYPE_OPTIONS = listOf(
    TypeOption(AccountType.CASH, "💵 Efectivo"),
    TypeOption(AccountType.SAVINGS, "🏦 Ahorros"),
    TypeOption(AccountType.CHECKING, "🏧 Corriente"),
    TypeOption(AccountType.INVESTMENT, "📈 Inversión"),
    TypeOption(AccountType.CREDIT_CARD, "💳 Crédito"),
    TypeOption(AccountType.LOAN, "💸 Préstamo"),
)
```

- [ ] **Step 2: Make the type-change reset debt-aware**

The chip `onClick` (lines 143-151) currently clears the balance only when card-ness flips. Switching e.g. CASH→LOAN also flips debt meaning and must clear the amount. Replace the `onClick` body:

```kotlin
                                onClick = {
                                    val wasDebt = isDebtAccount(selectedType)
                                    val willBeDebt = isDebtAccount(option.type)
                                    val willBeCard = option.type == AccountType.CREDIT_CARD
                                    selectedType = option.type
                                    // Currency selector is card-only; any non-card type is COP.
                                    if (!willBeCard) selectedCurrency = "COP"
                                    // Crossing the debt↔asset boundary flips the amount's meaning;
                                    // clear it so a debt isn't silently kept as a positive balance.
                                    if (wasDebt != willBeDebt) initialBalance = ""
                                },
```

(`isDebtAccount` is already importable here — `CreateAccountSheet.kt` imports `com.jvillada.movi.ui.components.*` on line 26, and `isDebtAccount` lives in that package.)

- [ ] **Step 3: Make the amount label debt-based**

Change line 161 from:

```kotlin
            val isCard = selectedType == AccountType.CREDIT_CARD
            SectionLabel(if (isCard) "DEUDA INICIAL" else "SALDO INICIAL")
```

to:

```kotlin
            val isCard = selectedType == AccountType.CREDIT_CARD
            val isDebt = isDebtAccount(selectedType)
            SectionLabel(if (isDebt) "DEUDA INICIAL" else "SALDO INICIAL")
```

Leave the `if (isCard)` currency-selector block (line 194) unchanged — the COP/USD selector stays exclusive to credit cards; LOAN is COP-only. The submit line 61 (`currency = if (selectedType == AccountType.CREDIT_CARD) selectedCurrency else "COP"`) also stays unchanged: a LOAN already resolves to "COP".

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt
git commit -m "feat(ui): create LOAN accounts with opening debt"
```

---

## Task 9: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Compile shared (all targets the build covers) + server**

Run: `./gradlew :shared:compileKotlinMetadata :server:test`
Expected: BUILD SUCCESSFUL. (Compilation proves every `when` over `AccountType` is exhaustive; tests prove debt math.)

- [ ] **Step 2: Build and run the web app for manual check**

Run: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
Then in the browser:
1. Create account → name "Crédito Vehículo Santander", type **💸 Préstamo**, amount `161115271`. Confirm the amount label reads **DEUDA INICIAL** and no COP/USD selector appears.
2. Save → the account appears in *Mis cuentas* with debt styling (💸 Préstamo) and the **DEUDA ACTUAL** hero shows `$161.115.271`.
3. Switch the type chip from Préstamo to Efectivo and back → confirm the amount field clears when crossing the boundary and the label flips DEUDA INICIAL ↔ SALDO INICIAL.
4. Record an INCOME (pago) on the account → confirm the debt decreases.

- [ ] **Step 3: Seed the real loans**

Using the screenshots in `docs/movements/`, create one LOAN account per loan with its *deuda a la fecha* / *saldo* (e.g. Bancolombia Consumo `540786`, Santander Vehículo `161115271`, plus libranza / libre inversión / AV Villas). This is a one-time data-entry step, not code.

- [ ] **Step 4: Final commit (only if any non-code tweaks were needed)**

```bash
git status   # expect clean if no further changes
```

---

## Self-review notes

- **Spec coverage:** model change → T1; debt math → T2; opening debt → T3; debt predicate → T5; three `when` maps → T6/T7; create-form chip + label + COP-only → T8; tests → T2/T3/T4; manual verify + seed → T9. All spec sections covered.
- **Type consistency:** `isDebt` (server) and `isDebtAccount` (UI) are distinct, intentionally — one is a local val in `OpeningBalance.kt`, the other the shared UI predicate in `MoneyDisplay.kt`. Emoji 💸 and label "Préstamo" used identically across T6/T7/T8.
- **No DB migration:** account `type` is persisted as a string; `AccountType.valueOf` reads it back. A new enum value needs no schema change.
