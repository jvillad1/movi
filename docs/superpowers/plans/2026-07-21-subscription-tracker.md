# Subscription Tracker v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-descubrir las suscripciones del usuario a partir de sus `financial_events` (extractos importados) con un motor determinístico server-side, persistirlas en Postgres y gestionarlas desde una pantalla Suscripciones.

**Architecture:** Motor puro `SubscriptionDetector` (sin DB/red, unit-testable) que agrupa gastos por comercio normalizado y detecta cadencia mensual + monto estable; upsert con estados (`auto`/`candidate`/`confirmed`/`dismissed`) en la tabla `subscriptions`; rutas REST por usuario; pantalla con candidatos a revisar y activas. Sin Claude en el camino caliente. Sin integración con recordatorios en v1 (el spec la excluye).

**Tech Stack:** Kotlin Multiplatform, Ktor, Exposed + Postgres (H2 en tests), Compose Multiplatform, kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-06-16-subscription-tracker-design.md`

## Global Constraints

- Trabajar SIEMPRE dentro de `/Users/carolinarestrepo/Developer/movi`, branch `feat/subscription-tracker-v1`. JBR 21 ya es el JAVA_HOME (pinneado; no tocar `gradle/gradle-daemon-jvm.properties`).
- Sin dependencias nuevas. Copy de UI en español, estilo `Min*` existente.
- Estados y confianza EXACTOS del spec: `SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }`, `SubConfidence { HIGH, MEDIUM, LOW }` (wire en mayúsculas; la columna `status` guarda el `.name`).
- Reglas de upsert del spec (locked): `dismissed` → NO tocar; `confirmed` → actualizar amount/lastSeen/occurrences sin bajar estado; `auto`/`candidate` → actualizar y estado = `AUTO` si HIGH, si no `CANDIDATE`; no existe → insertar (`AUTO` si HIGH, si no `CANDIDATE`).
- Detección solo EXPENSE; agrupación por `(merchantKey, currency)`; suscripción si ≥2 meses distintos, dispersión ≤15%, cadencia ~mensual; HIGH si ≥3 meses + dispersión ≤5% + cadencia regular.
- **Decisión de plan (refina el spec, documentada):** el monto representativo es la **mediana de la SUMA mensual** del grupo, no del cargo individual — así "Claude ×3 cuentas" (tres cargos iguales el mismo mes) se detecta como una suscripción con el gasto mensual real, sin disparar la dispersión. Cargos separados por ≤3 días cuentan como el mismo ciclo (no rompen cadencia).
- `monthlyTotalCop` = suma de `amount` de las subs **AUTO + CONFIRMED** (USD × `FxRateService.usdToCop()`).
- Tests HTTP con el harness H2 patrón `CreditRoutesTest.kt` (JWT test-secret, drop/create schema completo incluyendo `Subscriptions` y `Credits`).
- Cada tarea termina compilando (`:server:test`, `:core:jvmTest`, `:shared:compileDebugKotlinAndroid`, `:shared:compileKotlinWasmJs` según toque) y con commit.

## File Structure

```
core/.../shared/model/Subscription.kt                  [C] SubStatus, SubConfidence, Subscription, SubscriptionsResult
core/.../shared/repository/WalletRepository.kt         [M] 4 métodos nuevos
core/.../shared/repository/WalletRepositoryImpl.kt     [M] llamadas HTTP
core/nonWasmMain/.../LocalRepository.kt                [M] delegaciones a remote
core/jvmTest/.../NoOpRepository.kt                     [M] overrides
server/.../subscriptions/SubscriptionDetector.kt       [C] normalizeMerchant + detectSubscriptions (puro)
server/.../db/Tables.kt                                [M] object Subscriptions
server/.../db/DatabaseFactory.kt                       [M] registrar tabla
server/.../routes/SubscriptionRoutes.kt                [C] GET / detect / PUT / DELETE
server/.../plugins/Routing.kt                          [M] registrar subscriptionRoutes()
server/test/.../subscriptions/SubscriptionDetectorTest.kt [C] unit fixtures reales
server/test/.../routes/SubscriptionRoutesTest.kt       [C] HTTP H2
shared/.../ui/Navigation.kt                            [M] Screen.Subscriptions
shared/.../App.kt                                      [M] wiring
shared/.../ui/mas/MasScreen.kt                         [M] ítem "Suscripciones"
shared/.../ui/subscriptions/SuscripcionesScreen.kt     [C] pantalla
```

(`[C]`=create, `[M]`=modify. Prefijos: `core/...` = `core/src/commonMain/kotlin/com/jvillada/movi/`, `server/...` = `server/src/main/kotlin/com/jvillada/movi/`, `server/test/...` = `server/src/test/kotlin/com/jvillada/movi/`, `shared/...` = `shared/src/commonMain/kotlin/com/jvillada/movi/`.)

---

### Task 1: Modelos core + plomería de repositorios

**Files:**
- Create: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Subscription.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepository.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/repository/WalletRepositoryImpl.kt`
- Modify: `core/src/nonWasmMain/kotlin/com/jvillada/movi/shared/repository/LocalRepository.kt`
- Modify: `core/src/jvmTest/kotlin/com/jvillada/movi/shared/repository/NoOpRepository.kt`

