# SMS en Tiempo Real Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Los SMS bancarios llegan al server en el momento en que aterrizan en el teléfono Android (sin abrir la app), y el usuario recibe una Web Push "toca para confirmar".

**Architecture:** `BroadcastReceiver` de `RECEIVE_SMS` en `androidApp` → `BankSenderFilter` puro (solo SMS bancarios salen del teléfono) → `OneTimeWorkRequest` de WorkManager (red + backoff) → `POST /api/sms/sync` existente (idempotente) con `HttpURLConnection` + `org.json` (cero deps HTTP nuevas). Server: los SMS nuevos parseables disparan una Web Push agrupada (gate VAPID, best-effort).

**Tech Stack:** WorkManager (única dep nueva), Android SDK (`Telephony`, `HttpURLConnection`, `org.json`), Ktor/Exposed server side.

**Spec:** `docs/superpowers/specs/2026-07-24-sms-realtime-design.md`

## Global Constraints

- Branch `feat/sms-realtime` en `/Users/carolinarestrepo/Developer/movi`. JBR 21 ya es JAVA_HOME. NO correr `./gradlew build` completo.
- Filtro de privacidad LOCKED: solo suben SMS con remitente que contenga `85540`, `891333` o `87400`, o cuerpo que contenga `Bancolombia` (case-insensitive). Constantes en UN archivo (`BankSenderFilter`).
- Id determinístico LOCKED: `"sms_rt_" + SHA-256(sender|timestamp|body) hex truncado a 16`.
- Worker: token de `SessionManager.token`; sin token → `Result.failure()` (sin retry); IOException/5xx → `Result.retry()`; 4xx → `Result.failure()`. Única dep nueva: `androidx.work:work-runtime-ktx` (última estable verificada, vía catálogo).
- Hook server: los SMS que NO parsean no generan push; el hook JAMÁS falla el sync (`runCatching` + rethrow de `CancellationException` + warn con throwable — patrón de detect-on-import).
- El cambio temporal de `apiBaseUrl` para el e2e NO se commitea.
- Cada tarea termina verde y con commit.

## File Structure

```
androidApp/build.gradle.kts                                  [M] work-runtime-ktx + kotlin("test")
gradle/libs.versions.toml                                    [M] workmanager
androidApp/src/main/AndroidManifest.xml                      [M] RECEIVE_SMS + <receiver>
androidApp/src/main/kotlin/com/jvillada/movi/sms/BankSenderFilter.kt   [C] filtro + id (puro JVM)
androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsRealtimeReceiver.kt [C] BroadcastReceiver
androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsSyncWorker.kt      [C] CoroutineWorker
androidApp/src/test/kotlin/com/jvillada/movi/sms/BankSenderFilterTest.kt [C] unit
server/.../push/PushPayload.kt                               [M] buildSmsPushPayload
server/.../routes/SmsRoutes.kt                               [M] hook push post-insert
server/test/.../push/PushPayloadTest.kt                      [M] tests del payload SMS
server/test/.../routes/SmsSyncTest.kt                        [M] sync con VAPID on no rompe
```

---

### Task 1: BankSenderFilter + id determinístico (TDD, unit JVM en androidApp)

**Files:**
- Modify: `androidApp/build.gradle.kts` (agregar `testImplementation(kotlin("test"))` al bloque dependencies)
- Create: `androidApp/src/main/kotlin/com/jvillada/movi/sms/BankSenderFilter.kt`
- Test: `androidApp/src/test/kotlin/com/jvillada/movi/sms/BankSenderFilterTest.kt`

**Interfaces:**
- Produces (los usan Tasks 3): `object BankSenderFilter { fun matches(sender: String?, body: String): Boolean }`; `fun smsRealtimeId(sender: String?, timestamp: Long, body: String): String`.

- [ ] **Step 1: Test que falla**

`androidApp/src/test/kotlin/com/jvillada/movi/sms/BankSenderFilterTest.kt`:

