# Créditos reales (F1+F2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistir los términos contractuales de los préstamos del usuario sobre cuentas LOAN, mostrar deuda/progreso real en CreditosScreen, e integrar las cuotas al motor de recordatorios existente.

**Architecture:** La deuda vive donde siempre — derivada de `financial_events` de la cuenta LOAN. Los términos (banco, tasa, cuota, día de pago) van en una tabla nueva `credit_terms` 1:1 con la cuenta. Las cuotas entran a próximos pagos y al sweep de emails como `RecurringRule` **virtuales** construidas al vuelo desde los términos (sin filas duplicadas, sin sincronización de lifecycle).

**Tech Stack:** Kotlin Multiplatform, Ktor (server + client), Exposed + Postgres (H2 en tests), Compose Multiplatform, kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-07-07-creditos-reales-design.md`

## Global Constraints

- Trabajar SIEMPRE dentro de `/Users/carolinarestrepo/Developer/movi` (repo multi-proyecto: nunca correr Gradle desde `~/Developer`).
- La build de movi requiere JBR 21 (`gradle/gradle-daemon-jvm.properties` lo pinnea; `~/.zshrc` ya exporta `JAVA_HOME` correcto). NO editar ese properties file.
- Sin dependencias nuevas — todo lo necesario (Exposed, H2 test, Ktor, kotlinx.serialization) ya está en `gradle/libs.versions.toml`.
- Copy de UI en español (estilo existente: "Deuda total", "DEUDA INICIAL").
- **Naming:** el objeto Exposed de la tabla se llama `Credits` (convención plural de `Tables.kt`) con nombre SQL `"credit_terms"` — NO `CreditTerms`, para no chocar con el import del modelo wire `com.jvillada.movi.shared.model.CreditTerms` en los mismos archivos.
- Tests de servidor: harness H2 in-memory (`MODE=PostgreSQL`), patrón exacto de `server/src/test/kotlin/com/jvillada/movi/server/routes/IsolationTest.kt` (JWT con secret de prueba, drop+create schema en `@BeforeTest`).
- Cada tarea termina con todo compilando y tests verdes. Commits frecuentes en el branch `feat/creditos-reales`.
- Si el nombre exacto de una task de compilación de `:shared` no existe (AGP 9 / KMP renombra según target), listar con `./gradlew :shared:tasks --all | grep -i compile` y usar la equivalente para Android + wasmJs — no saltarse la verificación de compilación.

## File Structure

```
core/src/commonMain/.../shared/model/Finance.kt            [M] Credit → CreditTerms + CreditSummary
core/src/commonMain/.../shared/repository/WalletRepository.kt      [M] firmas nuevas
core/src/commonMain/.../shared/repository/WalletRepositoryImpl.kt  [M] llamadas HTTP
core/src/nonWasmMain/.../shared/repository/LocalRepository.kt      [M] delegaciones
core/src/jvmTest/.../shared/repository/NoOpRepository.kt           [M] overrides
server/src/main/.../server/db/Tables.kt                    [M] object Credits
server/src/main/.../server/db/DatabaseFactory.kt           [M] registrar tabla
server/src/main/.../server/credits/CreditSummaries.kt      [C] paidPctFor + mapper ResultRow→CreditTerms
server/src/main/.../server/routes/CreditRoutes.kt          [C] GET/PUT/DELETE /api/credits
server/src/main/.../server/routes/FinanceRoutes.kt         [M] quitar stub credits + mover GET recurring-rules
server/src/main/.../server/routes/ReminderRoutes.kt        [M] recibir GET recurring-rules + unión en upcoming
server/src/main/.../server/reminders/CreditReminders.kt    [C] virtualRuleFor + loadCreditRulePairs
server/src/main/.../server/reminders/ReminderScheduler.kt  [M] unión en sweep + sellar credit_terms
server/src/main/.../server/plugins/Routing.kt              [M] registrar creditRoutes()
server/src/test/.../server/credits/CreditSummariesTest.kt  [C] unit paidPctFor
server/src/test/.../server/reminders/CreditRemindersTest.kt [C] unit regla virtual + selección
server/src/test/.../server/routes/CreditRoutesTest.kt      [C] HTTP H2: CRUD + aislamiento + upcoming
server/src/test/.../server/routes/ReminderRoutesTest.kt    [M] schema: agregar Credits al drop/create
shared/src/commonMain/.../ui/credits/CreditosScreen.kt     [M] render real (T1) + interactividad (T5)
shared/src/commonMain/.../ui/credits/CreditTermsSheet.kt   [C] sheet crear/editar términos
shared/src/commonMain/.../ui/analisis/AnalisisScreen.kt    [M] adaptar a CreditSummary
```

(`[C]`=create, `[M]`=modify. Prefijo `core/...` = `core/src/commonMain/kotlin/com/jvillada/movi/`, `server/...` = `server/src/main/kotlin/com/jvillada/movi/`, `shared/...` = `shared/src/commonMain/kotlin/com/jvillada/movi/`.)

---

### Task 1: Modelos core + plomería de repositorios

Reemplaza el modelo legacy `Credit` (strings de display) por `CreditTerms`/`CreditSummary` tipados, actualiza la interfaz del repo y TODOS los puntos que referencian `Credit` para que cada módulo compile. El servidor queda con un stub interino (lista vacía) que Task 3 reemplaza.

**Files:**
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt:24-33`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- Modify: `core/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt:127`
- Modify: `core/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt:11`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt:15,37`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/analisis/AnalisisScreen.kt:22,37,43,57`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/credits/CreditosScreen.kt`

**Interfaces:**
- Consumes: `Account`, `AccountType` (existentes en `core/.../model/Account.kt`).
- Produces (los usan Tasks 2-5):
  - `data class CreditTerms(accountId: String, bank: String, principal: Long, rateEa: Double, termMonths: Int, installment: Long, dayOfMonth: Int, startDate: String, notes: String? = null)`
  - `data class CreditSummary(account: Account, terms: CreditTerms?, paidPct: Double?)`
  - `WalletRepository.getCredits(): List<CreditSummary>` / `putCreditTerms(terms: CreditTerms): CreditSummary` / `deleteCreditTerms(accountId: String)`

- [ ] **Step 1: Reemplazar `Credit` en Finance.kt**

En `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt`, borrar el data class `Credit` (líneas 24-33) y en su lugar:

```kotlin
@Serializable
data class CreditTerms(
    val accountId: String,
    val bank: String,
    val principal: Long,        // capital original (COP)
    val rateEa: Double,         // % EA, p.ej. 17.46
    val termMonths: Int,
    val installment: Long,      // cuota mensual total (incl. seguros)
    val dayOfMonth: Int,        // día de pago
    val startDate: String,      // ISO "2026-06-01" (desembolso)
    val notes: String? = null,
)

