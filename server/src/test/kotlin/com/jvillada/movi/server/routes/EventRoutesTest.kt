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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * HTTP-level tests for GET /api/events/card-payment-candidates (Task 2 de SP-ajustar-saldo).
 *
 * Mismo arnés que CreditRoutesTest: H2 en memoria (compat PostgreSQL), secreto JWT local,
 * cadena completa de plugins vía `wireApp()`.
 */
class EventRoutesTest {

    private val testSecret = "test-secret-for-event-routes-tests-minimum-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-events"
    private val userBId = "user-b-events"
    private val userAEmail = "a@events.test"
    private val userBEmail = "b@events.test"

    private val savingsAccountId    = "acc-savings-a"
    private val creditCardAccountId = "acc-cc-a"
    private val loanAccountId       = "acc-loan-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:event_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

            Accounts.insert {
                it[id]       = savingsAccountId
                it[userId]   = userAId
                it[name]     = "Ahorros"
                it[type]     = "SAVINGS"
                it[currency] = "COP"
            }
            Accounts.insert {
                it[id]       = creditCardAccountId
                it[userId]   = userAId
                it[name]     = "Tarjeta Bancolombia"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }
            Accounts.insert {
                it[id]       = loanAccountId
                it[userId]   = userAId
                it[name]     = "Libranza"
                it[type]     = "LOAN"
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

    // ── event seeding helper ─────────────────────────────────────────────────

    private fun seedEvent(
        id: String,
        userId: String,
        accountId: String,
        type: String,
        description: String,
        category: String,
        amount: Long = 100_000L,
    ) {
        transaction {
            Events.insert {
                it[Events.id]                   = id
                it[Events.userId]               = userId
                it[Events.accountId]            = accountId
                it[Events.type]                 = type
                it[Events.amount]                = amount
                it[Events.currency]             = "COP"
                it[Events.category]             = category
                it[Events.description]          = description
                it[Events.timestamp]            = System.currentTimeMillis()
                it[Events.eventSource]          = "STATEMENT"
                it[Events.reconciliationStatus] = "UNCONFIRMED"
            }
        }
    }

    private fun voidEvent(eventId: String, userId: String) {
        transaction {
            VoidEvents.insert {
                it[id]              = "void_$eventId"
                it[VoidEvents.userId] = userId
                it[originalEventId]  = eventId
                it[timestamp]        = System.currentTimeMillis()
            }
        }
    }

    private suspend fun ApplicationTestBuilder.candidatesFor(userId: String) =
        client.get("/api/events/card-payment-candidates") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
        }

    private suspend fun ApplicationTestBuilder.putCategory(id: String, category: String, userId: String) =
        client.put("/api/events/$id/category") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category"}""")
        }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `propone un EXPENSE de cuenta de activo cuya descripcion matchea un patron`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        val res = candidatesFor(userAId)
        assertEquals(HttpStatusCode.OK, res.status)
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        assertEquals("evt-1", arr[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PAGO QR de un comercio real no es candidato aunque este en cuenta de activo`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-qr", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "PAGO QR Dogger", category = "Otros",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "PAGO QR es un gasto real, nunca debería proponerse como pago de tarjeta")
    }

    @Test
    fun `excluye eventos de cuentas de deuda aunque el texto matchee`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-cc", userId = userAId, accountId = creditCardAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        seedEvent(
            id = "evt-loan", userId = userAId, accountId = loanAccountId,
            type = "EXPENSE", description = "Pago a tarjeta", category = "Otros",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "CREDIT_CARD y LOAN no son cuentas de activo")
    }

    @Test
    fun `excluye eventos ya categorizados como Pago de tarjeta`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-already", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Pago de tarjeta",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "ya está bien categorizado, no hay nada que proponer")
    }

    @Test
    fun `excluye eventos anulados`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-voided", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        voidEvent("evt-voided", userAId)

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty())
    }

    @Test
    fun `excluye eventos INCOME aunque el texto matchee`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-income", userId = userAId, accountId = savingsAccountId,
            type = "INCOME", description = "Pago tarjeta de crédito", category = "Otros ingresos",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "el endpoint solo propone EXPENSE — el pago del extracto sale de la cuenta de ahorros")
    }

    @Test
    fun `usuario B no ve los candidatos de usuario A`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        assertEquals("[]", candidatesFor(userBId).bodyAsText())
    }

    /**
     * El endpoint PROPONE, no recategoriza: es la promesa central del diseño (Task 3 la usa
     * para el endpoint de confirmación). Este test cubre que un GET —incluso repetido— no
     * cambia ni la categoría ni ningún otro campo del evento en la base.
     */
    @Test
    fun `GET no modifica ningun evento`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        candidatesFor(userAId)
        candidatesFor(userAId)

        val row = transaction {
            Events.selectAll().where { Events.id eq "evt-1" }.single()
        }
        assertEquals("Otros", row[Events.category])
        assertEquals("Pago tarjeta de crédito", row[Events.description])
        assertEquals("EXPENSE", row[Events.type])
    }

    // ── PUT /api/events/{id}/category (Task 3 de SP-ajustar-saldo) ─────────────

    /**
     * El caso central del diseño: un pago del extracto cargado en la cuenta de ahorros
     * (cuenta de activo, donde por tipo de cuenta `countsAsCashFlow` daría `true`) deja de
     * contar como flujo de caja en cuanto se le pone la categoría "Pago de tarjeta" — la regla
     * de [com.jvillada.movi.shared.model.isCashFlow] gana sobre el tipo de cuenta.
     */
    @Test
    fun `PUT category actualiza la categoria y responde countsAsCashFlow derivado`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-pago", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        val res = putCategory("evt-pago", "Pago de tarjeta", userAId)
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Pago de tarjeta", body["category"]!!.jsonPrimitive.content)
        assertEquals(false, body["countsAsCashFlow"]!!.jsonPrimitive.boolean)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-pago" }.single() }
        assertEquals("Pago de tarjeta", row[Events.category])
    }

    @Test
    fun `PUT category responde 404 en vez de 403 si el evento es de otro usuario`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-a", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val res = putCategory("evt-a", "Pago de tarjeta", userBId)
        assertEquals(HttpStatusCode.NotFound, res.status)

        // el evento de A no cambió
        val row = transaction { Events.selectAll().where { Events.id eq "evt-a" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `PUT category responde 404 si el evento no existe`() = testApplication {
        wireApp()
        val res = putCategory("no-existe", "Pago de tarjeta", userAId)
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `PUT category rechaza categoria vacia con 400`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-b", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val res = putCategory("evt-b", "", userAId)
        assertEquals(HttpStatusCode.BadRequest, res.status)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-b" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `PUT category rechaza categoria de mas de 60 caracteres con 400`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-c", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val tooLong = "x".repeat(61)
        val res = putCategory("evt-c", tooLong, userAId)
        assertEquals(HttpStatusCode.BadRequest, res.status)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-c" }.single() }
        assertEquals("Otros", row[Events.category])
    }
}
