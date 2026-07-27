package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.screens.SCREEN_SEED
import com.jvillada.movi.server.screens.seedScreens
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level tests for GET /api/screens/{slug} (Task 2 of SDUI movi F1).
 *
 * Same harness pattern as CreditRoutesTest.kt: own H2 in-memory DB
 * (`screens_routes_test`), a test-local JWT secret/verifier, and the full
 * serialization+jwt+routing plugin chain wired through wireApp().
 *
 * NOTE on section count: the design spec
 * (docs/superpowers/specs/2026-07-26-sdui-movi-design.md) enumerates the seed
 * as HERO_BALANCE -> ACCOUNTS_SUMMARY -> BANNER(Alertas) -> LINK_LIST(Explora)
 * -> BANNER(IA) -- five sections, matching the five items of the current
 * hardcoded Dashboard (Balance, Mis cuentas, Alertas, Patrimonio, AI prompt).
 * Some plan prose calls this "6 secciones"; that count does not match the
 * spec's own explicit, locked list, so these tests assert 5 (see task report).
 */
class ScreenRoutesTest {

    private val testSecret = "test-secret-for-screens-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-screens"
    private val userAEmail = "a@screens.test"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:screens_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Screens, PushSubscriptions, Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions, PushSubscriptions, Screens,
            )

            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
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

    private fun tokenFor(userId: String): String = mintToken(userId, userAEmail)

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
    fun `200 with seeded dashboard has 5 sections starting with HERO_BALANCE`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("dashboard", body["slug"]!!.jsonPrimitive.content)
        assertEquals(1, body["version"]!!.jsonPrimitive.content.toInt())
        val sections = body["sections"]!!.jsonArray
        assertEquals(5, sections.size)
        assertEquals("HERO_BALANCE", sections[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("ACCOUNTS_SUMMARY", sections[1].jsonObject["type"]!!.jsonPrimitive.content)

        val explora = sections[3].jsonObject
        assertEquals("LINK_LIST", explora["type"]!!.jsonPrimitive.content)
        assertEquals("Explora", explora["title"]!!.jsonPrimitive.content)
        val exploraTitles = explora["cards"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals(listOf("Inversiones", "Créditos", "Metas", "Suscripciones"), exploraTitles)

        val aiBanner = sections[4].jsonObject
        assertEquals("BANNER", aiBanner["type"]!!.jsonPrimitive.content)
        assertEquals("✦ Pregúntale a Movi AI", aiBanner["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `304 when If-None-Match equals current version`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.IfNoneMatch, "1")
        }
        assertEquals(HttpStatusCode.NotModified, res.status)
    }

    @Test
    fun `404 for unknown slug`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/nope-does-not-exist") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `404 and no 500 on corrupt sections_json`() = testApplication {
        transaction {
            Screens.insert {
                it[slug] = "corrupt"
                it[version] = 1
                it[sectionsJson] = "{{{"
                it[active] = true
                it[updatedAt] = System.currentTimeMillis()
            }
        }
        wireApp()

        val res = client.get("/api/screens/corrupt") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `401 without token`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `per-slug seed adds new slugs but never overwrites existing`() = testApplication {
        transaction { seedScreens() }

        // Simula una edición manual por SQL: dashboard pasa a v7.
        transaction {
            Screens.update({ Screens.slug eq "dashboard" }) {
                it[version] = 7
            }
        }

        // Re-invocar seedScreens con una lista extendida (dashboard sin cambios + un
        // slug fantasma nuevo) NO debe pisar la edición de dashboard, y SÍ debe agregar
        // el slug nuevo -- esta es la lección del deferido NeoVita.
        val phantom = SCREEN_SEED.first().copy(slug = "phantom", version = 1)
        transaction { seedScreens(SCREEN_SEED + phantom) }

        wireApp()

        val dashboardRes = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, dashboardRes.status)
        val dashboardBody = Json.parseToJsonElement(dashboardRes.bodyAsText()).jsonObject
        assertEquals(7, dashboardBody["version"]!!.jsonPrimitive.content.toInt())

        val phantomRes = client.get("/api/screens/phantom") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, phantomRes.status)
        assertTrue(Json.parseToJsonElement(phantomRes.bodyAsText()).jsonObject.containsKey("sections"))
    }
}
