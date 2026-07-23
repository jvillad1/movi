# Web Push para recordatorios de pago — diseño

**Fecha:** 2026-07-23
**Alcance:** `:server` (tabla + rutas + sender + scheduler), `webApp` (service worker
solo-push + helper JS + index.html), `:shared` (toggle en Perfil vía expect/actual).
`:core` NO cambia (el flujo de suscripción vive en JS con el token de localStorage).

## Problema / valor

Los recordatorios de pago solo existen por email (Resend, aún sin key en prod). El usuario
quiere push nativas en su teléfono. Con la PWA instalada (PR #17), **Web Push llega hoy**:
Android/Chrome pleno; iPhone desde iOS 16.4 con la app en el home screen. Además, con el
sello mensual desacoplado del email, push funciona en prod **sin esperar la key de Resend**.

## Decisiones (locked)

- **Canal: Web Push a la PWA** (elegido). FCM nativo queda para un ciclo futuro.
- **Contenido: pagos próximos** (cuotas de créditos + reglas recurrentes del sweep
  existente). Suscripciones-detectadas queda fuera de v1.
- **Enfoque A: librería `nl.martijndwars:web-push`** (+ BouncyCastle transitivo) vía
  `gradle/libs.versions.toml` — payload cifrado RFC 8291 con contenido real. Nada de
  crypto artesanal en una app financiera. (El implementador verifica la última versión
  estable en Maven Central; 5.1.1 como referencia.)
- **Service worker SOLO-push** (`push-sw.js`): handlers `push` y `notificationclick`
  únicamente. SIN handler `fetch`, SIN cache — no reintroduce el riesgo de bundle viejo
  que la PWA evitó a propósito.
- **Claves VAPID por env** (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, base64url; opcional
  `VAPID_SUBJECT`, default `mailto:jvillad1@gmail.com`). Sin claves → push deshabilitado
  con warning al boot (mismo patrón que `RESEND_API_KEY`). Script de generación
  committeado: `scripts/generate-vapid-keys.sh` (openssl + python3, ambos en macOS).
- **Sello mensual por CUALQUIER canal:** `lastRemindedPeriod` se sella si el email O el
  push se entregaron a ese usuario. Si ambos canales fallan (o ninguno está configurado),
  no se sella y el próximo sweep reintenta — comportamiento actual preservado cuando solo
  hay email.
- **Multi-dispositivo:** varias suscripciones por usuario. Respuestas 404/410 del push
  service ⇒ borrar la suscripción muerta (dispositivo des-registrado).
- **El flujo de opt-in vive en JS** (`push.js`): permiso → registro del SW →
  `pushManager.subscribe` → `POST /api/push/subscribe` con el Bearer de
  `localStorage['auth_token']` (mismo storage que ya usa el overlay de login). Compose
  solo invoca y refleja estado vía interop wasmJs. Android/iOS: actual no-soportado (la
  fila del toggle no se muestra).

## Diseño

### A — Tabla (`server/.../db/Tables.kt`, registrar en `DatabaseFactory`)

```kotlin
object PushSubscriptions : Table("push_subscriptions") {
    val endpoint  = varchar("endpoint", 500)   // PK: único por dispositivo/navegador
    val userId    = varchar("user_id", 50)
    val p256dh    = varchar("p256dh", 200)     // clave pública del cliente (base64url)
    val auth      = varchar("auth", 50)        // auth secret (base64url)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(endpoint)
    init { index("idx_push_subscriptions_user_id", false, userId) }
}
```

### B — Wire model (`core` NO cambia; el request es JSON plano)

`POST /api/push/subscribe` recibe `{"endpoint": "...", "p256dh": "...", "auth": "..."}` —
se modela con una `@Serializable private data class` LOCAL del archivo de rutas del server
(no es un modelo compartido con clientes Kotlin; el único consumidor es push.js).

### C — Rutas (`server/.../routes/PushRoutes.kt`, registrar en `Routing.kt`)

- `GET /api/push/vapid-key` — **pública** (fuera de `authenticate`): `{"key": "<VAPID_PUBLIC_KEY>"}`;
  404 si push no está configurado.
- `POST /api/push/subscribe` (auth): upsert por endpoint (re-suscribir actualiza userId y
  claves — un endpoint pertenece al último usuario que lo registró en este navegador).
- `DELETE /api/push/subscribe` (auth): body `{"endpoint": "..."}`; borra solo si
  `userId` coincide; 404 si no existe o es ajeno.

### D — Sender (`server/.../push/WebPushSender.kt`)

- Config por env con el patrón `readEnv` existente (env → `server/.env` → `.env`).
- `fun isConfigured(): Boolean`.
- `suspend fun sendToUser(uid: String, payloadJson: String): Boolean` — carga las
  suscripciones del usuario, envía a cada una con la librería (VAPID + aes128gcm);
  **204/201 = entregada**; 404/410 ⇒ `deleteWhere(endpoint)`; otros errores se loguean.
  Devuelve `true` si AL MENOS UNA entrega tuvo éxito.
- Payload builder **puro** (`fun buildPushPayload(selected: List<RecurringRule>, today: LocalDate, leadDays: Int): String`):
  JSON `{"title": "Pagos próximos en movi", "body": "<hasta 3 líneas 'Nombre — $monto (estado)'; si hay más: '…y N más'>", "url": "/"}`.
  Reusa `dueDateFor`/`statusFor` para el estado en texto (mismo copy del email: "vence
  hoy", "vence en N días", "vencido hace N días").

### E — Scheduler (`ReminderScheduler.processUser`)

```
selected = selectDueForReminder(pares reales + crédito)   // sin cambios
emailSent = si RESEND configurado → enviar email (como hoy)
pushSent  = si WebPushSender.isConfigured() → sendToUser(uid, buildPushPayload(selected, today, leadDays))
if (emailSent || pushSent) → sellar lastRemindedPeriod (lógica de sellado existente, sin cambios)
```

El arranque del scheduler deja de abortar si falta `RESEND_API_KEY`: ahora corre si
**cualquiera** de los dos canales está configurado (warning por cada canal ausente;
return solo si no hay ninguno).

### F — Cliente web (`webApp/src/wasmJsMain/resources/`)

- **`push-sw.js`** — `push` event: `self.registration.showNotification(data.title, {body, icon: 'icons/movi-192.png', badge: 'icons/movi-192.png', data: {url}})`;
  `notificationclick`: focus de una ventana existente o `clients.openWindow(url)`. Nada más.
- **`push.js`** — `window.moviPush = { supported(), status(), enable(), disable() }`:
  - `supported()`: `'serviceWorker' in navigator && 'PushManager' in window && !!localStorage.getItem('auth_token')`.
  - `status()`: `"enabled" | "disabled" | "denied" | "unsupported"` (consulta
    `Notification.permission` + `getSubscription()`; cachea el último resultado en una
    variable síncrona que el interop pueda leer, refrescada por `enable/disable/init`).
  - `enable()`: requestPermission → `navigator.serviceWorker.register('push-sw.js')` →
    `pushManager.subscribe({userVisibleOnly: true, applicationServerKey: <de GET /api/push/vapid-key, convertida base64url→Uint8Array>})`
    → `POST /api/push/subscribe` con Bearer. `disable()`: `getSubscription().unsubscribe()` +
    `DELETE /api/push/subscribe`.
- **`index.html`** — `<script src="push.js"></script>` antes de `composeApp.js`.

### G — Toggle en Perfil (`:shared`)

`expect object PushOptIn { val supported: Boolean; fun status(): String; fun enable(); fun disable() }`
en `shared/.../platform/PushOptIn.kt`; actual wasmJs = externals a `window.moviPush`
(`@JsFun` o `external`); actuals android/iOS = `supported=false`, no-ops. `PerfilScreen`:
fila "Notificaciones push" visible solo si `supported`, con estado según `status()` y
tap → enable/disable + refresco (polling corto tras enable, porque el flujo JS es async).

## Testing

- **Config testeable (locked):** la resolución de claves VAPID consulta PRIMERO las
  system properties `movi.vapid.public`/`movi.vapid.private`/`movi.vapid.subject` y luego
  el `readEnv` existente — los tests HTTP setean/limpian las properties en
  `@BeforeTest`/`@AfterTest` sin tocar env ni archivos.
- **HTTP (harness H2, patrón CreditRoutesTest):** vapid-key pública (200 con key seteada
  por property / 404 con properties limpias); subscribe upsert idempotente por endpoint;
  re-subscribe cambia de usuario; DELETE ajeno → 404; DELETE de B no borra la de A.
- **Unit:** `buildPushPayload` (0/1/3/5 pagos → título, líneas, "…y N más", montos con
  formato); decisión de sellado (`emailSent || pushSent`) si se extrae como función pura.
- **Manual post-deploy:** en el teléfono con la PWA instalada: Perfil → activar push →
  cuota con vencimiento próximo → llega la notificación; tap abre la app.

## Fuera de alcance (futuro)

FCM/APNs nativos; push de suscripciones-detectadas; preferencias por tipo de
notificación; quiet hours; re-intento con backoff por suscripción.
