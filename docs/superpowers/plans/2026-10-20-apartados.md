# Apartados Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the owner reserve part of an account's balance for a purpose, so Movi stops showing it as available in «Tu plata» and «Te quedan», without touching the real balance.

**Architecture:** A new server table `apartados` with CRUD routes; the server derives each apartado's `restante` (monto minus spending in its linked category since `desde`). `:core` gets the model and the pure math; `patrimonioDe` and `PlataDelDisponible` learn to subtract it. The client reads apartados through `remote_cache` (like Metas) and writes online-only.

**Tech Stack:** Kotlin Multiplatform, Ktor + Exposed (server), Compose Multiplatform (`:shared`), SQLDelight only for the existing cache.

**Spec:** `docs/superpowers/specs/2026-10-20-apartados-design.md`

## Global Constraints

- Every user-visible string in Spanish neutral latinoamericano with tuteo, no voseo (`VoseoScanTest`).
- New wire fields carry defaults (`= 0L` / `= null`) so installed APKs keep working.
- All money is COP `Long` pesos. Apartados apply only to accounts where `esDeTuPlata()` is true (not debt, not bien, not condicionada).
- `restante = clamp(monto − consumido, 0, monto)`; per account `efectivo = min(Σ restante, max(0, valorEnPesosDe(cuenta)))`.
- `consumido` = Σ amount of non-voided `EXPENSE` events, currency COP, same `account_id`, `category` == apartado.categoria, `timestamp ≥ desde`. No categoria ⇒ consumido = 0.
- Neto del patrimonio must not change: `loQueTienes = tuPlata + apartado + condicionado + bienes`.
- Never run `./gradlew build`. CI suite: `./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs` (needs `local.properties` in the worktree; see memory movi-ci).
- New server table with `user_id` ⇒ entry in `TABLAS_DEL_USUARIO` (`BorrarLaCuentaTest` enforces) and in `TABLAS_EXPORTADAS`.

## Review Focus

- Apartado larger than the account balance (balance dropped below it): Tu plata must not go negative for that account; `efectivo` caps at the balance.
- Spending in the linked category beyond the monto: `restante` stops at 0, never negative.
- A voided expense in the linked category must not count as consumed.
- Deleting the account deletes its apartados; an apartado pointing to a debt / condicionada / bien account is rejected with 400.
- A client with no apartados (or an old server without the route) renders exactly as today.

---

### Task 1: Core model and math

