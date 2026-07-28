# App Sensor (instalación única) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El APK se instala una vez: pantalla única de sensor, filtro de remitentes configurable desde el server (con cache y fallback), y todo lo demás evoluciona por deploy web.

**Architecture:** Ruta pública `GET /api/sms/filter-config` con las constantes del filtro; `SmsFilterConfigStore` en androidApp (SharedPreferences, TTL 24h, fail-open a defaults compilados, cero red en el camino del SMS); `BankSenderFilter.matches` parametrizado por config; `MainActivity` → `SensorScreen` (login mínimo, estado, "Abrir Movi") en lugar de `App()`.

**Tech Stack:** Ktor server, Android SDK (SharedPreferences, HttpURLConnection, org.json), Compose local en androidApp. Cero deps nuevas.

**Spec:** `docs/superpowers/specs/2026-07-26-sensor-app-design.md`

## Global Constraints

- Branch `feat/sensor-app`. JBR 21 ya es JAVA_HOME. NO correr `./gradlew build` completo.
- Config JSON EXACTO: `{"senderCodes": ["85540","891333","87400"], "bodyKeywords": ["bancolombia"]}` (fuente única server; los mismos valores quedan como defaults compilados de fallback).
- El receiver NUNCA hace red: lee config de SharedPreferences síncronamente; sin cache válida → defaults compilados (fail-open a capturar, jamás a ignorar).
- Refresh de config: TTL 24h; se dispara al abrir SensorScreen y tras sync exitoso del Worker; silencioso ante error.
- `SensorScreen` en español, Compose local de androidApp (sin tocar `:shared` UI); "Abrir Movi" = `ACTION_VIEW` a `apiBaseUrl`.
- Cero deps nuevas. Cada tarea verde + commit.

## File Structure

```
server/.../routes/SmsFilterConfigRoutes.kt        [C] ruta pública + constantes fuente
server/.../plugins/Routing.kt                     [M] registrar (fuera de authenticate)
server/test/.../routes/SmsFilterConfigTest.kt     [C] HTTP
androidApp/.../sms/SmsFilterConfigStore.kt        [C] cache/TTL/fetch/parse + defaults
androidApp/.../sms/BankSenderFilter.kt            [M] matches(sender, body, config)
androidApp/.../sms/SmsRealtimeReceiver.kt         [M] usa Store.load(context)
androidApp/.../sms/SmsSyncWorker.kt               [M] last_capture_at + refresh piggyback
androidApp/.../MainActivity.kt                    [M] setContent { SensorScreen() }
androidApp/.../sensor/SensorScreen.kt             [C] pantalla única (login/estado/abrir)
androidApp/src/test/.../sms/SmsFilterConfigStoreTest.kt [C] unit parse/TTL/defaults
androidApp/src/test/.../sms/BankSenderFilterTest.kt     [M] casos con config remota
```

---

### Task 1: Ruta pública de config del filtro (TDD HTTP)