@Serializable
data class CreditSummary(
    val account: Account,       // cuenta LOAN con deuda derivada en balance
    val terms: CreditTerms?,    // null si la cuenta LOAN aún no tiene términos
    val paidPct: Double?,       // 1 − deuda/principal clampado a [0,1]; null sin términos
)
```

`Account` ya está en el mismo paquete — no requiere import.

- [ ] **Step 2: Actualizar la interfaz del repo**

En `WalletRepository.kt`: cambiar el import `Credit` por `CreditSummary` y `CreditTerms`; reemplazar `suspend fun getCredits(): List<Credit>` por:

```kotlin
    suspend fun getCredits(): List<CreditSummary>
    suspend fun putCreditTerms(terms: CreditTerms): CreditSummary
    suspend fun deleteCreditTerms(accountId: String)
```

- [ ] **Step 3: Implementar en WalletRepositoryImpl**

Mismo cambio de imports. Reemplazar el override de `getCredits` y agregar los nuevos (patrón idéntico a los métodos de budgets existentes):

```kotlin
    override suspend fun getCredits(): List<CreditSummary> =
        client.get("$baseUrl/api/credits").body()

    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary =
        client.put("$baseUrl/api/credits/${terms.accountId}") {
            contentType(ContentType.Application.Json)
            setBody(terms)
        }.body()

    override suspend fun deleteCreditTerms(accountId: String) {
        client.delete("$baseUrl/api/credits/$accountId")
    }
```

- [ ] **Step 4: Delegar en LocalRepository (nonWasmMain)**

En `LocalRepository.kt` (línea 127), reemplazar `override suspend fun getCredits(): List<Credit> = remote.getCredits()` por:

```kotlin
    override suspend fun getCredits(): List<CreditSummary> = remote.getCredits()
    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary = remote.putCreditTerms(terms)
    override suspend fun deleteCreditTerms(accountId: String) = remote.deleteCreditTerms(accountId)
```

(ajustar imports `Credit` → `CreditSummary`, `CreditTerms`).

- [ ] **Step 5: Actualizar NoOpRepository (core jvmTest)**

En `NoOpRepository.kt`, reemplazar `override suspend fun getCredits() = emptyList<Credit>()` por:

```kotlin
    override suspend fun getCredits() = emptyList<CreditSummary>()
    override suspend fun putCreditTerms(terms: CreditTerms) = CreditSummary(
        account = Account(id = terms.accountId, name = "", type = AccountType.LOAN, balance = 0),
        terms = terms,
        paidPct = null,
    )
    override suspend fun deleteCreditTerms(accountId: String) {}
```

(ajustar imports; `Account`/`AccountType` pueden requerir import nuevo — seguir el estilo del archivo).

- [ ] **Step 6: Stub interino en FinanceRoutes**

En `FinanceRoutes.kt`: cambiar import `com.jvillada.movi.shared.model.Credit` → `com.jvillada.movi.shared.model.CreditSummary` y la línea 37 a:

```kotlin
    get("/api/credits") { call.respond(emptyList<CreditSummary>()) }
```

(Task 3 elimina esta línea al montar las rutas reales.)

- [ ] **Step 7: Adaptar AnalisisScreen**

En `AnalisisScreen.kt`: import `Credit` → `CreditSummary`; línea 37 `mutableStateOf<List<CreditSummary>>(emptyList())`; línea 57:

```kotlin
    val totalDeuda = credits.sumOf { it.account.balance }
```

La línea 200 (`sub = "Deuda pendiente · ${credits.size} obligaciones"`) no cambia.

- [ ] **Step 8: Adaptar CreditosScreen (render de datos reales, sin interactividad aún)**

Reemplazar en `CreditosScreen.kt` el import `Credit` → `CreditSummary`, el estado y el cuerpo de las tarjetas. Cambios concretos sobre el archivo actual:

```kotlin
    var credits by remember { mutableStateOf<List<CreditSummary>>(emptyList()) }
```

```kotlin
    val totalDebt = credits.sumOf { it.account.balance }
