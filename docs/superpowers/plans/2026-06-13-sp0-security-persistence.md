# SP-0 Security & Durable Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close movi's production blockers — fail-fast JWT secret, per-user data isolation, durable Postgres storage for recurring rules + SMS, and fix the AI chat context leak.

**Architecture:** Server-contained changes only (`:server`), plus zero `:shared` UI changes (legacy client repo methods are left as harmless dead code to avoid touching `QuickAddScreen`). New Exposed tables follow the existing `Budgets` pattern (per-user `dbQuery` with `call.userId()`). Clean cutover — new tables start empty; no data migration.

**Tech Stack:** Ktor + Exposed + Postgres (HikariCP), kotlin.test + Ktor `testApplication`. Build with JBR 21 (`./gradlew`).

**Spec:** `docs/superpowers/specs/2026-06-13-sp0-security-persistence-design.md`

---

## File map

- **Modify** `server/.../auth/JwtConfig.kt` — fail-fast on missing `JWT_SECRET`.
- **Modify** `server/.../db/Tables.kt` — add `RecurringRules`, `SmsMessages`.
- **Modify** `server/.../db/DatabaseFactory.kt` — register new tables in `SchemaUtils.create`.
- **Modify** `server/.../routes/FinanceRoutes.kt` — recurring from DB per-user; credits/goals → `emptyList()`.
- **Rewrite** `server/.../routes/SmsRoutes.kt` — per-user DB (keep `parseSms` regex).
- **Delete** `server/.../routes/WalletRoutes.kt`; **Modify** `server/.../plugins/Routing.kt` (drop `walletRoutes()`).
- **Modify** `server/.../storage/Stores.kt` — remove wallets/transactions/credits/goals/recurring/sms (keep `merchantRules`).
- **Modify** `server/.../routes/AiRoutes.kt` — `buildUserContext(uid)` from real data.
- **Add** `server/src/test/.../routes/IsolationTest.kt` — two-user HTTP isolation tests.
- **Add** `server/src/test/.../auth/JwtConfigTest.kt` — secret fail-fast.

> **Patterns to mirror:** `FinanceRoutes.kt` `budgets` endpoints already show the exact `val uid = call.userId()` + `dbQuery { Table.selectAll().where { Table.userId eq uid } }` idiom. `Tables.kt` shows the Exposed table style (`varchar`, `long`, `integer`, `index(...)`).

---

## Task 1: JWT secret fail-fast (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/auth/JwtConfig.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/auth/JwtConfigTest.kt`

Currently `secret` falls back to the literal `"movi-dev-secret-change-in-production"`. Replace that fallback: resolve `JWT_SECRET` from env → `.env` file → else throw. Expose the resolution as a testable pure function.

- [ ] **Step 1: Write the failing test**

Create `JwtConfigTest.kt`:

```kotlin
package com.jvillada.movi.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtConfigTest {
    @Test
    fun `resolveSecret returns provided value`() {
        assertEquals("abc123", JwtConfig.resolveSecret(env = "abc123", fromFile = null))
    }

    @Test
    fun `resolveSecret falls back to env file`() {
        assertEquals("filesecret", JwtConfig.resolveSecret(env = null, fromFile = "filesecret"))
    }

    @Test
    fun `resolveSecret throws when nothing is set`() {
        val ex = assertFailsWith<IllegalStateException> {
            JwtConfig.resolveSecret(env = null, fromFile = null)
        }
        assertEquals(true, ex.message?.contains("JWT_SECRET"))
    }

    @Test
    fun `resolveSecret rejects blank`() {
        assertFailsWith<IllegalStateException> { JwtConfig.resolveSecret(env = "  ", fromFile = null) }
    }
}
```

- [ ] **Step 2: Run test, verify it fails to compile/fail**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.auth.JwtConfigTest"`
Expected: FAIL — `resolveSecret` does not exist yet.

- [ ] **Step 3: Implement**

In `JwtConfig.kt`, replace the `secret` val and add `resolveSecret`:

```kotlin
    /** Pure, testable secret resolution: env var wins, then .env file, else fail fast. */
    fun resolveSecret(env: String?, fromFile: String?): String {
        val candidate = env?.takeIf { it.isNotBlank() }
            ?: fromFile?.takeIf { it.isNotBlank() }
        return candidate
            ?: error("JWT_SECRET not set — refusing to start with an insecure default. Set the JWT_SECRET env var.")
    }

    private val secret: String by lazy {
        resolveSecret(System.getenv("JWT_SECRET"), readFromEnvFile("JWT_SECRET"))
    }
```

