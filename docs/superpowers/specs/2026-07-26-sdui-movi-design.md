# SDUI de contenido para Movi — diseño (F1)

**Fecha:** 2026-07-26
**Alcance:** `:core` (schema wire + repo), `:server` (tabla + seed + ruta), `:shared`
(renderer + Dashboard piloto con fallback). Porta a movi el patrón probado y mergeado en
NeoVita (PR jvillad1/NeoVita#7) con adaptaciones deliberadas.

## Problema / valor

El Dashboard de movi está hardcodeado en Compose; cambiar secciones/tarjetas/orden exige
rebuild wasm + Docker + deploy (~15 min). Con SDUI, la pantalla se define en
`screen_definitions` (Postgres) y un cambio es un UPDATE: visible al refrescar la PWA,
sin build ni deploy.

## Decisiones (locked)

- **Piloto: Dashboard.** Flujos (login, quickadd, extractos, etc.) siguen nativos.
- **Definiciones en DB, sin editor en F1** (movi no tiene roles admin): edición por
  SQL/seed; editor sería F2.
- **SIN cache local** (adaptación vs NeoVita): la superficie real de movi es la PWA
  (wasm, sin SQLDelight) y la UI nativa se retira con el ciclo sensor-app. F1: fetch +
  memoria + fallback. (El endpoint conserva el 304 por versión igualmente — barato y
  útil para la PWA.)
- **Tipos de sección movi (schema cerrado en `:core`, patrón NeoVita):**
  `HERO_BALANCE` (smart: balance/ingresos/egresos del estado real) ·
  `ACCOUNTS_SUMMARY` (smart: cuentas del estado) · `CARD_ROW` · `CARD_LIST` ·
  `LINK_LIST` (filas título→acción, para los atajos) · `BANNER` (texto + acción
  opcional, para alertas/banner IA).
- **Acciones whitelist:** `NAVIGATE` con target en
  `["dashboard","transactions","quickadd","budgets","mas","accounts","credits","goals",
  "investments","subscriptions","recurrentes","analisis","extractos","aichat","profile"]`
  (mapa 1:1 al `sealed class Screen` existente) y `OPEN_URL` solo `https://`.
- **Tres capas anti-rotura (heredadas de NeoVita, con sus lecciones):**
  1. `renderableSections` filtra tipos desconocidos y strippea acciones inválidas
     (`type` String, tolerancia en el filtro, NO en la deserialización) + el cliente
     de movi ya usa Json tolerante (verificar `ignoreUnknownKeys` en la config del
     HttpClient de `:core`; si no está, agregarlo — lección NeoVita).
  2. Última definición válida retenida en memoria durante la sesión.
  3. `DashboardFallback` = el Dashboard actual byte-idéntico (solo renombrado), y el
     **top bar (avatar/nombre/campana) + bottom nav quedan como chrome nativo** en ambos
     caminos (lección del saludo perdido de NeoVita: el chrome no viaja en el schema).
- **Seed fiel:** slug `dashboard` v1 replicando las secciones actuales: HERO_BALANCE →
  ACCOUNTS_SUMMARY → BANNER "Alertas"/"Sin alertas por ahora" → LINK_LIST "Explora"
  (Inversiones/Créditos/Metas + Suscripciones como demostración del valor: hoy NO está
  en el dashboard y el seed la agrega sin tocar UI) → BANNER IA ("✦ Pregúntale a Movi
  AI", NAVIGATE aichat). Día 1: visualmente equivalente + una mejora visible.
- **Ruta:** `GET /api/screens/{slug}` autenticada, 200/304 (If-None-Match == version)/
  404 (inexistente o inactiva). Tabla `screen_definitions(slug PK, version, sections_json,
  active, updated_at)` + `seedIfEmpty` **por-slug** (lección del deferido NeoVita: una
  pantalla nueva en el seed debe llegar a deploys existentes).
- `sections_json` corrupto en DB → la ruta responde 404 + warn (no 500) — lección del
  deferido NeoVita.

## Testing

- Unit `:core`: (de)serialización, tipo desconocido tolerado+filtrado, acciones
  inválidas strippeadas (portar los tests de NeoVita adaptados a los tipos movi).
- HTTP (harness H2 existente de movi, patrón CreditRoutesTest): 200 con seed (6
  secciones), 304, 404 slug malo, 404 con sections_json corrupto, 401 sin auth, seed
  por-slug idempotente (edición sobrevive re-seed; slug nuevo en seed SÍ se agrega con
  tabla no vacía).
- Compile: `:core` + `:shared` (wasm y android) + `:server:test` completo. NO `build`
  completo (OOM iOS release ya conocido… mitigado por el gate de #15, pero innecesario).
- E2E: server local + Postgres nativo; UPDATE por SQL del sections_json → GET refleja
  versión nueva; reinicio no pisa la edición. La promesa.

## Fuera de alcance (F2+)

Editor de pantallas en la web UI; más pantallas (Análisis, Más); cache persistente;
secciones smart adicionales; segmentación por usuario.
