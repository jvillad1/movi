# Analytics + SMS Migration Design Spec
**Date:** 2026-05-04
**Status:** Approved

---

## Overview

Migrate the remaining screens that still reference the old `Wallet`/`Transaction`/`TransactionDay` domain model to the new `Account`/`FinancialEvent`/`EventDay` model. Delivered in two phases ordered by risk: analytics (pure client-side) first, SMS (client + server) second.

---

## Phase 1 — Analytics screens

### Affected files
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/analisis/AnalisisScreen.kt`
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/budgets/PresupuestosScreen.kt`

### Changes

Both screens have identical mechanical changes:

| Before | After |
|--------|-------|
| `import TransactionDay` | `import EventDay` |
| `var days: List<TransactionDay>` | `var days: List<EventDay>` |
| `getTransactionsByDay()` | `getEventsByDay()` |
| `it.amount.toLong()` | `it.amount` (already `Long` on `FinancialEvent`) |

`TransactionType` is **not removed** — `FinancialEvent.type` is still typed as `TransactionType`, so all filter expressions (`it.type == TransactionType.EXPENSE`) remain valid unchanged.

No server changes. Both screens read data already served by the `getEventsByDay()` path that was wired in Plan 3 (SQLDelight / LocalRepository).

---

## Phase 2 — SMS screens

### Affected files

| File | Change |
|------|--------|
| `composeApp/.../ui/sms/SMSScreens.kt` | Replace `getWallets()` with `getAccounts()`, rewrite `confirm()` |
| `server/.../routes/SmsRoutes.kt` | `/confirm` marks state only, no Transaction creation |
| `shared/.../repository/WalletRepository.kt` | `confirmSms()` return type `Transaction` → `Unit` |
| `shared/.../repository/WalletRepositoryImpl.kt` | Same |
| `shared/.../repository/LocalRepository.kt` | Update delegated `confirmSms()` |

### New SMS confirmation flow

**Before:** `confirm()` → `confirmSms(smsId, category, walletId)` → server creates `Transaction`, returns it to client.

**After:**
1. Client builds `FinancialEvent` from `ParsedSms` + selected `Account`
2. `postEvent(event)` → stored in SQLDelight, synced to server in background
3. `confirmSms(smsId)` → server marks `SmsMessage.state = "confirmed"`, returns `Unit`

### SMSReconcileScreen state changes

| Before | After |
|--------|-------|
| `var wallets: List<Wallet>` | `var accounts: List<Account>` |
| `getWallets()` | `getAccounts()` |
| `resolvedWallet: Wallet?` | `resolvedAccount: Account?` |
| `resolvedWallet?.name` | `resolvedAccount?.name` |
| `canConfirm = parsed != null && resolvedWallet != null` | `canConfirm = parsed != null && resolvedAccount != null` |

### `confirm()` rewrite

```kotlin
fun confirm() {
    val cat = selectedCategory ?: parsed?.category ?: return
    val acct = resolvedAccount ?: return
    val p = parsed ?: return
    working = true; error = null
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

### Server SmsRoutes.kt — `/confirm` endpoint

Replace the current implementation (which creates a `Transaction`) with:

```kotlin
post("/api/sms/{id}/confirm") {
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    Stores.sms.mutate { list ->
        val idx = list.indexOfFirst { it.id == id }
        if (idx != -1) list[idx] = list[idx].copy(state = "confirmed")
    }
    call.respond(HttpStatusCode.OK)
}
```

### WalletRepository interface change

```kotlin
// Before
suspend fun confirmSms(id: String, category: String?, walletId: String?): Transaction

// After
suspend fun confirmSms(id: String): Unit
```

`WalletRepositoryImpl` calls `POST /api/sms/$id/confirm` with no body and ignores the response body. `LocalRepository` delegates to remote unchanged (SMS state lives on the server).

---

## Out of scope

- `RecurrentesScreen.kt` — only imports `TransactionType` which is still valid in the new model; no changes needed.
- SMS BroadcastReceiver / real-time SMS reading — separate sub-project.
- WorkManager background sync — separate sub-project.
- `ParsedSms.amount: Double` field type — kept as-is; `.toLong()` conversion happens at the call site when building `FinancialEvent`.
