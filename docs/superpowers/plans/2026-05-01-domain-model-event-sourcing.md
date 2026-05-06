# Domain Model + Event Sourcing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current Wallet/Transaction CRUD model with spec-aligned Account/FinancialEvent append-only event log.

**Architecture:** `Account` replaces `Wallet` (adds `AccountType`). `FinancialEvent` replaces `Transaction` (amounts as `Long` pesos, adds `reconciliationStatus` and `source`). Server events store is append-only — balance is computed from the log. A `VoidEvent` cancels an event without deletion. `Category` with 15 predefined system entries replaces free-text categories.

**Tech Stack:** Kotlin, kotlinx.serialization, Ktor server (JSON file persistence), Compose Multiplatform UI

**Execute after:** nothing — this is Plan 1, the foundation.
**Required before:** Plan 2 (Auth) and Plan 3 (SQLDelight).

---

## File Map

### New files
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt` — Account + AccountType
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt` — FinancialEvent + VoidEvent + EventSource + ReconciliationStatus
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Category.kt` — Category model + predefined list
- `server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt` — GET/POST accounts
- `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt` — GET/POST events (append-only), GET /by-day, POST void

### Modified files
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Wallet.kt` — delete content, replace with typealias pointing to Account (for migration safety)
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt` — remove old `Credit`/`Goal` fields that duplicate spec types; keep SMS/Chat/Budget models
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt` — add Account + FinancialEvent methods, keep old ones during migration
- `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt` — implement new methods
- `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt` — add accounts + events stores, update seeds
- `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt` — register accountRoutes() + eventRoutes()
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/data/Repositories.kt` — no change (Repositories.wallets still works)
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt` — use FinancialEvent instead of Transaction
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt` — use FinancialEvent, Long amounts
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt` — use Account instead of Wallet
- `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt` — use FinancialEvent after confirm
- `gradle/libs.versions.toml` — no new deps needed

---

## Task 1: Define Account model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt`

- [ ] **Step 1: Create Account.kt**

```kotlin
// shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt
package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // in COP pesos (integer, no decimals)
    val currency: String = "COP",
)
```

- [ ] **Step 2: Build shared module to verify it compiles**

```bash
./gradlew :shared:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt
git commit -m "feat: add Account model with AccountType enum"
```

---

## Task 2: Define FinancialEvent and VoidEvent

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt`

- [ ] **Step 1: Create FinancialEvent.kt**

```kotlin
// shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt
package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventSource { MANUAL, SMS, OCR, STATEMENT }

@Serializable
enum class ReconciliationStatus { UNCONFIRMED, RECONCILED, UNMATCHED }

@Serializable
data class FinancialEvent(
    val id: String,
    val accountId: String,
    val type: TransactionType,          // INCOME | EXPENSE (reuse existing enum)
    val amount: Long,                   // in COP pesos
    val category: String,
    val description: String,
    val merchant: String? = null,
    val timestamp: Long,
    val source: EventSource = EventSource.MANUAL,
    val rawPayload: String? = null,
    val reconciliationStatus: ReconciliationStatus = ReconciliationStatus.UNCONFIRMED,
    val syncedAt: Long? = null,
)

@Serializable
data class VoidEvent(
    val id: String,
    val originalEventId: String,
    val reason: String? = null,
    val timestamp: Long,
)

// Day-grouped view (replaces TransactionDay)
@Serializable
data class EventDay(
    val date: String,
    val total: Long,
    val items: List<FinancialEvent>,
)
```

- [ ] **Step 2: Build shared to verify**

```bash
./gradlew :shared:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt
git commit -m "feat: add FinancialEvent, VoidEvent, EventDay models"
```

---

## Task 3: Define Category with predefined list

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Category.kt`

- [ ] **Step 1: Create Category.kt**

