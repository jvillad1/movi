package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.SmsMessage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for per-user idempotent POST /api/sms/sync.
 *
 * Uses the same H2 in-memory harness as IsolationTest / ReminderRoutesTest.
 * Separate in-memory DB name ("sms_sync_test") for isolation from other test suites.
 */
class SmsSyncTest {

    private val testSecret = "test-secret-for-sms-sync-tests-minimum-32-chars"
    private val issuer    = "movi"
    private val audience  = "movi-client"

    private val userAId    = "user-a-sms-sync"
    private val userBId    = "user-b-sms-sync"
    private val userAEmail = "a@smssync.test"
    private val userBEmail = "b@smssync.test"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:sms_sync_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, PushSubscriptions,
            )
            // Fresh slate: drop + recreate tables touched by this suite
            SchemaUtils.drop(SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)

            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
    }

    private fun mintToken(userId: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private fun smsClient(builder: io.ktor.server.testing.ApplicationTestBuilder) =
        builder.createClient { install(ContentNegotiation) { json() } }

    // ── Helper SMS factory ─────────────────────────────────────────────────────

    /**
     * `time` por defecto queda en ISO local a propósito: el dedupe por texto+tiempo acepta
     * tanto el formato del wire ("yyyy-MM-dd HH:mm", lo que mandan los dos caminos Android)
     * como ISO local, y estos casos históricos ejercitan esa tolerancia. Los tests nuevos
     * que dependen de la ventana de tiempo pasan el formato del wire explícitamente.
     */
    private fun makeSms(
        id: String,
        text: String = "Compra \$10.000 en Netflix",
        time: String = "2024-01-01T10:00:00",
    ) = SmsMessage(id = id, time = time, bank = "Bancolombia",
            text = text, state = "", det = "")

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * POST /api/sms/sync as user A inserts A's messages.
     * GET /api/sms as A returns them; as B returns none.
     */
    @Test
    fun `sync inserts messages for owner and B sees none`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val tokenB = mintToken(userBId, userBEmail)

        // Sync two DISTINCT messages as user A (distinct text — text is now part of the
        // dedupe key, so reusing makeSms' default text for both would collapse them).
        val syncResp = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                listOf(
                    makeSms("msg-sync-1", "Compra \$10.000 en Netflix"),
                    makeSms("msg-sync-2", "Compra \$20.000 en Spotify"),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, syncResp.status)
        val syncBody = Json.parseToJsonElement(syncResp.body<String>()).jsonObject
        assertEquals(2, syncBody["synced"]!!.jsonPrimitive.int, "synced count should be 2")

        // User A sees both messages
        val listA = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, listA.status)
        val arrA = Json.parseToJsonElement(listA.body<String>()).jsonArray
        assertEquals(2, arrA.size, "User A should see exactly 2 sms messages")

        // User B sees none
        val listB = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.OK, listB.status)
        val arrB = Json.parseToJsonElement(listB.body<String>()).jsonArray
        assertEquals(0, arrB.size, "User B must not see user A's messages")
    }

    /**
     * Re-syncing the same message id does NOT duplicate rows (count stays the same).
     */
    @Test
    fun `re-sync same id does not insert duplicate`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val msg = makeSms("msg-dedup-1")

        // First sync — inserts 1
        val first = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(msg))
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = Json.parseToJsonElement(first.body<String>()).jsonObject
        assertEquals(1, firstBody["synced"]!!.jsonPrimitive.int, "first sync should insert 1")

        // Re-sync the same id — inserts 0
        val second = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(msg))
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody = Json.parseToJsonElement(second.body<String>()).jsonObject
        assertEquals(0, secondBody["synced"]!!.jsonPrimitive.int, "re-sync same id should insert 0")

        // Row count stays 1
        val list = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val arr = Json.parseToJsonElement(list.body<String>()).jsonArray
        assertEquals(1, arr.size, "Only 1 row should exist after re-sync")
    }

    /**
     * Re-syncing after confirm does NOT reset state to "new".
     *
     * Steps:
     *  1. Sync message id "msg-confirm-1"
     *  2. Confirm via POST /api/sms/{id}/confirm
     *  3. Re-sync the same id
     *  4. GET /api/sms/{id} → state still "confirmed"
     */
    @Test
    fun `re-sync after confirm does not reset state`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val msgId = "msg-confirm-1"

        // 1. Sync
        client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms(msgId)))
        }

        // 2. Confirm
        val confirmResp = client.post("/api/sms/$msgId/confirm") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, confirmResp.status)

        // 3. Re-sync same id
        client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms(msgId)))
        }

        // 4. State is still "confirmed"
        val getResp = client.get("/api/sms/$msgId") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, getResp.status)
        val smsObj = Json.parseToJsonElement(getResp.body<String>()).jsonObject
        assertEquals("confirmed", smsObj["state"]!!.jsonPrimitive.content,
            "State must remain 'confirmed' after re-sync")
    }

    /**
     * Fix (final review): la ruta realtime inserta ids `sms_rt_<hex>` y la ruta de pull
     * manual inserta ids `sms_<hex>` para el MISMO SMS físico — nunca coinciden porque
     * hashean distintas fuentes de timestamp. El dedupe por-id solo no detecta esta
     * colisión cross-esquema, así que el mismo SMS bancario termina duplicado en el inbox.
     *
     * Sync con id estilo-pull → luego sync del MISMO texto/bank con id estilo-realtime →
     * el segundo sync no debe insertar (synced=0) y solo debe quedar UNA fila.
     */
    @Test
    fun `re-sync same text under different id scheme does not insert duplicate`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val sharedText = "Bancolombia: Compra por \$25.000 en EXITO"

        // Pull-style id (32 hex chars, prefix sms_)
        val pullMsg = makeSms("sms_" + "a".repeat(32), sharedText)
        val first = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(pullMsg))
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = Json.parseToJsonElement(first.body<String>()).jsonObject
        assertEquals(1, firstBody["synced"]!!.jsonPrimitive.int, "first sync (pull-style id) should insert 1")

        // Realtime-style id (16 hex chars, prefix sms_rt_), same text/bank
        val realtimeMsg = makeSms("sms_rt_" + "b".repeat(16), sharedText)
        val second = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(realtimeMsg))
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody = Json.parseToJsonElement(second.body<String>()).jsonObject
        assertEquals(0, secondBody["synced"]!!.jsonPrimitive.int, "cross-scheme duplicate should not be inserted")

        // Only one row exists
        val list = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val arr = Json.parseToJsonElement(list.body<String>()).jsonArray
        assertEquals(1, arr.size, "Only 1 row should exist after cross-scheme re-sync")
    }

    /**
     * Issue #27 — el dedupe por texto solo se tragaba transacciones reales distintas.
     *
     * "Compra aprobada $28.500 en Uber BV." no trae fecha, ni hora, ni referencia: dos
     * viajes de $28.500 en días distintos producen texto byte-idéntico. Con dedupe por
     * texto el segundo desaparecía en silencio y `synced` mentía. Ambos tienen que entrar,
     * tanto dentro del mismo lote como en un sync posterior.
     */
    @Test
    fun `identical text on different days both survive`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val uberText = "Compra aprobada \$28.500 en Uber BV."

        // Mismo lote, dos días distintos → los dos entran (dedupe intra-lote por texto+tiempo)
        val first = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                listOf(
                    makeSms("sms_" + "1".repeat(32), uberText, "2026-08-01 07:15"),
                    makeSms("sms_" + "2".repeat(32), uberText, "2026-08-03 19:40"),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = Json.parseToJsonElement(first.body<String>()).jsonObject
        assertEquals(2, firstBody["synced"]!!.jsonPrimitive.int,
            "dos viajes distintos con el mismo texto deben insertarse los dos")

        // Sync posterior con un tercer viaje idéntico → también entra (dedupe contra la tabla)
        val second = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms("sms_" + "3".repeat(32), uberText, "2026-08-05 08:02")))
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody = Json.parseToJsonElement(second.body<String>()).jsonObject
        assertEquals(1, secondBody["synced"]!!.jsonPrimitive.int,
            "un tercer viaje en otro día no es duplicado de los anteriores")

        val list = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val arr = Json.parseToJsonElement(list.body<String>()).jsonArray
        assertEquals(3, arr.size, "las 3 transacciones reales deben estar en el inbox")
    }

    /**
     * Issue #27 — la otra mitad: el MISMO SMS físico subido por los dos caminos sigue
     * colapsando a una fila aunque los timestamps no coincidan exactamente.
     *
     * El broadcast usa `timestampMillis` del PDU y el backfill `Telephony.Sms.DATE`; al
     * truncar a minutos pueden quedar a un minuto de distancia. La ventana de tolerancia
     * es lo que separa este caso del de arriba.
     */
    @Test
    fun `same sms via both id schemes with one minute skew collapses to one row`() = testApplication {
        application { testModule() }
        val client = smsClient(this)

        val tokenA = mintToken(userAId, userAEmail)
        val sharedText = "Bancolombia: Compra por \$25.000 en EXITO"

        // Camino backfill (id `sms_<32hex>`, hora del inbox del dispositivo)
        val first = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms("sms_" + "a".repeat(32), sharedText, "2026-08-01 07:15")))
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = Json.parseToJsonElement(first.body<String>()).jsonObject
        assertEquals(1, firstBody["synced"]!!.jsonPrimitive.int, "el primer camino inserta 1")

        // Camino realtime (id `sms_rt_<16hex>`, hora del PDU — un minuto de diferencia)
        val second = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms("sms_rt_" + "b".repeat(16), sharedText, "2026-08-01 07:16")))
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody = Json.parseToJsonElement(second.body<String>()).jsonObject
        assertEquals(0, secondBody["synced"]!!.jsonPrimitive.int,
            "el mismo SMS por el otro esquema de id no debe insertarse de nuevo")

        val list = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val arr = Json.parseToJsonElement(list.body<String>()).jsonArray
        assertEquals(1, arr.size, "solo debe quedar una fila para un mismo SMS físico")
    }

    /**
     * Push hook (spec sms-realtime): con VAPID configurado pero sin suscripciones,
     * el hook corre (best-effort) y NUNCA rompe el sync — sigue devolviendo 200
     * y contando todos los mensajes, sea o no parseable el texto, sin importar
     * el esquema de id (pull `sms_<hex>` vs realtime `sms_rt_<hex>`).
     *
     * Fix (final review): el push solo debe considerar mensajes `sms_rt_*` (capturas
     * en tiempo real); los de pull manual (históricos, sin filtrar) nunca deben empujar
     * push. No podemos observar el envío del sender directamente en este harness (no hay
     * suscripciones registradas), así que este test prueba que AMBOS esquemas de id
     * siguen sincronizando con éxito con VAPID activo — el scoping exacto del push se
     * verifica por re-revisión del diff.
     */
    @Test
    fun `sync with push configured but no subscriptions still succeeds`() = testApplication {
        System.setProperty("movi.vapid.public", "test-pub")
        System.setProperty("movi.vapid.private", "test-priv")
        try {
            application { testModule() }
            val client = smsClient(this)

            val tokenA = mintToken(userAId, userAEmail)
            val syncResp = client.post("/api/sms/sync") {
                header(HttpHeaders.Authorization, "Bearer $tokenA")
                contentType(ContentType.Application.Json)
                setBody(
                    listOf(
                        makeSms("msg-push-1", "Bancolombia: Compra por \$50.000 en EXITO"),
                        makeSms("msg-push-2", "hola"),
                        // pull-style id, parseable text — must sync fine but NOT push (manual pull)
                        makeSms("sms_" + "c".repeat(32), "Bancolombia: Compra por \$30.000 en Netflix"),
                        // realtime-style id, parseable text — must sync fine and stay push-eligible
                        makeSms("sms_rt_" + "d".repeat(16), "Bancolombia: Compra por \$40.000 en Rappi"),
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, syncResp.status)
            val syncBody = Json.parseToJsonElement(syncResp.body<String>()).jsonObject
            assertEquals(4, syncBody["synced"]!!.jsonPrimitive.int, "synced count should include all 4 distinct messages")
        } finally {
            System.clearProperty("movi.vapid.public")
            System.clearProperty("movi.vapid.private")
        }
    }
}
