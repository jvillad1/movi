package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CardTerms
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level tests for /api/cards (F20, Ola 5 T2). Mismo arnés que CreditRoutesTest:
 * H2 en memoria (modo PostgreSQL), JWT local y la cadena completa de plugins.
 */
class CardRoutesTest {

    private val testSecret = "test-secret-for-card-routes-tests-min-32-chars!"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-cards"
    private val userBId = "user-b-cards"
    private val userAEmail = "a@cards.test"
    private val userBEmail = "b@cards.test"

    private val cardAccountId = "acc-card-a"   // CREDIT_CARD de A, creada "desde Cuentas", sin términos
    private val cashAccountId = "acc-cash-a"

    private val validTermsJson =
        """{"accountId":"$cardAccountId","bank":"Bancolombia","creditLimit":20000000,"cutoffDay":10,"paymentDay":25}"""

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:card_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Cards, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                RecurringRules, SmsMessages, Credits, Cards,
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

            // ── Tarjeta de A creada "desde Cuentas": cuenta + deuda inicial, SIN términos ──
            Accounts.insert {
                it[id]       = cardAccountId
                it[userId]   = userAId
                it[name]     = "Visa Bancolombia"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }
            Events.insert {
                it[id]                   = "evt-card-a-opening"
                it[userId]               = userAId
                it[accountId]            = cardAccountId
                it[type]                 = "EXPENSE"
                it[amount]               = 3_450_000L
                it[currency]             = "COP"
                it[category]             = "Saldo inicial"
                it[description]          = "Deuda inicial"
                it[timestamp]            = System.currentTimeMillis()
                it[eventSource]          = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }

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
    fun `GET lists CREDIT_CARD accounts without terms with derived debt`() = testApplication {
        wireApp()
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        val summary = arr[0].jsonObject
        assertEquals(3_450_000L, summary["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        assertTrue(summary["terms"] is JsonNull)
        // `available` tiene default (= null) y el Json del server no serializa defaults:
        // puede venir omitido o como null explícito — ambos son "sin cupo declarado".
        assertTrue(summary["available"] == null || summary["available"] is JsonNull)
    }

    @Test
    fun `PUT gives an existing card its terms and GET derives available from the limit`() = testApplication {
        wireApp()
        val put = client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val summary = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject
        assertEquals("Bancolombia", summary["terms"]!!.jsonObject["bank"]!!.jsonPrimitive.content)
        // available = cupo 20M − deuda 3.45M
        assertEquals(16_550_000L, summary["available"]!!.jsonPrimitive.long)
    }

    @Test
    fun `PUT is an idempotent upsert`() = testApplication {
        wireApp()
        repeat(2) {
            client.put("/api/cards/$cardAccountId") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
                header(HttpHeaders.ContentType, "application/json")
                setBody(validTermsJson)
            }
        }
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(1, Json.parseToJsonElement(res.bodyAsText()).jsonArray.size)
    }

    @Test
    fun `PUT on another user's card is 404`() = testApplication {
        wireApp()
        val res = client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `PUT on a non-card account is 422`() = testApplication {
        wireApp()
        val res = client.put("/api/cards/$cashAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `POST creates account, terms and opening debt atomically`() = testApplication {
        wireApp()
        val post = client.post("/api/cards") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"name":"Mastercard USD","initialDebt":1200,"currency":"USD",
                   "terms":{"accountId":"","bank":"Davivienda","creditLimit":5000,"paymentDay":15}}""",
            )
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        val account = created["account"]!!.jsonObject
        assertEquals("USD", account["currency"]!!.jsonPrimitive.content)
        // Deuda derivada del evento de apertura que el server creó en la misma transacción
        // (en la moneda de la cuenta): available = 5000 − 1200.
        assertEquals(3800L, created["available"]!!.jsonPrimitive.long)

        val accountId = account["id"]!!.jsonPrimitive.content
        transaction {
            val openings = Events.selectAll().where { org.jetbrains.exposed.sql.SqlExpressionBuilder.run { Events.accountId eq accountId } }.toList()
            assertEquals(1, openings.size)
            assertEquals("EXPENSE", openings[0][Events.type])
            assertEquals(1200L, openings[0][Events.amount])
            assertEquals("USD", openings[0][Events.currency])
        }
    }

    @Test
    fun `POST with zero debt creates no opening event`() = testApplication {
        wireApp()
        val post = client.post("/api/cards") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Visa nueva","terms":{"accountId":"","bank":"Nu","paymentDay":5}}""")
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        val accountId = created["account"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(0L, created["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        transaction {
            val events = Events.selectAll().where { org.jetbrains.exposed.sql.SqlExpressionBuilder.run { Events.accountId eq accountId } }.toList()
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun `POST with blank name is 400`() = testApplication {
        wireApp()
        val res = client.post("/api/cards") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"  ","terms":{"accountId":"","bank":"Nu","paymentDay":5}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `POST with negative debt is 400`() = testApplication {
        wireApp()
        val res = client.post("/api/cards") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Visa","initialDebt":-1,"terms":{"accountId":"","bank":"Nu","paymentDay":5}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `DELETE removes only the terms and is 404 the second time`() = testApplication {
        wireApp()
        client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        val del = client.delete("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NoContent, del.status)

        // La cuenta sigue: solo se borraron los términos.
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val summary = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject
        assertTrue(summary["terms"] is JsonNull)

        val delAgain = client.delete("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NotFound, delAgain.status)
    }

    @Test
    fun `GET is isolated per user`() = testApplication {
        wireApp()
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, Json.parseToJsonElement(res.bodyAsText()).jsonArray.size)
    }
    // ── remindMe: la casilla «Recordarme unos días antes» ─────────────────────

    @Test
    fun `una tarjeta guardada sin recordatorio se relee sin recordatorio`() = testApplication {
        wireApp()
        val put = client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson.dropLast(1) + ""","remindMe":false}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!.jsonObject
        assertEquals(false, terms["remindMe"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `una tarjeta guardada sin decir nada nace con el recordatorio prendido`() = testApplication {
        wireApp()
        client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        // Ver el test hermano en CreditRoutesTest: `remindMe: true` es el default del modelo y
        // kotlinx.serialization no lo emite, así que se afirma sobre el modelo decodificado —
        // que es lo que efectivamente lee el cliente.
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.decodeFromJsonElement<CardTerms>(
            Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!,
        )
        assertTrue(terms.remindMe)
    }

    @Test
    fun `editar los terminos de la tarjeta conserva el valor del recordatorio`() = testApplication {
        wireApp()
        val sinAviso = validTermsJson.dropLast(1) + ""","remindMe":false}"""
        client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(sinAviso)
        }
        client.put("/api/cards/$cardAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(sinAviso.replace("\"paymentDay\":25", "\"paymentDay\":18"))
        }
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!.jsonObject
        assertEquals(false, terms["remindMe"]!!.jsonPrimitive.boolean)
        assertEquals(18L, terms["paymentDay"]!!.jsonPrimitive.long)
    }
}
