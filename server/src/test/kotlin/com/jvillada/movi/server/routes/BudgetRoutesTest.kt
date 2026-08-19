package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
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

/**
 * F17: `PUT /api/budgets/{category}/rename` — la categoría es la PK de `budgets`
 * (userId+category), así que renombrar es borrar e insertar en una transacción, conservando
 * el límite. Mismo arnés que AccountRoutesTest/FinanceRoutesTest: H2 en memoria (compat
 * PostgreSQL), JWT local, cadena completa de plugins vía `wireApp()`.
 */
class BudgetRoutesTest {

    private val testSecret = "test-secret-for-budget-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-budgets"
    private val userEmail = "a@budgets.test"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:budget_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals,
            )

            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    private fun tokenFor(userId: String, email: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    private val token get() = tokenFor(userId, userEmail)

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

    private suspend fun ApplicationTestBuilder.postBudget(category: String, limit: Long) =
        client.post("/api/budgets") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category","monthlyLimit":$limit}""")
        }

    private suspend fun ApplicationTestBuilder.rename(category: String, newCategory: String, asToken: String = token) =
        client.put("/api/budgets/$category/rename") {
            header(HttpHeaders.Authorization, "Bearer $asToken")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"newCategory":"$newCategory"}""")
        }

    private suspend fun ApplicationTestBuilder.budgets(): List<kotlinx.serialization.json.JsonObject> =
        Json.parseToJsonElement(
            client.get("/api/budgets") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonArray.map { it.jsonObject }

    @Test
    fun `rename borra el nombre viejo, crea el nuevo y conserva el limite`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)

        val res = rename("Mercado", "Supermercado")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Supermercado", body["category"]!!.jsonPrimitive.content)
        assertEquals(500_000L, body["monthlyLimit"]!!.jsonPrimitive.long)

        val list = budgets()
        assertEquals(1, list.size, "no debe quedar la fila vieja ni sobrar ninguna")
        assertEquals("Supermercado", list[0]["category"]!!.jsonPrimitive.content)
        assertEquals(500_000L, list[0]["monthlyLimit"]!!.jsonPrimitive.long)
    }

    @Test
    fun `rename de una categoria que no existe es 404`() = testApplication {
        wireApp()
        val res = rename("NoExiste", "Otra")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `rename a un nombre que ya esta en uso es 409 y no toca ninguna de las dos filas`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)
        postBudget("Salud", 200_000)

        val res = rename("Mercado", "Salud")
        assertEquals(HttpStatusCode.Conflict, res.status)

        val list = budgets().associateBy { it["category"]!!.jsonPrimitive.content }
        assertEquals(2, list.size, "las dos filas originales se quedan intactas")
        assertEquals(500_000L, list["Mercado"]!!["monthlyLimit"]!!.jsonPrimitive.long)
        assertEquals(200_000L, list["Salud"]!!["monthlyLimit"]!!.jsonPrimitive.long)
    }

    @Test
    fun `rename al mismo nombre es un no-op valido`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)
        val res = rename("Mercado", "Mercado")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1, budgets().size)
    }

    @Test
    fun `rename no toca los presupuestos de otro usuario`() = testApplication {
        wireApp()
        val otherUserId = "user-b-budgets"
        val otherEmail = "b@budgets.test"
        transaction {
            Users.insert {
                it[id]           = otherUserId
                it[email]        = otherEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
        postBudget("Mercado", 500_000)

        val res = rename("Mercado", "Supermercado", asToken = tokenFor(otherUserId, otherEmail))
        assertEquals(HttpStatusCode.NotFound, res.status, "B no tiene presupuesto \"Mercado\" — no puede renombrar el de A")

        val list = budgets()
        assertEquals(1, list.size)
        assertEquals("Mercado", list[0]["category"]!!.jsonPrimitive.content, "el de A sigue con su nombre viejo")
    }
}
