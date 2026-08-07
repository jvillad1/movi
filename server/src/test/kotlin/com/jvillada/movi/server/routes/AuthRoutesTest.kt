package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.PasswordReset
import com.jvillada.movi.server.auth.PasswordResetMailer
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.PasswordPolicy
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
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
import org.jetbrains.exposed.sql.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests HTTP de /api/auth: piso de contraseña y recuperación por correo.
 * Mismo harness que el resto (H2 en modo PostgreSQL + los plugins reales), pero sin JWT
 * porque estas rutas son públicas.
 *
 * El correo NO sale a la red: [PasswordResetMailer.sender] se reemplaza por un grabador,
 * que además es la única forma de leer el token que se envió (en la DB solo vive el hash).
 */
class AuthRoutesTest {

    private val strongPassword = "una-contrasena-larga-y-tranquila"
    private val legacyShortPassword = "abc123"           // 6 chars: por debajo del piso nuevo
    private val legacyUserId = "usr-legacy"
    private val legacyEmail = "legacy@movi.test"

    /** (destinatario, enlace) de cada correo que las rutas quisieron enviar. */
    private val sentEmails = mutableListOf<Pair<String, String>>()
    private var mailerResult = true

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-auth-routes-tests-min-32-chars")
        System.setProperty("movi.resend.apiKey", "test-resend-key")
        System.setProperty("movi.reminder.from", "movi <test@movi.test>")
        System.setProperty("movi.app.baseUrl", "https://movi.test")
        RateLimiter.reset()

        Database.connect(
            url    = "jdbc:h2:mem:auth_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(PasswordResetTokens, Users)
            SchemaUtils.create(Users, PasswordResetTokens)
            // Usuario "viejo": su contraseña de 6 caracteres es anterior al piso nuevo.
            Users.insert {
                it[id]           = legacyUserId
                it[email]        = legacyEmail
                it[name]         = "Legacy"
                it[passwordHash] = BCrypt.withDefaults().hashToString(12, legacyShortPassword.toCharArray())
            }
        }

