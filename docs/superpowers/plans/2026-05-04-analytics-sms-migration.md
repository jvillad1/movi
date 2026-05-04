# Analytics + SMS Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `AnalisisScreen`, `PresupuestosScreen`, and `SMSScreens` from the old `Wallet`/`Transaction`/`TransactionDay` model to the new `Account`/`FinancialEvent`/`EventDay` model.

**Architecture:** Two phases. Phase 1 (Tasks 1–2) is pure client-side: swap `getTransactionsByDay()` for `getEventsByDay()` in the two analytics screens — no server changes, no interface changes. Phase 2 (Tasks 3–5) updates the SMS confirmation flow so the client creates the `FinancialEvent` locally via `postEvent()` and the server only marks the SMS as processed.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor client/server, SQLDelight (LocalRepository), `kotlinx.serialization`

---

## File Map

| Action | File |
|--------|------|
| Modify | `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/analisis/AnalisisScreen.kt` |
| Modify | `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/budgets/PresupuestosScreen.kt` |
| Modify | `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt` |
| Modify | `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt` |
| Modify | `shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt` |
| Modify | `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt` |
| Modify | `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt` |
| Modify | `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt` |

---

## Task 1: Migrate AnalisisScreen to EventDay

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/analisis/AnalisisScreen.kt`

- [ ] **Step 1: Replace TransactionDay import with EventDay**

In `AnalisisScreen.kt`, find and replace the import:

```kotlin
// Remove:
import com.jvillada.movi.shared.model.TransactionDay

// Add:
import com.jvillada.movi.shared.model.EventDay
```

- [ ] **Step 2: Update state variable type**

```kotlin
// Replace:
var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }

// With:
var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
```

- [ ] **Step 3: Update API call**

```kotlin
// Replace:
runCatching { Repositories.wallets.getTransactionsByDay() }.onSuccess { days = it }

// With:
runCatching { Repositories.wallets.getEventsByDay() }.onSuccess { days = it }
```

- [ ] **Step 4: Remove `.toLong()` call on amount**

In the `byCategory` remember block, `FinancialEvent.amount` is already `Long` — drop the conversion:

```kotlin
// Replace:
.map { (cat, txs) -> CategoryTotal(cat, txs.sumOf { it.amount.toLong() }) }

// With:
.map { (cat, txs) -> CategoryTotal(cat, txs.sumOf { it.amount }) }
```

- [ ] **Step 5: Build to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/analisis/AnalisisScreen.kt
git commit -m "feat: AnalisisScreen — switch to EventDay + getEventsByDay()"
```

---

## Task 2: Migrate PresupuestosScreen to EventDay

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/budgets/PresupuestosScreen.kt`

- [ ] **Step 1: Replace TransactionDay import with EventDay**

```kotlin
// Remove:
import com.jvillada.movi.shared.model.TransactionDay

// Add:
import com.jvillada.movi.shared.model.EventDay
```

- [ ] **Step 2: Update state variable type**

```kotlin
// Replace:
var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }

// With:
var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
```

- [ ] **Step 3: Update API call**

```kotlin
// Replace:
runCatching { Repositories.wallets.getTransactionsByDay() }.onSuccess { days = it }

// With:
runCatching { Repositories.wallets.getEventsByDay() }.onSuccess { days = it }
```

- [ ] **Step 4: Remove `.toLong()` call on amount**

In the `progresses` remember block:

```kotlin
// Replace:
.mapValues { (_, txs) -> txs.sumOf { it.amount.toLong() } }

// With:
.mapValues { (_, txs) -> txs.sumOf { it.amount } }
```

- [ ] **Step 5: Build to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/budgets/PresupuestosScreen.kt
git commit -m "feat: PresupuestosScreen — switch to EventDay + getEventsByDay()"
```

---

## Task 3: Update WalletRepository.confirmSms() signature

The old signature returned `Transaction` (the server created the event). The new one returns `Unit` (the client creates the event via `postEvent()` and the server only marks state).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- Modify: `shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`
- Modify: `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`

- [ ] **Step 1: Update interface in WalletRepository.kt**

```kotlin
// Replace:
suspend fun confirmSms(id: String, category: String? = null, walletId: String? = null): Transaction

// With:
suspend fun confirmSms(id: String)
```

