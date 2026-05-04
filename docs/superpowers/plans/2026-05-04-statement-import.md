# Statement Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full bank statement import pipeline — Android file picker → multipart upload → PDFBox/POI/CSV text extraction → Claude parsing with merchant rules → fuzzy duplicate detection → bulk review + field-level reconciliation → event creation.

**Architecture:** The server handles all parsing (PDFBox, Apache POI, Claude claude-opus-4-7) and returns a `StatementParseResult`. The client presents a review screen where the user approves new transactions and reconciles detected duplicates field-by-field. Corrections are persisted as `MerchantRule` records to seed future Claude prompts.

**Tech Stack:** Kotlin Multiplatform, Ktor server (Exposed/PostgreSQL), PDFBox 3.0.2, Apache POI 5.3.0, Anthropic Java SDK, Jetpack Compose, `JsonListStore` for merchant rules, `expect`/`actual` for Android file picker.

---

## File Map

| Status | Path | Role |
|--------|------|------|
| Create | `shared/src/commonMain/.../model/Statement.kt` | All statement-domain serializable models |
| Modify | `gradle/libs.versions.toml` | Add pdfbox + poi version entries |
| Modify | `server/build.gradle.kts` | Add pdfbox + poi dependencies |
| Create | `server/src/main/.../parsing/StatementParser.kt` | Text extraction (PDF/CSV/XLS) |
| Create | `server/src/test/.../parsing/StatementParserTest.kt` | Unit test CSV extraction |
| Create | `server/src/main/.../storage/MerchantRulesStore.kt` | Per-user merchant rules JSON store |
| Modify | `server/src/main/.../storage/Stores.kt` | Add `merchantRules` singleton |
| Create | `server/src/main/.../parsing/ClaudeStatementParser.kt` | Claude API parsing |
| Create | `server/src/test/.../parsing/ClaudeStatementParserTest.kt` | Unit test JSON parsing |
| Create | `server/src/main/.../routes/StatementRoutes.kt` | Upload + import endpoints |
| Modify | `server/src/main/.../plugins/Routing.kt` | Register statementRoutes() |
| Modify | `shared/src/commonMain/.../repository/WalletRepository.kt` | Add uploadStatement + importStatement |
| Modify | `shared/src/commonMain/.../repository/WalletRepositoryImpl.kt` | Implement with multipart |
| Modify | `shared/src/nonWasmMain/.../repository/LocalRepository.kt` | Delegate both to remote |
| Modify | `shared/src/jvmTest/.../repository/NoOpRepository.kt` | Add stubs |
| Create | `composeApp/src/commonMain/.../ui/extractos/FilePicker.kt` | expect declaration |
| Create | `composeApp/src/androidMain/.../ui/extractos/FilePicker.kt` | Android actual |
| Create | `composeApp/src/iosMain/.../ui/extractos/FilePicker.kt` | iOS stub |
| Create | `composeApp/src/wasmJsMain/.../ui/extractos/FilePicker.kt` | wasmJs stub |
| Modify | `composeApp/src/commonMain/.../ui/Navigation.kt` | Add Screen.StatementReview |
| Modify | `composeApp/src/commonMain/.../App.kt` | Route StatementReviewScreen |
| Create | `composeApp/src/commonMain/.../ui/extractos/StatementReviewScreen.kt` | Bulk review + reconciliation UI |
| Modify | `composeApp/src/commonMain/.../ui/extractos/ExtractosScreen.kt` | Replace placeholder with real upload zone |

---

## Task 1: Shared models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt`

**Context:** `TransactionType` is defined in `shared/src/commonMain/.../model/Wallet.kt`. All shared models must be `@Serializable`. Two spec deviations vs the design doc: (1) `ReconciliationDecision` includes `parsed: ParsedTransaction` so the server has full data when processing `confirm=false` and saving merchant rules; (2) `ImportDecision` includes `accountId: String` so the server knows which account to associate new events with.

- [ ] **Step 1: Write the model file**

```kotlin
package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedTransaction(
    val id: String,           // UUID, session-scoped
    val date: String,         // "2025-05-28"
    val merchant: String,
    val amount: Long,         // COP pesos, always positive
    val type: TransactionType,
    val category: String,
    val description: String,
    val rawText: String,
)

@Serializable
data class ReconciliationMatch(
    val parsed: ParsedTransaction,
    val existingEventId: String,
    val existingEvent: FinancialEvent,
    val matchConfidence: Float,
)

@Serializable
data class StatementParseResult(
    val statementId: String,
    val bankName: String,
    val period: String,
    val newTransactions: List<ParsedTransaction>,
    val matches: List<ReconciliationMatch>,
)

@Serializable
data class ReconciliationDecision(
    val parsedId: String,
    val existingEventId: String,
    val confirm: Boolean,
    val categorySource: FieldSource,
    val descriptionSource: FieldSource,
    val merchantSource: FieldSource,
    val parsed: ParsedTransaction,
)

@Serializable
enum class FieldSource { MANUAL, STATEMENT }

@Serializable
data class ImportDecision(
    val statementId: String,
    val accountId: String,
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,
)

@Serializable
data class MerchantRule(
    val merchantPattern: String,
    val category: String,
)
```

- [ ] **Step 2: Verify shared module compiles**