```kotlin
package com.jvillada.movi.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankSenderFilterTest {

    @Test
    fun `short codes match regardless of body`() {
        assertTrue(BankSenderFilter.matches("85540", "Compra por 50.000 en EXITO"))
        assertTrue(BankSenderFilter.matches("891333", "cualquier cosa"))
        assertTrue(BankSenderFilter.matches("+5787400", "aviso"))   // contiene 87400
    }

    @Test
    fun `keyword Bancolombia in body matches any sender`() {
        assertTrue(BankSenderFilter.matches("InfoSMS", "Bancolombia: Retiro por 200.000"))
        assertTrue(BankSenderFilter.matches(null, "bancolombia le informa"))
    }

    @Test
    fun `personal messages never match`() {
        assertFalse(BankSenderFilter.matches("+573001234567", "hola, nos vemos a las 7"))
        assertFalse(BankSenderFilter.matches("Claro", "Tu factura llegó"))
        assertFalse(BankSenderFilter.matches(null, ""))
    }

    @Test
    fun `realtime id is deterministic and prefixed`() {
        val a = smsRealtimeId("85540", 1_700_000_000_000, "Compra por 50.000")
        val b = smsRealtimeId("85540", 1_700_000_000_000, "Compra por 50.000")
        val c = smsRealtimeId("85540", 1_700_000_000_001, "Compra por 50.000")
        assertEquals(a, b)
        assertTrue(a != c)
        assertTrue(a.startsWith("sms_rt_"))
        assertEquals("sms_rt_".length + 16, a.length)
    }
}
```

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.jvillada.movi.sms.BankSenderFilterTest"`
Expected: FAIL (unresolved). Si la task `testDebugUnitTest` no existe aún por falta de `testImplementation`, primero agregar la línea al build y re-sincronizar.

- [ ] **Step 2: Implementar**

`BankSenderFilter.kt`:

```kotlin
package com.jvillada.movi.sms

import java.security.MessageDigest

/**
 * Filtro de privacidad (LOCKED en el spec): SOLO los SMS que matchean aquí salen del
 * teléfono. Remitentes cortos de Bancolombia + keyword en el cuerpo. Ampliar estas
 * constantes cuando lleguen SMS reales de otros bancos.
 */
object BankSenderFilter {
    private val SENDER_CODES = listOf("85540", "891333", "87400")
    private const val BODY_KEYWORD = "bancolombia"

    fun matches(sender: String?, body: String): Boolean {
        val s = sender.orEmpty()
        if (SENDER_CODES.any { s.contains(it) }) return true
        return body.lowercase().contains(BODY_KEYWORD)
    }
}

/** Id estable ante re-entregas del broadcast y reintentos del Worker (dedupe extremo a extremo). */
fun smsRealtimeId(sender: String?, timestamp: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${sender.orEmpty()}|$timestamp|$body".toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return "sms_rt_${hex.take(16)}"
}
```

En `androidApp/build.gradle.kts`, dentro de `dependencies {`:

```kotlin
    testImplementation(kotlin("test"))
```

- [ ] **Step 3: Verde + commit**

Run: `./gradlew :androidApp:testDebugUnitTest` → PASS (4 tests).

```bash
git add androidApp
git commit -m "feat(android): BankSenderFilter + id determinístico para SMS en tiempo real"
```

---

