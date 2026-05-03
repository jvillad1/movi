# Movi Server — PostgreSQL Persistence
**Date:** 2026-05-02
**Status:** Approved

---

## Overview

Replace the server's JSON-file storage layer with PostgreSQL backed by Exposed ORM and HikariCP connection pooling. Fix the critical missing `userId` isolation (all users currently share the same data). Migrate `accounts`, `events`, `void_events`, and `users` tables. Leave legacy routes (`/api/wallets`, `/api/sms`, etc.) on JSON for now.

---

## Motivation

| Problem | Impact |
|---------|--------|
| JSON files lost on redeploy / disk wipe | Data loss in production |
| No `userId` filtering on accounts/events | Any authenticated user sees all data |
| `UserStore` is a separate JSON+BCrypt system | Two disconnected storage systems |
| `SyncEngine` on clients pushes to endpoints that write to JSON | Sync is unreliable |

---

## Architecture

```
Ktor Routes
    └── Exposed DSL (transactions)
            └── HikariCP connection pool
                    └── PostgreSQL 16
```

No repository interface layer — routes call Exposed directly. The project is small enough that the extra abstraction adds complexity without benefit.

---

## Infrastructure

### docker-compose.yml (project root)

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

### Environment variables

`server/.env` adds:
```
DATABASE_URL=jdbc:postgresql://localhost:5432/movi
DATABASE_USER=movi
DATABASE_PASSWORD=secret
```

`DATABASE_URL` is the single required variable. The server reads it at startup and fails fast with a clear error if missing.

### Dependencies added to `server/build.gradle.kts`

```kotlin
implementation(libs.exposed.core)
implementation(libs.exposed.jdbc)
implementation(libs.hikaricp)
implementation(libs.postgresql.driver)
```

Version catalog additions:
```toml
exposed = "0.61.0"
hikaricp = "6.3.0"
postgresql = "42.7.5"
```

---

## Database Tables

All defined in `server/src/main/kotlin/com/jvillada/movi/server/db/Tables.kt`.

### Users
```kotlin
object Users : Table("users") {
    val id           = varchar("id", 36)
    val email        = varchar("email", 255).uniqueIndex()
    val name         = varchar("name", 100)
    val passwordHash = varchar("password_hash", 255)
    override val primaryKey = PrimaryKey(id)
}
```

### Accounts
```kotlin
object Accounts : Table("accounts") {
    val id       = varchar("id", 36)
    val userId   = varchar("user_id", 36)
    val name     = varchar("name", 100)
    val type     = varchar("type", 30)
    val balance  = long("balance").default(0)
    val currency = varchar("currency", 10).default("COP")
    override val primaryKey = PrimaryKey(id)
}
```

### Financial Events
```kotlin
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
```

### Void Events
```kotlin
object VoidEvents : Table("void_events") {
    val id              = varchar("id", 36)
    val userId          = varchar("user_id", 36)
    val originalEventId = varchar("original_event_id", 36)
    val reason          = varchar("reason", 500).nullable()
    val timestamp       = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}
```

No foreign key constraints at the DB level — keeps schema creation simple and avoids ordering issues. Referential integrity is enforced at the application layer.

---

## DatabaseFactory

`server/src/main/kotlin/com/jvillada/movi/server/db/DatabaseFactory.kt`

```kotlin
object DatabaseFactory {
    fun init() {
        val url  = System.getenv("DATABASE_URL")  ?: error("DATABASE_URL not set")
        val user = System.getenv("DATABASE_USER") ?: "movi"
        val pass = System.getenv("DATABASE_PASSWORD") ?: "secret"

        val config = HikariConfig().apply {
            jdbcUrl         = url
            username        = user
            password        = pass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
        transaction { SchemaUtils.create(Users, Accounts, Events, VoidEvents) }
    }
}
```

Called once in `Application.kt` before any plugin configuration.

---

## JWT userId extraction

All authenticated routes extract `userId` from the JWT principal:

```kotlin
fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
```

Defined as an extension function added to the existing `server/src/main/kotlin/com/jvillada/movi/server/plugins/Auth.kt`.

---

## Updated Routes

### AccountRoutes.kt

| Method | Path | Behavior |
|--------|------|----------|
| `GET` | `/api/accounts` | All accounts for `userId` |
| `GET` | `/api/accounts/{id}` | Account by id, 404 if not found or belongs to different user |
| `POST` | `/api/accounts` | Insert new account, auto-generate id if blank |

Balance updates happen only via `postEvent` / `voidEvent` — never via a direct PATCH on account.

### EventRoutes.kt

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/api/events` | Insert event + update account balance atomically in a DB transaction |
| `GET` | `/api/events?accountId=` | Active events (excluding voided) for userId |
| `GET` | `/api/events/by-day` | Events grouped by calendar date (YYYY-MM-DD), sorted desc |
| `POST` | `/api/events/{id}/void` | Insert VoidEvent + reverse balance atomically, 409 if already voided |

The `by-day` grouping uses the event's `timestamp` (epoch millis) converted to a UTC date string (`YYYY-MM-DD`) server-side using `java.time.Instant`.

### AuthRoutes.kt (UserStore → DB)

`register` and `login` now query the `Users` table via Exposed instead of `UserStore`. BCrypt hashing stays the same.

---

## Migration of existing UserStore

`UserStore` and its `JsonListStore` backing are deleted. `Stores.users` is removed from `Stores.kt`. Auth routes access `Users` table directly.

Existing JSON user files are ignored — users re-register on the new DB. Since this is a development-stage app with no production users, no data migration script is needed.

---

## What stays on JSON (unchanged)

- `/api/wallets` → `Stores.wallets`
- `/api/sms` → `Stores.sms`
- `/api/finance-summary` → `Stores.recurring`, `Stores.goals`, etc.
- `/api/budgets` → `Stores.budgets`
- `/api/recurring-rules` → `Stores.recurring`
- `/api/ai/*` → stateless

These will be migrated in a future plan when the new-model UI screens are built for them.

---

## Error handling

- Missing `DATABASE_URL` at startup → `error()` crash with clear message
- `POST /api/events` with unknown `accountId` → 404 (account not found for userId)
- `POST /api/events/{id}/void` on already-voided → 409 Conflict
- DB exceptions → caught by existing Ktor `StatusPages` plugin, returns 500

---

## Testing

Manual smoke test against a real Postgres instance (Docker Compose):
1. `docker compose up -d` — start Postgres
2. `./gradlew :server:run` — server starts, tables created
3. `POST /api/auth/register` — create user
4. `POST /api/auth/login` — get JWT
5. `POST /api/accounts` — create account
6. `POST /api/events` — post event, verify balance updated
7. `GET /api/events/by-day` — verify grouping
8. `POST /api/events/{id}/void` — verify balance reversed + 409 on repeat

---

## Implementation Tasks

1. Add Exposed + HikariCP + PostgreSQL driver to version catalog and `server/build.gradle.kts`
2. Create `docker-compose.yml` + update `server/.env`
3. Create `db/Tables.kt` and `db/DatabaseFactory.kt`; wire `DatabaseFactory.init()` in `Application.kt`
4. Rewrite `AccountRoutes.kt` using Exposed + userId isolation
5. Rewrite `EventRoutes.kt` using Exposed + userId isolation + atomic balance updates
6. Migrate `AuthRoutes.kt` from `UserStore` to `Users` table; delete `UserStore.kt`
7. Smoke test end-to-end: register → login → account → event → void → get-by-day
