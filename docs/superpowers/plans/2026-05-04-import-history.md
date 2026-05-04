# Import History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an import history section to ExtractosScreen showing past statement imports with a drill-down screen listing the events each import created or reconciled.

**Architecture:** New `StatementImports` DB table stores one row per import batch (bank, period, counts). A nullable `statement_import_id` column on `Events` links each event back to its batch. Two new GET endpoints expose the list and detail. The client adds a history section to ExtractosScreen and a new `ImportDetailScreen` for drill-down.

**Tech Stack:** Kotlin Multiplatform · Ktor server · Exposed ORM · Compose Multiplatform · kotlinx.serialization · kotlinx.datetime

---

## File Structure

| File | Action |
|------|--------|
| `shared/src/commonMain/.../model/Statement.kt` | Modify — add `bankName`/`period` to `ImportDecision`, add `StatementImport` + `StatementImportDetail` |
| `server/src/main/.../db/Tables.kt` | Modify — add `StatementImports` table, add `statementImportId` column to `Events` |
| `server/src/main/.../db/DatabaseFactory.kt` | Modify — register `StatementImports` in schema, call `createMissingTablesAndColumns` |
| `server/src/main/.../routes/StatementRoutes.kt` | Modify — update POST /import, add GET /imports and GET /imports/{id} |
| `shared/src/commonMain/.../repository/WalletRepository.kt` | Modify — add 2 method signatures |
| `shared/src/commonMain/.../repository/WalletRepositoryImpl.kt` | Modify — implement 2 methods |
| `shared/src/nonWasmMain/.../repository/LocalRepository.kt` | Modify — delegate 2 methods to remote |
| `shared/src/jvmTest/.../repository/NoOpRepository.kt` | Modify — stub 2 methods |
| `composeApp/src/commonMain/.../ui/Navigation.kt` | Modify — add `ImportDetail(importId)` |
| `composeApp/src/commonMain/.../App.kt` | Modify — add `is Screen.ImportDetail` case |
| `composeApp/src/commonMain/.../ui/extractos/ImportDetailScreen.kt` | Create — new screen |
| `composeApp/src/commonMain/.../ui/extractos/ExtractosScreen.kt` | Modify — add history section |
| `shared/src/jvmTest/.../model/StatementModelTest.kt` | Create — serialization test |

---

### Task 1: Shared models — add `bankName`/`period` to `ImportDecision`, add `StatementImport` + `StatementImportDetail`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt` (update ImportDecision construction)
- Create: `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/model/StatementModelTest.kt`

Context: `ImportDecision` currently lacks `bankName` and `period`. The server needs them to fill the `StatementImports` row when recording a batch. The client (`StatementReviewScreen`) has them available via `result.bankName` and `result.period`. We add them with defaults so the change is additive.

- [ ] **Step 1: Write the failing test**

Create `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/model/StatementModelTest.kt`:

```kotlin
package com.jvillada.movi.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StatementModelTest {

    @Test
    fun `StatementImport round-trips through JSON`() {
        val original = StatementImport(
            id = "si_abc",
            accountId = "acc_1",
            bankName = "Bancolombia",
            period = "Mayo 2025",
            importedAt = 1_700_000_000_000L,
            importedCount = 21,
            reconciledCount = 2,
        )
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<StatementImport>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `StatementImportDetail round-trips through JSON`() {
        val imp = StatementImport("si_1", "acc_1", "BBVA", "Abril 2025", 1_000L, 5, 1)
        val detail = StatementImportDetail(imp, emptyList())
        val json = Json.encodeToString(detail)
        val decoded = Json.decodeFromString<StatementImportDetail>(json)
        assertEquals(detail, decoded)
    }

    @Test
    fun `ImportDecision includes bankName and period`() {
        val decision = ImportDecision(
            statementId = "s1",
            accountId = "acc1",
            bankName = "Nequi",
            period = "Marzo 2025",
            imports = emptyList(),
            reconciliations = emptyList(),
            skipped = emptyList(),
        )
        val json = Json.encodeToString(decision)
        val decoded = Json.decodeFromString<ImportDecision>(json)
        assertEquals("Nequi", decoded.bankName)
        assertEquals("Marzo 2025", decoded.period)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:jvmTest --tests "com.jvillada.movi.shared.model.StatementModelTest" 2>&1 | tail -20
```

