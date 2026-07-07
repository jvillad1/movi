# Créditos reales (F1 + F2) — diseño

**Fecha:** 2026-07-07
**Alcance:** `:core` (modelos + repo), `:server` (tabla `credit_terms` + rutas + integración
con recordatorios), `:shared` (pantalla Créditos real + sheet de creación/edición).
Construye sobre el tipo de cuenta `LOAN` (spec `2026-06-13-loan-account-type-design.md`)
y el motor de recordatorios SP-1 (`2026-06-13-sp1-payment-reminders-design.md`).

## Problema / valor

El usuario tiene 5 préstamos reales (~$650M+ de deuda entre Bancolombia, Santander y
AV Villas) y hoy la app no los ve: `GET /api/credits` devuelve `emptyList()` hardcodeado
(`FinanceRoutes.kt`) desde que SP-0 eliminó los JSON stores sin reemplazo, y
`CreditosScreen` está permanentemente vacía. El modelo legacy `Credit` (`Finance.kt`) es
de display (tasa y cuota como strings) y no tiene persistencia ni integración.

Capacidades elegidas por el usuario (todas), entregadas por fases:
- **F1 (este spec):** ver saldo/progreso por crédito y deuda consolidada.
- **F2 (este spec):** las cuotas alimentan próximos pagos y los emails de recordatorio.
- **F3 (futuro):** pagos importados de extractos/SMS bajan la deuda solos.
- **F4 (futuro):** simulador de abonos/refinanciación.

## Decisiones (locked)

- **Enfoque A — créditos sobre cuentas `LOAN` + tabla de términos.** El préstamo ya es
  una cuenta `LOAN` cuyo saldo (deuda) se deriva de eventos. Los términos contractuales
  van en una tabla 1:1 aparte. **Una sola fuente de verdad para la deuda**: los términos
  nunca almacenan saldo actual. Esto deja F3 trivial (un pago es un INCOME a la cuenta)
  y F4 puro (amortización sobre términos).
- **Regla virtual, no regla duplicada (F2):** las cuotas entran al motor de recordatorios
  como `RecurringRule` sintéticas derivadas de los términos en tiempo de lectura/sweep.
  Sin sincronización de lifecycle: editar la cuota cambia el recordatorio solo.
- **Solo cuentas `LOAN`, solo COP** en v1 (coherente con el spec de `LOAN`; las tarjetas
  ya tienen su flujo de extractos y su semántica de deuda propia).
- **El modelo legacy `Credit` se elimina** y se reemplaza por `CreditTerms` (escritura) y
  `CreditSummary` (lectura, derivado server-side).
- **YAGNI v1:** sin amortización proyectada, sin auto-aplicación de pagos, sin historial
  de tasa, sin múltiples cuotas por mes, sin moneda extranjera.

## Diseño

### A — Modelo de datos

Tabla `credit_terms` (Postgres, `server/.../db/Tables.kt`, registrar en
`DatabaseFactory.init()`):

```kotlin
object CreditTerms : Table("credit_terms") {
    val accountId          = varchar("account_id", 50)   // 1:1 con cuenta LOAN
    val userId             = varchar("user_id", 50)
    val bank               = varchar("bank", 80)
    val principal          = long("principal")            // capital original (COP)
    val rateEa             = double("rate_ea")            // % EA, p.ej. 17.46
    val termMonths         = integer("term_months")
    val installment        = long("installment")          // cuota mensual total (incl. seguros)
    val dayOfMonth         = integer("day_of_month")      // día de pago
    val startDate          = varchar("start_date", 10)    // ISO desembolso "2026-06-01"
    val notes              = varchar("notes", 300).nullable()
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable() // server-only, "YYYY-MM"
    override val primaryKey = PrimaryKey(accountId)
    init { index("idx_credit_terms_user_id", false, userId) }
}
```

Modelos wire (`core/.../model/Finance.kt` — reemplazan a `Credit`):

