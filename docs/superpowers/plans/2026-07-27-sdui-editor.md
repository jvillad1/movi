# Editor de Pantallas SDUI (F2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Editar el Dashboard desde la app — reordenar/agregar/eliminar secciones y textos — con validación estricta server-side, sin SQL y sin deploy.

**Architecture:** Gate de admin por env var (patrón VAPID/RESEND, sin roles); `PUT /api/screens/{slug}` + `POST .../restore` + `GET /api/screens/admin/status` protegidos por ese gate; validación pura que rechaza tipos/acciones inválidas y definiciones sin secciones renderizables (el bug de F1, imposible de guardar); pantalla de editor en `:shared` con formularios por tipo y selectores que no pueden construir acciones inválidas.

**Tech Stack:** Ktor/Exposed (harness H2), kotlinx.serialization, Compose Multiplatform (`Min*`).

**Spec:** `docs/superpowers/specs/2026-07-27-sdui-editor-design.md` (fuente de verdad de decisiones y mensajes).

## Global Constraints

- Branch `feat/sdui-editor` en `/Users/carolinarestrepo/Developer/movi`. JBR 21 ya es JAVA_HOME. NO `./gradlew build` completo.
- Config admin: property `movi.admin.userIds` PRIMERO (tests), luego env `ADMIN_USER_IDS` / `server/.env` / `.env` (copiar el `readEnv` de `server/.../push/VapidConfig.kt`). Vacía → nadie es admin (403 en escrituras).
- La **versión la asigna el server** (`actual + 1`), leída y escrita en la MISMA transacción; el `version` del body se ignora.
- `PUT` responde **422** ante: tipo de sección desconocido · acción inválida (tipo, target fuera de whitelist, OPEN_URL no-https) · `renderableSections(...)` vacío. Mensajes legibles en español.
- Las definiciones siguen siendo globales por slug; sin cambios de schema.
- Cada tarea verde + commit. Harness HTTP: patrón `server/src/test/.../routes/ScreenRoutesTest.kt` (ya existe, léelo).

## File Structure

```
server/.../admin/AdminConfig.kt                    [C] adminIds/isAdmin (property→env→.env)
server/.../screens/ScreenValidation.kt             [C] validateDefinition(sections): String?
server/.../routes/ScreenRoutes.kt                  [M] PUT, POST restore, GET admin/status
server/test/.../screens/ScreenValidationTest.kt    [C] unit (6 casos)
server/test/.../routes/ScreenEditorRoutesTest.kt   [C] HTTP (8 casos)
core/.../shared/repository/WalletRepository(.Impl/Local/NoOp) [M] putScreen/restoreScreen/isScreenAdmin
shared/.../ui/sdui/editor/ScreenEditorScreen.kt    [C] editor
shared/.../ui/Navigation.kt                        [M] Screen.ScreenEditor
shared/.../App.kt                                  [M] rama
shared/.../ui/mas/MasScreen.kt                     [M] ítem condicionado a isScreenAdmin()
```

---

### Task 1: Validación + gate de admin + endpoints (TDD)

**Files:** los 5 primeros del File Structure.

**Interfaces (produce):**
- `object AdminConfig { fun adminIds(): Set<String>; fun isAdmin(uid: String): Boolean }`
- `fun validateDefinition(sections: List<ScreenSection>): String?` — null si válida.
- `PUT /api/screens/{slug}` (auth + admin): body `ScreenDefinition`; ignora slug/version del body; 403 no-admin / 404 slug inexistente / 422 inválida / 200 `ScreenDefinition` guardada.
- `POST /api/screens/{slug}/restore` (auth + admin): 403 / 404 (slug no está en `SCREEN_SEED`) / 200 definición del seed con versión incrementada.
- `GET /api/screens/admin/status` (auth): `{"isAdmin": Boolean}`.

- [ ] **Step 1: Unit de validación que falla**

`server/src/test/kotlin/com/jvillada/movi/server/screens/ScreenValidationTest.kt` — 6 tests:

