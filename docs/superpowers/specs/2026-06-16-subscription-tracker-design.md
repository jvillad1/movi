# Subscription Tracker — diseño

**Fecha:** 2026-06-16
**Alcance:** `:core` (modelo + repo), `:server` (motor de detección + tabla + rutas),
`:shared` (pantalla Suscripciones). Reusa el pipeline de import de extractos y los
`financial_events` existentes.

## Problema / valor

El usuario quiere **auto-descubrir** sus suscripciones: que Movi lea su historial de
transacciones (extractos de tarjeta de varios meses) y **detecte solo** qué cargos se
repiten = suscripciones, para encontrar las que olvidó o que siguen cobrando sin uso.
Hoy las suscripciones se pierden entre los movimientos de tarjeta (Claude, Microsoft,
YouTube, DirecTV… vistos en el extracto de la Mastercard).

Valor #1 elegido: **auto-descubrimiento** (no carga manual). Control elegido: **híbrido
por confianza** — auto-confirmar lo obvio, dejar lo dudoso como candidato.

## Decisiones (locked)

- **Arquitectura A:** motor **determinístico server-side** sobre `financial_events` +
  tabla `subscriptions` persistida + endpoint + pantalla. (No Claude en el camino
  caliente; se reserva para limpiar nombres ambiguos solo si la heurística no alcanza —
  fuera de v1.)
- **Híbrido por confianza:** ALTA (≥3 meses + monto estable ±5% + cadencia regular) →
  `auto`; MEDIA/BAJA (2 meses o monto variable) → `candidate`.
- **Dependencia de datos:** la detección corre sobre los `financial_events` que existan.
  El usuario importa 2-3 meses de extractos de tarjeta con el flujo actual
  (`POST /api/statements/upload`). Degrada bien con poca data (todo queda `candidate`).
- **YAGNI v1:** sin alertas de suba de precio, sin auto-marcado de "sin usar" (solo se
  muestra "última vez"), solo cadencia mensual, sin cancelación/deep-links.

## Diseño

### A — Modelo de datos

Tabla `subscriptions` (Postgres, por usuario), en `server/.../db/Tables.kt`:

```kotlin
object Subscriptions : Table("subscriptions") {
    val id          = varchar("id", 50)
    val userId      = varchar("user_id", 50)
    val merchantKey = varchar("merchant_key", 80)   // canónico: "youtube", "anthropic_claude"
    val displayName = varchar("display_name", 100)  // "YouTube", "Claude"
    val amount      = long("amount")                // monto típico/último (moneda nativa)
    val currency    = varchar("currency", 10)       // "COP" | "USD"
    val dayOfMonth  = integer("day_of_month")        // día típico de cobro
    val status      = varchar("status", 20)         // auto | candidate | confirmed | dismissed
    val firstSeen   = long("first_seen")
    val lastSeen    = long("last_seen")
    val occurrences = integer("occurrences")         // meses detectados
    val confidence  = varchar("confidence", 10)      // high | medium | low
    val accountId   = varchar("account_id", 50).nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_subscriptions_user_id", false, userId) }
}
```

Registrar en `DatabaseFactory.init()` (`SchemaUtils.create(...)`).

Modelo wire (`core/.../model/Subscription.kt`):

```kotlin
@Serializable enum class SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }
@Serializable enum class SubConfidence { HIGH, MEDIUM, LOW }
@Serializable data class Subscription(
    val id: String, val merchantKey: String, val displayName: String,
    val amount: Long, val currency: String, val dayOfMonth: Int,
    val status: SubStatus, val confidence: SubConfidence,
    val firstSeen: Long, val lastSeen: Long, val occurrences: Int,
    val accountId: String? = null,
)
@Serializable data class SubscriptionsResult(
    val subscriptions: List<Subscription>,
    val monthlyTotalCop: Long,   // suma estimada en COP (USD × TRM)
)
```

### B — Motor de detección (`server/.../subscriptions/SubscriptionDetector.kt`, puro)

Funciones puras (unit-testables, sin DB/red):

```kotlin
fun normalizeMerchant(description: String): MerchantId?   // (key, displayName) o null si no es comercio reconocible
fun detectSubscriptions(events: List<DetectionEvent>, today: LocalDate): List<DetectedSub>
```

`normalizeMerchant`: lowercase; quita prefijos de gateway (`paypal *`, `google *play`,
`mercpago*`, `generic dlocalgo*`, `dtv*`…) y sufijos/códigos (`*d`, `*di...`); matchea
contra un **mapa de servicios conocidos** (netflix, spotify, youtube, anthropic/claude,
microsoft, directv, disney, hbo/max, prime, apple/icloud, google one, github, canva…).
Si no matchea un servicio conocido pero el patrón es claramente recurrente, usa el token
limpio como key.

