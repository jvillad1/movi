# SP-2 · Ingestion (image vision + Android SMS) — design

**Fecha:** 2026-06-13
**Parte de:** arco production-ready de movi (SP-0 ✅ → SP-1 ✅ → **SP-2**).
**Alcance:** dos partes independientes. **A) Visión:** leer imágenes (screenshots/fotos)
de extractos vía Claude vision por el flujo de upload existente. **B) SMS Android:** leer
los SMS del teléfono y sincronizarlos por usuario al server. Depende de SP-0 (tabla
`sms_messages` por usuario).

## Contexto

- El pipeline de extractos hoy extrae **texto** (PDF/xlsx/csv) y se lo pasa a Claude
  (`ClaudeStatementParser.parse(text, rules)`, modelo `claude-opus-4-7`). Las imágenes caen
  a `bytes.toString(UTF_8)` → basura. Los `docs/movements` del usuario son **screenshots**,
  hoy ilegibles. El flujo upload→review→import (reconciliación) ya funciona sobre
  `List<ParsedTransaction>`; solo falta producir esa lista desde una imagen.
- La app **no** lee SMS del dispositivo (manifest solo tiene INTERNET; el label
  "AUTO-LECTURA ACTIVA" es ficción). La tabla `sms_messages` por usuario existe (SP-0) pero
  **no hay escritor**; el flujo parse→confirm (`SMSReconcileScreen`) ya existe.

## Decisiones (elegidas por el usuario)

- **A: subida de imagen + Claude vision** (sin cámara). Se usa el file picker existente; web
  + móvil. Requiere `ANTHROPIC_API_KEY` (la que el usuario está rotando).
- **B: auto-lectura en Android** (`READ_SMS`), sincronizando por usuario al server. Solo
  Android (iOS/web no pueden leer SMS → no-op).

## Parte A — Visión

