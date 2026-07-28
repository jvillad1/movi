# App de instalación única (PWA + APK sensor) — diseño

**Fecha:** 2026-07-26
**Alcance:** `androidApp` (pantalla única de sensor), `:server` (ruta de config del
filtro), receiver (config remota con cache y fallback). La PWA no cambia (ya cumple su
parte del diseño).

## Problema / valor

El usuario quiere instalar el APK **una sola vez** y que todo lo demás evolucione con
deploys web. Hoy eso ya es cierto para el 95% de movi (la PWA se actualiza con cada
deploy), pero: (a) el APK carga toda la UI Compose duplicada, que envejece frente a la
web y tienta a reinstalar; (b) la lista de remitentes bancarios del filtro SMS está
compilada en el APK — agregar un banco exigiría reinstalar.

## Decisiones (locked)

- **Arquitectura A elegida:** PWA (Chrome) = UI diaria con push y auto-update; APK =
  sensor de SMS de instalación única. (Se descartó WebView shell: el WebView de Android
  no soporta Web Push — habría obligado FCM/Firebase y MÁS mantenimiento nativo.)
- **El APK renderiza SOLO una pantalla de sensor** (login + estado + "Abrir Movi").
  Matiz honesto: la dependencia a `:shared` se conserva (SessionManager, apiBaseUrl,
  cliente de login) — los bytes de la UI Compose siguen linkeados pero NO se renderizan.
  Recortar bytes del APK es no-objetivo (app personal sideloaded); el objetivo es que el
  comportamiento del APK sea estable y no haya razón funcional para reinstalarlo.
- **Filtro de remitentes servido por el server:** `GET /api/sms/filter-config` (pública,
  sin auth — no revela nada sensible: son códigos de remitentes bancarios) →
  `{"senderCodes": ["85540","891333","87400"], "bodyKeywords": ["bancolombia"]}`.
  Fuente: constantes en código del server (editar = deploy web).
- **Cache y fallback en el APK (locked):** el receiver lee la config SOLO de
  SharedPreferences (síncrono, sin red en el camino del SMS). La config cacheada se
  refresca best-effort: al abrir la pantalla del sensor y tras cada sync exitoso del
  Worker (TTL 24h). Sin cache válida → fallback a las constantes compiladas actuales
  (`BankSenderFilter` conserva sus defaults). Fail-open a los defaults, nunca a "no
  capturar".
- **Casos que SÍ requieren APK nuevo (aceptados):** permisos nuevos, cambios del
  receiver/Worker, rupturas de Android. Todo lo demás — UI, detección, filtros, push,
  parsers — viaja por deploy web.

## Diseño

1. **Server** (`routes/SmsRoutes.kt` o archivo propio `SmsFilterConfigRoutes.kt`): ruta
   pública con las constantes actuales del filtro. `@Serializable` local
   `SmsFilterConfig(senderCodes: List<String>, bodyKeywords: List<String>)` — también en
   el APK se parsea con `org.json` (sin dep nueva).
2. **androidApp — config remota:** `SmsFilterConfigStore` (SharedPreferences propias
   `movi_sms_filter`): `load(): Config` (cache o defaults), `refreshIfStale(force)` (fetch
   con HttpURLConnection + org.json, TTL 24h, silencioso ante error). `BankSenderFilter.
   matches(sender, body, config)` pasa a recibir la config (defaults = constantes de hoy);
   el receiver hace `matches(sender, body, SmsFilterConfigStore.load(context))`.
3. **androidApp — pantalla sensor:** `MainActivity` deja de llamar `App()` y renderiza
   `SensorScreen` (Compose local en androidApp): estado de sesión (email o botón de
   login → pantalla de login mínima local que llama al repo de auth existente), permisos
   SMS (concedidos/no + botón de settings), última captura enviada (timestamp en prefs,
   escrito por el Worker al éxito), config del filtro vigente (chips de remitentes) y
   botón "Abrir Movi" (`Intent ACTION_VIEW` a la URL de prod). Al abrir: dispara
   `refreshIfStale()`.
4. **Worker:** al éxito del sync escribe `last_capture_at` en las prefs del sensor y
   dispara `refreshIfStale()` (piggyback, best-effort).

## Testing

- **Unit (androidApp):** parseo del JSON de config (válido/corrupto→defaults);
  `matches` con config remota que agrega un remitente nuevo; TTL (stale/fresh).
- **HTTP (server):** la ruta responde el JSON con las constantes; es pública.
- **E2E emulador (la prueba de la promesa):** con el APK instalado, agregar un remitente
  de prueba SOLO en el server local → refresh de config (abrir sensor o forzar) →
  `adb emu sms send <nuevo-remitente> ...` → el SMS llega al server SIN reinstalar APK.
- Suite server + unit androidApp verdes; `:androidApp:assembleDebug` compila.

## Fuera de alcance (futuro)

Recorte de bytes del APK (quitar la dependencia de UI de `:shared`); FCM; keywords de
parseo (no de filtro) configurables; panel de administración del filtro en la web UI
(editar la lista sin deploy — hoy editar = tocar constantes del server y deploy).
