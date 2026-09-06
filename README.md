# Movi

Personal and family finance management app — accounts, expenses, credits/installments,
subscriptions, budgets, and bank-SMS capture, used daily with real money.

One Kotlin codebase, four clients (Android, iOS, Web, and a shared UI library) and a Ktor server,
built as a single [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) project.

## Stack

| Layer | Tech |
| --- | --- |
| Shared logic & models | Kotlin Multiplatform, `kotlinx.serialization`, SQLDelight |
| UI | Compose Multiplatform (Android, iOS, Web/wasmJs) |
| Server | Ktor (Netty), Exposed, HikariCP, Postgres |
| Deploy | Docker on Railway, auto-deploy on push to `master` |
| CI | GitHub Actions — 4 test suites + a deploy-config guard, on every PR |

## Project layout

```
movi/
├── core/         Pure Kotlin multiplatform — models + repositories + SQLDelight. Shared by server AND clients.
├── shared/       Compose Multiplatform UI library — Android, iOS, Web. Produces the iOS XCFramework.
├── androidApp/   Android application (MainActivity host). Depends on :shared + :core.
├── webApp/       wasmJs browser application. Depends on :shared.
├── server/       Ktor JVM backend. Depends on :core.
└── iosApp/       Swift shell that embeds the :shared XCFramework.
```

See [`CLAUDE.md`](CLAUDE.md) for the full module breakdown, architecture notes, and conventions —
that file is the canonical technical reference and is kept up to date on every change.

## Getting started

### Prerequisites

- **JetBrains Runtime (JBR) 21** — required to build. `gradle/gradle-daemon-jvm.properties` pins
  `toolchainVendor=jetbrains`; a plain OpenJDK will not satisfy the daemon JVM criteria.
- **Postgres** — local server needs a database (Homebrew `postgresql@16` works; `docker-compose.yml`
  is also available if you prefer a container).
- Android SDK / Xcode only if you're building those clients.

### Server

```bash
# 1. Create server/.env from the template and fill in DATABASE_URL + JWT_SECRET (both required)
cp server/.env.example server/.env

# 2. Run it (port 8080)
./gradlew :server:run
```

`ANTHROPIC_API_KEY` and `RESEND_API_KEY` are optional — without them, AI chat and payment-reminder
emails stay disabled but everything else works. See `server/.env.example` for the full list.

### Web

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

### Android / iOS

See [`CLAUDE.md`](CLAUDE.md#android-from-terminal-no-android-studio) for the terminal-only build/run
steps (no Android Studio or Xcode UI required).

## Testing

```bash
./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs
```

This is exactly what CI runs on every PR — nothing more, nothing less. **Never run
`./gradlew build`**: it drags in iOS release link tasks and takes about 48 minutes for no benefit
over the command above.

## Deployment

Railway auto-deploys `master` on every push (Dockerfile build, no manual `railway up` needed). A
merge is not proof of a deploy: `/version` returns the running commit SHA, and
`.github/workflows/despliegue.yml` polls it after every push to master and fails the workflow if the
new commit never comes up — Railway silently keeps serving the old build on a failed deploy
otherwise.

## Working with AI coding agents

[`CLAUDE.md`](CLAUDE.md) is the canonical, detailed reference (architecture, conventions, commands,
CI/deploy internals). [`AGENTS.md`](AGENTS.md) points there for tools that don't auto-load
`CLAUDE.md`.