Expected: FAIL — `StatementImport`, `StatementImportDetail` are not defined yet; `ImportDecision` has no `bankName` field.

- [ ] **Step 3: Update Statement.kt**

Open `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt`.

Replace:
```kotlin
@Serializable
data class ImportDecision(
    val statementId: String,
    val accountId: String,
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,
)
```

With:
```kotlin
@Serializable
data class ImportDecision(
    val statementId: String,
    val accountId: String,
    val bankName: String = "",
    val period: String = "",
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,
)
```

Then append before the closing of the file (after `MerchantRule`):
```kotlin
@Serializable
data class StatementImport(
    val id: String,
    val accountId: String,
    val bankName: String,
    val period: String,
    val importedAt: Long,
    val importedCount: Int,
    val reconciledCount: Int,
)

@Serializable
data class StatementImportDetail(
    val import: StatementImport,
    val events: List<FinancialEvent>,
)
```

- [ ] **Step 4: Update StatementReviewScreen to pass bankName/period**

Open `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt`.

In the `import()` function, find the `ImportDecision(...)` call and add the two new fields:

```kotlin
val decision = ImportDecision(
    statementId = result.statementId,
    accountId = acct.id,
    bankName = result.bankName,
    period = result.period,
    imports = result.newTransactions.filter { it.id in selectedIds },
    reconciliations = reconciliations.values.toList(),
    skipped = result.newTransactions.map { it.id }.filter { it !in selectedIds },
)
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :shared:jvmTest --tests "com.jvillada.movi.shared.model.StatementModelTest" 2>&1 | tail -20
```

Expected: PASS — 3 tests pass.

- [ ] **Step 6: Verify shared module compiles**

```bash
./gradlew :shared:compileKotlinJvm 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add \
  shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt \
  shared/src/jvmTest/kotlin/com/jvillada/movi/shared/model/StatementModelTest.kt \
  composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: StatementImport model + ImportDecision bankName/period"
```

---

