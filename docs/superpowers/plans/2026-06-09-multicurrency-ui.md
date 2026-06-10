# Multi-currency & Credit-Card Debt UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the server's per-currency balances, credit-card debt, and COP estimate across the Compose UI (accounts list, account detail, dashboard), fixing the asset/debt and currency-mixing display bugs.

**Architecture:** One new file of pure display helpers + a shared breakdown composable (`ui/components/MoneyDisplay.kt`); three screens consume it. No model, network, or server changes — the shared `Account`/`FinancialEvent` models already carry `balancesByCurrency`, `estimatedTotalCop`, and `currency`.

**Tech Stack:** Compose Multiplatform (`:shared` commonMain), Kotlin. No test infra exists in `:shared` (spec decision: helpers kept pure; verification = compile gates + Playwright visual check against real local data).

**Spec:** `docs/superpowers/specs/2026-06-09-multicurrency-ui-design.md`

**Pre-reqs:**
- Work on branch `feat/multicurrency-ui` (create from master before Task 1: `git checkout -b feat/multicurrency-ui`).
- Local Postgres running with the previously imported real TC data (account `acc_tc_fix`-style: USD 181 / COP 222.933 / estimate ≈872.377). `server/.env` already configured.
- Visual model reference (theme colors): `MinIncome` (green), `MinExpense` (red), `MinText`, `MinTextMute` from `com.jvillada.movi.theme`.

**Compile gate used throughout:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs --console=plain` (covers commonMain for both app targets; iOS compiles the same commonMain).

---

### Task 1: MoneyDisplay helpers + CurrencyBreakdown composable

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MoneyDisplay.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.MinTextMute

/** Thousands-grouped absolute value: 222933 -> "222.933". Sign is the caller's concern. */
private fun groupThousands(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    return abs.toString().reversed().chunked(3).joinToString(".").reversed()
}

/** Currency-aware money text: COP -> "$222.933", USD -> "US$181", other -> "EUR 50". */
fun formatMoney(amount: Long, currency: String): String = when (currency) {
    "COP" -> formatCOP(amount)
    "USD" -> "US$" + groupThousands(amount)
    else  -> "$currency " + groupThousands(amount)
}

/**
 * (activos, deudas, neto) across accounts.
 * Assets = COP balance of non-card accounts. Debts = each card's COP estimate
 * (or its COP balance when there is nothing foreign to estimate). Net = assets − debts.
 */
fun assetsDebtsNet(accounts: List<Account>): Triple<Long, Long, Long> {
    val activos = accounts.filter { it.type != AccountType.CREDIT_CARD }.sumOf { it.balance }
    val deudas = accounts.filter { it.type == AccountType.CREDIT_CARD }
        .sumOf { it.estimatedTotalCop ?: it.balance }
    return Triple(activos, deudas, activos - deudas)
}

/** The display value for a card's debt and whether it is a TRM estimate. */
fun cardDebt(account: Account): Pair<Long, Boolean> =
    (account.estimatedTotalCop ?: account.balance) to (account.estimatedTotalCop != null)

/** USD→COP rate the server applied, derived from the estimate. Null when not applicable. */
fun impliedTrm(account: Account): Long? {
    val est = account.estimatedTotalCop ?: return null
    val usd = account.balancesByCurrency["USD"] ?: return null
    if (usd == 0L) return null
    val cop = account.balancesByCurrency["COP"] ?: 0L
    return (est - cop) / usd
}

/** True when the account holds any non-zero balance in a currency other than COP. */
fun hasForeignBalance(account: Account): Boolean =
    account.balancesByCurrency.any { (cur, amt) -> cur != "COP" && amt != 0L }

/** Per-currency balance lines + the implied TRM, for the account-detail hero card. */
@Composable
fun CurrencyBreakdown(account: Account) {
    val cop = account.balancesByCurrency["COP"] ?: 0L
    val usd = account.balancesByCurrency["USD"] ?: 0L
    val trm = impliedTrm(account)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cop != 0L) BreakdownRow("En pesos", formatMoney(cop, "COP"))
        if (usd != 0L) BreakdownRow("En dólares", formatMoney(usd, "USD"))
        if (trm != null) BreakdownRow("TRM aplicada", "≈$" + groupThousands(trm))
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 12.sp, color = MinTextMute)
        MonoText(text = value, fontSize = 12f, color = MinTextMute)
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MoneyDisplay.kt
git commit -m "feat(ui): money display helpers + currency breakdown composable"
```

---

