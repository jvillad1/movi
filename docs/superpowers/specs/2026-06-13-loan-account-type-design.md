# Cuentas de préstamo (LOAN) — diseño

**Fecha:** 2026-06-13
**Alcance:** Nuevo tipo de cuenta `LOAN`. Modelo (`:core`) + lógica de deuda
(`:server`) + UI (`:shared`). Sin cambios en DB, repositorio ni el pipeline de
extractos.

## Contexto

El usuario tiene varios préstamos reales (consumo, libranza, libre inversión,
vehículo) en distintos bancos. La evidencia en `docs/movements/` son **capturas
de pantalla** de la banca en línea: cada una muestra el **estado actual** de un
préstamo (deuda a la fecha / saldo, tasa, plazo, próxima cuota, desembolso), no
una lista de movimientos. No son PDF/xlsx descargables ni extractos de
transacciones.

Por eso el pipeline de extractos (extraer texto → parsear transacciones →
reconciliar) no aplica: no hay transacciones que importar. Además
`StatementParser` ya detecta `LOAN_SUMMARY` y hoy lo **rechaza** (422 "no
contiene transacciones importables"); esa ruta no se toca.

La meta es **sembrar la deuda actual una sola vez**: representar cada préstamo
como una cuenta con su saldo pendiente, igual que la deuda inicial de tarjeta de
crédito que ya se entregó (PRs #1/#2/#4). Sin historial de transacciones, sin
amortización.

## Decisiones de alcance

- **Tipo de cuenta nuevo `LOAN`** (no reutilizar `CREDIT_CARD`): un crédito de
  vehículo no es una tarjeta; debe verse distinto en "Mis cuentas".
- **Entrada manual** vía el formulario de crear cuenta (`CreateAccountSheet`,
  entregado en PR #4). Son ~5 préstamos; un OCR/parser de capturas sería
  maquinaria desproporcionada para leer 5 números.
- **Solo COP.** Las cinco capturas son en COP. El selector COP/USD sigue siendo
  exclusivo de `CREDIT_CARD`; una cuenta `LOAN` envía `currency = "COP"`.
- **Etiqueta "Préstamo", ícono 💸** (🏦 ya es Ahorros, 💳 ya es Corriente/Crédito).
- **Fuera de alcance (YAGNI):** amortización, cuotas, tasa, plazo, fecha de corte
  o de próximo pago (no existen en el modelo `Account`; requerirían backend y
  derivarían hacia "seguimiento en el tiempo"). Préstamos en moneda extranjera.

## Comportamiento

Una cuenta `LOAN` se comporta como deuda, idéntico a `CREDIT_CARD`:

- Al crearse con saldo `S`, el backend genera un evento **EXPENSE "Deuda
  inicial"** por `S` (`openingEventFor`).
- El saldo de una cuenta de deuda es deuda positiva: un **EXPENSE** la aumenta,
  un **INCOME** (pago) la reduce (`signedDelta`).
- La UI la pinta con estilo de deuda y la etiqueta del monto al crear dice
  **"DEUDA INICIAL"**.

## Cambios (puntos de contacto)

Agregar un caso al enum obliga a cada `when` exhaustivo a manejarlo; el
compilador es la red de seguridad.

### Modelo (`:core`)
1. `model/Account.kt` — `enum AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD,
   LOAN, INVESTMENT }`. (Insertar `LOAN` antes de `INVESTMENT`.)

### Servidor (`:server`)
2. `balance/Balances.kt` (≈línea 14) — `signedDelta`: agrupar
   `AccountType.CREDIT_CARD, AccountType.LOAN ->` con la fórmula de deuda
   (`if (type == EXPENSE) amount else -amount`). Actualizar el comentario del
   archivo para nombrar ambos tipos como "saldo = deuda positiva".
3. `balance/OpeningBalance.kt` (≈línea 18-28) — renombrar `isCard` →
   `isDebt = account.type == CREDIT_CARD || account.type == LOAN`; el evento de
   apertura es EXPENSE "Deuda inicial" cuando `isDebt`, si no INCOME "Saldo
   inicial".

### UI (`:shared`)
4. `ui/components/MoneyDisplay.kt` (línea 28) — `isDebtAccount(type)` →
   `type == CREDIT_CARD || type == LOAN`. (Predicado central de deuda; gobierna
   el render rojo/deuda en toda la app.)
5. `ui/accounts/CreateAccountSheet.kt` —
   - Agregar `TypeOption(AccountType.LOAN, "💸 Préstamo")` a `TYPE_OPTIONS`
     (el grid `chunked(2)` ya tolera filas impares).
   - La etiqueta del monto pasa a depender de `isDebtAccount(selectedType)` (en
     vez de `== CREDIT_CARD`) para que diga "DEUDA INICIAL" también en `LOAN`.
   - El selector de moneda COP/USD **se mantiene** condicionado a
     `selectedType == CREDIT_CARD` (los `LOAN` quedan en COP). La línea de
     `currency = if (selectedType == CREDIT_CARD) selectedCurrency else "COP"`
     no cambia: un `LOAN` ya resuelve a COP.
6. `ui/accounts/AccountsScreen.kt` (≈línea 287) — agregar rama
   `AccountType.LOAN -> "💸" to "Préstamo"`.
7. `ui/accounts/AccountDetailScreen.kt` (≈línea 333) — misma rama
   `AccountType.LOAN -> "💸" to "Préstamo"`.
8. `ui/dashboard/DashboardScreen.kt` (≈línea 241) — agregar rama
   `AccountType.LOAN -> "Préstamo"`.

### Sin cambios
- `AccountRoutes` (genérico sobre `Account`), esquema DB (el tipo se guarda como
  string), `LocalRepository`/`AccountRoutes` (`AccountType.valueOf`), y todo el
  pipeline de extractos (el rechazo de `LOAN_SUMMARY` se queda como está; es otra
  ruta — parseo de documentos de transacciones, ajena a este sembrado manual).

## Verificación

- **Unit (`:server`):** extender los tests de `Balances`/`OpeningBalance`:
  - una cuenta `LOAN` con apertura 540_786 → deuda 540_786;
  - un INCOME (pago) de 40_786 → deuda 500_000;
  - `openingEventFor` emite EXPENSE "Deuda inicial" para `LOAN`.
- **Compilación:** `:shared` y `:server` compilan (los tres `when` de ícono/etiqueta
  no compilan hasta cubrir `LOAN`).
- **Manual (web/dispositivo):** crear "Crédito Vehículo Santander" / Préstamo /
  161_115_271 → aparece en "Mis cuentas" con estilo de deuda; el hero **DEUDA
  ACTUAL** muestra el saldo; registrar un pago (INCOME) lo reduce. Sembrar los ~5
  préstamos reales con su deuda a la fecha.