```kotlin
// shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Category.kt
package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryScope { PREDEFINED, CUSTOM }

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,       // "INCOME" | "EXPENSE" | "BOTH"
    val scope: CategoryScope = CategoryScope.PREDEFINED,
)

val PREDEFINED_CATEGORIES: List<Category> = listOf(
    // Expenses
    Category("cat_food",       "Comida",          "🍔", "#FF6B35", "EXPENSE"),
    Category("cat_transport",  "Transporte",      "🚗", "#4ECDC4", "EXPENSE"),
    Category("cat_health",     "Salud",           "💊", "#45B7D1", "EXPENSE"),
    Category("cat_education",  "Educación",       "📚", "#96CEB4", "EXPENSE"),
    Category("cat_entertain",  "Entretenimiento", "🎮", "#DDA0DD", "EXPENSE"),
    Category("cat_services",   "Servicios",       "#1E90FF","💡",   "EXPENSE"),
    Category("cat_housing",    "Vivienda",        "🏠", "#F0E68C", "EXPENSE"),
    Category("cat_clothing",   "Ropa",            "👗", "#FFB6C1", "EXPENSE"),
    Category("cat_tech",       "Tecnología",      "💻", "#87CEEB", "EXPENSE"),
    Category("cat_other_exp",  "Otros",           "📦", "#D3D3D3", "EXPENSE"),
    // Incomes
    Category("cat_salary",     "Salario",         "💼", "#90EE90", "INCOME"),
    Category("cat_freelance",  "Freelance",       "🖥️", "#98FB98", "INCOME"),
    Category("cat_rent",       "Arriendo recibido","🏘️","#8FBC8F", "INCOME"),
    Category("cat_invest_inc", "Inversiones",     "📈", "#3CB371", "INCOME"),
    Category("cat_other_inc",  "Otros ingresos",  "💰", "#2E8B57", "INCOME"),
)
```

- [ ] **Step 2: Build shared to verify**

```bash
./gradlew :shared:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Category.kt
git commit -m "feat: add Category model with 15 predefined categories"
```

---

## Task 4: Update server storage with Account + FinancialEvent stores

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt`

- [ ] **Step 1: Add Account and FinancialEvent stores to Stores.kt**

Replace the content of `Stores.kt` with:

```kotlin
package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.*
import java.io.File

private val DATA_DIR = File("movi-data")

// ─── Account seeds ───────────────────────────────────────────────────────────
private val accountSeed = listOf(
    Account("acc_1", "Efectivo",             AccountType.CASH,     580_000L),
    Account("acc_2", "Bancolombia Ahorros",  AccountType.CHECKING, 1_260_000L),
)

// ─── FinancialEvent seeds ─────────────────────────────────────────────────────
private val eventSeed = listOf(
    FinancialEvent("e1", "acc_2", TransactionType.EXPENSE, 42_300L,    "Comida",      "Crepes & Waffles",  "Crepes & Waffles",  1_745_870_400_000L, EventSource.SMS),
    FinancialEvent("e2", "acc_2", TransactionType.EXPENSE, 28_500L,    "Transporte",  "Uber",              "Uber",              1_745_866_800_000L, EventSource.SMS,    reconciliationStatus = ReconciliationStatus.UNCONFIRMED),
    FinancialEvent("e3", "acc_2", TransactionType.EXPENSE, 312_400L,   "Comida",      "Éxito Country",     "Éxito Country",     1_745_784_000_000L, EventSource.OCR),
    FinancialEvent("e4", "acc_2", TransactionType.INCOME,  80_000L,    "Otros ingresos","Daviplata",        null,                1_745_780_400_000L, EventSource.SMS),
    FinancialEvent("e5", "acc_2", TransactionType.INCOME,  4_500_000L, "Salario",     "Globant",           null,                1_745_697_600_000L, EventSource.SMS,    reconciliationStatus = ReconciliationStatus.RECONCILED),
    FinancialEvent("e6", "acc_2", TransactionType.EXPENSE, 28_900L,    "Tecnología",  "Netflix",           "Netflix",           1_745_694_000_000L, EventSource.MANUAL),
    FinancialEvent("e7", "acc_2", TransactionType.EXPENSE, 47_200L,    "Salud",       "Drogas La Rebaja",  "La Rebaja",         1_745_690_400_000L, EventSource.OCR,    reconciliationStatus = ReconciliationStatus.UNCONFIRMED),
)