Also remove the `Transaction` import if it's only used for this method (check that no other method returns `Transaction` first).

- [ ] **Step 2: Update implementation in WalletRepositoryImpl.kt**

```kotlin
// Replace:
override suspend fun confirmSms(id: String, category: String?, walletId: String?): Transaction =
    client.post("$baseUrl/api/sms/$id/confirm") {
        if (category != null) url.parameters.append("category", category)
        if (walletId != null) url.parameters.append("walletId", walletId)
    }.body()

// With:
override suspend fun confirmSms(id: String) {
    client.post("$baseUrl/api/sms/$id/confirm")
}
```

- [ ] **Step 3: Update delegation in LocalRepository.kt**

On line 135:

```kotlin
// Replace:
override suspend fun confirmSms(id: String, category: String?, walletId: String?): Transaction = remote.confirmSms(id, category, walletId)

// With:
override suspend fun confirmSms(id: String) = remote.confirmSms(id)
```

- [ ] **Step 4: Update NoOpRepository.kt**

On line 17:

```kotlin
// Replace:
override suspend fun confirmSms(id: String, category: String?, walletId: String?) = error("stub")

// With:
override suspend fun confirmSms(id: String) {}
```

- [ ] **Step 5: Build shared module and run tests**

```bash
./gradlew :shared:jvmTest 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL` — 3 tests completed, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt \
        shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt \
        shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt \
        shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt
git commit -m "refactor: confirmSms returns Unit — event creation moved to client"
```

---

## Task 4: Update server SMS confirm endpoint

The endpoint no longer creates a `Transaction`. It just marks the `SmsMessage.state` as `"confirmed"` and returns `200 OK`.

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt`

- [ ] **Step 1: Remove unused imports**

At the top of `SmsRoutes.kt`, remove these three imports (no longer needed after rewriting the confirm endpoint):

```kotlin
// Remove:
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionSource
```

`TransactionType` is still used by `parseSms()` — keep it.

- [ ] **Step 2: Remove walletIdForBank() helper**

Delete the entire `walletIdForBank()` function (lines ~57–62):

```kotlin
// Delete this function entirely:
private suspend fun walletIdForBank(bank: String): String? {
    val list = Stores.wallets.snapshot()
    return list.firstOrNull { it.name.contains(bank, ignoreCase = true) }?.id
        ?: list.firstOrNull { it.id != "1" }?.id
        ?: list.firstOrNull()?.id
}
```

- [ ] **Step 3: Rewrite the POST /confirm endpoint**

Replace the entire `post("/api/sms/{id}/confirm")` block with:

```kotlin
post("/api/sms/{id}/confirm") {
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val exists = Stores.sms.snapshot().any { it.id == id }
    if (!exists) return@post call.respond(HttpStatusCode.NotFound)
    Stores.sms.mutate { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i != -1) list[i] = list[i].copy(state = "confirmed")
    }
    call.respond(HttpStatusCode.OK)
}
```

- [ ] **Step 4: Build server to verify**

```bash
./gradlew :server:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Smoke-test the endpoint**

In one terminal, start the server:
```bash
./gradlew :server:run &
sleep 5
```

Then test (requires a user token from a prior login — skip if no local server setup):
```bash
curl -s -X POST http://localhost:8080/api/sms/s1/confirm \
  -H "Authorization: Bearer <TOKEN>"
# Expected: 200 OK

curl -s http://localhost:8080/api/sms/s1 \
  -H "Authorization: Bearer <TOKEN>" | python3 -m json.tool
# Expected: "state": "confirmed"

pkill -f "server:run" 2>/dev/null || true
```

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt
git commit -m "refactor: SMS confirm endpoint marks state only — no Transaction creation"
```

---

## Task 5: Update SMSReconcileScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt`

- [ ] **Step 1: Update imports**

```kotlin
// Remove:
import com.jvillada.movi.shared.model.Wallet

// Add:
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
```

- [ ] **Step 2: Replace wallets state with accounts**

In `SMSReconcileScreen`, change the state declaration:

```kotlin
// Replace:
var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }

// With:
var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
```

- [ ] **Step 3: Replace getWallets() call with getAccounts()**

In the `LaunchedEffect(smsId)` block:

```kotlin
// Replace:
runCatching { Repositories.wallets.getWallets() }.onSuccess { wallets = it }

// With:
runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it }
```

