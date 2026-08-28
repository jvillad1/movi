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
import com.jvillada.movi.shared.model.DASHBOARD_LAYOUT_VERSION
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
 * Ola 4 (F9/F40): el seed es `defaultDashboardDefinition()` de `:core` (generación 2 —
 * HERO_BALANCE -> UPCOMING_PAYMENTS -> ALERTS -> QUICK_LINKS_WITH_TOTALS -> BANNER(IA)), y
 * `seedScreens` ahora también ACTUALIZA filas de una generación anterior (ver el último test).
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
    fun `200 with seeded dashboard serves the Ola 4 layout (generation 2)`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("dashboard", body["slug"]!!.jsonPrimitive.content)
        assertEquals(DASHBOARD_LAYOUT_VERSION, body["version"]!!.jsonPrimitive.content.toInt())
        val sections = body["sections"]!!.jsonArray
        assertEquals(
            listOf("HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS", "QUICK_LINKS_WITH_TOTALS", "BANNER"),
            sections.map { it.jsonObject["type"]!!.jsonPrimitive.content },
        )

        val links = sections[3].jsonObject
        assertEquals("Explora", links["title"]!!.jsonPrimitive.content)
        val linkTitles = links["cards"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        // Ola 8: «Suscripciones» pasó a «Recurrentes» — la pantalla se plegó y el acceso lleva
        // ahora el nombre y la cifra de su destino real.
        assertEquals(listOf("Cuentas", "Créditos", "Presupuestos", "Metas", "Recurrentes"), linkTitles)

        val aiBanner = sections[4].jsonObject
        assertEquals("BANNER", aiBanner["type"]!!.jsonPrimitive.content)
        assertEquals("Pregúntale a Movi AI", aiBanner["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `304 when If-None-Match equals current version`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.IfNoneMatch, DASHBOARD_LAYOUT_VERSION.toString())
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
    fun `404 for inactive screen`() = testApplication {
        transaction { seedScreens() }
        transaction {
            Screens.update({ Screens.slug eq "dashboard" }) {
                it[active] = false
            }
        }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
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

    @Test
    fun `seed upgrades a row from an older generation and bumps version`() = testApplication {
        // Fila "vieja": layout de la generación 1 (ACCOUNTS_SUMMARY y compañía), editada tres
        // veces desde el Editor (version 3) pero nunca marcada con seed_version (default 0) —
        // exactamente lo que hay en una instalación desplegada antes de la Ola 4.
        transaction {
            Screens.insert {
                it[slug] = "dashboard"
                it[version] = 3
                it[sectionsJson] = """[{"type":"HERO_BALANCE"},{"type":"ACCOUNTS_SUMMARY"}]"""
                it[active] = true
                it[updatedAt] = 0L
            }
        }
        transaction { seedScreens() }
        wireApp()

        val res = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        // Sube por encima de la versión editada: un cliente con "3" en caché no recibe 304.
        // Contra la constante, no contra un literal: cada generación nueva del seed (la Ola 9
        // subió a 5) tiene que llegar sola a esta fila vieja.
        assertEquals(DASHBOARD_LAYOUT_VERSION, body["version"]!!.jsonPrimitive.content.toInt())
        val types = body["sections"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(SCREEN_SEED.first().sections.map { it.type }, types)

        // Un segundo boot con el mismo seed ya no toca nada (seed_version al día).
        transaction { seedScreens() }
        val again = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(DASHBOARD_LAYOUT_VERSION, Json.parseToJsonElement(again.bodyAsText()).jsonObject["version"]!!.jsonPrimitive.content.toInt())
    }
}
