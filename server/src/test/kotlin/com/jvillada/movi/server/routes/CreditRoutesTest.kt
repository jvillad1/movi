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
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.CreditTerms
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
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

    /** Ola 16 — la cuenta corriente del usuario B, destino del desembolso. */
    private val corrienteId = "acc-corriente-b"

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
    fun `POST with blank name or negative debt is 400`() = testApplication {
        wireApp()
        val terms = """"terms":{"accountId":"","bank":"X","principal":100,"rateEa":10.0,
                        "termMonths":12,"installment":10,"dayOfMonth":1,"startDate":"2026-01-01"}"""
        val blankName = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"  ","initialDebt":100,$terms}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        val negativeDebt = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Préstamo","initialDebt":-1,$terms}""")
        }
        assertEquals(HttpStatusCode.BadRequest, negativeDebt.status)
    }

    /**
     * **Ola 14 — deuda inicial en cero es válida, y este test dice lo contrario que el anterior.**
     * Hasta acá el cero era 400. Era la regla que hacía imposible registrar bien un crédito recién
     * desembolsado: la deuda quedaba declarada en la apertura y, si además se anotaba el desembolso
     * como traspaso (lo único que pone la plata en la cuenta corriente), quedaba contada dos veces.
     * Ahora el crédito puede nacer en $0 y la deuda la crea el desembolso — sin evento de apertura
     * de por medio, porque `openingEventFor` devuelve null con saldo cero.
     */
    @Test
    fun `un credito recien desembolsado se crea en cero y sin evento de apertura`() = testApplication {
        wireApp()
        val terms = """"terms":{"accountId":"","bank":"Bancolombia","principal":257000000,"rateEa":12.0,
                        "termMonths":120,"installment":3500000,"dayOfMonth":5,"startDate":"2026-08-28"}"""
        val response = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Libranza nueva","initialDebt":0,$terms}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val accountId = body["account"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(0L, body["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        // Los términos sí quedaron: el capital original es el contrato, no la deuda de hoy.
        assertEquals(257_000_000L, body["terms"]!!.jsonObject["principal"]!!.jsonPrimitive.long)
        assertEquals(
            0L,
            transaction { Events.selectAll().where { Events.accountId eq accountId }.count() },
            "un crédito en cero no deja evento de apertura que después haya que corregir",
        )
        // Y lo dice en el wire: sin esta bandera la tarjeta de Créditos leía `paidPct = 1.0` y
        // anunciaba «100% pagado» sobre un crédito de $257.000.000 recién creado.
        assertEquals(
            false,
            hasMovements(body),
            "un crédito sin un solo movimiento no está pagado: está sin registrar",
        )
    }

    /**
     * **La clave AUSENTE significa `true`, y eso no es un descuido: es el default del campo.**
     *
     * kotlinx-serialization omite lo que vale igual que su default (`encodeDefaults = false`), así
     * que `hasMovements = true` no viaja. Da la compatibilidad que se quería de los dos lados: un
     * cliente viejo ignora un campo que no conoce, y un cliente nuevo contra un server viejo —que
     * nunca manda la clave— cae en `true` y muestra el porcentaje de siempre, en vez de reclamarle
     * un desembolso a cada crédito. La primera versión de estos tests hacía `body[...]!!` y
     * explotaba con un NPE justo en el caso sano.
     */
    private fun hasMovements(body: JsonObject): Boolean =
        body["hasMovements"]?.jsonPrimitive?.content?.toBoolean() ?: true

    /**
     * El contracaso, en el mismo endpoint: un crédito creado CON deuda inicial sí tiene un
     * movimiento (su apertura) desde el primer instante, así que su porcentaje se muestra normal.
     */
    @Test
    fun `un credito creado con deuda inicial si tiene movimientos desde el arranque`() = testApplication {
        wireApp()
        val terms = """"terms":{"accountId":"","bank":"X","principal":100000000,"rateEa":10.0,
                        "termMonths":12,"installment":10,"dayOfMonth":1,"startDate":"2026-01-01"}"""
        val response = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"Crédito viejo","initialDebt":60000000,$terms}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, hasMovements(body))
        // Y **la clave no viaja**, que es la mitad que el helper de arriba no puede afirmar: él
        // lee la ausencia como `true`, así que pasaría igual si el server la mandara explícita.
        // Esta línea es la que fija la compatibilidad con el APK 1.8, que no la conoce.
        assertEquals(
            null,
            body["hasMovements"],
            "hasMovements=true no debe viajar: es el default y un cliente viejo no lo espera",
        )
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

    // ── FinanceSummary.eventCount ─────────────────────────────────────────────
    // Hallazgo 2 de la revisión de "primeros pasos": el Dashboard usaba
    // `GET /api/events` (todo el historial) solo para saber "¿hay al menos un
    // movimiento?". `eventCount` viaja gratis en `/api/finance-summary`, que ya carga
    // todos los eventos no anulados del usuario para calcular el resumen.

    // El server no serializa el JSON con `encodeDefaults` (Serialization.kt) — un
    // `eventCount == 0` (su default) sale del wire sin la clave, no como `0` explícito.
    // Mismo comportamiento que cualquier otro campo con default: es justamente lo que
    // permite que un cliente viejo contra un server nuevo siga deserializando.
    private fun JsonObject.eventCount(): Long =
        this["eventCount"]?.jsonPrimitive?.long ?: 0L

    /**
     * `eventCount` tiene que ser del usuario completo (no del mes, no del scope — el
     * endpoint hoy no filtra por scope en absoluto, ver `nonVoidedEvents` en
     * `FinanceRoutes.kt`), y sobre todo tiene que ser por usuario: los eventos de userB
     * no pueden sumarse al conteo de userA ni viceversa.
     */
    @Test
    fun `eventCount refleja los eventos del usuario y no los de otro usuario`() = testApplication {
        wireApp()
        // userA ya tiene 1 evento del setUp (la apertura del crédito LOAN).
        assertEquals(1L, summaryOf(userAId).eventCount())
        // userB no tiene cuentas ni eventos todavía — sale del wire sin la clave (ver
        // arriba), y el default de FinanceSummary.eventCount lo cubre.
        assertEquals(0L, summaryOf(userBId).eventCount())

        val gasto = client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"evt-otro-gasto","accountId":"$cashAccountId","type":"EXPENSE","amount":50000,""" +
                    """"currency":"COP","category":"Otro","description":"Otro gasto",""" +
                    """"timestamp":${System.currentTimeMillis()}}""",
            )
        }
        assertEquals(HttpStatusCode.Created, gasto.status)

        // El segundo evento de userA lo sube a 2...
        assertEquals(2L, summaryOf(userAId).eventCount())
        // ...pero no contamina el conteo de userB, que sigue en cero.
        assertEquals(0L, summaryOf(userBId).eventCount())
    }
    // ── remindMe: la casilla «Recordarme unos días antes» ─────────────────────

    /** El crédito guardado sin recordatorio se relee sin recordatorio. */
    @Test
    fun `un credito guardado sin recordatorio se relee sin recordatorio`() = testApplication {
        wireApp()
        val put = client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson.dropLast(1) + ""","remindMe":false}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!.jsonObject
        assertEquals(false, terms["remindMe"]!!.jsonPrimitive.boolean)
    }

    /** Sin decir nada, el crédito nace avisando — el comportamiento de siempre. */
    @Test
    fun `un credito guardado sin decir nada nace con el recordatorio prendido`() = testApplication {
        wireApp()
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(validTermsJson)
        }
        // Se decodifica al modelo en vez de mirar la clave cruda: kotlinx.serialization omite
        // los valores por defecto en el JSON, así que `remindMe: true` NO viaja por el cable —
        // y eso está bien, porque el default del modelo lo repone del otro lado. Lo que hay que
        // afirmar es lo que lee el cliente, no lo que aparece en el texto.
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.decodeFromJsonElement<CreditTerms>(
            Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!,
        )
        assertTrue(terms.remindMe)
    }

    /** Editar los términos con la casilla desmarcada no la vuelve a prender. */
    @Test
    fun `editar los terminos conserva el valor del recordatorio`() = testApplication {
        wireApp()
        val sinAviso = validTermsJson.dropLast(1) + ""","remindMe":false}"""
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(sinAviso)
        }
        // Segunda edición: cambia la cuota, manda el mismo remindMe que traía cargado.
        client.put("/api/credits/$loanAccountId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(sinAviso.replace("\"installment\":4888000", "\"installment\":5000000"))
        }
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val terms = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject["terms"]!!.jsonObject
        assertEquals(false, terms["remindMe"]!!.jsonPrimitive.boolean)
        assertEquals(5_000_000L, terms["installment"]!!.jsonPrimitive.long)
    }

    // ══ Ola 16 — el desembolso nace con el crédito ════════════════════════════════════════
    //
    // El escenario real, y el que se mide de punta a punta más abajo: una libranza de
    // $257.000.000 que el banco acaba de girar a la cuenta corriente del dueño, que tenía
    // $12.400.000. Los cuatro números que tienen que quedar bien son deuda, efectivo, patrimonio
    // e ingresos del mes — y el último es el que más fácil se rompe: **un desembolso no es un
    // ingreso**, aunque sea plata que entró a la cuenta.

    /** La cuenta corriente del dueño con sus $12.400.000, para el usuario B (que arranca sin nada). */
    private fun sembrarCuentaCorriente(saldo: Long = 12_400_000L) {
        transaction {
            Accounts.insert {
                it[id]       = corrienteId
                it[userId]   = userBId
                it[name]     = "Bancolombia"
                it[type]     = "CHECKING"
                it[balance]  = saldo
                it[currency] = "COP"
            }
            Events.insert {
                it[id]                   = "evt-corriente-opening"
                it[userId]               = userBId
                it[accountId]            = corrienteId
                it[type]                 = "INCOME"
                it[amount]               = saldo
                it[currency]             = "COP"
                it[category]             = "Saldo inicial"   // OPENING_CATEGORY: fuera del flujo de caja del mes
                it[description]          = "Saldo inicial"
                it[timestamp]            = System.currentTimeMillis()
                it[eventSource]          = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
    }

    /** Hoy en formato AAAA-MM-DD: el desembolso se fecha dentro del mes en curso a propósito. */
    private fun hoyIso(): String = java.time.LocalDate.now(com.jvillada.movi.server.time.AppClock.zone).toString()

    private fun cuerpoDeLibranza(
        desembolso: Long?,
        capital: Long = 257_000_000L,
        deudaActual: Long = 0L,
        cuenta: String = corrienteId,
    ): String {
        val terms = """"terms":{"accountId":"","bank":"Bancolombia","principal":$capital,"rateEa":12.0,
                        "termMonths":120,"installment":3500000,"dayOfMonth":5,"startDate":"${hoyIso()}"}"""
        val disb = if (desembolso == null) ""
            else ""","disbursement":{"toAccountId":"$cuenta","amount":$desembolso}"""
        return """{"name":"Libranza Bancolombia","initialDebt":$deudaActual,$terms$disb}"""
    }

    private suspend fun ApplicationTestBuilder.crearLibranza(cuerpo: String) =
        client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    /**
     * **Las cuatro cifras del escenario real, en una sola operación.**
     *
     * Deuda $257.000.000 · Efectivo $269.400.000 · Patrimonio $12.400.000 · Ingresos del mes $0.
     *
     * El patrimonio no se mueve, y eso es lo correcto: pedir prestado no te hace ni más rico ni
     * más pobre, te deja con la plata y con la deuda. Los ingresos tampoco, y ese es el que la
     * ola 14 vino a arreglar — anotar la libranza como ingreso decía que el mes había entrado
     * $257 millones sin que el dueño ganara un peso.
     */
    @Test
    fun `un credito recien recibido crea la deuda y la plata en un solo guardado`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 257_000_000L))
        assertEquals(HttpStatusCode.Created, post.status)
        // Se decodifica al modelo y no se miran las claves crudas: kotlinx.serialization omite los
        // valores por defecto, así que `hasMovements: true` no viaja por el cable. Lo que hay que
        // afirmar es lo que lee el cliente, no lo que aparece en el texto.
        val created = Json.decodeFromJsonElement<CreditSummary>(Json.parseToJsonElement(post.bodyAsText()))

        // 1 · Deuda: el capital entero, y NO el doble.
        assertEquals(257_000_000L, created.account.balance)
        assertEquals(0.0, created.paidPct!!, 1e-9)
        // 2 · Y con movimientos: la tarjeta no puede decir «Falta registrar el desembolso» sobre
        //     un crédito cuyo desembolso acaba de registrarse en el mismo guardado.
        assertTrue(created.hasMovements)

        // 3 · Las dos patas vuelven en la respuesta, para que el espejo local las escriba.
        val patas = created.disbursement!!
        assertEquals(TransactionType.EXPENSE, patas.from.type)
        assertEquals(TransactionType.INCOME, patas.to.type)
        assertEquals(257_000_000L, patas.to.amount)
        assertEquals(corrienteId, patas.to.accountId)
        // Categoría reservada y fuera del flujo de caja: es lo que hace que un desembolso no sea
        // un ingreso. Lo garantiza `transferLegsFor`, la misma función que usa POST /api/transfers.
        assertEquals(TRANSFER_CATEGORY, patas.to.category)
        assertEquals(false, patas.to.countsAsCashFlow)
        // Y las dos son el mismo traspaso.
        assertEquals(patas.from.transferId, patas.to.transferId)
        assertTrue(patas.to.transferId != null)
        // El encabezado dice qué pasó, no «Traspaso»: es lo que el dueño va a leer en Movimientos.
        assertTrue(patas.to.description.startsWith("Desembolso desde"), patas.to.description)

        // 4 · Efectivo: la cuenta corriente pasó de $12.400.000 a $269.400.000.
        val cuentas = client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        val corriente = Json.parseToJsonElement(cuentas.bodyAsText()).jsonArray
            .map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == corrienteId }
        assertEquals(269_400_000L, corriente["balance"]!!.jsonPrimitive.long)

        // 5 · Patrimonio e ingresos del mes: intactos los dos.
        val resumen = client.get("/api/finance-summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
        }
        val fin = Json.parseToJsonElement(resumen.bodyAsText()).jsonObject
        assertEquals(12_400_000L, fin["balance"]!!.jsonPrimitive.long, "patrimonio: la plata y la deuda se cancelan")
        assertEquals(0L, fin["ingresos"]!!.jsonPrimitive.long, "un desembolso no es un ingreso")
        assertEquals(0L, fin["egresos"]!!.jsonPrimitive.long)
    }

    /**
     * El desembolso se fecha con `startDate` —el campo «Desembolso (AAAA-MM-DD)» que la hoja ya
     * pide—, no con «hoy». Un crédito girado el día 3 y anotado el día 20 tiene que aparecer en
     * Movimientos el día 3, que es cuando pasó.
     */
    @Test
    fun `el desembolso se fecha el dia que dice el credito, no el dia que se anota`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val cuerpo = cuerpoDeLibranza(desembolso = 257_000_000L).replace("\"startDate\":\"${hoyIso()}\"", "\"startDate\":\"2026-03-03\"")
        val post = crearLibranza(cuerpo)
        assertEquals(HttpStatusCode.Created, post.status)
        val pata = Json.parseToJsonElement(post.bodyAsText()).jsonObject["disbursement"]!!
            .jsonObject["to"]!!.jsonObject
        val millis = pata["timestamp"]!!.jsonPrimitive.long
        assertEquals("2026-03-03", com.jvillada.movi.server.time.epochMillisToAppDateString(millis))
    }

    /**
     * **El desembolso neto de costos deja la deuda en el capital, no en lo que entró.**
     *
     * $250.000.000 de un capital de $257.000.000: entra a la cuenta lo que el banco giró, y los
     * $7.000.000 que descontó quedan como deuda igual — porque se deben igual. Sin esto, el
     * crédito nacería debiendo $250M y `paidPct` diría «2% pagado» sobre un crédito que nadie
     * pagó todavía: la misma mentira optimista que la ola anterior cerró, por otra puerta.
     */
    @Test
    fun `un desembolso neto de costos deja la deuda valiendo el capital`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 250_000_000L))
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals(257_000_000L, created["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        assertEquals(0.0, created["paidPct"]!!.jsonPrimitive.double, 1e-9)

        val cuentas = client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        val corriente = Json.parseToJsonElement(cuentas.bodyAsText()).jsonArray
            .map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == corrienteId }
        assertEquals(262_400_000L, corriente["balance"]!!.jsonPrimitive.long, "solo entró lo que el banco giró")

        // Patrimonio: $12.400.000 de antes MENOS los $7.000.000 de costos que quedaron debiéndose.
        val fin = Json.parseToJsonElement(
            client.get("/api/finance-summary") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }.bodyAsText(),
        ).jsonObject
        assertEquals(5_400_000L, fin["balance"]!!.jsonPrimitive.long)
        assertEquals(0L, fin["ingresos"]!!.jsonPrimitive.long)
    }

    /** Los dos números juntos son, literalmente, cómo se contaba la deuda dos veces. */
    @Test
    fun `pedir deuda actual y desembolso a la vez es 400`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val antes = huellaDe(userBId)
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 257_000_000L, deudaActual = 257_000_000L))
        assertEquals(HttpStatusCode.BadRequest, post.status)
        assertEquals(antes, huellaDe(userBId), "no queda nada a medias: ni cuenta, ni eventos, ni términos")
    }

    @Test
    fun `un desembolso mayor que el capital es 422 y no crea nada`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val antes = huellaDe(userBId)
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 260_000_000L))
        assertEquals(HttpStatusCode.UnprocessableEntity, post.status)
        assertEquals(antes, huellaDe(userBId))
    }

    @Test
    fun `un desembolso en cero es 422 y no crea nada`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val antes = huellaDe(userBId)
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 0L))
        assertEquals(HttpStatusCode.UnprocessableEntity, post.status)
        assertEquals(antes, huellaDe(userBId))
    }

    /**
     * La cuenta destino se lee ANTES de escribir nada. Si es de otro usuario —o no existe— el
     * crédito tampoco se crea: un crédito sin su desembolso es exactamente el estado a medias que
     * esta rama vino a evitar.
     */
    @Test
    fun `un desembolso a una cuenta ajena es 404 y no crea el credito`() = testApplication {
        wireApp()
        val antes = huellaDe(userBId)
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 257_000_000L, cuenta = cashAccountId))
        assertEquals(HttpStatusCode.NotFound, post.status)
        assertEquals(antes, huellaDe(userBId))
    }

    /** Un desembolso a otra deuda no es un desembolso. */
    @Test
    fun `un desembolso a una cuenta de deuda es 422`() = testApplication {
        wireApp()
        transaction {
            Accounts.insert {
                it[id]       = "acc-otra-libranza-b"
                it[userId]   = userBId
                it[name]     = "Otra libranza"
                it[type]     = "LOAN"
                it[currency] = "COP"
            }
        }
        val antes = huellaDe(userBId)
        val post = crearLibranza(cuerpoDeLibranza(desembolso = 1_000_000L, cuenta = "acc-otra-libranza-b"))
        assertEquals(HttpStatusCode.UnprocessableEntity, post.status)
        assertEquals(antes, huellaDe(userBId))
    }

    /**
     * **El camino viejo, sin un solo cambio.** Es el cuerpo exacto que manda el APK 1.9 que el
     * dueño tiene instalado: sin la clave `disbursement`, con su deuda actual, y sin ningún
     * movimiento de traspaso de por medio.
     */
    @Test
    fun `el cuerpo del APK 1_9 sigue creando el credito como siempre`() = testApplication {
        wireApp()
        sembrarCuentaCorriente()
        val post = crearLibranza(cuerpoDeLibranza(desembolso = null, deudaActual = 200_000_000L))
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals(200_000_000L, created["account"]!!.jsonObject["balance"]!!.jsonPrimitive.long)
        assertTrue(created["disbursement"] == null || created["disbursement"] is JsonNull)
        // La cuenta corriente no se movió: un crédito viejo no pone plata en ningún lado.
        val cuentas = client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        val corriente = Json.parseToJsonElement(cuentas.bodyAsText()).jsonArray
            .map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == corrienteId }
        assertEquals(12_400_000L, corriente["balance"]!!.jsonPrimitive.long)
    }

    /**
     * **Todo lo que un alta puede dejar escrito: la cuenta, sus eventos y sus términos.**
     *
     * La primera versión de estas guardas contaba solo `Credits` —los términos— y por eso **no
     * podía fallar cuando debía**: la revisión mutó la ruta para insertar la cuenta LOAN huérfana
     * ANTES de cada validación y los cuatro tests siguieron en verde, porque una cuenta sin
     * términos no movía el contador. El código de producción estaba bien; la aserción no servía
     * para saberlo.
     *
     * Se comparan las tres cifras contra la foto de antes del POST, y no contra cero, para que la
     * misma función sirva en el test que siembra una cuenta LOAN a propósito (el del desembolso a
     * una cuenta de deuda). La tesis entera de la rama es «nada a medias»: la única forma de
     * afirmarla es mirar todo lo que la transacción pudo haber tocado.
     */
    private data class HuellaEnLaBase(val cuentas: Long, val eventos: Long, val creditos: Long)

    private fun huellaDe(uid: String): HuellaEnLaBase = transaction {
        HuellaEnLaBase(
            cuentas  = Accounts.selectAll().where { Accounts.userId eq uid }.count(),
            eventos  = Events.selectAll().where { Events.userId eq uid }.count(),
            creditos = Credits.selectAll().where { Credits.userId eq uid }.count(),
        )
    }
}