```bash
./gradlew :shared:compileKotlinJvm
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/model/Statement.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: shared statement import models"
```

---

## Task 2: Server build dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`

- [ ] **Step 1: Add version entries to `gradle/libs.versions.toml`**

In the `[versions]` section, add after `postgresql = "42.7.3"`:
```toml
pdfbox = "3.0.2"
poi = "5.3.0"
```

In the `[libraries]` section, add after the `postgresql-driver` line:
```toml
pdfbox = { module = "org.apache.pdfbox:pdfbox", version.ref = "pdfbox" }
poi-ooxml = { module = "org.apache.poi:poi-ooxml", version.ref = "poi" }
```

- [ ] **Step 2: Add to `server/build.gradle.kts`**

In the `dependencies { }` block, after `implementation(libs.postgresql.driver)`:
```kotlin
implementation(libs.pdfbox)
implementation(libs.poi.ooxml)
```

- [ ] **Step 3: Verify Gradle resolves the deps**

```bash
./gradlew :server:dependencies --configuration runtimeClasspath 2>&1 | grep -E "pdfbox|poi-ooxml" | head -5
```

Expected: Lines showing `org.apache.pdfbox:pdfbox:3.0.2` and `org.apache.poi:poi-ooxml:5.3.0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml server/build.gradle.kts
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: add pdfbox and poi-ooxml server dependencies"
```

---

## Task 3: StatementParser — text extraction

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt`
- Create: `server/src/test/kotlin/com/jvillada/movi/server/parsing/StatementParserTest.kt`

**Context:** Detects file type from extension (lowercase). For PDF uses PDFBox 3.x `Loader.loadPDF()`. For XLS/XLSX uses Apache POI `WorkbookFactory`. Returns the raw text and a heuristic bank name from the filename (strip extension, keep text before underscore or first digit run). The test directory may not exist yet — create it.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/com/jvillada/movi/server/parsing/StatementParserTest.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementParserTest {

    @Test
    fun `extractText returns raw UTF-8 for CSV files`() {
        val csv = "Fecha,Descripcion,Valor\n2025-05-28,Rappi,-48900\n"
        val bytes = csv.toByteArray(Charsets.UTF_8)
        val result = StatementParser.extractText(bytes, "extracto.csv")
        assertEquals(csv, result)
    }

    @Test
    fun `detectBankName extracts first word of filename`() {
        assertEquals("Bancolombia", StatementParser.detectBankName("Bancolombia_Mayo2025.pdf"))
        assertEquals("Davivienda", StatementParser.detectBankName("davivienda_extracto.csv"))
        assertEquals("BBVA", StatementParser.detectBankName("BBVA2025.xls"))
    }

    @Test
    fun `extractText handles empty CSV`() {
        val result = StatementParser.extractText(ByteArray(0), "empty.csv")
        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew :server:test --tests "com.jvillada.movi.server.parsing.StatementParserTest" 2>&1 | tail -10
```

Expected: `FAILED` — `StatementParser` does not exist yet.

- [ ] **Step 3: Implement `StatementParser`**

Create `server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

object StatementParser {

    fun extractText(bytes: ByteArray, fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()
        return when (ext) {
            "pdf"         -> extractPdf(bytes)
            "csv"         -> bytes.toString(Charsets.UTF_8)
            "xls", "xlsx" -> extractSpreadsheet(bytes)
            else          -> bytes.toString(Charsets.UTF_8)
        }
    }

    fun detectBankName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val firstWord = base.split('_', '-', ' ').firstOrNull { it.isNotBlank() } ?: base
        return firstWord.replaceFirstChar { it.uppercaseChar() }
    }

    private fun extractPdf(bytes: ByteArray): String =
        Loader.loadPDF(bytes).use { doc -> PDFTextStripper().getText(doc) }

    private fun extractSpreadsheet(bytes: ByteArray): String {
        val wb = WorkbookFactory.create(ByteArrayInputStream(bytes))
        return wb.use { workbook ->
            buildString {
                repeat(workbook.numberOfSheets) { sheetIdx ->
                    val sheet = workbook.getSheetAt(sheetIdx)
                    sheet.forEach { row ->
                        appendLine(row.joinToString("\t") { cell ->
                            cell.toString().trim()
                        })
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
./gradlew :server:test --tests "com.jvillada.movi.server.parsing.StatementParserTest" 2>&1 | tail -10
```

Expected: `3 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt \
        server/src/test/kotlin/com/jvillada/movi/server/parsing/StatementParserTest.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: StatementParser — PDF/CSV/XLS text extraction"
```

---

