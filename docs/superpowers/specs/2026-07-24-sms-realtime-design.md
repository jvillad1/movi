# SMS en tiempo real — diseño

**Fecha:** 2026-07-24
**Alcance:** `androidApp` (BroadcastReceiver + Worker + manifest + dep WorkManager),
`:server` (hook de push en `/api/sms/sync`). `:core` y `:shared` UI sin cambios (la
pantalla SMS existente sigue siendo el lugar de confirmación).

## Problema / valor

Hoy los SMS del banco entran a movi solo cuando el usuario abre la pantalla SMS en
Android (pull-al-abrir de SP-2). El usuario (Android como teléfono principal) quiere el
loop completo sin abrir la app: llega el SMS de Bancolombia → movi lo captura al instante
→ le llega una Web Push "toca para confirmar".

## Decisiones (locked)

- **Enfoque A:** `BroadcastReceiver` de `RECEIVE_SMS` → filtro de remitentes bancarios →
  encola **WorkManager** (constraint red, backoff) → `POST /api/sms/sync` (endpoint
  existente, idempotente por id) → server intenta parseo regex de los NUEVOS y manda UNA
  Web Push agrupada (best-effort, gate VAPID, jamás falla el sync).
- **Privacidad:** SOLO se suben SMS que matcheen el filtro bancario
  (`BankSenderFilter`): remitente que contenga `85540`, `891333`, `87400` o cuerpo que
  contenga `Bancolombia` (case-insensitive). Los SMS personales nunca salen del
  teléfono. La lista es una constante editable en un solo archivo.
- **Confirmación manual siempre** (elegido): el regex no es confiable para auto-commit
  financiero. La push lleva a abrir la app (url "/", sin deep-link en v1).
- **Permisos:** `RECEIVE_SMS` se declara en el manifest; como `READ_SMS` ya está
  concedido y ambos pertenecen al grupo SMS, no hay prompt adicional. App sideloaded —
  la restricción de Google Play sobre permisos SMS no aplica (anotado).
- **Id del SMS:** el receiver construye el id determinístico
  `sms_rt_<hash SHA-256 hex truncado a 16 de (sender|timestamp|body)>` — re-entregas del
  broadcast o reintentos del Worker no duplican (dedupe server por id + este id estable).
  El formato `SmsMessage` que sube replica el del `SmsReader` actual (`time` legible,
  `bank`, `text`, `state` lo pisa el server, `det` vacío).
- **Auth del Worker:** JWT desde `SessionManager.token` (multiplatform-settings →
  SharedPreferences, accesible sin UI). Sin token (deslogueado) → el Worker descarta el
  trabajo con log (no retry infinito).
- **Worker HTTP:** cliente Ktor Android efímero (mismo engine que ya usa `:shared`
  androidMain) contra `apiBaseUrl` (constante existente de `Platform.android.kt` —
  exponerla o duplicarla mínimamente en androidApp; preferir referenciar la de `:shared`
  que androidApp ya tiene como dependencia). Retry: `Result.retry()` con backoff
  exponencial de WorkManager ante IOException/5xx; éxito o 4xx → `Result.success()`/
  `failure()` (un 401 no se reintenta).

## Diseño

### A — androidApp

1. **`SmsRealtimeReceiver`** (`androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsRealtimeReceiver.kt`):
   `BroadcastReceiver` registrado en el manifest para `android.provider.Telephony.SMS_RECEIVED`.
   `onReceive`: extrae mensajes con `Telephony.Sms.Intents.getMessagesFromIntent`,
   concatena cuerpos multiparte por remitente, aplica `BankSenderFilter.matches(sender, body)`;
   si matchea → `OneTimeWorkRequest<SmsSyncWorker>` con `Data` (sender, body, timestamp)
   y constraint `NetworkType.CONNECTED`, backoff exponencial 30s.
