package com.jvillada.movi.server.routes

import org.jetbrains.exposed.sql.and
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import io.ktor.client.request.delete
import org.jetbrains.exposed.sql.selectAll
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-level tests for POST /api/statements/import (Task 2 of SP-detect-on-import).
 * Same harness pattern as CreditRoutesTest.kt / SubscriptionRoutesTest.kt: H2
 * in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and the
 * full serialization+jwt+routing plugin chain wired through a local `wireApp()`.
 *
 * Verifies that importing a statement silently triggers subscription detection —
 * no separate call to /api/subscriptions/detect should be needed.
 */
class StatementRoutesTest {

    private val testSecret = "test-secret-for-statement-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-statements"
    private val userAEmail = "a@statements.test"

    private val accountAId = "acc-tc-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:statement_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

            // ── CREDIT_CARD account for A (no events) ───────────────────────
            Accounts.insert {
                it[id]       = accountAId
                it[userId]   = userAId
                it[name]     = "Tarjeta de Crédito"
                it[type]     = "CREDIT_CARD"
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
        mintToken(userId, userAEmail)

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

    // ── Body helpers ─────────────────────────────────────────────────────────

    private fun parsedTx(id: String, date: String, merchant: String, amount: Long) =
        """{"id":"$id","date":"$date","merchant":"$merchant","amount":$amount,"currency":"COP",
            "type":"EXPENSE","category":"Otros","description":"$merchant","rawText":""}"""

    private fun importBody(txs: String) =
        """{"statementId":"st-test","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[$txs],"reconciliations":[],"skipped":[]}"""

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `import triggers subscription detection automatically`() = testApplication {
        wireApp()
        val txs = listOf(
            parsedTx("p1", "2026-04-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p2", "2026-05-14", "PAYU*NETFLIX", 44_900),
            parsedTx("p3", "2026-06-14", "PAYU*NETFLIX", 44_900),
        ).joinToString(",")
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(txs))
        }
        assertEquals(HttpStatusCode.OK, res.status)

