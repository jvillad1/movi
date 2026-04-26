# Monedero — Core Design Spec
**Date:** 2026-04-26
**Sub-project:** 1 of 5 — Core (foundation)
**Status:** Approved

---

## Overview

Monedero es una app de gestión de finanzas personales y familiares. Este documento cubre el sub-proyecto 1 (Core), que es la fundación sobre la que se construyen los demás sub-proyectos.

**Sub-proyectos planificados:**
1. **Core** ← este documento
2. Ingesta automática (SMS + OCR + Extractos)
3. Analytics y proyecciones
4. IA — recomendaciones y Q&A
5. Reportes compartibles

---

## Plataformas

- **Android** — primera plataforma, features completos
- **iOS** — paridad progresiva (sin SMS, resto igual)
- **Web** — dashboard via `composeApp` wasmJs target

Algunos features son Android-only inicialmente (lectura de SMS, biométrico). iOS y Web se equiparan en fases posteriores.

---

## Decisiones de Arquitectura

### Event Sourcing — Append-Only Log

Cada movimiento financiero es un **evento inmutable**. Nunca se editan ni borran datos — solo se agregan. Si hay un error, se crea un `VoidEvent` que anula el evento original.

Beneficios para finanzas personales:
- Historial completo e inalterable
- Auditoría natural (quién registró qué y cuándo)
- Sincronización familiar sin conflictos
- Base natural para proyecciones y reconciliación

### Offline-First + Sync Continuo

```
Dispositivo (SQLDelight/SQLite) ←→ Servidor Ktor (PostgreSQL)
```

- El dispositivo es la fuente de respuesta inmediata (UX instantáneo)
- Cuando hay conexión, sync continuo en background (WorkManager)
- El servidor siempre tiene la copia completa de todos los eventos
- `syncedAt = null` indica eventos pendientes de sync

### Stack técnico

```
:shared         → modelos, domain, repository interfaces, SyncEngine
:composeApp     → UI Compose (Android + iOS + Web), SQLDelight, WorkManager
:server         → Ktor + Exposed + PostgreSQL, Claude API
```

**SQLDelight** en vez de Room — genera código para Android, iOS y JVM desde el mismo esquema SQL.

---

## Modelo de Dominio

### User
```kotlin
User(
  id: UUID,
  email: String,
  name: String,
  passwordHash: String,
  familyId: UUID?,       // null si no pertenece a un grupo familiar
  createdAt: Long,
)
```

### Family
```kotlin
Family(
  id: UUID,
  name: String,          // ej: "Familia Villada"
  members: List<UserId>,
  createdAt: Long,
)
```

### Account
```kotlin
Account(
  id: UUID,
  userId: UUID,
  name: String,
  type: CASH | CHECKING | SAVINGS | CREDIT_CARD | INVESTMENT,
  currency: String,      // "COP" por defecto
  creditObligationId: UUID?,  // si type == CREDIT_CARD
  investmentId: UUID?,        // si type == INVESTMENT
  createdAt: Long,
)
```

### FinancialEvent
```kotlin
FinancialEvent(
  id: UUID,              // generado en el dispositivo
  userId: UUID,
  familyId: UUID?,
  accountId: UUID,
  type: INCOME | EXPENSE,
  amount: Long,          // en centavos para evitar flotantes
  currency: String,
  categoryId: UUID,
  description: String,
  merchant: String?,     // comercio separado de descripción
  timestamp: Long,
  source: MANUAL | SMS | OCR | STATEMENT,
  rawPayload: String?,   // SMS original, texto OCR o referencia al extracto
  visibility: PERSONAL | FAMILY,
  recurrenceId: UUID?,   // apunta a RecurringRule
  reconciliationStatus: UNCONFIRMED | RECONCILED | UNMATCHED,
  statementRef: String?, // referencia al StatementEntry que lo confirmó
  syncedAt: Long?,       // null = pendiente de sync
  createdAt: Long,
)
```

### VoidEvent
```kotlin
VoidEvent(
  id: UUID,
  originalEventId: UUID, // evento que se anula
  userId: UUID,
  reason: String?,
  timestamp: Long,
  syncedAt: Long?,
)
```

### Category
```kotlin
Category(
  id: UUID,
  name: String,
  icon: String,
  color: String,
  type: INCOME | EXPENSE | BOTH,
  scope: PREDEFINED | CUSTOM,
  userId: UUID?,         // null si es predefinida del sistema
  familyId: UUID?,
)
```

**Categorías predefinidas incluidas:**
- Gastos: Comida, Transporte, Salud, Educación, Entretenimiento, Servicios, Vivienda, Ropa, Tecnología, Otros
- Ingresos: Salario, Freelance, Arriendo, Inversiones, Otros

### RecurringRule
```kotlin
RecurringRule(
  id: UUID,
  userId: UUID,
  description: String,
  amount: Long,
  type: INCOME | EXPENSE,
  categoryId: UUID,
  accountId: UUID,
  frequency: DAILY | WEEKLY | MONTHLY | YEARLY,
  dayOfMonth: Int?,
  startDate: Long,
  endDate: Long?,
  isActive: Boolean,
)
```

