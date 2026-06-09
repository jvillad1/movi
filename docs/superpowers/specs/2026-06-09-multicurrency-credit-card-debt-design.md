# Multi-currency credit-card debt + TRM estimate — Design

**Date:** 2026-06-09
**Status:** Approved (pending spec review)
**Area:** `:core` (model), `:server` (parsing, routes, fx)

## Problem

Testing movi's statement import with real Bancolombia data surfaced two defects in
how foreign-currency and credit-card movements are handled (see
`memory/movi-statement-import-findings.md`):

1. **USD imported as COP.** `ClaudeStatementParser` drops the statement's currency
   column, so `100 USD` lands as `100` pesos. Credit cards routinely carry USD
   charges (Anthropic, PayPal, DirecTV…).
2. **Card payments mis-modeled.** Automatic card payments (`ABONO DEBITO
   AUTOMATIC`) flip to INCOME with no coherent debt semantics, because balance
   updates assume an asset account (`INCOME→+`, `EXPENSE→−`).

## Goals

- A credit card can hold debt **in COP and in USD simultaneously**, tracked per
  currency in the transaction's **native** value (no conversion at import).
- Provide an **estimated total debt in COP** = `copDebt + usdDebt × TRM`, where
  the TRM is refreshed periodically from a public source.
- Credit-card debt direction is correct: a purchase **increases** debt, a payment
  **decreases** it. Card balance is shown as **positive debt**.

## Non-goals (YAGNI)

- Currencies beyond COP and USD (design generalizes, but only USD↔COP is wired).
- Historical/point-in-time FX (the estimate uses the latest TRM only).
- Converting and **storing** COP amounts for USD rows — we keep native values and
  estimate on read.

## Decisions (locked)

| Decision | Choice |
| --- | --- |
| FX source | **Official TRM via datos.gov.co** Socrata `32sa-8pi3.json` (free, no key, daily) |
| Balance representation | **Derived per-currency from events** (no stored running balance) |
| Card debt sign | Card balance = **positive debt**; CREDIT_CARD inverts the delta |
| Card payments (ABONO / PAGO ALTERNATIVO) | **Kept** as debt-reducing movements (not excluded) |
| TC statement target | Imported into a **new CREDIT_CARD account** |

## Design

### A — Native currency on events (`:core` + DB)

- Add `currency: String = "COP"` to `FinancialEvent` and `ParsedTransaction`
  (`core/.../model/`). Default keeps every existing call site and stored row COP.
- Add a `currency` column to the `Events` table (`server/.../db/Tables.kt`),
  `varchar(3)` default `"COP"`. Exposed's `createMissingTablesAndColumns(Events)`
  (already called at startup) adds it to existing DBs.
- `createEventFromParsed` and the manual event-create path persist
  `event.currency`.

### B — Parser preserves currency, no conversion (`ClaudeStatementParser`)

- Prompt change: emit `amount` in the row's **native** currency (still positive
  integer, rounded to the unit) **plus** a `currency` field (`"COP"` | `"USD"`).
  The currency comes from the statement's "Tipo de moneda" column when present,
  else `"COP"`.
- `ClaudeRow` gains `currency: String = "COP"`; `parseJson` maps it onto
  `ParsedTransaction.currency`. **No arithmetic conversion happens here.**
- Remove the "descartá / convertí a COP" framing for foreign rows; remove the
  blanket **PAGO ALTERNATIVO exclusion** so card payments are imported. Keep
  excluding non-movements (saldos, totales, headers).
- Revert the earlier import-time category fix? No — the category constraint
  (inject `PREDEFINED_CATEGORIES`) stays; it is orthogonal and already verified.

### C — Account-type-aware balance delta (single helper)

Today the delta `INCOME→+ / EXPENSE→−` is duplicated in 3 places
(`StatementRoutes.createEventFromParsed`, `EventRoutes` create, `EventRoutes`
void-reversal). Extract one helper:

```
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD -> if (type == EXPENSE) +amount else -amount  // debt
        else                    -> if (type == INCOME)  +amount else -amount  // asset
    }
```