### Task 2: AccountsScreen — net worth card + red debt rows

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt`

- [ ] **Step 1: Replace the "TOTAL ACTIVOS" card**

The current card (inside `item {}` under `// Total assets card`, ~lines 152-176) is:

```kotlin
                    item {
                        val totalAssets = accounts.sumOf { it.balance }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(20.dp),
                        ) {
                            Text(
                                text = "TOTAL ACTIVOS",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            MonoText(
                                text = formatCOP(totalAssets),
                                fontSize = 28f,
                                color = MinIncome,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }
```

Replace it with:

```kotlin
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
                                text = "${if (neto < 0) "−" else ""}${formatCOP(neto)}",
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
```

(`assetsDebtsNet` is already imported via the existing `com.jvillada.movi.ui.components.*` wildcard import.)

- [ ] **Step 2: Make card rows show debt in red**

The account row `right` slot (~lines 208-214) is:

```kotlin
                                    right = {
                                        MonoText(
                                            text = formatCOP(account.balance),
                                            fontSize = 14.5f,
                                            color = MinIncome,
                                        )
                                    },
```

Replace with:

```kotlin
                                    right = {
                                        if (account.type == AccountType.CREDIT_CARD) {
                                            val (debt, isEstimate) = cardDebt(account)
                                            MonoText(
                                                text = "−${if (isEstimate) "≈" else ""}${formatCOP(debt)}",
                                                fontSize = 14.5f,
                                                color = MinExpense,
                                            )
                                        } else {
                                            MonoText(
                                                text = formatCOP(account.balance),
                                                fontSize = 14.5f,
                                                color = MinIncome,
                                            )
                                        }
                                    },
```

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt
git commit -m "feat(ui): net-worth summary card and red debt rows in accounts list"
```

---

### Task 3: AccountDetailScreen — debt hero, breakdown, currency-aware amounts

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt`

- [ ] **Step 1: COP-only day totals (client-side grouping)**

In the `LaunchedEffect` grouping (~lines 50-60), the total currently is:

```kotlin
                        total = items.sumOf {
                            if (it.type == TransactionType.INCOME) it.amount else -it.amount
                        },
```

Replace with (server `EventDay.total` semantics — COP only):

```kotlin
                        total = items.filter { it.currency == "COP" }.sumOf {
                            if (it.type == TransactionType.INCOME) it.amount else -it.amount
                        },
```

- [ ] **Step 2: Hero card — debt variant + breakdown**

The current hero card body (~lines 133-163) is:

```kotlin
                    item(key = "balance-card") {
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
                                text = "${if (acc.balance < 0) "−" else ""}${formatCOP(acc.balance)}",
                                fontSize = 28f,
                                color = if (acc.balance >= 0) MinIncome else MinExpense,
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
```

Replace with:

```kotlin
                    item(key = "balance-card") {
                        val isCard = acc.type == AccountType.CREDIT_CARD
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(20.dp),
                        ) {
                            Text(
                                text = if (isCard) "DEUDA ACTUAL" else "SALDO ACTUAL",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (isCard) {
                                val (debt, isEstimate) = cardDebt(acc)
                                MonoText(
                                    text = "${if (isEstimate) "≈" else ""}${formatCOP(debt)}",
                                    fontSize = 28f,
                                    color = MinExpense,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                MonoText(
                                    text = "${if (acc.balance < 0) "−" else ""}${formatCOP(acc.balance)}",
                                    fontSize = 28f,
                                    color = if (acc.balance >= 0) MinIncome else MinExpense,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (typeLabel.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isCard) typeLabel else "COP · $typeLabel",
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                )
                            }
                            if (isCard || hasForeignBalance(acc)) {
                                Spacer(Modifier.height(12.dp))
                                CurrencyBreakdown(acc)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
```

- [ ] **Step 3: Currency-aware event amounts**

The event row amount (~line 256) is:

```kotlin
                                            Text(
                                                text = "${if (isIncome) "+" else "−"}${formatCOP(event.amount)}",
```

Replace `formatCOP(event.amount)` with `formatMoney(event.amount, event.currency)`:

```kotlin
                                            Text(
                                                text = "${if (isIncome) "+" else "−"}${formatMoney(event.amount, event.currency)}",
```

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountDetailScreen.kt
git commit -m "feat(ui): credit-card debt hero with currency breakdown; currency-aware event amounts"
```

---

### Task 4: DashboardScreen — net balance + red debt rows

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Net total**

Line ~68 currently:

```kotlin
    val totalBalance = accounts.sumOf { it.balance }