(Keep `readFromEnvFile`, `algorithm`, `ISSUER`, `AUDIENCE`, `VALIDITY_MS`, `makeToken`, `verifier` unchanged.)

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.auth.JwtConfigTest"`
Expected: PASS.

- [ ] **Step 5: Ensure local dev still has a secret**

Confirm `server/.env` contains a `JWT_SECRET=...` line (it does). The server reads it via `readFromEnvFile`. No commit of `.env` (it is gitignored).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/auth/JwtConfig.kt server/src/test/kotlin/com/jvillada/movi/server/auth/JwtConfigTest.kt
git commit -m "feat(server): fail fast when JWT_SECRET is unset"
```

---

## Task 2: New Postgres tables for recurring rules + SMS

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt`

- [ ] **Step 1: Add the two tables**

Append to `Tables.kt`:

```kotlin
object RecurringRules : Table("recurring_rules") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 100)
    val category   = varchar("category", 100)
    val amount     = long("amount")
    val dayOfMonth = integer("day_of_month")
    val type       = varchar("type", 20)
    override val primaryKey = PrimaryKey(id)
    init { index("idx_recurring_rules_user_id", false, userId) }
}

object SmsMessages : Table("sms_messages") {
    val id     = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val time   = varchar("time", 50)
    val bank   = varchar("bank", 100)
    val text   = text("text")
    val state  = varchar("state", 20)
    val det    = varchar("det", 255)
    override val primaryKey = PrimaryKey(id)
    init { index("idx_sms_messages_user_id", false, userId) }
}
```

- [ ] **Step 2: Register them in schema creation**

In `DatabaseFactory.kt`, change the `transaction { }` block in `init()`:

```kotlin
        transaction {
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages)
            SchemaUtils.createMissingTablesAndColumns(Events)
        }
```

- [ ] **Step 3: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt
git commit -m "feat(server): add recurring_rules and sms_messages tables"
```

---

## Task 3: FinanceRoutes — per-user recurring; credits/goals empty

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt`

- [ ] **Step 1: Replace the three store-backed GETs**

Change the top of `financeRoutes()`:

```kotlin
    get("/api/holdings") { call.respond(emptyList<Holding>()) }
    get("/api/credits") { call.respond(emptyList<Credit>()) }
    get("/api/goals") { call.respond(emptyList<Goal>()) }
    get("/api/recurring-rules") {
        val uid = call.userId()
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map {
                RecurringRule(
                    id = it[RecurringRules.id],
                    name = it[RecurringRules.name],
                    category = it[RecurringRules.category],
                    amount = it[RecurringRules.amount],
                    dayOfMonth = it[RecurringRules.dayOfMonth],
                    type = TransactionType.valueOf(it[RecurringRules.type]),
                )
            }
        }
        call.respond(rules)
    }
```

Add imports: `com.jvillada.movi.server.db.RecurringRules`, `com.jvillada.movi.shared.model.Credit`, `com.jvillada.movi.shared.model.Goal`, `com.jvillada.movi.shared.model.RecurringRule`. Remove the `com.jvillada.movi.server.storage.Stores` import if no longer used elsewhere in the file (it is not — budgets/finance-summary are DB-backed).

- [ ] **Step 2: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt
git commit -m "feat(server): per-user recurring rules; credits/goals return empty"
```

---

## Task 4: SmsRoutes — per-user, DB-backed

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt`

Keep the `parseSms`/`categoryFor`/regexes exactly as-is. Rewrite only `fun Route.smsRoutes()` to scope by `userId` against `SmsMessages`.

- [ ] **Step 1: Rewrite the route block**

Replace `fun Route.smsRoutes() { ... }` with:

```kotlin
fun Route.smsRoutes() {
    get("/api/sms") {
        val uid = call.userId()
        val list = dbQuery {
            SmsMessages.selectAll().where { SmsMessages.userId eq uid }.map { it.toSmsMessage() }
        }
        call.respond(list)
    }

    get("/api/sms/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(sms)
    }

    get("/api/sms/{id}/parse") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text)
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, "No se pudo parsear")
        call.respond(parsed)
    }

    post("/api/sms/{id}/confirm") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = "confirmed"
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.OK)
    }

    post("/api/sms/{id}/ignore") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = "ignored"
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toSmsMessage() = SmsMessage(
    id    = this[SmsMessages.id],
    time  = this[SmsMessages.time],
    bank  = this[SmsMessages.bank],
    text  = this[SmsMessages.text],
    state = this[SmsMessages.state],
    det   = this[SmsMessages.det],
)
```

