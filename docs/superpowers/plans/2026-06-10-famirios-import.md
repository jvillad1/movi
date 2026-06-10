# Famirios Historical Import — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import the user's Famirios budget spreadsheet (one tab per year, rows = categories, columns = months) as monthly aggregate events through the existing statement pipeline, parsed deterministically (no LLM).

**Architecture:** A new `FamiriosParser` works on the POI `Workbook` (tab name = year; the flattened-text path loses it). `StatementParser` gains a `FAMIRIOS` document type detected by text markers. The upload route branches: Famirios → deterministic parse; everything else → the existing Claude path. Review/import/reconciliation are untouched.

**Tech Stack:** Kotlin, Ktor, Apache POI (already a server dependency), kotlin.test. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-10-famirios-import-design.md`

**Pre-reqs:** Work on branch `feat/famirios-import` (create from master first: `git checkout -b feat/famirios-import`). Local Postgres running for the e2e task only.

---

### Task 1: FAMIRIOS document type + marker detection

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/parsing/StatementParserTest.kt` (exists — append)

- [ ] **Step 1: Write the failing test**

Append inside the existing `StatementParserTest` class:

```kotlin
    @Test
    fun `detectDocumentType identifies a Famirios budget export`() {
        val text = """
            Resumén	Jan	Feb	Mar
            Dineros iniciales
            Ingresos	100	200	300
            Gastos Fijos	50	60	70
            Tipo de ingreso
            Income Biweekly Pay 1	100	200	300
        """.trimIndent()
        assertEquals(StatementDocumentType.FAMIRIOS, StatementParser.detectDocumentType(text))
    }

    @Test
    fun `detectDocumentType does not flag bank statements as Famirios`() {
        val text = "Fecha\tTipo de transacción\tDescripción\tValor\n25 may 2026\tCrédito\tPago PAGOS\t100"
        assertEquals(StatementDocumentType.TRANSACTION_STATEMENT, StatementParser.detectDocumentType(text))
    }
```