```kotlin
package com.jvillada.movi.server.screens

import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenSection
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenValidationTest {
    private fun banner(text: String = "hola") = ScreenSection(type = "BANNER", text = text)

    @Test fun `valid definition passes`() {
        assertNull(validateDefinition(listOf(ScreenSection(type = "HERO_BALANCE"), banner())))
    }

    @Test fun `unknown section type is rejected`() {
        val msg = validateDefinition(listOf(banner(), ScreenSection(type = "HOLOGRAM_3D")))
        assertNotNull(msg); assertTrue(msg.contains("HOLOGRAM_3D"))
    }

    @Test fun `navigate outside whitelist is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("NAVIGATE", "settings"))))))
        assertNotNull(msg); assertTrue(msg.contains("settings"))
    }

    @Test fun `non-https open_url is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("OPEN_URL", "http://inseguro"))))))
        assertNotNull(msg); assertTrue(msg.contains("https"))
    }

    @Test fun `unknown action type is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("EXPLODE", "x"))))))
        assertNotNull(msg); assertTrue(msg.contains("EXPLODE"))
    }

    @Test fun `empty definition is rejected`() {
        assertNotNull(validateDefinition(emptyList()))
    }
}
```

Run: `./gradlew :server:test --tests "*.ScreenValidationTest"` → RED.

- [ ] **Step 2: Implementar validación + AdminConfig**

`ScreenValidation.kt`:

```kotlin
package com.jvillada.movi.server.screens

import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.ScreenTaxonomy
import com.jvillada.movi.shared.model.renderableSections

/**
 * Valida una definición ANTES de persistirla: el editor no puede guardar algo que el
 * renderer no sepa dibujar. Cierra en el origen el escenario que en F1 dejaba el
 * dashboard en blanco (definición "válida" cuyas secciones se filtran todas).
 * Devuelve null si es válida, o el mensaje del primer problema encontrado.
 */
fun validateDefinition(sections: List<ScreenSection>): String? {
    if (sections.isEmpty()) return "La pantalla debe tener al menos una sección"
    for (s in sections) {
        if (s.type !in ScreenTaxonomy.SECTION_TYPES) {
            return "Tipo de sección desconocido: ${s.type}"
        }
        for (c in s.cards) {
            val a = c.action ?: continue
            actionError(a)?.let { return it }
        }
    }
    val renderable = renderableSections(ScreenDefinition(slug = "_", version = 0, sections = sections))
    if (renderable.isEmpty()) return "La pantalla no tiene secciones que se puedan mostrar"
    return null
}

private fun actionError(a: ScreenAction): String? = when (a.type) {
    "NAVIGATE" -> if (a.target in ScreenTaxonomy.NAVIGATE_TARGETS) null
                  else "Destino de navegación inválido: ${a.target}"
    "OPEN_URL" -> if (a.target.startsWith("https://")) null
                  else "Los enlaces deben empezar con https:// — recibido: ${a.target}"
    else -> "Tipo de acción desconocido: ${a.type}"
}
```

`AdminConfig.kt` (copiar el `readEnv` de `VapidConfig.kt`, adaptando claves):

```kotlin
package com.jvillada.movi.server.admin

import java.io.File

/**
 * Quién puede editar pantallas. Movi no tiene roles: la capacidad se habilita por
 * configuración, igual que RESEND/VAPID. Sin config → nadie es admin (403 en escrituras).
 */
object AdminConfig {
    fun adminIds(): Set<String> =
        resolve("movi.admin.userIds", "ADMIN_USER_IDS")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    fun isAdmin(uid: String): Boolean = uid in adminIds()

    private fun resolve(prop: String, envKey: String): String? {
        System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
```

Run el unit → GREEN (6/6).

- [ ] **Step 3: Test HTTP que falla**

`server/src/test/kotlin/com/jvillada/movi/server/routes/ScreenEditorRoutesTest.kt` — harness COPIADO de `ScreenRoutesTest.kt` (misma estructura; H2 propia `screen_editor_test`; el `@BeforeTest` setea `System.setProperty("movi.admin.userIds", userAId)` y el `@AfterTest` la limpia). 8 tests, cuerpos completos con asserts concretos:

1. `PUT sin admin es 403` (token de userB, que NO está en la property).
2. `PUT válido incrementa la versión y persiste` — PUT con 2 secciones válidas → 200; el JSON de respuesta trae `version == 2`; un `GET /api/screens/dashboard` posterior devuelve esas 2 secciones y version 2.
3. `PUT con tipo desconocido es 422` (cuerpo contiene "HOLOGRAM_3D").
4. `PUT con NAVIGATE inválido es 422`.
5. `PUT con OPEN_URL http es 422`.
6. `PUT sin secciones es 422`.
7. `PUT a slug inexistente es 404`.
8. `restore devuelve el seed con versión incrementada` — primero un PUT que cambia todo, luego `POST /api/screens/dashboard/restore` → 200 con las 5 secciones del seed y versión mayor a la del PUT.
9. `admin status refleja la config` — con la property: `{"isAdmin":true}` para A y `false` para B.

Run → RED (404 en las rutas nuevas).

- [ ] **Step 4: Implementar endpoints**

En `ScreenRoutes.kt`, dentro del `route("/api/screens")` existente (el bloque ya está bajo `authenticate("jwt")` en Routing):

```kotlin
        get("/admin/status") {
            call.respond(mapOf("isAdmin" to AdminConfig.isAdmin(call.userId())))
        }

        put("/{slug}") {
            val uid = call.userId()
            if (!AdminConfig.isAdmin(uid)) return@put call.respond(HttpStatusCode.Forbidden, "No autorizado")
            val slug = call.parameters["slug"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing slug")
            val body = call.receive<ScreenDefinition>()
            validateDefinition(body.sections)?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to it))
            }
            val saved = dbQuery {
                val current = Screens.selectAll().where { Screens.slug eq slug }.singleOrNull()
                    ?: return@dbQuery null
                val newVersion = current[Screens.version] + 1
                Screens.update({ Screens.slug eq slug }) {
                    it[sectionsJson] = json.encodeToString(body.sections)
                    it[version] = newVersion
                    it[updatedAt] = System.currentTimeMillis()
                }
                ScreenDefinition(slug = slug, version = newVersion, sections = body.sections)
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(saved)
        }