### Task 2: DB schema — `StatementImports` table + `statement_import_id` on `Events`

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt`

Context: `Tables.kt` uses snake_case column names and `varchar(id, 50)` for all IDs. `DatabaseFactory.kt` calls `SchemaUtils.create(...)` which creates tables that don't exist. For the new nullable column on the existing `Events` table, `SchemaUtils.createMissingTablesAndColumns(Events)` is also needed — this adds the column to an already-created table.

- [ ] **Step 1: Add `StatementImports` table to Tables.kt**

Open `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`.

Add after the `Accounts` object (before `Events`):

```kotlin
object StatementImports : Table("statement_imports") {
    val id             = varchar("id", 50)
    val userId         = varchar("user_id", 50)
    val accountId      = varchar("account_id", 50)
    val bankName       = varchar("bank_name", 100)
    val period         = varchar("period", 50)
    val importedAt     = long("imported_at")
    val importedCount  = integer("imported_count")
    val reconciledCount = integer("reconciled_count")
    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Add `statementImportId` column to `Events`**

In the same `Tables.kt`, inside the `Events` object, add after `syncedAt`:

```kotlin
val statementImportId = varchar("statement_import_id", 50).nullable()
```

Full `Events` object after change:

```kotlin
object Events : Table("financial_events") {
    val id                   = varchar("id", 50)
    val userId               = varchar("user_id", 50)
    val accountId            = varchar("account_id", 50)
    val type                 = varchar("type", 20)
    val amount               = long("amount")
    val category             = varchar("category", 100)
    val description          = varchar("description", 255)
    val merchant             = varchar("merchant", 255).nullable()
    val timestamp            = long("timestamp")
    val eventSource          = varchar("source", 20).default("MANUAL")
    val rawPayload           = text("raw_payload").nullable()
    val reconciliationStatus = varchar("reconciliation_status", 20).default("UNCONFIRMED")
    val syncedAt             = long("synced_at").nullable()
    val statementImportId    = varchar("statement_import_id", 50).nullable()
    override val primaryKey  = PrimaryKey(id)
}
```

- [ ] **Step 3: Update DatabaseFactory.kt**

Open `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt`.

Replace the `transaction { SchemaUtils.create(...) }` block with:

```kotlin
transaction {
    SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents)
    SchemaUtils.createMissingTablesAndColumns(Events)
}
```

`createMissingTablesAndColumns(Events)` adds `statement_import_id` to an existing `financial_events` table. On a fresh DB, `create()` already includes the column; this call is a no-op.

- [ ] **Step 4: Verify server compiles**

```bash
./gradlew :server:compileKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add \
  server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt \
  server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: StatementImports table + statement_import_id column on Events"
```

---

### Task 3: Server routes — update POST /import + add GET /imports endpoints

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`

Context: The existing `POST /api/statements/import` handler creates events and updates accounts. We need to:
1. Generate an import UUID at the start
2. Pass it to `createEventFromParsed` and the reconciliation update query so each event records its batch
3. Insert a `StatementImports` row at the end with the actual counts

Then add two new read endpoints.

- [ ] **Step 1: Update imports and add helper**

Open `StatementRoutes.kt`. Add these imports at the top (alongside existing ones):

```kotlin
import com.jvillada.movi.server.db.StatementImports
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.ResultRow
import io.ktor.server.routing.get
```

- [ ] **Step 2: Update `createEventFromParsed` signature**

Change the private function signature from:

```kotlin
private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String) {
```

To:

```kotlin
private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String, importId: String) {
```

And inside the `Events.insert { ... }` block, add after `it[syncedAt] = null`:

```kotlin
it[statementImportId] = importId
```

- [ ] **Step 3: Update `POST /api/statements/import` handler**

Replace the entire `post("/api/statements/import") { ... }` block with:

```kotlin
post("/api/statements/import") {
    val uid = call.userId()
    val decision = call.receive<ImportDecision>()

    val accountExists = dbQuery {
        Accounts.selectAll()
            .where { (Accounts.id eq decision.accountId) and (Accounts.userId eq uid) }
            .count() > 0
    }
    if (!accountExists) {
        call.respond(HttpStatusCode.NotFound, "Account not found")
        return@post
    }

    val importId = "si_${UUID.randomUUID()}"
    var importedCount = 0
    var reconciledCount = 0

    for (tx in decision.imports) {
        createEventFromParsed(tx, decision.accountId, uid, importId)
        importedCount++
    }

    for (dec in decision.reconciliations) {
        if (dec.confirm) {
            val existingEvent = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq dec.existingEventId) and (Events.userId eq uid) }
                    .firstOrNull()?.let {
                        Triple(it[Events.category], it[Events.description], it[Events.merchant])
                    }
            }

            if (existingEvent != null) {
                val (existCat, existDesc, existMerchant) = existingEvent
                val finalCategory    = if (dec.categorySource    == FieldSource.STATEMENT) dec.parsed.category    else existCat
                val finalDescription = if (dec.descriptionSource == FieldSource.STATEMENT) dec.parsed.description else existDesc
                val finalMerchant    = if (dec.merchantSource    == FieldSource.STATEMENT) dec.parsed.merchant    else existMerchant

                dbQuery {
                    Events.update({ (Events.id eq dec.existingEventId) and (Events.userId eq uid) }) {
                        it[category]          = finalCategory
                        it[description]       = finalDescription
                        it[merchant]          = finalMerchant
                        it[statementImportId] = importId
                    }
                }

                if (dec.parsed.category != existCat) {
                    Stores.merchantRules.saveRule(uid, MerchantRule(
                        merchantPattern = dec.parsed.merchant.lowercase().trim(),
                        category = finalCategory,
                    ))
                }
                reconciledCount++
            }
        } else {
            createEventFromParsed(dec.parsed, decision.accountId, uid, importId)
            importedCount++
        }
    }

    dbQuery {
        StatementImports.insert {
            it[id]             = importId
            it[userId]         = uid
            it[accountId]      = decision.accountId
            it[bankName]       = decision.bankName
            it[period]         = decision.period
            it[importedAt]     = System.currentTimeMillis()
            it[StatementImports.importedCount]   = importedCount
            it[StatementImports.reconciledCount] = reconciledCount
        }
    }

    call.respond(HttpStatusCode.OK, mapOf("imported" to importedCount + reconciledCount))
}
```

- [ ] **Step 4: Add `GET /api/statements/imports` endpoint**

Add inside `fun Route.statementRoutes()`, after the import endpoint:

```kotlin
get("/api/statements/imports") {
    val uid = call.userId()
    val imports = dbQuery {
        StatementImports.selectAll()
            .where { StatementImports.userId eq uid }
            .orderBy(StatementImports.importedAt, SortOrder.DESC)
            .map { rowToStatementImport(it) }
    }
    call.respond(imports)
}
```

- [ ] **Step 5: Add `GET /api/statements/imports/{id}` endpoint**

Add inside `fun Route.statementRoutes()`, after the list endpoint:

```kotlin
get("/api/statements/imports/{id}") {
    val uid = call.userId()
    val importId = call.parameters["id"] ?: run {
        call.respond(HttpStatusCode.BadRequest, "Missing id")
        return@get
    }

    val importRow = dbQuery {
        StatementImports.selectAll()
            .where { (StatementImports.id eq importId) and (StatementImports.userId eq uid) }
            .firstOrNull()
    }
    if (importRow == null) {
        call.respond(HttpStatusCode.NotFound, "Import not found")
        return@get
    }

    val events = dbQuery {
        Events.selectAll()
            .where { Events.statementImportId eq importId }
            .map { row ->
                FinancialEvent(
                    id                   = row[Events.id],
                    accountId            = row[Events.accountId],
                    type                 = TransactionType.valueOf(row[Events.type]),
                    amount               = row[Events.amount],
                    category             = row[Events.category],
                    description          = row[Events.description],
                    merchant             = row[Events.merchant],
                    timestamp            = row[Events.timestamp],
                    source               = EventSource.valueOf(row[Events.eventSource]),
                    rawPayload           = row[Events.rawPayload],
                    reconciliationStatus = ReconciliationStatus.valueOf(row[Events.reconciliationStatus]),
                    syncedAt             = row[Events.syncedAt],
                )
            }
    }

    call.respond(StatementImportDetail(rowToStatementImport(importRow), events))
}
```

- [ ] **Step 6: Add `rowToStatementImport` helper**

Add as a private function at the bottom of `StatementRoutes.kt`, alongside `createEventFromParsed` and `monthName`:

```kotlin
private fun rowToStatementImport(row: ResultRow) = StatementImport(
    id             = row[StatementImports.id],
    accountId      = row[StatementImports.accountId],
    bankName       = row[StatementImports.bankName],
    period         = row[StatementImports.period],
    importedAt     = row[StatementImports.importedAt],
    importedCount  = row[StatementImports.importedCount],
    reconciledCount = row[StatementImports.reconciledCount],
)
```

- [ ] **Step 7: Register GET routes in Routing.kt**

The existing `statementRoutes()` call in `plugins/Routing.kt` already registers all endpoints — the GET routes are defined inside the same `fun Route.statementRoutes()` extension, so no change to `Routing.kt` is needed.

- [ ] **Step 8: Verify server compiles and existing tests pass**

```bash
./gradlew :server:compileKotlin 2>&1 | tail -10
./gradlew :server:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` for both; all existing `StatementParserTest` and `ClaudeStatementParserTest` tests pass.

- [ ] **Step 9: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: record import batches + GET history endpoints"
```