## Task 4: MerchantRulesStore

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/storage/MerchantRulesStore.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt`

**Context:** `JsonListStore<T>` takes a single `File`. For per-user stores, `MerchantRulesStore` lazily creates one `JsonListStore<MerchantRule>` per userId and caches it in a `ConcurrentHashMap`. When a rule for the same `merchantPattern` already exists, it is updated (upsert).

- [ ] **Step 1: Create `MerchantRulesStore`**

```kotlin
package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.MerchantRule
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MerchantRulesStore {

    private val stores = ConcurrentHashMap<String, JsonListStore<MerchantRule>>()

    private fun storeFor(userId: String): JsonListStore<MerchantRule> =
        stores.getOrPut(userId) {
            JsonListStore(
                file = File("movi-data", "merchant-rules-$userId.json"),
                elementSerializer = MerchantRule.serializer(),
                seed = emptyList(),
            )
        }

    suspend fun getRules(userId: String): List<MerchantRule> =
        storeFor(userId).snapshot()

    suspend fun saveRule(userId: String, rule: MerchantRule) {
        storeFor(userId).mutate { list ->
            val i = list.indexOfFirst { it.merchantPattern == rule.merchantPattern }
            if (i >= 0) list[i] = rule else list.add(rule)
        }
    }
}
```

- [ ] **Step 2: Add `merchantRules` to `Stores.kt`**

In `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt`, inside the `object Stores { }` block, add after the last existing line:

```kotlin
val merchantRules = MerchantRulesStore()
```

- [ ] **Step 3: Verify server compiles**

```bash
./gradlew :server:compileKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/storage/MerchantRulesStore.kt \
        server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: MerchantRulesStore — per-user JSON rule store"
```

---

## Task 5: ClaudeStatementParser

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt`
- Create: `server/src/test/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParserTest.kt`

