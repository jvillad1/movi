# SP-0 · Security & durable persistence — design

**Fecha:** 2026-06-13
**Parte de:** arco "production-ready" de movi (SP-0 → SP-1 reminders → SP-2 ingestion).
**Alcance:** `:server` (auth, rutas, persistencia) + limpieza de métodos muertos en
`:core` repo. Sin cambios de UI salvo verificar estados vacíos.

## Contexto

Una auditoría del código (2026-06-13) encontró bloqueadores de producción:

1. **Secreto filtrado:** `ANTHROPIC_API_KEY` real estuvo commiteado en `server/.env`
   (historia en 662617a). Ya **scrubbeado** de la historia local (git filter-branch);
   el usuario está **rotando** la llave en la consola de Anthropic; GitHub se limpia con
   force-push de `master`. `server/.env` ya está en `.gitignore` y des-trackeado.
2. **`JWT_SECRET` con default inseguro:** `JwtConfig` cae al literal
   `"movi-dev-secret-change-in-production"` si la env var falta.
3. **Sin aislamiento por usuario** en `WalletRoutes`, `SmsRoutes` y los endpoints
   credits/goals/recurring de `FinanceRoutes` → cualquier usuario autenticado ve datos
   globales (los mismos demo para todos). `AccountRoutes`/`EventRoutes`/`StatementRoutes`/
   budgets/finance-summary **sí** filtran por `userId`.
4. **Fuga en el chat AI:** `AiRoutes.buildUserContext()` está hardcodeado a una persona
   ("Camilo"); todo usuario recibe las finanzas de esa persona desde `/api/ai/chat`.
5. **Persistencia efímera:** `Stores.kt` usa archivos JSON (`movi-data/*.json`) en el disco
   **efímero** de Railway → se pierden en cada redeploy; además no tienen `userId`.

## Decisiones de alcance (aprobadas)

- Primer pase = **aislamiento + durabilidad core**. Se difiere a un follow-up rápido el
  endurecimiento de transporte (CORS a orígenes conocidos, desactivar cleartext/debuggable
  en release, rate-limiting de login).
- **Clean cutover:** las tablas nuevas arrancan vacías. Los datos JSON actuales no tienen
  dueño (sin `userId`) y ya se pierden en cada redeploy, así que no hay migración.
- **Sin refresh tokens** (fuera de alcance; el token sigue siendo de 30 días). YAGNI.

### Decisión abierta locked-as-recommended (override en review)

- **credits & goals → `emptyList()` por usuario** (igual que `/api/holdings` hoy), y se
  elimina el store JSON global. No tienen escritor in-app ni dueño en SP-0/1/2, así que crear
  tablas Postgres vacías sería infraestructura muerta (YAGNI). Cuando una feature real los
  posea (p.ej. créditos derivados de cuentas de deuda en SP-1, o una feature de metas), esa
  feature agrega su almacenamiento por usuario. **Consecuencia:** `CreditosScreen`,
  la sección de créditos/metas de `AnalisisScreen` y `MetasScreen` muestran estado vacío
  (honesto: ese demo era data compartida ficticia). `MetasScreen` ya tiene estado vacío
  ("Sin metas de ahorro aún"); se verifica que `CreditosScreen` también.

## Diseño

### A — Auth & secreto (`server/.../auth/JwtConfig.kt`)

`secret` deja de tener default inseguro. Resolución: `JWT_SECRET` env → `server/.env`/`.env`
(dev) → si falta, `error("JWT_SECRET not set")` y el server no arranca (igual que
`DATABASE_URL` en `DatabaseFactory`). Lectura de `.env` se mantiene solo para dev local.
Validez de token sin cambios (30 días). Sin refresh tokens.

### B — Aislamiento multi-tenant

Toda ruta autenticada filtra por `call.userId()`.

- **Eliminar `WalletRoutes` por completo:** rutas `/api/wallets`, `/api/wallets/{id}`,
  `/api/wallets/{id}/transactions` (GET/POST), `/api/transactions/by-day`. Quitar
  `walletRoutes()` de `plugins/Routing.kt`. Borrar `Stores.wallets` y `Stores.transactions`.
  Borrar los métodos muertos del repo en `:core`:
  `getWallets/getWallet/getTransactionsByDay/getTransactions/postTransaction` (y sus
  modelos `Wallet`/`Transaction`/`TransactionDay` si quedan sin uso tras la limpieza —
  verificar; pueden seguir usados por código legacy de UI, en cuyo caso solo se quitan los
  métodos de red). Cero llamadas desde `shared/` UI — verificado.
