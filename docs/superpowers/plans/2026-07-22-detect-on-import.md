# Detect-on-Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que importar un extracto dispare automáticamente (y en silencio) la detección de suscripciones, sin que un fallo de detección afecte jamás el import.

**Architecture:** Extraer el cuerpo de `POST /api/subscriptions/detect` a una función compartida `runSubscriptionDetection(uid)` en `server/.../subscriptions/SubscriptionSync.kt`; la ruta `/detect` delega (comportamiento idéntico, la suite existente es la red de seguridad); el handler de `POST /api/statements/import` la llama al final envuelta en `runCatching` + warn.

**Tech Stack:** Ktor + Exposed (server-only). Sin cambios en `:core` ni `:shared`.

**Spec:** `docs/superpowers/specs/2026-07-22-detect-on-import-design.md`

## Global Constraints

- Trabajar en `/Users/carolinarestrepo/Developer/movi`, branch `feat/detect-on-import`. JBR 21 ya es JAVA_HOME.
- Sin dependencias nuevas. Solo `:server` cambia.
- **Un fallo de detección JAMÁS falla el import** — `runCatching` + `call.application.log.warn(...)`.
- La extracción NO cambia comportamiento observable de `/detect`: la suite `SubscriptionRoutesTest` existente debe pasar SIN modificar ni un carácter de ese archivo.
- Tests HTTP con el harness H2 patrón `CreditRoutesTest.kt` (leerlo primero; JWT test-secret, drop/create schema completo con `Subscriptions` y `Credits`).
- NO correr `./gradlew build` completo (OOM pre-existente de links iOS release, tracked aparte); usar `:server:test`.
- Cada tarea termina verde y con commit.

## File Structure

```
server/.../subscriptions/SubscriptionSync.kt   [C] runSubscriptionDetection + helpers de upsert movidos
server/.../routes/SubscriptionRoutes.kt        [M] /detect delega; pierde los helpers movidos
server/.../routes/StatementRoutes.kt           [M] trigger al final del import (~3 líneas)
server/test/.../routes/StatementRoutesTest.kt  [C] trigger e2e (2 tests)
```

---

### Task 1: Extraer runSubscriptionDetection (sin cambio de comportamiento)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionSync.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/SubscriptionRoutes.kt`

**Interfaces:**
- Consumes: `detectSubscriptions`, `DetectedSub` (mismo paquete); `loadNonVoidedEvents`; tabla `Subscriptions`; `dbQuery`.
- Produces (lo usa Task 2): `suspend fun runSubscriptionDetection(uid: String)` — pública, en `com.jvillada.movi.server.subscriptions`.

Esto es un MOVE mecánico: los símbolos se trasladan VERBATIM (sin editar sus cuerpos) desde `SubscriptionRoutes.kt` al archivo nuevo. La suite existente de `SubscriptionRoutesTest` (que NO se toca) es la prueba de no-regresión.

- [ ] **Step 1: Crear SubscriptionSync.kt con la función pública y los símbolos movidos**

Encabezado y función pública nuevos:

```kotlin
package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Corre la detección de suscripciones y el upsert por estados para [uid].
 * Best-effort: el caller decide si un fallo importa (el import de extractos lo
 * envuelve en runCatching; la ruta /detect lo deja propagar).
 */
suspend fun runSubscriptionDetection(uid: String) {
    val events = loadNonVoidedEvents(uid)
        .filterNot { it.description.startsWith(FAMIRIOS_STAMP_PREFIX) }
    val detected = detectSubscriptions(events, LocalDate.now(ZoneOffset.UTC))
    dbQuery {
        val existing = Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .associateBy { it[Subscriptions.merchantKey] to it[Subscriptions.currency] }
        for (d in detected) {
            upsertDetected(uid, d, existing[d.merchantKey to d.currency])
        }
    }
}
```

Luego MOVER (cortar de `SubscriptionRoutes.kt`, pegar aquí VERBATIM, cuerpos intactos, visibilidad `private` como están) estos símbolos con sus KDoc/comentarios:
- `UNIQUE_VIOLATION_SQLSTATE`
- `FAMIRIOS_STAMP_PREFIX` (con su comentario largo)
- `statusForNew(d: DetectedSub)`
- `Transaction.upsertDetected(uid, d, row)` (con su comentario del SAVEPOINT)
- `applyExisting(...)`, `refreshRow(...)`, `insertNew(...)` (todos los helpers privados que `upsertDetected` llama — verificar con el compilador que ninguno quedó atrás)

Ajustar los imports de ambos archivos: `SubscriptionSync.kt` toma los que sus símbolos necesiten (la lista de arriba cubre lo previsible; el compilador es la red); `SubscriptionRoutes.kt` pierde los que ya no usa (probablemente `ExposedSQLException`, `Transaction`, `insert`, `UUID`, `loadNonVoidedEvents`, `detectSubscriptions`... dejar solo lo que `resultFor`/`toSubscription`/las rutas usan).