**Context:** Follow the pattern in `AiRoutes.kt` exactly — reuse `resolveApiKey()` logic and the `AnthropicOkHttpClient` setup. Key differences: no `ThinkingConfigAdaptive` (we want pure JSON output), `maxTokens = 4096`. The `parseJson()` companion function is exposed separately so it can be unit-tested without an API key. The response may contain a JSON array anywhere in the text — extract the substring from `[` to the last `]`.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParserTest.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeStatementParserTest {

    @Test
    fun `parseJson extracts transactions from clean JSON array`() {
        val json = """[{"date":"2025-05-28","merchant":"Rappi","amount":48900,"type":"EXPENSE","category":"Restaurantes","description":"Domicilio","rawText":"COMPRA RAPPI"}]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Rappi", result[0].merchant)
        assertEquals(48900L, result[0].amount)
        assertEquals(TransactionType.EXPENSE, result[0].type)
    }

    @Test
    fun `parseJson extracts JSON array embedded in prose`() {
        val json = """Here are the transactions: [{"date":"2025-05-25","merchant":"Globant","amount":4500000,"type":"INCOME","category":"Salario","description":"Nomina","rawText":"ABONO NOMINA"}] end."""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Globant", result[0].merchant)
        assertEquals(TransactionType.INCOME, result[0].type)
    }

    @Test
    fun `parseJson returns empty list for invalid JSON`() {
        val result = ClaudeStatementParser.parseJson("no json here")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseJson assigns unique IDs to each transaction`() {
        val json = """[
          {"date":"2025-05-28","merchant":"A","amount":100,"type":"EXPENSE","category":"Otro","description":"","rawText":""},
          {"date":"2025-05-27","merchant":"B","amount":200,"type":"EXPENSE","category":"Otro","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertTrue(result[0].id != result[1].id)
        assertTrue(result[0].id.isNotBlank())
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew :server:test --tests "com.jvillada.movi.server.parsing.ClaudeStatementParserTest" 2>&1 | tail -10
```

Expected: `FAILED` — `ClaudeStatementParser` does not exist yet.

- [ ] **Step 3: Implement `ClaudeStatementParser`**

Create `server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

object ClaudeStatementParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: AnthropicClient? by lazy {
        val key = resolveApiKey() ?: return@lazy null
        runCatching { AnthropicOkHttpClient.builder().apiKey(key).build() }.getOrNull()
    }

    private fun resolveApiKey(): String? {
        System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() && it != "x" }?.let { return it }
        val envFile = File(System.getProperty("user.dir"), "server/.env")
            .takeIf { it.exists() } ?: File(System.getProperty("user.dir"), ".env")
        return envFile.takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("ANTHROPIC_API_KEY=") }
            ?.substringAfter("=")?.trim()
            ?.takeIf { it.isNotBlank() && it != "x" }
    }

    private fun buildSystemPrompt(rules: List<MerchantRule>): String {
        val rulesJson = if (rules.isEmpty()) "[]"
            else json.encodeToString(ListSerializer(MerchantRule.serializer()), rules)
        return """
Sos un parser de extractos bancarios colombianos. Tu trabajo es extraer todas las transacciones de un extracto bancario y devolver JSON válido.

Reglas del usuario (aprendidas de correcciones anteriores):
$rulesJson

Devolvé ÚNICAMENTE un array JSON con este formato exacto, sin explicaciones:
[{"date":"YYYY-MM-DD","merchant":"nombre limpio","amount":123456,"type":"EXPENSE|INCOME","category":"categoría","description":"descripción corta","rawText":"línea original"}]

- amount: entero en pesos colombianos (sin puntos ni comas), siempre positivo
- type: EXPENSE para débitos/compras/pagos, INCOME para créditos/abonos/nómina
- Aplicá las reglas del usuario cuando el merchant coincida
""".trimIndent()
    }

    suspend fun parse(text: String, rules: List<MerchantRule>): List<ParsedTransaction> {
        val c = client ?: return emptyList()
        val params = MessageCreateParams.builder()
            .model("claude-opus-4-7")
            .maxTokens(4096L)
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(buildSystemPrompt(rules)).build()))
            .messages(listOf(MessageParam.builder().role(MessageParam.Role.USER).content(text).build()))
            .build()
        val rawText = withContext(Dispatchers.IO) {
            val response = c.messages().create(params)
            response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("")
        }
        return parseJson(rawText)
    }

    fun parseJson(rawText: String): List<ParsedTransaction> {
        val start = rawText.indexOf('[')
        val end = rawText.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return emptyList()
        val arrayJson = rawText.substring(start, end + 1)
        return runCatching {
            json.decodeFromString(ListSerializer(ClaudeRow.serializer()), arrayJson)
                .map { row ->
                    ParsedTransaction(
                        id = UUID.randomUUID().toString(),
                        date = row.date,
                        merchant = row.merchant,
                        amount = row.amount,
                        type = runCatching { TransactionType.valueOf(row.type) }.getOrDefault(TransactionType.EXPENSE),
                        category = row.category,
                        description = row.description,
                        rawText = row.rawText,
                    )
                }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class ClaudeRow(
        val date: String,
        val merchant: String,
        val amount: Long,
        val type: String,
        val category: String,
        val description: String = "",
        val rawText: String = "",
    )
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```bash
./gradlew :server:test --tests "com.jvillada.movi.server.parsing.ClaudeStatementParserTest" 2>&1 | tail -10
```

Expected: `4 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParser.kt \
        server/src/test/kotlin/com/jvillada/movi/server/parsing/ClaudeStatementParserTest.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: ClaudeStatementParser — AI bank statement parsing"
```

---

## Task 6: StatementRoutes + Routing

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt`

**Context:** Follow the pattern in `EventRoutes.kt` — use `call.userId()` for JWT user ID, `dbQuery { }` for DB access, `Stores.*` for JSON stores. The upload endpoint converts each parsed transaction's `date` string to epoch millis for fuzzy matching against existing events. The import endpoint creates new `FinancialEvent`s for `imports` and for `reconciliations` with `confirm=false`; updates existing events for `confirm=true`. After processing reconciliations, saves merchant rules where category differed.

For converting parsed date string to epoch millis for comparison:
```kotlin
import java.time.LocalDate
import java.time.ZoneOffset
// ...
val parsedEpoch = LocalDate.parse(parsed.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
```

For same-day check: `parsedEpoch / 86_400_000L == existing.timestamp / 86_400_000L`
For within-2-days check: `kotlin.math.abs(parsedEpoch - existing.timestamp) <= 2 * 86_400_000L`

Updating an existing event uses `Events.update { }` from Exposed. The `Events` table columns are `category`, `description`, `merchant` (nullable String).

- [ ] **Step 1: Create `StatementRoutes.kt`**

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.StatementParser
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs

fun Route.statementRoutes() {

    post("/api/statements/upload") {
        val uid = call.userId()
        val multipart = call.receiveMultipart()
        var fileName = "statement"
        var bytes = ByteArray(0)

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "statement"
                bytes = part.streamProvider().readBytes()
            }
            part.dispose()
        }

        if (bytes.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "No file received")
            return@post
        }

        val text = StatementParser.extractText(bytes, fileName)
        val bankName = StatementParser.detectBankName(fileName)
        val rules = Stores.merchantRules.getRules(uid)
        val parsed = ClaudeStatementParser.parse(text, rules)

        val existing = dbQuery {
            Events.selectAll().where { Events.userId eq uid }.map { row ->
                FinancialEvent(
                    id = row[Events.id],
                    accountId = row[Events.accountId],
                    type = TransactionType.valueOf(row[Events.type]),
                    amount = row[Events.amount],
                    category = row[Events.category],
                    description = row[Events.description],
                    merchant = row[Events.merchant],
                    timestamp = row[Events.timestamp],
                    source = EventSource.valueOf(row[Events.eventSource]),
                    rawPayload = row[Events.rawPayload],
                    reconciliationStatus = ReconciliationStatus.valueOf(row[Events.reconciliationStatus]),
                    syncedAt = row[Events.syncedAt],
                )
            }
        }

        val matches = mutableListOf<ReconciliationMatch>()
        val newTransactions = mutableListOf<ParsedTransaction>()

        for (tx in parsed) {
            val parsedEpoch = runCatching {
                LocalDate.parse(tx.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()

            val match = if (parsedEpoch != null) {
                existing.firstOrNull { ev ->
                    ev.amount == tx.amount &&
                        abs(parsedEpoch - ev.timestamp) <= 2 * 86_400_000L
                }
            } else null

            if (match != null) {
                val sameDay = parsedEpoch != null &&
                    parsedEpoch / 86_400_000L == match.timestamp / 86_400_000L
                matches += ReconciliationMatch(
                    parsed = tx,
                    existingEventId = match.id,
                    existingEvent = match,
                    matchConfidence = if (sameDay) 0.95f else 0.7f,
                )
            } else {
                newTransactions += tx
            }
        }

        val period = runCatching {
            val date = LocalDate.parse(parsed.firstOrNull()?.date ?: "2025-01-01")
            "${monthName(date.monthValue)} ${date.year}"
        }.getOrDefault("")

        call.respond(
            StatementParseResult(
                statementId = UUID.randomUUID().toString(),
                bankName = bankName,
                period = period,
                newTransactions = newTransactions,
                matches = matches,
            )
        )
    }

    post("/api/statements/import") {
        val uid = call.userId()
        val decision = call.receive<ImportDecision>()
        var imported = 0

        // Create events for new transactions
        for (tx in decision.imports) {
            createEventFromParsed(tx, decision.accountId, uid)
            imported++
        }

        // Process reconciliation decisions
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
                            it[category]    = finalCategory
                            it[description] = finalDescription
                            it[merchant]    = finalMerchant
                        }
                    }

                    // Save merchant rule when category differed
                    if (dec.parsed.category != existCat) {
                        Stores.merchantRules.saveRule(uid, MerchantRule(
                            merchantPattern = dec.parsed.merchant.lowercase().trim(),
                            category = finalCategory,
                        ))
                    }
                }
                imported++
            } else {
                // User said "not the same" — create new event from parsed
                createEventFromParsed(dec.parsed, decision.accountId, uid)
                imported++
            }
        }

        call.respond(HttpStatusCode.OK, mapOf("imported" to imported))
    }
}

private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String) {
    val eventId = "ev_${UUID.randomUUID()}"
    val ts = runCatching {
        LocalDate.parse(tx.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }
    dbQuery {
        Events.insert {
            it[id]                   = eventId
            it[Events.userId]        = uid
            it[Events.accountId]     = accountId
            it[type]                 = tx.type.name
            it[amount]               = tx.amount
            it[category]             = tx.category
            it[description]          = tx.description
            it[merchant]             = tx.merchant
            it[timestamp]            = ts
            it[eventSource]          = EventSource.STATEMENT.name
            it[rawPayload]           = tx.rawText.ifBlank { null }
            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
            it[syncedAt]             = null
        }
        val delta = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
        Accounts.update({ (Accounts.id eq accountId) and (Accounts.userId eq uid) }) {
            it[balance] = balance + delta
        }
    }
}

private fun monthName(month: Int) = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)[month - 1]
```

- [ ] **Step 2: Register in `Routing.kt`**

In `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt`, inside `authenticate("jwt") { }`, add:

```kotlin
statementRoutes()
```

The final `authenticate("jwt")` block should look like:

```kotlin
authenticate("jwt") {
    accountRoutes()
    eventRoutes()
    walletRoutes()
    financeRoutes()
    smsRoutes()
    aiRoutes()
    statementRoutes()
}
```

- [ ] **Step 3: Verify server compiles**

```bash
./gradlew :server:compileKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: StatementRoutes — upload + import endpoints"
```

---

## Task 7: Repository layer

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- Modify: `shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`
- Modify: `shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`

**Context:** `WalletRepositoryImpl` uses `io.ktor.client.request.forms.MultiPartFormDataContent` and `io.ktor.client.request.forms.formData` — both in `ktor-client-core`, already on the classpath. The `uploadStatement` response has type `StatementParseResult`. `importStatement` returns `Unit`. `LocalRepository` delegates both to `remote` because statement parsing is server-only.

- [ ] **Step 1: Add methods to `WalletRepository.kt`**

Add these two methods at the end of the interface (before the closing `}`):

```kotlin
suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult
suspend fun importStatement(decision: ImportDecision)
```

Also add the import at the top of the file (alongside the other model imports):
```kotlin
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementParseResult
```

- [ ] **Step 2: Implement in `WalletRepositoryImpl.kt`**

Add these imports at the top of the file:
```kotlin
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementParseResult
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
```

Add these two methods at the end of the class (before the closing `}`):

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
    client.post("$baseUrl/api/statements/upload") {
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                append(HttpHeaders.ContentType, mimeType)
            })
        }))
    }.body()