```kotlin
@Serializable data class CreditTerms(
    val accountId: String,
    val bank: String,
    val principal: Long,
    val rateEa: Double,
    val termMonths: Int,
    val installment: Long,
    val dayOfMonth: Int,
    val startDate: String,       // ISO
    val notes: String? = null,
)

@Serializable data class CreditSummary(
    val account: Account,        // cuenta LOAN con deuda derivada (balance)
    val terms: CreditTerms?,     // null si la cuenta LOAN aún no tiene términos
    val paidPct: Double?,        // 1 − deuda/principal; null sin términos o principal ≤ 0
)
```

`paidPct` se calcula server-side con la deuda derivada de eventos (la misma que pinta
DEUDA ACTUAL) — se clampa a `[0, 1]` (abonos extra pueden dejar deuda < cuota teórica;
deuda > principal por mora/intereses capitalizados se muestra como 0%).

### B — API (`server/.../routes/CreditRoutes.kt`, registrar en `Routing.kt`)

Todo por `call.userId()`:

- `GET /api/credits` → `List<CreditSummary>`: todas las cuentas `LOAN` del usuario
  (con balances derivados, reusa la misma derivación de `AccountRoutes`) + sus términos
  si existen. Una cuenta LOAN sin términos aparece con `terms = null` (la UI invita a
  completarlos).
- `PUT /api/credits/{accountId}` → upsert de `CreditTerms`. Valida: la cuenta existe,
  pertenece al usuario y es tipo `LOAN` (si no → 404 / 422). `dayOfMonth` se coerce a
  1..31 (el motor de fechas ya clampa al largo del mes). Un upsert de términos **no
  resetea** `lastRemindedPeriod` (simplificación v1): si este mes ya se recordó la cuota
  y el usuario cambia `dayOfMonth`, el nuevo día aplica desde el mes siguiente.
- `DELETE /api/credits/{accountId}` → borra los términos (la cuenta LOAN queda intacta).

Se elimina el stub `get("/api/credits")` de `FinanceRoutes.kt`. De paso se **mueve** el
`GET /api/recurring-rules` de `FinanceRoutes.kt` a `ReminderRoutes.kt` (hoy las
mutaciones viven allá y el GET acá, partido en dos archivos) — higiene directamente
relacionada porque este spec toca ambos archivos de rutas.

Repo (`:core` `WalletRepository` + impls):
`getCredits(): List<CreditSummary>` (reemplaza el stub actual que devuelve
`List<Credit>`), `putCreditTerms(terms: CreditTerms): CreditSummary`,
`deleteCreditTerms(accountId: String)`.

### C — Integración con recordatorios (F2)

Función pura en `server/.../reminders/` (junto a `DueDates.kt`):

```kotlin
fun virtualRuleFor(terms: CreditTerms, accountName: String): RecurringRule =
    RecurringRule(
        id = "credit_${terms.accountId}",
        name = "Cuota $accountName",
        category = "Créditos",
        amount = terms.installment,
        dayOfMonth = terms.dayOfMonth,
        type = TransactionType.EXPENSE,
    )
```

- **`GET /api/payments/upcoming`** (`ReminderRoutes.kt`): une reglas reales + virtuales
  de los créditos del usuario y pasa todo por `upcomingPayments(...)` (el motor
  `DueDates` no cambia). El cliente recibe `UpcomingPayment` normales; el prefijo de id
  `credit_` permite a la UI distinguirlas si algún día hace falta (v1 no lo necesita).
- **Sweep de emails** (`ReminderScheduler`): además de las `recurring_rules`, selecciona
  términos de crédito vencidos/próximos con `selectDueForReminder` (mismas funciones
  puras, pares `virtualRule to lastRemindedPeriod`), los incluye en el mismo email
  agrupado, y sella `credit_terms.lastRemindedPeriod` al enviar.
