# SP-1 Monthly Payment Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user manage monthly payments (rent, subscriptions, loan/card cuotas) as recurring rules, see an in-app "Próximos pagos" view with due/overdue status, and receive email reminders before payments are due.

**Architecture:** Reminder source = `RecurringRule` (one concept for all monthly payments). Server adds CRUD + a pure due-date engine + a `/api/payments/upcoming` endpoint + an in-process daily email scheduler (Resend, no-op without `RESEND_API_KEY`). Built on SP-0's per-user `recurring_rules` table. Delivered in 3 batches: (1) server engine + CRUD, (2) email scheduler, (3) `:shared` UI.

**Tech Stack:** Kotlin Multiplatform, Ktor + Exposed + Postgres, Compose Multiplatform, kotlin.test, Resend HTTP API. Build with JBR 21.

**Spec:** `docs/superpowers/specs/2026-06-13-sp1-payment-reminders-design.md`

---

## BATCH 1 — Server engine + CRUD + endpoint (this batch)

### Task 1: Payment DTOs in `:core`

**Files:**
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt`

- [ ] **Step 1: Add the DTOs**

Append to `Finance.kt`:

```kotlin
@Serializable
enum class PaymentStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

@Serializable
data class UpcomingPayment(
    val rule: RecurringRule,
    val dueDate: String,    // ISO "2026-06-05", current month
    val daysUntil: Int,     // negative if overdue
    val status: PaymentStatus,
)
```

- [ ] **Step 2: Compile** — `./gradlew :core:compileKotlinMetadata` → SUCCESS.
- [ ] **Step 3: Commit** — `git add -A core && git commit -m "feat(core): UpcomingPayment + PaymentStatus DTOs"`

---

### Task 2: Due-date engine (TDD, pure)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/reminders/DueDates.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/reminders/DueDatesTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DueDatesTest {
    private fun rule(day: Int) = RecurringRule("r$day", "Pago", "Otros", 1000, day, TransactionType.EXPENSE)

    @Test fun `due date is the day of the current month`() {
        assertEquals(LocalDate.of(2026, 6, 5), dueDateFor(rule(5), LocalDate.of(2026, 6, 13)))
    }

    @Test fun `day past month length clamps to last day`() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 10)))
    }

    @Test fun `status overdue when due before today`() {
        assertEquals(PaymentStatus.OVERDUE, statusFor(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due today`() {
        assertEquals(PaymentStatus.DUE_TODAY, statusFor(LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due soon within lead`() {
        assertEquals(PaymentStatus.DUE_SOON, statusFor(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status upcoming beyond lead`() {
        assertEquals(PaymentStatus.UPCOMING, statusFor(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `upcomingPayments sorts by due date and computes fields`() {
        val rules = listOf(rule(25), rule(5), rule(13))
        val out = upcomingPayments(rules, LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf(5, 13, 25), out.map { LocalDate.parse(it.dueDate).dayOfMonth })
        assertEquals(PaymentStatus.OVERDUE, out[0].status)   // day 5 < 13
        assertEquals(-8, out[0].daysUntil)
        assertEquals(PaymentStatus.DUE_TODAY, out[1].status) // day 13
        assertEquals(0, out[1].daysUntil)
    }
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :server:test --tests "*.DueDatesTest"` → FAIL (functions undefined).

- [ ] **Step 3: Implement**

```kotlin
package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.UpcomingPayment
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Due date in `today`'s month, clamping the rule's day to the month length. */
fun dueDateFor(rule: RecurringRule, today: LocalDate): LocalDate {
    val day = rule.dayOfMonth.coerceIn(1, today.lengthOfMonth())
    return LocalDate.of(today.year, today.month, day)
}

fun statusFor(dueDate: LocalDate, today: LocalDate, leadDays: Int): PaymentStatus = when {
    dueDate.isBefore(today) -> PaymentStatus.OVERDUE
    dueDate.isEqual(today)  -> PaymentStatus.DUE_TODAY
    ChronoUnit.DAYS.between(today, dueDate) <= leadDays -> PaymentStatus.DUE_SOON
    else -> PaymentStatus.UPCOMING
}

fun upcomingPayments(rules: List<RecurringRule>, today: LocalDate, leadDays: Int): List<UpcomingPayment> =
    rules.map { rule ->
        val due = dueDateFor(rule, today)
        UpcomingPayment(
            rule = rule,
            dueDate = due.toString(),
            daysUntil = ChronoUnit.DAYS.between(today, due).toInt(),
            status = statusFor(due, today, leadDays),
        )
    }.sortedBy { it.dueDate }
```

- [ ] **Step 4: Run, verify pass** — `./gradlew :server:test --tests "*.DueDatesTest"` → PASS.
- [ ] **Step 5: Commit** — `git add -A server && git commit -m "feat(server): payment due-date engine"`

---

### Task 3: recurring_rules CRUD + upcoming endpoint

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/ReminderRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt` (register `reminderRoutes()`)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt` (add `lastRemindedPeriod` column)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt` (createMissingTablesAndColumns for RecurringRules)

- [ ] **Step 1: Add server-only column**

In `Tables.kt`, add to `RecurringRules`:

```kotlin
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable()  // "YYYY-MM", server-only
```

In `DatabaseFactory.kt`, extend the migration call:

```kotlin
            SchemaUtils.createMissingTablesAndColumns(Events, RecurringRules)
```

- [ ] **Step 2: Create ReminderRoutes.kt**

Mirror the `Budgets` CRUD idiom in `FinanceRoutes.kt`. All handlers `val uid = call.userId()`; filter by `userId`.

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.upcomingPayments
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private fun org.jetbrains.exposed.sql.ResultRow.toRule() = RecurringRule(
    id = this[RecurringRules.id],
    name = this[RecurringRules.name],
    category = this[RecurringRules.category],
    amount = this[RecurringRules.amount],
    dayOfMonth = this[RecurringRules.dayOfMonth],
    type = TransactionType.valueOf(this[RecurringRules.type]),
)

fun Route.reminderRoutes() {
    post("/api/recurring-rules") {
        val uid = call.userId()
        val body = call.receive<RecurringRule>()
        val newId = "rr_${UUID.randomUUID()}"
        dbQuery {
            RecurringRules.insert {
                it[id] = newId
                it[userId] = uid
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
            }
        }
        call.respond(HttpStatusCode.Created, body.copy(id = newId))
    }

    put("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<RecurringRule>()
        val updated = dbQuery {
            RecurringRules.update({ (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }) {
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(body.copy(id = id))
    }

    delete("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val deleted = dbQuery {
            RecurringRules.deleteWhere { (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }
        }
        if (deleted == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }

    get("/api/payments/upcoming") {
        val uid = call.userId()
        val leadDays = System.getenv("REMINDER_LEAD_DAYS")?.toIntOrNull() ?: 3
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        call.respond(upcomingPayments(rules, LocalDate.now(ZoneOffset.UTC), leadDays))
    }
}
```

> NOTE: `/api/recurring-rules` GET already exists in `FinanceRoutes.kt` (SP-0). Leave it there; ReminderRoutes only adds POST/PUT/DELETE + the upcoming endpoint. Do not duplicate the GET.

- [ ] **Step 3: Register in Routing.kt** — add `reminderRoutes()` inside the `authenticate("jwt") { }` block.

- [ ] **Step 4: Compile** — `./gradlew :server:compileKotlin` → SUCCESS.

- [ ] **Step 5: Commit** — `git add -A server && git commit -m "feat(server): recurring-rule CRUD + upcoming payments endpoint"`

---

### Task 4: Repo methods in `:core`

**Files:**
- Modify: `core/.../repository/WalletRepository.kt` (interface)
- Modify: `core/.../repository/WalletRepositoryImpl.kt` (HTTP impl)
- Modify: `core/.../repository/LocalRepository.kt` (delegate to `remote`)
- Modify: `core/src/jvmTest/.../repository/NoOpRepository.kt` (stubs)

- [ ] **Step 1: Interface** — add to `WalletRepository`:

```kotlin
    suspend fun createRecurringRule(rule: RecurringRule): RecurringRule
    suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule
    suspend fun deleteRecurringRule(id: String)
    suspend fun getUpcomingPayments(): List<UpcomingPayment>
```

(Add imports for `UpcomingPayment`.)

- [ ] **Step 2: HTTP impl** — in `WalletRepositoryImpl`, mirror the budget CRUD style (`client.post(...).body()`, `setBody`, `contentType(ContentType.Application.Json)` exactly as `createBudget` does — copy its surrounding pattern):

```kotlin
    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule =
        client.post("$baseUrl/api/recurring-rules") { setJson(rule) }.body()
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule =
        client.put("$baseUrl/api/recurring-rules/$id") { setJson(rule) }.body()
    override suspend fun deleteRecurringRule(id: String) {
        client.delete("$baseUrl/api/recurring-rules/$id")
    }
    override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
        client.get("$baseUrl/api/payments/upcoming").body()
```

> Use whatever the existing `createBudget` uses to set the JSON body/content-type (look at it and replicate; `setJson` above is a placeholder for that exact idiom — do NOT invent a helper, inline the same `contentType(...)` + `setBody(...)` calls `createBudget` uses).

- [ ] **Step 3: LocalRepository + NoOpRepository** — delegate to `remote.*` / return stubs (`createRecurringRule = remote.createRecurringRule(rule)`, etc.; NoOp returns the input / `emptyList()`).

- [ ] **Step 4: Compile** — `./gradlew :core:compileKotlinMetadata :core:jvmTest` → SUCCESS.

- [ ] **Step 5: Commit** — `git add -A core && git commit -m "feat(core): recurring-rule CRUD + upcoming payments repo methods"`

---

### Task 5: Batch-1 isolation tests + verify

**Files:**
- Modify: `server/src/test/.../routes/IsolationTest.kt` (or a new `ReminderRoutesTest.kt` reusing the H2 harness)

- [ ] **Step 1:** Add tests proving: user A `POST`s a rule; user B `PUT`/`DELETE` on A's rule id → 404; `GET /api/payments/upcoming` as B excludes A's rule. Reuse the H2 `testApplication` harness from SP-0's `IsolationTest`.
- [ ] **Step 2:** `./gradlew :server:test` → all PASS.
- [ ] **Step 3:** Commit — `git commit -am "test(server): recurring-rule CRUD isolation + upcoming"`

---

## BATCH 2 — Email scheduler (next batch, summarized)

- `reminders/ResendClient.kt`: `suspend fun sendEmail(to, subject, html): Boolean` — POST `https://api.resend.com/emails`, Bearer `RESEND_API_KEY`, `from = REMINDER_FROM`. Graceful false on failure. Uses a Ktor `HttpClient` (add `ktor-client-cio` to server deps if absent) or `java.net.http.HttpClient`.
- `reminders/ReminderScheduler.kt`: `fun Application.startReminderScheduler()` launched from `Application.module()` ONLY if `RESEND_API_KEY` present. A coroutine: run a sweep at boot, then every `REMINDER_SWEEP_HOURS` (default 12). Sweep = for each user, find their `EXPENSE` rules whose `dueDateFor` is `OVERDUE`/`DUE_TODAY`/`DUE_SOON` AND `lastRemindedPeriod != currentPeriod("YYYY-MM")`; group into one email per user (join the user's `Users.email`); on send success, set `lastRemindedPeriod = currentPeriod` on those rules.
- TDD the **sweep selection** logic (`selectDueForReminder(rules, today, leadDays, period): List<RecurringRule>`) as a pure function; do NOT call Resend in tests.
- Tasks: (2a) ResendClient + deps, (2b) selectDueForReminder (TDD), (2c) scheduler wiring + module call, (2d) verify.

## BATCH 3 — UI "Próximos pagos" (next batch, summarized)

- Enhance `shared/.../ui/recurrentes/RecurrentesScreen.kt`: load `getUpcomingPayments()`; render sorted list with status badges (OVERDUE red `MinExpense`, DUE_TODAY/DUE_SOON amber, UPCOMING neutral) + "vence el N · en X días / hoy / vencido hace X".
- New `CreateRecurringRuleSheet.kt` modeled on `ui/accounts/CreateAccountSheet.kt` (name, amount, day-of-month 1–31, type EXPENSE default + INCOME chip, category). Calls `createRecurringRule`.
- Tap item → edit/delete via `updateRecurringRule`/`deleteRecurringRule`.
- Verify `:shared` compiles; manual check on web.

---

## Self-review notes
- **Spec coverage (batch 1):** DTOs → T1; due engine → T2; CRUD + upcoming + dedupe column → T3; repo → T4; isolation → T5. Batches 2-3 cover email + UI per spec sections E/F.
- **Isolation:** every new handler filters by `userId` (CRUD by id+userId). Tested in T5.
- **Graceful degradation:** scheduler only starts with `RESEND_API_KEY` (batch 2).
