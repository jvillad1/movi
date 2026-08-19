# Ola 5 — T1: tipos de cuenta e Inversiones (F56 + F50)

## Qué cambié

### F56 — Dos tipos de cuenta: Dinero e Inversión

1. **`core/.../shared/model/Account.kt`** — `AccountType` se queda igual (compat de DB/wire).
   Nuevo: `enum class AccountGroup { DINERO, INVERSION, DEUDA }` + extensiones
   `AccountType.group` y `AccountType.groupLabel` ("Dinero" / "Inversión" / "Deuda").
   CASH/CHECKING/SAVINGS → DINERO; INVESTMENT → INVERSION; CREDIT_CARD/LOAN → DEUDA (se quedan
   mapeados aunque ya no se puedan crear desde Cuentas, por si hay filas viejas).

2. **`CreateAccountSheet.kt`** — el selector de tipo pasa de 6 chips en grilla 2×2 a **2**
   tarjetas de ancho completo, cada una con ícono + línea de explicación:
   - **Dinero** (guarda `AccountType.SAVINGS`) — «La plata disponible: ahorros, corriente,
     efectivo»
   - **Inversión** (guarda `AccountType.INVESTMENT`) — «Plata guardada: CDT, fondos»

   Debajo, nota de texto (no botón): «¿Tarjetas o préstamos? Se cargan en Créditos».
   Tarjeta/Préstamo salieron del selector (F51/F52) — ya no se pueden crear como cuenta.
   Nuevo parámetro `initialType: AccountType = AccountType.SAVINGS` para que otras pantallas
   abran la hoja con el tipo ya elegido (lo usa Inversiones, ver F50).

   **Moneda**: antes era exclusiva de tarjeta de crédito (que salió del selector). Decisión: el
   selector de moneda (COP/USD) queda disponible para **Inversión** — un CDT en dólares existe
   de verdad — y **Dinero** queda fijo en COP. `save()` ajustado: `currency = if (selectedType
   == AccountType.INVESTMENT) selectedCurrency else "COP"`.

3. **`AccountsScreen.kt`** — el subtítulo de cada fila ya no repite el tipo crudo
   ("Efectivo"/"Ahorros"/"Corriente" son el mismo cálculo) — ahora usa `account.type.groupLabel`.
   El ícono se queda por tipo específico (glifo, no texto), función renombrada
   `accountTypeIcon`. Cuentas de deuda que ya existan (CREDIT_CARD/LOAN) siguen listándose con
   su ícono y muestran "Deuda" — no toqué el filtrado de la lista en sí (fuera del alcance de
   F56/F50; `AccountDetailScreen.kt` y las rutas de borrado quedaron sin tocar, como se pidió).

4. **SMS/Extractos — `it.type != AccountType.CASH`** (`SMSScreens.kt`,
   `StatementReviewScreen.kt`): decidí **acotar** el fallback a
   `it.type.group == AccountGroup.DINERO && it.type != AccountType.CASH` en vez de dejarlo
   igual. Antes "cualquier cuenta que no sea Efectivo" también podía resolver a una cuenta de
   Inversión o a una deuda como destino por defecto de un SMS/extracto bancario — no tiene
   sentido postear un movimiento de banco directo a un CDT o a una tarjeta. Acotarlo a Dinero
   (sin Efectivo, que ya se probó como segunda opción) es más correcto y no cambia el
   comportamiento para el caso común (cuentas de banco = Dinero).

### F50 — Inversiones unificado con Cuentas

5. **`InversionesScreen.kt`** — reescrita: ya no lee `getHoldings()` (modelo de "posiciones"
   sin alta). Ahora lee `getAccounts()` y filtra `type == AccountType.INVESTMENT`. "Patrimonio
   invertido" = suma de esas cuentas. Lista con nombre + saldo, cada fila navega a
   `Screen.AccountDetail(account.id)`. El "+" volvió (F21 lo había sacado por no prometer un
   alta que no existía) — ahora abre `CreateAccountSheet(initialType = AccountType.INVESTMENT)`
   y refresca al crear. Se sacó el gráfico por período (1M/3M/6M/1A/Todo) y el sparkline: eran
   una curva fija inventada, no datos reales — mismo defecto que Ola 4 ya sacó del Balance del
   Inicio.

