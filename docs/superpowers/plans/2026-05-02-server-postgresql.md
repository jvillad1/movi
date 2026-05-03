# Server PostgreSQL Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JSON-file storage on the Ktor server with PostgreSQL via Exposed ORM, adding userId isolation to accounts and events.

**Architecture:** Routes call Exposed DSL directly inside `dbQuery { }` (a `withContext(Dispatchers.IO)` wrapper). `DatabaseFactory.init()` starts before all Ktor plugins. Tables are created via `SchemaUtils.create()` at startup — no migration framework.

**Tech Stack:** Kotlin/Ktor 3, Exposed 0.55.0, HikariCP 5.1.0, PostgreSQL 16 (Docker Compose), PostgreSQL JDBC 42.7.3

---

## File Map

| Action | File |
|--------|------|
| Create | `docker-compose.yml` (project root) |
| Modify | `server/.env` |
| Modify | `gradle/libs.versions.toml` |
| Modify | `server/build.gradle.kts` |
| Create | `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt` |
| Create | `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt` |
| Modify | `server/src/main/kotlin/com/jvillada/movi/server/Application.kt` |
| Modify | `server/src/main/kotlin/com/jvillada/movi/server/plugins/Auth.kt` |
| Rewrite | `server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt` |
| Rewrite | `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt` |
| Rewrite | `server/src/main/kotlin/com/jvillada/movi/server/routes/AuthRoutes.kt` |
| Modify | `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt` |
| Delete | `server/src/main/kotlin/com/jvillada/movi/server/storage/UserStore.kt` |

---

## Task 1: Dependencies + Docker Compose + .env

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Create: `docker-compose.yml`
- Modify: `server/.env`

- [ ] **Step 1: Add versions to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
exposed    = "0.55.0"
hikaricp   = "5.1.0"
postgresql = "42.7.3"
```

Add to `[libraries]`:
```toml
exposed-core        = { module = "org.jetbrains.exposed:exposed-core",        version.ref = "exposed" }
exposed-jdbc        = { module = "org.jetbrains.exposed:exposed-jdbc",        version.ref = "exposed" }
hikaricp            = { module = "com.zaxxer:HikariCP",                       version.ref = "hikaricp" }
postgresql-driver   = { module = "org.postgresql:postgresql",                 version.ref = "postgresql" }
```

- [ ] **Step 2: Add dependencies to server/build.gradle.kts**

In the `dependencies { }` block, add after the existing `implementation` lines:
```kotlin
implementation(libs.exposed.core)
implementation(libs.exposed.jdbc)
implementation(libs.hikaricp)
implementation(libs.postgresql.driver)
```

- [ ] **Step 3: Create docker-compose.yml at project root**

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: movi
      POSTGRES_USER: movi
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-secret}
    ports:
      - "5432:5432"
    volumes:
      - movi_pgdata:/var/lib/postgresql/data

volumes:
  movi_pgdata:
```

- [ ] **Step 4: Update server/.env**

Add these three lines (keep the existing JWT_SECRET and ANTHROPIC_API_KEY lines):
```
DATABASE_URL=jdbc:postgresql://localhost:5432/movi
DATABASE_USER=movi
DATABASE_PASSWORD=secret
```

- [ ] **Step 5: Verify Gradle resolves new deps**

```bash
./gradlew :server:dependencies --configuration runtimeClasspath 2>&1 | grep -E "exposed|hikari|postgresql"
```

Expected: lines showing `exposed-core`, `exposed-jdbc`, `HikariCP`, `postgresql` resolved.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml server/build.gradle.kts docker-compose.yml server/.env
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "build: add Exposed 0.55 + HikariCP + PostgreSQL driver deps"
```

---

## Task 2: Tables + DatabaseFactory + wire into Application

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`
- Create: `server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/Application.kt`

- [ ] **Step 1: Create Tables.kt**

