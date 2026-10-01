package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **«Cerrar sesión en todos los aparatos» y la versión de las sesiones** (`users.token_version`).
 *
 * Lo que se prueba es el `configureAuth()` de verdad —el mismo que monta el server—, no un `jwt`
 * armado a mano como hacen las pruebas de rutas: lo que cambió es justamente el validador.
 *
 * La prueba que más importa es la primera: **un token firmado antes de este cambio, sin el claim,
 * sigue entrando**. Es el token que hoy tiene el teléfono del dueño; si esa prueba se pone roja,
 * el despliegue desloguea a todo el mundo.
 */
class CerrarSesionesTest {

    private val secreto = "test-secret-for-cerrar-sesiones-min-32-chars"
    private val uid = "usr-a"
    private val email = "a@movi.test"
    private val otroUid = "usr-b"
    private val otroEmail = "b@movi.test"

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", secreto)
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:cerrar_sesiones_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(PushSubscriptions, PasswordResetTokens, Users)
            SchemaUtils.create(Users, PasswordResetTokens, PushSubscriptions)
            for ((id, correo) in listOf(uid to email, otroUid to otroEmail)) {
                Users.insert {
                    it[Users.id] = id
                    it[Users.email] = correo
                    it[name] = id
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
        RateLimiter.reset()
    }

    private fun ApplicationTestBuilder.armar() {
        application {
            configureSerialization()
            configureAuth()
            routing { authenticate("jwt") { userRoutes() } }
        }
    }

    /** Un token como los que firmaba el server ANTES de este cambio: sin el claim `tv`. */
    private fun tokenSinVersion(userId: String, correo: String): String =
        JwtConfig.makeTokenSinVersion(userId, correo)

    private suspend fun ApplicationTestBuilder.perfil(token: String) =
        client.get("/api/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun ApplicationTestBuilder.cerrarSesiones(token: String) =
        client.post("/api/users/me/cerrar-sesiones") { header(HttpHeaders.Authorization, "Bearer $token") }

    private fun versionDe(id: String): Int = transaction {
        Users.selectAll().where { Users.id eq id }.single()[Users.tokenVersion]
    }

    @Test
    fun `un token de antes del cambio, sin claim, sigue entrando`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.OK, perfil(tokenSinVersion(uid, email)).status)
    }

    @Test
    fun `cerrar sesiones deja afuera a todos los tokens anteriores, viejos y nuevos`() = testApplication {
        armar()
        val viejo = tokenSinVersion(uid, email)
        val otroAparato = JwtConfig.makeToken(uid, email, 0)
        val este = JwtConfig.makeToken(uid, email, 0)

        val res = cerrarSesiones(este)
        assertEquals(HttpStatusCode.NoContent, res.status, res.bodyAsText())
        assertEquals(1, versionDe(uid))

        assertEquals(HttpStatusCode.Unauthorized, perfil(viejo).status, "el token sin claim es versión 0")
        assertEquals(HttpStatusCode.Unauthorized, perfil(otroAparato).status, "el teléfono perdido sigue adentro")
        assertEquals(HttpStatusCode.Unauthorized, perfil(este).status, "el de quien lo pidió también se cierra")
        // Un token de la versión nueva —el que da el login siguiente— sí entra.
        assertEquals(HttpStatusCode.OK, perfil(JwtConfig.makeToken(uid, email, 1)).status)
    }

    @Test
    fun `cerrar las sesiones de uno no toca las de otro`() = testApplication {
        armar()
        val deB = JwtConfig.makeToken(otroUid, otroEmail)
        cerrarSesiones(JwtConfig.makeToken(uid, email))
        assertEquals(0, versionDe(otroUid))
        assertEquals(HttpStatusCode.OK, perfil(deB).status)
    }

    @Test
    fun `un token ya cerrado no puede volver a cerrar nada`() = testApplication {
        armar()
        val token = JwtConfig.makeToken(uid, email)
        cerrarSesiones(token)
        assertEquals(HttpStatusCode.Unauthorized, cerrarSesiones(token).status)
        assertEquals(1, versionDe(uid), "un 401 no puede subir la versión")
    }

    @Test
    fun `un token bien firmado de un usuario que no existe no entra`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.Unauthorized, perfil(JwtConfig.makeToken("usr-borrado", "x@movi.test")).status)
    }

    @Test
    fun `cerrar sesiones suelta las suscripciones push de la cuenta`() = testApplication {
        armar()
        transaction {
            PushSubscriptions.insert {
                it[endpoint] = "https://push.example/perdido"
                it[userId] = uid
                it[p256dh] = "k"
                it[auth] = "s"
                it[createdAt] = 0L
            }
        }
        cerrarSesiones(JwtConfig.makeToken(uid, email))
        val quedan = transaction { PushSubscriptions.selectAll().where { PushSubscriptions.userId eq uid }.count() }
        assertEquals(0L, quedan)
    }
}
