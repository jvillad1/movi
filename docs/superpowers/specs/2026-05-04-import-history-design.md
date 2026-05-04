# Import History Design Spec
**Date:** 2026-05-04
**Status:** Approved

---

## Overview

Add an import history section to `ExtractosScreen` showing past statement imports with a drill-down screen that lists the events each import created or reconciled. Read-only. No rollback.

---

## Architecture

### Data flow

```
ExtractosScreen loads
  → GET /api/statements/imports
  → returns List<StatementImport> ordered by importedAt DESC
  → renders "IMPORTACIONES ANTERIORES" section

User taps an import
  → navigate to ImportDetailScreen(importId)
  → GET /api/statements/imports/{id}
  → returns StatementImportDetail { import, events }
  → renders header + event list (read-only)

POST /api/statements/import (existing endpoint — modified)
  → inserts StatementImport row first (generates UUID)
  → passes statementImportId to each created/updated event
```

---

## Shared Models

**File:** `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt` (append to existing)

```kotlin
@Serializable
data class StatementImport(
    val id: String,
    val accountId: String,
    val bankName: String,
    val period: String,
    val importedAt: Long,          // epoch millis
    val importedCount: Int,
    val reconciledCount: Int,
)

@Serializable
data class StatementImportDetail(
    val import: StatementImport,
    val events: List<FinancialEvent>,   // new + reconciled events from this import
)
```

---

## Server

### Database schema changes — `server/src/main/kotlin/com/jvillada/movi/server/database/DatabaseFactory.kt`

New Exposed table object `StatementImports`:

```kotlin
object StatementImports : Table("StatementImports") {
    val id           = varchar("id", 36)
    val userId       = varchar("userId", 36)
    val accountId    = varchar("accountId", 36) references Accounts.id
    val bankName     = varchar("bankName", 100)
    val period       = varchar("period", 50)
    val importedAt   = long("importedAt")
    val importedCount    = integer("importedCount")
    val reconciledCount  = integer("reconciledCount")
    override val primaryKey = PrimaryKey(id)
}
```

New nullable column on `Events`:

```kotlin
object Events : Table("Events") {
    // existing columns ...
    val statementImportId = varchar("statementImportId", 36).nullable()
        .references(StatementImports.id)
}
```

`SchemaUtils.createMissingTablesAndColumns()` in `DatabaseFactory.init()` handles both the new table and the new column automatically.

### Modified file — `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`

**`POST /api/statements/import` — additions:**

Before creating/updating events:
1. Generate `importId = UUID.randomUUID().toString()`
2. Insert into `StatementImports` (importedCount = imports.size, reconciledCount = confirmed reconciliations count)
3. Pass `importId` when inserting new events and when updating reconciled events

**Two new endpoints (same file):**

`GET /api/statements/imports`
- Auth: JWT, extract `userId`
- Query: `StatementImports.selectAll().where { StatementImports.userId eq uid }.orderBy(StatementImports.importedAt, SortOrder.DESC)`
- Map rows to `StatementImport`; return as JSON list

`GET /api/statements/imports/{id}`
- Auth: JWT, extract `userId`
- Validate import belongs to user (404 if not found or wrong user)
- Query events: `Events.selectAll().where { Events.statementImportId eq importId }`
- Map to `FinancialEvent` list (reuse existing `rowToEvent()`)
- Return `StatementImportDetail`

### Modified file — `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt`

No change needed — `statementRoutes()` is already registered.

---

## Shared Repository

**`WalletRepository.kt`** — add two methods:

```kotlin
suspend fun getStatementImports(): List<StatementImport>
suspend fun getStatementImportDetail(id: String): StatementImportDetail
```

**`WalletRepositoryImpl.kt`**:

```kotlin
override suspend fun getStatementImports(): List<StatementImport> =
    client.get("$baseUrl/api/statements/imports").body()

override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
    client.get("$baseUrl/api/statements/imports/$id").body()
```

**`LocalRepository.kt`**:

```kotlin
override suspend fun getStatementImports() = remote.getStatementImports()
override suspend fun getStatementImportDetail(id: String) = remote.getStatementImportDetail(id)
```

**`NoOpRepository.kt`**:

```kotlin
override suspend fun getStatementImports() = emptyList<StatementImport>()
override suspend fun getStatementImportDetail(id: String) =
    StatementImportDetail(StatementImport("", "", "", "", 0L, 0, 0), emptyList())
```

---

## Client — Android

### Navigation — `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`

```kotlin
data class ImportDetail(val importId: String) : Screen()
```

### App.kt — add case

```kotlin
is Screen.ImportDetail -> ImportDetailScreen(
    onNavigate = navigate,
    importId = currentScreen.importId,
)
```

### ExtractosScreen — `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt`

Add state:
```kotlin
var imports: List<StatementImport> by remember { mutableStateOf(emptyList()) }
var importsError: String? by remember { mutableStateOf(null) }
```

Add `LaunchedEffect(Unit)` to load imports on screen entry. Navigating back from `ImportDetailScreen` recomposes `ExtractosScreen` from scratch so imports are always fresh.

Add "IMPORTACIONES ANTERIORES" section below the upload zone in the `LazyColumn`:
- Section label (same style as other section headers)
- `items(imports)` — each renders an `ImportCard` composable showing: bank name, period, formatted `importedAt` date, "N importadas · M reconciliadas"
- On tap: `onNavigate(Screen.ImportDetail(it.id))`
- Empty state: "Aún no hay importaciones" in muted text
- Error state: inline error message

### ImportDetailScreen — `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ImportDetailScreen.kt`

State:
```kotlin
var detail: StatementImportDetail? by remember { mutableStateOf(null) }
var loading: Boolean by remember { mutableStateOf(true) }
var error: String? by remember { mutableStateOf(null) }
```

`LaunchedEffect(importId)` loads `getStatementImportDetail(importId)`.

Layout (`LazyColumn`):
1. **Header** — bank name, period, formatted date, "N importadas · M reconciliadas"
2. **Events list** — each event as a read-only `MinCard`: merchant/description, category chip, amount (colored by type), date
3. **Empty state** if events list is empty: "No se encontraron movimientos"
4. **Back** — top app bar with back arrow navigating to previous screen

---

## Out of scope

- Rollback / delete an import
- Filtering/searching history
- Re-parsing a past import
- iOS or web (same server endpoint works when those pickers are added)