`detectSubscriptions`:
1. Toma `financial_events` EXPENSE, los pasa por `normalizeMerchant`, descarta los que no
   normalizan a un comercio.
2. Agrupa por `(merchantKey, currency)`.
3. Por grupo: cuenta **meses distintos** con cargo; calcula monto representativo (mediana)
   y dispersión; estima `dayOfMonth` (mediana de día).
4. Es suscripción si: **≥2 meses distintos**, dispersión de monto **≤15%**, cadencia
   ~mensual (gaps de ~28-31 días).
5. `confidence`: HIGH si ≥3 meses + dispersión ≤5% + cadencia regular; si no, MEDIUM/LOW.
6. Devuelve `DetectedSub(merchantKey, displayName, amount, currency, dayOfMonth,
   occurrences, firstSeen, lastSeen, confidence, accountId)`.

**Upsert** (en la capa de ruta, con DB): por cada `DetectedSub`, hacer match con
`subscriptions` por `(userId, merchantKey, currency)`:
- existe `dismissed` → no tocar (el usuario dijo que no es sub).
- existe `confirmed` → actualizar `amount/lastSeen/occurrences`, no bajar el estado.
- existe `auto/candidate` → actualizar; estado = `auto` si HIGH, si no `candidate`.
- no existe → insertar (`auto` si HIGH, `candidate` si no).

### C — API (`server/.../routes/SubscriptionRoutes.kt`, registrar en `Routing.kt`)

Todo por `call.userId()` (patrón Budgets/Recurring):
- `GET /api/subscriptions` → `SubscriptionsResult` (lista + `monthlyTotalCop` usando
  `FxRateService.usdToCop()` para los USD).
- `POST /api/subscriptions/detect` → corre `detectSubscriptions` sobre los eventos del
  usuario (`loadNonVoidedEvents`), hace el upsert, devuelve la lista actualizada.
- `PUT /api/subscriptions/{id}` → cambia `status` (confirm/dismiss) y/o edita
  `amount/displayName/dayOfMonth`. Filtra por id + userId.
- `DELETE /api/subscriptions/{id}` → borra (id + userId).

Repo (`:core` `WalletRepository` + impls): `getSubscriptions(): SubscriptionsResult`,
`detectSubscriptions(): SubscriptionsResult`, `updateSubscription(id, Subscription)`,
`deleteSubscription(id)`.

### D — UI (`shared/.../ui/subscriptions/SuscripcionesScreen.kt`)

Estilo Movi (`MinCard`, tema `Min*`):
- **Header:** total mensual de suscripciones (suma, USD→COP) + cantidad.
- **Sección "Candidatos a revisar"** (si hay `candidate`): comercio · monto · "visto N
  meses" · botones **Confirmar** (`PUT status=confirmed`) / **Descartar** (`PUT
  status=dismissed`).
- **Sección "Activas"** (`auto` + `confirmed`): comercio · monto · día de cobro ·
  **"última vez: hace X"** (de `lastSeen`). Tap → editar/eliminar.
- **Botón "Re-escanear"** → `POST /detect` y refresca.
- **Entrada:** ítem "Suscripciones" en el menú **Más** (`MasScreen`); opcional una tarjeta
  resumen en el dashboard (fuera de v1 si agrega ruido).

### Dependencia: importar historial

El usuario importa 2-3 meses de extractos de tarjeta (xlsx/PDF/imagen) por
`POST /api/statements/upload` → quedan como `financial_events` → el motor los usa. No hay
trabajo nuevo de import; se reusa el pipeline existente. (El extracto
`docs/movements/bancolombia tc mastercard.xlsx` es un ejemplo real con las subs del
usuario.)

## Testing

- **Unit `SubscriptionDetector`** (server): fixtures con los datos reales de la Mastercard
  → debe detectar Claude (×3 cuentas), Microsoft, YouTube (×2), DirecTV; y **NO** detectar
  McDonald's, Uber, Éxito, compras de cuota=1 puntuales. Tests de `normalizeMerchant`
  (gateways/sufijos → key correcta). Tests de confianza (3 meses estable → HIGH; 2 meses →
  candidate).
- **Aislamiento HTTP** (harness H2): `GET/PUT/DELETE/detect` por usuario; el usuario B no
  ve/edita las subs de A; upsert idempotente (re-detectar no duplica).
- **Compile:** `:shared` compila; `:server:test` verde.

## Fuera de alcance (futuro)
Alertas de suba de precio; auto-marcado "sin usar"; cadencias anual/semanal; limpieza de
comercios con Claude; cancelación/deep-links; auto-detección al importar (v1 es por botón
"Re-escanear", el trigger en import se puede sumar después).
