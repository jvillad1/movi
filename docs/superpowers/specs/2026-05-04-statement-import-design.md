# Statement Import Design Spec
**Date:** 2026-05-04
**Status:** Approved

---

## Overview

Build a bank statement import pipeline that lets users upload PDF, CSV, or XLS extractos from Colombian banks. Claude parses the text into structured transactions, the server detects potential duplicates against existing events, and the client presents a bulk-review screen where the user approves new transactions and reconciles matches field-by-field. Corrections are stored as merchant rules to seed future parsing.

**Platform scope:** Android for validation; iOS uses the same server endpoint (picker deferred to next plan).

---

## Architecture

### Data flow

```
Android file picker
  → multipart POST /api/statements/upload
  → server: text extraction (PDFBox / Apache POI / CSV stdlib)
  → server: load user merchant rules from MerchantRulesStore
  → server: Claude prompt = extracted text + merchant rules as few-shot context
  → Claude returns: List<ParsedTransaction>
  → server: fuzzy-match against existing FinancialEvents (amount + date ±2 days)
  → server returns: StatementParseResult {
        newTransactions: List<ParsedTransaction>,
        matches: List<ReconciliationMatch>
      }
Client: StatementReviewScreen
  → new transactions: checkboxes (import or skip)
  → matches: reconciliation cards with field-level source selection
User confirms
  → POST /api/statements/import with ImportDecision
  → server: creates FinancialEvents for new, updates existing for reconciled
  → server: persists merchant rule corrections to MerchantRulesStore
```

---

## Shared Models

**File:** `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt`

```kotlin
@Serializable
data class ParsedTransaction(
    val id: String,               // session-scoped temp ID
    val date: String,             // "2025-05-28"
    val merchant: String,
    val amount: Long,             // COP pesos
    val type: TransactionType,
    val category: String,         // Claude suggestion
    val description: String,
    val rawText: String,          // original line from statement
)

@Serializable
data class ReconciliationMatch(
    val parsed: ParsedTransaction,
    val existingEventId: String,
    val existingEvent: FinancialEvent,
    val matchConfidence: Float,   // 0.0–1.0
)

@Serializable
data class StatementParseResult(
    val statementId: String,
    val bankName: String,
    val period: String,           // "Mayo 2025"
    val newTransactions: List<ParsedTransaction>,
    val matches: List<ReconciliationMatch>,
)

@Serializable
data class ImportDecision(
    val statementId: String,
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,    // ParsedTransaction IDs to skip
)

@Serializable
data class ReconciliationDecision(
    val parsedId: String,
    val existingEventId: String,
    val confirm: Boolean,         // false = treat as new transaction instead
    val categorySource: FieldSource,
    val descriptionSource: FieldSource,
    val merchantSource: FieldSource,
)

@Serializable
enum class FieldSource { MANUAL, STATEMENT }

@Serializable
data class MerchantRule(
    val merchantPattern: String,  // normalized (lowercase, trimmed)
    val category: String,
)
```

---

## Server

### New dependencies — `server/build.gradle.kts`

```kotlin
implementation("org.apache.pdfbox:pdfbox:3.0.2")
implementation("org.apache.poi:poi-ooxml:5.3.0")       // XLS + XLSX
```

Multipart support is already present via `ktor-server-host-common`. `ktor-server-content-negotiation` is already in the server (required by the Serialization plugin) — do not add it again.

### New files

#### `server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt`

Extracts plain text from the uploaded file based on MIME type or extension:

- **PDF** → `PDDocument.load(bytes).use { PDFTextStripper().getText(it) }`
- **CSV** → `bytes.toString(Charsets.UTF_8)` (already text)
- **XLS/XLSX** → Apache POI `WorkbookFactory.create(bytes.inputStream())`, iterate rows, join cells with tab separator

Returns `String` (raw extracted text) and detected `String` bank name (heuristic from filename or first lines).

#### `server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt`

Calls Anthropic Claude with a structured prompt:

**System prompt:**
```
Sos un parser de extractos bancarios colombianos. Tu trabajo es extraer todas las transacciones de un extracto bancario y devolver JSON válido.

Reglas del usuario (aprendidas de correcciones anteriores):
{merchant_rules_json}

Devolvé ÚNICAMENTE un array JSON con este formato exacto, sin explicaciones:
[{"date":"YYYY-MM-DD","merchant":"nombre limpio","amount":123456,"type":"EXPENSE|INCOME","category":"categoría","description":"descripción corta"}]

- amount: entero en pesos colombianos (sin puntos ni comas)
- type: EXPENSE para débitos/compras/pagos, INCOME para créditos/abonos/nómina
- Aplicá las reglas del usuario cuando el merchant coincida
```

Uses `claude-opus-4-7` with `maxTokens = 4096`. Returns `List<ParsedTransaction>` parsed from Claude's JSON response.

#### `server/src/main/kotlin/com/jvillada/movi/server/storage/MerchantRulesStore.kt`

`JsonListStore<MerchantRule>` keyed per user (file: `movi-data/merchant-rules-{userId}.json`). Exposes:
- `getRules(userId): List<MerchantRule>`
- `saveRule(userId, rule: MerchantRule)`

#### `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`

Two endpoints, both JWT-authenticated:

**`POST /api/statements/upload`** — multipart, field name `file`:
1. Read bytes + filename from multipart part
2. Extract text via `StatementParser`
3. Load `merchantRules` for `userId`
4. Call `ClaudeStatementParser.parse(text, merchantRules)`
5. Fuzzy-match results against existing `FinancialEvent`s for `userId`:
   - Match condition: `abs(parsed.amount - existing.amount) == 0` AND `abs(parsedDateEpoch - existing.timestamp) <= 2 days`
   - Assign `matchConfidence = 0.95` when amount matches AND date is the same day, `0.7` when amount matches AND date is within ±2 days (but not the same day)