```

Replace with:

```kotlin
    val (_, _, totalBalance) = assetsDebtsNet(accounts)
```

And the hero text (~line 155) `text = formatCOP(totalBalance),` becomes sign-aware:

```kotlin
                            text = "${if (totalBalance < 0) "−" else ""}${formatCOP(totalBalance)}",
```

(The `Sparkline` `hasData` check at ~line 167 uses `totalBalance != 0L` — leave as is; it works with the net.)

- [ ] **Step 2: Red debt rows in the accounts preview**

The row (~line 247):

```kotlin
                                        right = { MonoText(formatCOP(account.balance), 14.5f) },
```

Replace with:

```kotlin
                                        right = {
                                            if (account.type == com.jvillada.movi.shared.model.AccountType.CREDIT_CARD) {
                                                val (debt, isEstimate) = cardDebt(account)
                                                MonoText("−${if (isEstimate) "≈" else ""}${formatCOP(debt)}", 14.5f, color = MinExpense)
                                            } else {
                                                MonoText(formatCOP(account.balance), 14.5f)
                                            }
                                        },
```

(This file already references `AccountType` fully-qualified in the `typeLabel` lambda just above — keep the same style.)

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt
git commit -m "feat(ui): dashboard net balance and red debt rows"
```

---

### Task 5: Visual verification (web app against real local data)

No new code — this proves the feature on screen. The wasm client uses `window.location.origin` as `apiBaseUrl`, so serving the bundle from the local Ktor server (`staticResources` at `server/src/main/resources/static`, already gitignored) wires it to local data automatically.

- [ ] **Step 1: Build the web bundle into the server's static dir**

```bash
./gradlew :webApp:wasmJsBrowserDistribution --console=plain
mkdir -p server/src/main/resources/static
cp -R webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/
```

- [ ] **Step 2: Run the server**

```bash
pkill -f ':server:run' 2>/dev/null; sleep 2
./gradlew :server:run --console=plain > /tmp/movi_server.log 2>&1 &
until curl -s http://localhost:8080/health | grep -q OK; do sleep 2; done; echo up
```

- [ ] **Step 3: Verify with Playwright (browser tools)**

Navigate to `http://localhost:8080`, log in with a test user that owns the imported TC data (register a fresh user + create a CREDIT_CARD account with opening debt and a SAVINGS account with opening balance if the old test users' passwords are unknown — opening-balance events make this trivial now). Screenshot and check:
1. **Mis cuentas**: PATRIMONIO NETO card shows net + Activos/Deudas lines; the card row shows `−≈$…` in red.
2. **Card detail**: "DEUDA ACTUAL ≈$…" in red; "En pesos / En dólares / TRM aplicada" rows (when USD data exists).
3. **Card detail movements**: USD rows show `US$…`; day totals exclude USD amounts.
4. **Dashboard**: Balance = net (assets − debts).

- [ ] **Step 4: Stop the server & clean the static dir**

```bash
pkill -f ':server:run'
rm -rf server/src/main/resources/static
```

(The static dir is build-time-populated by the Dockerfile and gitignored — leaving it would shadow future webApp changes when running locally.)

- [ ] **Step 5: Final check & wrap up**

```bash
git status --short   # expect: clean (screenshots live in /tmp, static dir removed)
git log --oneline master..HEAD
```

Then use superpowers:finishing-a-development-branch (merge/PR decision belongs to the user).

---

## Self-review notes

- **Spec coverage:** A (helpers + CurrencyBreakdown) → Task 1. B (net card + red rows) → Task 2. C (debt hero, breakdown, COP-only day totals, currency-aware amounts) → Task 3. D (dashboard) → Task 4. Verification section → Task 5. Edge cases (COP-only card → no breakdown/≈/TRM; null estimate → balance fallback) are encoded in `cardDebt`/`impliedTrm`/`CurrencyBreakdown` conditionals.
- **Type consistency:** `formatMoney(Long, String)`, `assetsDebtsNet(List<Account>): Triple<Long,Long,Long>`, `cardDebt(Account): Pair<Long,Boolean>`, `impliedTrm(Account): Long?`, `hasForeignBalance(Account): Boolean`, `CurrencyBreakdown(Account)` — used with the same signatures in Tasks 2-4. All reachable via the screens' existing `com.jvillada.movi.ui.components.*` wildcard imports (verified in AccountsScreen/AccountDetailScreen; DashboardScreen also imports `components.*`).
- **No placeholders.** All code blocks complete; quoted "current code" matches the files as of master `a8829d6`.
```
