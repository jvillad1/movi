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
 * F54: crear una cuenta con saldo no debe contarse como ingreso/egreso del mes ni como "primer
 * movimiento" para la guía de primeros pasos. Mismo arnés que CreditRoutesTest/EventRoutesTest:
 * H2 en memoria (compat PostgreSQL), JWT local, cadena completa de plugins vía `wireApp()`.
 *
 * A diferencia de esos dos, acá la cuenta se crea vía POST /api/accounts (no sembrada
 * directamente en la DB) para ejercitar el camino real: `openingEventFor` + el filtro de
 * `eventCount` en `/api/finance-summary` trabajando juntos, tal como los ve el cliente.
 */
class FinanceRoutesTest {

    private val testSecret = "test-secret-for-finance-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-finance"
    private val userEmail = "a@finance.test"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:finance_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

    // ── JWT helpers ───────────────────────────────────────────────────────────

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

    private suspend fun ApplicationTestBuilder.createAccount(id: String, type: String, balance: Long) =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"$id","name":"Cuenta","type":"$type","balance":$balance}""")
        }

    private suspend fun ApplicationTestBuilder.postEvent(accountId: String, type: String, amount: Long) =
        client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"","accountId":"$accountId","type":"$type","amount":$amount,
                    "category":"Comida","description":"Almuerzo","timestamp":0}""",
            )
        }

    private suspend fun ApplicationTestBuilder.summary() =
        Json.parseToJsonElement(
            client.get("/api/finance-summary") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject

    // El Json del server tiene encodeDefaults=false (default de kotlinx.serialization): un
    // eventCount de 0 — exactamente el default de FinanceSummary.eventCount — no viaja en el
    // wire. El cliente ya lo maneja con el propio default de la data class (ver su KDoc); acá
    // se hace lo mismo para no confundir "no vino" con "vino en cero".
    private fun kotlinx.serialization.json.JsonObject.eventCount(): Int =
        this["eventCount"]?.jsonPrimitive?.long?.toInt() ?: 0

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `crear una cuenta de activo con saldo no cuenta como ingreso del mes ni como movimiento`() = testApplication {
        wireApp()
        val res = createAccount("acc-savings", "SAVINGS", 1_000_000L)
        assertEquals(HttpStatusCode.Created, res.status)

        val body = summary()
        assertEquals(0L, body["ingresos"]!!.jsonPrimitive.long, "el saldo inicial no es un ingreso de agosto")
        assertEquals(0L, body["egresos"]!!.jsonPrimitive.long)
        assertEquals(
            0,
            body.eventCount(),
            "el saldo inicial no cuenta como \"primer movimiento\" para la guía de primeros pasos",
        )
    }

    @Test
    fun `crear una tarjeta con deuda no cuenta como egreso del mes ni como movimiento`() = testApplication {
        wireApp()
        val res = createAccount("acc-cc", "CREDIT_CARD", 500_000L)
        assertEquals(HttpStatusCode.Created, res.status)

        val body = summary()
        assertEquals(0L, body["egresos"]!!.jsonPrimitive.long, "la deuda inicial no es un egreso de agosto")
        assertEquals(0L, body["ingresos"]!!.jsonPrimitive.long)
        assertEquals(0, body.eventCount())
    }

    @Test
    fun `un movimiento real despues de crear la cuenta si cuenta como primer movimiento`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)
        assertEquals(0, summary().eventCount())

        val evRes = postEvent("acc-savings", "EXPENSE", 25_000L)
        assertEquals(HttpStatusCode.Created, evRes.status)

        assertEquals(
            1,
            summary().eventCount(),
            "un gasto anotado por el usuario sí debería apagar la guía de primeros pasos",
        )
    }
}
