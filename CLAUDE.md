# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend & Web
```bash
# Run Ktor server (port 8080)
./gradlew :server:run

# Run web app in browser (Compose/Wasm)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Build server fat JAR
./gradlew :server:buildFatJar

# Build all modules
./gradlew build

# Run tests
./gradlew test
./gradlew :shared:test
./gradlew :server:test
```

### Android (from terminal, no Android Studio)
```bash
# List available AVDs
emulator -list-avds
# Available: Pixel_8_Pro, Pixel_8_Pro_Clean

# Boot emulator in background
emulator -avd Pixel_8_Pro -no-snapshot-load &
adb wait-for-device shell getprop sys.boot_completed

# Build, install and launch
./gradlew :composeApp:installDebug
adb shell am start -n com.jvillada.monedero/.MainActivity

# View logs
adb logcat -s "monedero"
```

### iOS (from terminal, no Xcode)
```bash
# Step 1 — build the Kotlin framework (required before every Xcode build)
./gradlew :composeApp:assembleDebugXCFramework
# Output: composeApp/build/XCFrameworks/debug/ComposeApp.xcframework

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
xcrun simctl launch booted com.jvillada.monedero

# List available simulators
xcrun simctl list devices available
# Available iPhone 16 Pro, iPhone 16, iPhone 15 Pro, iPhone SE (3rd gen), etc.

# View logs
xcrun simctl spawn booted log stream --predicate 'subsystem contains "monedero"'
```

## Architecture

This is a full-stack Kotlin project — one language, one codebase, four targets.

```
monedero/
├── shared/       Pure Kotlin multiplatform — models + repository layer
├── composeApp/   Compose Multiplatform UI — Android, iOS, Web (wasmJs)
├── server/       Ktor JVM backend
└── iosApp/       Swift shell that embeds the ComposeApp framework
```

### Module dependency graph

```
composeApp  ──▶  shared
server      ──▶  shared
iosApp      ──▶  composeApp (Kotlin framework)
```

### shared module

Targets: `android`, `iosX64/Arm64/SimulatorArm64`, `wasmJs`, `jvm`.

- `model/` — `@Serializable` data classes (`Wallet`, `Transaction`, `TransactionType`). These are the wire types used by both the Ktor server responses and the client deserialization — do not add platform-specific code here.
- `repository/` — `WalletRepository` interface + `WalletRepositoryImpl` (Ktor client). The impl is constructed with an `HttpClient` and a `baseUrl` string; the caller provides the platform-specific engine.

### composeApp module

Source sets:
- `commonMain` — all Compose UI screens and `App.kt` entry point. Uses `:shared` for data types.
- `androidMain` — `MainActivity` wraps `App()` with `setContent`.
- `iosMain` — `MainViewController()` bridges to `ComposeUIViewController`.
- `wasmJsMain` — `main()` calls `CanvasBasedWindow("Monedero") { App() }`. The HTML shell is at `wasmJsMain/resources/index.html`.

Ktor HTTP engine is platform-specific: `ktor-client-android` for Android, `ktor-client-darwin` for iOS, `ktor-client-js` for wasmJs.

### server module

JVM-only Ktor application on Netty, port 8080.

`Application.kt` wires four plugins in order: CORS → Serialization → Monitoring → Routing.

- `plugins/` — one file per Ktor plugin (`CORS.kt`, `Serialization.kt`, `Monitoring.kt`, `Routing.kt`).
- `routes/WalletRoutes.kt` — REST endpoints under `/api/wallets`. Currently uses an in-memory `mutableListOf` — replace with a real database when persistence is needed.
- `/health` endpoint returns `"OK"` for liveness checks.

### Version catalog

All dependency versions are centralized in `gradle/libs.versions.toml`. Add new dependencies there and reference them via `libs.*` aliases in build files — never hardcode version strings in `build.gradle.kts` files.

## Key conventions

- **New REST endpoints** go in `server/src/main/kotlin/.../routes/` as extension functions on `Route`, then registered in `plugins/Routing.kt`.
- **New shared models** go in `shared/src/commonMain/.../shared/model/` and must be annotated with `@Serializable`.
- **Platform-specific Ktor engine wiring** belongs in each `composeApp` source set's dependency block, not in `commonMain`.
- The iOS Xcode project (`iosApp/iosApp.xcodeproj`) references the `ComposeApp` XCFramework at `composeApp/build/XCFrameworks/debug/ComposeApp.xcframework`. Always run `./gradlew :composeApp:assembleDebugXCFramework` before building the iOS app — the Xcode build will fail if the framework is missing.
