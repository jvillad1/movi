# Reporte — el teléfono corre Movi completo; el sensor vive adentro

Rama: `feat/android-app-completa` (base 8314b94). Hallazgo original: `MainActivity` montaba
`SensorScreen()` — el APK era «Movi Sensor», no Movi.

## Qué cambió

### 1. MainActivity monta `App()` (androidApp/src/main/kotlin/com/jvillada/movi/MainActivity.kt)
- `setContent { App() }` (la app completa de `:shared`), con `DatabaseDriverFactory.init` (ya
  estaba) — como documenta CLAUDE.md y como arrancan web/iOS (`CanvasBasedWindow`/
  `ComposeUIViewController` llaman `App()` sin más init; en Android solo hace falta el driver).
- Se mantienen `SmsFilterRefreshWorker.schedule` y se agrega `SmsFilterConfigStore.refreshIfStale`
  en cada apertura (reemplaza el trigger que vivía en el `LaunchedEffect` de SensorScreen).
- El login es el `LoginScreen` de la app; el `SessionManager` es el mismo objeto de `:shared` que
  los Workers ya leían (`SessionManager.token`), así que la captura funciona con la sesión
  iniciada desde la app — **verificado en emulador** (ver Prueba real).

### 2. El sensor se mudó adentro (sin perder nada)
- **Eliminado**: `SensorScreen.kt` entero, incluido su login propio (redundante) y su tarjeta de
  «SESIÓN» (el aviso de sesión vencida ya no tiene pantalla donde vivir: un 401 del Worker cierra
  sesión y la app navega sola a LoginScreen; al volver a entrar, la sección limpia la marca
  `clearAuthExpired`, como hacía el login del sensor).
- **Nueva sección** «Captura en este teléfono» dentro de Mensajes del banco:
  - `shared/src/commonMain/.../ui/sms/SmsSensorSetupSection.kt` — `expect @Composable fun
    SmsSensorSetupSection(onSynced: () -> Unit)`; llamada desde `SMSInboxScreen` (SMSScreens.kt).
  - `shared/src/androidMain/.../ui/sms/SmsSensorSetupSection.android.kt` — actual real: tarjeta de
    permisos (estado RECEIVE/READ, «Conceder permisos» / «Abrir ajustes de la app», texto
    condicional de ajustes restringidos Android 15 para instalaciones fuera de tienda), tarjeta de
    hibernación («Evitar que Android la pause», solo cuando aplica) y tarjeta de historial
    (backfill 30 días con el filtro bancario + fechas de última captura / último historial).
    Estilo Min* de la app (no el tema oscuro propio del sensor).
  - actuals vacíos en `iosMain`/`wasmJsMain` — en web/iOS no se pinta nada (sigue el texto de
    «los lee tu teléfono»).
- **Movidos a `shared/src/androidMain`** (mismo paquete, `git mv`): `sensor/RestrictedSettings.kt`,
  `sensor/Hibernation.kt`, `sms/BankSenderFilter.kt`, `sms/SmsFilterConfigStore.kt`,
  `sms/SmsSync.kt`, `sms/SmsBackfill.kt`; más `sensor/SensorSetup.kt` (nuevo: helpers de
  permisos/ajustes/OnResume extraídos de SensorScreen). En `SmsSync.kt` los tipos que usa
  `SmsSyncWorker` pasaron de `internal` a públicos (el Worker quedó al otro lado de la frontera
  del módulo); documentado en el KDoc.
- **Tests movidos** con su código a `shared/src/androidUnitTest` (7 archivos: sensor/ y sms/);
  `libs.org.json` agregado a las deps de androidUnitTest de `:shared` (mismo motivo que tenía
  androidApp: el android.jar mockeable stubbea org.json). `androidApp` quedó sin tests y sin esa
  dep.