6. Return `StatementParseResult`

**`POST /api/statements/import`** — body: `ImportDecision`:
1. For each `ParsedTransaction` in `imports`: call existing `postEvent()` logic
2. For each `ReconciliationDecision` with `confirm = true`: update the existing `FinancialEvent` fields per `FieldSource` selection
3. For each `ReconciliationDecision` with `confirm = false`: create new event from `parsed`
4. For each `ReconciliationDecision` where `categorySource == STATEMENT` and `parsed.category != existingEvent.category`: save `MerchantRule(merchantPattern = parsed.merchant.lowercase(), category = parsed.category)`. If `categorySource == MANUAL`, the existing event's category was preferred — save `MerchantRule(merchantPattern = parsed.merchant.lowercase(), category = existingEvent.category)`.
5. Return `HttpStatusCode.OK` with count of imported events

### Modified files

- `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt` — add `statementRoutes()`

---

## Shared Repository

**`WalletRepository.kt`** — add two methods:

```kotlin
suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult
suspend fun importStatement(decision: ImportDecision)
```

**`WalletRepositoryImpl.kt`** — implement using Ktor multipart:

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
    client.post("$baseUrl/api/statements/upload") {
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=$fileName")
                append(HttpHeaders.ContentType, mimeType)
            })
        }))
    }.body()

override suspend fun importStatement(decision: ImportDecision) {
    client.post("$baseUrl/api/statements/import") { setBody(decision) }
}
```

**`LocalRepository.kt`** — delegate both methods to remote (statement operations are server-side):

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String) =
    remote.uploadStatement(fileName, bytes, mimeType)

override suspend fun importStatement(decision: ImportDecision) =
    remote.importStatement(decision)
```

**`NoOpRepository.kt`** (test stub):

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String) =
    StatementParseResult("", "", "", emptyList(), emptyList())
override suspend fun importStatement(decision: ImportDecision) {}
```

---

## Client — Android

### File picker — `composeApp/src/androidMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`

```kotlin
@Composable
actual fun rememberFilePicker(onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ctx = LocalContext.current
        val name = uri.lastPathSegment ?: "statement"
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        onResult(name, bytes, mime)
    }
    return { launcher.launch("*/*") }
}
```

**`composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`** — expect declaration:

```kotlin
@Composable
expect fun rememberFilePicker(onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit): () -> Unit
```

**`composeApp/src/iosMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`** — stub for now:

```kotlin
@Composable
actual fun rememberFilePicker(onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit): () -> Unit = { }
```

**`composeApp/src/wasmJsMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`** — stub:

```kotlin
@Composable
actual fun rememberFilePicker(onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit): () -> Unit = { }
```

### ExtractosScreen — updated

Replace the placeholder upload zone with:
1. `rememberFilePicker` hook
2. Upload zone tap → launch picker
3. On file selected: set `uploading = true`, call `Repositories.wallets.uploadStatement(...)`, navigate to `Screen.StatementReview(result)`
4. Loading state: progress bar + "Claude está leyendo el extracto" label
5. Error state: inline error message

Add `Screen.StatementReview` to `Navigation.kt`:
```kotlin
data class StatementReview(val resultJson: String) : Screen()
```
The result is JSON-serialized `StatementParseResult` passed as a navigation argument.

### StatementReviewScreen

**File:** `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt`

State:
```kotlin
var result: StatementParseResult           // from nav arg, deserialized
var selectedIds: Set<String>               // ParsedTransaction IDs checked for import
var reconciliations: Map<String, ReconciliationDecision>  // parsedId → decision
var working: Boolean
var error: String?
```

Layout (LazyColumn):
1. **Header** — bank name, period, "X nuevas · Y coincidencias"
2. **Matches section** (if any) — each `ReconciliationMatch` shows a reconciliation card (see below)
3. **New transactions section** — each `ParsedTransaction` with checkbox + category chip
4. **"Seleccionar todas" / "Deseleccionar"** link
5. **Sticky bottom bar** — "Importar N seleccionadas" button (disabled if 0 selected and 0 reconciliations confirmed)

**Reconciliation card** (inline, not a bottom sheet):
- Amber border, "POSIBLE DUPLICADO" badge
- Two columns: INGRESO MANUAL | EXTRACTO
- Rows: Comercio, Categoría, Descripción (only fields that differ are shown with source toggle)
- Identical fields shown as single read-only row
- "Confirmar reconciliación" / "No son el mismo" actions
- On confirm: stores `ReconciliationDecision` in state
- On "no son el mismo": treats `parsed` as new transaction, added to `selectedIds`

**Import action:**
```kotlin
fun import() {
    working = true
    coroutine.launch {
        runCatching {
            val decision = ImportDecision(
                statementId = result.statementId,
                imports = result.newTransactions.filter { it.id in selectedIds },
                reconciliations = reconciliations.values.toList(),
                skipped = result.newTransactions.map { it.id }.filter { it !in selectedIds },
            )
            Repositories.wallets.importStatement(decision)
        }.onSuccess {
            working = false
            onNavigate(Screen.Transactions)
        }.onFailure {
            working = false
            error = "No pude importar: ${it.message ?: "error"}"
        }
    }
}
```

---

## Out of scope

- iOS file picker implementation (stub only — same server endpoint works when iOS picker is added)
- Advanced learning: auto-injecting merchant rules into Claude prompt (rules are stored, injection comes in next plan)
- Import history screen
- Re-parsing a previously uploaded statement
- Web upload
- Conflict resolution for multiple concurrent imports
- WorkManager background import