(`assertEquals` and `StatementDocumentType` imports already exist in that file; add them if not.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.StatementParserTest"`
Expected: FAIL to compile — `FAMIRIOS` unresolved.

- [ ] **Step 3: Implement detection**

In `StatementParser.kt`:

(a) Extend the enum:
```kotlin
enum class StatementDocumentType { TRANSACTION_STATEMENT, LOAN_SUMMARY, INVESTMENT_FUND, FAMIRIOS }
```

(b) In `detectDocumentType`, add the Famirios check FIRST (before the loan/investment checks — a Famirios export can't contain those markers, but order makes intent explicit):
```kotlin
    fun detectDocumentType(text: String): StatementDocumentType {
        val upper = text.uppercase()
        if (upper.contains("RESUMÉN") && upper.contains("TIPO DE INGRESO") && upper.contains("GASTOS FIJOS"))
            return StatementDocumentType.FAMIRIOS
        if ((upper.contains("LÍNEA DE CRÉDITO") || upper.contains("LINEA DE CREDITO") ||
```
(rest of the function unchanged).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.StatementParserTest"`
Expected: PASS (all tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/parsing/StatementParser.kt \
        server/src/test/kotlin/com/jvillada/movi/server/parsing/StatementParserTest.kt
git commit -m "feat(server): detect Famirios budget exports as a statement document type"
```

---

### Task 2: FamiriosParser (deterministic workbook parser)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/parsing/FamiriosParser.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/parsing/FamiriosParserTest.kt`

- [ ] **Step 1: Write the failing test**

Create `FamiriosParserTest.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FamiriosParserTest {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** Build a minimal Famirios-shaped workbook: a noise tab + two year tabs. */
    private fun famiriosWorkbook(): Workbook {
        val wb = XSSFWorkbook()

        wb.createSheet("How to Guide").createRow(0).createCell(0)
            .setCellValue("This spreadsheet is used to track expenses")

        // ---- 2024 tab: header offset by one extra leading column ("Death") ----
        val s24 = wb.createSheet("2024")
        var r = 0
        s24.createRow(r).also { row ->                      // header row with extra col
            row.createCell(0).setCellValue("Resumén")
            row.createCell(1).setCellValue("Death")
            MONTHS.forEachIndexed { i, m -> row.createCell(i + 2).setCellValue(m) }
        }; r++
        s24.createRow(r).also { it.createCell(0).setCellValue("Ingresos"); it.createCell(2).setCellValue(999.0) }; r++  // summary noise
        s24.createRow(r).also { it.createCell(0).setCellValue("Tipo de ingreso") }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Income Biweekly Pay 1")
            row.createCell(2).setCellValue(1_000_000.0)      // Jan
            row.createCell(3).setCellValue(2_000_000.0)      // Feb
        }; r++
        s24.createRow(r).also { it.createCell(0).setCellValue("Gastos") }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Arriendo")
            row.createCell(2).setCellValue(500_000.0)        // Jan
        }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Crédito Vehículo")
            row.createCell(2).setCellValue(300_000.49)       // Jan — rounds to 300000
        }; r++
        s24.createRow(r).also { row ->                       // #REF! label row -> skipped
            row.createCell(0).setCellErrorValue(FormulaError.REF.code)
            row.createCell(2).setCellValue(123.0)
        }; r++
        s24.createRow(r).also { row ->                       // YTD noise row -> skipped
            row.createCell(0).setCellValue("YTD TOTAL")
            row.createCell(2).setCellValue(456.0)
        }

        // ---- 2026 tab: standard layout, has future months ----
        val s26 = wb.createSheet("2026")
        var q = 0
        s26.createRow(q).also { row ->
            row.createCell(0).setCellValue("Gastos")
        }; q++
        // month header BELOW a section is also valid: use nearest header ABOVE data rows,
        // so put header first in this tab instead:
        val s26b = wb.getSheet("2026")
        s26b.createRow(q).also { row ->
            row.createCell(0).setCellValue("")
            MONTHS.forEachIndexed { i, m -> row.createCell(i + 1).setCellValue(m) }
        }; q++
        s26b.createRow(q).also { row ->
            row.createCell(0).setCellValue("Mercado")
            row.createCell(5).setCellValue(2_000_000.0)      // May 2026 (col 5 = May)
            row.createCell(9).setCellValue(3_000_000.0)      // Sep 2026 — FUTURE, excluded
        }
        return wb
    }

    @Test
    fun `parses sections, maps categories, skips noise and future months`() {
        val today = LocalDate.of(2026, 6, 15)
        val txs = FamiriosParser.parse(famiriosWorkbook(), today)

        // 2024: 2 income cells + 2 expense cells; 2026: 1 (Sep excluded). Noise rows skipped.
        assertEquals(5, txs.size)

        val income = txs.filter { it.type == TransactionType.INCOME }
        assertEquals(2, income.size)
        assertEquals("Salario", income.first().category)
        assertEquals("2024-01-31", income.first { it.amount == 1_000_000L }.date)
        assertEquals("2024-02-29", income.first { it.amount == 2_000_000L }.date) // leap year

        val arriendo = txs.first { it.merchant == "Arriendo" }
        assertEquals("Vivienda", arriendo.category)
        assertEquals(TransactionType.EXPENSE, arriendo.type)

        val vehiculo = txs.first { it.merchant == "Crédito Vehículo" }
        assertEquals("Otros", vehiculo.category)              // crédito wins over vehículo
        assertEquals(300_000L, vehiculo.amount)               // rounded

        val mercado = txs.first { it.merchant == "Mercado" }
        assertEquals("Comida", mercado.category)
        assertEquals("2026-05-31", mercado.date)
        assertTrue(txs.none { it.date.startsWith("2026-09") }) // future month excluded

        assertTrue(txs.all { it.currency == "COP" })
        assertTrue(txs.all { it.rawText.isNotBlank() })
    }

    @Test
    fun `returns empty for a workbook with no year tabs`() {
        val wb = XSSFWorkbook()
        wb.createSheet("Notas").createRow(0).createCell(0).setCellValue("hola")
        assertTrue(FamiriosParser.parse(wb, LocalDate.of(2026, 6, 15)).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.FamiriosParserTest"`
Expected: FAIL to compile — `FamiriosParser` unresolved.

- [ ] **Step 3: Implement the parser**

Create `FamiriosParser.kt`:

```kotlin
package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Deterministic parser for the user's "Famirios" budget spreadsheet
 * (one tab per year; rows = categories, columns = months). Each non-zero
 * (category × month) cell becomes one monthly-aggregate [ParsedTransaction].
 * Months after [today] are excluded — Famirios pre-fills the whole year
 * with projections. See docs/superpowers/specs/2026-06-10-famirios-import-design.md.
 */
object FamiriosParser {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTH_NAMES_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
                                        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** Row labels that are summaries/headers, never data. Compared against the normalized label. */
    private val NOISE_EXACT = setOf(
        "resumen", "dineros iniciales", "ingresos", "gastos fijos",
        "gastos extraordinarios", "gastos", "tipo de ingreso",
    )
    private val NOISE_CONTAINS = listOf("ytd", "promedio", "presupuesto", "total", "gastos (%)")

    private enum class Section { NONE, INCOME, EXPENSE }

    fun parse(workbook: Workbook, today: LocalDate): List<ParsedTransaction> {
        val out = mutableListOf<ParsedTransaction>()
        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val year = sheet.sheetName.trim().toIntOrNull()?.takeIf { it in 2000..2099 } ?: continue
            parseYearSheet(sheet, year, today, out)
        }
        return out
    }

    private fun parseYearSheet(sheet: Sheet, year: Int, today: LocalDate, out: MutableList<ParsedTransaction>) {
        var monthCols: Map<Int, Int> = emptyMap()  // column index -> month number (1..12)
        var section = Section.NONE

        for (row in sheet) {
            // A header row maps month names to columns (>=6 matches to avoid false positives).
            val headerCols = monthColumns(row)
            if (headerCols.size >= 6) {
                monthCols = headerCols
                // fall through: the same row may also carry a section label in col 0 ("Resumén")
            }

            val label = labelOf(row) ?: run {
                if (rowHasErrorLabel(row)) return@run null   // #REF! label -> skip row
                null
            }
            val norm = label?.let(::normalize)

            // Section transitions / summary blocks.
            when {
                norm == "tipo de ingreso" -> { section = Section.INCOME; continue }
                norm == "gastos" || norm == "gastos extraordinarios" -> { section = Section.EXPENSE; continue }
                norm == "resumen" || (norm != null && NOISE_CONTAINS.any { norm.contains(it) } && norm.contains("gastos (%)")) ->
                    { section = Section.NONE; continue }
            }

            if (section == Section.NONE || label == null || monthCols.isEmpty()) continue
            if (norm in NOISE_EXACT || NOISE_CONTAINS.any { norm!!.contains(it) }) continue

            for ((col, month) in monthCols) {
                if (year > today.year || (year == today.year && month > today.monthValue)) continue
                val raw = numericAt(row, col) ?: continue
                if (raw == 0.0) continue
                val lastDay = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
                out += ParsedTransaction(
                    id = UUID.randomUUID().toString(),
                    date = lastDay.toString(),
                    merchant = label,
                    amount = abs(raw).roundToLong(),
                    currency = "COP",
                    type = if (section == Section.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                    category = categoryFor(label, section),
                    description = "Famirios · $label · ${MONTH_NAMES_ES[month - 1]} $year",
                    rawText = "${sheet.sheetName}!$label:${MONTHS[month - 1]}=$raw",
                )
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun monthColumns(row: Row): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for (cell in row) {
            val text = stringAt(cell)?.trim() ?: continue
            val m = MONTHS.indexOf(text)
            if (m >= 0) map[cell.columnIndex] = m + 1
        }
        return map
    }

    private fun labelOf(row: Row): String? {
        val cell = row.getCell(0) ?: return null
        return stringAt(cell)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun rowHasErrorLabel(row: Row): Boolean =
        row.getCell(0)?.let { effectiveType(it) == CellType.ERROR } == true

    private fun stringAt(cell: Cell): String? = when (effectiveType(cell)) {
        CellType.STRING -> cell.stringCellValue
        else -> null
    }

    private fun numericAt(row: Row, col: Int): Double? {
        val cell = row.getCell(col) ?: return null
        return when (effectiveType(cell)) {
            CellType.NUMERIC -> cell.numericCellValue
            else -> null
        }
    }

    /** Resolves FORMULA cells to their cached result type. */
    private fun effectiveType(cell: Cell): CellType =
        if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    /** First match wins; see spec for the ordering rationale. */
    private fun categoryFor(label: String, section: Section): String {
        val n = normalize(label)
        if (section == Section.INCOME) {
            return if (listOf("pay", "salario", "nomina", "cesantias").any { n.contains(it) }) "Salario"
                   else "Otros ingresos"
        }
        return when {
            listOf("credito", "tarjeta", "deuda", "nubank").any { n.contains(it) } -> "Otros"
            listOf("arriendo", "apartamento", "hogar", "casa", "administr").any { n.contains(it) } -> "Vivienda"
            listOf("mercado", "rappi").any { n.contains(it) } -> "Comida"
            listOf("gasolina", "vehiculo", "carro", "soat", "seguro del carro").any { n.contains(it) } -> "Transporte"
            listOf("celular", "internet", "servicios").any { n.contains(it) } -> "Servicios"
            listOf("gym", "coomeva", "prepagada", "salud", "eps").any { n.contains(it) } -> "Salud"
            listOf("diversion", "entreten", "happy").any { n.contains(it) } -> "Entretenimiento"
            else -> "Otros"
        }
    }
}
```

NOTE for the implementer: the test's 2026 tab puts the header row AFTER a "Gastos" row — the implementation above handles that because `monthCols` updates whenever a header row appears and data rows use the latest mapping; the "Gastos" section flag survives the header row (header rows have no col-0 label, or their label sets section as needed). If the test exposes an ordering bug, fix the implementation, not the test's layout (the real sheet has both layouts).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.parsing.FamiriosParserTest"`
Expected: PASS (2 tests). Iterate on the parser (NOT the test expectations) if assertions fail.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/parsing/FamiriosParser.kt \
        server/src/test/kotlin/com/jvillada/movi/server/parsing/FamiriosParserTest.kt
git commit -m "feat(server): deterministic Famirios workbook parser (monthly aggregate events)"
```

---

### Task 3: Upload-route integration

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt`

No unit test (route, no test DB) — verified by Task 4 e2e. Make it compile.

- [ ] **Step 1: Branch the upload handler**

In the `post("/api/statements/upload")` handler, the current block:

```kotlin
        val text = StatementParser.extractText(bytes, fileName)

        val docType = StatementParser.detectDocumentType(text)
        if (docType != StatementDocumentType.TRANSACTION_STATEMENT) {
            val msg = when (docType) {
                StatementDocumentType.LOAN_SUMMARY ->
                    "Este documento es un resumen de crédito, no un extracto de movimientos. No contiene transacciones importables."
                StatementDocumentType.INVESTMENT_FUND ->
                    "Este documento es un estado de fondo de inversión. No contiene transacciones importables."
                else -> "Documento no reconocido como extracto de transacciones."
            }
            call.respond(HttpStatusCode.UnprocessableEntity, msg)
            return@post
        }

        val bankName = StatementParser.detectBankName(fileName, text)
        val rules = Stores.merchantRules.getRules(uid)
        val parsed = ClaudeStatementParser.parse(text, rules)
```

becomes:

```kotlin
        val text = StatementParser.extractText(bytes, fileName)

        val docType = StatementParser.detectDocumentType(text)
        if (docType == StatementDocumentType.LOAN_SUMMARY || docType == StatementDocumentType.INVESTMENT_FUND) {
            val msg = when (docType) {
                StatementDocumentType.LOAN_SUMMARY ->
                    "Este documento es un resumen de crédito, no un extracto de movimientos. No contiene transacciones importables."
                else ->
                    "Este documento es un estado de fondo de inversión. No contiene transacciones importables."
            }
            call.respond(HttpStatusCode.UnprocessableEntity, msg)
            return@post
        }

        val isFamirios = docType == StatementDocumentType.FAMIRIOS
        val bankName = if (isFamirios) "Famirios" else StatementParser.detectBankName(fileName, text)
        val parsed = if (isFamirios) {
            WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
                FamiriosParser.parse(wb, LocalDate.now(ZoneOffset.UTC))
            }
        } else {
            ClaudeStatementParser.parse(text, Stores.merchantRules.getRules(uid))
        }
        if (isFamirios && parsed.isEmpty()) {
            call.respond(HttpStatusCode.UnprocessableEntity,
                "El archivo parece un Famirios pero no contiene celdas importables.")
            return@post
        }
```

- [ ] **Step 2: Famirios-aware period**

The current period block:

```kotlin
        val period = runCatching {
            val date = LocalDate.parse(parsed.firstOrNull()?.date ?: "2025-01-01")
            "${monthName(date.monthValue)} ${date.year}"
        }.getOrDefault("")
```

becomes:

```kotlin
        val period = if (isFamirios) {
            val years = parsed.mapNotNull { runCatching { LocalDate.parse(it.date).year }.getOrNull() }
            if (years.isEmpty()) "" else "${years.min()}–${years.max()}"
        } else runCatching {
            val date = LocalDate.parse(parsed.firstOrNull()?.date ?: "2025-01-01")
            "${monthName(date.monthValue)} ${date.year}"
        }.getOrDefault("")
```

- [ ] **Step 3: Imports**

Add to `StatementRoutes.kt` imports:
```kotlin
import com.jvillada.movi.server.parsing.FamiriosParser
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
```

- [ ] **Step 4: Compile + full suite**

Run: `./gradlew :server:compileKotlin :server:test`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt
git commit -m "feat(server): route Famirios uploads through the deterministic parser"
```

---

### Task 4: E2E with the real Famirios export (controller-run)

No code. The controller (main session) runs this — it has Drive access.

- [ ] **Step 1:** Export the real sheet: Drive `download_file_content` of file id `1J7ZTwlxzK5MOwHFIr5hGAonFyxYk8jRg1_rdUG5AjAc` with `exportMimeType: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, base64-decode to `/tmp/famirios.xlsx`.
- [ ] **Step 2:** Start the server; as `demo@movi.app`, create account `{"id":"acc_famirios","name":"Histórico Famirios","type":"SAVINGS","balance":0}`.
- [ ] **Step 3:** `POST /api/statements/upload` with `/tmp/famirios.xlsx`; assert response `bankName == "Famirios"`, `period` spans `2018–2026` (or the sheet's actual year range), transactions count in the hundreds-to-~1.500 range, **zero transactions dated after the current month**, and spot-check categories (an `Arriendo` → Vivienda; a `Crédito…` → Otros).
- [ ] **Step 4:** Import all into `acc_famirios`; verify the account's derived balance equals (sum of INCOME − sum of EXPENSE) of the imported set, and `GET /api/events?accountId=acc_famirios` returns month-end-dated events.
- [ ] **Step 5:** Re-upload the same file; assert the parse result now reports them as `matches` (idempotency), not `newTransactions`.
- [ ] **Step 6:** Stop the server. Delete `/tmp/famirios.xlsx`.

---

## Self-review notes

- **Spec coverage:** Detection (spec A) → Task 1. Parser incl. year tabs, sections, noise set, future-month exclusion, category mapping, date/description/rawText (spec B) → Task 2. Route integration, bankName/period, empty-parse 422 (spec C, D) → Task 3. Idempotency + real-file verification (spec C, E) → Task 4. Synthetic-workbook unit tests (spec E) → Tasks 1–2.
- **Type consistency:** `FamiriosParser.parse(Workbook, LocalDate): List<ParsedTransaction>` used identically in Tasks 2–3; `StatementDocumentType.FAMIRIOS` in Tasks 1 and 3; `ParsedTransaction` fields match the core model (incl. `currency`).
- **Honesty note:** Task 2's test pins exact behavior the implementation sketch may not satisfy on first run (header-after-section ordering); the plan explicitly tells the implementer to fix the parser, not the test.
```