// ─── Legacy seeds (unchanged) ─────────────────────────────────────────────────
private val walletSeed = listOf(
    Wallet("1", "Efectivo", 580_000.0, "COP"),
    Wallet("2", "Bancolombia Ahorros", 1_260_000.0, "COP"),
)

private val transactionSeed = listOf(
    Transaction("t1", "2", "Crepes & Waffles", 42_300.0, "Restaurantes", TransactionType.EXPENSE, TransactionSource.SMS, false, 1_745_870_400_000L),
    Transaction("t2", "2", "Uber", 28_500.0, "Transporte", TransactionType.EXPENSE, TransactionSource.SMS, true, 1_745_866_800_000L),
    Transaction("t3", "2", "Éxito Country", 312_400.0, "Mercado", TransactionType.EXPENSE, TransactionSource.OCR, false, 1_745_784_000_000L),
    Transaction("t4", "2", "Daviplata", 80_000.0, "Transferencia", TransactionType.INCOME, TransactionSource.SMS, false, 1_745_780_400_000L),
    Transaction("t5", "2", "Globant", 4_500_000.0, "Nómina", TransactionType.INCOME, TransactionSource.SMS, false, 1_745_697_600_000L),
    Transaction("t6", "2", "Netflix", 28_900.0, "Suscripción", TransactionType.EXPENSE, TransactionSource.MANUAL, false, 1_745_694_000_000L),
    Transaction("t7", "2", "Drogas La Rebaja", 47_200.0, "Salud", TransactionType.EXPENSE, TransactionSource.OCR, true, 1_745_690_400_000L),
)

private val creditSeed = listOf(
    Credit("Crédito de vivienda", "Bancolombia", 240_000_000, 86_400_000, "11,2% E.A.", "30 abr", "\$1.860.000"),
    Credit("Tarjeta Falabella", "CMR", 4_320_000, 2_680_000, "24,5% E.A.", "5 may", "\$580.000"),
    Credit("Libre inversión", "Davivienda", 12_000_000, 7_200_000, "18,9% E.A.", "15 may", "\$420.000"),
)

private val goalSeed = listOf(
    Goal("Viaje a Cartagena", 5_000_000, 3_400_000, "Junio 2026", 320_000),
    Goal("Cuota inicial apto", 30_000_000, 8_600_000, "Diciembre 2027", 1_200_000),
    Goal("Fondo de emergencia", 12_000_000, 12_000_000, "Completado", 0),
    Goal("Cumpleaños Mateo", 800_000, 220_000, "Agosto 2026", 145_000),
)

private val recurringSeed = listOf(
    RecurringRule("r1", "Salario Globant",      "Nómina",       4_500_000, 25, TransactionType.INCOME),
    RecurringRule("r2", "Netflix",              "Suscripción",  28_900,    1,  TransactionType.EXPENSE),
    RecurringRule("r3", "Arriendo apartamento", "Servicios",    1_500_000, 5,  TransactionType.EXPENSE),
    RecurringRule("r4", "Internet Claro",       "Servicios",    89_000,    10, TransactionType.EXPENSE),
    RecurringRule("r5", "Spotify Family",       "Suscripción",  19_900,    15, TransactionType.EXPENSE),
)

private val budgetSeed = listOf(
    Budget("Mercado", 350_000),
    Budget("Salud", 200_000),
    Budget("Restaurantes", 50_000),
    Budget("Suscripción", 35_000),
    Budget("Transporte", 25_000),
)