override suspend fun importStatement(decision: ImportDecision) {
    client.post("$baseUrl/api/statements/import") {
        contentType(ContentType.Application.Json)
        setBody(decision)
    }
}
```

- [ ] **Step 3: Add to `LocalRepository.kt`**

Add these imports to `LocalRepository.kt`:
```kotlin
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementParseResult
```

Add these two methods to the "Delegate everything else to remote" section (after `override suspend fun login(...)`):

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
    remote.uploadStatement(fileName, bytes, mimeType)

override suspend fun importStatement(decision: ImportDecision) =
    remote.importStatement(decision)
```

- [ ] **Step 4: Add to `NoOpRepository.kt`**

Add these two lines at the end of `NoOpRepository` (before the closing `}`):

```kotlin
override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String) =
    StatementParseResult("", "", "", emptyList(), emptyList())
override suspend fun importStatement(decision: ImportDecision) {}
```

Also add these imports:
```kotlin
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementParseResult
```

- [ ] **Step 5: Verify shared module tests pass**

```bash
./gradlew :shared:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` (existing tests pass, no new failures)

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt \
        shared/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt \
        shared/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt \
        shared/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: repository — uploadStatement + importStatement"
```

---

## Task 8: FilePicker — expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`
- Create: `composeApp/src/androidMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`
- Create: `composeApp/src/iosMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt`

**Context:** Follow the pattern of `BackHandler.kt` / `BackHandler.android.kt` for the expect/actual structure. The Android actual uses `ActivityResultContracts.GetContent()` which requires `androidx-activity-compose` (already a dependency). The lambda receives `(fileName, bytes, mimeType)` and runs on the main thread — the stream read blocks the main thread briefly; this is acceptable for the initial implementation.