### A1. Parser (`server/.../parsing/ClaudeStatementParser.kt`)
Nuevo `suspend fun parseImage(bytes: ByteArray, mimeType: String, rules: List<MerchantRule>):
List<ParsedTransaction>`. Igual que `parse(text, ...)` pero el mensaje USER lleva un bloque
de imagen (base64) + un bloque de texto con la instrucción ("Extraé los movimientos de este
extracto/captura"). Reusa `buildSystemPrompt(rules)` y `parseJson(...)`. Usa la API de
bloques de imagen del SDK Anthropic (`ContentBlockParam` con `base64` source y el media type).
Si no hay API key → `emptyList()` (igual que hoy).

### A2. Routing (`server/.../routes/StatementRoutes.kt`)
En `POST /api/statements/upload`: detectar imagen por `mimeType.startsWith("image/")` o
extensión (`png/jpg/jpeg/webp/gif/heic`). Si es imagen → `ClaudeStatementParser.parseImage(
bytes, mime, rules)` y `bankName = detectBankName(fileName)`; saltear `extractText`/
`detectDocumentType` (no aplica a binarios). El resto (reconciliación, period, respuesta
`StatementParseResult`, import) **sin cambios**. El `mimeType` ya llega por el multipart
(el cliente lo manda). Si el parse de imagen da vacío → mismo manejo "sin transacciones".

### A3. Cliente
El file picker existente (`rememberFilePicker`) ya devuelve `mimeType`; asegurar que el
selector **permita imágenes** (que el filtro de tipos incluya `image/*` además de
pdf/sheets/csv). `ExtractosScreen` ya sube y muestra `StatementReviewScreen`. (Las pantallas
`OCRScreens` son mocks muertos; fuera de alcance — el camino real es el upload de Extractos.)

### A4. Test
`ClaudeStatementParserTest` ya cubre `parseJson` offline; agregar un test de que la rama de
imagen arma el request con bloque de imagen (sin llamar a la red — testear el builder/branch
o, si es difícil, dejar la lógica de `parseImage` mínima y cubrir `parseJson`). No se llama a
Claude en tests.

## Parte B — SMS Android

### B1. Permiso (`androidApp/src/main/AndroidManifest.xml`)
Agregar `<uses-permission android:name="android.permission.READ_SMS" />`. (Solo lectura del
inbox; no se pide `RECEIVE_SMS` salvo que se quiera tiempo real — v1 lee el inbox al abrir la
pantalla.)

### B2. Lector multiplataforma (`:shared`)
`expect object SmsReader { suspend fun readInbox(sinceDays: Int): List<SmsMessage> }` en
commonMain.
- **androidMain:** query a `Telephony.Sms.Inbox` vía `ContentResolver` (necesita `Context` —
  usar el provider de contexto existente que usan `FilePicker`/`Platform.android`). Mapea cada
  SMS a `SmsMessage(id, time, bank=address, text=body, state="new", det="")` con `id`
  **determinístico** (hash de address+date+body) para idempotencia. Filtra a remitentes
  "tipo banco" (heurística: address alfanumérica / corta / contiene nombre de banco conocido)
  o trae todo y deja que `parseSms` decida; v1: traer inbox de los últimos `sinceDays` (def 30).
- **iosMain / wasmJsMain:** `actual` que devuelve `emptyList()` (no se puede leer SMS).

### B3. Permiso runtime + trigger (`:shared` androidMain + `SMSInboxScreen`)
Al abrir `SMSInboxScreen` en Android: si `READ_SMS` está concedido → leer inbox y
`syncSms(...)`; si no → mostrar botón "Activar lectura de SMS" que dispara el request de
permiso (Activity Result API) y, al conceder, sincroniza. En iOS/web la sección no aparece o
queda como manual.

### B4. Endpoint de sync (`server/.../routes/SmsRoutes.kt`)
Nuevo `POST /api/sms/sync` que recibe `List<SmsMessage>` (o `SmsSyncRequest(messages)`) e
**inserta por usuario** con dedupe por `id` (insertIgnore / upsert; si el id ya existe para
ese user, no duplica ni pisa el `state`). Responde `{ "synced": N }`. Todo por `call.userId()`.

### B5. Repo (`:core`)
`suspend fun syncSms(messages: List<SmsMessage>)` en interface + impls (POST a
`/api/sms/sync`). El flujo `getSmsMessages()`/`parseSms`/`confirmSms` ya existe y ahora opera
sobre filas reales del usuario.

### B6. Test
- `SmsReader` android es platform-specific (no test JVM puro); cubrir el mapeo/dedupe-id como
  función pura testeable si se extrae (`fun smsRowToMessage(...)` / `fun stableSmsId(...)`).
- Server: test de aislamiento — `POST /api/sms/sync` inserta solo para el usuario; re-sync no
  duplica (dedupe por id); B no ve los SMS de A (reusa harness H2 de SP-0).

## Seguridad / privacidad

- `READ_SMS` es sensible: pedir en runtime con justificación clara; solo se leen SMS, se
  sincronizan al server del propio usuario (aislado), nunca a terceros. Documentar en el
  permiso/onboarding por qué se pide.
- Nunca loguear cuerpos de SMS ni la API key.

## Config / env
Sin nuevas env vars (vision reusa `ANTHROPIC_API_KEY`). 

## Fuera de alcance (futuro)
- Cámara in-app (se eligió solo subida de imagen).
- `RECEIVE_SMS` / lectura en tiempo real por BroadcastReceiver.
- Lectura de SMS en iOS (no lo permite la plataforma).
- Quitar las pantallas `OCRScreens` mock (limpieza aparte).

## Entrega en batches
1. **Part A (vision):** A1 parser + A2 routing + A3 picker + A4 test. Server-contenido +
   ajuste mínimo de cliente. Alto valor inmediato ("leer documentos").
2. **Part B server:** B4 endpoint + B5 repo + B6 server test. Sin tocar Android todavía.
3. **Part B Android:** B1 manifest + B2 lector android/expect-actual + B3 permiso/trigger UI.
   Verificable con `:androidApp:assembleDebug`.
