# Multi-currency credit-card debt + TRM estimate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track credit-card debt per native currency (COP + USD), derive account balances from events, and surface an estimated total COP debt using the official TRM.

**Architecture:** Transactions store a native `currency` (no conversion at import). Account balances stop being a stored running total and become **derived** by aggregating non-voided events per currency, applying an account-type-aware sign (`CREDIT_CARD` inverts so purchases raise debt, payments lower it). A `FxRateService` fetches the daily TRM from datos.gov.co (cached, with fallback) to compute `estimatedTotalCop = copDebt + usdDebt × TRM`.

**Tech Stack:** Kotlin Multiplatform, Ktor server, Exposed (Postgres), kotlinx.serialization, JDK `java.net.http.HttpClient` (no new dependency), JUnit/kotlin.test.

**Spec:** `docs/superpowers/specs/2026-06-09-multicurrency-credit-card-debt-design.md`

**Pre-req:** Local Postgres running (`brew services start postgresql@16`; db/user/pass = movi/movi/secret). The server reads `server/.env` (has `ANTHROPIC_API_KEY`, `DATABASE_URL`, `JWT_SECRET`).

**Testing note:** The project has **no test database harness**, so DB/route tasks (B, F, G) are verified by the end-to-end harness in **Task H** rather than unit tests. Pure-logic tasks (A, C, D, E) use real unit tests (TDD).

---

### Task A: Currency on core models