private val smsSeed = listOf(
    SmsMessage("s1", "hace 2 min", "Bancolombia", "Compra aprobada \$42.300 en Crepes & Waffles el 28/04 a las 13:24.", "pending", "Crepes & Waffles · \$42.300"),
    SmsMessage("s2", "1 h", "Davivienda", "Recibiste \$80.000 de Daviplata.", "pending", "Daviplata · +\$80.000"),
    SmsMessage("s3", "3 h", "Bancolombia", "Compra aprobada \$28.500 en Uber BV.", "auto", "Uber · \$28.500"),
    SmsMessage("s4", "ayer", "Bancolombia", "Nómina recibida \$4.500.000.", "auto", "Globant · +\$4.500.000"),
)

object Stores {
    // New spec-aligned stores
    val accounts     = JsonListStore(File(DATA_DIR, "accounts.json"),     Account.serializer(),        accountSeed)
    val events       = JsonListStore(File(DATA_DIR, "events.json"),       FinancialEvent.serializer(), eventSeed)
    val voidEvents   = JsonListStore(File(DATA_DIR, "void_events.json"),  VoidEvent.serializer(),      emptyList())

    // Legacy stores (kept until UI migration is complete)
    val wallets      = JsonListStore(File(DATA_DIR, "wallets.json"),      Wallet.serializer(),         walletSeed)
    val transactions = JsonListStore(File(DATA_DIR, "transactions.json"), Transaction.serializer(),    transactionSeed)

    // Unchanged stores
    val credits      = JsonListStore(File(DATA_DIR, "credits.json"),      Credit.serializer(),         creditSeed)
    val goals        = JsonListStore(File(DATA_DIR, "goals.json"),        Goal.serializer(),           goalSeed)
    val recurring    = JsonListStore(File(DATA_DIR, "recurring.json"),    RecurringRule.serializer(),  recurringSeed)
    val sms          = JsonListStore(File(DATA_DIR, "sms.json"),          SmsMessage.serializer(),     smsSeed)
    val budgets      = BudgetStorage(File(DATA_DIR, "budgets.json"),      budgetSeed)
}
```

- [ ] **Step 2: Build server to verify**

```bash
./gradlew :server:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt
git commit -m "feat: add Account + FinancialEvent stores to server"
```

---

## Task 5: Add AccountRoutes and EventRoutes (append-only)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt`
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt`

- [ ] **Step 1: Create AccountRoutes.kt**

```kotlin
// server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.Account
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            call.respond(Stores.accounts.snapshot())
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val account = Stores.accounts.snapshot().find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(account)
        }
        post {
            val body = call.receive<Account>()
            val account = if (body.id.isBlank())
                body.copy(id = "acc_${System.currentTimeMillis()}")
            else body
            Stores.accounts.mutate { it.add(account) }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}
```

- [ ] **Step 2: Create EventRoutes.kt**

```kotlin
// server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun signedAmount(e: FinancialEvent): Long =
    if (e.type == TransactionType.EXPENSE) -e.amount else e.amount

private fun dayLabel(ts: Long): String {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return when {
        ts >= now - day -> "Hoy"
        ts >= now - 2 * day -> "Ayer"
        else -> {
            // Format as "DD MMM" — simple epoch-based approach
            val days = (now - ts) / day
            "$days días atrás"
        }
    }
}