**Files:**
- Create: `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsFilterConfigRoutes.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/plugins/Routing.kt` (junto a `pushPublicRoutes()`, FUERA de authenticate)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/SmsFilterConfigTest.kt`

**Interfaces:**
- Produces: `GET /api/sms/filter-config` → 200 `{"senderCodes":[...],"bodyKeywords":[...]}` sin auth.

- [ ] **Step 1: Test que falla** — harness H2 mínimo (patrón `PushRoutesTest` pero sin usuarios: la ruta es pública y no toca DB; basta `testApplication` + `configureSerialization` + `configureRouting` con el verificador JWT del harness):

```kotlin
    @Test
    fun `filter config is public and returns the bank filter`() = testApplication {
        wireApp()
        val res = client.get("/api/sms/filter-config")   // SIN auth
        assertEquals(HttpStatusCode.OK, res.status)
        val obj = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals(listOf("85540", "891333", "87400"), obj["senderCodes"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("bancolombia"), obj["bodyKeywords"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
```

(El `@BeforeTest` del harness conecta H2 y crea el schema como los demás — copiar de `PushRoutesTest` recortando la siembra de usuarios; la DB no se usa pero `configureRouting` registra rutas que sí la referencian al construirse.)

Run: `./gradlew :server:test --tests "*.SmsFilterConfigTest"` → FAIL (404).

- [ ] **Step 2: Implementar**

```kotlin
package com.jvillada.movi.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Config del filtro de SMS bancarios del APK sensor. Fuente única: editar estas
 * constantes + deploy web = el filtro cambia en los teléfonos SIN reinstalar APK
 * (el receiver la cachea con TTL 24h y fallback a defaults compilados idénticos).
 * Pública a propósito: solo contiene códigos de remitentes bancarios, nada sensible.
 */
@Serializable
private data class SmsFilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

private val CURRENT_FILTER = SmsFilterConfig(
    senderCodes = listOf("85540", "891333", "87400"),
    bodyKeywords = listOf("bancolombia"),
)

fun Route.smsFilterConfigRoutes() {
    get("/api/sms/filter-config") { call.respond(CURRENT_FILTER) }
}
```

Registrar `smsFilterConfigRoutes()` en `Routing.kt` junto a `pushPublicRoutes()`.

- [ ] **Step 3: Verde + suite + commit**

Run: `./gradlew :server:test` → verde. Commit: `feat(server): ruta pública de config del filtro de SMS bancarios`.

---

### Task 2: Config remota en el APK — Store + filtro parametrizado (TDD)

**Files:**
- Create: `androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsFilterConfigStore.kt`
- Modify: `androidApp/src/main/kotlin/com/jvillada/movi/sms/BankSenderFilter.kt`
- Modify: `androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsRealtimeReceiver.kt`
- Modify: `androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsSyncWorker.kt`
- Test: `androidApp/src/test/kotlin/com/jvillada/movi/sms/SmsFilterConfigStoreTest.kt` (nuevo) + `BankSenderFilterTest.kt` (ampliar)

**Interfaces:**
- Produces: `data class FilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)`; `BankSenderFilter.DEFAULTS: FilterConfig`; `BankSenderFilter.matches(sender: String?, body: String, config: FilterConfig = DEFAULTS): Boolean`; `object SmsFilterConfigStore { fun load(context: Context): FilterConfig; fun refreshIfStale(context: Context, force: Boolean = false); fun parseConfigJson(json: String): FilterConfig? /* interno testeable */ }`; clave prefs `movi_sms_filter` (`config_json`, `fetched_at`, `last_capture_at`).

- [ ] **Step 1: Tests que fallan**

`SmsFilterConfigStoreTest.kt` (JVM puro — solo `parseConfigJson` y la lógica de staleness extraída pura):

```kotlin
package com.jvillada.movi.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SmsFilterConfigStoreTest {
    @Test
    fun `valid json parses`() {
        val c = SmsFilterConfigStore.parseConfigJson("""{"senderCodes":["85540","123"],"bodyKeywords":["bancolombia","nequi"]}""")!!
        assertEquals(listOf("85540", "123"), c.senderCodes)
        assertEquals(listOf("bancolombia", "nequi"), c.bodyKeywords)
    }

    @Test
    fun `corrupt or empty json yields null (caller falls back to defaults)`() {
        assertNull(SmsFilterConfigStore.parseConfigJson("not json"))
        assertNull(SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[]}"""))   // sin keywords
        assertNull(SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[],"bodyKeywords":[]}"""))  // vacía = inválida (fail-open)
    }

    @Test
    fun `staleness honors the 24h ttl`() {
        val now = 1_700_000_000_000
        assertFalse(SmsFilterConfigStore.isStale(fetchedAt = now - 23 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = now - 25 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = 0, now = now))
    }
}
```

Ampliar `BankSenderFilterTest.kt`:

```kotlin
    @Test
    fun `remote config can add senders and keywords without reinstalling`() {
        val remote = FilterConfig(senderCodes = listOf("85540", "890123"), bodyKeywords = listOf("bancolombia", "nequi"))
        assertTrue(BankSenderFilter.matches("890123", "cualquier cosa", remote))
        assertTrue(BankSenderFilter.matches("Info", "Nequi: pago recibido", remote))
        assertFalse(BankSenderFilter.matches("890123", "cualquier cosa"))   // defaults no lo conocen
    }
```

Run → FAIL.

- [ ] **Step 2: Implementar**

`BankSenderFilter.kt` — reemplazar el objeto por versión parametrizada (misma semántica con defaults):

```kotlin
data class FilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

object BankSenderFilter {
    /** Idénticos a la fuente del server (GET /api/sms/filter-config) — fallback compilado. */
    val DEFAULTS = FilterConfig(
        senderCodes = listOf("85540", "891333", "87400"),
        bodyKeywords = listOf("bancolombia"),
    )

    fun matches(sender: String?, body: String, config: FilterConfig = DEFAULTS): Boolean {
        val s = sender.orEmpty()
        if (config.senderCodes.any { s.contains(it) }) return true
        val lower = body.lowercase()
        return config.bodyKeywords.any { lower.contains(it.lowercase()) }
    }
}
```

(`smsRealtimeId` no cambia.)

`SmsFilterConfigStore.kt`:

```kotlin
package com.jvillada.movi.sms

import android.content.Context
import com.jvillada.movi.data.apiBaseUrl
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Config remota del filtro con cache local. El camino del SMS (receiver) SOLO lee
 * SharedPreferences — la red vive en refreshIfStale, disparado desde la pantalla del
 * sensor y el Worker. Fail-open: sin cache válida → BankSenderFilter.DEFAULTS.
 */
object SmsFilterConfigStore {
    private const val PREFS = "movi_sms_filter"
    private const val KEY_JSON = "config_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    const val KEY_LAST_CAPTURE_AT = "last_capture_at"
    private const val TTL_MS = 24 * 3_600_000L

    fun load(context: Context): FilterConfig {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        return json?.let { parseConfigJson(it) } ?: BankSenderFilter.DEFAULTS
    }

    fun refreshIfStale(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force && !isStale(prefs.getLong(KEY_FETCHED_AT, 0), System.currentTimeMillis())) return
        thread(name = "sms-filter-refresh") {
            runCatching {
                val conn = URL("$apiBaseUrl/api/sms/filter-config").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                if (parseConfigJson(body) != null) {
                    prefs.edit()
                        .putString(KEY_JSON, body)
                        .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                        .apply()
                }
            }  // silencioso: el fallback compilado cubre
        }
    }

    fun isStale(fetchedAt: Long, now: Long): Boolean = now - fetchedAt > TTL_MS

    fun parseConfigJson(json: String): FilterConfig? = runCatching {
        val obj = JSONObject(json)
        val codes = obj.getJSONArray("senderCodes").let { a -> (0 until a.length()).map { a.getString(it) } }
        val kws = obj.getJSONArray("bodyKeywords").let { a -> (0 until a.length()).map { a.getString(it) } }
        if (codes.isEmpty() && kws.isEmpty()) null else FilterConfig(codes, kws)
    }.getOrNull()
}
```

`SmsRealtimeReceiver.kt`: `BankSenderFilter.matches(sender, body)` → `BankSenderFilter.matches(sender, body, SmsFilterConfigStore.load(context))`.

`SmsSyncWorker.kt`: en el camino de `Result.success()`, antes de retornar:

```kotlin
            applicationContext.getSharedPreferences("movi_sms_filter", Context.MODE_PRIVATE)
                .edit().putLong(SmsFilterConfigStore.KEY_LAST_CAPTURE_AT, System.currentTimeMillis()).apply()
            SmsFilterConfigStore.refreshIfStale(applicationContext)