### Investment
```kotlin
Investment(
  id: UUID,
  userId: UUID,
  name: String,
  categoryId: UUID,      // CDT, Acciones, Fondo, etc.
  initialAmount: Long,
  currentValue: Long,    // actualizado periódicamente por el usuario
  currency: String,
  startDate: Long,
  rate: Double?,         // tasa pactada si aplica (ej: CDT 12% EA)
  notes: String?,
  // Calculado automáticamente:
  // tasaDeRetornoEA = ((currentValue/initialAmount)^(365/días) - 1) × 100
)

InvestmentSnapshot(
  id: UUID,
  investmentId: UUID,
  value: Long,
  timestamp: Long,
)
```

### CreditObligation
```kotlin
CreditObligation(
  id: UUID,
  userId: UUID,
  name: String,
  originalAmount: Long,
  TEA: Double,           // Tasa Efectiva Anual
  termMonths: Int,
  startDate: Long,
  paymentDay: Int,       // día del mes
  remainingBalance: Long,
  // Calculado automáticamente:
  // cuotaMensual = amortización francesa sobre TEA y plazo
  // tablaAmortizacion generada en servidor
)
```

### SavingsGoal
```kotlin
SavingsGoal(
  id: UUID,
  userId: UUID,
  familyId: UUID?,
  name: String,
  targetAmount: Long,
  targetDate: Long,
  linkedAccountId: UUID?,
  currentAmount: Long,   // proyectado automáticamente
)
```

### Budget
```kotlin
Budget(
  id: UUID,
  categoryId: UUID,
  amount: Long,
  period: MONTHLY | YEARLY,
  userId: UUID?,
  familyId: UUID?,
)
```

### StatementEntry
```kotlin
StatementEntry(
  id: UUID,
  statementId: UUID,
  date: Long,
  amount: Long,
  type: INCOME | EXPENSE,
  description: String,
  balance: Long?,
  bankName: String,
  accountLast4: String,
)
```

---

## Ingesta de Datos

### Jerarquía de confianza
```
1. Extracto bancario (STATEMENT)  → RECONCILED automáticamente
2. SMS bancario                   → UNCONFIRMED hasta reconciliar
3. OCR / imagen                   → UNCONFIRMED hasta reconciliar
4. Manual                         → UNCONFIRMED hasta reconciliar
```

### A) SMS — cualquier banco
- Usuario configura remitentes a monitorear (ej: "Bancolombia", "Nequi")
- **BroadcastReceiver** escucha SMS nuevos en tiempo real
- **Modo histórico**: importa inbox una sola vez
- Parsing: regex-first (aprende el patrón del banco) → Claude Haiku como fallback para formatos nuevos
- El patrón aprendido se guarda en servidor → próximos SMS del mismo banco: cero costo de IA

### B) Manual
- Formulario: Monto | Tipo | Categoría | Descripción | Fecha | Cuenta
- `source = MANUAL`, `reconciliationStatus = UNCONFIRMED`

### C) Imágenes — tres formas de entrada
- **Cámara in-app** — foto de recibo
- **Galería** — imagen existente
- **Share Sheet** — screenshot desde app del banco o WhatsApp
- Pipeline: ML Kit (OCR on-device, gratis) → texto extraído → Claude Haiku (servidor) → campos estructurados
- Usuario confirma antes de guardar
- `rawPayload` = texto del OCR, `imageRef` = path local

### D) Extractos bancarios — fuente de verdad
- Formatos: PDF, CSV, XLS
- CSV/XLS: parser directo, sin IA
- PDF: Claude Haiku en servidor
- **Reconciliación automática:**
  ```
  ✅ RECONCILED  — evento ya existe, monto + fecha coinciden (±1 día)
  ⚠️ UNMATCHED   — en extracto pero no en Monedero → se importa automáticamente
  🔍 UNCONFIRMED — en Monedero pero no en extracto → posible duplicado o error
  ```
- El usuario aprueba el informe de reconciliación antes de confirmar cambios

---

## Optimización de Costos Claude API

| Tarea | Modelo | Estrategia |
|-------|--------|-----------|
| Extracción SMS/OCR | Haiku | Regex-first; Claude solo para formatos nuevos |
| Parsing PDF extractos | Haiku | Sin alternativa, pero PDFs son infrecuentes |
| Recomendaciones | Sonnet | Máximo 2-3 por semana por usuario |
| Q&A | Sonnet | On-demand, contexto agregado (no datos crudos) |

**Prompt caching:** el system prompt de extracción se cachea en servidor (10x más barato).
**Rate limiting:** tope configurable de llamadas diarias/mensuales por usuario.
**Privacidad:** Claude nunca recibe nombres de comercios ni descripciones sensibles — solo montos, categorías y patrones agregados.

---

## Analytics y Proyecciones

### Vistas disponibles

```
Individual                    Familiar
──────────────────────────    ────────────────────────────
Saldo actual por cuenta       Saldo combinado familia
Gastos del mes por categoría  Gastos por persona
Ingresos del mes              Contribución % de cada uno
Por comercio (merchant)       Categorías compartidas
Flujo libre disponible        Metas familiares
```

