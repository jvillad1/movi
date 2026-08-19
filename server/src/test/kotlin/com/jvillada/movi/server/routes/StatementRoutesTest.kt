package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
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
 * HTTP-level tests for POST /api/statements/import (Task 2 of SP-detect-on-import).
 * Same harness pattern as CreditRoutesTest.kt / SubscriptionRoutesTest.kt: H2
 * in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and the
 * full serialization+jwt+routing plugin chain wired through a local `wireApp()`.
 *
 * Verifies that importing a statement silently triggers subscription detection —
 * no separate call to /api/subscriptions/detect should be needed.
 */
class StatementRoutesTest {

    private val testSecret = "test-secret-for-statement-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-statements"
    private val userAEmail = "a@statements.test"

    private val accountAId = "acc-tc-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:statement_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions,
            )

            // ── User A ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }

            // ── CREDIT_CARD account for A (no events) ───────────────────────
            Accounts.insert {
                it[id]       = accountAId
                it[userId]   = userAId
                it[name]     = "Tarjeta de Crédito"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
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

    private fun tokenFor(userId: String): String =
        mintToken(userId, userAEmail)

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

    // ── Body helpers ─────────────────────────────────────────────────────────

    private fun parsedTx(id: String, date: String, merchant: String, amount: Long) =
        """{"id":"$id","date":"$date","merchant":"$merchant","amount":$amount,"currency":"COP",
            "type":"EXPENSE","category":"Otros","description":"$merchant","rawText":""}"""

    private fun importBody(txs: String) =
        """{"statementId":"st-test","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[$txs],"reconciliations":[],"skipped":[]}"""

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `import triggers subscription detection automatically`() = testApplication {
        wireApp()
        val txs = listOf(
            parsedTx("p1", "2026-04-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p2", "2026-05-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p3", "2026-06-14", "PAYU*NETFLIX", 44_900),
        ).joinToString(",")
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(txs))
        }
        assertEquals(HttpStatusCode.OK, res.status)

        // SIN llamar /detect: el import debe haber disparado la detección solo
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(1, subs.size)
        val netflix = subs[0].jsonObject
        assertEquals("netflix", netflix["merchantKey"]!!.jsonPrimitive.content)
        // F39: nada nace activo — ni siquiera HIGH confidence salta a AUTO. La detección
        // disparada por el import deja la fila CANDIDATE, igual que el /detect manual.
        assertEquals("CANDIDATE", netflix["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `import without recurring patterns creates no subscriptions`() = testApplication {
        wireApp()
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(parsedTx("p1", "2026-06-11", "EXITO COUNTRY", 312_400)))
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(0, subs.size)
    }
}