fun Route.eventRoutes() {
    route("/api/events") {
        // Append a new financial event (never edit or delete)
        post {
            val body = call.receive<FinancialEvent>()
            val now = System.currentTimeMillis()
            val event = body.copy(
                id = body.id.ifBlank { "ev_$now" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
            )
            Stores.events.mutate { it.add(event) }

            // Update account balance
            Stores.accounts.mutate { accounts ->
                val idx = accounts.indexOfFirst { it.id == event.accountId }
                if (idx != -1) {
                    val acc = accounts[idx]
                    accounts[idx] = acc.copy(balance = acc.balance + signedAmount(event))
                }
            }
            call.respond(HttpStatusCode.Created, event)
        }

        // List all events, optional accountId filter
        get {
            val accountId = call.request.queryParameters["accountId"]
            val all = Stores.events.snapshot()
            val voidIds = Stores.voidEvents.snapshot().map { it.originalEventId }.toSet()
            val active = all.filter { it.id !in voidIds }
            val result = if (accountId != null) active.filter { it.accountId == accountId } else active
            call.respond(result.sortedByDescending { it.timestamp })
        }

        // Events grouped by day
        get("/by-day") {
            val voidIds = Stores.voidEvents.snapshot().map { it.originalEventId }.toSet()
            val grouped = Stores.events.snapshot()
                .filter { it.id !in voidIds }
                .sortedByDescending { it.timestamp }
                .groupBy { dayLabel(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        date = date,
                        total = items.sumOf { signedAmount(it) },
                        items = items,
                    )
                }
            call.respond(grouped)
        }

        // Void (cancel) an event — append-only, never delete
        post("/{id}/void") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val exists = Stores.events.snapshot().any { it.id == id }
            if (!exists) return@post call.respond(HttpStatusCode.NotFound)
            val alreadyVoided = Stores.voidEvents.snapshot().any { it.originalEventId == id }
            if (alreadyVoided) return@post call.respond(HttpStatusCode.Conflict, "Already voided")

            val reason = call.request.queryParameters["reason"]
            val void = VoidEvent(
                id = "void_${System.currentTimeMillis()}",
                originalEventId = id,
                reason = reason,
                timestamp = System.currentTimeMillis(),
            )
            Stores.voidEvents.mutate { it.add(void) }

            // Reverse the balance effect
            val event = Stores.events.snapshot().first { it.id == id }
            Stores.accounts.mutate { accounts ->
                val idx = accounts.indexOfFirst { it.id == event.accountId }
                if (idx != -1) {
                    val acc = accounts[idx]
                    accounts[idx] = acc.copy(balance = acc.balance - signedAmount(event))
                }
            }
            call.respond(HttpStatusCode.Created, void)
        }
    }
}
```

- [ ] **Step 3: Register new routes in Routing.kt**

```kotlin
// server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt
package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.routes.*
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        accountRoutes()
        eventRoutes()
        walletRoutes()      // kept for backward compat
        financeRoutes()
        smsRoutes()
        aiRoutes()
    }
}
```

- [ ] **Step 4: Build and smoke-test**

```bash
./gradlew :server:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

Start server and test new endpoints:
```bash
./gradlew :server:run &
sleep 5
curl -s http://localhost:8080/api/accounts | head -3
curl -s http://localhost:8080/api/events/by-day | head -3
```
Expected: JSON arrays for both.

Kill server: `pkill -f "server:run" 2>/dev/null; pkill -f GradleDaemon 2>/dev/null || true`

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt
git commit -m "feat: add AccountRoutes + EventRoutes (append-only) to server"
```

---

## Task 6: Extend WalletRepository interface with Account + Event methods

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`

- [ ] **Step 1: Add new methods to WalletRepository.kt**

Add these imports and methods to the existing interface (keep all existing methods):

```kotlin
// At top of file, add imports:
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.VoidEvent

// Add these methods to the interface body:
    suspend fun getAccounts(): List<Account>
    suspend fun getAccount(id: String): Account
    suspend fun createAccount(account: Account): Account
    suspend fun postEvent(event: FinancialEvent): FinancialEvent
    suspend fun getEvents(accountId: String? = null): List<FinancialEvent>
    suspend fun getEventsByDay(): List<EventDay>
    suspend fun voidEvent(id: String, reason: String? = null): VoidEvent
```

- [ ] **Step 2: Implement new methods in WalletRepositoryImpl.kt**

Add these imports and method implementations (keep all existing methods):

