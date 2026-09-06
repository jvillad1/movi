# AGENTS.md

This repo's canonical, actively-maintained agent guidance lives in [`CLAUDE.md`](CLAUDE.md) — read
it. This file exists only for agents/tools that follow the [agents.md](https://agents.md) convention
and don't auto-load `CLAUDE.md`; it mirrors the load-bearing facts so those tools aren't flying
blind, but `CLAUDE.md` is the one that gets updated first. If the two ever disagree, `CLAUDE.md` wins.

## The essentials

- **Movi** — Kotlin Multiplatform personal finance app. Modules: `:core` (models/repos/SQLDelight,
  shared by server and clients), `:shared` (Compose UI library), `:androidApp`, `:webApp` (wasmJs),
  `:server` (Ktor), `:iosApp` (Swift shell).
- **Build with JetBrains Runtime 21**, not a plain OpenJDK — the Gradle daemon JVM is pinned to it.
- **Test with exactly this command** (it's what CI runs, nothing more):
  ```bash
  ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs
  ```
- **Never run `./gradlew build`** — it pulls in iOS release link tasks and takes ~48 minutes.
- **All user-visible text is neutral Latin American Spanish (tuteo, no voseo)** — enforced by
  `VoseoScanTest` in `core/src/jvmTest` (run with `--rerun-tasks`, since Gradle's cache can mask it).
- **New REST endpoints** go in `server/src/main/kotlin/.../routes/` as extension functions on
  `Route`, registered in `plugins/Routing.kt`. **New shared models** go in
  `core/src/commonMain/.../shared/model/`, `@Serializable`.
- Server config is env vars via `server/.env` (gitignored) — copy `server/.env.example` to start.
- Railway auto-deploys `master` on push. A merge landing is not proof it deployed — check `/version`
  against the commit SHA before claiming something is live.

For the full module architecture, CI/deploy internals, terminal-only Android/iOS build steps, and
key conventions, see [`CLAUDE.md`](CLAUDE.md). For a human-facing quick start, see
[`README.md`](README.md).