**Interfaces:**
- Consumes: nada nuevo.
- Produces (los usan Tasks 3-4):
  - `enum SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }`, `enum SubConfidence { HIGH, MEDIUM, LOW }`
  - `Subscription(id, merchantKey, displayName, amount: Long, currency: String, dayOfMonth: Int, status: SubStatus, confidence: SubConfidence, firstSeen: Long, lastSeen: Long, occurrences: Int, accountId: String?)`
  - `SubscriptionsResult(subscriptions: List<Subscription>, monthlyTotalCop: Long)`
  - Repo: `getSubscriptions(): SubscriptionsResult`, `detectSubscriptions(): SubscriptionsResult`, `updateSubscription(id: String, subscription: Subscription): Subscription`, `deleteSubscription(id: String)`

- [ ] **Step 1: Crear el modelo wire**

`core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Subscription.kt`:

```kotlin
package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }

@Serializable
enum class SubConfidence { HIGH, MEDIUM, LOW }

@Serializable
data class Subscription(
    val id: String,
    val merchantKey: String,    // canónico: "youtube", "anthropic_claude"
    val displayName: String,    // "YouTube", "Claude"
    val amount: Long,           // gasto mensual típico (mediana de la suma mensual, moneda nativa)
    val currency: String,       // "COP" | "USD"
    val dayOfMonth: Int,        // día típico de cobro
    val status: SubStatus,
    val confidence: SubConfidence,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int,       // meses distintos detectados
    val accountId: String? = null,
)

@Serializable
data class SubscriptionsResult(
    val subscriptions: List<Subscription>,
    val monthlyTotalCop: Long,  // suma AUTO+CONFIRMED en COP (USD × TRM)
)
```

- [ ] **Step 2: Interfaz del repo**

En `WalletRepository.kt`, agregar imports `Subscription`, `SubscriptionsResult` y, junto a los métodos de créditos:

```kotlin
    suspend fun getSubscriptions(): SubscriptionsResult
    suspend fun detectSubscriptions(): SubscriptionsResult
    suspend fun updateSubscription(id: String, subscription: Subscription): Subscription
    suspend fun deleteSubscription(id: String)
```

- [ ] **Step 3: Implementación HTTP**

En `WalletRepositoryImpl.kt` (mismos imports de estilo; `put`/`delete`/`get`/`post` ya están importados):

```kotlin
    override suspend fun getSubscriptions(): SubscriptionsResult =
        client.get("$baseUrl/api/subscriptions").body()

    override suspend fun detectSubscriptions(): SubscriptionsResult =
        client.post("$baseUrl/api/subscriptions/detect").body()

    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription =
        client.put("$baseUrl/api/subscriptions/$id") {
            contentType(ContentType.Application.Json)
            setBody(subscription)
        }.body()

    override suspend fun deleteSubscription(id: String) {
        client.delete("$baseUrl/api/subscriptions/$id")
    }
```

- [ ] **Step 4: LocalRepository (nonWasmMain) delega al remoto**

```kotlin
    override suspend fun getSubscriptions(): SubscriptionsResult = remote.getSubscriptions()
    override suspend fun detectSubscriptions(): SubscriptionsResult = remote.detectSubscriptions()
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = remote.updateSubscription(id, subscription)
    override suspend fun deleteSubscription(id: String) = remote.deleteSubscription(id)
```

(+ imports `Subscription`, `SubscriptionsResult`.)

- [ ] **Step 5: NoOpRepository (core jvmTest)**

```kotlin
    override suspend fun getSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun detectSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun updateSubscription(id: String, subscription: Subscription) = subscription
    override suspend fun deleteSubscription(id: String) {}
```

(+ imports.)

- [ ] **Step 6: Compilar**

Run: `./gradlew :core:test :core:jvmTest :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src
git commit -m "feat(core): modelos Subscription + métodos de repo del subscription tracker"
```

---

### Task 2: SubscriptionDetector puro (TDD)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionDetector.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionDetectorTest.kt`