```kotlin
// At top of file, add imports:
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.VoidEvent

// Implementations:
    override suspend fun getAccounts(): List<Account> =
        client.get("$baseUrl/api/accounts").body()

    override suspend fun getAccount(id: String): Account =
        client.get("$baseUrl/api/accounts/$id").body()

    override suspend fun createAccount(account: Account): Account =
        client.post("$baseUrl/api/accounts") {
            contentType(ContentType.Application.Json)
            setBody(account)
        }.body()

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent =
        client.post("$baseUrl/api/events") {
            contentType(ContentType.Application.Json)
            setBody(event)
        }.body()

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> {
        val url = if (accountId != null) "$baseUrl/api/events?accountId=$accountId"
                  else "$baseUrl/api/events"
        return client.get(url).body()
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        client.get("$baseUrl/api/events/by-day").body()

    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val url = if (reason != null) "$baseUrl/api/events/$id/void?reason=$reason"
                  else "$baseUrl/api/events/$id/void"
        return client.post(url).body()
    }
```

- [ ] **Step 3: Build shared to verify**

```bash
./gradlew :shared:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/
git commit -m "feat: add Account + FinancialEvent methods to WalletRepository"
```

---

## Task 7: Update UI — TransactionsScreen to use FinancialEvent + Long amounts

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`

- [ ] **Step 1: Update TransactionsScreen to call getEventsByDay() instead of getTransactionsByDay()**

In `TransactionsScreen.kt`, find the `LaunchedEffect` block that calls `getTransactionsByDay()` and replace it plus update the state variable:

```kotlin
// Replace:
//   var days by remember { mutableStateOf<List<TransactionDay>>(emptyList()) }
//   LaunchedEffect(Unit) {
//       runCatching { Repositories.wallets.getTransactionsByDay() }
//           .onSuccess { days = it }
//   }
// With:
    var days by remember { mutableStateOf<List<com.jvillada.movi.shared.model.EventDay>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess { days = it }
    }
```

Then update the rendering code. Find where `day.total` and `tx.amount` are used and remove the `.toLong()` calls (since EventDay.total and FinancialEvent.amount are already Long):

```kotlin
// Replace:
//   text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total.toLong())}",
// With:
    text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",

// Replace:
//   text = "${if (isIncome) "+" else "−"}${formatCOP(tx.amount.toLong())}",
// With:
    text = "${if (isIncome) "+" else "−"}${formatCOP(tx.amount)}",

// Replace (the variable name if it was tx: Transaction):
//   val isIncome = tx.type == TransactionType.INCOME
// (TransactionType is still the same enum, no change needed)
```

Also update the import for `TransactionDay` → `EventDay` and `Transaction` → `FinancialEvent`:
```kotlin
// Replace in imports:
// import com.jvillada.movi.shared.model.Transaction
// import com.jvillada.movi.shared.model.TransactionDay
// With:
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
```

- [ ] **Step 2: Build composeApp to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt
git commit -m "feat: TransactionsScreen uses FinancialEvent + EventDay"
```

---

## Task 8: Update UI — QuickAddScreen to post FinancialEvent

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt`

- [ ] **Step 1: Replace Transaction usage with FinancialEvent in QuickAddScreen**

In `QuickAddScreen.kt`:

1. Add import, remove old ones:
```kotlin
// Remove:
// import com.jvillada.movi.shared.model.Transaction
// import com.jvillada.movi.shared.model.TransactionSource
// Add:
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
```

2. Change wallets state to accounts:
```kotlin
// Replace:
// var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
// var selectedWalletId by remember { mutableStateOf<String?>(null) }
// LaunchedEffect(Unit) {
//     runCatching { Repositories.wallets.getWallets() }
//         .onSuccess { list ->
//             wallets = list
//             if (selectedWalletId == null) selectedWalletId = list.firstOrNull()?.id
//         }
// }
// With:
var accounts by remember { mutableStateOf<List<com.jvillada.movi.shared.model.Account>>(emptyList()) }
var selectedAccountId by remember { mutableStateOf<String?>(null) }
LaunchedEffect(Unit) {
    runCatching { Repositories.wallets.getAccounts() }
        .onSuccess { list ->
            accounts = list
            if (selectedAccountId == null) selectedAccountId = list.firstOrNull()?.id
        }
}
```

3. Update the save block (find where `addTransaction` is called):
```kotlin
// Replace the coroutine save block:
// val tx = Transaction(
//     id = "", walletId = selectedWalletId ?: "1",
//     name = note, amount = amount.toDoubleOrNull() ?: 0.0,
//     category = category, type = txType,
//     source = TransactionSource.MANUAL, pending = false,
//     timestamp = System.currentTimeMillis(),
// )
// Repositories.wallets.addTransaction(tx)

