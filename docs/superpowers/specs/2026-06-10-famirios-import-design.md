# Famirios historical import — Design

**Date:** 2026-06-10
**Status:** Approved (pending spec review)
**Area:** `:server` (parsing + upload routing). No UI, model, or DB changes.

## Problem

The user's finance history (2018→today) lives in "Famirios", a Google Sheets
budget tracker (folder "Finanzas" in Drive; one tab per year; rows = income/
expense categories, columns = months). movi has no historical data older than
the imported bank statements. Re-keying ~8 years × ~15 categories by hand is
not viable, and the statement pipeline's Claude parser is the wrong tool for a
known, deterministic format with ~1.500 data points (token limits, cost,
non-determinism).

## Goals

- Import Famirios as **monthly aggregate events**: one `FinancialEvent` per
  non-zero (category × month) cell, into a dedicated "Histórico Famirios"
  account, through the **existing** statement upload → review → import flow.
- Deterministic parsing — no LLM call for this format.
- Re-import is idempotent via the existing reconciliation matching.

## Non-goals (YAGNI)

- Reconstructing individual transactions (the source only has monthly sums).
- Live Google Sheets sync / OAuth (user downloads the .xlsx manually).
- Auto-creating recurring rules, budgets, or credits from the sheet (seeded
  separately; see memory note `movi-real-data-seed`).
- Distinguishing budgeted vs. actual values in past months (the sheet does not
  encode the difference; values are taken as the user's record).

## Decisions (locked)

| Decision | Choice |
| --- | --- |
| Scope | Monthly aggregates only |
| Delivery | Existing statement pipeline (`POST /api/statements/upload`) |
| Parsing | Deterministic (Apache POI), bypassing `ClaudeStatementParser` |
| Target account | User-created dedicated account (e.g. "Histórico Famirios") — flow already lets the user pick the account at import |
| Future months | **Excluded** — Famirios pre-fills the whole year with projections; only months `<=` the current month (server clock) are imported |
| Event date | Last day of the month, midnight UTC |

## Design

### A — Detection

- `StatementDocumentType` gains `FAMIRIOS`.
- `StatementParser.detectDocumentType(text)` returns it when the extracted text
  contains the format's markers (all three, case-insensitive): `"Resumén"`,
  `"Tipo de ingreso"`, `"Gastos Fijos"`. (Bank statements contain none of
  these; the How-to tab text alone does not contain all three.)
- `detectBankName` is bypassed for this type — the result's `bankName` is
  `"Famirios"` and `period` is the year range (e.g. `"2018–2026"`).

### B — `FamiriosParser` (new, `server/.../parsing/FamiriosParser.kt`)

Works on the **workbook**, not the flattened text — the year lives in the tab
name, which `extractSpreadsheet` discards. The upload route, on detecting a
`.xls/.xlsx` upload, opens the workbook once (`WorkbookFactory`) and routes:
Famirios → `FamiriosParser.parse(workbook, today)`; otherwise the existing
text + Claude path.

Parsing rules per sheet (tab):
- A tab is a **year tab** when its name parses as a year (`2018`..`2099`).
  Other tabs (How-to guide, forms data) are skipped.
- Within a year tab, locate section header rows: a cell `"Gastos"` or
  `"Tipo de ingreso"` (section start) and the 12 month columns from the nearest
  preceding header row containing `"Jan".."Dec"` (Famirios uses English month
  abbreviations; some year tabs prepend an extra column, e.g. "Death" — month
  columns are matched by header NAME, not position).
- Data rows under a section: first cell = row label; numeric cells under month
  columns. A row is **skipped** when its label is blank or matches the noise
  set: `Resumén`, `Dineros iniciales`, `Ingresos`, `Gastos Fijos`,
  `Gastos Extraordinarios` (summary block), `YTD`, `Promedio`, `Presupuesto`,
  `Total`, `Gastos (%)` (and everything after it until the next section),
  contains `#REF!`, or equals the section headers themselves.
- Each remaining cell with value ≠ 0 for month `m` of year `y`, with
  `(y, m) <= (currentYear, currentMonth)`, becomes a `ParsedTransaction`:
  - `date` = last day of `(y, m)` (ISO `yyyy-MM-dd`)
  - `merchant` = row label (trimmed)
  - `amount` = `abs(round(value))`, `currency = "COP"`
  - `type` = `INCOME` for `Tipo de ingreso` section rows, `EXPENSE` for
    `Gastos`/`Gastos extraordinarios` rows
  - `category` = deterministic mapping (below)
  - `description` = `"Famirios · <label> · <month name> <year>"`
  - `rawText` = `"<tab>!<label>:<month>=<raw value>"` (audit trail)

Category mapping (label contains, case/accent-insensitive; first match wins):
- `crédito|tarjeta|deuda|nubank` → `Otros` (debt payments have no natural
  category; listed FIRST so `Crédito Vehículo` lands here, not in Transporte)
- `arriendo|apartamento|hogar|casa|administr` → `Vivienda`
- `mercado|rappi` → `Comida`
- `gasolina|vehículo|carro|soat|seguro del carro` → `Transporte`
- `celular|internet|servicios` → `Servicios`
- `gym|coomeva|prepagada|salud|eps` → `Salud`
- `diversión|entreten|happy` → `Entretenimiento`
- income rows: `pay|salario|nómina|cesantías` → `Salario`; everything else
  (`bonos`, `proyecto`, `extra`, `nata`…) → `Otros ingresos`
- expense fallback → `Otros`

### C — Upload route integration (`StatementRoutes.kt`)

- The handler keeps its shape; for spreadsheets it inspects the workbook first.
  If Famirios: `parsed = FamiriosParser.parse(...)`, `bankName = "Famirios"`,
  `period = "<minYear>–<maxYear>"`, and the existing reconciliation/matching
  and `StatementParseResult` response run unchanged. The import POST is
  untouched — monthly aggregates land as `EventSource.STATEMENT` events with
  `RECONCILED` status like any statement import.
- Idempotency: re-uploading matches existing events by the current rule
  (amount + currency + ±2 days) → they surface as `matches`, not duplicates.

### D — Error handling

- Year tab with no recognizable month header row → tab skipped (logged count of
  skipped tabs in the server log; parse continues).
- Non-numeric cells in month columns → ignored.
- Workbook with Famirios markers but zero parsed transactions → respond
  `422 Unprocessable Entity` with a message, mirroring the existing
  loan-summary rejection style.

### E — Testing

- Unit tests for `FamiriosParser` against a **synthetic workbook built with POI
  in the test** (no fixture file, no network): 2 year tabs + 1 noise tab,
  summary rows to skip, a future month to exclude, both sections, category
  mapping cases, the extra leading column variant, and a `#REF!` row.
- Unit test for `detectDocumentType` marker logic.
- Manual e2e: real Famirios export through the web UI into a "Histórico
  Famirios" account (verified by the resulting per-month events and the
  account's derived balance).

## Affected files

- `server/.../parsing/StatementParser.kt` — `FAMIRIOS` doc type + markers
- `server/.../parsing/FamiriosParser.kt` — new
- `server/.../routes/StatementRoutes.kt` — workbook-first routing for xlsx
- `server/src/test/.../parsing/FamiriosParserTest.kt` — new