### Proyecciones
```
Flujo libre = Ingresos - Egresos fijos - Cuotas crédito - Aportes metas
```
- Proyección mensual y anual basada en historial (promedio 3 meses) + RecurringRules
- Alertas: "Vas 73% del presupuesto de comida — día 15/30"

### Inversiones
- Tasa de retorno EA por inversión: `((currentValue/initialAmount)^(365/días) - 1) × 100`
- Vista por categoría (CDT, acciones, fondo)
- Histórico de valor via InvestmentSnapshots

### Créditos
- TEA y plazo restante por obligación
- Tabla de amortización generada en servidor
- Costo total de intereses pagados vs pendientes
- Próximas cuotas con alerta anticipada

### Presupuestos
- Progreso visual por categoría (mensual/anual)
- Alerta al 80% y al 100%

### Metas de ahorro
- Proyección: "A este ritmo llegas en X meses"
- Progreso visual

---

## IA — Recomendaciones y Q&A

### Recomendaciones proactivas (Claude Sonnet)
Generadas server-side cuando hay cambio significativo vs patrón histórico. Máximo 2-3/semana:
- Comparación de gastos vs meses anteriores
- Oportunidades de refinanciación de créditos
- Alertas de metas y vencimientos
- Cambios en flujo libre

### Q&A en lenguaje natural (Claude Sonnet)
```
"¿Puedo comprar un televisor de $2,500,000?"
"¿Cuánto he gastado en restaurantes este año?"
"¿En qué mes gasto más?"
"¿Cuánto me falta para mi meta de vacaciones?"
"¿Cuánto me cuesta el crédito del carro en total?"
```
El servidor construye un resumen financiero agregado del usuario y lo envía como contexto a Claude. No se envían datos sensibles crudos.

---

## Seguridad

- **Biométrico** (huella / Face ID) para abrir la app — Android Biometric API
- **PIN** como fallback
- **SQLCipher** — base de datos SQLite local encriptada en el dispositivo
- **JWT** para autenticación API server-side
- **HTTPS** obligatorio en todas las comunicaciones cliente-servidor

---

## Notificaciones Locales

Corren completamente on-device via WorkManager. Sin servidor, sin costo:

| Trigger | Mensaje |
|---------|---------|
| Cuota crédito en 3 días | "Cuota de [nombre] vence el [fecha] — $X" |
| Budget al 80% | "Llevas el 80% del presupuesto de [categoría]" |
| Budget al 100% | "Superaste el presupuesto de [categoría]" |
| Inversión vence en 7 días | "Tu [inversión] vence el [fecha]" |
| Meta al 50%, 75%, 100% | "Meta [nombre] al X% — vas bien" |
| Extracto sin subir (mensual) | "Sube tu extracto de [banco] para reconciliar" |

---

## Onboarding — Flujo Guiado (5 pasos)

```
Paso 1 → Crear cuenta + moneda base (COP)
Paso 2 → Agregar cuentas (bancos, efectivo, inversiones, créditos)
Paso 3 → Subir primer extracto para poblar historial
Paso 4 → Autorizar lectura de SMS bancarios (Android)
Paso 5 → Definir presupuestos base por categoría
```

Cada paso es opcional y saltable. El usuario puede completar el onboarding después desde Settings.

---

## Vistas / Navegación Principal

```
Bottom Nav:
├── Dashboard    → resumen financiero (individual / familiar toggle)
├── Movimientos  → lista de eventos con filtros y búsqueda
├── + (FAB)      → registro rápido (manual | foto | SMS)
├── Presupuestos → progreso por categoría
└── Más          → Inversiones, Créditos, Metas, Extractos, Settings
```

---

## Módulos Gradle — Cambios al Scaffolding Actual

```
:shared
  model/        → todas las entidades del dominio
  domain/       → use cases
  repository/   → interfaces
  sync/         → SyncEngine

:composeApp
  commonMain/   → UI + ViewModels
  androidMain/
    db/         → SQLDelight Android driver + SQLCipher
    sms/        → SmsReceiver, SmsParser, PatternCache
    sync/       → WorkManager sync jobs
    biometric/  → BiometricManager
  iosMain/
    db/         → SQLDelight iOS driver

:server
  routes/       → auth, events, sync, ai, statements
  db/           → Exposed + PostgreSQL
  ai/           → Claude API (extracción, recomendaciones, Q&A)
  parser/       → CSV/XLS/PDF statement parsers
  reconcile/    → ReconciliationEngine
```

---

## Sub-proyectos Pendientes (fuera de este spec)

| # | Sub-proyecto | Depende de |
|---|-------------|-----------|
| 2 | Ingesta automática (SMS + OCR + Extractos — detalles de implementación) | Core |
| 3 | Analytics y proyecciones — pantallas completas | Core + Ingesta |
| 4 | IA — recomendaciones + Q&A | Core + Analytics |
| 5 | Reportes compartibles | Core + Analytics |
