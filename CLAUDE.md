# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## App

**Movi** — personal and family finance management app. Package: `com.jvillada.movi`. Full design spec: `docs/superpowers/specs/2026-04-26-monedero-core-design.md`.

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

# Build all modules
./gradlew build

# Run tests
./gradlew test
./gradlew :core:test
./gradlew :server:test
```

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

- `model/` — `@Serializable` data classes (`Wallet`, `Transaction`, `TransactionType`). These are the wire types used by both the Ktor server responses and the client deserialization — do not add platform-specific code here.
- `repository/` — `WalletRepository` interface + `WalletRepositoryImpl` (Ktor client). The impl is constructed with an `HttpClient` and a `baseUrl` string; the caller provides the platform-specific engine.
- SQLDelight DB lives here. SQLDelight has no wasmJs artifact, so a `nonWasmMain` intermediate source set holds the DB/driver code and the generated SQLDelight Kotlin; `wasmJs` configurations exclude the `app.cash.sqldelight` group.

### shared module

The Compose Multiplatform UI library. Source sets:
- `commonMain` — all Compose UI screens and `App.kt` entry point. Uses `:core` for data types.
- `androidMain` — Android `actual`s (`Platform.android`, `BackHandler.android`, `FilePicker`, `SmsSensorSetupSection`) using `ktor-client-android` + `activity-compose`, plus the SMS-capture subsystem (`sms/` — bank filter, filter-config store, uploader, backfill — and `sensor/` — permission/hibernation helpers) shared with the receivers/workers in `:androidApp`. (The Android *application* — `MainActivity` — lives in `:androidApp`.)
- `iosMain` — `MainViewController()` bridges to `ComposeUIViewController`; the iOS XCFramework (`baseName = "ComposeApp"`) is built from this module.
- `wasmJsMain` — wasm `actual`s + `ktor-client-js`. (The wasm executable `main()` lives in `:webApp`.)

Ktor HTTP engine is platform-specific: `ktor-client-android` for Android, `ktor-client-darwin` for iOS, `ktor-client-js` for wasmJs.

### androidApp module

`com.android.application` (NOT multiplatform). Holds `MainActivity`, the `AndroidManifest.xml`, `res/` (launcher icons, strings), and the SMS-capture receivers/workers (`SmsRealtimeReceiver`, `SmsSyncWorker`, `SmsFilterRefreshWorker`), which reuse the `sms/` logic that lives in `:shared`'s `androidMain`. `applicationId = com.jvillada.movi`; module `namespace = com.jvillada.movi.app`. `MainActivity` (Kotlin package `com.jvillada.movi`) calls `App()` from `:shared` and `DatabaseDriverFactory.init` from `:core` — the phone runs the full Movi app; the SMS-capture setup UI lives inside it (Mensajes del banco → «Captura en este teléfono»), not in a separate sensor screen.

### webApp module

KMP module with a single `wasmJs` executable target. `main()` calls `CanvasBasedWindow("Movi") { App() }`. The HTML shell is at `webApp/src/wasmJsMain/resources/index.html`. `moduleName`/`outputFileName` are kept as `composeApp`/`composeApp.js` so the HTML script ref and the Dockerfile copy path are unchanged.

### server module

JVM-only Ktor application on Netty, port 8080.

`Application.kt` wires four plugins in order: CORS → Serialization → Monitoring → Routing.

- `plugins/` — one file per Ktor plugin (`CORS.kt`, `Serialization.kt`, `Monitoring.kt`, `Routing.kt`).
- `routes/WalletRoutes.kt` — REST endpoints under `/api/wallets`.
- `/health` endpoint returns `"OK"` for liveness checks.
- Serves the wasm web bundle from `server/src/main/resources/static` via `staticResources("/", "static")`. The Dockerfile builds `:webApp:wasmJsBrowserDistribution` and copies `webApp/build/dist/wasmJs/productionExecutable/` into that dir before building the fat JAR.

### Version catalog

All dependency versions are centralized in `gradle/libs.versions.toml`. Add new dependencies there and reference them via `libs.*` aliases in build files — never hardcode version strings in `build.gradle.kts` files.

## Key conventions

- **New REST endpoints** go in `server/src/main/kotlin/.../routes/` as extension functions on `Route`, then registered in `plugins/Routing.kt`.
- **New shared models** go in `core/src/commonMain/.../shared/model/` and must be annotated with `@Serializable`.
- **Platform-specific Ktor engine wiring** belongs in each `:shared` source set's dependency block, not in `commonMain`.
- The iOS Xcode project (`iosApp/iosApp.xcodeproj`) references the `ComposeApp` XCFramework at `shared/build/XCFrameworks/debug/ComposeApp.xcframework`. Always run `./gradlew :shared:assembleComposeAppDebugXCFramework` before building the iOS app — the Xcode build will fail if the framework is missing.
- **Todo texto visible por el usuario va en español neutro latinoamericano (tuteo), sin voseo.** Los comentarios de código pueden seguir en rioplatense. `core/src/jvmTest/kotlin/com/jvillada/movi/shared/quality/VoseoScanTest.kt` protege `shared/src/commonMain/.../ui` contra regresiones (lista negra de formas voseantes dentro de literales de string).