- [ ] **Step 1: commonMain expect declaration**

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit
```

- [ ] **Step 2: Android actual**

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment ?: "statement"
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        onResult(name, bytes, mime)
    }
    return { launcher.launch("*/*") }
}
```

- [ ] **Step 3: iOS stub**

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit = { }
```

- [ ] **Step 4: wasmJs stub**

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit = { }
```

- [ ] **Step 5: Verify Android build compiles**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt \
        composeApp/src/androidMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt \
        composeApp/src/iosMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt \
        composeApp/src/wasmJsMain/kotlin/com/jvillada/movi/ui/extractos/FilePicker.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: FilePicker expect/actual — Android + stubs"
```

---

## Task 9: Navigation + App routing

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`

**Context:** `Screen.StatementReview` carries the `StatementParseResult` as a JSON string — same pattern as `Screen.SMSReconcile` carrying `smsId`. Serialization uses `kotlinx.serialization.json.Json` from `kotlinx-serialization-json` which is already on the classpath.

- [ ] **Step 1: Add `Screen.StatementReview` to `Navigation.kt`**

In `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt`, add this line at the end of the sealed class (before the closing `}`):

```kotlin
data class StatementReview(val resultJson: String) : Screen()
```

- [ ] **Step 2: Wire `StatementReviewScreen` in `App.kt`**

Add this import to `App.kt`:
```kotlin
import com.jvillada.movi.ui.extractos.StatementReviewScreen
import com.jvillada.movi.shared.model.StatementParseResult
import kotlinx.serialization.json.Json
```

Add this branch to the `when (currentScreen)` block (after the `is Screen.AccountDetail -> ...` line):
```kotlin
is Screen.StatementReview -> StatementReviewScreen(
    onNavigate = navigate,
    result = Json.decodeFromString(currentScreen.resultJson),
)
```

