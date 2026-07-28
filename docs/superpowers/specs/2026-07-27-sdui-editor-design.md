# Editor de pantallas SDUI (F2) — diseño

**Fecha:** 2026-07-27
**Alcance:** `:core` (métodos de repo), `:server` (gate de admin + PUT/restore validados),
`:shared` (pantalla de editor + entrada). Cierra el bucle de F1: editar el Dashboard
**desde la app**, sin SQL y sin deploy.

## Problema / valor

F1 dejó el Dashboard servido por `screen_definitions`, pero la única forma de editarlo es
`psql`. El editor lo vuelve una operación de producto: reordenar secciones, cambiar
textos y agregar accesos desde la propia app.

## Decisiones (locked)

- **Gate de admin por variable de entorno** (patrón del codebase: RESEND/VAPID):
  `ADMIN_USER_IDS` = ids separados por comas. Resolución: system property
  `movi.admin.userIds` primero (tests), luego env / `server/.env` / `.env` (mismo
  `readEnv` que ya usan `VapidConfig`/`DatabaseFactory`). Sin la variable → NADIE es
  admin: los endpoints de escritura responden 403 y la UI no muestra la entrada.
  Movi no gana un sistema de roles.
- **Las definiciones siguen siendo GLOBALES por slug** (no por usuario): la pantalla es
  chrome de la app, no dato personal. El gate decide quién la edita.
- **La versión la maneja el server**: `PUT` la incrementa (`version + 1`) sobre el valor
  actual leído en la misma transacción; el cliente NUNCA la envía (si la manda, se
  ignora). Esto preserva la semántica del 304 y evita versiones repetidas con contenido
  distinto (deferido de NeoVita atendido).
- **Validación server-side estricta (la red de seguridad en el origen):** `PUT` responde
  422 si (a) alguna sección tiene `type` fuera de `ScreenTaxonomy.SECTION_TYPES`; (b)
  alguna acción es inválida (`type` fuera de ACTION_TYPES, NAVIGATE con target fuera de
  la whitelist, OPEN_URL que no empieza con `https://`); (c) **la definición no tiene
  secciones renderizables** (`renderableSections(...)` vacío) — el escenario que dejó el
  home en blanco en la revisión de F1 se vuelve imposible de guardar. Los mensajes de
  error son legibles (qué sección/qué acción).
- **`POST /api/screens/{slug}/restore`**: reescribe la definición desde `SCREEN_SEED`
  (mismo contenido del seed original), incrementando versión. Recuperación de un toque.
  404 si el slug no existe en el seed.
- **Alcance del editor v1:** reordenar (subir/bajar), eliminar y agregar secciones
  (selector de tipo); editar por tipo: `BANNER` (title?/text + acción opcional en su
  card única — el idiom `cards[0].action` documentado en F1), `LINK_LIST`/`CARD_ROW`/
  `CARD_LIST` (lista de tarjetas: título, subtítulo?, badge?, acción), `HERO_BALANCE`/
  `ACCOUNTS_SUMMARY` (sin campos: solo posición). **Sin preview en vivo** (guardas y el
  Dashboard ya refleja) y **sin edición de JSON crudo**.
- **Acciones en el editor:** selector de tipo (Navegar / Abrir enlace / Ninguna) +
  selector de destino con los 15 targets de la whitelist (etiquetas legibles) o campo de
  URL. Imposible construir una acción inválida desde la UI; el server valida igual.

## Diseño

### Server

1. `AdminConfig` (`server/.../admin/AdminConfig.kt`): `fun adminIds(): Set<String>`,
   `fun isAdmin(uid: String): Boolean` (false si la config está vacía).
2. En `screenRoutes()`, dentro del bloque autenticado ya existente:
   - `PUT /api/screens/{slug}` — body `ScreenDefinition` (se ignoran `slug`/`version` del
     body; mandan la ruta y el server). 403 si no admin; 404 si el slug no existe; 422 si
     la validación falla; 200 con la definición guardada (versión nueva) si OK.
   - `POST /api/screens/{slug}/restore` — 403/404/200 con la definición del seed.
   - `GET /api/screens/admin/status` — `{"isAdmin": true|false}` para que la UI decida si
     muestra la entrada (evita exponer el listado de admins).
3. Validación pura en `server/.../screens/ScreenValidation.kt`:
   `fun validateDefinition(sections: List<ScreenSection>): String?` (null = válida; si no,
   el mensaje del primer problema). Reusa `ScreenTaxonomy` y `renderableSections`.

### Cliente

4. Repo (`:core`): `putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition`,
   `restoreScreen(slug: String): ScreenDefinition`, `isScreenAdmin(): Boolean`.
5. `ScreenEditorScreen` (`shared/.../ui/sdui/editor/`): carga la definición actual, lista
   de secciones con controles (↑ ↓ ✕), botón "Agregar sección", edición inline por tipo,
   botones "Guardar" (PUT) y "Restaurar original" (con confirmación). Errores 422 del
   server se muestran tal cual (son legibles). Entrada: ítem "Editor de pantallas" en
   `MasScreen`, visible solo si `isScreenAdmin()` devolvió true.

## Testing

- **Unit (server):** `validateDefinition` — válida; tipo desconocido; NAVIGATE fuera de
  whitelist; OPEN_URL http; secciones vacías; secciones todas desconocidas (renderizables
  vacío).
- **HTTP (harness H2, patrón ScreenRoutesTest):** PUT sin admin → 403; PUT admin válido →
  200 y `version` = anterior+1 y el GET siguiente lo refleja; PUT inválido → 422 (uno por
  cada causa); PUT slug inexistente → 404; restore → 200, contenido == seed, versión
  incrementada; `admin/status` true/false según la property.
- **Compile:** `:shared` android + wasm; `:server:test` completo.
- **E2E:** server local; editar el Dashboard **desde la web** (agregar una sección BANNER,
  reordenar, guardar) → recargar → el Dashboard muestra el cambio; "Restaurar original" lo
  devuelve al seed. La prueba de que el bucle quedó cerrado sin SQL.

## Fuera de alcance (F3)

Preview en vivo; edición de JSON crudo; historial/rollback de versiones; edición de otras
pantallas (el editor ya es genérico por slug, pero solo `dashboard` existe hoy);
permisos por pantalla; segmentación por usuario.
