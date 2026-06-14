# SP-1 · Monthly payment reminders — design

**Fecha:** 2026-06-13
**Parte de:** arco production-ready de movi (SP-0 ✅ → **SP-1** → SP-2).
**Alcance:** `:core` (modelo + repo), `:server` (CRUD + motor de vencimientos + scheduler de
email), `:shared` (pantalla Próximos pagos + sheet de alta/edición). Depende de SP-0
(tabla `recurring_rules` por usuario, aislamiento).

## Contexto

El usuario pidió que la app le **recuerde los pagos mensuales** que tiene que hacer
(arriendo, suscripciones, y cuotas de créditos/tarjetas). Hoy existe `RecurringRule`
(`id, name, category, amount, dayOfMonth, type`) y una tabla `recurring_rules` por usuario
(creada en SP-0) **vacía y sin CRUD** — `RecurrentesScreen` la lista pero su botón "+" no
hace nada. No hay cómputo de vencimientos ni notificaciones.

## Decisiones (locked)

- **Fuente única de pagos = `RecurringRule`.** Un pago de cuota (vehículo, TC, libranza) se
  modela como una regla recurrente de tipo `EXPENSE` (`name="Cuota Crédito Vehículo"`,
  `amount`, `dayOfMonth`). **No** se agregan campos de fecha/cuota al modelo `Account`
  (YAGNI; las cuentas LOAN/CREDIT_CARD siguen siendo para el saldo). Derivar pagos desde las
  cuentas de deuda se puede revisitar después.
- **Vencimiento mensual desde `dayOfMonth`:** la fecha de vencimiento del mes en curso es
  `min(dayOfMonth, díasDelMes)`. Estados: `OVERDUE` (venció antes de hoy), `DUE_TODAY`,
  `DUE_SOON` (dentro de `leadDays`), `UPCOMING`.
- **Sin tracking de "pagado" en v1.** El recordatorio se basa en proximidad de fecha; la
  vista muestra todos los pagos del mes con su estado. (Marcar como pagado / conciliar con
  eventos reales = futuro.)
- **Canal de recordatorio = email** (elegido por el usuario). Provider **Resend** (HTTP API
  simple). Scheduler in-process (Railway single-instance). Degradación elegante: sin
  `RESEND_API_KEY`, el scheduler no arranca (warn en log), el resto funciona.
- **Dedupe de email:** columna server-only `last_reminded_period` (varchar "YYYY-MM",
  nullable) en `recurring_rules`, fuera del modelo wire. Un recordatorio por regla por mes.

## Diseño

### A — Modelo (`:core`)

`RecurringRule` sin cambios (wire). Nuevo DTO computado para la vista:

```kotlin
@Serializable
enum class PaymentStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

@Serializable
data class UpcomingPayment(
    val rule: RecurringRule,
    val dueDate: String,      // "2026-06-05" (mes en curso)
    val daysUntil: Int,       // negativo si OVERDUE
    val status: PaymentStatus,
)
```

Repo (`WalletRepository` + impls): agregar
`createRecurringRule`, `updateRecurringRule(id)`, `deleteRecurringRule(id)`,
`getUpcomingPayments(): List<UpcomingPayment>`. (`getRecurringRules` ya existe.)

### B — Persistencia (`:server`)

`recurring_rules` (de SP-0) gana una columna **server-only** `last_reminded_period`
varchar(7) nullable (no se serializa en `RecurringRule`). `createMissingTablesAndColumns`
la agrega.

### C — Motor de vencimientos (`server/.../reminders/DueDates.kt`, puro/testeable)

```kotlin
fun dueDateFor(rule: RecurringRule, today: LocalDate): LocalDate   // min(dayOfMonth, lengthOfMonth) del mes de `today`
fun statusFor(dueDate: LocalDate, today: LocalDate, leadDays: Int): PaymentStatus
fun upcomingPayments(rules: List<RecurringRule>, today: LocalDate, leadDays: Int): List<UpcomingPayment>  // ordenado por dueDate
```