**Files:**
- Create: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Apartado.kt`
- Modify: `core/.../model/Patrimonio.kt`, `core/.../model/DisponibleDelPeriodo.kt`, `core/.../model/DashboardSummary.kt`
- Test: `core/src/commonTest/kotlin/com/jvillada/movi/shared/model/ApartadoTest.kt`, extend `PatrimonioTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Apartado(val id: String = "", val accountId: String, val proposito: String, val monto: Long, val categoria: String? = null, val desde: Long = 0L, val restante: Long = monto)`
  - `const val MAX_PROPOSITO_APARTADO = 60`
  - `fun restanteDelApartado(monto: Long, consumido: Long): Long`
  - `fun apartadoPorCuenta(apartados: List<Apartado>): Map<String, Long>` (Σ restante by accountId)
  - `fun patrimonioDe(accounts: List<Account>, apartados: List<Apartado> = emptyList()): Patrimonio`
  - `Patrimonio.apartado: Long = 0L`, `Patrimonio.apartadoPara: String? = null` (single distinct propósito or null)
  - `PlataDelDisponible.apartado: Long = 0L`, subtracted in `neto`
  - `DashboardSummary.apartado: Long? = null`, read by `plataDelDisponibleDe`

- [ ] **Step 1: Write failing tests** in `ApartadoTest`:
  - `restanteDelApartado(8_000_000, 4_000_000) == 4_000_000`; `(8_000_000, 9_000_000) == 0`; `(8_000_000, 0) == 8_000_000`.
  - `patrimonioDe` with Nu SAVINGS balance 8_217_464 and apartado restante 8_000_000 ⇒ `tuPlata == 217_464`, `apartado == 8_000_000`, `apartadoPara == "Colegio"`, `neto` equal to `patrimonioDe(accounts).neto`.
  - Apartado 8_000_000 on an account with balance 3_000_000 ⇒ `apartado == 3_000_000`, account contributes 0 to `tuPlata`.
  - Apartado on an INVESTMENT account lowers `invertido`, not `disponible`.
  - Apartado whose accountId is a condicionada or debt account is ignored.
  - `plataDelDisponibleDe(summary with apartado = 500_000)` ⇒ `neto` 500_000 lower than without it.
- [ ] **Step 2:** `./gradlew :core:jvmTest --tests '*ApartadoTest*' --tests '*PatrimonioTest*'` → FAIL (unresolved).
- [ ] **Step 3:** Implement. In `patrimonioDe`, per libre account: `efectivo = min(Σrestante de la cuenta, max(0, valorEnPesosDe(it)))`; subtract from that account's group (disponible/invertido); `apartado = Σ efectivo`. Update KDoc of `Patrimonio`/`patrimonioDe` (the «regla del patrimonio»).
- [ ] **Step 4:** Same command → PASS; also `./gradlew :core:jvmTest` all green.
- [ ] **Step 5:** Commit `Apartados: el modelo y la cuenta en :core`.

### Task 2: Server table, routes and restante

**Files:**
- Modify: `server/.../db/Tables.kt` (new `object Apartados`), `server/.../db/DatabaseFactory.kt:72` (add to `SchemaUtils.create`), `server/.../cuenta/BorrarLaCuenta.kt` (`TABLAS_DEL_USUARIO`), `server/.../exportar/ExportarDatos.kt` (`TABLAS_EXPORTADAS`), `server/.../routes/AccountRoutes.kt` (DELETE cleanup next to Goals ~589), `server/.../plugins/Routing.kt`
- Create: `server/.../apartados/ApartadosDelUsuario.kt`, `server/.../routes/ApartadoRoutes.kt`
- Test: `server/src/test/.../routes/ApartadoRoutesTest.kt`; `BorrarLaCuentaTest` seeder if needed

**Interfaces:**
- Consumes: `Apartado`, `restanteDelApartado`, `MAX_PROPOSITO_APARTADO` (Task 1).
- Produces: `fun Transaction.apartadosDelUsuario(uid: String, voidedIds: Set<String>): List<Apartado>` (restante filled). Routes: `GET /api/apartados` → `List<Apartado>`; `POST /api/apartados` body `Apartado` (idempotent upsert by `id`, 201/200) ; `PUT /api/apartados/{id}` body `Apartado`; `DELETE /api/apartados/{id}` → 204.
- Table columns: `id varchar(50) PK, user_id varchar(50) indexed, account_id varchar(50), proposito varchar(60), monto long, categoria varchar(100) nullable, desde long, created_at long, last_edited_at long nullable`. `desde` defaults to now when the client sends 0.

- [ ] **Step 1: Failing tests** in `ApartadoRoutesTest` (use the existing test app helpers of `GoalRoutesTest`/`BienesRoutesTest`):
  - POST then GET returns the apartado with `restante == monto`.
  - With categoria «Hija» and an EXPENSE 4_000_000 «Hija» on that account after `desde` ⇒ `restante == 4_000_000`; the same expense voided ⇒ `restante == monto`; an expense before `desde` or in another account doesn't count.
  - POST on a debt account / condicionada account / another user's account ⇒ 400/404; monto ≤ 0 or blank propósito ⇒ 400.
  - DELETE `/api/accounts/{id}` removes its apartados.
  - GET dashboard summary is covered in Task 3, not here.
- [ ] **Step 2:** `./gradlew :server:test --tests '*ApartadoRoutesTest*'` → FAIL.
- [ ] **Step 3:** Implement table, routes, helper, cleanup, deletion/export lists.
- [ ] **Step 4:** `./gradlew :server:test --tests '*ApartadoRoutesTest*' --tests '*BorrarLaCuentaTest*' --tests '*ExportRoutesTest*'` → PASS.
- [ ] **Step 5:** Commit `Apartados: tabla y rutas en el server`.

### Task 3: Server consumers use the apartados

**Files:**
- Modify: `server/.../routes/DashboardRoutes.kt` (patrimonio at ~213, `DashboardSummary.apartado`), `routes/AiRoutes.kt` (~637, `renderizarPatrimonio` 804-813, per-account text 715-735), `ai/HechosDeLaPregunta.kt` (254, 325-330), `ai/LasPreguntasSugeridas.kt` (190-210, PlataDelDisponible), `routes/CierreDelPeriodoRoutes.kt` (120-145), `routes/CajaRoutes.kt:131`, `routes/FinanceRoutes.kt:213`, `compartir/ResumenCompartido.kt:134`
- Test: extend `DashboardRoutesTest`, `HechosDeLaPreguntaTest` (or the AI context test that exists)

**Interfaces:**
- Consumes: `apartadosDelUsuario` (Task 2), `patrimonioDe(accounts, apartados)`, `PlataDelDisponible.apartado`, `DashboardSummary.apartado` (Task 1).
- Rule: `DashboardSummary.apartado = patrimonio.apartado` (the efectivo total). Every `PlataDelDisponible(...)` built on the server passes `apartado = that total`.

- [ ] **Step 1: Failing tests:** dashboard summary for a user with Nu 8_217_464 and apartado 8_000_000 ⇒ `patrimonio.tuPlata == 217_464`, `apartado == 8_000_000`, `patrimonio.neto` unchanged; AI patrimonio text mentions «apartado» with the propósito and the amount and does not count it in Tu plata.
- [ ] **Step 2:** run those tests → FAIL.
- [ ] **Step 3:** Thread apartados through each listed call site.
- [ ] **Step 4:** `./gradlew :server:test` → all PASS.
- [ ] **Step 5:** Commit `Apartados: Tu plata, Te quedan y Movi AI los descuentan`.

### Task 4: Client repository

**Files:**
- Modify: `core/.../repository/WalletRepository.kt`, `WalletRepositoryImpl.kt`, `core/src/nonWasmMain/.../repository/LocalRepository.kt` (read via `leerConCache` like `getGoals` ~1992; writes go remote then invalidate that cache key), `shared/.../data/InvalidaElInicioAlEscribir.kt`, `core/src/jvmTest/.../NoOpRepository.kt`, `shared/src/androidUnitTest/.../data/RepositorioDePrueba.kt`
- Test: core jvmTest for LocalRepository cache path if a similar test exists for goals; otherwise compile-level coverage via Task 5 tests.

**Interfaces:**
- Produces on `WalletRepository`: `suspend fun getApartados(): List<Apartado>` (default `emptyList()` on 404 from an old server), `suspend fun guardarApartado(a: Apartado): Apartado` (POST when new, PUT when it has a server id — or always POST upsert), `suspend fun quitarApartado(id: String)`.

- [ ] **Step 1:** Write a test that `getApartados()` returns `emptyList()` when the server answers 404 (old server).
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** `./gradlew :core:jvmTest :shared:testDebugUnitTest --tests '*Repositorio*'` → PASS.
- [ ] **Step 5:** Commit `Apartados: el repositorio del cliente`.

### Task 5: Client UI

**Files:**
- Modify: `shared/.../ui/dashboard/DashboardLogic.kt` (`heroBalance` 482-493, `patrimonioExplicacion` 433-460), `ui/dashboard/InicioDeUnVistazo.kt` (tramos 395-412), the data loader that builds Hoy's data (load apartados next to accounts), `ui/accounts/AccountsScreen.kt` (PATRIMONIO NETO card 231-300), `ui/accounts/AccountDetailScreen.kt` (after balance card 261-310)
- Create: `shared/.../ui/accounts/ApartadoSheet.kt`
- Test: `shared/src/androidUnitTest/.../accounts/ApartadosEnLaCuentaTest.kt`, extend `InicioDeUnVistazoTest`

**Interfaces:**
- Consumes: `getApartados/guardarApartado/quitarApartado` (Task 4), `patrimonioDe(accounts, apartados)`, `Patrimonio.apartado/apartadoPara` (Task 1).
- Copy (fixed): tramo label «Apartado · {propósito}» (or «Apartado» with several); in account detail a section «Apartado» listing «{propósito} · {restante}» and a line «Libre · {saldo − efectivo}»; button «Apartar plata»; sheet fields «¿Para qué?», «Monto», «Se descuenta con los gastos de» (category picker, optional, default none) ; actions «Guardar», «Quitar». Without network: «Para apartar plata necesitas conexión».
- The card shows only for accounts where `esDeTuPlata()`.

- [ ] **Step 1: Failing tests:** account detail of Nu with an apartado shows «Colegio nov-dic», its restante and «Libre · $217.464»; Hoy's patrimonio tramos include «Apartado · Colegio nov-dic» and Tu plata shows 217.464; with no apartados the screens are unchanged (existing tests stay green).
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** `./gradlew :shared:testDebugUnitTest :shared:compileKotlinWasmJs` → PASS.
- [ ] **Step 5:** Commit `Apartados: verlos y crearlos en la app`.

### Task 6: Docs and full suite

- [ ] Add an «Apartados» bullet to `CLAUDE.md` (server section, near Bienes/condicionado: table, routes, the rule, that `patrimonioDe` takes them).
- [ ] Run the full CI command from Global Constraints → all green.
- [ ] Commit `Apartados: CLAUDE.md`.