- **Tarjeta vieja «Sincronizar SMS del teléfono» eliminada** de SMSInboxScreen (y con ella el
  expect/actual `rememberSmsSync` + `SmsReader.kt`/`SmsReader.ios.kt`/`SmsReader.wasmjs.kt`;
  `SmsReader.android.kt` conserva las funciones puras y `readDeviceSms` que usa el backfill).
  Razón: era Android-only (nunca alcanzable mientras el APK fue solo-sensor), subía el inbox de
  30 días SIN el filtro bancario, y habría quedado como segundo botón de sync en la misma
  pantalla. El historial de la sección la reemplaza con el MISMO filtro de privacidad del
  receiver.

### 3. Receivers y workers — intactos en androidApp
`SmsRealtimeReceiver`, `SmsSyncWorker`, `SmsFilterRefreshWorker` no se tocaron (solo resuelven
sus helpers desde `:shared` ahora). AndroidManifest sin cambios: receiver registrado igual.

### 4. Identidad
- `strings.xml` ya decía «Movi»; el único «Movi Sensor» restante (título dentro de SensorScreen)
  se fue con la pantalla. Los workers no tienen notificaciones. `grep -rn "Movi Sensor"` sobre
  androidApp/shared/iosApp/webApp: 0 resultados.
- `versionCode 4`, `versionName "1.3"` en androidApp/build.gradle.kts.
- CLAUDE.md: párrafos de `:androidApp` y `shared/androidMain` actualizados (receivers/workers en
  androidApp, subsistema sms/sensor en shared, la app completa en el teléfono).
- `VoseoScanTest` ahora escanea también `shared/src/androidMain/kotlin` (los textos del sensor
  salieron del árbol `androidApp` que ya escaneaba). Textos nuevos en tuteo.

## Prueba real (AVD `Movi_Sensor`, API 35)

El `apiBaseUrl` de androidMain apunta a producción y NO se tocó en el commit. Para poder probar
sesión sin tocar la instancia real del dueño, la prueba se hizo con un **parche local temporal**
(`apiBaseUrl = http://10.0.2.2:8080`, revertido con `git checkout` antes del commit — verificado
en el diff) contra el server local (`/health` OK, Postgres local, usuario `demo@movi.app`).
Contra producción solo salieron requests con token inválido (401); no se creó ningún dato.

Verificado con capturas (`/tmp/movi-*.png`, copias en `.superpowers/sdd/movi-*.png`):
1. **Arranque → login de la app** (Movi / Finanzas personales, campos, «Entrar», «Regístrate»).
2. **Login demo → Inicio** (Dashboard con guía de primeros pasos, balance, barra de navegación).
3. **Mensajes del banco** con la sección «CAPTURA EN ESTE TELÉFONO»: permisos («Falta» → CTA con
   el texto de ajustes; el veredicto DENIED salió del estado real del AVD, que ya había negado
   permisos en la era sensor), hibernación y el historial con fechas.
4. **Captura en tiempo real con la sesión de la app**: permisos concedidos por adb, SMS inyectado
   (remitente 85540) → `SmsRealtimeReceiver` + `SmsSyncWorker` lo subieron solos: «Última captura
   automática: 20/08/2026 18:40».
5. **Backfill**: «Sincronizar últimos 30 días» → «10 mensajes bancarios encontrados · 9 nuevos
   subidos» (el décimo dedupeado: era el capturado en tiempo real) y la Bandeja se refrescó a 10
   vía `onSynced`.
6. **APK final (URL de producción)** reinstalado: el token del server local produjo la racha de
   401 y la app cerró sesión sola hacia el login — el comportamiento diseñado.

## Verificación

```
./gradlew :server:test :core:jvmTest :shared:testDebugUnitTest   → BUILD SUCCESSFUL
./gradlew :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs \
          :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug → BUILD SUCCESSFUL
```
`:shared:testDebugUnitTest` corre las 12 clases del subsistema (incluidas las 7 movidas);
`:core:jvmTest` incluye `VoseoScanTest` con el árbol nuevo (re-ejecutado con `--rerun-tasks`).

## Dudas / notas
- La marca `KEY_AUTH_ERROR_AT` sigue existiendo (el Worker la escribe); su único lector visible
  hoy es la limpieza al abrir la sección. `isSessionExpired` queda con su test como contrato del
  Worker. Si molesta, se puede retirar en una pasada futura.