---

### Task 4: Repository layer — add `getStatementImports` and `getStatementImportDetail`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- Modify: `shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`
- Modify: `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`

Context: `WalletRepository` is the interface; `WalletRepositoryImpl` calls the Ktor HTTP client; `LocalRepository` delegates statement operations to remote; `NoOpRepository` is the test stub.

- [ ] **Step 1: Add method signatures to `WalletRepository`**

Open `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`.

Add these two imports at the top (alongside existing imports):

```kotlin
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
```

Add these two methods to the interface, after `importStatement`:

```kotlin
suspend fun getStatementImports(): List<StatementImport>
suspend fun getStatementImportDetail(id: String): StatementImportDetail
```

- [ ] **Step 2: Implement in `WalletRepositoryImpl`**

Open `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`.

Add these two imports:

```kotlin
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
```

Add these two overrides after the existing `importStatement` override:

```kotlin
override suspend fun getStatementImports(): List<StatementImport> =
    client.get("$baseUrl/api/statements/imports").body()

override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
    client.get("$baseUrl/api/statements/imports/$id").body()
```

- [ ] **Step 3: Delegate in `LocalRepository`**

Open `shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`.

Add these two imports:

```kotlin
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
```

Add these two overrides near the bottom (alongside `uploadStatement` and `importStatement`):