**Files:**
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt`
- Test: `core/src/jvmTest/kotlin/com/jvillada/movi/shared/model/MultiCurrencyModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/com/jvillada/movi/shared/model/MultiCurrencyModelTest.kt`:

```kotlin
package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultiCurrencyModelTest {

    @Test
    fun `ParsedTransaction defaults currency to COP`() {
        val tx = ParsedTransaction(
            id = "1", date = "2026-05-01", merchant = "X", amount = 100,
            type = TransactionType.EXPENSE, category = "Otros", description = "", rawText = "",
        )
        assertEquals("COP", tx.currency)
    }

    @Test
    fun `FinancialEvent keeps explicit currency through JSON round-trip`() {
        val ev = FinancialEvent(
            id = "ev1", accountId = "acc1", type = TransactionType.EXPENSE, amount = 100,
            category = "Tecnología", description = "Claude", timestamp = 0L, currency = "USD",
        )
        val json = Json.encodeToString(FinancialEvent.serializer(), ev)
        val back = Json.decodeFromString(FinancialEvent.serializer(), json)
        assertEquals("USD", back.currency)
    }

    @Test
    fun `Account defaults multi-currency fields to empty`() {
        val acc = Account(id = "a", name = "n", type = AccountType.SAVINGS, balance = 0)
        assertEquals(emptyMap(), acc.balancesByCurrency)
        assertNull(acc.estimatedTotalCop)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests "com.jvillada.movi.shared.model.MultiCurrencyModelTest"`
Expected: FAIL to compile — `currency`, `balancesByCurrency`, `estimatedTotalCop` unresolved.

- [ ] **Step 3: Add `currency` to `FinancialEvent`**

In `FinancialEvent.kt`, the `FinancialEvent` data class — add `currency` right after `amount`:

```kotlin
    val amount: Long,                   // in COP pesos
    val currency: String = "COP",       // native currency of the amount (e.g. "COP", "USD")
    val category: String,
```

- [ ] **Step 4: Add `currency` to `ParsedTransaction`**

In `Statement.kt`, the `ParsedTransaction` data class — add `currency` after `amount`:

```kotlin
    val amount: Long,         // native currency, always positive
    val currency: String = "COP",
    val type: TransactionType,
```

- [ ] **Step 5: Add computed fields to `Account`**

In `Account.kt`, extend the `Account` data class:

```kotlin
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // COP component (derived on read)
    val currency: String = "COP",
    val balancesByCurrency: Map<String, Long> = emptyMap(),  // derived: per-currency balance
    val estimatedTotalCop: Long? = null,                     // derived: COP + foreign × TRM
)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:jvmTest --tests "com.jvillada.movi.shared.model.MultiCurrencyModelTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add core/src/commonMain/kotlin/com/jvillada/movi/shared/model/FinancialEvent.kt \
        core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt \
        core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt \
        core/src/jvmTest/kotlin/com/jvillada/movi/shared/model/MultiCurrencyModelTest.kt
git commit -m "feat(core): add native currency to events/parsed tx + derived balance fields on Account"
```

---

### Task B: DB currency column + shared event mapper

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`
- Create: `server/src/main/kotlin/com/jvillada/movi/server/db/EventMapper.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`

This task DRYs three duplicated `ResultRow → FinancialEvent` mappers into one and persists `currency` everywhere events are written. No unit test (DB-backed); verified in Task H.

- [ ] **Step 1: Add the `currency` column**

In `Tables.kt`, `Events` object — add after `amount`:

```kotlin
    val amount               = long("amount")
    val currency             = varchar("currency", 10).default("COP")
    val category             = varchar("category", 100)
```

(`DatabaseFactory.init` already calls `createMissingTablesAndColumns(Events)`, so existing DBs get the column with default `COP`.)

- [ ] **Step 2: Create the shared mapper**

Create `server/src/main/kotlin/com/jvillada/movi/server/db/EventMapper.kt`:

```kotlin
package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.ResultRow

/** Single source of truth for mapping an [Events] row to a [FinancialEvent]. */
fun ResultRow.toFinancialEvent(): FinancialEvent = FinancialEvent(
    id                   = this[Events.id],
    accountId            = this[Events.accountId],
    type                 = TransactionType.valueOf(this[Events.type]),
    amount               = this[Events.amount],
    currency             = this[Events.currency],
    category             = this[Events.category],
    description          = this[Events.description],
    merchant             = this[Events.merchant],
    timestamp            = this[Events.timestamp],
    source               = EventSource.valueOf(this[Events.eventSource]),
    rawPayload           = this[Events.rawPayload],
    reconciliationStatus = ReconciliationStatus.valueOf(this[Events.reconciliationStatus]),
    syncedAt             = this[Events.syncedAt],
)
```

- [ ] **Step 3: Replace `EventRoutes` private mapper with the shared one**

In `EventRoutes.kt`: delete the private `ResultRow.toEvent()` function (lines ~160–173) and replace every `.toEvent()` call with `.toFinancialEvent()`. Add the import `import com.jvillada.movi.server.db.toFinancialEvent`. Then in the create-event `Events.insert { ... }` block, persist currency — add after `it[amount] = event.amount`:

```kotlin
                    it[amount]               = event.amount
                    it[Events.currency]      = event.currency
                    it[category]             = event.category
```

- [ ] **Step 4: Persist + read currency in `StatementRoutes`**

In `StatementRoutes.kt`:

(a) In `createEventFromParsed`'s `Events.insert { ... }`, add after `it[amount] = tx.amount`:

```kotlin
            it[amount]               = tx.amount
            it[Events.currency]      = tx.currency
            it[category]             = tx.category
```

(b) The two inline `FinancialEvent(...)` constructions (the `existing` query ~lines 90-105 and the `imports/{id}` query ~lines 262-277) build events by hand. Replace **both** mapping lambdas with the shared mapper. For the `existing` query, change:

```kotlin
                .map { row ->
                    FinancialEvent(
                        id = row[Events.id],
                        // ...all fields...
                    )
                }
```

to:

```kotlin
                .map { it.toFinancialEvent() }
```

Apply the identical replacement to the `imports/{id}` query block. Add `import com.jvillada.movi.server.db.toFinancialEvent`.

- [ ] **Step 5: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL (the deprecation warning on `streamProvider` is pre-existing and fine).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt \
        server/src/main/kotlin/com/jvillada/movi/server/db/EventMapper.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt
git commit -m "feat(server): add events.currency column, persist it, and DRY the event mapper"
```

---

### Task C: Account-type-aware balance math

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/balance/Balances.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/balance/BalancesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/com/jvillada/movi/server/balance/BalancesTest.kt`:

```kotlin
package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalancesTest {

    @Test
    fun `signedDelta on asset account income adds expense subtracts`() {
        assertEquals(100, signedDelta(AccountType.SAVINGS, TransactionType.INCOME, 100))
        assertEquals(-100, signedDelta(AccountType.SAVINGS, TransactionType.EXPENSE, 100))
    }

    @Test
    fun `signedDelta on credit card purchase raises debt payment lowers it`() {
        assertEquals(100, signedDelta(AccountType.CREDIT_CARD, TransactionType.EXPENSE, 100)) // compra
        assertEquals(-100, signedDelta(AccountType.CREDIT_CARD, TransactionType.INCOME, 100)) // abono
    }

    @Test
    fun `computeBalances groups by currency with credit-card signs`() {
        val evs = listOf(
            ev(TransactionType.EXPENSE, 100, "USD"),  // compra USD  -> +100 debt
            ev(TransactionType.EXPENSE, 50_000, "COP"), // compra COP -> +50000 debt
            ev(TransactionType.INCOME, 20_000, "COP"),  // abono COP  -> -20000 debt
        )
        val balances = computeBalances(AccountType.CREDIT_CARD, evs)
        assertEquals(100, balances["USD"])
        assertEquals(30_000, balances["COP"])
    }

    @Test
    fun `estimatedTotalCop adds foreign converted at rate`() {
        val balances = mapOf("COP" to 30_000L, "USD" to 100L)
        // 30000 + 100*3950 = 425000
        assertEquals(425_000, estimatedTotalCop(balances, 3950.0))
    }

    @Test
    fun `estimatedTotalCop is null when only COP`() {
        assertNull(estimatedTotalCop(mapOf("COP" to 30_000L), 3950.0))
    }

    private fun ev(t: TransactionType, amount: Long, cur: String) = FinancialEvent(
        id = "x", accountId = "a", type = t, amount = amount, currency = cur,
        category = "Otros", description = "", timestamp = 0L,
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.BalancesTest"`
Expected: FAIL to compile — `signedDelta`/`computeBalances`/`estimatedTotalCop` unresolved.

- [ ] **Step 3: Implement the helpers**

Create `server/src/main/kotlin/com/jvillada/movi/server/balance/Balances.kt`:

```kotlin
package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType

/**
 * One event's contribution to its account balance.
 * Asset accounts: INCOME adds, EXPENSE subtracts.
 * CREDIT_CARD (balance = positive debt): EXPENSE (purchase) raises debt, INCOME (payment) lowers it.
 */
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD -> if (type == TransactionType.EXPENSE) amount else -amount
        else                    -> if (type == TransactionType.INCOME) amount else -amount
    }

/** Per-currency balance derived from an account's (already non-voided) events. */
fun computeBalances(accountType: AccountType, events: List<FinancialEvent>): Map<String, Long> =
    events.groupBy { it.currency }
        .mapValues { (_, evs) -> evs.sumOf { signedDelta(accountType, it.type, it.amount) } }

/**
 * Estimated total COP value: COP balance + each foreign balance converted at [usdToCop].
 * Returns null when there is no foreign-currency balance (nothing to estimate).
 * Only USD is converted; any other foreign currency contributes 0 (extend when needed).
 */
fun estimatedTotalCop(balances: Map<String, Long>, usdToCop: Double): Long? {
    val hasForeign = balances.keys.any { it != "COP" }
    if (!hasForeign) return null
    val cop = balances["COP"] ?: 0L
    val foreign = balances.entries.sumOf { (cur, amt) ->
        when (cur) {
            "COP" -> 0L
            "USD" -> Math.round(amt * usdToCop)
            else  -> 0L
        }
    }
    return cop + foreign
}

/** COP-equivalent value of an account: the estimate if it has foreign balances, else the COP balance. */
fun accountCopValue(accountType: AccountType, events: List<FinancialEvent>, usdToCop: Double): Long {
    val balances = computeBalances(accountType, events)
    return estimatedTotalCop(balances, usdToCop) ?: (balances["COP"] ?: 0L)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.balance.BalancesTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/balance/Balances.kt \
        server/src/test/kotlin/com/jvillada/movi/server/balance/BalancesTest.kt
git commit -m "feat(server): account-type-aware balance math (signedDelta, per-currency, TRM estimate)"
```

---

### Task D: TRM rate service

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/fx/FxRateService.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/fx/FxRateServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/com/jvillada/movi/server/fx/FxRateServiceTest.kt`:

```kotlin
package com.jvillada.movi.server.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FxRateServiceTest {

    @Test
    fun `parseTrm reads valor from a Socrata TRM payload`() {
        val body = """[{"valor":"3950.55","unidad":"COP","vigenciadesde":"2026-06-08T00:00:00.000"}]"""
        assertEquals(3950.55, FxRateService.parseTrm(body))
    }

    @Test
    fun `parseTrm picks the most recent row when several are returned`() {
        val body = """
          [{"valor":"3900.00","vigenciadesde":"2026-06-07T00:00:00.000"},
           {"valor":"3950.55","vigenciadesde":"2026-06-08T00:00:00.000"}]
        """.trimIndent()
        assertEquals(3950.55, FxRateService.parseTrm(body))
    }

    @Test
    fun `parseTrm returns null for garbage`() {
        assertNull(FxRateService.parseTrm("not json"))
        assertNull(FxRateService.parseTrm("[]"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.fx.FxRateServiceTest"`
Expected: FAIL to compile — `FxRateService` unresolved.

- [ ] **Step 3: Implement the service**

Create `server/src/main/kotlin/com/jvillada/movi/server/fx/FxRateService.kt`:

```kotlin
package com.jvillada.movi.server.fx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Official Colombian TRM (USD→COP) from datos.gov.co, cached one calendar day,
 * with a fallback chain so a failed fetch never produces a wrong estimate.
 */
object FxRateService {

    // Most-recent TRM row, newest first.
    private const val URL =
        "https://www.datos.gov.co/resource/32sa-8pi3.json?%24order=vigenciadesde%20DESC&%24limit=1"
    private const val FALLBACK_RATE = 4000.0

    private val http: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cachedRate: Double? = null
    @Volatile private var cachedDay: Long = -1

    /** Latest USD→COP rate. Cached per day; falls back to last value, then [envRate], then constant. */
    suspend fun usdToCop(): Double {
        val today = System.currentTimeMillis() / 86_400_000L
        cachedRate?.let { if (cachedDay == today) return it }
        val fetched = withContext(Dispatchers.IO) { runCatching { fetchTrm() }.getOrNull() }
        if (fetched != null) {
            cachedRate = fetched
            cachedDay = today
            return fetched
        }
        return cachedRate ?: envRate() ?: FALLBACK_RATE
    }

    private fun fetchTrm(): Double? {
        val req = HttpRequest.newBuilder(URI.create(URL)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) return null
        return parseTrm(res.body())
    }

    /** Pure parser for a Socrata TRM JSON array. Returns the newest row's `valor`, or null. */
    fun parseTrm(body: String): Double? = runCatching {
        val arr = json.parseToJsonElement(body).jsonArray
        arr.maxByOrNull { it.jsonObject["vigenciadesde"]?.jsonPrimitive?.content ?: "" }
            ?.jsonObject?.get("valor")?.jsonPrimitive?.content?.toDouble()
    }.getOrNull()

    private fun envRate(): Double? =
        System.getenv("USD_COP_RATE")?.toDoubleOrNull()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.fx.FxRateServiceTest"`
Expected: PASS (3 tests). No network call (only `parseTrm` is exercised).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/fx/FxRateService.kt \
        server/src/test/kotlin/com/jvillada/movi/server/fx/FxRateServiceTest.kt
git commit -m "feat(server): TRM (USD->COP) rate service with daily cache and fallback"
```

---

### Task E: Parser keeps native currency, stops excluding card payments

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParserTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ClaudeStatementParserTest.kt` (inside the class):

```kotlin
    @Test
    fun `parseJson maps currency and defaults to COP`() {
        val json = """[
          {"date":"2026-06-04","merchant":"Anthropic","amount":100,"currency":"USD","type":"EXPENSE","category":"Tecnología","description":"","rawText":""},
          {"date":"2026-05-31","merchant":"YouTube","amount":79000,"type":"EXPENSE","category":"Entretenimiento","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertEquals("USD", result[0].currency)
        assertEquals("COP", result[1].currency) // absent -> default
    }
```

(Imports `kotlin.test.assertEquals` already present in the file.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.ClaudeStatementParserTest"`
Expected: FAIL — `result[0].currency` unresolved (ParsedTransaction lacks the mapping in `parseJson`).

- [ ] **Step 3: Add `currency` to `ClaudeRow` and map it**

In `ClaudeStatementParser.kt`, the private `ClaudeRow` data class — add `currency`:

```kotlin
    @Serializable
    private data class ClaudeRow(
        val date: String,
        val merchant: String,
        val amount: Long,
        val currency: String = "COP",
        val type: String,
        val category: String,
        val description: String = "",
        val rawText: String = "",
    )
```

In `parseJson`, the `ParsedTransaction(...)` construction — add `currency = row.currency`:

```kotlin
                    ParsedTransaction(
                        id = UUID.randomUUID().toString(),
                        date = row.date,
                        merchant = row.merchant,
                        amount = row.amount,
                        currency = row.currency,
                        type = runCatching { TransactionType.valueOf(row.type) }.getOrDefault(TransactionType.EXPENSE),
                        category = row.category,
                        description = row.description,
                        rawText = row.rawText,
                    )
```

- [ ] **Step 4: Update the prompt — emit currency, no conversion, keep card payments**

In `buildSystemPrompt`, change the JSON-format example line to include `currency`:

```kotlin
[{"date":"YYYY-MM-DD","merchant":"nombre limpio","amount":123456,"currency":"COP","type":"EXPENSE|INCOME","category":"categoría","description":"descripción corta","rawText":"línea original"}]
```

Replace the `MONTOS:` block:

```kotlin
MONTOS Y MONEDA:
- currency: la moneda NATIVA de la transacción ("COP" o "USD"), tomada de la columna de moneda del extracto. Si no hay columna de moneda, usá "COP".
- amount: el valor en su moneda NATIVA. NO conviertas USD a COP — dejá el valor tal cual viene en esa moneda.
- amount es entero positivo, sin separadores de miles ni decimales.
- El extracto puede usar formato colombiano ($ 46.489,00) o americano (46,489.00) — detectá cuál es según el documento.
- Descartá los centavos: redondeá a la unidad más cercana.
```

Replace the credit-card payment line:

```kotlin
- Los pagos/abonos a la tarjeta (ABONO, ABONO DEBITO AUTOMATICO, PAGO ALTERNATIVO) SÍ se incluyen, como INCOME — reducen la deuda de la tarjeta.
```

In the `EXCLUIR (no son movimientos del titular):` block, remove the `PAGO ALTERNATIVO` bullet so it reads:

```kotlin
EXCLUIR (no son movimientos del titular):
- Filas de saldo corriente (columna "Saldo" que muestra balance acumulado)
- Filas de totales, subtotales y encabezados de tabla
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.ClaudeStatementParserTest"`
Expected: PASS (all tests, including the new currency test).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt \
        server/src/test/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParserTest.kt
git commit -m "feat(server): parser preserves native currency and keeps card payments (no USD->COP conversion)"
```

---

### Task F: Derive balances in AccountRoutes

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/balance/EventQueries.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt`

No unit test (DB/route); verified in Task H.

- [ ] **Step 1: Create the shared non-voided-events query**

Create `server/src/main/kotlin/com/jvillada/movi/server/balance/EventQueries.kt`:

```kotlin
package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/** Non-voided events for a user, optionally filtered to one account. */
suspend fun loadNonVoidedEvents(uid: String, accountId: String? = null): List<FinancialEvent> = dbQuery {
    val voided = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    val accountFilter = if (accountId != null) Events.accountId eq accountId else Op.TRUE
    Events.selectAll()
        .where { (Events.userId eq uid) and accountFilter }
        .filterNot { it[Events.id] in voided }
        .map { it.toFinancialEvent() }
}
```

- [ ] **Step 2: Enrich accounts with derived balances**

Rewrite `AccountRoutes.kt` so GET list and GET by-id return derived balances. Replace the whole file body of `accountRoutes()` GET handlers and the `toAccount()` mapper:

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.estimatedTotalCop
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            val uid = call.userId()
            val rate = FxRateService.usdToCop()
            val rows = dbQuery {
                Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
            }
            val enriched = rows.map { enrich(uid, it, rate) }
            call.respond(enriched)
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val base = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(enrich(uid, base, FxRateService.usdToCop()))
        }

        post {
            val body = call.receive<Account>()
            val uid = call.userId()
            val account = body.copy(
                id = body.id.ifBlank { "acc_${System.currentTimeMillis()}" }
            )
            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[name]     = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
            }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}

private suspend fun enrich(uid: String, base: Account, rate: Double): Account {
    val events = loadNonVoidedEvents(uid, base.id)
    val balances = computeBalances(base.type, events)
    return base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
    )
}

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
```

- [ ] **Step 3: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/balance/EventQueries.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt
git commit -m "feat(server): derive account balances per currency with TRM estimate"
```

---

### Task G: Remove write-time balance updates; derive FinanceSummary

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt`

Balances are now derived, so the running-total updates must go (otherwise they double-apply to the stored column, which nothing reads — but leaving them is dead, confusing code). FinanceSummary must derive instead of summing the now-stale stored column. No unit test; verified in Task H.

- [ ] **Step 1: Remove the balance update from event creation**

In `EventRoutes.kt` create handler, delete these lines (the `delta`/`Accounts.update` after the insert):

```kotlin
                val delta = if (event.type == TransactionType.INCOME) event.amount else -event.amount
                Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                    it[balance] = Accounts.balance + delta
                }
```

- [ ] **Step 2: Remove the balance reversal from void**

In `EventRoutes.kt` void handler, delete:

```kotlin
                    val delta = if (event.type == TransactionType.INCOME) -event.amount else event.amount
                    Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                        it[balance] = Accounts.balance + delta
                    }
```

Remove the now-unused imports `org.jetbrains.exposed.sql.SqlExpressionBuilder.plus` and `org.jetbrains.exposed.sql.update` from `EventRoutes.kt` only if the compiler flags them as unused (Kotlin treats unused imports as warnings, not errors — leaving them is harmless; delete if you prefer a clean build).

- [ ] **Step 3: Remove the balance update from statement import**

In `StatementRoutes.kt` `createEventFromParsed`, delete the tail of the `dbQuery { ... }` block:

```kotlin
        val delta = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
        Accounts.update({ (Accounts.id eq accountId) and (Accounts.userId eq uid) }) {
            it[balance] = Accounts.balance + delta
        }
```

so the block ends right after the `Events.insert { ... }`.

- [ ] **Step 4: Derive the FinanceSummary balance**

In `FinanceRoutes.kt`, replace the stored-balance sum (lines ~123-125):

```kotlin
            val balance = Accounts.selectAll()
                .where { Accounts.userId eq uid }
                .sumOf { it[Accounts.balance] }
```

with a derived sum over each account's events. Because the rate lookup is suspend and outside `dbQuery`, fetch it before the `dbQuery { ... }` block and compute the per-account value with the shared helpers. Concretely, **above** the `val summary = dbQuery {` line add:

```kotlin
        val rate = com.jvillada.movi.server.fx.FxRateService.usdToCop()
        val accountRows = dbQuery {
            Accounts.selectAll().where { Accounts.userId eq uid }
                .map { it[Accounts.id] to AccountType.valueOf(it[Accounts.type]) }
        }
        val derivedBalance = accountRows.sumOf { (accId, accType) ->
            com.jvillada.movi.server.balance.accountCopValue(
                accType,
                com.jvillada.movi.server.balance.loadNonVoidedEvents(uid, accId),
                rate,
            )
        }
```

Then inside the `dbQuery { ... }` block, change the `balance` usage in the returned `FinanceSummary` to use `derivedBalance` and delete the old `val balance = Accounts.selectAll()...` lines:

```kotlin
            FinanceSummary(scope = scope, balance = derivedBalance, ingresos = ingresos, egresos = egresos)
```

Add the import `import com.jvillada.movi.shared.model.AccountType` to `FinanceRoutes.kt` if not already present.

- [ ] **Step 5: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full server test suite**

Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL — all existing tests plus BalancesTest, FxRateServiceTest, and the parser currency test pass. (The temporary `RealStatementParseHarness` makes live calls; if it interferes, run with `--tests` filters per task instead.)

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt
git commit -m "refactor(server): derive balances from events everywhere; drop stored running balance"
```

---

### Task H: End-to-end verification (credit-card import)

**Files:**
- Modify (temporary): `server/src/test/kotlin/com/jvillada/movi/server/parsing/RealStatementParseHarness.kt`

This task proves the whole feature against the real `bancolombia tc mastercard.xlsx`. It is **verification, not committed code** — the harness is temporary.

- [ ] **Step 1: Restart the server with the new code**

```bash
pkill -f ':server:run' 2>/dev/null; sleep 2
./gradlew :server:run --console=plain > /tmp/movi_server.log 2>&1 &
until curl -s http://localhost:8080/health | grep -q OK; do sleep 2; done; echo "server up"
```

- [ ] **Step 2: Drive the credit-card flow**

Run this script (registers a user, creates a CREDIT_CARD account, imports the TC statement, reads back the derived balances):

```bash
set -e
BASE=http://localhost:8080
EMAIL="cctest_$(date +%s)@example.com"
TOKEN=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"name\":\"CC Test\",\"password\":\"secret123\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

ACCID=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"acc_tc_mastercard","name":"Mastercard","type":"CREDIT_CARD","balance":0,"currency":"COP"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s -X POST $BASE/api/statements/upload -H "Authorization: Bearer $TOKEN" \
  -F "file=@docs/movements/bancolombia tc mastercard.xlsx" > /tmp/tc_parse.json
python3 - "$ACCID" <<'PY'
import json,sys
pr=json.load(open('/tmp/tc_parse.json'))
dec={"statementId":pr["statementId"],"accountId":sys.argv[1],"bankName":pr["bankName"],
     "period":pr["period"],"imports":pr["newTransactions"],"reconciliations":[],"skipped":[]}
json.dump(dec,open('/tmp/tc_decision.json','w'))
print("parsed:",len(pr["newTransactions"]),"currencies:",sorted({t["currency"] for t in pr["newTransactions"]}))
PY
curl -s -X POST $BASE/api/statements/import -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d @/tmp/tc_decision.json
echo
echo "=== account after import ==="
curl -s $BASE/api/accounts/$ACCID -H "Authorization: Bearer $TOKEN"
echo
```

- [ ] **Step 3: Verify the expectations**

Confirm in the final account JSON:
- `balancesByCurrency` has **both** `COP` and `USD` keys.
- The `USD` value equals the sum of USD purchases minus USD payments (native units, e.g. ~`100+20+20+67+26+13 − 66`), NOT converted.
- `estimatedTotalCop` ≈ `COP debt + USD debt × TRM` (TRM ~3900-4100), and is clearly larger than the COP-only `balance`.
- The `ABONO DEBITO AUTOMATIC` rows are present (not excluded) and reduced the debt.

If a value is off, debug the specific helper (Task C) or the parser output (`/tmp/tc_parse.json`) before declaring done.

- [ ] **Step 4: Stop the server**

```bash
pkill -f ':server:run' 2>/dev/null; echo "server stopped"
```

- [ ] **Step 5 (optional): Delete the temporary harness**

Once verified, remove the scratch harness so it never runs in CI:

```bash
git rm -f --ignore-unmatch server/src/test/kotlin/com/jvillada/movi/server/parsing/RealStatementParseHarness.kt 2>/dev/null \
  || rm -f server/src/test/kotlin/com/jvillada/movi/server/parsing/RealStatementParseHarness.kt
```

---

## Self-review notes

- **Spec coverage:** A (currency on events) → Tasks A, B. B (per-currency card debt) → Tasks C, F. C (TRM service, cache, fallback) → Task D. D (signedDelta single helper) → Task C, applied in F/G. E (parser preserves currency, keep payments) → Task E. Derived balances → F; consistency with FinanceSummary → G. Estimate exposure → F. Testing → A/C/D/E unit, H e2e.
- **Type consistency:** `signedDelta(AccountType, TransactionType, Long)`, `computeBalances(AccountType, List<FinancialEvent>)`, `estimatedTotalCop(Map<String,Long>, Double)`, `accountCopValue(AccountType, List<FinancialEvent>, Double)`, `loadNonVoidedEvents(String, String?)`, `FxRateService.usdToCop()` / `parseTrm(String)`, `ResultRow.toFinancialEvent()` — used identically across Tasks B, C, D, F, G.
- **Out of scope (noted):** UI/web rendering of `balancesByCurrency`/`estimatedTotalCop`; currencies other than USD; dropping the now-unused `accounts.balance` column (left in place).
```