// With:
val event = FinancialEvent(
    id = "",
    accountId = selectedAccountId ?: accounts.firstOrNull()?.id ?: "acc_1",
    type = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
    amount = amount.toLongOrNull() ?: 0L,
    category = category,
    description = note.ifBlank { category },
    source = EventSource.MANUAL,
    timestamp = System.currentTimeMillis(),
)
Repositories.wallets.postEvent(event)
```

4. Update all references to `wallets` list in the wallet picker UI to use `accounts` and `selectedAccountId`.

- [ ] **Step 2: Build to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Install and smoke-test on emulator**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

Open the app, tap the center FAB, enter an amount and category, tap Guardar. Verify the transaction appears in Movimientos.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/quickadd/QuickAddScreen.kt
git commit -m "feat: QuickAdd posts FinancialEvent instead of Transaction"
```

---

## Task 9: Update DashboardScreen to use Account balances

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Replace getWallets() with getAccounts() in DashboardScreen**

In `DashboardScreen.kt`, find the wallet-loading code:
```kotlin
// Replace:
// var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
// LaunchedEffect(Unit) {
//     runCatching { Repositories.wallets.getWallets() }
//         .onSuccess { wallets = it }
// }
// With:
var accounts by remember { mutableStateOf<List<com.jvillada.movi.shared.model.Account>>(emptyList()) }
LaunchedEffect(Unit) {
    runCatching { Repositories.wallets.getAccounts() }
        .onSuccess { accounts = it }
}
```

Update balance computation (find where wallet balances are summed):
```kotlin
// Replace:
// val totalBalance = wallets.sumOf { it.balance }.toLong()
// With:
val totalBalance = accounts.sumOf { it.balance }
```

Update any wallet name/list rendering to use `accounts` and `Account` fields (`account.balance` is already `Long`, `account.name` and `account.type` work the same way).

- [ ] **Step 2: Build to verify**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt
git commit -m "feat: DashboardScreen uses Account model"
```

---

## Task 10: Full build + install + verify

- [ ] **Step 1: Full project build**

```bash
./gradlew build 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install on emulator**

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

- [ ] **Step 3: Smoke test**
- Dashboard loads with balances from `getAccounts()`
- Movimientos tab loads from `getEventsByDay()`
- FAB → QuickAdd → saves FinancialEvent → appears in Movimientos

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: domain model aligned with spec — Account + FinancialEvent + VoidEvent"
```

---

## Self-Review

**Spec coverage:**
- ✅ `Account` with `AccountType` enum
- ✅ `FinancialEvent` with `source`, `reconciliationStatus`, `rawPayload`, amounts as `Long`
- ✅ `VoidEvent` (append-only cancellation)
- ✅ `Category` with 15 predefined entries
- ✅ Server events are append-only (no update/delete endpoints)
- ✅ Balance computed from event log on `POST /api/events`
- ⏭️ `User` model — deferred to Plan 2 (Auth) where it's the main concern
- ⏭️ `FinancialProfile` — deferred to after auth (requires userId)
- ⏭️ `Family` model — deferred to a later sub-project

**Legacy kept intentionally:**
- `Wallet` / `Transaction` / `TransactionType` / `TransactionSource` types remain in the codebase. The SMS and OCR flows still reference them. Full cleanup happens after those screens are migrated to FinancialEvent (can be done incrementally).