- El backfill y la config del filtro siguen subiendo por `HttpURLConnection` directo (SmsSync),
  no por el repositorio offline-first — deliberado: el KDoc de SmsSync exige un único uploader
  alineado con el dedupe del server.
- Durante la sesión el árbol compartido cambió de rama un momento (stash de coordinación); las
  movidas se rehicieron y el commit incluye SOLO los archivos de esta tarea, por nombre.

## Correcciones de la revisión (commit posterior)

- **M1** — El 401 del sync en background ya no cierra sesión directo: `SmsSyncWorker` y
  `SmsBackfill` rutean por `SessionManager.onUnauthorized()` (racha de 3), manteniendo
  `markAuthExpired`. En el Worker, mientras la sesión sobreviva, `Result.retry()` acotado
  (`runAttemptCount < MAX_AUTH_ATTEMPTS = 5` — el tope importa porque la racha vive en
  memoria y un proceso nuevo la reinicia) para no perder ese SMS. En el backfill, un 401
  con sesión viva devuelve el outcome nuevo `AuthRetry` («El servidor rechazó la sesión
  esta vez — prueba de nuevo en un momento»); `SessionExpired` queda solo para cuando la
  racha de verdad cerró la sesión. Además el branch Success del Worker limpia
  `KEY_AUTH_ERROR_AT`: si la captura volvió a subir, la pausa terminó.
- **m2** — La marca ganó su lector: la sección lee `authErrorAt` ANTES de limpiarla
  (`remember`, así el texto sobrevive a la limpieza durante la composición) y la tarjeta
  de historial muestra «La captura estuvo pausada por un problema de sesión desde el
  dd/MM/yyyy HH:mm — sincroniza el historial para recuperar lo que haya llegado en ese
  tiempo» (`captureOutageNotice`, función pura con test).
- **m3** — Comentario del Worker actualizado: ya no promete una SensorScreen que no
  existe; describe la racha y el logout global real.
- **m4** — «AUTO-LECTURA ACTIVA» condicionado al estado real vía nuevo expect/actual
  `rememberSmsCaptureReady()` (Android: permiso de SMS releído OnResume; iOS/wasm: false,
  nunca visible porque el rótulo solo se pinta con `isAndroid`). Sin permiso el rótulo
  dice «CAPTURA PENDIENTE DE PERMISO» (MinWarn) y el cuerpo manda a la sección de abajo.
- TDD: los dos tests nuevos de `BackfillMessageTest` (AuthRetry y aviso de pausa) se
  escribieron primero y fallaron en rojo antes de implementar.
- Verificación: `:shared:testDebugUnitTest` (7 casos en BackfillMessageTest, todos verdes),
  `:core:jvmTest` (VoseoScan sobre los textos nuevos), `:webApp:compileKotlinWasmJs`,
  `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` — BUILD SUCCESSFUL.
  No se repitió la prueba de emulador para estas correcciones (lógica fina cubierta por
  tests; la UI nueva es un texto condicional).

## Seguimientos del re-review (commit posterior)

- `SessionManager.onAuthSuccess()` en los branch Success de `SmsSyncWorker` y `SmsBackfill`:
  el camino HttpURLConnection no pasa por el pipeline Ktor, así que nadie cortaba la racha
  de 401 — dos transitorios recuperados con semanas de distancia contaban como
  "consecutivos" y deslogueaban por sorpresa (el perfil exacto del teléfono-sensor).
- `rememberSaveable` (no `remember`) para `authErrorAt` en la sección: vive en un `item{}`
  de la LazyColumn y con bandeja larga el scroll la descarta; un `remember` pelado se
  re-ejecutaba contra la pref ya limpiada y el aviso moría en mitad de la visita.
- `markAuthExpired` first-write-wins (escribe solo si la marca está en 0): el «desde
  dd/MM/yyyy HH:mm» ahora es el inicio real de la pausa, no el último 401 del episodio —
  Success y re-login limpian la marca, así que la próxima escritura vuelve a ser un inicio.
- Verificación: `:shared:testDebugUnitTest` y `:androidApp:assembleDebug` — BUILD SUCCESSFUL.
