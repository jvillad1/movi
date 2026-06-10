# Multi-currency & credit-card debt UI — Design

**Date:** 2026-06-09
**Status:** Approved (pending spec review)
**Area:** `:shared` (Compose Multiplatform UI only — no model, network, or server changes)

## Problem

The server now returns per-currency derived balances (`Account.balancesByCurrency`),
a COP debt estimate (`Account.estimatedTotalCop`), and currency-tagged events
(`FinancialEvent.currency`) — merged in PR #1. The UI predates all of it, so:

1. **`AccountsScreen` "TOTAL ACTIVOS"** sums `account.balance` across ALL accounts —
   a credit card's **debt** (positive) is added as if it were an asset, in green.
2. **`AccountDetailScreen` hero** shows "SALDO ACTUAL" in green for a credit card and
   hardcodes "COP"; the multi-currency breakdown and estimate are invisible.
3. **Event rows** render USD amounts through `formatCOP` → a US$100 charge displays
   as "$100" (pesos). The client-side day total in `AccountDetailScreen` re-sums
   mixed currencies (same bug fixed server-side in PR #1, duplicated in the client).
4. **`DashboardScreen` `totalBalance`** has the same asset/debt mixing bug as (1).

## Decisions (locked)

| Decision | Choice |
| --- | --- |
| Accounts top card | **"PATRIMONIO NETO"**: net large, then Activos / Deudas lines |
| Card detail hero | **"DEUDA ACTUAL"** with COP estimate + per-currency breakdown + implied TRM |
| USD amount format | `US$181` (COP stays `$222.933` via existing `formatCOP`) |
| Day totals (client) | COP-only, matching the server's `EventDay.total` semantics |
| Scope | `:shared` only; no test infra added (follows existing pattern — helpers kept pure) |

## Design

### A — Shared display helpers (`ui/components/MoneyDisplay.kt`, new)

Pure functions; single source for every screen:

- `formatMoney(amount: Long, currency: String): String` — `"COP"` → `formatCOP`
  (`$222.933`); `"USD"` → `"US$" + thousands-formatted` (`US$181`); any other code →
  `"<CODE> " + formatted` (future-proof, no special casing).
- `assetsDebtsNet(accounts: List<Account>): Triple<Long, Long, Long>` —
  `(activos, deudas, neto)`. Assets = sum of `balance` over non-CREDIT_CARD accounts.
  Debts = sum over CREDIT_CARD accounts of `estimatedTotalCop ?: balance`
  (estimate is null when the card has no foreign balance — fall back to COP debt).
  Net = activos − deudas.
- `impliedTrm(account: Account): Long?` — `(estimatedTotalCop − balance) / usdBalance`
  rounded, only when `estimatedTotalCop != null` and `balancesByCurrency["USD"]`
  is non-null and non-zero; otherwise null. (The server does not expose the rate;
  this derives the applied one for display.)
- `isDebtAccount(type: AccountType): Boolean` — `type == CREDIT_CARD`.

### B — `AccountsScreen`

- Top card becomes **PATRIMONIO NETO** using `assetsDebtsNet`:
  - Net amount large (28sp `MonoText`), `MinIncome` when ≥ 0, `MinExpense` when < 0.
  - Two `CardRow`-style lines: "Activos" (`MinIncome`) and "Deudas"
    (`MinExpense`, rendered `−$X`); Deudas line only when debts > 0.
- Account rows: CREDIT_CARD rows render `−` + (`estimatedTotalCop ?: balance`) in
  `MinExpense`; an `≈` prefix when the value is an estimate (estimatedTotalCop != null).
  Non-card rows unchanged (green balance).

### C — `AccountDetailScreen`

- Hero card, CREDIT_CARD: label **"DEUDA ACTUAL"**; main figure =
  `estimatedTotalCop ?: balance` in `MinExpense`, `≈` prefix when estimated.
  Below, a breakdown block (only rows that apply):
  - "En pesos" → `formatMoney(balancesByCurrency["COP"], "COP")` (when non-zero)
  - "En dólares" → `formatMoney(balancesByCurrency["USD"], "USD")` (when non-zero)
  - "TRM aplicada" → `≈$<impliedTrm>` (when `impliedTrm` non-null)
  - The existing "COP · <type>" caption is replaced by "<type>" for cards.
- Hero card, asset accounts: unchanged ("SALDO ACTUAL", green/red by sign), but if
  the account has any non-COP balance, the same breakdown block renders beneath.
  The block is one shared composable, `CurrencyBreakdown(account: Account)`, defined
  in `ui/components/MoneyDisplay.kt` next to the helpers it consumes.
- Day grouping (client-side, lines ~50-60): `total` becomes COP-only:
  `items.filter { it.currency == "COP" }.sumOf { ... }` — paired with the
  server-side semantics from PR #1.
- Event rows: amount text becomes `formatMoney(event.amount, event.currency)`
  with the existing +/− prefix and colors.

### D — `DashboardScreen`

- `totalBalance = accounts.sumOf { it.balance }` → `assetsDebtsNet(accounts).third`
  (net). The per-account rows in the dashboard list reuse the same card-row rule
  as B (debt in red with `−`/`≈`).

## Error handling / edge cases

- Card with COP-only debt: no breakdown, no `≈` (exact figure), no TRM line.
- `estimatedTotalCop == null` → fall back to `balance` everywhere it's used.
- Negative USD balance on a card (overpayment): breakdown shows the signed value;
  the estimate already accounts for it server-side.
- `balancesByCurrency` empty (no events): balance 0 renders as today.

## Testing / verification

- No new test infra in `:shared` (none exists; helpers are pure so tests can be
  added later if the module gains a test source set).
- Build gates: `./gradlew :shared:compileDebugKotlinAndroid :webApp:wasmJsBrowserDistribution`
  (or the equivalent compile tasks) must pass.
- Visual verification: run `:server:run` + `:webApp:wasmJsBrowserDevelopmentRun`
  against the local Postgres (which already holds the imported real TC data:
  USD 181 / COP 222.933 / estimate ≈872.377) and verify with Playwright screenshots:
  accounts list (net + red card row), card detail (breakdown + TRM), an asset
  account detail (unchanged), dashboard total.

## Out of scope

- Currency selection when creating accounts/events from the UI (server accepts it;
  UI keeps COP defaults).
- FinanceSummary scope toggle changes; CreditosScreen (loan products) — separate
  feature.
- Historical/point-in-time TRM.