```kotlin
override suspend fun getStatementImports(): List<StatementImport> =
    remote.getStatementImports()

override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
    remote.getStatementImportDetail(id)
```

- [ ] **Step 4: Stub in `NoOpRepository`**

Open `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`.

Add these two imports:

```kotlin
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
```

Add these two overrides after `importStatement`:

```kotlin
override suspend fun getStatementImports(): List<StatementImport> = emptyList()
override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
    StatementImportDetail(StatementImport("", "", "", "", 0L, 0, 0), emptyList())
```

- [ ] **Step 5: Verify shared module compiles and tests pass**

```bash
./gradlew :shared:compileKotlinJvm :shared:jvmTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add \
  shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt \
  shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt \
  shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt \
  shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: repository methods for import history"
```

---

### Task 5: Navigation + App.kt — add `ImportDetail` screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`

- [ ] **Step 1: Add `ImportDetail` to Navigation.kt**

Open `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`.

Add after `data class StatementReview(val resultJson: String) : Screen()`:

```kotlin
data class ImportDetail(val importId: String) : Screen()
```

- [ ] **Step 2: Add case in App.kt**

Open `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`.

Add the import at the top:

```kotlin
import com.jvillada.movi.ui.extractos.ImportDetailScreen
```

In the `when (currentScreen)` block, add after the `is Screen.StatementReview` case:

```kotlin
is Screen.ImportDetail -> ImportDetailScreen(
    onNavigate = navigate,
    importId = currentScreen.importId,
)
```

- [ ] **Step 3: Verify composeApp compiles**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | tail -15
```

Expected: error about `ImportDetailScreen` not found — correct, we haven't created it yet. The compile error should be only that.

- [ ] **Step 4: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add \
  composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt \
  composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: ImportDetail navigation"
```

---

### Task 6: ImportDetailScreen — new composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ImportDetailScreen.kt`

Context: Shows a header (bank, period, date, counts) and a `LazyColumn` of events that belonged to this import batch. Events are read-only. Uses the `MinCard` + `MinCardVariant.Elevated` pattern from `AccountDetailScreen.kt`. Date is formatted with `epochToDate()` pattern using `kotlinx.datetime`. The screen loads on `LaunchedEffect(importId)`.

