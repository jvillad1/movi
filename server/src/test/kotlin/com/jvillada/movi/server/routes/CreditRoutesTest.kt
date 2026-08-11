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
    fun `POST creates account, opening debt and terms atomically`() = testApplication {
        wireApp()
        val post = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"name":"Crédito Vehículo Santander","initialDebt":160000000,
                   "terms":{"accountId":"","bank":"Santander","principal":160000000,"rateEa":21.56,
                            "termMonths":72,"installment":4550030,"dayOfMonth":25,"startDate":"2025-11-25"}}"""
            )
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals(160_000_000L, created["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        assertEquals("Santander", created["terms"]!!.jsonObject["bank"]!!.jsonPrimitive.content)
        // deuda == principal recién creado → 0% pagado
        assertEquals(0.0, created["paidPct"]!!.jsonPrimitive.double, 1e-9)

        // GET refleja lo mismo derivado desde el evento de apertura persistido
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        assertEquals(160_000_000L, arr[0].jsonObject["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
    }

    @Test
    fun `POST with blank name or non-positive debt is 400`() = testApplication {
        wireApp()
        val terms = """"terms":{"accountId":"","bank":"X","principal":100,"rateEa":10.0,
                        "termMonths":12,"installment":10,"dayOfMonth":1,"startDate":"2026-01-01"}"""
        val blankName = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"  ","initialDebt":100,$terms}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        val zeroDebt = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Préstamo","initialDebt":0,$terms}""")
        }
        assertEquals(HttpStatusCode.BadRequest, zeroDebt.status)
    }

    // ── POST /{accountId}/balance-adjustment ──────────────────────────────────
    // La deuda de A arranca en 100.000.000 (evento de apertura sembrado en setUp).

    private suspend fun ApplicationTestBuilder.adjust(userId: String, accountId: String, target: Long) =
        client.post("/api/credits/$accountId/balance-adjustment") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"targetBalance":$target}""")
        }

    private suspend fun ApplicationTestBuilder.debtOf(userId: String, accountId: String): Long {
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}") }
        return Json.parseToJsonElement(res.bodyAsText()).jsonArray
            .map { it.jsonObject }
            .first { it["account"]!!.jsonObject["id"]!!.jsonPrimitive.content == accountId }
            .let { it["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long }
    }

    private suspend fun ApplicationTestBuilder.eventsOf(userId: String, accountId: String) =
        Json.parseToJsonElement(
            client.get("/api/events?accountId=$accountId") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            }.bodyAsText()
        ).jsonArray.map { it.jsonObject }

    private suspend fun ApplicationTestBuilder.summaryOf(userId: String) =
        Json.parseToJsonElement(
            client.get("/api/finance-summary") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            }.bodyAsText()
        ).jsonObject

    /**
     * El ajuste NO es flujo de caja del mes.
     *
     * Este es el renglón que hace falta cuidar de todo el feature: bajar la deuda al saldo real
     * del banco registra un INCOME por la diferencia, y sin filtrar por tipo de cuenta el
     * Dashboard reportaba ese INCOME como "Ingresos del mes". Con la deuda real de la libranza
     * eso son sesenta millones de pesos de ingreso inventado, en la cifra más visible de la app.
     *
     * Se comprueba también que el gasto de una cuenta de activo SÍ sigue contando: el filtro
     * tiene que excluir la deuda, no vaciar el resumen.
     */
    @Test
    fun `un ajuste de deuda no entra como ingreso del mes`() = testApplication {
        wireApp()
        val antes = summaryOf(userAId)
        // La apertura del crédito (100M EXPENSE sobre la cuenta LOAN) tampoco es egreso del mes.
        assertEquals(0L, antes["ingresos"]!!.jsonPrimitive.long)
        assertEquals(0L, antes["egresos"]!!.jsonPrimitive.long)

        assertEquals(HttpStatusCode.OK, adjust(userAId, loanAccountId, 40_000_000L).status)
        assertEquals(40_000_000L, debtOf(userAId, loanAccountId))

        val despues = summaryOf(userAId)
        assertEquals(
            0L,
            despues["ingresos"]!!.jsonPrimitive.long,
            "el abono de 60.000.000 que bajó la deuda no puede leerse como ingreso del mes",
        )
        assertEquals(0L, despues["egresos"]!!.jsonPrimitive.long)

        // Control positivo: un gasto de una cuenta de activo sí cuenta.
        val gasto = client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"evt-mercado","accountId":"$cashAccountId","type":"EXPENSE","amount":250000,""" +
                    """"currency":"COP","category":"Mercado","description":"Mercado",""" +
                    """"timestamp":${System.currentTimeMillis()}}""",
            )
        }
        assertEquals(HttpStatusCode.Created, gasto.status)
        assertEquals(250_000L, summaryOf(userAId)["egresos"]!!.jsonPrimitive.long)
    }

    @Test
    fun `ajustar hacia arriba deja la deuda exactamente en el objetivo`() = testApplication {
        wireApp()
        val res = adjust(userAId, loanAccountId, 226_465_057L)
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            226_465_057L,
            Json.parseToJsonElement(res.bodyAsText()).jsonObject["account"]!!
                .jsonObject["balance"]!!.jsonPrimitive.long,
        )
        assertEquals(226_465_057L, debtOf(userAId, loanAccountId))

        val adjustment = eventsOf(userAId, loanAccountId).single { it["id"]!!.jsonPrimitive.content != "evt-loan-a-opening" }
        assertEquals("EXPENSE", adjustment["type"]!!.jsonPrimitive.content)
        assertEquals(126_465_057L, adjustment["amount"]!!.jsonPrimitive.long)
        assertEquals("Ajuste al saldo del banco — quedó en $226.465.057", adjustment["description"]!!.jsonPrimitive.content)
        // `source` se omite del JSON cuando vale el default (MANUAL).
        assertEquals("MANUAL", adjustment["source"]?.jsonPrimitive?.content ?: "MANUAL")
        assertEquals("RECONCILED", adjustment["reconciliationStatus"]!!.jsonPrimitive.content)
        assertEquals("Ajuste de saldo", adjustment["category"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ajustar hacia abajo registra un abono y deja la deuda en el objetivo`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, adjust(userAId, loanAccountId, 40_000_000L).status)
        assertEquals(40_000_000L, debtOf(userAId, loanAccountId))

        val adjustment = eventsOf(userAId, loanAccountId).single { it["id"]!!.jsonPrimitive.content != "evt-loan-a-opening" }
        assertEquals("INCOME", adjustment["type"]!!.jsonPrimitive.content)
        assertEquals(60_000_000L, adjustment["amount"]!!.jsonPrimitive.long)
    }

    @Test
    fun `ajustes sucesivos siguen cayendo en el objetivo`() = testApplication {
        wireApp()
        adjust(userAId, loanAccountId, 226_465_057L)
        adjust(userAId, loanAccountId, 226_352_287L)
        assertEquals(226_352_287L, debtOf(userAId, loanAccountId))
        assertEquals(3, eventsOf(userAId, loanAccountId).size)
    }

    @Test
    fun `ajustar al mismo saldo no registra evento`() = testApplication {
        wireApp()
        val res = adjust(userAId, loanAccountId, 100_000_000L)
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(100_000_000L, debtOf(userAId, loanAccountId))
        assertEquals(1, eventsOf(userAId, loanAccountId).size)   // solo el de apertura
    }

    @Test
    fun `ajustar el credito de otro usuario es 404 y no toca su saldo`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.NotFound, adjust(userBId, loanAccountId, 1L).status)
        assertEquals(100_000_000L, debtOf(userAId, loanAccountId))
        assertEquals(1, eventsOf(userAId, loanAccountId).size)
        assertEquals("[]", client.get("/api/events?accountId=$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
        }.bodyAsText())
    }

    @Test
    fun `ajustar una cuenta inexistente es 404`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.NotFound, adjust(userAId, "acc-que-no-existe", 1_000L).status)
    }

    @Test
    fun `ajustar una cuenta no LOAN es 422`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.UnprocessableEntity, adjust(userAId, cashAccountId, 1_000L).status)
    }

    @Test
    fun `objetivo negativo o absurdo es 400 y no registra nada`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, adjust(userAId, loanAccountId, -1L).status)
        assertEquals(HttpStatusCode.BadRequest, adjust(userAId, loanAccountId, 1_000_000_000_001L).status)
        assertEquals(100_000_000L, debtOf(userAId, loanAccountId))
        assertEquals(1, eventsOf(userAId, loanAccountId).size)
    }

    @Test
    fun `ajustar a cero deja la deuda saldada`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, adjust(userAId, loanAccountId, 0L).status)
        assertEquals(0L, debtOf(userAId, loanAccountId))
    }

    @Test
    fun `el ajuste conserva los terminos y recalcula el pct pagado`() = testApplication {
        wireApp()
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        val res = adjust(userAId, loanAccountId, 131_000_000L)   // mitad del principal (262M)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Bancolombia", body["terms"]!!.jsonObject["bank"]!!.jsonPrimitive.content)
        assertEquals(0.5, body["paidPct"]!!.jsonPrimitive.double, 1e-9)
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
