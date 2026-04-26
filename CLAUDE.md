# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run Ktor server (port 8080)
./gradlew :server:run

# Run web app in browser (Compose/Wasm)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Build all modules
./gradlew build

# Build server fat JAR
./gradlew :server:buildFatJar

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :shared:test
./gradlew :server:test

# Compile-check without running
./gradlew assemble
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
- The iOS Xcode project (`iosApp/`) references the `ComposeApp` framework built by Gradle — always build the framework before opening Xcode: `./gradlew :composeApp:assembleReleaseXCFramework`.
