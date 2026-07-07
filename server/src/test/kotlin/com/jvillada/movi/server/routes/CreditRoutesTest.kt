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
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
 * HTTP-level tests for GET/PUT/DELETE /api/credits (Task 3 of SP-creditos-reales).
 *
 * Same harness pattern as IsolationTest.kt: H2 in-memory DB (PostgreSQL compat
 * mode), a test-local JWT secret/verifier, and the full serialization+jwt+routing
 * plugin chain wired through a local `wireApp()` helper.
 */
class CreditRoutesTest {

    private val testSecret = "test-secret-for-credit-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-credits"
    private val userBId = "user-b-credits"
    private val userAEmail = "a@credits.test"
    private val userBEmail = "b@credits.test"

    private val loanAccountId = "acc-loan-a"
    private val cashAccountId = "acc-cash-a"

    private val validTermsJson =
        """{"accountId":"acc-loan-a","bank":"Bancolombia","principal":262000000,"rateEa":17.46,"termMonths":72,"installment":4888000,"dayOfMonth":5,"startDate":"2024-01-15"}"""

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:credit_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits,
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

            // ── LOAN account for A with an opening-balance EXPENSE event ────────
            Accounts.insert {
                it[id]       = loanAccountId
                it[userId]   = userAId
                it[name]     = "Crédito Bancolombia"
                it[type]     = "LOAN"
                it[currency] = "COP"
            }
            Events.insert {
                it[id]                   = "evt-loan-a-opening"
                it[userId]               = userAId
                it[accountId]            = loanAccountId
                it[type]                 = "EXPENSE"
                it[amount]               = 100_000_000L
                it[currency]             = "COP"
                it[category]             = "Deuda inicial"
                it[description]          = "Saldo inicial del crédito"
                it[timestamp]            = System.currentTimeMillis()
                it[eventSource]          = "MANUAL"
                it[reconciliationStatus] = "UNCONFIRMED"
            }

            // ── CASH account for A (used for the 422 non-LOAN case) ─────────────
            Accounts.insert {
                it[id]       = cashAccountId
                it[userId]   = userAId
                it[name]     = "Efectivo"
                it[type]     = "CASH"
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
    fun `PUT then GET returns terms with derived debt and paid pct`() = testApplication {
        wireApp()
        val put = client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val res = client.get("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        val summary = arr[0].jsonObject
        assertEquals("Bancolombia", summary["terms"]!!.jsonObject["bank"]!!.jsonPrimitive.content)
        assertEquals(100000000L, summary["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        // paidPct = 1 - 100M/262M ≈ 0.6183
        assertTrue(summary["paidPct"]!!.jsonPrimitive.double in 0.61..0.62)
    }

    @Test
    fun `PUT is an idempotent upsert`() = testApplication {
        wireApp()
        repeat(2) {
            client.put("/api/credits/$loanAccountId") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
                header(HttpHeaders.ContentType, "application/json")
                setBody(validTermsJson)
            }
        }
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(1, Json.parseToJsonElement(res.bodyAsText()).jsonArray.size)
    }

    @Test
    fun `PUT on another user's account is 404`() = testApplication {
        wireApp()
        val res = client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `PUT on a non-LOAN account is 422`() = testApplication {
        wireApp()
        val res = client.put("/api/credits/$cashAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `GET returns LOAN accounts without terms with null terms`() = testApplication {
        wireApp()
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val summary = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject
        assertTrue(summary["terms"] is JsonNull)
        assertTrue(summary["paidPct"] is JsonNull)
    }

    @Test
    fun `DELETE removes terms and is 404 the second time`() = testApplication {
        wireApp()
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/api/credits/$loanAccountId") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/api/credits/$loanAccountId") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status,
        )
    }

    @Test
    fun `user B cannot see user A's credits`() = testApplication {
        wireApp()
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals("[]", res.bodyAsText())
    }
}