        sentEmails.clear()
        mailerResult = true
        if (realSender == null) realSender = PasswordResetMailer.sender
        PasswordResetMailer.sender = { to, _, html, _, _ ->
            sentEmails += to to (LINK_RE.find(html)?.groupValues?.get(1) ?: "")
            mailerResult
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
        System.clearProperty("movi.resend.apiKey")
        System.clearProperty("movi.reminder.from")
        System.clearProperty("movi.app.baseUrl")
        realSender?.let { PasswordResetMailer.sender = it }
        RateLimiter.reset()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application {
            configureSerialization()
            routing { authRoutes() }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.register(email: String, password: String, ip: String = "10.0.0.1") =
        client.post("/api/auth/register") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.XForwardedFor, ip)
            setBody("""{"email":"$email","name":"Alguien","password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.login(email: String, password: String, ip: String = "10.0.0.2") =
        client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.XForwardedFor, ip)
            setBody("""{"email":"$email","password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.requestReset(email: String, ip: String = "10.0.0.3") =
        client.post("/api/auth/password-reset/request") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.XForwardedFor, ip)
            setBody("""{"email":"$email"}""")
        }

    private suspend fun ApplicationTestBuilder.confirmReset(token: String, newPassword: String, ip: String = "10.0.0.4") =
        client.post("/api/auth/password-reset/confirm") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.XForwardedFor, ip)
            setBody("""{"token":"$token","newPassword":"$newPassword"}""")
        }

    /** El token solo existe en el enlace del correo — en la DB vive el hash. */
    private fun tokenFromLastEmail(): String =
        sentEmails.last().second.substringAfter("reset=")

    private fun storedHashFor(userId: String): String = transaction {
        Users.selectAll().where { Users.id eq userId }.single()[Users.passwordHash]
    }

    // ── Piso de contraseña ────────────────────────────────────────────────────

    @Test
    fun `registro rechaza una contrasena por debajo del piso`() = testApplication {
        wireApp()
        val res = register("corta@movi.test", "a".repeat(PasswordPolicy.MIN_LENGTH - 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("${PasswordPolicy.MIN_LENGTH}"), res.bodyAsText())
    }

    @Test
    fun `registro acepta exactamente el piso`() = testApplication {
        wireApp()
        val res = register("justa@movi.test", "a".repeat(PasswordPolicy.MIN_LENGTH))
        assertEquals(HttpStatusCode.Created, res.status)
    }

    @Test
    fun `registro rechaza por encima del maximo`() = testApplication {
        wireApp()
        val res = register("larga@movi.test", "a".repeat(PasswordPolicy.MAX_LENGTH + 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    /**
     * El caso que no se puede romper: el hash guardado de una contraseña vieja no se puede
     * re-evaluar contra el piso nuevo, así que el piso NO aplica al login. Si aplicara,
     * el único usuario real de la app quedaría afuera de sus propias finanzas.
     */
    @Test
    fun `un usuario con contrasena por debajo del piso sigue pudiendo entrar`() = testApplication {
        wireApp()
        assertTrue(legacyShortPassword.length < PasswordPolicy.MIN_LENGTH)
        val res = login(legacyEmail, legacyShortPassword)
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("token"))
    }

    // ── Pedido de reset: anti-enumeración ─────────────────────────────────────

    @Test
    fun `el pedido responde identico para un correo registrado y uno desconocido`() = testApplication {
        wireApp()
        val conocido = requestReset(legacyEmail, ip = "10.1.0.1")
        val desconocido = requestReset("nadie@movi.test", ip = "10.1.0.2")

        assertEquals(conocido.status, desconocido.status)
        assertEquals(HttpStatusCode.Accepted, conocido.status)
        assertEquals(conocido.bodyAsText(), desconocido.bodyAsText())
    }

    @Test
    fun `un correo desconocido no genera token ni correo`() = testApplication {
        wireApp()
        requestReset("nadie@movi.test")
        assertTrue(sentEmails.isEmpty())
        assertEquals(0L, transaction { PasswordResetTokens.selectAll().count() })
    }

    /**
     * El canal de temporización: el camino "registrado" hace escrituras que el otro no hace.
     * Ambos deben pasar el piso y ninguno puede dispararse por encima del otro.
     */
    @Test
    fun `los dos caminos tardan parecido — no hay oraculo de temporizacion`() = testApplication {
        wireApp()
        val tConocido = measure { requestReset(legacyEmail, ip = "10.2.0.1") }
        val tDesconocido = measure { requestReset("nadie@movi.test", ip = "10.2.0.2") }

        assertTrue(
            tConocido >= PasswordReset.REQUEST_FLOOR_MS && tDesconocido >= PasswordReset.REQUEST_FLOOR_MS,
            "piso no respetado: conocido=$tConocido ms, desconocido=$tDesconocido ms",
        )
        // El envío del correo sale del camino de la petición, así que la diferencia tiene que
        // quedar muy por debajo de lo que costaría un round-trip a Resend.
        assertTrue(
            kotlin.math.abs(tConocido - tDesconocido) < PasswordReset.REQUEST_FLOOR_MS,
            "diferencia demasiado grande: conocido=$tConocido ms, desconocido=$tDesconocido ms",
        )
    }

    @Test
    fun `el pedido esta rate-limitado`() = testApplication {
        wireApp()
        val ip = "10.3.0.1"
        var got429 = false
        repeat(20) {
            if (requestReset(legacyEmail, ip = ip).status == HttpStatusCode.TooManyRequests) got429 = true
        }
        assertTrue(got429, "20 pedidos seguidos desde la misma IP no dispararon el rate limit")
    }

    @Test
    fun `el confirm esta rate-limitado`() = testApplication {
        wireApp()
        val ip = "10.3.0.2"
        var got429 = false
        repeat(30) {
            if (confirmReset("token-inventado", strongPassword, ip = ip).status == HttpStatusCode.TooManyRequests) got429 = true
        }
        assertTrue(got429, "30 confirms seguidos desde la misma IP no dispararon el rate limit")
    }

    // ── El token en la DB ─────────────────────────────────────────────────────

    @Test
    fun `en la base se guarda el hash y nunca el token`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        assertTrue(token.isNotBlank())

        val stored = transaction { PasswordResetTokens.selectAll().single()[PasswordResetTokens.tokenHash] }
        assertNotEquals(token, stored)
        assertEquals(PasswordReset.hashToken(token), stored)
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Test
    fun `con un token valido se cambia la contrasena y la nueva sirve para entrar`() = testApplication {
        wireApp()
        val hashAntes = storedHashFor(legacyUserId)

        requestReset(legacyEmail)
        val res = confirmReset(tokenFromLastEmail(), strongPassword)
        assertEquals(HttpStatusCode.OK, res.status)

        assertNotEquals(hashAntes, storedHashFor(legacyUserId))
        assertEquals(HttpStatusCode.OK, login(legacyEmail, strongPassword).status)
    }

    @Test
    fun `tras el reset la contrasena vieja deja de servir`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        confirmReset(tokenFromLastEmail(), strongPassword)
        assertEquals(HttpStatusCode.Unauthorized, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `un token ya usado no sirve dos veces`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        assertEquals(HttpStatusCode.OK, confirmReset(token, strongPassword).status)
        val segundo = confirmReset(token, "otra-contrasena-larga-igual")
        assertEquals(HttpStatusCode.BadRequest, segundo.status)
        // Y la contraseña quedó en la del PRIMER canje.
        assertEquals(HttpStatusCode.OK, login(legacyEmail, strongPassword).status)
    }

    @Test
    fun `un token vencido no sirve`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        transaction {
            PasswordResetTokens.update({ PasswordResetTokens.tokenHash eq PasswordReset.hashToken(token) }) {
                it[expiresAt] = System.currentTimeMillis() - 1
            }
        }
        assertEquals(HttpStatusCode.BadRequest, confirmReset(token, strongPassword).status)
        assertEquals(HttpStatusCode.OK, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `un token manipulado o inventado no sirve`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        val manipulado = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'

        assertEquals(HttpStatusCode.BadRequest, confirmReset(manipulado, strongPassword).status)
        assertEquals(HttpStatusCode.BadRequest, confirmReset("no-existe-este-token", strongPassword).status)
        // La contraseña original sigue intacta.
        assertEquals(HttpStatusCode.OK, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `el confirm aplica el mismo piso de contrasena que el registro`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        val res = confirmReset(token, "a".repeat(PasswordPolicy.MIN_LENGTH - 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("${PasswordPolicy.MIN_LENGTH}"), res.bodyAsText())
        // Y el token NO se consumió: sigue sirviendo con una contraseña que cumple.
        assertEquals(HttpStatusCode.OK, confirmReset(token, strongPassword).status)
    }

    @Test
    fun `una contrasena debil con un token invalido no revela que el token es invalido`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val valido = tokenFromLastEmail()
        val debil = "a".repeat(PasswordPolicy.MIN_LENGTH - 1)

        val conTokenValido = confirmReset(valido, debil)
        val conTokenBasura = confirmReset("token-que-no-existe", debil)
        assertEquals(conTokenValido.status, conTokenBasura.status)
        assertEquals(conTokenValido.bodyAsText(), conTokenBasura.bodyAsText())
    }

    @Test
    fun `un reset exitoso invalida los otros tokens pendientes del mismo usuario`() = testApplication {
        wireApp()
        requestReset(legacyEmail, ip = "10.4.0.1")
        val primero = tokenFromLastEmail()
        requestReset(legacyEmail, ip = "10.4.0.2")
        val segundo = tokenFromLastEmail()
        assertNotEquals(primero, segundo)

        // Pedir uno nuevo ya invalida el anterior…
        assertEquals(HttpStatusCode.BadRequest, confirmReset(primero, strongPassword).status)
        // …y el nuevo funciona.
        assertEquals(HttpStatusCode.OK, confirmReset(segundo, strongPassword).status)
        assertTrue(transaction { PasswordResetTokens.selectAll().all { it[PasswordResetTokens.usedAt] != null } })
    }

    @Test
    fun `el reset de un usuario no toca la contrasena de otro`() = testApplication {
        wireApp()
        register("otro@movi.test", strongPassword)
        val otroHashAntes = transaction {
            Users.selectAll().where { Users.email eq "otro@movi.test" }.single()[Users.passwordHash]
        }

        requestReset(legacyEmail)
        confirmReset(tokenFromLastEmail(), "contrasena-nueva-del-legacy")

        val otroHashDespues = transaction {
            Users.selectAll().where { Users.email eq "otro@movi.test" }.single()[Users.passwordHash]
        }
        assertEquals(otroHashAntes, otroHashDespues)
        assertEquals(HttpStatusCode.OK, login("otro@movi.test", strongPassword).status)
    }

    // ── RESEND_API_KEY ausente ────────────────────────────────────────────────

    /**
     * En producción la clave NO está puesta. Contestar 202 "revisá tu correo" mandaría a la
     * persona a esperar un mensaje que no existe, en el único flujo que tiene para recuperar
     * el acceso. Se contesta 503 — es una condición del servidor, igual para todo el mundo,
     * así que no filtra nada sobre qué correos están registrados.
     */
    @Test
    fun `sin RESEND_API_KEY el pedido responde 503 y no promete ningun correo`() = testApplication {
        System.clearProperty("movi.resend.apiKey")
        wireApp()
        val res = requestReset(legacyEmail)
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertTrue(sentEmails.isEmpty())
        assertEquals(0L, transaction { PasswordResetTokens.selectAll().count() })
    }

    @Test
    fun `sin RESEND_API_KEY la respuesta sigue siendo identica para registrado y desconocido`() = testApplication {
        System.clearProperty("movi.resend.apiKey")
        wireApp()
        val conocido = requestReset(legacyEmail, ip = "10.5.0.1")
        val desconocido = requestReset("nadie@movi.test", ip = "10.5.0.2")
        assertEquals(conocido.status, desconocido.status)
        assertEquals(conocido.bodyAsText(), desconocido.bodyAsText())
    }

    /**
     * Con la clave puesta pero Resend caído, el token YA se creó. No se puede desdecir sin
     * abrir un canal (un 502 solo para correos registrados sería un oráculo), así que la
     * respuesta sigue siendo el 202 genérico y el fallo queda en los logs.
     */
    @Test
    fun `si el envio falla la respuesta sigue siendo el 202 generico`() = testApplication {
        wireApp()
        mailerResult = false
        val res = requestReset(legacyEmail)
        assertEquals(HttpStatusCode.Accepted, res.status)
    }

    // ── Compatibilidad: nada de esto cambió la forma de las respuestas ────────

    @Test
    fun `register y login siguen devolviendo el mismo AuthResponse`() = testApplication {
        wireApp()
        val creado = register("forma@movi.test", strongPassword)
        assertEquals(HttpStatusCode.Created, creado.status)
        for (res in listOf(creado, login("forma@movi.test", strongPassword))) {
            val obj = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals(setOf("token", "userId", "name", "email"), obj.keys)
            assertTrue(obj["token"]!!.jsonPrimitive.content.isNotBlank())
            assertEquals("forma@movi.test", obj["email"]!!.jsonPrimitive.content)
        }
    }

    // ── utilidades ────────────────────────────────────────────────────────────

    private suspend fun measure(block: suspend () -> HttpResponse): Long {
        val start = System.currentTimeMillis()
        assertNotNull(block())
        return System.currentTimeMillis() - start
    }

    private companion object {
        /** El enlace del correo: href="…?reset=TOKEN". */
        val LINK_RE = Regex("""href="([^"]+)"""")

        /** El sender real, capturado antes de reemplazarlo, para restaurarlo al terminar. */
        var realSender: (suspend (String, String, String, String, String) -> Boolean)? = null
    }
}