```

En la tarjeta hero, **eliminar** el bloque `if (credits.isNotEmpty()) { ... Próxima cuota ... }` completo (las cuotas ahora viven en próximos pagos; Task 4 las integra). En el `forEach`, reemplazar el cuerpo de cada tarjeta por:

```kotlin
                        credits.forEach { c ->
                            val pct = (c.paidPct ?: 0.0).toFloat()
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(18.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
                                    Text(c.terms?.let { "${it.rateEa}% EA" } ?: "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                }
                                Text(c.terms?.bank ?: "Sin términos registrados", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(formatCOP(c.account.balance), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                    Text("${(pct * 100).toInt()}% pagado", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MinHairline)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(MinText.copy(alpha = 0.9f))
                                    )
                                }
                                c.terms?.let { t ->
                                    Spacer(Modifier.height(14.dp))
                                    Hairline()
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Cuota · día ${t.dayOfMonth}", fontSize = 12.sp, color = MinTextMute)
                                        Text(formatCOP(t.installment), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                    }
                                }
                            }
                        }
```

- [ ] **Step 9: Verificar que no queda ninguna referencia a `Credit`**

Run: `grep -rn "shared.model.Credit\b\|List<Credit>\|<Credit>" core/src shared/src server/src webApp/src --include="*.kt"`
Expected: sin resultados.

- [ ] **Step 10: Compilar los tres módulos**

Run: `./gradlew :core:test :server:compileKotlin :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (los tests de :core y compilación de server/shared en verde).

- [ ] **Step 11: Commit**

```bash
git add core/src shared/src server/src
git commit -m "feat(core): CreditTerms + CreditSummary reemplazan el modelo legacy Credit"
```

---

### Task 2: Tabla `credit_terms` + helpers puros (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt:26-29`
- Create: `server/src/main/kotlin/com/jvillada/movi/server/credits/CreditSummaries.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/credits/CreditSummariesTest.kt`

**Interfaces:**
- Consumes: `CreditTerms` (Task 1).
- Produces (los usan Tasks 3-4):
  - `object Credits : Table("credit_terms")` con columnas `accountId, userId, bank, principal, rateEa, termMonths, installment, dayOfMonth, startDate, notes, lastRemindedPeriod`
  - `fun paidPctFor(principal: Long, debt: Long): Double?`
  - `fun ResultRow.toCreditTerms(): CreditTerms`

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/kotlin/com/jvillada/movi/server/credits/CreditSummariesTest.kt`:

```kotlin
package com.jvillada.movi.server.credits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditSummariesTest {

    @Test
    fun `paid pct is 1 minus debt over principal`() {
        assertEquals(0.6, paidPctFor(principal = 100_000_000, debt = 40_000_000)!!, 1e-9)
    }

    @Test
    fun `zero or negative principal yields null`() {
        assertNull(paidPctFor(principal = 0, debt = 10))
        assertNull(paidPctFor(principal = -5, debt = 10))
    }

    @Test
    fun `debt above principal clamps to zero pct`() {
        assertEquals(0.0, paidPctFor(principal = 100, debt = 150)!!, 1e-9)
    }

    @Test
    fun `overpaid credit (negative debt) clamps to one hundred pct`() {
        assertEquals(1.0, paidPctFor(principal = 100, debt = -20)!!, 1e-9)
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.credits.CreditSummariesTest"`
Expected: FAIL — `unresolved reference: paidPctFor` (error de compilación).

- [ ] **Step 3: Implementar tabla + helpers**

En `Tables.kt`, agregar al final:

```kotlin
object Credits : Table("credit_terms") {
    val accountId          = varchar("account_id", 50)   // 1:1 con cuenta LOAN
    val userId             = varchar("user_id", 50)
    val bank               = varchar("bank", 80)
    val principal          = long("principal")            // capital original (COP)
    val rateEa             = double("rate_ea")            // % EA
    val termMonths         = integer("term_months")
    val installment        = long("installment")          // cuota mensual total
    val dayOfMonth         = integer("day_of_month")
    val startDate          = varchar("start_date", 10)    // ISO desembolso
    val notes              = varchar("notes", 300).nullable()
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable() // "YYYY-MM", server-only
    override val primaryKey = PrimaryKey(accountId)
    init { index("idx_credit_terms_user_id", false, userId) }
}
```

En `DatabaseFactory.init()`, agregar `Credits` al final de la lista de `SchemaUtils.create(...)`.

Crear `server/src/main/kotlin/com/jvillada/movi/server/credits/CreditSummaries.kt`:

```kotlin
package com.jvillada.movi.server.credits

import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.shared.model.CreditTerms
import org.jetbrains.exposed.sql.ResultRow

/**
 * Progreso de pago de un crédito: 1 − deuda/principal, clampado a [0, 1].
 * Null cuando el principal no es positivo (términos inválidos/incompletos).
 * La deuda llega derivada de los eventos de la cuenta LOAN — nunca se almacena aquí.
 */
fun paidPctFor(principal: Long, debt: Long): Double? {
    if (principal <= 0L) return null
    return (1.0 - debt.toDouble() / principal.toDouble()).coerceIn(0.0, 1.0)
}

fun ResultRow.toCreditTerms() = CreditTerms(
    accountId  = this[Credits.accountId],
    bank       = this[Credits.bank],
    principal  = this[Credits.principal],
    rateEa     = this[Credits.rateEa],
    termMonths = this[Credits.termMonths],
    installment = this[Credits.installment],
    dayOfMonth = this[Credits.dayOfMonth],
    startDate  = this[Credits.startDate],
    notes      = this[Credits.notes],
)
```

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.credits.CreditSummariesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src
git commit -m "feat(server): tabla credit_terms + paidPctFor puro"
```

---

### Task 3: Rutas /api/credits + limpieza de FinanceRoutes (TDD HTTP)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/CreditRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt:16-24`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/FinanceRoutes.kt` (quitar stub credits y GET recurring-rules)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/ReminderRoutes.kt` (recibe el GET recurring-rules)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/CreditRoutesTest.kt`

**Interfaces:**
- Consumes: `Credits`, `paidPctFor`, `toCreditTerms` (Task 2); `CreditTerms`, `CreditSummary` (Task 1); `computeBalances`, `estimatedTotalCop`, `loadNonVoidedEvents` (existentes en `server/.../balance/`); `FxRateService.usdToCop()`.
- Produces (los usan Tasks 4-5): rutas `GET /api/credits` → `List<CreditSummary>`, `PUT /api/credits/{accountId}` (body `CreditTerms`) → `CreditSummary`, `DELETE /api/credits/{accountId}` → 204; y `fun Route.creditRoutes()` registrado en Routing.

- [ ] **Step 1: Escribir el test HTTP que falla**

`server/src/test/kotlin/com/jvillada/movi/server/routes/CreditRoutesTest.kt` — copiar el harness de `IsolationTest.kt` (mismo esquema de JWT test-secret, mismo `testApplication` con `configureSerialization` + verificador JWT + `configureRouting`), con estos ajustes:

- Nombre de la DB H2: `jdbc:h2:mem:credit_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`
- En `@BeforeTest`, el drop/create incluye **todas** las tablas de `Tables.kt` MÁS `Credits`:
  `SchemaUtils.drop(Credits, SmsMessages, RecurringRules, VoidEvents, Events, StatementImports, Budgets, Accounts, Users)` y el `create` espejo.
- Sembrar: usuario A y usuario B (mismo patrón de inserts en `Users`); para A una cuenta LOAN `acc-loan-a` (`Accounts.insert { type = "LOAN", currency = "COP", ... }`) con un evento de apertura EXPENSE de 100_000_000 en `Events` (categoría "Deuda inicial", `source = "MANUAL"`, `reconciliationStatus = "UNCONFIRMED"`), y una cuenta `acc-cash-a` tipo `CASH` para el caso 422.

Tests (usar los helpers de token/JSON del harness):

```kotlin
    @Test
    fun `PUT then GET returns terms with derived debt and paid pct`() = testApplication {
        wireApp()  // helper local que instala serialization + jwt + routing, igual a IsolationTest
        val put = client.put("/api/credits/acc-loan-a") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"accountId":"acc-loan-a","bank":"Bancolombia","principal":262000000,"rateEa":17.46,"termMonths":72,"installment":4888000,"dayOfMonth":5,"startDate":"2024-01-15"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val res = client.get("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        val summary = arr[0].jsonObject
        assertEquals("Bancolombia", summary["terms"]!!.jsonObject["bank"]!!.jsonPrimitive.content)
        assertEquals(100000000L, summary["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        // paidPct = 1 - 100M/262M ≈ 0.6183
        assertTrue(summary["paidPct"]!!.jsonPrimitive.double in 0.61..0.62)
    }

    @Test
    fun `PUT is an idempotent upsert`() = testApplication {
        wireApp()
        repeat(2) {
            client.put("/api/credits/acc-loan-a") { /* mismo body del test anterior */ }
        }
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(1, Json.parseToJsonElement(res.bodyAsText()).jsonArray.size)
    }

    @Test
    fun `PUT on another user's account is 404`() = testApplication {
        wireApp()
        val res = client.put("/api/credits/acc-loan-a") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `PUT on a non-LOAN account is 422`() = testApplication {
        wireApp()
        val res = client.put("/api/credits/acc-cash-a") { /* token A, body válido */ }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `GET returns LOAN accounts without terms with null terms`() = testApplication {
        wireApp()
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val summary = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject
        assertTrue(summary["terms"] is JsonNull)
        assertTrue(summary["paidPct"] is JsonNull)
    }

    @Test
    fun `DELETE removes terms and is 404 the second time`() = testApplication {
        wireApp()
        client.put("/api/credits/acc-loan-a") { /* token A, body válido */ }
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/credits/acc-loan-a") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
        assertEquals(HttpStatusCode.NotFound,  client.delete("/api/credits/acc-loan-a") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }

    @Test
    fun `user B cannot see user A's credits`() = testApplication {
        wireApp()
        client.put("/api/credits/acc-loan-a") { /* token A, body válido */ }
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals("[]", res.bodyAsText())
    }
```

Los comentarios `/* ... */` de arriba son abreviaturas de ESTE plan para no repetir el mismo bloque de headers+body ya mostrado completo en el primer test — en el archivo real cada llamada lleva sus headers y `setBody(validTermsJson)` con `validTermsJson` como constante privada del test.

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.CreditRoutesTest"`
Expected: FAIL — los PUT/DELETE devuelven 404 (ruta no existe) y el GET devuelve `[]` del stub, así que el primer test falla en el assert del PUT.

- [ ] **Step 3: Implementar CreditRoutes**

`server/src/main/kotlin/com/jvillada/movi/server/routes/CreditRoutes.kt`:

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.estimatedTotalCop
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

fun Route.creditRoutes() {
    route("/api/credits") {
        get {
            val uid = call.userId()
            val rate = FxRateService.usdToCop()
            val loans = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.LOAN.name) }
                    .map { it.toAccount() }
            }
            val termsByAccount = dbQuery {
                Credits.selectAll().where { Credits.userId eq uid }
                    .associate { it[Credits.accountId] to it.toCreditTerms() }
            }
            val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            call.respond(loans.map { acc ->
                summaryFor(acc, termsByAccount[acc.id], eventsByAccount[acc.id] ?: emptyList(), rate)
            })
        }

        put("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            if (account.type != AccountType.LOAN) {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, "Solo cuentas LOAN llevan términos de crédito")
            }
            val body = call.receive<CreditTerms>()
                .copy(accountId = accountId)
                .let { it.copy(dayOfMonth = it.dayOfMonth.coerceIn(1, 31)) }
            dbQuery {
                val exists = Credits.selectAll()
                    .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
                    .count() > 0
                if (exists) {
                    // lastRemindedPeriod se conserva a propósito: un cambio de día aplica desde el mes siguiente (v1)
                    Credits.update({ (Credits.accountId eq accountId) and (Credits.userId eq uid) }) {
                        it[bank]        = body.bank
                        it[principal]   = body.principal
                        it[rateEa]      = body.rateEa
                        it[termMonths]  = body.termMonths
                        it[installment] = body.installment
                        it[dayOfMonth]  = body.dayOfMonth
                        it[startDate]   = body.startDate
                        it[notes]       = body.notes
                    }
                } else {
                    Credits.insert {
                        it[Credits.accountId] = accountId
                        it[userId]      = uid
                        it[bank]        = body.bank
                        it[principal]   = body.principal
                        it[rateEa]      = body.rateEa
                        it[termMonths]  = body.termMonths
                        it[installment] = body.installment
                        it[dayOfMonth]  = body.dayOfMonth
                        it[startDate]   = body.startDate
                        it[notes]       = body.notes
                    }
                }
            }
            call.respond(summaryFor(account, body, loadNonVoidedEvents(uid, accountId), FxRateService.usdToCop()))
        }

        delete("/{accountId}") {
            val uid = call.userId()
            val accountId = call.parameters["accountId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing accountId")
            val deleted = dbQuery {
                Credits.deleteWhere { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun summaryFor(base: Account, terms: CreditTerms?, events: List<FinancialEvent>, rate: Double): CreditSummary {
    val balances = computeBalances(base.type, events)
    val account = base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
    )
    return CreditSummary(
        account = account,
        terms   = terms,
        paidPct = terms?.let { paidPctFor(it.principal, account.balance) },
    )
}

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
```

Nota: si `deleteWhere` da error de compilación por el receiver del lambda, usar la misma forma que `ReminderRoutes.kt` ya usa (`import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq` + `deleteWhere { ... }`).

- [ ] **Step 4: Registrar en Routing y limpiar FinanceRoutes**

En `Routing.kt`, dentro del bloque `authenticate("jwt")`, agregar `creditRoutes()` después de `financeRoutes()`.

En `FinanceRoutes.kt`:
- Eliminar la línea `get("/api/credits") { call.respond(emptyList<CreditSummary>()) }` y el import de `CreditSummary`.
- Eliminar el bloque completo `get("/api/recurring-rules") { ... }` (líneas 38-54 aprox.) y los imports que solo él usaba (`RecurringRules`, `RecurringRule`).

En `ReminderRoutes.kt`, agregar el GET junto a sus mutaciones (reusa el mapper `toRule()` privado que ya existe en ese archivo):

```kotlin
    get("/api/recurring-rules") {
        val uid = call.userId()
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        call.respond(rules)
    }
```

- [ ] **Step 5: Verificar que pasa todo**

Run: `./gradlew :server:test`
Expected: PASS — CreditRoutesTest en verde y CERO regresiones en IsolationTest / ReminderRoutesTest / SmsSyncTest (el GET de recurring-rules movido responde idéntico).

- [ ] **Step 6: Commit**

```bash
git add server/src
git commit -m "feat(server): rutas /api/credits (GET/PUT/DELETE) con deuda derivada y aislamiento por usuario"
```

---

### Task 4: Cuotas en recordatorios — reglas virtuales (TDD)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/reminders/CreditReminders.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/ReminderRoutes.kt` (GET upcoming)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/reminders/ReminderScheduler.kt` (`processUser`)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/reminders/CreditRemindersTest.kt`
- Modify: `server/src/test/kotlin/com/jvillada/movi/server/routes/ReminderRoutesTest.kt` (schema) — y agregar allí el test HTTP de upcoming con cuota
- Modify: `server/src/test/kotlin/com/jvillada/movi/server/routes/CreditRoutesTest.kt` (si su schema no incluía ya `Credits`, verificar)

**Interfaces:**
- Consumes: `Credits`, `toCreditTerms` (Task 2); `virtualRuleFor` definido aquí; `selectDueForReminder`, `upcomingPayments`, `dueDateFor` (existentes en `DueDates.kt`); rutas de Task 3.
- Produces:
  - `fun virtualRuleFor(terms: CreditTerms, accountName: String): RecurringRule` — id `"credit_<accountId>"`, categoría `"Créditos"`, EXPENSE.
  - `suspend fun loadCreditRulePairs(userId: String): List<Pair<RecurringRule, String?>>`
  - `GET /api/payments/upcoming` incluye cuotas de créditos.
  - El sweep de emails incluye cuotas y sella `credit_terms.last_reminded_period`.

- [ ] **Step 1: Escribir el test unit que falla**

`server/src/test/kotlin/com/jvillada/movi/server/reminders/CreditRemindersTest.kt`:

```kotlin
package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditRemindersTest {

    private val terms = CreditTerms(
        accountId = "acc-loan-1", bank = "Santander", principal = 160_000_000,
        rateEa = 21.56, termMonths = 72, installment = 4_550_030,
        dayOfMonth = 25, startDate = "2025-11-25",
    )

    @Test
    fun `virtual rule maps terms to an EXPENSE recurring rule`() {
        val rule = virtualRuleFor(terms, accountName = "Crédito Vehículo")
        assertEquals("credit_acc-loan-1", rule.id)
        assertEquals("Cuota Crédito Vehículo", rule.name)
        assertEquals("Créditos", rule.category)
        assertEquals(4_550_030, rule.amount)
        assertEquals(25, rule.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, rule.type)
    }

    @Test
    fun `due virtual rule enters the reminder sweep`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)  // un día antes del día 25
        val selected = selectDueForReminder(listOf(rule to null), today, leadDays = 3, period = "2026-07")
        assertEquals(listOf(rule), selected)
    }

    @Test
    fun `already-reminded virtual rule is excluded this period`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(rule to "2026-07"), today, leadDays = 3, period = "2026-07")
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `manual rule with the same name coexists with the virtual one`() {
        val virtual = virtualRuleFor(terms, "Crédito Vehículo")
        val manual = virtual.copy(id = "rr_manual-dup")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(virtual to null, manual to null), today, leadDays = 3, period = "2026-07")
        assertEquals(2, selected.size)  // conviven por diseño; la de-duplicación es manual (siembra)
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.reminders.CreditRemindersTest"`
Expected: FAIL — `unresolved reference: virtualRuleFor`.

- [ ] **Step 3: Implementar CreditReminders**

`server/src/main/kotlin/com/jvillada/movi/server/reminders/CreditReminders.kt`:

```kotlin
package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.selectAll

/** Prefijo que distingue las reglas sintéticas de crédito de las recurring_rules reales. */
const val CREDIT_RULE_PREFIX = "credit_"

/**
 * Regla recurrente sintética para la cuota de un crédito. NO existe en recurring_rules:
 * se construye al vuelo desde credit_terms para entrar al mismo motor de DueDates.
 */
fun virtualRuleFor(terms: CreditTerms, accountName: String): RecurringRule =
    RecurringRule(
        id         = "$CREDIT_RULE_PREFIX${terms.accountId}",
        name       = "Cuota $accountName",
        category   = "Créditos",
        amount     = terms.installment,
        dayOfMonth = terms.dayOfMonth,
        type       = TransactionType.EXPENSE,
    )

/** Pares (regla virtual, lastRemindedPeriod) de todos los créditos del usuario. */
suspend fun loadCreditRulePairs(userId: String): List<Pair<RecurringRule, String?>> = dbQuery {
    Credits.join(Accounts, JoinType.INNER, Credits.accountId, Accounts.id)
        .selectAll()
        .where { Credits.userId eq userId }
        .map { row ->
            virtualRuleFor(row.toCreditTerms(), row[Accounts.name]) to row[Credits.lastRemindedPeriod]
        }
}
```

(Import de `eq`: usar `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq` como en el resto del paquete.)

- [ ] **Step 4: Verificar que el unit pasa**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.reminders.CreditRemindersTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Unir en GET upcoming + test HTTP que falla**

En `ReminderRoutesTest.kt`:
1. Agregar `Credits` a las listas de `SchemaUtils.drop(...)` y `SchemaUtils.create(...)` del `@BeforeTest` (primera posición en el drop) e importar `com.jvillada.movi.server.db.Credits` y `com.jvillada.movi.server.db.Accounts` si falta.
2. Agregar un test (siguiendo los helpers de ese archivo para token/headers):

```kotlin
    @Test
    fun `upcoming payments include credit installments`() = testApplication {
        // wiring igual al resto de tests del archivo
        transaction {
            Accounts.insert {
                it[id] = "acc-loan-up"; it[userId] = existingUserId
                it[name] = "Crédito Vehículo"; it[type] = "LOAN"
                it[balance] = 0; it[currency] = "COP"
            }
            Credits.insert {
                it[accountId] = "acc-loan-up"; it[userId] = existingUserId
                it[bank] = "Santander"; it[principal] = 160_000_000
                it[rateEa] = 21.56; it[termMonths] = 72
                it[installment] = 4_550_030; it[dayOfMonth] = 15
                it[startDate] = "2025-11-25"
            }
        }
        val res = client.get("/api/payments/upcoming") { /* auth header del harness */ }
        val body = res.bodyAsText()
        assertTrue(body.contains("credit_acc-loan-up"), "expected virtual credit rule in: $body")
        assertTrue(body.contains("Cuota Crédito Vehículo"))
    }
```

(`existingUserId` = el usuario que el harness de ese archivo ya siembra; usar su nombre real al editar.)

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.ReminderRoutesTest"`
Expected: FAIL solo el test nuevo (upcoming aún no une créditos).

- [ ] **Step 6: Implementar la unión en la ruta y el scheduler**

En `ReminderRoutes.kt`, el handler de `get("/api/payments/upcoming")` queda:

```kotlin
    get("/api/payments/upcoming") {
        val uid = call.userId()
        val leadDays = System.getenv("REMINDER_LEAD_DAYS")?.toIntOrNull() ?: 3
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        val creditRules = loadCreditRulePairs(uid).map { it.first }
        call.respond(upcomingPayments(rules + creditRules, LocalDate.now(ZoneOffset.UTC), leadDays))
    }
```

En `ReminderScheduler.kt`, dentro de `processUser`:
1. Después de cargar `rulePairs`, unir: `val allPairs = rulePairs + loadCreditRulePairs(userId)` y pasar `allPairs` a `selectDueForReminder`.
2. En el bloque `if (sent)`, sellar según el tipo de regla:

```kotlin
        for (rule in selected) {
            dbQuery {
                if (rule.id.startsWith(CREDIT_RULE_PREFIX)) {
                    Credits.update({
                        (Credits.accountId eq rule.id.removePrefix(CREDIT_RULE_PREFIX)) and (Credits.userId eq userId)
                    }) { it[Credits.lastRemindedPeriod] = period }
                } else {
                    RecurringRules.update({
                        (RecurringRules.id eq rule.id) and (RecurringRules.userId eq userId)
                    }) { it[RecurringRules.lastRemindedPeriod] = period }
                }
            }
        }
```

(agregar imports `Credits`, `CREDIT_RULE_PREFIX` ya está en el mismo paquete).

- [ ] **Step 7: Verificar suite completa**

Run: `./gradlew :server:test`
Expected: PASS — todo verde, incluido el test nuevo de upcoming.

- [ ] **Step 8: Commit**

```bash
git add server/src
git commit -m "feat(server): cuotas de créditos como reglas virtuales en próximos pagos y sweep de emails"
```

---

### Task 5: UI — CreditosScreen interactiva + CreditTermsSheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/credits/CreditosScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/credits/CreditTermsSheet.kt`

**Interfaces:**
- Consumes: `Repositories.wallets.getCredits()/putCreditTerms()/deleteCreditTerms()/createAccount()` (Task 1); componentes existentes `MinCard`, `MinCardVariant`, `SectionLabel`, `Hairline`, `MinSectionHeader`, `MinBottomNav`, `formatCOP`, tokens `Min*`, `toUserMessage()` (mismos imports que `CreateAccountSheet.kt`).
- Produces: pantalla final; no hay consumidores posteriores.

No hay infraestructura de tests de UI en el repo — la verificación es compilación (+ verificación manual en Task 6), consistente con las pantallas existentes.

- [ ] **Step 1: Crear CreditTermsSheet**

`shared/src/commonMain/kotlin/com/jvillada/movi/ui/credits/CreditTermsSheet.kt`. Sigue el patrón visual exacto de `CreateAccountSheet.kt` (overlay negro 0.6f, columna inferior con esquinas 28dp, drag handle, `SectionLabel` + `BasicTextField` en cajas `MinSurfaceContainerLow` con borde `MinBorder` 12dp, botón guardar al fondo):

```kotlin
package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Crea o edita los términos de un crédito.
 * - [editing] != null → modo edición sobre ese crédito (cuenta fija, campos precargados, permite eliminar).
 * - [editing] == null → modo creación: elegir una cuenta LOAN sin términos de [candidates] o crear cuenta nueva.
 */
@Composable
fun CreditTermsSheet(
    editing: CreditSummary?,
    candidates: List<Account>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val existingTerms = editing?.terms

    var selectedAccountId by remember { mutableStateOf(editing?.account?.id ?: candidates.firstOrNull()?.id) }
    var newAccountMode by remember { mutableStateOf(editing == null && candidates.isEmpty()) }
    var newAccountName by remember { mutableStateOf("") }
    var newAccountDebt by remember { mutableStateOf("") }

    var bank by remember { mutableStateOf(existingTerms?.bank ?: "") }
    var principal by remember { mutableStateOf(existingTerms?.principal?.toString() ?: "") }
    var rateEa by remember { mutableStateOf(existingTerms?.rateEa?.toString() ?: "") }
    var termMonths by remember { mutableStateOf(existingTerms?.termMonths?.toString() ?: "") }
    var installment by remember { mutableStateOf(existingTerms?.installment?.toString() ?: "") }
    var dayOfMonth by remember { mutableStateOf(existingTerms?.dayOfMonth?.toString() ?: "") }
    var startDate by remember { mutableStateOf(existingTerms?.startDate ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val termsValid = bank.isNotBlank() &&
        (principal.toLongOrNull() ?: 0L) > 0L &&
        (rateEa.toDoubleOrNull() != null) &&
        (termMonths.toIntOrNull() ?: 0) > 0 &&
        (installment.toLongOrNull() ?: 0L) > 0L &&
        (dayOfMonth.toIntOrNull() in 1..31) &&
        startDate.isNotBlank()
    val accountValid = if (editing != null) true
        else if (newAccountMode) newAccountName.isNotBlank()
        else selectedAccountId != null
    val canSave = termsValid && accountValid && !saving

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val accountId = when {
                    editing != null -> editing.account.id
                    newAccountMode -> Repositories.wallets.createAccount(
                        Account(
                            id = "",
                            name = newAccountName.trim(),
                            type = AccountType.LOAN,
                            balance = newAccountDebt.toLongOrNull() ?: 0L,
                            currency = "COP",
                        )
                    ).id
                    else -> selectedAccountId!!
                }
                Repositories.wallets.putCreditTerms(
                    CreditTerms(
                        accountId = accountId,
                        bank = bank.trim(),
                        principal = principal.toLong(),
                        rateEa = rateEa.toDouble(),
                        termMonths = termMonths.toInt(),
                        installment = installment.toLong(),
                        dayOfMonth = dayOfMonth.toInt(),
                        startDate = startDate.trim(),
                    )
                )
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun deleteTerms() {
        if (editing == null || saving) return
        saving = true
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteCreditTerms(editing.account.id) }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !saving, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint),
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                if (editing != null) {
                    SectionLabel("CRÉDITO")
                    Spacer(Modifier.height(8.dp))
                    Text(editing.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                    Spacer(Modifier.height(16.dp))
                } else {
                    SectionLabel("CUENTA DEL PRÉSTAMO")
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { acc ->
                        SelectRow(
                            label = acc.name,
                            selected = !newAccountMode && selectedAccountId == acc.id,
                            onClick = { newAccountMode = false; selectedAccountId = acc.id },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    SelectRow(
                        label = "+ Nueva cuenta de préstamo",
                        selected = newAccountMode,
                        onClick = { newAccountMode = true },
                    )
                    if (newAccountMode) {
                        Spacer(Modifier.height(10.dp))
                        FieldBox("Nombre (p.ej. Crédito Vehículo Santander)", newAccountName, { newAccountName = it })
                        Spacer(Modifier.height(8.dp))
                        FieldBox("Deuda actual (COP)", newAccountDebt, { newAccountDebt = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel("TÉRMINOS")
                Spacer(Modifier.height(8.dp))
                FieldBox("Banco", bank, { bank = it })
                Spacer(Modifier.height(8.dp))
                FieldBox("Capital original (COP)", principal, { principal = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FieldBox("Tasa % EA", rateEa, { rateEa = it }, KeyboardType.Decimal) }
                    Box(Modifier.weight(1f)) { FieldBox("Plazo (meses)", termMonths, { termMonths = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FieldBox("Cuota mensual (COP)", installment, { installment = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { FieldBox("Día de pago", dayOfMonth, { dayOfMonth = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                FieldBox("Desembolso (AAAA-MM-DD)", startDate, { startDate = it })

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = MinDanger)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) MinText else MinTextFaint)
                    .clickable(enabled = canSave) { save() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (saving) "Guardando…" else "Guardar crédito", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (editing?.terms != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Eliminar términos",
                    fontSize = 13.sp,
                    color = MinDanger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { deleteTerms() }.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FieldBox(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 14.sp, color = MinText),
            cursorBrush = SolidColor(MinText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinSurfaceContainerLow else Color.Transparent)
            .border(1.dp, if (selected) MinText else MinBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.5.sp, color = MinText, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
```

**Nota de tokens:** si `MinDanger` no existe en el tema, buscar el token de error usado por `CreateAccountSheet`/otras pantallas (`grep -rn "error" shared/src/commonMain/kotlin/com/jvillada/movi/theme/`) y usar ese. No inventar colores hardcodeados.

- [ ] **Step 2: Cablear el sheet en CreditosScreen**

En `CreditosScreen.kt` (sobre la versión de Task 1):

1. Estado y recarga:

```kotlin
    var credits by remember { mutableStateOf<List<CreditSummary>>(emptyList()) }
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CreditSummary?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        runCatching { Repositories.wallets.getCredits() }
            .onSuccess { credits = it }
    }
```

2. Header de la pantalla: agregar a la derecha del título un botón "+" (mismo patrón de texto clickable del back `‹`):

```kotlin
            Text("+", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { editing = null; showSheet = true })
```

3. Cada tarjeta de crédito se hace clickable para editar — en el `MinCard` del `forEach`, agregar `modifier = Modifier.fillMaxWidth().clickableSimple { editing = c; showSheet = true }` (si `MinCard` no acepta clickable en su modifier, envolver la tarjeta en un `Box` clickable).

4. En cuentas LOAN sin términos, la línea "Sin términos registrados" ya invita al tap (abre el sheet de edición con campos vacíos — `existingTerms` es null y funciona como creación sobre cuenta fija: en `CreditTermsSheet`, `editing != null` fija la cuenta aunque `terms` sea null).

5. Al final del `Column` raíz (después de `MinBottomNav`… NO: como overlay), envolver todo en un `Box` raíz y superponer el sheet:

```kotlin
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
            // ... contenido existente ...
        }
        if (showSheet) {
            CreditTermsSheet(
                editing = editing,
                candidates = credits.filter { it.terms == null }.map { it.account },
                onDismiss = { showSheet = false },
                onSaved = { showSheet = false; reloadKey++ },
            )
        }
    }
```

6. Estado vacío: reemplazar el texto "Sin créditos registrados" del `MinCard` vacío por uno con CTA:

```kotlin
                            Text(
                                "Sin créditos registrados — toca + para agregar el primero",
                                fontSize = 14.sp, color = MinTextMute,
                            )
```

- [ ] **Step 3: Compilar :shared**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src
git commit -m "feat(ui): CreditosScreen con datos reales + sheet de términos de crédito"
```

---

### Task 6: Verificación end-to-end y siembra de datos reales

**Files:**
- Ninguno nuevo — verificación. (Cualquier fix que salga aquí se commitea con mensaje `fix:`.)

**Interfaces:**
- Consumes: todo lo anterior + Postgres local (memoria `movi-local-postgres-setup`: Homebrew postgresql@16 nativo, credenciales en `server/.env`).

- [ ] **Step 1: Build completa + suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, `:server:test` y `:core:test` verdes.

- [ ] **Step 2: Levantar server + web localmente**

```bash
./gradlew :server:run   # terminal 1 (usa server/.env con el Postgres local)
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # terminal 2
```

Login `demo@movi.app` / `demo12345` (instancia local con datos reales).

- [ ] **Step 3: Verificación manual (checklist)**

1. Más → Créditos: pantalla carga (vacía o con cuentas LOAN existentes sin términos).
2. Tocar `+` → crear "Crédito Vehículo Santander": nueva cuenta, deuda actual al corte, términos ($160M, 21.56% EA, 72m, cuota $4.550.030, día 25). Guardar → tarjeta con deuda, % pagado y "Cuota · día 25".
3. Repetir para los otros 4 créditos reales (datos base en `server/movi-data/credits.json`, gitignored — **actualizar saldos al corte de hoy** y verificar si AV Villas ya fue reemplazado por la compra de cartera Bancolombia $257M/cuota $6.040.259; si sí, sembrar el crédito nuevo).
4. Dashboard/Mis cuentas: la deuda de los LOAN sigue apareciendo (sin regresión).
5. Recurrentes → próximos pagos: aparecen las cuotas "Cuota <crédito>" con su día. Si el día de pago de alguno cae en ≤3 días, verificar estado DUE_SOON.
6. Borrar las `recurring_rules` manuales que dupliquen cuotas de créditos (creadas en la siembra de junio) desde la UI de Recurrentes.
7. Editar un crédito (cambiar cuota) → el monto cambia en próximos pagos sin tocar Recurrentes.
8. Análisis: la sección de deuda muestra el total real.

- [ ] **Step 4: (Opcional, si `RESEND_API_KEY` está en `server/.env` local)**

Reiniciar el server y revisar el log del sweep: `ReminderScheduler: reminded user ...` debe incluir cuotas de crédito si alguna está en ventana. Si no hay API key, verificar solo el log `RESEND_API_KEY not set`.

- [ ] **Step 5: Commit final de cualquier ajuste + push del branch**

```bash
git push -u origin feat/creditos-reales
```

(El merge/PR se decide con la skill superpowers:finishing-a-development-branch.)