- [ ] **Step 3: Verify Android compiles (StatementReviewScreen doesn't exist yet — expect an unresolved reference)**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | grep -E "error:|BUILD"
```

Expected: `error: unresolved reference: StatementReviewScreen` — this is correct; the screen is added in Task 10.

- [ ] **Step 4: Commit Navigation.kt only (App.kt goes in after Task 10)**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: add Screen.StatementReview to navigation"
```

---

## Task 10: StatementReviewScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt`

**Context:** State-driven Compose screen. Loads accounts on entry to auto-select the destination account (`bankName` case-insensitive match → first non-CASH → first available). Reconciliation decisions are stored as `MutableMap<String, ReconciliationDecision>` keyed by `parsedId`. The import action sends confirmed reconciliations + all `confirm=false` decisions (which the server creates as new events). Amount display: EXPENSE = negative red, INCOME = positive green. Colors: `MinExpense = Color(0xFFE85C5C)`, `MinIncome = Color(0xFF5CB85C)`, `MinAmber = Color(0xFFE8A85C)` — use the amber for match cards. The theme file is at `composeApp/src/commonMain/kotlin/com/jvillada/movi/theme/Color.kt`; check if `MinAmber` exists before using it and define inline if not (e.g., `Color(0xFFE8A85C)`).

- [ ] **Step 1: Check available theme colors**

```bash
grep "Min" composeApp/src/commonMain/kotlin/com/jvillada/movi/theme/Color.kt
```

Note the available colors for use in the screen (use `MinExpense`, `MinIncome`, `MinPrimary`, `MinText`, `MinTextDim`, `MinTextMute`, `MinTextFaint`, `MinSurface`, `MinSurfaceContainer`, `MinSurfaceContainerHigh`, `MinSurfaceContainerLow`, `MinBorderStrong`, `MinBg`).

- [ ] **Step 2: Create `StatementReviewScreen.kt`**

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.launch

private val MinAmber = Color(0xFFE8A85C)

@Composable
fun StatementReviewScreen(
    onNavigate: (Screen) -> Unit,
    result: StatementParseResult,
) {
    val coroutine = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var selectedIds by remember { mutableStateOf(result.newTransactions.map { it.id }.toSet()) }
    val reconciliations = remember { mutableStateMapOf<String, ReconciliationDecision>() }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load accounts to determine destination
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
    }

    val destinationAccount = remember(accounts, result.bankName) {
        accounts.firstOrNull { it.name.contains(result.bankName, ignoreCase = true) }
            ?: accounts.firstOrNull { it.type != AccountType.CASH }
            ?: accounts.firstOrNull()
    }

    val confirmedCount = reconciliations.values.count { it.confirm }
    val importCount = selectedIds.size + confirmedCount
    val canImport = importCount > 0 && !working && destinationAccount != null

    fun import() {
        val acct = destinationAccount ?: return
        working = true; error = null
        coroutine.launch {
            runCatching {
                val decision = ImportDecision(
                    statementId = result.statementId,
                    accountId = acct.id,
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

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        // Header
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
            Column {
                Text(
                    text = "${result.bankName.uppercase()} · ${result.period.uppercase()}",
                    fontSize = 10.sp, color = MinTextMute, letterSpacing = 1.sp,
                )
                val newCount = result.newTransactions.size
                val matchCount = result.matches.size
                Text(
                    text = "$newCount nuevas · $matchCount coincidencias",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MinText,
                )
            }
        }

        // Account destination chip
        destinationAccount?.let { acct ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Destino:", fontSize = 11.sp, color = MinTextMute)
                Text(
                    acct.name,
                    fontSize = 11.sp, color = MinPrimary, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MinPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Matches section
            if (result.matches.isNotEmpty()) {
                item {
                    Text(
                        "POSIBLES DUPLICADOS",
                        fontSize = 10.sp, color = MinAmber, letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(result.matches, key = { it.parsed.id }) { match ->
                    ReconciliationCard(
                        match = match,
                        decision = reconciliations[match.parsed.id],
                        onConfirm = { dec -> reconciliations[match.parsed.id] = dec },
                        onReject = {
                            reconciliations[match.parsed.id] = ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = false,
                                categorySource = FieldSource.STATEMENT,
                                descriptionSource = FieldSource.STATEMENT,
                                merchantSource = FieldSource.STATEMENT,
                                parsed = match.parsed,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // New transactions section
            if (result.newTransactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "NUEVAS TRANSACCIONES",
                            fontSize = 10.sp, color = MinTextDim, letterSpacing = 1.sp,
                        )
                        val allSelected = selectedIds.size == result.newTransactions.size
                        Text(
                            if (allSelected) "Deseleccionar todas" else "Seleccionar todas",
                            fontSize = 11.sp, color = MinPrimary,
                            modifier = Modifier.clickable {
                                selectedIds = if (allSelected) emptySet()
                                    else result.newTransactions.map { it.id }.toSet()
                            },
                        )
                    }
                }
                items(result.newTransactions, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = {
                            selectedIds = if (tx.id in selectedIds)
                                selectedIds - tx.id else selectedIds + tx.id
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Error message
        error?.let {
            Text(
                it, fontSize = 12.sp, color = MinExpense,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Sticky bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinSurfaceContainer)
                .padding(16.dp),
        ) {
            Button(
                onClick = ::import,
                enabled = canImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MinPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Importar $importCount seleccionada${if (importCount != 1) "s" else ""}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTransactionRow(
    tx: ParsedTransaction,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MinSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = if (checked) "Seleccionado" else "No seleccionado",
            tint = if (checked) MinPrimary else MinTextMute,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchant, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
            Text("${tx.category} · ${tx.date}", fontSize = 11.sp, color = MinTextMute)
        }
        val amountColor = if (tx.type == TransactionType.INCOME) MinIncome else MinExpense
        val prefix = if (tx.type == TransactionType.INCOME) "+" else "−"
        Text(
            "$prefix$${"%,d".format(tx.amount)}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = amountColor,
        )
    }
}

@Composable
private fun ReconciliationCard(
    match: ReconciliationMatch,
    decision: ReconciliationDecision?,
    onConfirm: (ReconciliationDecision) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var categorySource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var descriptionSource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var merchantSource by remember { mutableStateOf(FieldSource.MANUAL) }

    val isDecided = decision != null
    val borderColor = if (isDecided && decision!!.confirm) MinIncome else MinAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Badge + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⚠ POSIBLE DUPLICADO",
                fontSize = 9.sp, color = MinAmber, letterSpacing = 0.5.sp,
            )
            val amtColor = if (match.parsed.type == TransactionType.INCOME) MinIncome else MinExpense
            val prefix = if (match.parsed.type == TransactionType.INCOME) "+" else "−"
            Text(
                "$prefix$${"%,d".format(match.parsed.amount)}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = amtColor,
            )
        }

        // Column headers
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(80.dp))
            Text(
                "MANUAL", fontSize = 9.sp, color = MinPrimary, letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text(
                "EXTRACTO", fontSize = 9.sp, color = Color(0xFF5CB8E8), letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
        }

        // Merchant row (differs always since bank names are messy)
        FieldRow(
            label = "Comercio",
            manualValue = match.existingEvent.merchant ?: match.existingEvent.description,
            statementValue = match.parsed.merchant,
            selected = merchantSource,
            onToggle = { merchantSource = if (merchantSource == FieldSource.MANUAL) FieldSource.STATEMENT else FieldSource.MANUAL },
        )

        // Category row (only if different)
        if (match.parsed.category != match.existingEvent.category) {
            FieldRow(
                label = "Categoría",
                manualValue = match.existingEvent.category,
                statementValue = match.parsed.category,
                selected = categorySource,
                onToggle = { categorySource = if (categorySource == FieldSource.STATEMENT) FieldSource.MANUAL else FieldSource.STATEMENT },
            )
        }

        // Description row (only if extracto has one and differs)
        val existDesc = match.existingEvent.description
        val parsedDesc = match.parsed.description
        if (parsedDesc.isNotBlank() && parsedDesc != existDesc) {
            FieldRow(
                label = "Descripción",
                manualValue = existDesc.ifBlank { "—" },
                statementValue = parsedDesc,
                selected = descriptionSource,
                onToggle = { descriptionSource = if (descriptionSource == FieldSource.STATEMENT) FieldSource.MANUAL else FieldSource.STATEMENT },
            )
        }

        if (!isDecided) {
            Text(
                "Toca cada campo para cambiar la fuente",
                fontSize = 9.sp, color = MinTextFaint,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Action buttons
        if (!isDecided) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("No son el mismo", fontSize = 11.sp, color = MinTextDim)
                }
                Button(
                    onClick = {
                        onConfirm(
                            ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = true,
                                categorySource = categorySource,
                                descriptionSource = descriptionSource,
                                merchantSource = merchantSource,
                                parsed = match.parsed,
                            )
                        )
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinPrimary),
                ) {
                    Text("Confirmar reconciliación", fontSize = 11.sp, color = Color.White)
                }
            }
        } else {
            Text(
                if (decision!!.confirm) "✓ Reconciliado" else "→ Se importará como nuevo",
                fontSize = 11.sp,
                color = if (decision.confirm) MinIncome else MinTextMute,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    manualValue: String,
    statementValue: String,
    selected: FieldSource,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, color = MinTextMute, modifier = Modifier.width(80.dp))
        FieldCell(
            value = manualValue,
            active = selected == FieldSource.MANUAL,
            onClick = onToggle,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        FieldCell(
            value = statementValue,
            active = selected == FieldSource.STATEMENT,
            onClick = onToggle,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

@Composable
private fun FieldCell(
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (active) MinPrimary else Color.Transparent
    val bgColor = if (active) MinPrimary.copy(alpha = 0.08f) else MinSurfaceContainerHigh
    Text(
        value,
        fontSize = 10.sp,
        color = if (active) MinText else MinTextMute,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
```

- [ ] **Step 3: Check which color tokens exist and fix any missing ones**

```bash
grep "val Min" composeApp/src/commonMain/kotlin/com/jvillada/movi/theme/Color.kt
```

If `MinSurface`, `MinIncome`, or `MinExpense` are missing, find the correct names and update the screen file accordingly.

- [ ] **Step 4: Compile**

```bash
./gradlew :composeApp:compileKotlinAndroid 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: StatementReviewScreen — bulk review + reconciliation UI"
```

---

## Task 11: Wire ExtractosScreen + final build verification

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt`

**Context:** Replace the placeholder upload zone in `ExtractosScreen` with a live upload flow using `rememberFilePicker`. After a successful upload, navigate to `Screen.StatementReview` with the result JSON-serialized. Show inline progress and error states. `App.kt` already has the `StatementReviewScreen` import added in Task 9 — commit it now.

- [ ] **Step 1: Replace `ExtractosScreen.kt` upload zone**

Replace the entire file content with:

```kotlin
package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ExtractosScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploadingFileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val launchPicker = rememberFilePicker { fileName, bytes, mimeType ->
        uploading = true
        uploadingFileName = fileName
        error = null
        coroutine.launch {
            runCatching { Repositories.wallets.uploadStatement(fileName, bytes, mimeType) }
                .onSuccess { result: StatementParseResult ->
                    uploading = false
                    onNavigate(Screen.StatementReview(Json.encodeToString(result)))
                }
                .onFailure {
                    uploading = false
                    error = "No pude procesar el extracto: ${it.message ?: "error"}"
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowBackIosNew, "Volver",
                tint = MinTextDim,
                modifier = Modifier.size(20.dp).clickable { onNavigate(Screen.Mas) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Extractos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
        ) {
            // Info banner
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

            Spacer(Modifier.height(24.dp))

            if (uploading) {
                // Progress state
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
                // Upload zone
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

            // Error
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            // Supported banks
            Text(
                "Bancos soportados",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinTextDim,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            listOf("Bancolombia", "Nequi", "Davivienda", "BBVA", "Falabella", "Colpatria", "Banco de Bogotá").chunked(3).forEach { row ->
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

        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> onNavigate(Screen.Mas)
            }
        }
    }
}
```

- [ ] **Step 2: Commit `App.kt` (with StatementReviewScreen routing from Task 9)**

```bash
git add composeApp/src/commonMain/kotlin/com/jvillada/movi/App.kt \
        composeApp/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/ExtractosScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: wire ExtractosScreen upload flow + App.kt routing"
```

- [ ] **Step 3: Build debug APK**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` and `composeApp/build/outputs/apk/debug/composeApp-debug.apk` exists.

- [ ] **Step 4: Run server tests**

```bash
./gradlew :server:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Run shared tests**

```bash
./gradlew :shared:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Install and smoke-test on emulator (optional — requires running emulator)**

```bash
# Boot emulator if not running
/Users/jvillada/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro -no-snapshot-load > /tmp/emulator.log 2>&1 &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done && echo "booted"

# Install
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity
```

Navigate to: Más → Extractos → tap upload zone → select a CSV file.
Expected: file picker opens, after selection the progress bar appears, then navigates to `StatementReviewScreen`.

- [ ] **Step 7: Final commit (if any fixes needed)**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "fix: statement import smoke-test corrections" -- <changed files>
```

---

## Summary of deviations from spec

| # | Deviation | Reason |
|---|-----------|--------|
| 1 | `ReconciliationDecision` includes `parsed: ParsedTransaction` | Server needs full parsed data to handle `confirm=false` (new event) and to save merchant rules |
| 2 | `ImportDecision` includes `accountId: String` | Server cannot create `FinancialEvent` without knowing the destination account |
| 3 | `App.kt` imports `StatementReviewScreen` | Required for navigation routing |
| 4 | `StatementReviewScreen` loads accounts via `getAccounts()` | Needed to auto-select the destination account for import |
| 5 | PDFBox API uses `Loader.loadPDF()` not `PDDocument.load()` | `PDDocument.load()` was removed in PDFBox 3.x |