### Task 2: buildSmsPushPayload + hook en /api/sms/sync (TDD server)

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/push/PushPayload.kt`
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt`
- Test: `server/src/test/kotlin/com/jvillada/movi/server/push/PushPayloadTest.kt` (agregar tests)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/SmsSyncTest.kt` (agregar 1 test)

**Interfaces:**
- Consumes: `ParsedSms(amount: Double, merchant, type, category)` (core); `parseSms(text)` (internal en SmsRoutes.kt, mismo archivo); `WebPushSender.sendToUser(uid, payloadJson)`; `formatMiles` (privado en PushPayload.kt — reutilizar).
- Produces: `fun buildSmsPushPayload(parsed: List<ParsedSms>): String`.

- [ ] **Step 1: Tests del payload que fallan**

Agregar a `PushPayloadTest.kt`:

```kotlin
    @Test
    fun `single sms movement renders amount and merchant`() {
        val json = buildSmsPushPayload(listOf(ParsedSms(50_000.0, "EXITO COUNTRY", TransactionType.EXPENSE, "Mercado")))
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("Nuevo movimiento", obj["title"]!!.jsonPrimitive.content)
        assertEquals("${'$'}50.000 en EXITO COUNTRY — toca para confirmar", obj["body"]!!.jsonPrimitive.content)
        assertEquals("/", obj["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `multiple sms movements group with suffix`() {
        val parsed = (1..4).map { ParsedSms(10_000.0 * it, "Comercio $it", TransactionType.EXPENSE, "Otros") }
        val obj = Json.parseToJsonElement(buildSmsPushPayload(parsed)).jsonObject
        assertEquals("4 movimientos nuevos", obj["title"]!!.jsonPrimitive.content)
        val lines = obj["body"]!!.jsonPrimitive.content.split("\n")
        assertEquals(4, lines.size)
        assertEquals("${'$'}10.000 en Comercio 1", lines[0])
        assertEquals("…y 1 más", lines[3])
    }
```

(Import nuevo: `com.jvillada.movi.shared.model.ParsedSms`.)

Run: `./gradlew :server:test --tests "com.jvillada.movi.server.push.PushPayloadTest"` → FAIL (unresolved `buildSmsPushPayload`).

- [ ] **Step 2: Implementar el payload**

Agregar a `PushPayload.kt`:

```kotlin
/** Push para SMS bancarios recién capturados. Los montos de SMS COP son enteros → roundToLong. */
fun buildSmsPushPayload(parsed: List<ParsedSms>): String {
    val lines = parsed.take(MAX_LINES).map { "$${formatMiles(it.amount.roundToLong())} en ${it.merchant}" }
    val extra = parsed.size - MAX_LINES
    val allLines = lines + if (extra > 0) listOf("…y $extra más") else emptyList()
    val single = parsed.size == 1
    val body = if (single) "${allLines.first()} — toca para confirmar" else allLines.joinToString("\n")
    return buildJsonObject {
        put("title", if (single) "Nuevo movimiento" else "${parsed.size} movimientos nuevos")
        put("body", body)
        put("url", "/")
    }.toString()
}
```

(Imports nuevos: `com.jvillada.movi.shared.model.ParsedSms`, `kotlin.math.roundToLong`.)

Run el test → PASS.

- [ ] **Step 3: Hook en el sync**

En `SmsRoutes.kt`, handler `post("/api/sms/sync")`: leerlo completo primero. Modificar el loop de inserción para recolectar los nuevos, y agregar el hook entre el fin del `dbQuery` de inserción y el `call.respond(...)`:

```kotlin
        // (dentro del dbQuery existente, junto al loop) — recolectar los realmente insertados:
        val inserted = mutableListOf<SmsMessage>()
        // ... en el loop, tras el insert de cada msg nuevo: inserted += msg

        // Hook de push (spec sms-realtime): SMS nuevos parseables → una push agrupada.
        // Best-effort — jamás falla el sync; los que no parsean quedan en el inbox como siempre.
        if (inserted.isNotEmpty() && WebPushSender.isConfigured()) {
            runCatching {
                val parsed = inserted.mapNotNull { parseSms(it.text) }
                if (parsed.isNotEmpty()) WebPushSender.sendToUser(uid, buildSmsPushPayload(parsed))
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                call.application.log.warn("push de sms-sync falló para $uid", it)
            }
        }
```

Adaptar al código real del handler (nombres de variables del loop); la lista `inserted` debe salir del `dbQuery` (declararla fuera o devolverla). Imports: `WebPushSender`, `buildSmsPushPayload`, `io.ktor.server.application.log` si falta.

- [ ] **Step 4: Test de no-ruptura en SmsSyncTest**

Agregar a `SmsSyncTest.kt` (leyendo su harness primero; agregar set/clear de las properties VAPID como en `PushRoutesTest`):

```kotlin
    @Test
    fun `sync with push configured but no subscriptions still succeeds`() = testApplication {
        System.setProperty("movi.vapid.public", "test-pub")
        System.setProperty("movi.vapid.private", "test-priv")
        try {
            // wiring + POST /api/sms/sync con 2 mensajes: uno parseable
            // ("Bancolombia: Compra por $50.000 en EXITO") y uno no ("hola") —
            // usar los helpers/formatos del propio harness para construir el body.
            // Assert: 200 y synced == 2 (el hook corre con 0 suscripciones y no rompe).
        } finally {
            System.clearProperty("movi.vapid.public")
            System.clearProperty("movi.vapid.private")
        }
    }
```

El comentario describe la intención; el cuerpo real se escribe con los helpers concretos del harness de ese archivo (que este plan no reproduce porque el archivo ya existe — leerlo). El assert obligatorio es: status 200 y `synced` incluye ambos mensajes.

- [ ] **Step 5: Suite completa + commit**

Run: `./gradlew :server:test` → todo verde, cero regresiones.

```bash
git add server/src
git commit -m "feat(server): push de confirmación al capturar SMS nuevos en sync"
```

---

### Task 3: Receiver + Worker + manifest (androidApp)

**Files:**
- Modify: `gradle/libs.versions.toml` (+ `workmanager` — verificar última estable de `androidx.work:work-runtime-ktx`, referencia 2.9.1)
- Modify: `androidApp/build.gradle.kts` (+ `implementation(libs.androidx.work.runtime.ktx)`)
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsRealtimeReceiver.kt`
- Create: `androidApp/src/main/kotlin/com/jvillada/movi/sms/SmsSyncWorker.kt`

**Interfaces:**
- Consumes: `BankSenderFilter.matches`, `smsRealtimeId` (Task 1); `SessionManager.token` (shared); `apiBaseUrl` (`com.jvillada.movi.data`, actual de androidMain en :shared).

- [ ] **Step 1: Catálogo + dep**

`[versions]`: `workmanager = "2.9.1"` (verificar última estable). `[libraries]`:
`androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }`.
`androidApp/build.gradle.kts`: `implementation(libs.androidx.work.runtime.ktx)`.

- [ ] **Step 2: Manifest**

Junto a `READ_SMS`: `<uses-permission android:name="android.permission.RECEIVE_SMS" />`.
Dentro de `<application>`, después de la activity:

```xml
        <receiver
            android:name="com.jvillada.movi.sms.SmsRealtimeReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Receiver**

`SmsRealtimeReceiver.kt`:

```kotlin
package com.jvillada.movi.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Captura SMS bancarios en el momento en que llegan y los encola para subir al server.
 * El filtro corre AQUÍ: los SMS que no matchean jamás salen del teléfono.
 */
class SmsRealtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multiparte: agrupar por remitente y concatenar cuerpos en orden
        val bySender = messages.filterNotNull().groupBy { it.originatingAddress }
        for ((sender, parts) in bySender) {
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            if (body.isBlank() || !BankSenderFilter.matches(sender, body)) continue
            val ts = parts.first().timestampMillis
            val id = smsRealtimeId(sender, ts, body)
            val work = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(workDataOf("id" to id, "sender" to (sender ?: ""), "body" to body, "ts" to ts))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Unique por id: re-entregas del broadcast no encolan duplicados
            WorkManager.getInstance(context).enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, work)
        }
    }
}
```

- [ ] **Step 4: Worker**

`SmsSyncWorker.kt`:

```kotlin
package com.jvillada.movi.sms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.apiBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sube UN SMS bancario capturado al endpoint idempotente /api/sms/sync.
 * Sin token (deslogueado) → failure sin retry. IOException/5xx → retry con backoff.
 */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = SessionManager.token
        if (token.isNullOrBlank()) {
            Log.w(TAG, "sin sesión — descartando SMS capturado")
            return@withContext Result.failure()
        }
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val sender = inputData.getString("sender").orEmpty()
        val body = inputData.getString("body").orEmpty()
        val ts = inputData.getLong("ts", System.currentTimeMillis())

        val payload = JSONArray().put(
            JSONObject()
                .put("id", id)
                .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts)))
                .put("bank", sender.ifBlank { "SMS" })
                .put("text", body)
                .put("state", "new")
                .put("det", "")
        ).toString()

        try {
            val conn = URL("$apiBaseUrl/api/sms/sync").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            when {
                code in 200..299 -> Result.success()
                code >= 500 -> Result.retry()
                else -> {
                    Log.w(TAG, "sync rechazado con $code — sin retry")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            Result.retry()
        }
    }

    private companion object { const val TAG = "movi" }
}
```

Nota: si `SessionManager.token` o `apiBaseUrl` no resuelven desde androidApp con esos
imports exactos, verificar el paquete real en `:shared` (`com.jvillada.movi.data`) y
ajustar el import — no duplicar valores.

- [ ] **Step 5: Compilar + tests + commit**

Run: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest` → BUILD SUCCESSFUL.