```kotlin
package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id           = varchar("id", 36)
    val email        = varchar("email", 255).uniqueIndex()
    val name         = varchar("name", 100)
    val passwordHash = varchar("password_hash", 255)
    override val primaryKey = PrimaryKey(id)
}

object Accounts : Table("accounts") {
    val id       = varchar("id", 36)
    val userId   = varchar("user_id", 36)
    val name     = varchar("name", 100)
    val type     = varchar("type", 30)
    val balance  = long("balance").default(0)
    val currency = varchar("currency", 10).default("COP")
    override val primaryKey = PrimaryKey(id)
}

object Events : Table("financial_events") {
    val id                   = varchar("id", 36)
    val userId               = varchar("user_id", 36)
    val accountId            = varchar("account_id", 36)
    val type                 = varchar("type", 20)
    val amount               = long("amount")
    val category             = varchar("category", 100)
    val description          = varchar("description", 255)
    val merchant             = varchar("merchant", 255).nullable()
    val timestamp            = long("timestamp")
    val source               = varchar("source", 20).default("MANUAL")
    val rawPayload           = text("raw_payload").nullable()
    val reconciliationStatus = varchar("reconciliation_status", 20).default("UNCONFIRMED")
    val syncedAt             = long("synced_at").nullable()
    override val primaryKey  = PrimaryKey(id)
}

object VoidEvents : Table("void_events") {
    val id              = varchar("id", 36)
    val userId          = varchar("user_id", 36)
    val originalEventId = varchar("original_event_id", 36)
    val reason          = varchar("reason", 500).nullable()
    val timestamp       = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Create DatabaseFactory.kt**

```kotlin
package com.jvillada.movi.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseFactory {
    fun init() {
        val url  = readEnv("DATABASE_URL")  ?: error("DATABASE_URL not set — add it to server/.env")
        val user = readEnv("DATABASE_USER") ?: "movi"
        val pass = readEnv("DATABASE_PASSWORD") ?: "secret"

        val config = HikariConfig().apply {
            jdbcUrl         = url
            username        = user
            password        = pass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
        }
    }

    private fun readEnv(key: String): String? {
        System.getenv(key)?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
        }
    }
}

suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
```

- [ ] **Step 3: Wire DatabaseFactory.init() in Application.kt**

Replace the full file:
```kotlin
package com.jvillada.movi.server

import com.jvillada.movi.server.db.DatabaseFactory
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureCORS
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureCORS()
    configureSerialization()
    configureMonitoring()
    configureAuth()
    configureRouting()
}
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :server:compileKotlin 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Smoke test startup**

```bash
docker compose up -d
./gradlew :server:run &
sleep 5
curl -s http://localhost:8080/health
```

Expected: `OK`

Kill the server with `pkill -f "server:run"` after the test.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/db/ \
        server/src/main/kotlin/com/jvillada/movi/server/Application.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: DatabaseFactory + Exposed table definitions, wired at startup"
```

---

## Task 3: userId() extension on ApplicationCall

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Auth.kt`

- [ ] **Step 1: Add the extension function**

Append to the bottom of `Auth.kt` (after the `configureAuth()` function):
```kotlin
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
```

The full file becomes:
```kotlin
package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.auth.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal

fun Application.configureAuth() {
    authentication {
        jwt("jwt") {
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }
}

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :server:compileKotlin 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/plugins/Auth.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: userId() extension on ApplicationCall from JWT principal"
```

---

## Task 4: Rewrite AccountRoutes with Exposed + userId isolation

**Files:**
- Rewrite: `server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt`

- [ ] **Step 1: Replace full file content**

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            val uid = call.userId()
            val accounts = dbQuery {
                Accounts.selectAll()
                    .where { Accounts.userId eq uid }
                    .map { it.toAccount() }
            }
            call.respond(accounts)
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val account = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(account)
        }

        post {
            val body = call.receive<Account>()
            val uid = call.userId()
            val account = body.copy(
                id = body.id.ifBlank { "acc_${System.currentTimeMillis()}" }
            )
            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[name]     = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
            }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}

private fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :server:compileKotlin 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/AccountRoutes.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: AccountRoutes — Exposed + userId isolation (replaces JsonListStore)"
```

---

## Task 5: Rewrite EventRoutes with Exposed + atomic balance updates

**Files:**
- Rewrite: `server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt`

- [ ] **Step 1: Replace full file content**

```kotlin
package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Route.eventRoutes() {
    route("/api/events") {

        post {
            val body = call.receive<FinancialEvent>()
            val uid = call.userId()
            val now = System.currentTimeMillis()
            val event = body.copy(
                id        = body.id.ifBlank { "ev_$now" },
                timestamp = if (body.timestamp == 0L) now else body.timestamp,
            )

            val accountExists = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .count() > 0
            }
            if (!accountExists) return@post call.respond(HttpStatusCode.NotFound, "Account not found")

            dbQuery {
                Events.insert {
                    it[id]                   = event.id
                    it[userId]               = uid
                    it[accountId]            = event.accountId
                    it[type]                 = event.type.name
                    it[amount]               = event.amount
                    it[category]             = event.category
                    it[description]          = event.description
                    it[merchant]             = event.merchant
                    it[timestamp]            = event.timestamp
                    it[source]               = event.source.name
                    it[rawPayload]           = event.rawPayload
                    it[reconciliationStatus] = event.reconciliationStatus.name
                    it[syncedAt]             = event.syncedAt
                }
                val currentBalance = Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .first()[Accounts.balance]
                val delta = if (event.type == TransactionType.INCOME) event.amount else -event.amount
                Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                    it[balance] = currentBalance + delta
                }
            }
            call.respond(HttpStatusCode.Created, event)
        }

        get {
            val uid = call.userId()
            val accountId = call.request.queryParameters["accountId"]
            val result = dbQuery {
                val voidedIds = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                val notVoided = if (voidedIds.isEmpty()) Op.TRUE
                                else Events.id notInList voidedIds.toList()
                val accountFilter = if (accountId != null) Events.accountId eq accountId else Op.TRUE
                Events.selectAll()
                    .where { (Events.userId eq uid) and accountFilter and notVoided }
                    .orderBy(Events.timestamp, SortOrder.DESC)
                    .map { it.toEvent() }
            }
            call.respond(result)
        }

        get("/by-day") {
            val uid = call.userId()
            val result = dbQuery {
                val voidedIds = VoidEvents.selectAll()
                    .where { VoidEvents.userId eq uid }
                    .map { it[VoidEvents.originalEventId] }
                    .toSet()
                val notVoided = if (voidedIds.isEmpty()) Op.TRUE
                                else Events.id notInList voidedIds.toList()
                Events.selectAll()
                    .where { (Events.userId eq uid) and notVoided }
                    .orderBy(Events.timestamp, SortOrder.DESC)
                    .map { it.toEvent() }
                    .groupBy { epochToUtcDate(it.timestamp) }
                    .map { (date, items) ->
                        EventDay(
                            date  = date,
                            total = items.sumOf { e ->
                                if (e.type == TransactionType.INCOME) e.amount else -e.amount
                            },
                            items = items,
                        )
                    }
                    .sortedByDescending { it.date }
            }
            call.respond(result)
        }

        post("/{id}/void") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val reason = call.request.queryParameters["reason"]

            val event = dbQuery {
                Events.selectAll()
                    .where { (Events.id eq id) and (Events.userId eq uid) }
                    .firstOrNull()?.toEvent()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val alreadyVoided = dbQuery {
                VoidEvents.selectAll()
                    .where { VoidEvents.originalEventId eq id }
                    .count() > 0
            }
            if (alreadyVoided) return@post call.respond(HttpStatusCode.Conflict, "Already voided")

            val void = dbQuery {
                val now = System.currentTimeMillis()
                val voidId = "void_$now"
                VoidEvents.insert {
                    it[VoidEvents.id]              = voidId
                    it[VoidEvents.userId]          = uid
                    it[VoidEvents.originalEventId] = id
                    it[VoidEvents.reason]          = reason
                    it[VoidEvents.timestamp]       = now
                }
                val currentBalance = Accounts.selectAll()
                    .where { (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }
                    .first()[Accounts.balance]
                val delta = if (event.type == TransactionType.INCOME) -event.amount else event.amount
                Accounts.update({ (Accounts.id eq event.accountId) and (Accounts.userId eq uid) }) {
                    it[balance] = currentBalance + delta
                }
                VoidEvent(
                    id              = voidId,
                    originalEventId = id,
                    reason          = reason,
                    timestamp       = now,
                )
            }
            call.respond(HttpStatusCode.Created, void)
        }
    }
}

private fun ResultRow.toEvent() = FinancialEvent(
    id                   = this[Events.id],
    accountId            = this[Events.accountId],
    type                 = TransactionType.valueOf(this[Events.type]),
    amount               = this[Events.amount],
    category             = this[Events.category],
    description          = this[Events.description],
    merchant             = this[Events.merchant],
    timestamp            = this[Events.timestamp],
    source               = EventSource.valueOf(this[Events.source]),
    rawPayload           = this[Events.rawPayload],
    reconciliationStatus = ReconciliationStatus.valueOf(this[Events.reconciliationStatus]),
    syncedAt             = this[Events.syncedAt],
)

