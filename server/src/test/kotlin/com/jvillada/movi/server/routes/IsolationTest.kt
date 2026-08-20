package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
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
import kotlin.test.assertTrue

/**
 * HTTP-level multi-tenant isolation tests (Task 7 of SP-0).
 *
 * Strategy B: H2 in-memory DB (PostgreSQL-compatibility mode).
 * The full Ktor plugin chain (serialization + JWT auth + routing) is wired
 * using a test-local module so we never touch DatabaseFactory.init() or
 * JwtConfig's lazy secret — instead we build tokens directly with a known
 * test secret and install a matching verifier.
 */
class IsolationTest {

    private val testSecret = "test-secret-for-isolation-tests-minimum-32-chars"
    private val issuer    = "movi"
    private val audience  = "movi-client"

    private val userAId    = "user-a-isolation"
    private val userBId    = "user-b-isolation"
    private val userAEmail = "a@isolation.test"
    private val userBEmail = "b@isolation.test"
    private val ruleId     = "rule-owned-by-a"
    private val smsId      = "sms-owned-by-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        // Connect Exposed to an H2 in-memory database in PostgreSQL compat mode.
        // "isolation_test" is the in-memory DB name; DB_CLOSE_DELAY=-1 keeps it
        // alive for the entire JVM lifetime so the @BeforeTest wiring persists
        // into the testApplication block.
        Database.connect(
            url    = "jdbc:h2:mem:isolation_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            // Full drop + recreate of every table the app uses for a guaranteed
            // clean slate between tests (no bleed from other suites or prior runs).
            SchemaUtils.drop(
                Goals, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Goals,
            )

            // ── User A ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
            // ── User B ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
            // ── RecurringRule owned by user A ─────────────────────────────────
            RecurringRules.insert {
                it[id]         = ruleId
                it[userId]     = userAId
                it[name]       = "Netflix A"
                it[category]   = "Suscripción"
                it[amount]     = 50_000L
                it[dayOfMonth] = 15
                it[type]       = "EXPENSE"
            }
            // ── SmsMessage owned by user A ────────────────────────────────────
            SmsMessages.insert {
                it[id]     = smsId
                it[userId] = userAId
                it[time]   = "2024-01-01T10:00:00"
                it[bank]   = "Bancolombia"
                it[text]   = "Compra \$50.000 en Netflix"
                it[state]  = "pending"
                it[det]    = ""
            }
        }
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private fun mintToken(userId: String, email: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    // ── Test application module ───────────────────────────────────────────────
    //
    // Skips DatabaseFactory.init() (Postgres not available in CI).
    // Wires serialization + a JWT verifier using the known test secret + routing.

    private fun Application.testModule() {
        configureSerialization()

        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm)
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

    // ── Tests: multi-tenant isolation ─────────────────────────────────────────

    /**
     * User B GET /api/recurring-rules — must NOT see user A's rule → [].
     */
    @Test
    fun `recurring-rules GET as user B returns empty list not A's data`() = testApplication {
        application { testModule() }
        val token = mintToken(userBId, userBEmail)

        val resp = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "User B must not see user A's recurring rules; got: ${resp.bodyAsText()}")
    }

    /**
     * User B GET /api/sms — must NOT see user A's message → [].
     */
    @Test
    fun `sms GET as user B returns empty list not A's data`() = testApplication {
        application { testModule() }
        val token = mintToken(userBId, userBEmail)

        val resp = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "User B must not see user A's sms messages; got: ${resp.bodyAsText()}")
    }

    /**
     * User B GET /api/sms/{id} where id is owned by user A → 404.
     */
    @Test
    fun `sms GET by id as user B returns 404 for user A's message`() = testApplication {
        application { testModule() }
        val token = mintToken(userBId, userBEmail)

        val resp = client.get("/api/sms/$smsId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    /**
     * GET /api/credits → [] (no credits model yet; route always returns empty).
     */
    @Test
    fun `credits GET returns empty array`() = testApplication {
        application { testModule() }
        val token = mintToken(userAId, userAEmail)

        val resp = client.get("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "Expected [] from /api/credits, got: ${resp.bodyAsText()}")
    }

    /**
     * GET /api/goals → [] cuando el usuario no tiene ninguna meta creada (F26: la ruta ya
     * tiene tabla y alta — ver GoalRoutesTest.kt — pero sigue devolviendo `[]` sin filas).
     */
    @Test
    fun `goals GET returns empty array`() = testApplication {
        application { testModule() }
        val token = mintToken(userAId, userAEmail)

        val resp = client.get("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "Expected [] from /api/goals, got: ${resp.bodyAsText()}")
    }

    // ── Sanity: user A can see its own data ───────────────────────────────────

    /**
     * User A GET /api/recurring-rules → sees exactly its own 1 rule.
     */
    @Test
    fun `recurring-rules GET as user A returns own rule`() = testApplication {
        application { testModule() }
        val token = mintToken(userAId, userAEmail)

        val resp = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertEquals(1, arr.size, "User A should see exactly 1 recurring rule; got: ${resp.bodyAsText()}")
    }

    /**
     * User A GET /api/sms → sees exactly its own 1 message.
     */
    @Test
    fun `sms GET as user A returns own message`() = testApplication {
        application { testModule() }
        val token = mintToken(userAId, userAEmail)

        val resp = client.get("/api/sms") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertEquals(1, arr.size, "User A should see exactly 1 sms message; got: ${resp.bodyAsText()}")
    }

    // ── Cross-user SMS MUTATION isolation ─────────────────────────────────────

    /**
     * User B POST /api/sms/{id}/confirm where id is owned by A → 404.
     * A's row state must remain unchanged.
     */
    @Test
    fun `sms confirm by user B on user A message returns 404`() = testApplication {
        application { testModule() }
        val tokenB = mintToken(userBId, userBEmail)
        val tokenA = mintToken(userAId, userAEmail)

        // User B tries to confirm A's sms
        val resp = client.post("/api/sms/$smsId/confirm") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status, "User B confirming A's SMS should return 404")

        // A's row state must still be "pending" (unchanged)
        val getResp = client.get("/api/sms/$smsId") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, getResp.status)
        val state = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject["state"]!!.jsonPrimitive.content
        assertEquals("pending", state, "A's SMS state must remain 'pending' after B's failed confirm")
    }

    /**
     * User B POST /api/sms/{id}/ignore where id is owned by A → 404.
     * A's row state must remain unchanged.
     */
    @Test
    fun `sms ignore by user B on user A message returns 404`() = testApplication {
        application { testModule() }
        val tokenB = mintToken(userBId, userBEmail)
        val tokenA = mintToken(userAId, userAEmail)

        // User B tries to ignore A's sms
        val resp = client.post("/api/sms/$smsId/ignore") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status, "User B ignoring A's SMS should return 404")

        // A's row state must still be "pending" (unchanged)
        val getResp = client.get("/api/sms/$smsId") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, getResp.status)
        val state = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject["state"]!!.jsonPrimitive.content
        assertEquals("pending", state, "A's SMS state must remain 'pending' after B's failed ignore")
    }
}