```bash
git add gradle/libs.versions.toml androidApp
git commit -m "feat(android): receiver de SMS en tiempo real + Worker de sync con WorkManager"
```

---

### Task 4: E2E en emulador

**Files:** ninguno commiteado (el cambio de `apiBaseUrl` se revierte).

- [ ] **Step 1: Preparar entorno**

1. `pg_isready -h localhost` (arrancar Postgres si hace falta) y `./gradlew :server:run` en background; esperar `/health` → OK.
2. Cambio TEMPORAL (no commitear): en `shared/src/androidMain/kotlin/com/jvillada/movi/data/Platform.android.kt`, `apiBaseUrl = "http://10.0.2.2:8080"`.
3. Emulador: `"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_Pro -no-snapshot-load > /tmp/emulator.log 2>&1 &` y esperar `adb shell getprop sys.boot_completed` → 1.
4. `./gradlew :androidApp:assembleDebug && adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

- [ ] **Step 2: Sesión sin UI (token inyectado)**

1. Registrar usuario desechable vía curl al server local (`verify-smsrt-<ts>@movi.test`) → capturar token.
2. Arrancar la app una vez (`adb shell am start -n com.jvillada.movi/.MainActivity`) para que cree sus prefs, luego `adb shell am force-stop com.jvillada.movi`.
3. Inyectar el token (build debug permite run-as):

```bash
adb shell run-as com.jvillada.movi sh -c 'cat > shared_prefs/com.jvillada.movi_preferences.xml' <<EOF
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<map>
    <string name="auth_token">TOKEN_AQUI</string>
    <string name="user_id">USER_ID_AQUI</string>
