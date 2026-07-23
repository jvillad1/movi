package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-level tests for GET /api/push/vapid-key, POST/DELETE /api/push/subscribe
 * (Task 1 of SP-web-push). Same harness pattern as CreditRoutesTest.kt: H2
 * in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and
 * the full serialization+jwt+routing plugin chain wired through wireApp().
 */
class PushRoutesTest {

    private val testSecret = "test-secret-for-push-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-push"
    private val userBId = "user-b-push"
    private val userAEmail = "a@push.test"
    private val userBEmail = "b@push.test"

    private val subBody = """{"endpoint":"https://push.example/ep-1","p256dh":"pk","auth":"as"}"""

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:push_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                PushSubscriptions, Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions, PushSubscriptions,
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
        }
    }

    @BeforeTest
    fun vapidProps() {
        System.setProperty("movi.vapid.public", "test-public-key")
        System.setProperty("movi.vapid.private", "test-private-key")
    }

    @AfterTest
    fun clearVapidProps() {
        System.clearProperty("movi.vapid.public")
        System.clearProperty("movi.vapid.private")
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

    private fun tokenFor(userId: String): String =
        mintToken(userId, if (userId == userAId) userAEmail else userBEmail)

    // ── Test application module ───────────────────────────────────────────────

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

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `vapid key is public and returns the configured key`() = testApplication {
        wireApp()
        val res = client.get("/api/push/vapid-key")   // SIN auth
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("test-public-key", Json.parseToJsonElement(res.bodyAsText()).jsonObject["key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `vapid key is 404 when not configured`() = testApplication {
        System.clearProperty("movi.vapid.public")
        System.clearProperty("movi.vapid.private")
        wireApp()
        assertEquals(HttpStatusCode.NotFound, client.get("/api/push/vapid-key").status)
    }

    @Test
    fun `subscribe is an idempotent upsert by endpoint`() = testApplication {
        wireApp()
        repeat(2) {
            val res = client.post("/api/push/subscribe") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
                header(HttpHeaders.ContentType, "application/json")
                setBody(subBody)
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
        val count = transaction { PushSubscriptions.selectAll().count() }
        assertEquals(1, count)
    }

    @Test
    fun `re-subscribe from another user takes over the endpoint`() = testApplication {
        wireApp()
        client.post("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(subBody)
        }
        client.post("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(subBody)
        }
        val owner = transaction { PushSubscriptions.selectAll().first()[PushSubscriptions.userId] }
        assertEquals(userBId, owner)
    }

    @Test
    fun `unsubscribe deletes own and 404s on foreign or missing`() = testApplication {
        wireApp()
        client.post("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(subBody)
        }
        val delBody = """{"endpoint":"https://push.example/ep-1"}"""
        val foreign = client.delete("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(delBody)
        }
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        val own = client.delete("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(delBody)
        }
        assertEquals(HttpStatusCode.NoContent, own.status)
        val again = client.delete("/api/push/subscribe") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json"); setBody(delBody)
        }
        assertEquals(HttpStatusCode.NotFound, again.status)
    }
}