- Las reglas virtuales **no** aparecen en `GET /api/recurring-rules` ni son editables
  por las rutas de reglas (id `credit_*` no existe en esa tabla; editar la cuota se hace
  editando los términos del crédito).
- Convivencia: si el usuario ya creó a mano una regla para la misma cuota (p.ej. "Cuota
  AV Villas" de la siembra de junio), recibirá ambas. Al sembrar términos, parte de la
  verificación manual es borrar las reglas manuales que queden redundantes.

### D — UI (`shared/.../ui/credits/CreditosScreen.kt` + sheet)

Estilo Movi (`MinCard`, tema `Min*`):

- **Header:** deuda total de créditos (suma de balances LOAN) + cantidad.
- **Lista:** tarjeta por crédito — nombre, banco, **deuda actual** (grande, estilo
  deuda), barra de progreso `paidPct`, tasa EA, "Cuota $X · día N". Cuenta LOAN sin
  términos → tarjeta con CTA "Completar términos".
- **"Agregar crédito"** → sheet en dos partes: (1) selector de cuenta LOAN existente
  sin términos **o** "Nueva cuenta" (nombre + deuda actual → reusa `POST /api/accounts`
  con tipo `LOAN`, igual que `CreateAccountSheet`); (2) campos de términos: banco,
  capital, tasa EA, plazo (meses), cuota, día de pago, fecha de desembolso, notas.
  Al guardar: crea la cuenta si aplica → `PUT /api/credits/{accountId}`.
- **Tap en un crédito** → mismo sheet en modo edición + acción "Eliminar términos"
  (`DELETE`). La cuenta se maneja donde siempre (Mis cuentas).
- Las cuotas aparecen en `RecurrentesScreen`/próximos pagos sin cambio de UI.

### E — Datos reales (siembra)

Manual por la UI al verificar (5 créditos; misma filosofía que el spec de `LOAN`).
Datos base en `server/movi-data/credits.json` (huérfano, gitignored), **actualizando
saldos al corte del día** y verificando el estado del crédito AV Villas: si la compra de
cartera con Bancolombia ($257M / 72m / cuota $6.040.259) ya se ejecutó, se siembra el
crédito nuevo de Bancolombia en su lugar. Tras sembrar términos, borrar las
`recurring_rules` manuales que dupliquen cuotas.

## Fuera de alcance (futuro)

- **F3:** matcher en el pipeline de import/SMS que reconozca pagos de cuota y los
  aplique como INCOME a la cuenta LOAN.
- **F4:** motor puro de amortización (proyección, ahorro por abono, comparador de
  refinanciación).
- Términos para `CREDIT_CARD`, moneda extranjera, cadencias no mensuales, historial de
  tasas, edición/borrado de cuentas (sigue sin existir en general).

## Testing

- **Unit (`:server`):**
  - `CreditSummary`: `paidPct` con deuda derivada (parcial, 0%, clamp en abonos extra y
    en deuda > principal), `terms = null`.
  - `virtualRuleFor`: mapeo de campos; `selectDueForReminder` con mezcla de reglas
    reales y virtuales (la cuota entra al sweep, dedupe por `lastRemindedPeriod` de
    `credit_terms`, una regla real con el mismo nombre NO deduplica a la virtual —
    conviven por diseño).
- **HTTP (harness H2):** `GET/PUT/DELETE /api/credits` por usuario; B no ve/edita los
  términos de A; upsert idempotente; `PUT` sobre cuenta ajena, inexistente o no-LOAN →
  4xx; `GET /api/payments/upcoming` incluye la cuota tras el upsert.
- **Compile:** `:shared` compila; `:server:test` verde.
- **Manual (web local):** sembrar los 5 créditos reales → CreditosScreen muestra deuda
  total y progreso; la cuota aparece en próximos pagos; con `RESEND_API_KEY` local, el
  sweep incluye la cuota en el email.