- [ ] **Step 2: Delegar /detect**

El handler queda:

```kotlin
        post("/detect") {
            val uid = call.userId()
            runSubscriptionDetection(uid)
            call.respond(resultFor(uid))
        }
```

(`runSubscriptionDetection` está en el mismo paquete que importa este archivo — agregar el import `com.jvillada.movi.server.subscriptions.runSubscriptionDetection`.)

- [ ] **Step 3: Verificar cero regresiones**

Run: `git diff --stat server/src/test/` → vacío (los tests NO se tocaron).
Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL, 111 tests verdes (misma cuenta que antes).

- [ ] **Step 4: Commit**

```bash
git add server/src
git commit -m "refactor(server): extraer runSubscriptionDetection a SubscriptionSync (sin cambio de comportamiento)"
```

---

### Task 2: Trigger en el import + tests e2e (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/StatementRoutes.kt` (final del handler `post("/api/statements/import")`, justo antes del `call.respond(HttpStatusCode.OK, ...)`)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/StatementRoutesTest.kt` (nuevo)

**Interfaces:**
- Consumes: `runSubscriptionDetection(uid)` (Task 1); handler de import existente; modelos `ImportDecision`/`ParsedTransaction` (core).
- Produces: nada nuevo para tareas posteriores.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/kotlin/com/jvillada/movi/server/routes/StatementRoutesTest.kt` — harness copiado de `CreditRoutesTest.kt` (leerlo primero): H2 propia `jdbc:h2:mem:statement_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`, mismos helpers `mintToken`/`tokenFor`/`testModule`/`wireApp`, drop/create de TODAS las tablas de `Tables.kt` (incluye `Subscriptions`, `Credits`). Sembrar: usuario A y una cuenta `acc-tc-a` tipo `CREDIT_CARD`, COP, userId A (sin eventos).

Helper local para el body (JSON directo):

```kotlin
    private fun parsedTx(id: String, date: String, merchant: String, amount: Long) =
        """{"id":"$id","date":"$date","merchant":"$merchant","amount":$amount,"currency":"COP",
            "type":"EXPENSE","category":"Otros","description":"$merchant","rawText":""}"""

    private fun importBody(txs: String) =
        """{"statementId":"st-test","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[$txs],"reconciliations":[],"skipped":[]}"""
```

Tests:

```kotlin
    @Test
    fun `import triggers subscription detection automatically`() = testApplication {
        wireApp()
        val txs = listOf(
            parsedTx("p1", "2026-04-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p2", "2026-05-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p3", "2026-06-14", "PAYU*NETFLIX", 44_900),
        ).joinToString(",")
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(txs))
        }
        assertEquals(HttpStatusCode.OK, res.status)

        // SIN llamar /detect: el import debe haber disparado la detección solo
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(1, subs.size)
        val netflix = subs[0].jsonObject
        assertEquals("netflix", netflix["merchantKey"]!!.jsonPrimitive.content)
        assertEquals("AUTO", netflix["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `import without recurring patterns creates no subscriptions`() = testApplication {
        wireApp()
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(parsedTx("p1", "2026-06-11", "EXITO COUNTRY", 312_400)))
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(0, subs.size)
    }
```

(Imports como en `SubscriptionRoutesTest.kt`: ktor client get/post/header/setBody, kotlinx JSON, HttpStatusCode, etc.)

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.StatementRoutesTest"`
Expected: FAIL — el primer test espera 1 suscripción y encuentra 0 (el import aún no dispara detección). El segundo test puede pasar (vacío trivialmente) — correcto.

- [ ] **Step 3: Implementar el trigger**

En `StatementRoutes.kt`, dentro del handler `post("/api/statements/import")`, entre el `dbQuery { StatementImports.insert { ... } }` y el `call.respond(HttpStatusCode.OK, mapOf("imported" to importedCount + reconciledCount))`:

```kotlin
        // Trigger silencioso de detección de suscripciones (spec 2026-07-22-detect-on-import):
        // best-effort — un fallo aquí JAMÁS falla el import; "Re-escanear" queda como fallback.
        runCatching { runSubscriptionDetection(uid) }
            .onFailure { call.application.log.warn("detect-on-import falló para $uid: ${it.message}") }
```

Imports nuevos en el archivo: `com.jvillada.movi.server.subscriptions.runSubscriptionDetection` y `io.ktor.server.application.log` (si `call.application.log` no resuelve con los imports actuales).

- [ ] **Step 4: Verificar que pasa + suite completa**

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.routes.StatementRoutesTest"`
Expected: PASS (2 tests).
Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL, 113 tests verdes, cero regresiones.

- [ ] **Step 5: Commit y push**

```bash
git add server/src
git commit -m "feat(server): el import de extractos dispara la detección de suscripciones (best-effort)"
git push -u origin feat/detect-on-import
```