- [ ] **Step 1: Create the file**

Create `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ImportDetailScreen.kt`:

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ImportDetailScreen(onNavigate: (Screen) -> Unit, importId: String) {
    var detail by remember { mutableStateOf<StatementImportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(importId) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getStatementImportDetail(importId) }
            .onSuccess { detail = it; loading = false }
            .onFailure { t ->
                if (t is CancellationException) throw t
                loading = false
                error = "No pude cargar el detalle: ${t.message ?: "error"}"
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowBackIosNew, "Volver",
                tint = MinTextDim,
                modifier = Modifier.size(20.dp).clickable { onNavigate(Screen.Extractos) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Detalle de importación", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        when {
            loading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MinPrimary,
                trackColor = MinSurfaceContainerHigh,
            )
            error != null -> Text(
                error!!,
                fontSize = 13.sp, color = MinExpense,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
            detail != null -> ImportDetailContent(detail = detail!!)
        }
    }
}

@Composable
private fun ImportDetailContent(detail: StatementImportDetail) {
    val imp = detail.import
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            ImportSummaryHeader(imp)
        }

        if (detail.events.isEmpty()) {
            item {
                Text(
                    "No se encontraron movimientos",
                    fontSize = 13.sp, color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                )
            }
        } else {
            item {
                Text(
                    "MOVIMIENTOS",
                    fontSize = 11.sp, color = MinTextDim,
                    letterSpacing = 0.8.sp,
                )
            }
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    detail.events.forEachIndexed { i, event ->
                        ImportEventRow(event)
                        if (i < detail.events.size - 1) Hairline()
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ImportSummaryHeader(imp: StatementImport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${imp.bankName.uppercase()} · ${imp.period.uppercase()}",
            fontSize = 11.sp, color = MinTextDim, letterSpacing = 0.8.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MinText,
            )
            Text(
                epochToShortDate(imp.importedAt),
                fontSize = 12.sp, color = MinTextMute,
            )
        }
    }
}

@Composable
private fun ImportEventRow(event: FinancialEvent) {
    val isIncome = event.type == TransactionType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.description.ifBlank { event.merchant ?: "Sin descripción" },
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(event.category, fontSize = 12.sp, color = MinTextMute)
                StatusDot(MinTextFaint, 2.dp)
                Text(
                    epochToShortDate(event.timestamp),
                    fontSize = 11.sp, color = MinTextMute,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        MonoText(
            text = "${if (isIncome) "+" else "−"}${formatCOP(event.amount)}",
            fontSize = 14f,
            color = if (isIncome) MinIncome else MinText,
        )
    }
}

private fun epochToShortDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
```

- [ ] **Step 2: Verify composeApp compiles**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ImportDetailScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: ImportDetailScreen"
```

---

