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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-level tests for GET/POST-detect/PUT/DELETE /api/subscriptions (Task 3 of
 * SP-subscription-tracker). Same harness pattern as CreditRoutesTest.kt: H2
 * in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and the
 * full serialization+jwt+routing plugin chain wired through a local `wireApp()`.
 */
class SubscriptionRoutesTest {

    private val testSecret = "test-secret-for-subscription-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-subs"
    private val userBId = "user-b-subs"
    private val userAEmail = "a@subs.test"
    private val userBEmail = "b@subs.test"

    private val accountAId = "acc-tc-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:subscription_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            // ── User B ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }

            // ── CREDIT_CARD account for A ────────────────────────────────────
            Accounts.insert {
                it[id]       = accountAId
                it[userId]   = userAId
                it[name]     = "Tarjeta de Crédito"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }

            // ── Netflix: HIGH confidence → AUTO ─────────────────────────────
            insertExpense("evt-netflix-1", "PAYU*NETFLIX", 44_900, "2026-04-14")
            insertExpense("evt-netflix-2", "PAYU*NETFLIX", 44_900, "2026-05-14")
            insertExpense("evt-netflix-3", "PAYU*NETFLIX", 44_900, "2026-06-14")

            // ── YouTube: MEDIUM confidence → CANDIDATE ──────────────────────
            insertExpense("evt-youtube-1", "Google YOUTUBE Mmbrshp", 26_900, "2026-05-10")
            insertExpense("evt-youtube-2", "Google YOUTUBE Mmbrshp", 26_900, "2026-06-10")

            // ── EXITO: single occurrence → not detected ─────────────────────
            insertExpense("evt-exito-1", "EXITO COUNTRY", 312_400, "2026-06-02")
        }
    }

    private fun insertExpense(id: String, desc: String, amount: Long, tsIso: String) {
        Events.insert {
            it[Events.id]                   = id
            it[Events.userId]               = userAId
            it[Events.accountId]            = accountAId
            it[Events.type]                 = "EXPENSE"
            it[Events.amount]               = amount
            it[Events.currency]             = "COP"
            it[Events.category]             = "Otros"
            it[Events.description]          = desc
            it[Events.timestamp]            = LocalDate.parse(tsIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            it[Events.eventSource]          = "STATEMENT"
            it[Events.reconciliationStatus] = "UNCONFIRMED"
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
    fun `detect creates AUTO and CANDIDATE subscriptions from events`() = testApplication {
        wireApp()
        val res = client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val subs = body["subscriptions"]!!.jsonArray
        assertEquals(2, subs.size)
        val byKey = subs.associateBy { it.jsonObject["merchantKey"]!!.jsonPrimitive.content }
        assertEquals("AUTO",      byKey["netflix"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("CANDIDATE", byKey["youtube"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        // total mensual = solo AUTO+CONFIRMED → netflix
        assertEquals(44_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    @Test
    fun `re-detect is idempotent`() = testApplication {
        wireApp()
        repeat(2) { client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") } }
        val res = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(2, Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
    }

    @Test
    fun `dismissed stays dismissed after re-detect`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val netflix = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        val id = netflix["id"]!!.jsonPrimitive.content
        val dismissed = netflix.toMutableMap().apply { put("status", JsonPrimitive("DISMISSED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(dismissed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val after = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        assertEquals("DISMISSED", after["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `confirmed is not downgraded by re-detect and total includes it`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val youtube = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        val id = youtube["id"]!!.jsonPrimitive.content
        val confirmed = youtube.toMutableMap().apply { put("status", JsonPrimitive("CONFIRMED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(confirmed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val body = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject
        val after = body["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        assertEquals("CONFIRMED", after["status"]!!.jsonPrimitive.content)
        assertEquals(44_900L + 26_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    @Test
    fun `user B has no subscriptions and cannot edit A's`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val bList = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals(0, Json.parseToJsonElement(bList.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
        val aSub = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject
        val id = aSub["id"]!!.jsonPrimitive.content
        val put = client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(aSub.toMutableMap())))
        }
        assertEquals(HttpStatusCode.NotFound, put.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }.status)
    }

    @Test
    fun `DELETE removes and second delete is 404`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val id = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }
}
