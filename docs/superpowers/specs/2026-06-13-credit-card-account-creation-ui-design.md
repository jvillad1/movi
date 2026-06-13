# Crear tarjetas de crédito desde la UI — diseño

**Fecha:** 2026-06-13
**Alcance:** Cambio solo de UI. Cero cambios en backend, repositorio o modelo.
**Archivo único:** `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt`

## Contexto

El backend ya soporta crear cuentas `CREDIT_CARD` con deuda inicial en cualquier
moneda (`POST /api/accounts` → `openingEventFor()` genera un evento `EXPENSE`
"Deuda inicial" en la moneda de la cuenta; el server enriquece con
`balancesByCurrency` + `estimatedTotalCop`). La feature multi-moneda está mergeada
y en producción (PRs #1/#2). Lo único que falta es exponer el tipo en el formulario:
hoy `TYPE_OPTIONS` solo ofrece CASH, SAVINGS, CHECKING, INVESTMENT.

## Decisiones de alcance

- Incluir selector de moneda **COP/USD** para la deuda inicial (caso de uso real:
  tarjetas con deuda en USD).
- El selector de moneda aparece **solo cuando el tipo es Tarjeta de crédito**. Los
  demás tipos quedan siempre en COP, sin fricción adicional.
- **Fuera de alcance (YAGNI):** cupo/límite de crédito y fecha de corte/pago (no
  existen en el modelo `Account`; requerirían cambios de backend). Moneda para
  otros tipos de cuenta.

## Cambios en `CreateAccountSheet.kt`

1. **Nueva opción de tipo.** Agregar `TypeOption(AccountType.CREDIT_CARD,
   "💳 Tarjeta de crédito")` a `TYPE_OPTIONS`. El grid pasa de 4 a 5 chips; el
   layout `chunked(2)` ya tolera la fila impar (el quinto chip queda solo,
   ocupando media fila por el `weight(1f)`).

2. **Estado nuevo.** `var selectedCurrency by remember { mutableStateOf("COP") }`.

3. **Etiqueta dinámica del monto.** Cuando `selectedType == CREDIT_CARD`, la
   `SectionLabel` dice **"DEUDA INICIAL"** en vez de "SALDO INICIAL".

4. **Selector de moneda.** Visible solo si `selectedType == CREDIT_CARD`: dos chips
   (COP / USD), mismo estilo visual que los chips de tipo, COP por defecto.

5. **Reset al cambiar de tipo.** Al seleccionar un tipo distinto de `CREDIT_CARD`,
   forzar `selectedCurrency = "COP"` para no mandar una moneda colada en una cuenta
   que no la representa.

6. **Submit.** El `Account` construido pasa a incluir:
   ```kotlin
   currency = if (selectedType == AccountType.CREDIT_CARD) selectedCurrency else "COP"
   ```
   El resto (`id = ""`, `name`, `type`, `balance`) sin cambios.
   `Repositories.wallets.createAccount(...)` sin tocar.

## Flujo (ya implementado en backend)

UI → `POST /api/accounts` con `{type: CREDIT_CARD, balance, currency}` →
`openingEventFor()` crea evento `EXPENSE` "Deuda inicial" en esa moneda →
server enriquece con `balancesByCurrency` + `estimatedTotalCop` (USD × TRM) →
la UI ya renderiza el hero **DEUDA ACTUAL** con desglose por moneda.

## Verificación

- `:shared` compila (build web app wasm).
- Manual en la web (https://movi-project-production.up.railway.app tras `railway up`,
  o local): crear una tarjeta con deuda en USD → aparece en "Mis cuentas" y
  **DEUDA ACTUAL** muestra desglose USD + TRM implícita. Crear una en COP → desglose
  COP simple. Verificar que el selector de moneda desaparece al elegir otro tipo y
  que la moneda vuelve a COP.
- La lógica de deuda (`openingEventFor` / `computeBalances`) ya está cubierta por
  tests del server; no se toca esa ruta.