- **`FinanceRoutes`:** `/api/credits` y `/api/goals` → `call.respond(emptyList<...>())`.
  `/api/recurring-rules` → consulta la tabla `recurring_rules` por `userId`.
- **`SmsRoutes`:** los cinco endpoints filtran por `userId` contra `sms_messages`.
- Rutas ya correctas (accounts, events, statements, budgets, finance-summary): sin cambios.

### C — Persistencia durable (Postgres, Exposed)

Dos tablas nuevas en `server/.../db/Tables.kt`, añadidas a `SchemaUtils.create(...)` en
`DatabaseFactory.init()`:

```kotlin
object RecurringRules : Table("recurring_rules") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 100)
    val category   = varchar("category", 100)
    val amount     = long("amount")
    val dayOfMonth = integer("day_of_month")
    val type       = varchar("type", 20)   // TransactionType.name
    override val primaryKey = PrimaryKey(id)
    init { index("idx_recurring_rules_user_id", false, userId) }
}

object SmsMessages : Table("sms_messages") {
    val id     = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val time   = varchar("time", 50)
    val bank   = varchar("bank", 100)
    val text   = text("text")
    val state  = varchar("state", 20)      // "new" | "confirmed" | "ignored"
    val det    = varchar("det", 255)
    override val primaryKey = PrimaryKey(id)
    init { index("idx_sms_messages_user_id", false, userId) }
}
```

`recurring_rules` arranca vacía; su GET devuelve las filas del usuario (vacío hasta que SP-1
agregue CRUD + due-dates + reminders). `SmsMessages` arranca vacía; `parse` (regex puro) sin
cambios; `confirm`/`ignore` mutan la fila propia del usuario (vacío hasta que SP-2 agregue
ingestión desde el dispositivo).

`Stores.kt` pierde `wallets`, `transactions`, `credits`, `goals`, `recurring`, `sms` —
queda solo `merchantRules` (ya por usuario, `merchant-rules-{userId}.json`). Se puede borrar
`JsonListStore`/`BudgetStorage`/`UserStore` si quedan sin uso (verificar).

### D — Fix de la fuga del contexto AI (`server/.../routes/AiRoutes.kt`)

Reemplazar `buildUserContext()` hardcodeado por `buildUserContext(uid: String)` que consulta
los datos reales del usuario autenticado:
- sus cuentas + balances computados (`accountCopValue` / `loadNonVoidedEvents`, ya existen);
- ingresos/egresos del mes en curso (misma lógica que `/api/finance-summary`);
- sus presupuestos (`Budgets` por `userId`).
credits/goals/recurring están vacíos por ahora → se omiten del contexto hasta que existan.
El handler pasa `call.userId()` a `buildUserContext`. Cierra la fuga y hace el copiloto útil.

## Testing

- **Unit (`:server`):** `JwtConfig` arranca con secreto seteado y lanza error sin él;
  mantener cobertura de `parseSms`.
- **Aislamiento (lo importante):** tests HTTP con `testApplication` y dos usuarios probando
  que el usuario A no ve `recurring_rules`/`sms_messages` del usuario B, y que
  `/api/credits` y `/api/goals` devuelven `[]`. Sería el primer test de integración HTTP del
  repo: se levanta un harness mínimo de Ktor. Si una DB de test es complicada en CI, se deja
  registrado y se cae a testear los query-builders/branches directamente (sin red).
- **Boot/compile:** `:server:test` verde; el server arranca con `JWT_SECRET` seteado y falla
  rápido sin él.

## Fuera de alcance (siguientes sub-proyectos / follow-up)

- Endurecimiento de transporte: CORS por origen, `usesCleartextTraffic=false` y
  `debuggable=false` en release, rate-limiting de login.
- Refresh tokens / expiración corta.
- SP-1: reminders (due-dates en cuentas/reglas, motor de ejecución, notificaciones, CRUD de
  recurring rules). SP-2: ingestión por visión (screenshots) + lectura real de SMS en Android.