private fun epochToUtcDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :server:compileKotlin 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/EventRoutes.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: EventRoutes — Exposed + userId isolation + atomic balance updates"
```

---

## Task 6: Migrate AuthRoutes to Users table, delete UserStore

**Files:**
- Rewrite: `server/src/main/kotlin/com/jvillada/movi/server/routes/AuthRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt`
- Delete: `server/src/main/kotlin/com/jvillada/movi/server/storage/UserStore.kt`

- [ ] **Step 1: Replace AuthRoutes.kt**

```kotlin
package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.authRoutes() {
    route("/api/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            if (req.email.isBlank() || req.name.isBlank() || req.password.length < 6) {
                return@post call.respond(HttpStatusCode.BadRequest, "Email required, password min 6 chars")
            }

            val emailTaken = dbQuery {
                Users.selectAll().where { Users.email eq req.email.lowercase().trim() }.count() > 0
            }
            if (emailTaken) return@post call.respond(HttpStatusCode.Conflict, "Email already registered")

            val userId  = "usr_${System.currentTimeMillis()}"
            val email   = req.email.lowercase().trim()
            val name    = req.name.trim()
            val hash    = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())

            dbQuery {
                Users.insert {
                    it[id]           = userId
                    it[Users.email]  = email
                    it[Users.name]   = name
                    it[passwordHash] = hash
                }
            }

            val token = JwtConfig.makeToken(userId, email)
            call.respond(HttpStatusCode.Created, AuthResponse(token, userId, name, email))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val row = dbQuery {
                Users.selectAll()
                    .where { Users.email eq req.email.lowercase().trim() }
                    .firstOrNull()
            } ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")

            val verified = BCrypt.verifyer()
                .verify(req.password.toCharArray(), row[Users.passwordHash]).verified
            if (!verified) return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")

            val token = JwtConfig.makeToken(row[Users.id], row[Users.email])
            call.respond(AuthResponse(token, row[Users.id], row[Users.name], row[Users.email]))
        }
    }
}
```

- [ ] **Step 2: Remove users from Stores.kt**

In `Stores.kt`, delete this import and the `users` line:
```kotlin
// Remove this line:
val users = UserStore(File(DATA_DIR, "users.json"))
```

Also remove the import: `import com.jvillada.movi.server.storage.UserStore` if present. Keep all other stores (`accounts`, `events`, `voidEvents`, `wallets`, etc.) as-is since they're still used by legacy routes.

Note: `accounts`, `events`, and `voidEvents` in `Stores.kt` are now unused by the rewritten routes but are still referenced by `FinanceRoutes` and other legacy routes — leave them until those routes are migrated.

- [ ] **Step 3: Delete UserStore.kt**

```bash
rm server/src/main/kotlin/com/jvillada/movi/server/storage/UserStore.kt
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :server:compileKotlin 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/jvillada/movi/server/routes/AuthRoutes.kt \
        server/src/main/kotlin/com/jvillada/movi/server/storage/Stores.kt
git rm server/src/main/kotlin/com/jvillada/movi/server/storage/UserStore.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: AuthRoutes migrated to Users table, UserStore deleted"
```

---

## Task 7: End-to-end smoke test

**Goal:** Verify the full register → login → account → event → void → get-by-day flow against a live Postgres instance.

- [ ] **Step 1: Start Postgres and server**

```bash
docker compose up -d
sleep 3
./gradlew :server:run &
sleep 8
curl -s http://localhost:8080/health
```

Expected: `OK`

- [ ] **Step 2: Register a user**

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@movi.co","name":"Juan Test","password":"secret123"}' | python3 -m json.tool
```

Expected: JSON with `token`, `userId`, `name`, `email`. Save the token:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test2@movi.co","name":"Juan Test","password":"secret123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo $TOKEN
```

- [ ] **Step 3: Create an account**

```bash
curl -s -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"","name":"Bancolombia","type":"CHECKING","balance":0,"currency":"COP"}' | python3 -m json.tool
```

Expected: JSON with `id`, `name`, `type`, `balance: 0`. Save the account id:
```bash
ACCT_ID=$(curl -s -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"","name":"Efectivo","type":"CASH","balance":0,"currency":"COP"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo $ACCT_ID
```

- [ ] **Step 4: Post an event, verify balance**

```bash
curl -s -X POST http://localhost:8080/api/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"\",\"accountId\":\"$ACCT_ID\",\"type\":\"INCOME\",\"amount\":500000,\"category\":\"Salario\",\"description\":\"Globant\",\"timestamp\":0,\"source\":\"MANUAL\",\"reconciliationStatus\":\"UNCONFIRMED\"}" | python3 -m json.tool

# Verify account balance is now 500000
curl -s http://localhost:8080/api/accounts/$ACCT_ID \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

Expected: account `balance: 500000`

- [ ] **Step 5: Get events by day**

```bash
curl -s http://localhost:8080/api/events/by-day \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

Expected: list with one `EventDay` containing the income event, `total: 500000`, `date` in `YYYY-MM-DD` format.

- [ ] **Step 6: Void the event, verify balance reversal**

```bash
EVENT_ID=$(curl -s "http://localhost:8080/api/events?accountId=$ACCT_ID" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

curl -s -X POST "http://localhost:8080/api/events/$EVENT_ID/void" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Verify balance back to 0
curl -s http://localhost:8080/api/accounts/$ACCT_ID \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

Expected: account `balance: 0`. GET /api/events returns empty list (voided event excluded).

- [ ] **Step 7: Stop server and commit**

```bash
pkill -f "server:run" || true
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit --allow-empty -m "test: smoke test Plan 4 — PostgreSQL persistence verified end-to-end"
```

- [ ] **Step 8: Push to GitHub**

```bash
git push https://jvillad1:$(gh auth token --user jvillad1)@github.com/jvillad1/movi.git master
```