**Interfaces:**
- Consumes: `FinancialEvent`, `TransactionType` (core), `SubConfidence` (Task 1).
- Produces (los usa Task 3):
  - `data class MerchantId(key: String, displayName: String, known: Boolean)`
  - `data class DetectedSub(merchantKey: String, displayName: String, amount: Long, currency: String, dayOfMonth: Int, occurrences: Int, firstSeen: Long, lastSeen: Long, confidence: SubConfidence, accountId: String?)`
  - `fun normalizeMerchant(description: String): MerchantId?`
  - `fun detectSubscriptions(events: List<FinancialEvent>, today: LocalDate): List<DetectedSub>`

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionDetectorTest.kt`:

```kotlin
package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionDetectorTest {

    private val today = LocalDate.of(2026, 7, 20)

    private fun at(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private var seq = 0
    private fun expense(desc: String, amount: Long, date: String, currency: String = "COP") = FinancialEvent(
        id = "ev-${seq++}", accountId = "acc-tc", type = TransactionType.EXPENSE,
        amount = amount, currency = currency, category = "Otros", description = desc,
        timestamp = at(date), source = EventSource.STATEMENT,
    )

    // ── normalizeMerchant ────────────────────────────────────────────────────

    @Test
    fun `known services match through gateway prefixes and suffixes`() {
        assertEquals("netflix", normalizeMerchant("PAYU*NETFLIX 110111")!!.key)
        assertEquals("youtube", normalizeMerchant("Google YOUTUBE Mmbrshp g.co")!!.key)
        assertEquals("anthropic_claude", normalizeMerchant("ANTHROPIC CLAUDE.AI SUBSCR")!!.key)
        assertEquals("directv", normalizeMerchant("DTV*DIRECTV COLOMBIA")!!.key)
        assertEquals("microsoft", normalizeMerchant("MICROSOFT*M365 FAMILIA")!!.key)
        assertTrue(normalizeMerchant("PAYU*NETFLIX 110111")!!.known)
    }

    @Test
    fun `unknown merchant falls back to a clean token`() {
        val m = normalizeMerchant("MERCPAGO*GIMNASIO BODYTECH")!!
        assertEquals(false, m.known)
        assertEquals("gimnasio_bodytech", m.key)
    }

    @Test
    fun `blank or too-short descriptions are rejected`() {
        assertNull(normalizeMerchant("  "))
        assertNull(normalizeMerchant("A1"))
    }

    // ── detectSubscriptions ──────────────────────────────────────────────────

    @Test
    fun `three stable months with regular cadence is HIGH`() {
        val events = listOf(
            expense("PAYU*NETFLIX", 44_900, "2026-04-14"),
            expense("PAYU*NETFLIX", 44_900, "2026-05-14"),
            expense("PAYU*NETFLIX", 44_900, "2026-06-14"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals("netflix", s.merchantKey)
        assertEquals(44_900, s.amount)
        assertEquals(14, s.dayOfMonth)
        assertEquals(3, s.occurrences)
        assertEquals(SubConfidence.HIGH, s.confidence)
    }

    @Test
    fun `two months is MEDIUM (candidate)`() {
        val events = listOf(
            expense("Google YOUTUBE Mmbrshp", 26_900, "2026-05-10"),
            expense("Google YOUTUBE Mmbrshp", 26_900, "2026-06-10"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        assertEquals(SubConfidence.MEDIUM, subs[0].confidence)
    }

    @Test
    fun `multiple same-cycle charges aggregate into one monthly amount`() {
        // Claude ×3 cuentas: tres cargos de USD 20 el mismo día, tres meses seguidos
        val events = (4..6).flatMap { m ->
            (1..3).map { expense("ANTHROPIC CLAUDE.AI", 20, "2026-0$m-05", currency = "USD") }
        }
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals("anthropic_claude", s.merchantKey)
        assertEquals(60, s.amount)           // suma mensual, no cargo individual
        assertEquals("USD", s.currency)
        assertEquals(SubConfidence.HIGH, s.confidence)
    }

    @Test
    fun `irregular amounts are not a subscription`() {
        val events = listOf(
            expense("EXITO COUNTRY", 312_400, "2026-04-02"),
            expense("EXITO COUNTRY", 128_900, "2026-05-07"),
            expense("EXITO COUNTRY", 402_100, "2026-06-19"),
        )
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `single month is not a subscription`() {
        val events = listOf(expense("MCDONALDS 73", 38_500, "2026-06-11"))
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `a gap longer than 45 days breaks the cadence`() {
        val events = listOf(
            expense("UBER *TRIP", 25_000, "2026-02-01"),
            expense("UBER *TRIP", 25_000, "2026-06-01"),
        )
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `INCOME events and future events are ignored`() {
        val income = expense("PAYU*NETFLIX", 44_900, "2026-05-14").copy(type = TransactionType.INCOME, id = "ev-i")
        val future = expense("PAYU*NETFLIX", 44_900, "2026-09-14")
        val events = listOf(income, future, expense("PAYU*NETFLIX", 44_900, "2026-06-14"))
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `same merchant in different currencies stays separate`() {
        val events = listOf(
            expense("ANTHROPIC CLAUDE.AI", 20, "2026-05-05", currency = "USD"),
            expense("ANTHROPIC CLAUDE.AI", 20, "2026-06-05", currency = "USD"),
            expense("ANTHROPIC CLAUDE.AI", 90_000, "2026-05-06"),
            expense("ANTHROPIC CLAUDE.AI", 90_000, "2026-06-06"),
        )
        assertEquals(2, detectSubscriptions(events, today).size)
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.subscriptions.SubscriptionDetectorTest"`
Expected: FAIL — `unresolved reference: normalizeMerchant`.

- [ ] **Step 3: Implementar el detector**

`server/src/main/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionDetector.kt`:

```kotlin
package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

data class MerchantId(val key: String, val displayName: String, val known: Boolean)

data class DetectedSub(
    val merchantKey: String,
    val displayName: String,
    val amount: Long,        // mediana de la suma mensual (moneda nativa)
    val currency: String,
    val dayOfMonth: Int,
    val occurrences: Int,    // meses distintos
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: SubConfidence,
    val accountId: String?,
)

// Servicios conocidos: substring (lowercase) → (key canónico, nombre de display).
// Se matchea sobre la descripción COMPLETA antes de limpiar, para que un prefijo de
// gateway que también identifica al servicio (DTV*) no se pierda al recortarlo.
private val KNOWN_SERVICES: List<Pair<String, Pair<String, String>>> = listOf(
    "netflix"     to ("netflix" to "Netflix"),
    "spotify"     to ("spotify" to "Spotify"),
    "youtube"     to ("youtube" to "YouTube"),
    "anthropic"   to ("anthropic_claude" to "Claude"),
    "claude"      to ("anthropic_claude" to "Claude"),
    "openai"      to ("openai" to "OpenAI"),
    "chatgpt"     to ("openai" to "OpenAI"),
    "microsoft"   to ("microsoft" to "Microsoft"),
    "directv"     to ("directv" to "DirecTV"),
    "dtv"         to ("directv" to "DirecTV"),
    "disney"      to ("disney" to "Disney+"),
    "hbo"         to ("hbo_max" to "Max"),
    "prime video" to ("prime_video" to "Prime Video"),
    "icloud"      to ("apple_icloud" to "iCloud"),
    "apple.com"   to ("apple" to "Apple"),
    "google one"  to ("google_one" to "Google One"),
    "github"      to ("github" to "GitHub"),
    "canva"       to ("canva" to "Canva"),
)

private val GATEWAY_PREFIXES = listOf(
    "paypal *", "paypal*", "google *", "google ", "mercpago*", "mercpago ",
    "generic dlocalgo*", "dlocalgo*", "dlo*", "payu*", "payu ", "ebanx*",
)

/** Comercio normalizado, o null si la descripción no identifica un comercio usable. */
fun normalizeMerchant(description: String): MerchantId? {
    val raw = description.trim().lowercase()
    if (raw.isBlank()) return null
    for ((needle, id) in KNOWN_SERVICES) {
        if (raw.contains(needle)) return MerchantId(id.first, id.second, known = true)
    }
    var d = raw
    for (p in GATEWAY_PREFIXES) {
        if (d.startsWith(p)) { d = d.removePrefix(p).trim(); break }
    }
    d = d.replace(Regex("[*#][a-z0-9 .\\-]*$"), "").trim()  // sufijos/códigos tras * o #
    d = d.replace(Regex("\\s+\\d{4,}$"), "").trim()           // números largos finales
    if (d.length < 3) return null
    val key = d.replace(Regex("[^a-z0-9]+"), "_").trim('_')
    if (key.length < 3) return null
    val display = d.split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    return MerchantId(key, display, known = false)
}

private const val DAY_MS = 86_400_000L

private fun dateOf(ts: Long): LocalDate =
    Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Detección determinística: agrupa EXPENSE por (merchantKey, currency) y marca como
 * suscripción los grupos con ≥2 meses distintos, suma mensual estable (dispersión ≤15%
 * sobre la mediana) y cadencia ~mensual (ningún hueco > 45 días entre cargos; los cargos
 * separados ≤3 días cuentan como el mismo ciclo). HIGH = ≥3 meses + dispersión ≤5% +
 * huecos regulares (26–35 días). Eventos futuros a [today] se ignoran.
 */
fun detectSubscriptions(events: List<FinancialEvent>, today: LocalDate): List<DetectedSub> {
    val expenses = events.asSequence()
        .filter { it.type == TransactionType.EXPENSE && !dateOf(it.timestamp).isAfter(today) }
        .mapNotNull { ev -> normalizeMerchant(ev.merchant ?: ev.description)?.let { it to ev } }
        .toList()

    return expenses
        .groupBy({ (m, ev) -> m.key to ev.currency }, { it })
        .mapNotNull { (groupKey, pairs) ->
            val (merchantKey, currency) = groupKey
            val display = pairs.first().first.displayName
            val evs = pairs.map { it.second }.sortedBy { it.timestamp }

            val byMonth = evs.groupBy { dateOf(it.timestamp).let { d -> d.year to d.monthValue } }
            if (byMonth.size < 2) return@mapNotNull null

            val gaps = evs.zipWithNext { a, b -> (b.timestamp - a.timestamp) / DAY_MS }
                .filter { it > 3 }   // mismo ciclo (p.ej. 3 cuentas Claude el mismo día) no es gap
            if (gaps.isEmpty() || gaps.any { it > 45 }) return@mapNotNull null

            val monthlySums = byMonth.values.map { l -> l.sumOf { it.amount } }.sorted()
            val median = monthlySums[monthlySums.size / 2]
            if (median <= 0) return@mapNotNull null
            val maxDev = monthlySums.maxOf { abs(it - median).toDouble() / median }
            if (maxDev > 0.15) return@mapNotNull null

            val days = evs.map { dateOf(it.timestamp).dayOfMonth }.sorted()
            val regular = gaps.all { it in 26..35 }
            val confidence = when {
                byMonth.size >= 3 && maxDev <= 0.05 && regular -> SubConfidence.HIGH
                else -> SubConfidence.MEDIUM
            }
            DetectedSub(
                merchantKey = merchantKey,
                displayName = display,
                amount      = median,
                currency    = currency,
                dayOfMonth  = days[days.size / 2],
                occurrences = byMonth.size,
                firstSeen   = evs.first().timestamp,
                lastSeen    = evs.last().timestamp,
                confidence  = confidence,
                accountId   = evs.map { it.accountId }.distinct().singleOrNull(),
            )
        }
}
```

Nota sobre el test `INCOME events and future events are ignored`: al ignorar el INCOME y el cargo futuro queda UN solo cargo de netflix → un solo mes → no es suscripción. Ese es el assert.

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.subscriptions.SubscriptionDetectorTest"`
Expected: PASS (10 tests). Si algún fixture no cuadra con la implementación, arreglar la IMPLEMENTACIÓN (los fixtures son el contrato), salvo error aritmético evidente del fixture.

- [ ] **Step 5: Commit**

```bash
git add server/src
git commit -m "feat(server): SubscriptionDetector puro — normalización de comercios + detección mensual"
```

---

### Task 3: Tabla + rutas /api/subscriptions (TDD HTTP)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt` (agregar al final)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt` (lista de `SchemaUtils.create`)
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/SubscriptionRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt` (registrar tras `creditRoutes()`)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/SubscriptionRoutesTest.kt`

**Interfaces:**
- Consumes: `detectSubscriptions`, `DetectedSub` (Task 2); `Subscription`, `SubscriptionsResult`, `SubStatus`, `SubConfidence` (Task 1); `loadNonVoidedEvents` (`server/.../balance/EventQueries.kt`); `FxRateService.usdToCop()`.
- Produces: rutas `GET /api/subscriptions` → `SubscriptionsResult`; `POST /api/subscriptions/detect` → `SubscriptionsResult` (corre el detector + upsert); `PUT /api/subscriptions/{id}` (body `Subscription`; solo `status`, `displayName`, `amount`, `dayOfMonth` son mutables) → `Subscription`; `DELETE /api/subscriptions/{id}` → 204/404.

- [ ] **Step 1: Tabla**

Al final de `Tables.kt`:

```kotlin
object Subscriptions : Table("subscriptions") {
    val id          = varchar("id", 50)
    val userId      = varchar("user_id", 50)
    val merchantKey = varchar("merchant_key", 80)
    val displayName = varchar("display_name", 100)
    val amount      = long("amount")                 // gasto mensual típico (moneda nativa)
    val currency    = varchar("currency", 10)
    val dayOfMonth  = integer("day_of_month")
    val status      = varchar("status", 20)          // AUTO | CANDIDATE | CONFIRMED | DISMISSED
    val confidence  = varchar("confidence", 10)      // HIGH | MEDIUM | LOW
    val firstSeen   = long("first_seen")
    val lastSeen    = long("last_seen")
    val occurrences = integer("occurrences")
    val accountId   = varchar("account_id", 50).nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        index("idx_subscriptions_user_id", false, userId)
        uniqueIndex("uq_subscriptions_user_merchant_currency", userId, merchantKey, currency)
    }
}
```

En `DatabaseFactory.init()`, agregar `Subscriptions` al final de la lista de `SchemaUtils.create(...)`.

- [ ] **Step 2: Escribir el test HTTP que falla**

`server/src/test/kotlin/com/jvillada/movi/server/routes/SubscriptionRoutesTest.kt` — MISMO harness que `CreditRoutesTest.kt` (leerlo primero y copiar: H2 URL propia `jdbc:h2:mem:subscription_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`, mismo `mintToken`/`tokenFor`/`testModule`/`wireApp`). En `@BeforeTest`: drop/create de TODAS las tablas de `Tables.kt` (incluyendo `Subscriptions` y `Credits`); sembrar usuarios A y B, una cuenta `acc-tc-a` (`type = "CREDIT_CARD"`, `currency = "COP"`, userId A) y estos eventos EXPENSE para A (usar un helper local `insertExpense(id, desc, amount, tsIso)` que haga `Events.insert` con `type="EXPENSE"`, `currency="COP"`, `category="Otros"`, `source="STATEMENT"`, `reconciliationStatus="UNCONFIRMED"`, `accountId="acc-tc-a"`, `timestamp = LocalDate.parse(tsIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()`):

- `PAYU*NETFLIX` 44_900 en `2026-04-14`, `2026-05-14`, `2026-06-14` (→ HIGH → AUTO)
- `Google YOUTUBE Mmbrshp` 26_900 en `2026-05-10`, `2026-06-10` (→ MEDIUM → CANDIDATE)
- `EXITO COUNTRY` 312_400 en `2026-06-02` (una sola vez → no detectado)

Tests (todos con auth de A salvo que se indique):

```kotlin
    @Test
    fun `detect creates AUTO and CANDIDATE subscriptions from events`() = testApplication {
        wireApp()
        val res = client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val subs = body["subscriptions"]!!.jsonArray
        assertEquals(2, subs.size)
        val byKey = subs.associateBy { it.jsonObject["merchantKey"]!!.jsonPrimitive.content }
        assertEquals("AUTO",      byKey["netflix"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("CANDIDATE", byKey["youtube"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        // total mensual = solo AUTO+CONFIRMED → netflix
        assertEquals(44_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    @Test
    fun `re-detect is idempotent`() = testApplication {
        wireApp()
        repeat(2) { client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") } }
        val res = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(2, Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
    }

    @Test
    fun `dismissed stays dismissed after re-detect`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val netflix = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        val id = netflix["id"]!!.jsonPrimitive.content
        val dismissed = netflix.toMutableMap().apply { put("status", JsonPrimitive("DISMISSED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(dismissed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val after = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        assertEquals("DISMISSED", after["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `confirmed is not downgraded by re-detect and total includes it`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val youtube = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        val id = youtube["id"]!!.jsonPrimitive.content
        val confirmed = youtube.toMutableMap().apply { put("status", JsonPrimitive("CONFIRMED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(confirmed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val body = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject
        val after = body["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        assertEquals("CONFIRMED", after["status"]!!.jsonPrimitive.content)
        assertEquals(44_900L + 26_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    @Test
    fun `user B has no subscriptions and cannot edit A's`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val bList = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals(0, Json.parseToJsonElement(bList.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
        val aSub = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject
        val id = aSub["id"]!!.jsonPrimitive.content
        val put = client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(aSub.toMutableMap())))
        }
        assertEquals(HttpStatusCode.NotFound, put.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }.status)
    }

    @Test
    fun `DELETE removes and second delete is 404`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val id = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }
```

Imports extra que este archivo necesita sobre el patrón de `CreditRoutesTest`: `kotlinx.serialization.json.JsonObject`, `kotlinx.serialization.json.JsonPrimitive`, `io.ktor.client.request.post`, `java.time.LocalDate`, `java.time.ZoneOffset`.

Nota FX: los fixtures son 100% COP, así que `monthlyTotalCop` no depende de la TRM (el `FxRateService` no se invoca para COP o devuelve fallback — ver Step 3: solo convierte USD).

- [ ] **Step 3: Verificar que falla, luego implementar rutas**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.SubscriptionRoutesTest"`
Expected: FAIL — 404 en `/api/subscriptions/detect` (ruta no existe).

`server/src/main/kotlin/com/jvillada/movi/server/routes/SubscriptionRoutes.kt`:

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.subscriptions.DetectedSub
import com.jvillada.movi.server.subscriptions.detectSubscriptions
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToLong

fun Route.subscriptionRoutes() {
    route("/api/subscriptions") {
        get {
            val uid = call.userId()
            call.respond(resultFor(uid))
        }

        post("/detect") {
            val uid = call.userId()
            val events = loadNonVoidedEvents(uid)
            val detected = detectSubscriptions(events, LocalDate.now(ZoneOffset.UTC))
            dbQuery {
                val existing = Subscriptions.selectAll()
                    .where { Subscriptions.userId eq uid }
                    .associateBy { it[Subscriptions.merchantKey] to it[Subscriptions.currency] }
                for (d in detected) {
                    val row = existing[d.merchantKey to d.currency]
                    when {
                        row == null -> Subscriptions.insert {
                            it[id]          = "sub_${UUID.randomUUID()}"
                            it[userId]      = uid
                            it[merchantKey] = d.merchantKey
                            it[displayName] = d.displayName
                            it[amount]      = d.amount
                            it[currency]    = d.currency
                            it[dayOfMonth]  = d.dayOfMonth
                            it[status]      = statusForNew(d).name
                            it[confidence]  = d.confidence.name
                            it[firstSeen]   = d.firstSeen
                            it[lastSeen]    = d.lastSeen
                            it[occurrences] = d.occurrences
                            it[accountId]   = d.accountId
                        }
                        row[Subscriptions.status] == SubStatus.DISMISSED.name -> Unit  // el usuario dijo que no
                        row[Subscriptions.status] == SubStatus.CONFIRMED.name ->
                            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                                it[amount]      = d.amount
                                it[lastSeen]    = d.lastSeen
                                it[occurrences] = d.occurrences
                                it[confidence]  = d.confidence.name
                            }
                        else ->  // AUTO o CANDIDATE: refrescar todo y re-evaluar estado
                            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                                it[displayName] = d.displayName
                                it[amount]      = d.amount
                                it[dayOfMonth]  = d.dayOfMonth
                                it[status]      = statusForNew(d).name
                                it[confidence]  = d.confidence.name
                                it[firstSeen]   = d.firstSeen
                                it[lastSeen]    = d.lastSeen
                                it[occurrences] = d.occurrences
                                it[accountId]   = d.accountId
                            }
                    }
                }
            }
            call.respond(resultFor(uid))
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receive<Subscription>()
            val updated = dbQuery {
                Subscriptions.update({ (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }) {
                    it[status]      = body.status.name
                    it[displayName] = body.displayName
                    it[amount]      = body.amount
                    it[dayOfMonth]  = body.dayOfMonth.coerceIn(1, 31)
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound)
            val row = dbQuery {
                Subscriptions.selectAll()
                    .where { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
                    .first().toSubscription()
            }
            call.respond(row)
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val deleted = dbQuery {
                Subscriptions.deleteWhere { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun statusForNew(d: DetectedSub): SubStatus =
    if (d.confidence == SubConfidence.HIGH) SubStatus.AUTO else SubStatus.CANDIDATE

private suspend fun resultFor(uid: String): SubscriptionsResult {
    val subs = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .map { it.toSubscription() }
    }
    val active = subs.filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
    val needsFx = active.any { it.currency == "USD" }
    val rate = if (needsFx) FxRateService.usdToCop() else 0.0
    val total = active.sumOf { s ->
        when (s.currency) {
            "COP" -> s.amount
            "USD" -> (s.amount * rate).roundToLong()
            else  -> 0L
        }
    }
    return SubscriptionsResult(subscriptions = subs, monthlyTotalCop = total)
}

private fun ResultRow.toSubscription() = Subscription(
    id          = this[Subscriptions.id],
    merchantKey = this[Subscriptions.merchantKey],
    displayName = this[Subscriptions.displayName],
    amount      = this[Subscriptions.amount],
    currency    = this[Subscriptions.currency],
    dayOfMonth  = this[Subscriptions.dayOfMonth],
    status      = SubStatus.valueOf(this[Subscriptions.status]),
    confidence  = SubConfidence.valueOf(this[Subscriptions.confidence]),
    firstSeen   = this[Subscriptions.firstSeen],
    lastSeen    = this[Subscriptions.lastSeen],
    occurrences = this[Subscriptions.occurrences],
    accountId   = this[Subscriptions.accountId],
)
```

En `Routing.kt`, dentro de `authenticate("jwt")`, agregar `subscriptionRoutes()` después de `creditRoutes()`.

- [ ] **Step 4: Suite completa**

Run: `./gradlew :server:test`
Expected: PASS — SubscriptionRoutesTest verde y CERO regresiones.

- [ ] **Step 5: Commit**

```bash
git add server/src
git commit -m "feat(server): tabla subscriptions + rutas GET/detect/PUT/DELETE con upsert por estados"
```

---

### Task 4: UI — SuscripcionesScreen + navegación

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/Navigation.kt` (agregar `data object Subscriptions : Screen()` tras `Recurrentes`)
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/App.kt` (import + rama `Screen.Subscriptions -> SuscripcionesScreen(navigate)`)
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/mas/MasScreen.kt` (ítem en la lista, después de "Recurrentes"):
  `MasItem("Suscripciones", Icons.Rounded.Autorenew, Color(0xFF81D4FA), Color(0x2481D4FA), Screen.Subscriptions),` (import `Icons.Rounded.Autorenew`)
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/subscriptions/SuscripcionesScreen.kt`

**Interfaces:**
- Consumes: `Repositories.wallets.getSubscriptions()/detectSubscriptions()/updateSubscription()/deleteSubscription()` (Task 1); componentes `MinCard`, `MinCardVariant`, `MinSectionHeader`, `Hairline`, `MinBottomNav`, `NavTab`, `formatCOP`, tokens `Min*`, `toUserMessage()` (mismos imports que `CreditosScreen.kt` — leerla primero como referencia de estilo).
- Produces: pantalla final; sin consumidores posteriores.

No hay infra de tests de UI — el gate es compilación (Android + wasmJs) + fidelidad al spec; e2e manual en Task 5.

- [ ] **Step 1: Navegación + menú** (los tres `Modify` de arriba, exactamente como se indican).

- [ ] **Step 2: Crear SuscripcionesScreen**

`shared/src/commonMain/kotlin/com/jvillada/movi/ui/subscriptions/SuscripcionesScreen.kt`:

```kotlin
package com.jvillada.movi.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SuscripcionesScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var result by remember { mutableStateOf(SubscriptionsResult(emptyList(), 0)) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { Repositories.wallets.getSubscriptions() }
            .onSuccess { result = it }
            .onFailure { error = it.toUserMessage() }
    }

    fun rescan() {
        if (scanning) return
        scanning = true
        error = null
        coroutine.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                .onSuccess { result = it }
                .onFailure { error = it.toUserMessage() }
            scanning = false
        }
    }

    fun setStatus(sub: Subscription, status: SubStatus) {
        coroutine.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { reloadKey++ }
                .onFailure { error = it.toUserMessage() }
        }
    }

    val candidates = result.subscriptions.filter { it.status == SubStatus.CANDIDATE }
    val active = result.subscriptions
        .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
        .sortedBy { it.dayOfMonth }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickable { onNavigate(Screen.Mas) })
            Text("Suscripciones", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
            Text(
                if (scanning) "Escaneando…" else "Re-escanear",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (scanning) MinTextMute else MinText,
                modifier = Modifier.clickable(enabled = !scanning) { rescan() },
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Text("Total mensual", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        formatCOP(result.monthlyTotalCop),
                        fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText,
                        letterSpacing = (-1.4).sp, lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${active.size} activas", fontSize = 12.sp, color = MinTextMute)
                }
            }

            error?.let { msg ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(msg, fontSize = 12.sp, color = MinExpense, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            if (candidates.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Candidatos a revisar", count = candidates.size)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            candidates.forEach { s ->
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
                                        Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                        Text(formatAmount(s), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                    }
                                    Text("Visto ${s.occurrences} ${if (s.occurrences == 1) "mes" else "meses"} · día ${s.dayOfMonth}", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ActionChip("Confirmar", primary = true) { setStatus(s, SubStatus.CONFIRMED) }
                                        ActionChip("Descartar", primary = false) { setStatus(s, SubStatus.DISMISSED) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Activas", count = if (active.isNotEmpty()) active.size else null)
                    if (active.isEmpty()) {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Text(
                                "Sin suscripciones aún — importa 2-3 meses de extractos de tarjeta y toca Re-escanear",
                                fontSize = 14.sp, color = MinTextMute,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        active.forEach { s ->
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
                                    Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(formatAmount(s), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Cobro el día ${s.dayOfMonth}${if (s.status == SubStatus.AUTO) " · auto" else ""}", fontSize = 12.sp, color = MinTextMute)
                                    Text("Quitar", fontSize = 12.sp, color = MinExpense, modifier = Modifier.clickable { setStatus(s, SubStatus.DISMISSED) })
                                }
                            }
                        }
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

private fun formatAmount(s: Subscription): String =
    if (s.currency == "USD") "US$${s.amount}" else formatCOP(s.amount)

@Composable
private fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
    }
}
```

Notas: (1) el spec pide "última vez: hace X" — v1 lo simplifica a "Cobro el día N" porque `lastSeen` en meses relativos requiere lógica de fechas multiplataforma que no aporta al flujo de decisión; si el revisor lo objeta, es una desviación consciente documentada aquí. (2) "Quitar" en activas cubre el "editar/eliminar" del spec como `DISMISSED` (recuperable re-escaneando… no: dismissed no vuelve — es el comportamiento correcto para "no me interesa"). (3) Si `MinSectionHeader` no acepta `count` nullable o `MinExpense` no existe, mirar el uso real en `CreditosScreen.kt` y ajustar al token/firma correcta — nunca inventar colores.

- [ ] **Step 3: Compilar ambos targets**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src
git commit -m "feat(ui): SuscripcionesScreen con candidatos/activas + entrada en Más"
```

---

### Task 5: Verificación end-to-end

**Files:** ninguno (verificación; fixes con mensaje `fix:` si aparecen defectos).

- [ ] **Step 1: Suite y compilación completas**

Run: `./gradlew :server:test :core:jvmTest :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL. (NO correr `./gradlew build` completo: los links iOS release tienen un OOM pre-existente con chip propio.)

- [ ] **Step 2: E2E vivo contra server local**

Postgres local ya corre (Homebrew, credenciales en `server/.env`). `./gradlew :server:run` en background, esperar `curl localhost:8080/health` → OK. Con un usuario desechable `verify-subs-<ts>@movi.test` (registrar vía `POST /api/auth/register`, capturar JWT):

1. Crear cuenta: `POST /api/accounts` `{"id":"","name":"TC Verify","type":"CREDIT_CARD","balance":0,"currency":"COP"}` → tomar `id`.
2. Sembrar 3 meses de Netflix + 2 de YouTube + 1 compra puntual vía `POST /api/events` (timestamps retro-fechados; body `FinancialEvent` con `type:"EXPENSE"`, `source:"MANUAL"`, `description` como en los fixtures del detector, `accountId` de la cuenta).
3. `POST /api/subscriptions/detect` → verificar: netflix `AUTO`, youtube `CANDIDATE`, la compra puntual ausente, `monthlyTotalCop` = monto de netflix.
4. `PUT` youtube → `CONFIRMED` → `GET` refleja total con ambas.
5. Re-detect → youtube sigue `CONFIRMED` (no downgrade).
6. `DELETE` una y verificar 204/404.
7. No tocar `demo@movi.app`. Matar el server al final.

- [ ] **Step 3: Commit de cualquier fix + push**

```bash
git push -u origin feat/subscription-tracker-v1
```

(El merge/PR se decide con superpowers:finishing-a-development-branch.)