</map>
EOF
```

(Verificar el nombre real del archivo de prefs con `adb shell run-as com.jvillada.movi ls shared_prefs/` — multiplatform-settings no-arg usa `<applicationId>_preferences.xml` por defecto; si difiere, usar el que exista.)

4. Conceder permisos SMS: `adb shell pm grant com.jvillada.movi android.permission.READ_SMS && adb shell pm grant com.jvillada.movi android.permission.RECEIVE_SMS`.

- [ ] **Step 3: Los tres escenarios**

1. **Bancario por remitente:** `adb emu sms send 85540 "Bancolombia: Compra por \$50.000 en EXITO COUNTRY 12:00"` → esperar ~20s → `curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/sms` debe contener el texto (el Worker corrió SIN abrir la app).
2. **Bancario por keyword:** `adb emu sms send InfoSMS "Bancolombia le informa retiro por 200.000"` → aparece.
3. **Personal:** `adb emu sms send +573001112233 "hola nos vemos"` → NO aparece (privacidad).

Si el Worker no dispara: `adb logcat -s movi WM-WorkerWrapper` para diagnóstico. Si hay un defecto real: BLOCKED, no arreglar.

- [ ] **Step 4: Limpieza + push**

```bash
git checkout shared/src/androidMain/kotlin/com/jvillada/movi/data/Platform.android.kt
adb shell am force-stop com.jvillada.movi
pkill -f "com.jvillada.movi.server"; pkill -f ":server:run"
adb emu kill
git push -u origin feat/sms-realtime
```

Reporte con cada comando y salida.