### Task 7: ExtractosScreen — add import history section

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt`

Context: The current ExtractosScreen uses a `Column(modifier = Modifier.weight(1f))` for the scrollable area. We convert it to a `LazyColumn` so the import history list can scroll together with the rest of the content. Each existing block becomes an `item {}`. The history section is appended at the bottom: a label + list of `ImportCard` composables. `LaunchedEffect(Unit)` loads imports on every screen entry (navigating back recomposes from scratch).

- [ ] **Step 1: Update imports in ExtractosScreen.kt**

Open `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt`.

Add these imports (alongside existing ones):

```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontFamily
import com.jvillada.movi.shared.model.StatementImport
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
```

- [ ] **Step 2: Add state for import history**

Inside `ExtractosScreen`, after the existing `var error` state line, add:

```kotlin
var imports by remember { mutableStateOf<List<StatementImport>>(emptyList()) }
var importsError by remember { mutableStateOf<String?>(null) }
```

- [ ] **Step 3: Add `LaunchedEffect` to load history**

After the `rememberFilePicker { ... }` block, add:

```kotlin
LaunchedEffect(Unit) {
    runCatching { Repositories.wallets.getStatementImports() }
        .onSuccess { imports = it }
        .onFailure { t ->
            if (t is CancellationException) throw t
            importsError = "No pude cargar el historial"
        }
}
```

- [ ] **Step 4: Replace inner `Column` with `LazyColumn`**

Replace the entire inner `Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp))` block with a `LazyColumn`. The content is unchanged except each piece becomes an `item {}` block, and the history section is appended:

```kotlin
LazyColumn(
    modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp),
) {
    // Info banner
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainer)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.Description, null,
                tint = MinPrimary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fuente de verdad", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                Text(
                    "Los extractos bancarios reconcilian automáticamente tus movimientos. Sube PDF, CSV o XLS de cualquier banco colombiano.",
                    fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp,
                )
            }
        }
    }

    item { Spacer(Modifier.height(24.dp)) }

    // Upload zone or progress
    item {
        if (uploading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(uploadingFileName, fontSize = 12.sp, color = MinText)
                    Text("Parseando…", fontSize = 11.sp, color = MinPrimary)
                }
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MinPrimary,
                    trackColor = MinSurfaceContainerHigh,
                )
                Text("Claude está leyendo el extracto", fontSize = 11.sp, color = MinTextMute)
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                    .clickable { launchPicker() },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.UploadFile, null, tint = MinPrimary, modifier = Modifier.size(40.dp))
                    Text("Subir extracto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                    Text("PDF · CSV · XLS", fontSize = 12.sp, color = MinTextMute)
                }
            }
        }
    }

    // Error
    error?.let {
        item {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 12.sp, color = MinExpense, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    item { Spacer(Modifier.height(24.dp)) }

    // Supported banks
    item {
        Text(
            "Bancos soportados",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinTextDim,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    listOf("Bancolombia", "Nequi", "Davivienda", "BBVA", "Falabella", "Colpatria", "Banco de Bogotá").chunked(3).forEach { row ->
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { banco ->
                    Text(
                        banco, fontSize = 11.sp, color = MinTextMute,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MinSurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

    // Import history section
    item { Spacer(Modifier.height(28.dp)) }
    item {
        Text(
            "IMPORTACIONES ANTERIORES",
            fontSize = 11.sp, color = MinTextDim, letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    when {
        importsError != null -> item {
            Text(importsError!!, fontSize = 12.sp, color = MinExpense)
        }
        imports.isEmpty() -> item {
            Text(
                "Aún no hay importaciones",
                fontSize = 13.sp, color = MinTextMute,
            )
        }
        else -> items(imports, key = { it.id }) { imp ->
            ImportCard(imp) { onNavigate(Screen.ImportDetail(imp.id)) }
            Spacer(Modifier.height(8.dp))
        }
    }

    item { Spacer(Modifier.height(16.dp)) }
}
```

- [ ] **Step 5: Add `ImportCard` composable**

Add as a private function at the bottom of `ExtractosScreen.kt`:

```kotlin
@Composable
private fun ImportCard(imp: StatementImport, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MinSurfaceContainerLow)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${imp.bankName} · ${imp.period}",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText,
            )
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                fontSize = 11.sp, color = MinTextMute,
            )
        }
        Text(
            epochToShortDate(imp.importedAt),
            fontSize = 11.sp, color = MinTextMute,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun epochToShortDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
```

- [ ] **Step 6: Verify composeApp compiles**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run all tests**

```bash
./gradlew :shared:jvmTest :server:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Build and install APK on emulator**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -10
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

Navigate to Extractos screen. Verify:
1. "IMPORTACIONES ANTERIORES" section appears below the banks list
2. "Aún no hay importaciones" shows when history is empty
3. After an import, tapping the card navigates to `ImportDetailScreen`
4. `ImportDetailScreen` shows the bank/period header and event list

- [ ] **Step 9: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: import history section in ExtractosScreen"
```