```

- [ ] **Step 3: Verde + compile + commit**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug` → verde.
Commit: `feat(android): filtro de SMS configurable desde el server (cache TTL + fail-open a defaults)`.

---

### Task 3: SensorScreen — el APK deja de renderizar la app completa

**Files:**
- Create: `androidApp/src/main/kotlin/com/jvillada/movi/sensor/SensorScreen.kt`
- Modify: `androidApp/src/main/kotlin/com/jvillada/movi/MainActivity.kt`

**Interfaces:**
- Consumes: `SessionManager` (loggedIn/token/userEmail/login-vía-repo — leer `shared/.../data/SessionManager.kt` y el repo de auth para las llamadas exactas); `SmsFilterConfigStore.load/refreshIfStale` + `KEY_LAST_CAPTURE_AT`; `apiBaseUrl`.

- [ ] **Step 1: SensorScreen**

Compose local (Material3 básico del classpath de androidApp; SIN componentes de `:shared` UI). Estructura (el implementador escribe el Compose con esta especificación funcional exacta — estados y acciones, estilo libre sobrio oscuro coherente con Movi `#121212`/`#C9B8FF`):

- Header "Movi Sensor" + subtítulo "Captura de SMS bancarios".
- **Card Sesión:** si `SessionManager.loggedIn` → email + botón "Cerrar sesión"; si no → campos email/contraseña + botón "Entrar" (llama al método de login del repositorio existente igual que la pantalla de login de `:shared` — leerla como referencia de llamadas, no de UI).
- **Card Permisos:** estado de `RECEIVE_SMS`/`READ_SMS` (`checkSelfPermission`); si faltan, botón que lanza `requestPermissions`.
- **Card Sensor:** "Última captura: <fecha o 'ninguna aún'>" (de `KEY_LAST_CAPTURE_AT`), remitentes vigentes (`SmsFilterConfigStore.load(context).senderCodes.joinToString()`).
- **Botón primario "Abrir Movi":** `startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apiBaseUrl)))`.
- `LaunchedEffect(Unit) { SmsFilterConfigStore.refreshIfStale(context) }`.

