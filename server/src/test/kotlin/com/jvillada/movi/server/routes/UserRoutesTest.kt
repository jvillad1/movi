package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AvatarPalette
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests HTTP de F42 · F46 — `GET/PUT /api/users/me` y `PUT /api/users/me/password`. Antes de
 * esto ninguno de los tres existía: el usuario tenía id, correo, nombre y contraseña, y nada
 * detrás para leerlos ni cambiarlos.
 *
 * Mismo arnés que AccountRoutesTest/AuthRoutesTest: H2 en memoria (compat PostgreSQL), JWT
 * local, cadena completa de plugins vía `wireApp()`.
 */
class UserRoutesTest {

    private val testSecret = "test-secret-for-user-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-profile"
    private val userEmail = "a@profile.test"
    private val userPassword = "una-contrasena-larga-y-tranquila"

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        Database.connect(
            url    = "jdbc:h2:mem:user_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "Juan"
                it[passwordHash] = BCrypt.withDefaults().hashToString(BCRYPT_COST, userPassword.toCharArray())
            }
        }
    }

    @AfterTest
    fun tearDown() {
        RateLimiter.reset()
    }

    // ── JWT / harness ────────────────────────────────────────────────────────

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

    private fun Application.testModule() {
        configureSerialization()
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
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

    private suspend fun ApplicationTestBuilder.getProfile() =
        client.get("/api/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun ApplicationTestBuilder.putProfile(body: String) =
        client.put("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.putPassword(current: String, new: String) =
        client.put("/api/users/me/password") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"current":"$current","new":"$new"}""")
        }

    private suspend fun ApplicationTestBuilder.login(email: String, password: String) =
        client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"email":"$email","password":"$password"}""")
        }

    private fun dbAvatarColor(): String? = transaction {
        Users.selectAll().where { Users.id eq userId }.single()[Users.avatarColor]
    }

    // ── GET /api/users/me ───────────────────────────────────────────────────

    @Test
    fun `GET devuelve el perfil con el color por defecto cuando nunca eligio ninguno`() = testApplication {
        wireApp()
        val res = getProfile()
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals(userId, body["id"]!!.jsonPrimitive.content)
        assertEquals(userEmail, body["email"]!!.jsonPrimitive.content)
        assertEquals("Juan", body["name"]!!.jsonPrimitive.content)
        assertEquals(AvatarPalette.DEFAULT, body["avatarColor"]!!.jsonPrimitive.content)
    }

    // ── PUT /api/users/me ────────────────────────────────────────────────────

    @Test
    fun `PUT cambia el alias y lo recorta`() = testApplication {
        wireApp()
        val res = putProfile("""{"name":"  Juanito  "}""")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Juanito", body["name"]!!.jsonPrimitive.content)
        assertEquals("Juanito", transaction { Users.selectAll().where { Users.id eq userId }.single()[Users.name] })
    }

    @Test
    fun `PUT rechaza un nombre vacio`() = testApplication {
        wireApp()
        val res = putProfile("""{"name":"   "}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals("Juan", transaction { Users.selectAll().where { Users.id eq userId }.single()[Users.name] })
    }

    @Test
    fun `PUT rechaza un color fuera de la paleta`() = testApplication {
        wireApp()
        val res = putProfile("""{"avatarColor":"#123456"}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(null, dbAvatarColor())
    }

    @Test
    fun `PUT acepta un color de la paleta y lo persiste`() = testApplication {
        wireApp()
        val color = AvatarPalette.COLORS[3]
        val res = putProfile("""{"avatarColor":"$color"}""")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals(color, body["avatarColor"]!!.jsonPrimitive.content)
        assertEquals(color, dbAvatarColor())
    }

    // ── PUT /api/users/me/password ───────────────────────────────────────────

    @Test
    fun `cambio de password rechaza la contrasena actual mala con 403`() = testApplication {
        wireApp()
        val res = putPassword("no-es-la-actual", "una-contrasena-nueva-y-larga")
        assertEquals(HttpStatusCode.Forbidden, res.status)
        // La contraseña vieja sigue funcionando: nada cambió en la base.
        val loginRes = login(userEmail, userPassword)
        assertEquals(HttpStatusCode.OK, loginRes.status)
    }

    @Test
    fun `cambio de password rechaza una nueva corta con 400 y no toca la actual`() = testApplication {
        wireApp()
        val res = putPassword(userPassword, "corta")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        val loginRes = login(userEmail, userPassword)
        assertEquals(HttpStatusCode.OK, loginRes.status, "la vieja tiene que seguir sirviendo")
    }

    @Test
    fun `cambio de password exitoso permite loguear con la nueva y no con la vieja`() = testApplication {
        wireApp()
        val nueva = "una-contrasena-nueva-y-bien-larga"
        val res = putPassword(userPassword, nueva)
        assertEquals(HttpStatusCode.OK, res.status)

        val conNueva = login(userEmail, nueva)
        assertEquals(HttpStatusCode.OK, conNueva.status)

        val conVieja = login(userEmail, userPassword)
        assertEquals(HttpStatusCode.Unauthorized, conVieja.status)
    }

    @Test
    fun `cambio de password tiene su propio balde y no lo comparte con login`() = testApplication {
        wireApp()
        var bloqueado = false
        repeat(7) {
            if (putPassword("mala-$it", "una-contrasena-nueva-y-larga").status == HttpStatusCode.TooManyRequests) {
                bloqueado = true
            }
        }
        assertTrue(bloqueado, "7 intentos fallidos no dispararon el balde de cambio de contraseña")

        // El login de la MISMA cuenta sigue sirviendo — es un balde distinto.
        val loginRes = login(userEmail, userPassword)
        assertEquals(HttpStatusCode.OK, loginRes.status, "el balde de cambio de contraseña se comió el de login")
    }
}
