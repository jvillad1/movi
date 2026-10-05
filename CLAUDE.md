# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
It is the canonical technical reference for this repo — `README.md` is the short human-facing intro,
and `AGENTS.md` is a pointer here for tools that don't auto-load `CLAUDE.md`. Keep this file the one
place that gets updated; don't fork details into the other two.

## App

**Movi** — personal and family finance management app. Package: `com.jvillada.movi`. Design specs for
individual features live under `docs/superpowers/specs/` (one file per feature, dated); the original
core design is `2026-04-26-monedero-core-design.md`.

## Project structure

Movi follows the **2026-05 KMP default structure** (JetBrains): a pure KMP library is split from the per-platform application modules, so no multiplatform module applies `com.android.application` (required by AGP 9.0).

```
movi/
├── core/         Pure Kotlin multiplatform — models + repository + SQLDelight. Shared by server AND clients.
├── shared/       Compose Multiplatform UI library — Android (lib), iOS, Web (wasmJs). Produces the iOS XCFramework.
├── androidApp/   Android application (com.android.application) — MainActivity host. Depends on :shared + :core.
├── webApp/       wasmJs browser application — main() + index.html. Depends on :shared.
├── server/       Ktor JVM backend. Depends on :core.
└── iosApp/       Swift shell that embeds the ComposeApp XCFramework (built from :shared).
```

### Module dependency graph

```
shared      ──▶  core
androidApp  ──▶  shared, core
webApp      ──▶  shared
server      ──▶  core
iosApp      ──▶  shared   (ComposeApp XCFramework)
```

> **Naming note:** the Gradle module is `:core`, but its Kotlin package stayed `com.jvillada.movi.shared.*` (and the SQLDelight DB package is `com.jvillada.movi.shared.db`). The module was renamed `shared`→`core` at the Gradle/directory level only; packages were intentionally left untouched to avoid regenerating SQLDelight code and rewriting imports.

## Commands

### Backend & Web
```bash
# Run Ktor server (port 8080)
./gradlew :server:run

# Run web app in browser (Compose/Wasm)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Build server fat JAR
./gradlew :server:buildFatJar

# Run tests — this is exactly what CI runs (.github/workflows/pruebas.yml), nothing more, nothing less
./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs
```

> **Never run `./gradlew build`.** It drags in the iOS release link tasks and takes ~48 minutes.
> There is no reason to run it — CI never does, and neither should you. Use the test command above,
> or a scoped task like `./gradlew :androidApp:assembleDebug` when you specifically need an APK.

### Android (from terminal, no Android Studio)
```bash
# List available AVDs
"$ANDROID_HOME/emulator/emulator" -list-avds
# Available: Pixel_8_Pro, Pixel_9_Pro

# Boot emulator in background (use the SDK binary at $ANDROID_HOME/emulator,
# not a stray `emulator` on PATH — on Apple Silicon the native arm64 binary lives there)
"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_Pro -no-snapshot-load > /tmp/emulator.log 2>&1 &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done && echo "booted"

# Build APK and install via adb (installDebug Gradle task doesn't see the device reliably)
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.jvillada.movi/.MainActivity

# View logs
adb logcat -s "movi"
```

### iOS (from terminal, no Xcode)
```bash
# Step 1 — build the Kotlin framework (required before every Xcode build)
./gradlew :shared:assembleComposeAppDebugXCFramework
# Output: shared/build/XCFrameworks/debug/ComposeApp.xcframework

# Step 2 — build the iOS app for simulator
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -configuration Debug \
  -derivedDataPath build/ios \
  build

# Step 3 — boot simulator (if not already running)
xcrun simctl boot "iPhone 16"
open -a Simulator

# Step 4 — install and launch
xcrun simctl install booted build/ios/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.jvillada.movi

# List available simulators
xcrun simctl list devices available
# Available iPhone 16 Pro, iPhone 16, iPhone 15 Pro, iPhone SE (3rd gen), etc.

# View logs
xcrun simctl spawn booted log stream --predicate 'subsystem contains "movi"'
```

## Architecture

This is a full-stack Kotlin project — one language, one codebase, four client targets + a server.

### core module

Targets: `android`, `iosX64/Arm64/SimulatorArm64`, `wasmJs`, `jvm`. Pure Kotlin (no Compose) so the server can depend on it without pulling UI code.