- [ ] **Step 2: MainActivity**

`setContent { App() }` → `setContent { SensorScreen() }` (mantener `enableEdgeToEdge` y `DatabaseDriverFactory.init`; borrar el `@Preview AppAndroidPreview`). El import de `App` se elimina.

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest` → verde.
Commit: `feat(android): MainActivity renderiza SensorScreen — el APK deja de duplicar la UI web`.

---

### Task 4: E2E — la prueba de la promesa

**Files:** ninguno commiteado (cambio temporal de constantes del server local + apiBaseUrl, ambos revertidos).

- [ ] Server local + emulador + APK debug con `apiBaseUrl` → `http://10.0.2.2:8080` (temporal, revertir), token por `run-as` (procedimiento del e2e de sms-realtime, en `.superpowers/sdd/` hay reporte de referencia si sobrevive, si no: registrar throwaway vía curl, arrancar app, force-stop, escribir prefs, permisos con `pm grant`).
- [ ] **Escenario base:** `adb emu sms send 85540 "Bancolombia: Compra por \$10.000 en TEST"` → llega al server (sanity del refactor del filtro).
- [ ] **Escenario promesa:** agregar `"999888"` a `CURRENT_FILTER.senderCodes` SOLO en el server local (edición temporal, revertir) + reiniciar server → en el emulador, abrir SensorScreen (dispara refresh; verificar en las prefs vía `run-as cat` que el JSON cacheado incluye 999888) → `adb emu sms send 999888 "Aviso banco nuevo por 20.000"` → **llega al server SIN reinstalar el APK**.
- [ ] **Escenario fail-open:** limpiar las prefs del filtro (`run-as` borrar el archivo) → mandar SMS de `85540` → sigue capturando (defaults compilados).
- [ ] Limpieza total (revert de apiBaseUrl y constantes del server, matar emulador/server, borrar throwaway) + `git push -u origin feat/sensor-app`.
