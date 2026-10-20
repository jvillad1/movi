package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Apartados
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
import kotlinx.coroutines.runBlocking

/**
 * HTTP-level tests for GET/POST/PUT/DELETE /api/apartados.
 *
 * Same harness pattern as CreditRoutesTest.kt: H2 in-memory DB (PostgreSQL compat mode), a
 * test-local JWT secret/verifier, and the full serialization+jwt+routing plugin chain wired
 * through a local `wireApp()` helper.
 */
class ApartadoRoutesTest {

    private val testSecret = "test-secret-for-apartado-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-apt"
    private val userBId = "user-b-apt"
    private val userAEmail = "a@apt.test"
    private val userBEmail = "b@apt.test"

    private val cashAccountId = "acc-cash-a"      // Dinero — cuenta válida para apartar
    private val cardAccountId = "acc-card-a"      // Deuda — rechazada: no se aparta
    private val cashAccountBId = "acc-cash-b"
    private val condicionadaId = "acc-cond-a"     // condicionada: no se aparta

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:apartado_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Apartados, Goals, CardPaymentDismissals, Cards, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, Cards, Goals, Apartados,
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
            Accounts.insert {
                it[id]            = condicionadaId
                it[userId]        = userAId
                it[name]          = "Cesantías"
                it[type]          = "CASH"
                it[currency]      = "COP"
                it[conditionedTo] = "Vivienda"
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

    private val auth get() = "Bearer ${tokenFor(userAId)}"

    private fun json(
        accountId: String = cashAccountId, monto: Long = 8_000_000L, proposito: String = "Colegio",
        categoria: String? = "Hija", id: String = "", desde: Long = 0L,
    ): String {
        val cat = categoria?.let { """"$it"""" } ?: "null"
        return """{"id":"$id","accountId":"$accountId","proposito":"$proposito","monto":$monto,"categoria":$cat,"desde":$desde}"""
    }

    private suspend fun ApplicationTestBuilder.crear(cuerpo: String, token: String = auth) =
        client.post("/api/apartados") {
            header(HttpHeaders.Authorization, token)
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.listar(): kotlinx.serialization.json.JsonArray =
        Json.parseToJsonElement(
            client.get("/api/apartados") { header(HttpHeaders.Authorization, auth) }.bodyAsText(),
        ).jsonArray

    private fun gasto(id: String, monto: Long, categoria: String, cuenta: String = cashAccountId, ts: Long = System.currentTimeMillis() + 1_000) =
        transaction {
            Events.insert {
                it[Events.id]                   = id
                it[userId]                      = userAId
                it[accountId]                   = cuenta
                it[type]                        = "EXPENSE"
                it[amount]                      = monto
                it[currency]                    = "COP"
                it[Events.category]             = categoria
                it[description]                 = categoria
                it[timestamp]                   = ts
                it[eventSource]                 = "MANUAL"
                it[reconciliationStatus]        = "UNCONFIRMED"
            }
        }

    @Test
    fun `POST y GET devuelven el apartado con restante igual al monto`() = testApplication {
        wireApp()
        val res = crear(json())
        assertEquals(HttpStatusCode.Created, res.status)
        val creado = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertTrue(creado["id"]!!.jsonPrimitive.content.startsWith("apt_"))
        assertEquals(8_000_000L, creado["restante"]!!.jsonPrimitive.long)
        assertTrue(creado["desde"]!!.jsonPrimitive.long > 0L, "desde = ahora cuando el cliente manda 0")

        val lista = listar()
        assertEquals(1, lista.size)
        assertEquals("Colegio", lista[0].jsonObject["proposito"]!!.jsonPrimitive.content)
        assertEquals(8_000_000L, lista[0].jsonObject["restante"]!!.jsonPrimitive.long)
    }

    @Test
    fun `POST con el mismo id es un upsert, 200 y conserva desde`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, crear(json(id = "apt-1", desde = 5L)).status)
        val otra = crear(json(id = "apt-1", monto = 9_000_000L, proposito = " Colegio 2 "))
        assertEquals(HttpStatusCode.OK, otra.status)
        val lista = listar()
        assertEquals(1, lista.size)
        assertEquals(9_000_000L, lista[0].jsonObject["monto"]!!.jsonPrimitive.long)
        assertEquals("Colegio 2", lista[0].jsonObject["proposito"]!!.jsonPrimitive.content)
        assertEquals(5L, lista[0].jsonObject["desde"]!!.jsonPrimitive.long)
    }

    @Test
    fun `un gasto de la categoria despues de desde consume el apartado, uno anulado no`() = testApplication {
        wireApp()
        crear(json(id = "apt-1", desde = 1_000L))
        gasto("g-ok", 4_000_000L, "Hija")
        gasto("g-antes", 1_000_000L, "Hija", ts = 500L)
        gasto("g-otra-cuenta", 1_000_000L, "Hija", cuenta = cardAccountId)
        gasto("g-otra-categoria", 1_000_000L, "Comida")
        assertEquals(4_000_000L, listar()[0].jsonObject["restante"]!!.jsonPrimitive.long)

        transaction {
            VoidEvents.insert {
                it[id] = "v1"; it[userId] = userAId; it[originalEventId] = "g-ok"; it[timestamp] = 1L
            }
        }
        assertEquals(8_000_000L, listar()[0].jsonObject["restante"]!!.jsonPrimitive.long)
    }

    @Test
    fun `el restante no baja de cero`() = testApplication {
        wireApp()
        crear(json(id = "apt-1", monto = 1_000_000L, desde = 1_000L))
        gasto("g-1", 5_000_000L, "Hija")
        assertEquals(0L, listar()[0].jsonObject["restante"]!!.jsonPrimitive.long)
    }

    @Test
    fun `cuentas que no se pueden apartar y datos invalidos`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, crear(json(accountId = cardAccountId)).status)
        assertEquals(HttpStatusCode.BadRequest, crear(json(accountId = condicionadaId)).status)
        assertEquals(HttpStatusCode.NotFound, crear(json(accountId = cashAccountBId)).status)
        assertEquals(HttpStatusCode.BadRequest, crear(json(monto = 0)).status)
        assertEquals(HttpStatusCode.BadRequest, crear(json(proposito = "  ")).status)
        assertEquals(HttpStatusCode.BadRequest, crear(json(proposito = "x".repeat(61))).status)
        assertTrue(listar().isEmpty())
    }

    @Test
    fun `PUT actualiza, 404 si no es del usuario, y DELETE borra`() = testApplication {
        wireApp()
        crear(json(id = "apt-1"))
        val put = client.put("/api/apartados/apt-1") {
            header(HttpHeaders.Authorization, auth)
            header(HttpHeaders.ContentType, "application/json")
            setBody(json(monto = 3_000_000L, categoria = " "))
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val actualizado = listar()[0].jsonObject
        assertEquals(3_000_000L, actualizado["monto"]!!.jsonPrimitive.long)
        assertTrue(actualizado["categoria"] is kotlinx.serialization.json.JsonNull)

        val ajeno = client.put("/api/apartados/apt-1") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(json(accountId = cashAccountBId))
        }
        assertEquals(HttpStatusCode.NotFound, ajeno.status)
        val delAjeno = client.delete("/api/apartados/apt-1") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals(HttpStatusCode.NotFound, delAjeno.status)

        val del = client.delete("/api/apartados/apt-1") { header(HttpHeaders.Authorization, auth) }
        assertEquals(HttpStatusCode.NoContent, del.status)
        assertTrue(listar().isEmpty())
    }

    @Test
    fun `borrar la cuenta se lleva sus apartados`() = testApplication {
        wireApp()
        crear(json(id = "apt-1"))
        val res = client.delete("/api/accounts/$cashAccountId") { header(HttpHeaders.Authorization, auth) }
        assertTrue(res.status.value in 200..299, "borrar la cuenta: ${res.status}")
        assertTrue(listar().isEmpty())
    }
}