### D — Per-currency balances derived from events

- Balances become **derived**: stop using the stored `Accounts.balance` column as
  the source of truth (the write-time `Accounts.balance + delta` updates in
  import/create/void are removed). On read, aggregate the account's (non-voided)
  events **grouped by currency**, summing `signedDelta(account.type, ev.type,
  ev.amount)`. The DB column is left in place but ignored (dropping it is a
  separate migration, out of scope).
- Event volumes are small (personal finance); aggregate in Kotlin, not SQL.
- Extend the `Account` wire type with computed, read-only fields (defaults keep
  back-compat):
  - `balancesByCurrency: Map<String, Long> = emptyMap()` — e.g. `{ "COP": …, "USD": … }`
  - `estimatedTotalCop: Long? = null` — `copBalance + usdBalance × TRM`, set only
    when a non-COP balance exists.
  - `balance` remains and equals the **COP** component (so existing UI/summary
    code keeps working).
- Populated in `AccountRoutes` GET (list + by id). `FinanceSummary` aggregation
  is out of scope for this change (follow-up note).

### E — TRM service (`server/.../fx/FxRateService.kt`)

- Fetch the latest TRM from `https://www.datos.gov.co/resource/32sa-8pi3.json`
  (most-recent row; field `valor`). Uses the server's Ktor `HttpClient`.
- **Cache** the value with a daily TTL; lazy refresh on read when stale.
- **Fallback** chain if the API is unreachable: last cached value → configurable
  constant (`USD_COP_RATE` env, default ~4000) → estimate omitted (`null`) rather
  than wrong.
- Exact Socrata field names verified during implementation.

## Data flow (TC import)

```
upload .xlsx ─▶ POI text ─▶ Claude parse (native amount + currency)
   ─▶ ParsedTransaction{amount, currency} ─▶ import into CREDIT_CARD account
   ─▶ events stored with currency
GET /api/accounts/{id}
   ─▶ aggregate events by currency via signedDelta
   ─▶ { balance: copDebt, balancesByCurrency:{COP,USD}, estimatedTotalCop }
        where estimatedTotalCop = copDebt + usdDebt × TRM(cached)
```

## Error handling

- TRM API down → fallback chain (above); the per-currency balances are still
  exact, only the COP estimate degrades or is omitted.
- Unknown/blank currency from the parser → treated as `"COP"`.
- Voided events excluded from aggregation (existing `VoidEvents` logic).

## Testing

- Unit: `signedDelta` for every `AccountType` × `{INCOME,EXPENSE}`.
- Unit: `parseJson` maps `currency`; defaults to `"COP"` when absent.
- Unit: estimate math `copDebt + usdDebt × rate` with a stubbed rate.
- Unit: `FxRateService` parses a sample Socrata payload; fallback when fetch fails
  (HTTP mocked — no live call in the suite).
- Manual harness (existing, temporary): import `bancolombia tc mastercard.xlsx`
  into a CREDIT_CARD account, assert COP debt and USD debt aggregate correctly and
  the estimate is `copDebt + usdDebt × rate`.

## Affected files

- `core/.../model/FinancialEvent.kt`, `Statement.kt` (currency field), `Account.kt`
  (computed fields)
- `server/.../db/Tables.kt` (currency column)
- `server/.../parsing/ClaudeStatementParser.kt` (prompt + ClaudeRow + parseJson)
- `server/.../routes/StatementRoutes.kt`, `EventRoutes.kt`, `AccountRoutes.kt`
  (signedDelta helper, currency persistence, derived balances)
- `server/.../fx/FxRateService.kt` (new)
- Tests under `server/src/test/...`

## Open point for spec review

Card payments (`ABONO`, `PAGO ALTERNATIVO`) are now **kept** as debt-reducing.
This is the consistent choice given a dedicated CREDIT_CARD account, but reverses
the prompt's previous PAGO-ALTERNATIVO exclusion. Confirm this is desired (the
double-count it once guarded against only arises if the funding account's own
statement is also imported — a reconciliation concern, not a reason to drop the
row).
```
