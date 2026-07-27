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
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level tests for the SDUI editor endpoints (Task 1 of F2 -- editor):
 * PUT /api/screens/{slug}, POST /api/screens/{slug}/restore, GET /api/screens/admin/status.
 *
 * Same harness pattern as ScreenRoutesTest.kt (own H2 in-memory DB `screen_editor_test`,
 * test-local JWT secret/verifier, full serialization+jwt+routing plugin chain). Admin gate
 * is driven purely by config (AdminConfig reads the `movi.admin.userIds` system property in
 * tests) -- @BeforeTest sets it to userAId so A is admin and B is not; @AfterTest clears it
 * so it never leaks into other test classes sharing the same JVM.
 */
class ScreenEditorRoutesTest {

    private val testSecret = "test-secret-for-screen-editor-routes-tests-min-32"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-editor"
    private val userAEmail = "a@editor.test"
    private val userBId = "user-b-editor"
    private val userBEmail = "b@editor.test"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:screen_editor_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }

        System.setProperty("movi.admin.userIds", userAId)
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.admin.userIds")
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

    private fun tokenForA(): String = mintToken(userAId, userAEmail)
    private fun tokenForB(): String = mintToken(userBId, userBEmail)

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

    private fun bodyOf(def: ScreenDefinition): String = Json.encodeToString(def)

    private val validTwoSections = ScreenDefinition(
        slug = "ignored-slug-from-body",
        version = 999,
        sections = listOf(
            ScreenSection(type = "HERO_BALANCE"),
            ScreenSection(type = "BANNER", text = "hola"),
        ),
    )

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `PUT sin admin es 403`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForB()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(validTwoSections))
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `PUT valido incrementa la version y persiste`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val put = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(validTwoSections))
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val putBody = Json.parseToJsonElement(put.bodyAsText()).jsonObject
        assertEquals("dashboard", putBody["slug"]!!.jsonPrimitive.content)
        assertEquals(2, putBody["version"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, putBody["sections"]!!.jsonArray.size)

        val get = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
        }
        assertEquals(HttpStatusCode.OK, get.status)
        val getBody = Json.parseToJsonElement(get.bodyAsText()).jsonObject
        assertEquals(2, getBody["version"]!!.jsonPrimitive.content.toInt())
        val sections = getBody["sections"]!!.jsonArray
        assertEquals(2, sections.size)
        assertEquals("HERO_BALANCE", sections[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("BANNER", sections[1].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PUT con tipo desconocido es 422`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val invalid = ScreenDefinition(
            slug = "dashboard",
            version = 1,
            sections = listOf(ScreenSection(type = "HOLOGRAM_3D")),
        )

        val res = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(invalid))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("HOLOGRAM_3D"))
    }

    @Test
    fun `PUT con NAVIGATE invalido es 422`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val invalid = ScreenDefinition(
            slug = "dashboard",
            version = 1,
            sections = listOf(
                ScreenSection(
                    type = "LINK_LIST",
                    cards = listOf(ScreenCard(title = "x", action = ScreenAction("NAVIGATE", "settings"))),
                ),
            ),
        )

        val res = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(invalid))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("settings"))
    }

    @Test
    fun `PUT con OPEN_URL http es 422`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val invalid = ScreenDefinition(
            slug = "dashboard",
            version = 1,
            sections = listOf(
                ScreenSection(
                    type = "LINK_LIST",
                    cards = listOf(ScreenCard(title = "x", action = ScreenAction("OPEN_URL", "http://inseguro"))),
                ),
            ),
        )

        val res = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(invalid))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("https"))
    }

    @Test
    fun `PUT sin secciones es 422`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val invalid = ScreenDefinition(slug = "dashboard", version = 1, sections = emptyList())

        val res = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(invalid))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `PUT a slug inexistente es 404`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val res = client.put("/api/screens/no-existe-este-slug") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(validTwoSections))
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `restore devuelve el seed con version incrementada`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val put = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(bodyOf(validTwoSections))
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val putVersion = Json.parseToJsonElement(put.bodyAsText()).jsonObject["version"]!!.jsonPrimitive.content.toInt()

        val restore = client.post("/api/screens/dashboard/restore") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
        }
        assertEquals(HttpStatusCode.OK, restore.status)

        val restoreBody = Json.parseToJsonElement(restore.bodyAsText()).jsonObject
        assertEquals("dashboard", restoreBody["slug"]!!.jsonPrimitive.content)
        val restoreVersion = restoreBody["version"]!!.jsonPrimitive.content.toInt()
        assertTrue(restoreVersion > putVersion)

        val seedDashboard = SCREEN_SEED.first { it.slug == "dashboard" }
        val restoredSections = restoreBody["sections"]!!.jsonArray
        assertEquals(seedDashboard.sections.size, restoredSections.size)
        assertEquals(5, restoredSections.size)
        assertEquals("HERO_BALANCE", restoredSections[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `admin status refleja la config`() = testApplication {
        transaction { seedScreens() }
        wireApp()

        val resA = client.get("/api/screens/admin/status") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForA()}")
        }
        assertEquals(HttpStatusCode.OK, resA.status)
        val bodyA = Json.parseToJsonElement(resA.bodyAsText()).jsonObject
        assertEquals(true, bodyA["isAdmin"]!!.jsonPrimitive.content.toBoolean())

        val resB = client.get("/api/screens/admin/status") {
            header(HttpHeaders.Authorization, "Bearer ${tokenForB()}")
        }
        assertEquals(HttpStatusCode.OK, resB.status)
        val bodyB = Json.parseToJsonElement(resB.bodyAsText()).jsonObject
        assertEquals(false, bodyB["isAdmin"]!!.jsonPrimitive.content.toBoolean())
    }
}