        post("/{slug}/restore") {
            val uid = call.userId()
            if (!AdminConfig.isAdmin(uid)) return@post call.respond(HttpStatusCode.Forbidden, "No autorizado")
            val slug = call.parameters["slug"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing slug")
            val seed = SCREEN_SEED.firstOrNull { it.slug == slug }
                ?: return@post call.respond(HttpStatusCode.NotFound)
            val saved = dbQuery {
                val current = Screens.selectAll().where { Screens.slug eq slug }.singleOrNull()
                    ?: return@dbQuery null
                val newVersion = current[Screens.version] + 1
                Screens.update({ Screens.slug eq slug }) {
                    it[sectionsJson] = json.encodeToString(seed.sections)
                    it[version] = newVersion
                    it[updatedAt] = System.currentTimeMillis()
                }
                ScreenDefinition(slug = slug, version = newVersion, sections = seed.sections)
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(saved)
        }
```

(Ajustar imports: `AdminConfig`, `validateDefinition`, `SCREEN_SEED`, `receive`, `put`, `post`, `update`, `encodeToString` con el `json` privado que el archivo ya tiene. Verificar el nombre real de la constante del seed leyendo `screens/ScreenSeed.kt`.)

Run: `./gradlew :server:test` → todo verde, cero regresiones (los 7 tests de `ScreenRoutesTest` incluidos).

- [ ] **Step 5: Commit** — `feat(server): endpoints de edición de pantallas con gate de admin y validación estricta`

---

### Task 2: Repo en `:core`

**Files:** `WalletRepository.kt` + `WalletRepositoryImpl.kt` + `LocalRepository.kt` + `NoOpRepository.kt`.

**Interfaces (produce):** `suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition`; `suspend fun restoreScreen(slug: String): ScreenDefinition`; `suspend fun isScreenAdmin(): Boolean`.

- [ ] **Step 1:** Implementar los 3 métodos en los 4 archivos siguiendo el estilo existente de `getScreen`/`putCreditTerms`:

```kotlin
    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition =
        client.put("$baseUrl/api/screens/$slug") {
            contentType(ContentType.Application.Json)
            setBody(ScreenDefinition(slug = slug, version = 0, sections = sections))
        }.body()

    override suspend fun restoreScreen(slug: String): ScreenDefinition =
        client.post("$baseUrl/api/screens/$slug/restore").body()

    override suspend fun isScreenAdmin(): Boolean =
        runCatching {
            client.get("$baseUrl/api/screens/admin/status").body<Map<String, Boolean>>()["isAdmin"] == true
        }.getOrDefault(false)
```

(Local delega; NoOp: `putScreen`/`restoreScreen` devuelven `ScreenDefinition(slug, 1, sections)` / `ScreenDefinition(slug, 1, emptyList())` y `isScreenAdmin` false. Comentario en `putScreen`: la versión del body se ignora — la asigna el server.)

- [ ] **Step 2:** `./gradlew :core:jvmTest :shared:compileDebugKotlinAndroid` → verde. Commit `feat(core): métodos de edición de pantallas en el repositorio`.

---

### Task 3: Pantalla de editor + entrada

**Files:** `shared/.../ui/sdui/editor/ScreenEditorScreen.kt` [C]; `ui/Navigation.kt`, `App.kt`, `ui/mas/MasScreen.kt` [M].

**Interfaces (consume):** Task 2; modelos y `ScreenTaxonomy` de `:core`; componentes `Min*` (leer `CreditTermsSheet.kt` y `SuscripcionesScreen.kt` como referencia de estilo de formularios y listas).

- [ ] **Step 1: Navegación** — `data object ScreenEditor : Screen()` en `Navigation.kt`; rama en `App.kt`; en `MasScreen.kt`, ítem "Editor de pantallas" (icono `Icons.Rounded.Edit` o el que exista en el classpath) mostrado SOLO si un estado `isAdmin` (cargado con `LaunchedEffect(Unit) { isAdmin = runCatching { Repositories.wallets.isScreenAdmin() }.getOrDefault(false) }`) es true.

- [ ] **Step 2: Editor** — `ScreenEditorScreen(onNavigate: (Screen) -> Unit)`:
  - Estado: `sections: List<ScreenSection>` (mutable local), `loading`, `saving`, `error`, `saved`.
  - Carga: `Repositories.wallets.getScreen("dashboard")` → `sections = def.sections`.
  - Lista: por cada sección una `MinCard` con encabezado (nombre legible del tipo: HERO_BALANCE→"Balance", ACCOUNTS_SUMMARY→"Cuentas", CARD_ROW→"Fila de tarjetas", CARD_LIST→"Lista de tarjetas", LINK_LIST→"Lista de enlaces", BANNER→"Aviso") + controles ↑ ↓ ✕ (mover/eliminar en la lista local).
  - Edición por tipo dentro de la card: `BANNER` → campos Título (opcional) y Texto + editor de acción; `CARD_ROW`/`CARD_LIST`/`LINK_LIST` → por cada tarjeta: Título, Subtítulo, Badge + editor de acción, con "+ Agregar tarjeta" y ✕ por tarjeta; `HERO_BALANCE`/`ACCOUNTS_SUMMARY` → texto "Esta sección no tiene campos editables".
  - **Editor de acción** (composable reutilizable): selector Ninguna / Navegar / Enlace; si Navegar → selector con los 15 targets (etiquetas legibles: "dashboard"→"Inicio", "transactions"→"Movimientos", "quickadd"→"Agregar", "budgets"→"Presupuestos", "mas"→"Más", "accounts"→"Cuentas", "credits"→"Créditos", "goals"→"Metas", "investments"→"Inversiones", "subscriptions"→"Suscripciones", "recurrentes"→"Recurrentes", "analisis"→"Análisis", "extractos"→"Extractos", "aichat"→"Movi AI", "profile"→"Perfil"); si Enlace → campo URL con placeholder `https://`. En BANNER la acción se guarda en `cards` como una única `ScreenCard(title = "", action = ...)` (idiom de F1) y se elimina la card si la acción pasa a Ninguna.
  - "Agregar sección" → selector de tipo → agrega al final con contenido vacío del tipo.
  - Botones: **Guardar** → `putScreen("dashboard", sections)`; éxito → mensaje "Guardado" y `sections` = respuesta; error 422 → mostrar el mensaje del server. **Restaurar original** → confirmación → `restoreScreen("dashboard")` → recarga.
  - Nota: tras guardar, `ScreenDefCache.dashboard` queda obsoleto — asignarlo a null (`ScreenDefCache.dashboard = null`) para que el Dashboard re-fetchee al volver.

- [ ] **Step 3:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinWasmJs` → verde. Commit `feat(ui): editor de pantallas SDUI`.

---

### Task 4: E2E — editar sin SQL

- [ ] Server local con `movi.admin.userIds` o `ADMIN_USER_IDS` seteado al id del usuario desechable (registrar primero, capturar id del token/response); `:server:run` con esa env; health OK.
- [ ] Con curl (simulando lo que hace el editor): `GET /api/screens/admin/status` → `{"isAdmin":true}`; `PUT /api/screens/dashboard` con una definición de 2 secciones válidas → 200 version+1; `GET` → refleja; `PUT` con `"type":"HOLOGRAM_3D"` → 422 con mensaje legible; `PUT` con `NAVIGATE`→`settings` → 422; `POST /api/screens/dashboard/restore` → 200 con las 5 secciones del seed.
- [ ] Con un usuario NO admin: `PUT` → 403; `admin/status` → false.
- [ ] Verificación de UI (opcional si el tiempo lo permite): `:webApp:wasmJsBrowserDevelopmentRun` y editar desde la pantalla — si no es viable en la sesión, documentarlo como verificación manual pendiente del usuario.
- [ ] Restaurar la DB al estado del seed (el restore ya lo hace) y dejar constancia; matar server; `git push -u origin feat/sdui-editor`. Reporte completo.
