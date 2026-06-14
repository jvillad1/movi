package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.jsonArray
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
 * HTTP-level isolation tests for the recurring-rule CRUD + upcoming-payments endpoint.
 * Reuses the same H2 in-memory harness as IsolationTest.
 */
class ReminderRoutesTest {

    private val testSecret = "test-secret-for-reminder-tests-minimum-32-chars"
    private val issuer    = "movi"
    private val audience  = "movi-client"

    private val userAId    = "user-a-reminder"
    private val userBId    = "user-b-reminder"
    private val userAEmail = "a@reminder.test"
    private val userBEmail = "b@reminder.test"
    private val ruleOwnedByA = "rule-a-reminder"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:reminder_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages,
            )
            SchemaUtils.drop(RecurringRules, Users)
            SchemaUtils.create(Users, RecurringRules)

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
            RecurringRules.insert {
                it[id]         = ruleOwnedByA
                it[userId]     = userAId
                it[name]       = "Rent A"
                it[category]   = "Vivienda"
                it[amount]     = 1_500_000L
                it[dayOfMonth] = 5
                it[type]       = "EXPENSE"
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

    /** User A POSTs a rule; User B PUT on A's id → 404 */
    @Test
    fun `user B cannot update user A's recurring rule`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User A posts a new rule
        val tokenA = mintToken(userAId, userAEmail)
        val newRule = RecurringRule("ignored", "Netflix", "Suscripción", 50_000, 15, TransactionType.EXPENSE)
        val createResp = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(newRule)
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val created = createResp.body<RecurringRule>()

        // User B tries to update that rule
        val tokenB = mintToken(userBId, userBEmail)
        val updateResp = client.put("/api/recurring-rules/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody(newRule.copy(name = "Hacked"))
        }
        assertEquals(HttpStatusCode.NotFound, updateResp.status,
            "User B must get 404 trying to update user A's rule")
    }

    /** User B DELETE on A's rule id → 404 */
    @Test
    fun `user B cannot delete user A's recurring rule`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        val tokenB = mintToken(userBId, userBEmail)
        val resp = client.delete("/api/recurring-rules/$ruleOwnedByA") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status,
            "User B must get 404 trying to delete user A's rule")
    }

    /** GET /api/payments/upcoming as B excludes A's rule */
    @Test
    fun `upcoming payments as user B excludes user A's rules`() = testApplication {
        application { testModule() }
        val tokenB = mintToken(userBId, userBEmail)
        val resp = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "User B's upcoming payments must be empty; got: ${resp.bodyAsText()}")
    }

    /** User A can see its own upcoming payments */
    @Test
    fun `upcoming payments as user A includes own rules`() = testApplication {
        application { testModule() }
        val tokenA = mintToken(userAId, userAEmail)
        val resp = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertEquals(1, arr.size, "User A should see exactly 1 upcoming payment; got: ${resp.bodyAsText()}")
    }
}