2. **`BankSenderFilter`** (mismo paquete, objeto puro): `matches(sender: String?, body: String): Boolean`
   con las constantes de la decisión de privacidad. Unit-testable sin Android.
3. **`SmsSyncWorker`** (`CoroutineWorker`): lee token; construye `SmsMessage(id = hash
   determinístico, time = timestamp formateado "yyyy-MM-dd HH:mm", bank = sender ?: "SMS",
   text = body, state = "new", det = "")`; POST `/api/sms/sync` con Bearer; mapea
   respuesta a success/retry/failure según la política locked.
4. **Manifest:** `<uses-permission android:name="android.permission.RECEIVE_SMS"/>` +
   `<receiver android:exported="true" android:permission="android.permission.BROADCAST_SMS">`
   con intent-filter `SMS_RECEIVED`.
5. **Dep nueva:** `androidx.work:work-runtime-ktx` (versión estable actual; vía catálogo).

### B — Server: hook de push en sync

En `POST /api/sms/sync` (`SmsRoutes.kt`): el loop de inserción ya sabe cuáles mensajes
son NUEVOS — recolectarlos. Tras responder... no: ANTES de responder (mismo patrón que
detect-on-import), si hay nuevos y `WebPushSender.isConfigured()`:

```kotlin
runCatching {
    val parsed = nuevos.mapNotNull { msg -> parseSms(msg.text)?.let { it to msg } }
    if (parsed.isNotEmpty()) {
        WebPushSender.sendToUser(uid, buildSmsPushPayload(parsed.map { it.first }))
    }
}.onFailure {
    if (it is kotlinx.coroutines.CancellationException) throw it
    call.application.log.warn("push de sms-sync falló para $uid", it)
}
```

`buildSmsPushPayload(parsed: List<ParsedSms>): String` — puro, en
`server/.../push/PushPayload.kt` junto al de pagos: 1 movimiento →
`{"title": "Nuevo movimiento", "body": "$<monto miles> en <comercio> — toca para confirmar", "url": "/"}`;
varios → title "N movimientos nuevos", body hasta 3 líneas `$monto en comercio` + "…y N
más". Monto formateado con el mismo `formatMiles` (el `ParsedSms.amount` es Double —
formatear sin decimales cuando es entero, con el helper que ya existe ajustado o uno
local).

Los SMS que NO parsean no generan push (quedan en el inbox como siempre).

## Testing

- **Unit (androidApp):** `BankSenderFilter` (remitentes cortos, keyword en cuerpo,
  negativos personales); hash determinístico del id (mismo input → mismo id).
  (androidApp no tiene test infra hoy — crear `androidApp/src/test` con JUnit del
  catálogo; los tests del filtro/hash son JVM puros.)
- **Unit (server):** `buildSmsPushPayload` (1 y 4 movimientos, formato de montos).
- **HTTP (server):** sync con VAPID sin configurar → responde igual que hoy (sin push,
  sin fallo); la lógica "solo nuevos disparan push" se verifica por unit del payload +
  revisión (el sender real no se puede ejercitar sin push service).
- **E2E en emulador (verificación):** `adb emu sms send 85540 "Bancolombia: Compra por
  $50.000 en EXITO 12:00"` con la app instalada y logueada → el SMS aparece en
  `/api/sms` del server local SIN abrir la pantalla SMS. SMS de remitente personal → NO
  aparece. (Emulador x86_64 `Pixel_9_Pro` existente; server local en `10.0.2.2:8080` —
  para el e2e, `apiBaseUrl` de debug se apunta al server local vía la constante o
  BuildConfig si ya existe ese mecanismo; si no existe, documentar el cambio manual
  temporal y revertirlo.)

## Fuera de alcance (futuro)

Auto-confirmación con parser LLM; deep-link de la push a la pantalla SMS; filtro de
remitentes configurable en UI; captura retroactiva (ya existe con el pull actual); otros
bancos en el filtro (agregar constantes cuando lleguen SMS reales).