6. **`DashboardLogic.kt`** — `quickLinkFigure("investments", …)` pasó de `data.holdings` a
   `data.accounts.filter { it.type == AccountType.INVESTMENT }`. Campo `holdings` retirado de
   `DashboardData`.

7. **`DashboardScreen.kt`** — se quitó el `launch { getHoldings() }` (ya no hace falta, la
   cifra sale de `data.accounts`, que ya se cargaba).

8. **Cliente — `getHoldings` retirado por completo**: `WalletRepository` (interfaz),
   `WalletRepositoryImpl` (Ktor), `LocalRepository` (:core nonWasmMain) y `NoOpRepository`
   (test double). El **endpoint del server se queda** (`GET /api/holdings` en
   `FinanceRoutes.kt`, siempre devolvía `emptyList()` — nunca tuvo datos reales) con un
   comentario nuevo anotando que quedó sin consumidor.

9. **`Sparkline.kt`** — se borró `InvestmentSparkline` (única consumidora era la pantalla que
   acabo de reescribir); `SimpleSparkline` se queda, la usa `OnboardingScreens.kt`.

## Textos exactos (tuteo neutro)

- «Dinero» / «La plata disponible: ahorros, corriente, efectivo»
- «Inversión» / «Plata guardada: CDT, fondos»
- «¿Tarjetas o préstamos? Se cargan en Créditos»
- «Aún no tienes cuentas de inversión»
- «Mis cuentas de inversión»
- «+ Nueva» (mismo texto que Cuentas)

## Tests

- `core/src/commonTest/.../shared/model/AccountGroupTest.kt` (nuevo): mapeo completo de los 6
  `AccountType` a su `AccountGroup`, y `groupLabel` en español neutro. 2 tests, verdes.
- `shared/src/commonTest/.../ui/dashboard/DashboardLogicTest.kt`: nuevo test
  `investments toma cuentas tipo INVESTMENT, no el modelo de posiciones retirado` — arma
  `DashboardData` con cuentas Dinero + Inversión, verifica que `quickLinkFigure("investments", …)`
  solo suma las INVESTMENT ($3.700.000 · 2 cuentas). 14 tests en el archivo, todos verdes.
- `VoseoScanTest` (:core:jvmTest) sigue verde — los textos nuevos son tuteo neutro.

## Verificación (salida real)

```
$ ./gradlew :server:test :core:jvmTest :shared:testDebugUnitTest \
    :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs \
    :shared:compileKotlinIosSimulatorArm64 --console=plain -q
(sin salida — BUILD exitoso, exit code 0)
```

Conteos de los tests nuevos (test-results XML):
- `AccountGroupTest`: `tests="2" failures="0" errors="0"`
- `DashboardLogicTest`: `tests="14" failures="0" errors="0"`

## Archivos tocados

- `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Account.kt`
- `core/src/commonTest/kotlin/com/jvillada/movi/shared/model/AccountGroupTest.kt` (nuevo)
- `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- `core/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`
- `core/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`
- `server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/AccountsScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/accounts/CreateAccountSheet.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/Sparkline.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardLogic.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/extractos/StatementReviewScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/investments/InversionesScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt`
- `shared/src/commonTest/kotlin/com/jvillada/movi/ui/dashboard/DashboardLogicTest.kt`

No toqué `AccountDetailScreen.kt`, `PresupuestosScreen.kt`, `SuscripcionesScreen.kt` ni rutas
de server de budgets/subscriptions/delete (worktree paralelo).

## Dudas

- `AccountsScreen.kt` sigue listando cuentas CREDIT_CARD/LOAN si ya existen en la base (con
  ícono propio y "Deuda" como grupo) — no las filtré de la lista de Cuentas porque F56/F50 no
  lo piden explícitamente y tocar eso se cruza con F20 (Créditos, otro desarrollador). Si la
  decisión real es que Cuentas nunca vuelva a mostrar deuda, es un ítem de una línea
  (`accounts.filter { it.type.group != AccountGroup.DEUDA }`) para cuando F20/F55 estén
  mergeados — no lo hice para no adelantarme a esa rama.
- No agregué un test dedicado para `CreateAccountSheet` (es Compose UI sin lógica pura que
  extraer) ni para `AccountsScreen`/`InversionesScreen` — seguí el patrón existente del repo
  (esas pantallas no tienen tests de Compose, la lógica que sí es pura vive y se prueba en
  `DashboardLogic.kt`/`AccountGroupTest.kt`).