- [ ] **Step 4: Replace resolvedWallet with resolvedAccount**

```kotlin
// Replace:
val resolvedWallet = wallets.firstOrNull { sms != null && it.name.contains(sms!!.bank, ignoreCase = true) }
    ?: wallets.firstOrNull { it.id != "1" }
    ?: wallets.firstOrNull()

// With:
val resolvedAccount = accounts.firstOrNull { sms != null && it.name.contains(sms!!.bank, ignoreCase = true) }
    ?: accounts.firstOrNull { it.type != AccountType.CASH }
    ?: accounts.firstOrNull()
```

- [ ] **Step 5: Update canConfirm condition**

```kotlin
// Replace:
val canConfirm = parsed != null && resolvedWallet != null && !working

// With:
val canConfirm = parsed != null && resolvedAccount != null && !working
```

- [ ] **Step 6: Update display references**

There are two places that show `resolvedWallet?.name`:

```kotlin
// Replace (in the "Movi sugiere" card):
"${selectedCategory ?: p.category} · ${resolvedWallet?.name ?: "Sin cuenta"}",

// With:
"${selectedCategory ?: p.category} · ${resolvedAccount?.name ?: "Sin cuenta"}",

// Replace (in the Detail row for "Cuenta"):
Detail(
    ok = resolvedWallet != null,
    label = "Cuenta",
    value = resolvedWallet?.name ?: "Sin cuenta",
)

// With:
Detail(
    ok = resolvedAccount != null,
    label = "Cuenta",
    value = resolvedAccount?.name ?: "Sin cuenta",
)
```

- [ ] **Step 7: Rewrite confirm() function**

Replace the entire `fun confirm()` block:

```kotlin
fun confirm() {
    val cat = selectedCategory ?: parsed?.category ?: return
    val acct = resolvedAccount ?: return
    val p = parsed ?: return
    working = true
    error = null
    coroutine.launch {
        runCatching {
            val event = FinancialEvent(
                id = "",
                accountId = acct.id,
                type = p.type,
                amount = p.amount.toLong(),
                category = cat,
                description = p.merchant,
                merchant = p.merchant,
                source = EventSource.SMS,
                timestamp = System.currentTimeMillis(),
            )
            Repositories.wallets.postEvent(event)
            Repositories.wallets.confirmSms(smsId)
        }.onSuccess {
            working = false
            onNavigate(Screen.SMSInbox)
        }.onFailure {
            working = false
            error = "No pude confirmar: ${it.message ?: "error"}"
        }
    }
}
```

- [ ] **Step 8: Build to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt
git commit -m "feat: SMSReconcileScreen — confirm creates FinancialEvent locally via postEvent"
```

---

## Task 6: Full build + install + smoke test

- [ ] **Step 1: Full project build (Android target)**

```bash
./gradlew :composeApp:assembleDebug :server:build 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Boot emulator and start server**

```bash
/Users/jvillada/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro -no-snapshot-load > /tmp/emulator.log 2>&1 &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done && echo "booted"

# Start Docker (PostgreSQL) + server
docker compose up -d
./gradlew :server:run > /tmp/server.log 2>&1 &
sleep 8 && curl -s http://localhost:8080/health
# Expected: OK
```

- [ ] **Step 3: Install and launch**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

- [ ] **Step 4: Smoke test — Analytics**

1. Login → tap nav tab **Análisis**
2. Verify "Egresos del mes" shows a non-zero total from real FinancialEvents
3. Verify the category breakdown renders rows with correct amounts
4. Tap **Presupuestos** → verify "Gastado del mes" reflects the same expense data

- [ ] **Step 5: Smoke test — SMS confirm**

1. Tap **Inicio** → scroll to any SMS section or navigate to SMS inbox
2. Tap "Revisar" on a pending SMS
3. Verify the account name shows (not "Sin cuenta")
4. Select a category chip, tap **Confirmar**
5. Verify: returns to SMS inbox AND the confirmed item no longer shows "PENDIENTE"
6. Navigate to **Movimientos** → verify the new event appears in the list with correct amount and category

- [ ] **Step 6: Stop emulator and server**

```bash
pkill -f "server:run" 2>/dev/null || true
docker compose stop
adb emu kill
```
