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

    private fun makeSms(id: String, text: String = "Compra \$10.000 en Netflix") =
        SmsMessage(id = id, time = "2024-01-01T10:00:00", bank = "Bancolombia",
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

        // Sync two messages as user A
        val syncResp = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(listOf(makeSms("msg-sync-1"), makeSms("msg-sync-2")))
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
     * Push hook (spec sms-realtime): con VAPID configurado pero sin suscripciones,
     * el hook corre (best-effort) y NUNCA rompe el sync — sigue devolviendo 200
     * y contando ambos mensajes, sea o no parseable el texto.
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
                setBody(listOf(makeSms("msg-push-1", "Bancolombia: Compra por \$50.000 en EXITO"), makeSms("msg-push-2", "hola")))
            }
            assertEquals(HttpStatusCode.OK, syncResp.status)
            val syncBody = Json.parseToJsonElement(syncResp.body<String>()).jsonObject
            assertEquals(2, syncBody["synced"]!!.jsonPrimitive.int, "synced count should include both messages")
        } finally {
            System.clearProperty("movi.vapid.public")
            System.clearProperty("movi.vapid.private")
        }
    }
}