Update imports: remove `com.jvillada.movi.server.storage.Stores`; add `com.jvillada.movi.server.db.SmsMessages`, `com.jvillada.movi.server.db.dbQuery`, `com.jvillada.movi.server.plugins.userId`, `com.jvillada.movi.shared.model.SmsMessage`, `org.jetbrains.exposed.sql.selectAll`, `org.jetbrains.exposed.sql.update`, `org.jetbrains.exposed.sql.and`, `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`.

- [ ] **Step 2: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt
git commit -m "feat(server): per-user DB-backed SMS routes"
```

---

## Task 5: Delete legacy WalletRoutes + stores

**Files:**
- Delete: `server/src/main/kotlin/com/jvillada/movi/server/routes/WalletRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt`

> Leave the client-side repo methods (`getWallets`, `addTransaction`, etc.) in `:core` untouched — they are unused dead code (verified: no `shared/` UI caller) and removing them risks `QuickAddScreen` compile churn. A later cleanup task can remove them. SP-0 stays server-contained.

- [ ] **Step 1: Delete the route file**

```bash
git rm server/src/main/kotlin/com/jvillada/movi/server/routes/WalletRoutes.kt
```

- [ ] **Step 2: Drop registration in Routing.kt**

Remove the `walletRoutes()` line from the `authenticate("jwt") { ... }` block so it reads:

```kotlin
        authenticate("jwt") {
            accountRoutes()
            eventRoutes()
            financeRoutes()
            smsRoutes()
            aiRoutes()
            statementRoutes()
        }
```

- [ ] **Step 3: Remove the dead stores**

In `Stores.kt`, delete the `wallets`, `transactions`, `credits`, `goals`, `recurring`, and `sms` lines (and the "Legacy" comment), leaving only `merchantRules`:

```kotlin
object Stores {
    val merchantRules = MerchantRulesStore()
}
```

Remove the now-unused `DATA_DIR`/`File`/`com.jvillada.movi.shared.model.*` imports if they become unused (keep whatever `MerchantRulesStore` needs — check by compiling).

- [ ] **Step 4: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL. If `JsonListStore` is now unused, leaving it is fine (don't chase unrelated deletions).

- [ ] **Step 5: Commit**

```bash
git add -A server/src/main/kotlin/com/jvillada/movi/server/
git commit -m "feat(server): remove legacy global wallet/transaction routes and stores"
```

---

## Task 6: Fix the AI context leak (real per-user data)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/AiRoutes.kt`

Replace the hardcoded `buildUserContext()` with `buildUserContext(uid)` querying the authenticated user's real data, and pass `call.userId()` in the handler.

- [ ] **Step 1: Rewrite buildUserContext + call site**

In the handler, change `val context = buildUserContext()` to:

```kotlin
        val context = buildUserContext(call.userId())
```

Replace the whole `private fun buildUserContext(): String = ...` with a DB-backed version. Mirror the month-window + balance logic already in `FinanceRoutes.get("/api/finance-summary")`:

```kotlin
private suspend fun buildUserContext(uid: String): String {
    val rate = FxRateService.usdToCop()
    val accountRows = dbQuery {
        Accounts.selectAll().where { Accounts.userId eq uid }
            .map { Triple(it[Accounts.id], it[Accounts.name], AccountType.valueOf(it[Accounts.type])) }
    }
    val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }

    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
    val monthEnd = now.withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()

    val voidedIds = dbQuery {
        VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    }
    val monthEvents = dbQuery {
        Events.selectAll().where {
            (Events.userId eq uid) and (Events.timestamp greaterEq monthStart) and (Events.timestamp less monthEnd)
        }.filterNot { it[Events.id] in voidedIds }
            .map { it[Events.type] to it[Events.amount] to it[Events.currency] }
    }
    val ingresos = monthEvents.filter { it.first.first == TransactionType.INCOME.name && it.second == "COP" }.sumOf { it.first.second }
    val egresos = monthEvents.filter { it.first.first == TransactionType.EXPENSE.name && it.second == "COP" }.sumOf { it.first.second }

    val budgets = dbQuery {
        Budgets.selectAll().where { Budgets.userId eq uid }.map { it[Budgets.category] to it[Budgets.monthlyLimit] }
    }

    return buildString {
        appendLine("DATOS DEL USUARIO (Colombia)")
        appendLine()
        appendLine("== Resumen del mes en curso ==")
        appendLine("- Ingresos: ${'$'}$ingresos")
        appendLine("- Egresos: ${'$'}$egresos")
        appendLine("- Flujo: ${'$'}${ingresos - egresos}")
        appendLine()
        appendLine("== Cuentas ==")
        if (accountRows.isEmpty()) appendLine("- (sin cuentas registradas)")
        accountRows.forEach { (id, name, type) ->
            val value = accountCopValue(type, eventsByAccount[id] ?: emptyList(), rate)
            val kind = if (type == AccountType.CREDIT_CARD || type == AccountType.LOAN) "deuda" else "saldo"
            appendLine("- $name ($type): $kind ${'$'}$value")
        }
        appendLine()
        appendLine("== Presupuestos ==")
        if (budgets.isEmpty()) appendLine("- (sin presupuestos)")
        budgets.forEach { (cat, limit) -> appendLine("- $cat: límite ${'$'}$limit") }
    }
}
```