- `model/` — `@Serializable` data classes (`Account`, `FinancialEvent`, `CreditTerms`, `Subscription`, `RecurringRule`, and ~25 more). These are the wire types used by both the Ktor server responses and the client deserialization — do not add platform-specific code here. `Wallet`/`Transaction`/`TransactionType` in `Wallet.kt` are an early, largely superseded model kept for compatibility; `Account` + `FinancialEvent` are the ones actual features build on. **Bienes** (la casa, el carro) son un `Account` `INVESTMENT` con el campo `bien` (`Bien.kt`) — no un valor nuevo de `AccountType`, para no reventar APKs viejos — y **toda** cifra de «Tu plata» / condicionado / bienes / patrimonio neto sale de `patrimonioDe` (`Patrimonio.kt`), en cliente y server: no sumar el patrimonio a mano en ningún otro lado.
- `repository/` — repository interfaces + Ktor-client impls. Constructed with an `HttpClient` and a `baseUrl` string; the caller provides the platform-specific engine.
- SQLDelight DB lives here. SQLDelight has no wasmJs artifact, so a `nonWasmMain` intermediate source set holds the DB/driver code and the generated SQLDelight Kotlin; `wasmJs` configurations exclude the `app.cash.sqldelight` group.

### shared module

The Compose Multiplatform UI library. Source sets:
- `commonMain` — all Compose UI screens and `App.kt` entry point. Uses `:core` for data types.
- `androidMain` — Android `actual`s (`Platform.android`, `BackHandler.android`, `FilePicker`, `SmsSensorSetupSection`) using `ktor-client-android` + `activity-compose`, plus the SMS-capture subsystem (`sms/` — bank filter, filter-config store, uploader, backfill — and `sensor/` — permission/hibernation/notification-access helpers) and its sibling, the bank-notification capture (`notificaciones/` — package allowlist, the pure capture decision, and the phone's small memory of what it already uploaded), both shared with the receivers/services/workers in `:androidApp`. (The Android *application* — `MainActivity` — lives in `:androidApp`.)
- `iosMain` — `MainViewController()` bridges to `ComposeUIViewController`; the iOS XCFramework (`baseName = "ComposeApp"`) is built from this module.
- `wasmJsMain` — wasm `actual`s + `ktor-client-js`. (The wasm executable `main()` lives in `:webApp`.)

Ktor HTTP engine is platform-specific: `ktor-client-android` for Android, `ktor-client-darwin` for iOS, `ktor-client-js` for wasmJs.

### androidApp module

`com.android.application` (NOT multiplatform). Holds `MainActivity`, the `AndroidManifest.xml`, `res/` (launcher icons, strings), and the two capture sensors — the SMS receivers/workers (`SmsRealtimeReceiver`, `SmsSyncWorker`, `SmsFilterRefreshWorker`) and the `NotificationListenerService` that reads bank-app notifications (`notificaciones/EscuchaDeNotificaciones`) — which reuse the `sms/` and `notificaciones/` logic that lives in `:shared`'s `androidMain`. Both sensors upload through the **same** `SmsSyncWorker` → `POST /api/sms/sync` → the «Por revisar» inbox (Movimientos; history in Ajustes → «Captura del banco»); they differ only in the worker's `origen` input, which decides which "last capture" mark gets written (mixing them would hide one sensor going mute). `applicationId = com.jvillada.movi`; module `namespace = com.jvillada.movi.app`. `MainActivity` is a **`FragmentActivity`** (not `ComponentActivity`): AndroidX `BiometricPrompt` — «Entrar con huella», see `:shared` `platform/HuellaDelAparato.android.kt` — mounts itself as a fragment and finds no host otherwise. That prompt is a **door, not a safe**: it gates opening the app, and the session token stays in ordinary app storage on purpose, so `SmsBackfillWorker`/`SmsSyncWorker` keep uploading bank SMS with nobody in front of the phone. An earlier version encrypted the token with a Keystore key that required a fingerprint and was removed whole for exactly that reason — don't reintroduce it without solving the background workers first (`data/EntrarConHuella.kt` carries the full rationale). It (Kotlin package `com.jvillada.movi`) calls `App()` from `:shared` and `DatabaseDriverFactory.init` from `:core` — the phone runs the full Movi app; the capture setup UI lives inside it (Ajustes → «Captura del banco» → «Captura en este teléfono» — permiso de SMS, acceso a notificaciones, hibernación e historial; `Screen.CapturaDelBanco`, called «Mensajes del banco» on web/iOS), not in a separate sensor screen. What's pending review (bank messages, events that came in on their own, card-payment candidates) lives in one inbox, `Screen.PorRevisar` (`ui/porrevisar/`), opened from Movimientos' «N por revisar» row and the Hoy alerts.

### webApp module

KMP module with a single `wasmJs` executable target. `main()` calls `CanvasBasedWindow("Movi") { App() }`. The HTML shell is at `webApp/src/wasmJsMain/resources/index.html`. `moduleName`/`outputFileName` are kept as `composeApp`/`composeApp.js` so the HTML script ref and the Dockerfile copy path are unchanged.

### server module

JVM-only Ktor application on Netty, port 8080.

`Application.kt` wires: `DatabaseFactory.init()` → CORS → Serialization → Monitoring → Auth → Routing → `startReminderScheduler()` (no-op without `RESEND_API_KEY`).

- `plugins/` — one file per Ktor plugin (`CORS.kt`, `Serialization.kt`, `Monitoring.kt`, `Auth.kt`, `Routing.kt`).
- `routes/` — one file per resource, registered in `plugins/Routing.kt`: `AccountRoutes` (`/api/accounts`), `EventRoutes`, `CreditRoutes`, `PagoDeCuotaRoutes`, `TransferRoutes`, `SubscriptionRoutes`, `CardRoutes`, `CategoryRoutes`, `GoalRoutes`, `DestinoRoutes` (`/api/destinos` — «cuentas de otros»: el registro de números de cuenta AJENOS, con nombre y de quién son. No son `Account` a propósito: no suman en «Tu plata» ni en el patrimonio. Sirven para que un SMS/notificación/fila de extracto que nombra ese número se proponga como «Transferencia a Caro», y para ver junto lo que se le envió — ver `DestinoConocido` en `:core`; desde el 4-oct-2026 un tercero tiene **varios identificadores** —números, llaves y el nombre con que lo nombra el banco— en `known_destinations.identificadores` (JSON; `numero`/`llave` siguen con el primero de cada clase para el APK instalado), un `tipo` persona/comercio, y la ficha separa lo enviado de lo recibido. `GET /api/destinos/sugeridos` (`destinosSugeridos`, `:core` `TercerosSugeridos.kt`) junta lo que los avisos y movimientos nombran y nadie guardó —sin cuentas propias, sin el nombre del dueño, sin lo descartado en `destinos_descartados`— y propone nombre, tipo y «¿Es Caro?»; **nada se une ni se renombra sin que el dueño lo confirme** (`POST /{id}/identificadores`, `/{id}/renombrar` solo sobre conceptos ilegibles), `DocumentRoutes`, `StatementRoutes`, `SmsRoutes`/`SmsFilterConfigRoutes`, `CorreoEntranteRoutes` (alertas del banco por correo — ver abajo), `PushRoutes`, `ReminderRoutes`, `ScreenRoutes` (SDUI), `AuthRoutes`, `UserRoutes`, `AiRoutes` (Claude API chat, needs `ANTHROPIC_API_KEY`), `DashboardRoutes`, `VersionRoutes`. There is no `WalletRoutes` — that model is legacy (see `core` above).
- **Las alertas del banco por correo** (`correo/`, `routes/CorreoEntranteRoutes.kt`) son el **tercer hermano** de la captura de SMS y de la de notificaciones, y el único que vive en el server: cubren lo que el banco **se cobra solo** (cuota de manejo del cupo rotativo, su IVA, intereses, retenciones, débitos automáticos), que no llega por SMS ni publica notificación, y siguen funcionando con el teléfono apagado. Un proveedor de correo entrante (Postmark, Mailgun, Resend) postea el correo a `POST /api/correo-entrante`, **público** —no tiene sesión— y protegido por `INBOUND_EMAIL_SECRET`: sin esa env var la ruta contesta 503 y no acepta nada. El secreto viaja en `Authorization: Basic` (usuario cualquiera, contraseña = el secreto) o en `X-Movi-Correo-Secreto`, **nunca en la URL** — `CallLogging` imprime la ruta de cada petición. De ahí en adelante **no hay nada nuevo**: mismo `parseSms`, misma tabla `sms_messages`, mismo dedupe (`SmsDedupe`, texto + tiempo) y misma bandeja, con el origen marcado en `bank` como «Correo · Bancolombia» (los SMS traen el código del remitente, las notificaciones «Notificación · …»). El correo se le atribuye a un usuario **por la dirección a la que llegó** (`base+<token>@dominio`, token = SHA-256 de su id): un reenvío de Gmail conserva el `From:` del banco y el `To:` personal, así que el destinatario de SOBRE es el único dato que identifica al dueño. Un correo sin token resoluble contesta 202 y **no escribe nada**. `GET /api/correo-entrante/direccion` (autenticada) devuelve la dirección de reenvío de quien pregunta.
  - **El proveedor real es Resend** (Postmark no acepta registrarse con un Gmail): `POST /api/correo-entrante/resend`, también pública, la protege la **firma Svix** del webhook (`RESEND_WEBHOOK_SECRET`, el `whsec_…`; `correo/FirmaDeSvix.kt`, HMAC-SHA256 a mano sobre `svix-id.svix-timestamp.cuerpo crudo`, tolerancia 5 min): sin la env var 503, firma ausente/mala/vieja 401. El evento `email.received` **no trae el cuerpo**: con su `email_id` se pide `GET https://api.resend.com/emails/receiving/{id}` (`LectorDeCorreosRecibidos`, inyectable: ninguna prueba toca la red) con `RESEND_RECEIVING_API_KEY` o, si no está, `RESEND_API_KEY` — **tiene que ser una clave «Full access»**: una «Sending access» recibe 401/403 al leer. La API caída o un 404 → 502 (Resend reintenta: 5 s, 5 min, 30 min, 2 h…); otro evento → 200 sin hacer nada. El contenido se arma como el MISMO `CorreoEntrante` y sigue por `guardarCorreoEntrante`, el final común con la ruta de Postmark. **Atribución con un reenvío de Gmail**: el `to` de Resend es la cabecera, que el reenvío deja con el Gmail del dueño; por eso se miran antes `received_for` (Resend: «la cláusula `for` de las cabeceras `Received`», o sea el sobre) y `X-Forwarded-To` (la pone Gmail), y se prueban **todos** los tokens hasta dar con el de un usuario. El subdominio `<id>.resend.app` recibe cualquier dirección: la base va en `INBOUND_EMAIL_ADDRESS` (`alertas@<id>.resend.app` → `alertas+<token>@<id>.resend.app`), y si el `+` no sobreviviera, `<token>@<id>.resend.app` también atribuye (una parte local de 16 hex). La **confirmación de reenvío de Gmail** (código + enlace) entra apartada con motivo `CONFIRMACION_DE_REENVIO`, con el código legible en `sms_messages.text`. Encender: Resend → Emails → Receiving (la dirección `<id>.resend.app`) → Webhooks → Add Webhook a `https://movi-project-production.up.railway.app/api/correo-entrante/resend` con el evento `email.received` → copiar el signing secret a `RESEND_WEBHOOK_SECRET` en Railway.
- **Confirmar un aviso arma las dos patas** (arreglo 1 de la auditoría de la ingesta, `routes/LasDosPatasDelAviso.kt`, `:core` `LasDosPatasDelAviso.kt`): el pago de una tarjeta propia, la cuota de un crédito propio, un traspaso entre cuentas suyas y el avance de una tarjeta se confirman con `DosPatasDelAviso` (operación, origen, destino, monto, fecha, nota y tres ids del cliente) y el server escribe las dos patas con **una sola función**, `escribirLasPatasDelAviso`, dentro de `confirmarElMismoPago` (bloqueo `FOR UPDATE` de los avisos, idempotencia, `evento_id` = la pata del dinero, `confirmado_en`). La usan las dos confirmaciones: `POST /api/sms/{id}/confirm` **con cuerpo** y `POST /api/sms/grupo/{id}/confirmar` con `patas`. **Sin cuerpo, `/confirm` hace lo de siempre** (el APK 1.69 crea su movimiento y confirma): el server nunca deduce las patas solo, porque el destino lo elige el dueño en Reconciliar («Sale de … · entra a …»). Pago y cuota con `pagoDeCuotaLegs` (por eso el período queda marcado igual que con `vincular-deuda`); traspaso con `transferLegsFor`; avance con `patasDelAvance`: la tarjeta EXPENSE con «Traspaso» (con desembolso contaría como gasto) y la cuenta INCOME con «Desembolso de crédito» (suma en «Entró»). El desglose de un pago a una deuda vive en `desgloseDelPagoDeDeuda` (`DesgloseDelPago.kt`), compartido con `PagoDeCuotaRoutes` y la Ola Y.
- **«Compartir con Movi»: Movi lee papeles** (Ola 2, `routes/PapelesRoutes.kt`, `parsing/LectorDePapeles.kt`): el dueño comparte una imagen o un PDF desde Android (`intent-filter` SEND/SEND_MULTIPLE de `MainActivity`, leído en `:shared` `androidMain/compartir/`), o lo sube desde Por revisar / Agregar / soltándolo en la web. La app lo guarda con el mismo `POST /api/documents` (campo `reusar`: el mismo archivo no se duplica) y pide `POST /api/documents/{id}/leer`, que decide con **Haiku** qué es: un **comprobante** queda como una fila más de `sms_messages` con id `cmp_<documento>` y `bank` «Comprobante · <archivo>» (hereda Revisar, la memoria de categorías, las cuentas de otros, «¿Ya lo anotaste?»; su `/parse` sale de la lectura guardada, no del parser de SMS; si ya está anotado no se crea y se dice `yaAnotado`); un **extracto** va por `leerElExtracto` + `conciliarYArchivar` (las dos mitades de `procesarExtracto`) y la app abre su revisión. **El mismo archivo nunca va dos veces a Claude**: la lectura se guarda por SHA-256 en `lecturas_de_papeles`. Todo lo compartido se procesa **después** de la puerta de la huella (`sePuedeLeerLoCompartido`). Las pruebas cambian `LectorDePapeles.actual` por uno falso: ninguna llama a la API. La captura (`capturaDeSms`, banco mudo) cuenta solo `soloLoQueLlegoSolo`: un comprobante no prueba que el teléfono capture. **Modelos (benchmark del 4-oct con los papeles reales):** Haiku clasifica; el extractor (`ClaudeStatementParser.conLoDeSiempre`, y el modelo que guarda `leerElPapel`) usa `MODELO_DE_EXTRACTOS` = Sonnet 5.5 con `effort` bajo y el sistema cacheado — misma precisión que Opus 4.7 con 64 % menos de costo; Haiku NO extrae (se equivoca en silencio con montos y meses). El prompt lee **solo el período facturado** (no las compras diferidas de períodos anteriores), descarta filas de $0, y un crédito sin lista de movimientos o una factura sin pagar son NADA. **Sin crédito en Anthropic** (400 «credit balance», 402) o con la clave rechazada, las rutas de papeles, extractos y Movi AI contestan **503** con `IA_SIN_CREDITO` / `IA_NO_DISPONIBLE` (`core/.../LaIaNoEstaDisponible.kt`) y la app lo dice; el archivo queda en Documentos.
- **Los cargos y abonos del banco** (`:core` `CargosDelBanco.kt`, `CargosDelExtracto.kt`): `cargoDelBanco(texto, tipo?)` reconoce por el rótulo el 4x1000/GMF/retención («Impuestos»), cuota de manejo/IVA/comisiones/intereses de la tarjeta («Comisiones del banco») e intereses de ahorros/rendimientos («Ingreso»); **solo reconoce, nunca calcula una cifra**, y si el dueño no tiene esa categoría devuelve `null` (no se crea). `categoriaProbablePorElNombre` lo usa en un bloque aparte, detrás de la memoria. En un extracto, `conciliarYArchivar` les pone la categoría y manda `StatementParseResult.cargosDelBanco` con los movimientos que ya las cubren sumadas (misma categoría/tipo/moneda, el rango que escribió el dueño —«del 27 al 30 de septiembre»— o su día): suma exacta en la cuenta del extracto → «ya anotado» y destildada; si no, aviso. La revisión las agrupa en «Cargos y abonos del banco (N)» con «Anotar los N» (un importe propio).
- **«Banco mudo»** (Ola 2, `origenesMudos` en `:core` `BancoMudo.kt`, `server/sms/OrigenesMudos.kt`): un origen de captura (los SMS de Bancolombia, las notificaciones de Nu, los correos) que llegaba con regularidad —5+ capturas en 3+ días distintos en los 30 días antes de la última— y lleva `users.dias_para_banco_mudo` días (default 3, `0` = apagado, se elige en Captura del banco) sin llegar, con un silencio más largo que cualquier hueco de esa ventana. Viaja en `DashboardSummary.bancosMudos` (alerta urgente en Hoy y en la campana → Captura del banco) y en `GET /api/sms/origenes-mudos`, que lee el `AvisoDeBancoMudoWorker` del teléfono (9:00, con su interruptor; la misma huella no suena dos veces).
- **«Movi mira adelante»** (Ola 4). **Caja proyectada**: `cajaProyectada` (`:core` `CajaProyectada.kt`) parte de Tu plata (`patrimonioDe`), mueve cada fila pendiente de la lista «Pagos del período» el día que vence (lo vencido, hoy; una tarjeta paga su `pagoMinimo` y sin él va a `sinContar`, nunca un porcentaje) y resta **desde mañana** el gasto del día a día de `GET /api/caja-proyectada/gasto-del-dia-a-dia` (promedio total/días de hasta 3 períodos cerrados que Movi cubrió enteros: si el primer movimiento del dueño es posterior al arranque de un período, ese no cuenta). Para que server y cliente usen LA MISMA lista, `checklistDelPeriodo`/`PagoDelPeriodo`/`EstadoDeLaFila` se mudaron a `:core` (`PagosDelPeriodo.kt`); `:shared` los sigue nombrando igual por `typealias`. Plan la dibuja (`ui/plan/CajaDelPeriodo.kt`) en pantalla ancha en la columna del disponible, debajo de «Tus períodos», y en una columna como primera cosa de «Pagos del mes» (debajo del selector: encima lo empujaba y rompía la regla de `PlanScreenTest` de que nada salta más de 8 dp al llegar los datos); Movi AI la lee con la herramienta `proyectar_caja` (`cajaProyectadaDe` en `CajaRoutes.kt`, sobre `proximosPagos` + `ocurrenciasDelPeriodo`, lo mismo que contestan `/api/payments/upcoming` y `/occurrences`).
- **Plan de salida de deudas** (Ola 4): `planDeSalida` (`:core` `PlanDeSalida.kt`) sobre `deudasParaSalir(getCredits, getCards)` — amortización mes a mes con `interesDelPeriodo` (la misma de `planDeUnaDeuda`; sin abono da exactamente lo de Créditos), avalancha (mayor tasa primero) y bola de nieve (menor saldo primero), el abono extra va a capital de la primera viva y pasa a la siguiente, **la cuota liberada NO se suma** (el ahorro sale solo del abono). Sin tasa / sin mínimo / sin cuota / en otra moneda → `faltanDatos` («Falta la tasa: cárgala»); nómina o tercero → `ajenas`, no compiten. Las tarjetas ganaron `CardTerms.tasaEa` (columna `card_terms.tasa_ea`, nullable, misma guarda por clave que `pagoMinimo`, >100 se rechaza). Pantalla `Screen.SalidaDeDeudas` (`ui/credits/SalidaDeDeudasScreen.kt`) desde la fila «Cómo salir de tus deudas» de Créditos; Movi AI: `simular_abono`.
- **Lo que se sale de lo normal** (Ola 4, `:core` `LoQueSeSaleDeLoNormal.kt`): cuatro detectores puros con su evidencia — cobro duplicado (mismo monto, comercio por `claveDeComercio`, cuenta, ≤ 2 días, desde $20.000), suscripción que subió (último cobro del período vs el del anterior, > 2 % y ≥ $1.000), categoría muy por encima (≥ 1,5× el promedio de hasta 3 períodos anteriores cubiertos y ≥ $300.000 de diferencia) y comercio nuevo ≥ $500.000 (con ≥ 60 días de historia). Umbrales **elegidos, no medidos**. `GET /api/anomalias` (`AnomaliasRoutes.kt`) los arma y saca las huellas de `anomalias_descartadas`; `POST /api/anomalias/descartar` es el «Está bien». Hoy los pide aparte (no van en `/api/dashboard/summary`, que es la ruta más caliente) y los pinta en «Para revisar» con «Ver los movimientos»; `AnomaliasDescartadasEnLaSesion` (limpia en `SessionManager.clear`) cubre el rato hasta la próxima lectura.
- **Lo que el banco cobra solo** (débitos automáticos, `routes/DebitosAutomaticosRoutes.kt`, `:core` `DebitoAutomatico.kt`): el banco no avisa cuando debita una cuota o un seguro. El dueño marca el crédito (`CreditTerms.debitoAutomaticoDesde`, la cuenta de la que sale; no convive con libranza ni «la paga otro») o la regla (`RecurringRule.seDebitaSolo`, gasto con cuenta; tres estados en el wire como `accountId`). `GET /api/debitos-automaticos` **deriva en cada lectura** —no es una fila de `sms_messages`: no cuenta para «banco mudo» ni se agrupa con avisos reales— lo vencido (`ocurrenciaPorPreguntar`) sin movimiento que lo salde (`periodosSaldados` / el checklist abierto y sin candidatos), sin «No se cobró» (`debitos_automaticos_descartados`) y sin un aviso del banco pendiente por el mismo monto. «Por revisar» lo pinta en su bloque («Lo que el banco cobró solo») con «Sí, se cobró» / «Cambiar monto» / «No se cobró». **Nunca se anota solo**: «Sí, se cobró» es `POST /api/debitos-automaticos/confirmar` con los ids deterministas por (regla, período) que trae la propuesta — la cuota de dos patas por `escribirLasPatasDelAviso` (la de la confirmación de un aviso), o el gasto de un recurrente + el sello de su período, en una transacción. 4x1000, intereses y rendimientos quedan fuera a propósito: su cifra solo la sabe el extracto.
- **Compartir con un tercero** (`compartir/`, `routes/EnlaceCompartidoRoutes.kt`): un enlace de solo lectura, con vencimiento (1/7/30 días) y revocable, que abre una página HTML liviana servida por el server (sin wasm, sin login) con el resumen del dueño. `POST/GET/DELETE /api/enlaces-compartidos` son autenticadas; `GET /compartido` sirve la cáscara y `POST /compartido` el resumen. **El token va en el fragmento** (`/compartido#<token>`) y la página lo manda en el cuerpo del POST: el navegador nunca envía el fragmento, así que no queda en `CallLogging` ni en el log del borde de Railway. La base guarda solo el SHA-256 del token (tabla `enlaces_compartidos`, revocar no borra la fila); vencido, revocado e inexistente contestan el mismo 404. La página usa los colores de `Tokens.kt` (lo vigila `PaginaCompartidaTest`) y recorta todo número de 5+ dígitos a sus últimos 4. En la app: Ajustes (el avatar) → «Compartir» y el ícono del encabezado de Hoy.
- **Movi AI propone, recuerda y no olvida** (Ola 3, `ai/LoQueMoviPropone.kt`, `ai/LoQueMoviSabeDeTi.kt`, `ai/LaConversacionGuardada.kt`): **el asistente nunca escribe solo**. Las herramientas `proponer_movimiento`, `proponer_cambio_de_categoria`, `proponer_recurrente`, `proponer_pago_hecho` y `recordar` no escriben nada: validan en el server (cuenta del dueño, monto > 0, categoría existente o marcada nueva y nunca reservada, pago solo con un movimiento candidato del checklist) y devuelven una `AccionPropuesta` (`:core`, `Asistente.kt`) en `AiChatResponse.propuestas`; lo inválido no llega a la app. La app la pinta como tarjeta y «Hacerlo» llama al método de SIEMPRE del repositorio (`postEvent`, `recategorizarEnLote`, `createRecurringRule`, `markOccurrence` con `eventId`, `guardarRecuerdo`); `POST /api/ai/propuestas/{id}/estado` solo anota HECHA/RECHAZADA para que el turno siguiente lo sepa (tabla `asistente_propuestas`). La memoria («Lo que Movi sabe de ti», Ajustes) vive en `memoria_del_asistente`, viaja en su propio bloque cacheado del sistema con tope, y se edita por `/api/asistente/memoria`. La conversación se recarga con `GET /api/ai/conversacion` (los turnos de `ai_turns` desde `asistente_conversaciones.empezada_en`) y «Nueva conversación» es `POST /api/ai/conversacion/nueva`. Las herramientas de lectura ven los pagos de tarjeta y los traspasos rotulados, sin que entren a ningún total. Las pruebas montan el chat con `aiRoutes(fabricaDePrueba)`: ninguna llama a Anthropic.
- No migration files: `DatabaseFactory.init()` runs `SchemaUtils.create` (new tables) plus a manual `createMissingTablesAndColumns` step (new columns on existing tables) on every boot.
- `/health` endpoint returns `"OK"` for liveness checks.
- `/version` endpoint (público, sin auth) returns `{"commit":"<sha>"}` con 200 — o `{"commit":null}` con 503 si el proceso no sabe qué commit corre. Es la única forma de saber si un merge llegó a producción: cuando el build de Railway falla, la instancia vieja sigue arriba contestando 200 a todo. `.github/workflows/despliegue.yml` lo espera (a mano, ver abajo) y falla si no llega (`scripts/esperar-despliegue.sh`).
- Serves the wasm web bundle from `server/src/main/resources/static` via `staticResources("/", "static")`. The Dockerfile builds `:webApp:wasmJsBrowserDistribution` and copies `webApp/build/dist/wasmJs/productionExecutable/` into that dir before building the fat JAR.
- Config is env vars, read via `server/.env` (gitignored) in local dev or process env in prod — see `server/.env.example` for the full list (`DATABASE_URL`/`JWT_SECRET` required; `ANTHROPIC_API_KEY`, `RESEND_API_KEY`+reminder vars, `INBOUND_EMAIL_SECRET`/`INBOUND_EMAIL_ADDRESS`, `RESEND_WEBHOOK_SECRET`/`RESEND_RECEIVING_API_KEY`, `ALLOWED_ORIGINS`, `APP_TIMEZONE`, `USD_COP_RATE` optional).

### Version catalog

All dependency versions are centralized in `gradle/libs.versions.toml`. Add new dependencies there and reference them via `libs.*` aliases in build files — never hardcode version strings in `build.gradle.kts` files.

## CI & deploy

- `.github/workflows/pruebas.yml` ("Pruebas") runs on every PR and push to master: the same four Gradle tasks from the Commands section above, plus a deploy guard that moves `local.properties` aside (Railway's image has no Android SDK, and Gradle's config phase would otherwise choke on it) before dry-running the wasm distribution task.
- **Railway auto-deploy is OFF (since 2026-09-13, to save money): merging to master does NOT deploy.** Deploy explicitly, in batches, with **`./scripts/desplegar.sh`** — don't hand-roll `railway up`. It exports `origin/master` with `git archive` (a `railway up` from a git worktree uploads the *main* checkout, not the worktree), always passes `--service movi-project` (the main folder was once linked to the **Postgres** service, and a bare `railway up` shipped Movi's Dockerfile to the database: «Build failed · Service: Postgres»), sets `MOVI_COMMIT_SHA` with `--skip-deploys`, waits for *its own* deployment id, and verifies the served web actually has the fonts. `/version` alone proves nothing: it reads a variable you set yourself. Production: `https://movi-project-production.up.railway.app`.
- `.github/workflows/despliegue.yml` ("Despliegue") is manual only (`gh workflow run despliegue.yml -f commit=<sha>`): waits on `/version` via `scripts/esperar-despliegue.sh` (35 min timeout) and fails if the deployed commit never matches — Railway keeps serving the old build on a failed one.
- `scripts/` also has `build-apk.sh`, `generate-vapid-keys.sh` (web push), `seed-credits.sh` and
  `seed-subscriptions.sh`. Los dos `seed-*` comparten contrato: leen el token de `MOVI_TOKEN`
  (nunca por argumento — quedaría en el historial del shell), son dry-run salvo `--apply`, y son
  idempotentes contra lo que ya existe — con el alcance que eso tiene: la criba compara el
  nombre normalizado igual que el server más la moneda, así que atrapa un re-run pero **no** un
  registro que alguien haya escrito con otro nombre («Netflix» contra un «Netflix Colombia» que
  ya está). Para ese caso avisa y sigue; leé el dry-run antes de `--apply`.
  `seed-subscriptions.sh` resuelve la cuenta por NOMBRE contra `/api/accounts` al correr, en vez
  de guardar un id en el JSON, y en `--apply` cierra con un resumen y sale distinto de 0 si algún
  POST falló: un POST que falla no corta los que siguen.

## Key conventions

- **New REST endpoints** go in `server/src/main/kotlin/.../routes/` as extension functions on `Route`, then registered in `plugins/Routing.kt`.
- **New shared models** go in `core/src/commonMain/.../shared/model/` and must be annotated with `@Serializable`.
- **Platform-specific Ktor engine wiring** belongs in each `:shared` source set's dependency block, not in `commonMain`.
- The iOS Xcode project (`iosApp/iosApp.xcodeproj`) references the `ComposeApp` XCFramework at `shared/build/XCFrameworks/debug/ComposeApp.xcframework`. Always run `./gradlew :shared:assembleComposeAppDebugXCFramework` before building the iOS app — the Xcode build will fail if the framework is missing.
- **El desembolso de un crédito es plata que ENTRÓ** (decisión del dueño): las dos patas de un par
  que sale de una cuenta LOAN llevan `DESEMBOLSO_CATEGORY` («Desembolso de crédito»), la del dinero
  suma en «Entró»/«Ingresos» y la del crédito no (la excluye el tipo de cuenta). No es reservada
  —cuenta como cualquier categoría— pero solo la escribe `transferLegsFor`: `POST /api/events` la
  rechaza suelta, renombrar/unificar la protegen (igual que a `CUOTA_CATEGORY`), y Movi AI dice cuánto
  de los ingresos es deuda. El abono extraordinario sigue siendo un traspaso puro.
- **Las pruebas Robolectric arrancan con los `object` de la app en cero.** Todas las clases de
  `:shared:testDebugUnitTest` comparten un fork de JVM y un sandbox, así que el estado estático se
  filtraba de una clase a la siguiente (y ya hizo intermitente a una prueba que medía una pantalla).
  Lo resuelve `shared/src/androidUnitTest/kotlin/com/jvillada/movi/AppDePrueba.kt`, declarado en
  `androidUnitTest/resources/robolectric.properties`: limpia antes de **cada** método, sin que
  ninguna clase tenga que hacer nada. Si agregas un `object` mutable que una pantalla llene, fíjate
  que `SessionManager.clear()` lo limpie y agrégalo a `ElForkLlegaLimpioTest`.
- **Todo texto visible por el usuario va en español neutro latinoamericano (tuteo), sin voseo.** Los comentarios de código pueden seguir en rioplatense. `core/src/jvmTest/kotlin/com/jvillada/movi/shared/quality/VoseoScanTest.kt` protege `shared/src/commonMain/.../ui` contra regresiones (lista negra de formas voseantes dentro de literales de string).