### D — Rutas (`server/.../routes/ReminderRoutes.kt`, nuevas; registradas en `Routing.kt`)

Todas por usuario (`call.userId()`), patrón Budgets:
- `POST /api/recurring-rules` — crea (genera `id`), inserta con `userId`.
- `PUT /api/recurring-rules/{id}` — actualiza (filtra por id + userId).
- `DELETE /api/recurring-rules/{id}` — borra (filtra por id + userId).
- `GET /api/payments/upcoming` — lee las reglas del usuario, corre `upcomingPayments(...)`
  con `leadDays` (env `REMINDER_LEAD_DAYS`, default 3) y `LocalDate.now(UTC)`.

`GET /api/recurring-rules` (de SP-0) queda para la lista cruda.

### E — Scheduler de email (`server/.../reminders/ReminderScheduler.kt` + `ResendClient.kt`)

- Arranca en `Application.module()` **solo si** `RESEND_API_KEY` está seteada.
- Un coroutine que cada `REMINDER_SWEEP_HOURS` (default 12) hace un sweep, y uno al boot.
- Sweep: por cada usuario, toma sus reglas `EXPENSE` cuyo vencimiento del mes está dentro de
  `leadDays` o ya vencido **y** `last_reminded_period != mesActual`; las agrupa en **un** email
  por usuario (asunto "Pagos próximos en movi", cuerpo con lista: nombre, monto, "vence el N
  (en X días / hoy / vencido hace X)"). Tras enviar OK, sella `last_reminded_period = mesActual`
  en cada regla incluida.
- `ResendClient`: POST `https://api.resend.com/emails` con `Authorization: Bearer
  $RESEND_API_KEY`, `from = REMINDER_FROM` (env, p.ej. "movi <reminders@tudominio>"),
  `to = user.email`. Reusa el `HttpClient` (CIO/OkHttp) o java.net.http.
- El email del usuario sale de la tabla `Users` (ya existe).

### F — UI (`:shared`)

Reemplazar/enriquecer `RecurrentesScreen` como **Próximos pagos**:
- Carga `getUpcomingPayments()`; lista ordenada por `dueDate` con badge de estado
  (OVERDUE rojo, DUE_TODAY/DUE_SOON ámbar, UPCOMING neutro) y texto "vence el N · en X días /
  hoy / vencido hace X".
- FAB "+" abre un **sheet de alta** (estilo `CreateAccountSheet`): nombre, monto, día del mes
  (1–31), tipo (EXPENSE por defecto; INCOME opcional para ingresos fijos), categoría.
  `createRecurringRule(...)`.
- Tap en un ítem → editar/borrar (`updateRecurringRule`/`deleteRecurringRule`).

### Config / env nuevos

`RESEND_API_KEY` (sin él, no hay email), `REMINDER_FROM`, `REMINDER_LEAD_DAYS` (def 3),
`REMINDER_SWEEP_HOURS` (def 12). Documentar en `.env.example` si existe; nunca commitear `.env`.

## Testing

- **Unit (`:server`):** `DueDates` — clamping de día (ej. dayOfMonth=31 en febrero → 28/29),
  estados (overdue/today/soon/upcoming) por proximidad, orden por fecha. Selección de sweep:
  qué reglas entran (dentro de lead / vencidas / no notificadas) y dedupe por
  `last_reminded_period`. `ResendClient` y el envío real **no** se llaman en tests (se testea
  la lógica de selección, no la red).
- **Isolation (HTTP, H2):** A no puede editar/borrar la regla de B; `POST/PUT/DELETE` por
  usuario; `GET /api/payments/upcoming` solo del usuario.
- **Compile:** `:shared` compila; `:server:test` verde.

## Fuera de alcance (futuro)

- Marcar pagos como pagados / conciliar con eventos reales.
- Derivar cuotas automáticamente desde cuentas LOAN/CREDIT_CARD (due-date en `Account`).
- Notificaciones push/local (este SP usa email; otros canales se suman como capa fina).
- Reglas no mensuales (semanal/anual), recordatorios multi-lead.