Add imports: `com.jvillada.movi.server.balance.accountCopValue`, `com.jvillada.movi.server.balance.loadNonVoidedEvents`, `com.jvillada.movi.server.db.*` (Accounts, Budgets, Events, VoidEvents, dbQuery), `com.jvillada.movi.server.fx.FxRateService`, `com.jvillada.movi.server.plugins.userId`, `com.jvillada.movi.shared.model.AccountType`, `com.jvillada.movi.shared.model.TransactionType`, `org.jetbrains.exposed.sql.*` selectAll/and/comparisons, `java.time.ZoneOffset`, `java.time.ZonedDateTime`.

- [ ] **Step 2: Compile**

Run: `./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/AiRoutes.kt
git commit -m "fix(server): AI chat context uses authenticated user's real data"
```

---

## Task 7: Two-user isolation tests

**Files:**
- Add: `server/src/test/kotlin/com/jvillada/movi/server/routes/IsolationTest.kt`

Prove user A cannot read user B's recurring rules / SMS, and that credits/goals return `[]`. Use Ktor `testApplication`. The server needs a DB; use an in-memory H2 in Postgres-compatibility mode if a Postgres test instance is unavailable.

- [ ] **Step 1: Check test DB availability**

Inspect `server/build.gradle.kts` test deps. If neither H2 nor a testcontainers Postgres is present, add H2: in `gradle/libs.versions.toml` add `h2 = "2.2.224"` and a library alias, then `testImplementation(libs.h2)`. Exposed speaks H2.

> If wiring a test DB proves too involved for this task, STOP and report DONE_WITH_CONCERNS — do NOT fake the isolation test. A query-builder-level test (constructing the `where` clauses and asserting they include `userId`) is an acceptable fallback; note the downgrade explicitly.

- [ ] **Step 2: Write the isolation test**

Create `IsolationTest.kt` that: boots the app against a fresh test DB with `JWT_SECRET` set; registers user A and user B; inserts a `RecurringRules`/`SmsMessages` row owned by A; calls the endpoints as B (with B's token) and asserts B sees none of A's rows; asserts `GET /api/credits` and `/api/goals` return `[]`. (Construct tokens via `JwtConfig.makeToken`.) Follow the app's existing `Application.module()` wiring; set env/system properties so `DatabaseFactory` points at the test DB.

- [ ] **Step 3: Run**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.IsolationTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add server/src/test/kotlin/com/jvillada/movi/server/routes/IsolationTest.kt gradle/libs.versions.toml server/build.gradle.kts
git commit -m "test(server): two-user isolation for recurring + sms; credits/goals empty"
```

---

## Task 8: Full verification

**Files:** none.

- [ ] **Step 1: Full server build + tests**

Run: `./gradlew :server:test`
Expected: PASS — all tests including new JwtConfig + Isolation tests.

- [ ] **Step 2: Boot check (fail-fast)**

Run without a secret: `JWT_SECRET= DATABASE_URL=... ./gradlew :server:run` is hard to script; instead rely on Task 1's unit test for the fail-fast path. Confirm the normal boot path works locally with `server/.env` present (manual, optional).

- [ ] **Step 3: Confirm shared still compiles (no UI changes, sanity)**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL (SP-0 made no `:shared` changes).

---

## Self-review notes

- **Spec coverage:** secret fail-fast → T1; new tables → T2; recurring per-user + credits/goals empty → T3; SMS per-user → T4; delete WalletRoutes + stores → T5; AI leak → T6; isolation tests → T7; verification → T8.
- **Deferred (per spec):** transport hardening (CORS/cleartext/debuggable/rate-limit), refresh tokens, and removal of dead `:core` client repo methods — all explicitly out of SP-0 scope.
- **Risk note:** Task 7's test DB wiring is the one place that may need a capable model / fallback; the plan calls that out rather than hiding it.
