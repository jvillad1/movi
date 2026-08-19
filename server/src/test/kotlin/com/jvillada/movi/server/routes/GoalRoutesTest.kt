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
 * HTTP-level tests for GET/POST/PUT/DELETE /api/goals (F26, Ola 6).
 *
 * Same harness pattern as CreditRoutesTest.kt: H2 in-memory DB (PostgreSQL compat mode), a
 * test-local JWT secret/verifier, and the full serialization+jwt+routing plugin chain wired
 * through a local `wireApp()` helper.
 */
class GoalRoutesTest {

    private val testSecret = "test-secret-for-goal-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-goals"
    private val userBId = "user-b-goals"
    private val userAEmail = "a@goals.test"
    private val userBEmail = "b@goals.test"

    private val cashAccountId = "acc-cash-a"      // Dinero — cuenta válida para una meta
    private val cardAccountId = "acc-card-a"      // Deuda — rechazada con 422
    private val cashAccountBId = "acc-cash-b"     // Dinero de B — para el PUT del test de aislamiento

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:goal_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Goals, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Goals,
            )

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

            // ── Cuenta de Dinero para A, con $1.000.000 ya adentro ───────────────
            Accounts.insert {
                it[id]       = cashAccountId
                it[userId]   = userAId
                it[name]     = "Ahorros"
                it[type]     = "CASH"
                it[currency] = "COP"
            }
            Events.insert {
                it[id]                   = "evt-cash-opening"
                it[userId]               = userAId
                it[accountId]            = cashAccountId
                it[type]                 = "INCOME"
                it[amount]               = 1_000_000L
                it[currency]             = "COP"
                it[category]             = "Saldo inicial"
                it[description]          = "Saldo inicial"
                it[timestamp]            = System.currentTimeMillis()
                it[eventSource]          = "MANUAL"
                it[reconciliationStatus] = "UNCONFIRMED"
            }

            // ── Tarjeta de crédito para A (deuda — usada para el caso 422) ───────
            Accounts.insert {
                it[id]       = cardAccountId
                it[userId]   = userAId
                it[name]     = "Visa"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }

            // ── Cuenta de Dinero para B (para probar el aislamiento sin que el 404
            // salga por "cuenta ajena" en vez de por "meta ajena") ───────────────
            Accounts.insert {
                it[id]       = cashAccountBId
                it[userId]   = userBId
                it[name]     = "Ahorros B"
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

    private fun validGoalJson(accountId: String, name: String = "Viaje a Cartagena") =
        """{"name":"$name","target":5000000,"accountId":"$accountId","targetDate":"2027-01-01"}"""

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `GET is empty before creating any goal`() = testApplication {
        wireApp()
        val res = client.get("/api/goals") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(Json.parseToJsonElement(res.bodyAsText()).jsonArray.isEmpty())
    }

    @Test
    fun `POST creates a goal and GET returns it with saved derived from the account balance`() = testApplication {
        wireApp()
        val post = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountId))
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals(1_000_000L, created["saved"]!!.jsonPrimitive.long, "saved sale del saldo de la cuenta, no de un aporte manual")

        val res = client.get("/api/goals") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        val goal = arr[0].jsonObject
        assertEquals("Viaje a Cartagena", goal["name"]!!.jsonPrimitive.content)
        assertEquals(5_000_000L, goal["target"]!!.jsonPrimitive.long)
        assertEquals(1_000_000L, goal["saved"]!!.jsonPrimitive.long)
    }

    @Test
    fun `POST on a debt account is 422`() = testApplication {
        wireApp()
        val res = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cardAccountId))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `POST on another user's account is 404`() = testApplication {
        wireApp()
        val res = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountId))
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `PUT updates name and target, and re-derives saved`() = testApplication {
        wireApp()
        val post = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountId))
        }
        val id = Json.parseToJsonElement(post.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val put = client.put("/api/goals/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Viaje a San Andrés","target":8000000,"accountId":"$cashAccountId","targetDate":null}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val updated = Json.parseToJsonElement(put.bodyAsText()).jsonObject
        assertEquals("Viaje a San Andrés", updated["name"]!!.jsonPrimitive.content)
        assertEquals(8_000_000L, updated["target"]!!.jsonPrimitive.long)
        assertEquals(1_000_000L, updated["saved"]!!.jsonPrimitive.long)
    }

    @Test
    fun `DELETE removes and second delete is 404`() = testApplication {
        wireApp()
        val post = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountId))
        }
        val id = Json.parseToJsonElement(post.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/goals/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/goals/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }

    @Test
    fun `user B cannot see, edit or delete user A's goal`() = testApplication {
        wireApp()
        val post = client.post("/api/goals") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountId))
        }
        val id = Json.parseToJsonElement(post.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val bList = client.get("/api/goals") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertTrue(Json.parseToJsonElement(bList.bodyAsText()).jsonArray.isEmpty())

        // Con una cuenta propia válida (así el 404 es por "meta ajena", no por "cuenta ajena").
        val put = client.put("/api/goals/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validGoalJson(cashAccountBId))
        }
        assertEquals(HttpStatusCode.NotFound, put.status)

        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/goals/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }.status)
    }
}