        // SIN llamar /detect: el import debe haber disparado la detección solo
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(1, subs.size)
        val netflix = subs[0].jsonObject
        assertEquals("netflix", netflix["merchantKey"]!!.jsonPrimitive.content)
        // F39: nada nace activo — ni siquiera HIGH confidence salta a AUTO. La detección
        // disparada por el import deja la fila CANDIDATE, igual que el /detect manual.
        assertEquals("CANDIDATE", netflix["status"]!!.jsonPrimitive.content)
    }

    /**
     * **Los movimientos de un extracto llegan en el mismo orden que el resto de la app.**
     *
     * Sin `ORDER BY`, esta lista salía en el orden físico de la tabla — el que un UPDATE o un
     * VACUUM cambia sin avisar — y es la pantalla donde el dueño repasa fila por fila contra el
     * PDF. Es el mismo bug que tenía la bandeja de SMS: invisible con cuatro filas, arbitrario con
     * cuarenta.
     *
     * Se importan **en desorden a propósito**: si el orden desapareciera, lo más probable es que
     * salgan como entraron y el test pasaría sin haber probado nada.
     */
    @Test
    fun `los movimientos de un extracto llegan del mas reciente al mas viejo`() = testApplication {
        wireApp()
        val txs = listOf(
            parsedTx("p1", "2026-06-14", "DEL MEDIO", 20_000),
            parsedTx("p2", "2026-06-02", "EL MAS VIEJO", 30_000),
            parsedTx("p3", "2026-06-28", "EL MAS NUEVO", 10_000),
        ).joinToString(",")
        client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(txs))
        }

        val importId = Json.parseToJsonElement(
            client.get("/api/statements/imports") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            }.bodyAsText()
        ).jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content

        val detalle = Json.parseToJsonElement(
            client.get("/api/statements/imports/$importId") {
                header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            }.bodyAsText()
        ).jsonObject
        val conceptos = detalle["events"]!!.jsonArray
            .map { it.jsonObject["description"]!!.jsonPrimitive.content }

        assertEquals(listOf("EL MAS NUEVO", "DEL MEDIO", "EL MAS VIEJO"), conceptos)
    }

    @Test
    fun `import without recurring patterns creates no subscriptions`() = testApplication {
        wireApp()
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody(parsedTx("p1", "2026-06-11", "EXITO COUNTRY", 312_400)))
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(0, subs.size)
    }

    // ── Conciliar: solo contra lo que de verdad es el mismo movimiento ────────────────────────

    private fun sembrar(id: String, cuenta: String, monto: Long, estado: String = "UNCONFIRMED", cuando: Long = System.currentTimeMillis()) = transaction {
        Events.insert {
            it[Events.id] = id
            it[Events.userId] = userAId
            it[Events.accountId] = cuenta
            it[Events.type] = "EXPENSE"
            it[Events.amount] = monto
            it[Events.currency] = "COP"
            it[Events.category] = "Comida"
            it[Events.description] = "anotado"
            it[Events.timestamp] = cuando
            it[Events.reconciliationStatus] = estado
        }
    }

    private fun reconciliacion(parsedId: String, existente: String, monto: Long) =
        """{"parsedId":"$parsedId","existingEventId":"$existente","confirm":true,"categorySource":"MANUAL",
            "descriptionSource":"MANUAL","merchantSource":"MANUAL","parsed":${parsedTx(parsedId, "2026-06-14", "COMPRA", monto)}}"""

    private fun eventosDeLaTarjeta() = transaction {
        Events.selectAll().where { Events.accountId eq accountAId }.map { it[Events.id] to it[Events.reconciliationStatus] }
    }

    @Test
    fun `conciliar confirma el movimiento, y dos filas contra el mismo o contra otra cuenta entran como nuevas`() = testApplication {
        wireApp()
        transaction {
            Accounts.insert {
                it[id] = "acc-ahorros-a"; it[userId] = userAId; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
        sembrar("ev-sms", accountAId, 50_000)
        sembrar("ev-ahorros", "acc-ahorros-a", 5_000_000, estado = "RECONCILED")

        val body = """{"statementId":"st-rec","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[],"reconciliations":[${reconciliacion("r1", "ev-sms", 50_000)},${reconciliacion("r2", "ev-sms", 50_000)},${reconciliacion("r3", "ev-ahorros", 5_000_000)}],"skipped":[]}"""
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val tarjeta = eventosDeLaTarjeta()
        // El SMS quedó confirmado por el extracto.
        assertEquals("RECONCILED", tarjeta.single { it.first == "ev-sms" }.second)
        // La segunda compra de $50.000 y la de $5.000.000 (que no era la pata de ahorros) entraron nuevas.
        assertEquals(3, tarjeta.size, "ev-sms + 2 filas nuevas: $tarjeta")
        val ahorros = transaction { Events.selectAll().where { Events.id eq "ev-ahorros" }.single()[Events.statementImportId] }
        assertEquals(null, ahorros, "el movimiento de otra cuenta no se toca")
    }

    /**
     * Si la propuesta era de otra cuenta pero en la del extracto está la pareja de verdad, se concilia
     * con esa en vez de crear la compra otra vez: antes quedaba duplicada.
     */
    @Test
    fun `una propuesta de otra cuenta busca la pareja en la cuenta del extracto antes de duplicar`() = testApplication {
        wireApp()
        transaction {
            Accounts.insert {
                it[id] = "acc-ahorros-b"; it[userId] = userAId; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
        val dia = com.jvillada.movi.server.time.appDateToEpochMillis(java.time.LocalDate.parse("2026-06-14"))
        sembrar("ev-traspaso-ahorros", "acc-ahorros-b", 500_000, estado = "RECONCILED", cuando = dia)
        sembrar("ev-sms-tarjeta", accountAId, 500_000, cuando = dia + 86_400_000L)

        val body = """{"statementId":"st-pareja","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[],"reconciliations":[${reconciliacion("p1", "ev-traspaso-ahorros", 500_000)}],"skipped":[]}"""
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val tarjeta = eventosDeLaTarjeta()
        assertEquals(listOf("ev-sms-tarjeta" to "RECONCILED"), tarjeta, "se concilió con el SMS de la tarjeta, sin duplicar")
    }

    @Test
    fun `el pago de la tarjeta del extracto de ahorros no se vuelve gasto del mes`() = testApplication {
        wireApp()
        val pago = """{"id":"pt","date":"2026-06-10","merchant":"PAGO AUTOM TC","amount":9809799,"currency":"COP",
            "type":"EXPENSE","category":"Pago de tarjeta","description":"PAGO AUTOM TC 1234","rawText":""}"""
        val compra = """{"id":"pc","date":"2026-06-11","merchant":"EXITO","amount":80000,"currency":"COP",
            "type":"EXPENSE","category":"Pago de tarjeta","description":"EXITO COUNTRY","rawText":""}"""
        client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(importBody("$pago,$compra"))
        }
        val categorias = transaction {
            Events.selectAll().where { Events.userId eq userAId }.associate { it[Events.amount] to it[Events.category] }
        }
        assertEquals("Pago de tarjeta", categorias[9_809_799L])
        assertEquals("Otros", categorias[80_000L], "una compra real mal etiquetada no se esconde del mes")
    }

    // ── Deshacer, reimportar y fechas ─────────────────────────────────────────────────────────

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.importar(body: String) =
        client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }

    private fun ultimoImporte() = transaction {
        StatementImports.selectAll().orderBy(StatementImports.importedAt, org.jetbrains.exposed.sql.SortOrder.DESC).first()[StatementImports.id]
    }

    @Test
    fun `deshacer un importe anula lo que creo, suelta lo que concilio y borra el registro`() = testApplication {
        wireApp()
        val dia = com.jvillada.movi.server.time.appDateToEpochMillis(java.time.LocalDate.parse("2026-06-14"))
        sembrar("ev-sms-real", accountAId, 50_000, cuando = dia)
        val body = """{"statementId":"st-undo","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[${parsedTx("n1", "2026-06-10", "CAFE", 12_000)}],"reconciliations":[${reconciliacion("r1", "ev-sms-real", 50_000)}],"skipped":[]}"""
        assertEquals(HttpStatusCode.OK, importar(body).status)
        val importe = ultimoImporte()
        val creado = transaction { Events.selectAll().where { (Events.statementImportId eq importe) and (Events.id neq "ev-sms-real") }.single()[Events.id] }

        val res = client.delete("/api/statements/imports/$importe") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.NoContent, res.status)

        transaction {
            assertEquals(1L, VoidEvents.selectAll().where { VoidEvents.originalEventId eq creado }.count(), "lo creado queda anulado")
            assertEquals(0L, VoidEvents.selectAll().where { VoidEvents.originalEventId eq "ev-sms-real" }.count(), "lo conciliado no se anula")
            assertEquals(null, Events.selectAll().where { Events.id eq "ev-sms-real" }.single()[Events.statementImportId])
            assertEquals(0L, StatementImports.selectAll().where { StatementImports.id eq importe }.count())
        }
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/statements/imports/$importe") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }

    @Test
    fun `reimportar no le quita al primer importe lo que ya habia conciliado`() = testApplication {
        wireApp()
        val dia = com.jvillada.movi.server.time.appDateToEpochMillis(java.time.LocalDate.parse("2026-06-14"))
        sembrar("ev-conciliado", accountAId, 70_000, cuando = dia)
        val body = """{"statementId":"st-re","accountId":"acc-tc-a","bankName":"Bancolombia","period":"2026-06",
            "imports":[],"reconciliations":[${reconciliacion("r1", "ev-conciliado", 70_000)}],"skipped":[]}"""
        assertEquals(HttpStatusCode.OK, importar(body).status)
        val primero = ultimoImporte()
        Thread.sleep(5)
        assertEquals(HttpStatusCode.OK, importar(body).status)
        assertEquals(primero, transaction { Events.selectAll().where { Events.id eq "ev-conciliado" }.single()[Events.statementImportId] })
    }

    @Test
    fun `una fecha en otro formato se entiende y una ilegible no cae en hoy`() = testApplication {
        wireApp()
        assertEquals(java.time.LocalDate.of(2026, 5, 28), fechaDelExtracto("2026-5-28"))
        assertEquals(java.time.LocalDate.of(2026, 5, 28), fechaDelExtracto("28/05/2026"))
        assertEquals(null, fechaDelExtracto("mayo 28"))
        val res = importar(importBody("${parsedTx("f1", "2026-5-28", "UNO", 11_000)},${parsedTx("f2", "ayer", "DOS", 22_000)}"))
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("\"sinFecha\"" in res.bodyAsText() && "1" in res.bodyAsText(), res.bodyAsText())
        val filas = transaction { Events.selectAll().where { Events.accountId eq accountAId }.map { it[Events.amount] to it[Events.timestamp] } }
        assertEquals(listOf(11_000L), filas.map { it.first }, "la ilegible no se importa")
        assertEquals(java.time.LocalDate.of(2026, 5, 28), com.jvillada.movi.server.time.epochMillisToAppDate(filas.single().second))
    }
}
