package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.ReminderConfig
import com.jvillada.movi.shared.model.ReminderChannels
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/reminders/channels` — **el endpoint por el que el cliente deja de mentir.**
 *
 * La app afirmaba «este recordatorio no te va a llegar» mirando solo el permiso de notificaciones
 * del navegador. Acá se prueba lo que ahora le contesta el server, que es lo único con lo que esa
 * afirmación se puede sostener o descartar: si hay canal de correo, a qué dirección sale, si el
 * remitente es el de pruebas de Resend, y con cuántos días de anticipación avisa el barrido.
 *
 * Mismo arnés que `PushRoutesTest`: H2 en memoria, JWT de test y la cadena completa de plugins.
 */
class ReminderChannelsRoutesTest {

    private val testSecret = "test-secret-for-reminder-channels-tests-min-32"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-canales"
    private val userEmail = "dueno@canales.test"

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:reminder_channels_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                PushSubscriptions, Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions, PushSubscriptions,
            )
            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "El dueño"
                it[passwordHash] = "hash"
            }
        }
    }

    @AfterTest
    fun limpiarProps() {
        listOf(
            "movi.resend.apiKey", "movi.reminder.from", "movi.reminder.leadDays",
            "movi.vapid.public", "movi.vapid.private",
        ).forEach { System.clearProperty(it) }
    }

    // ── Arnés ─────────────────────────────────────────────────────────────────

    private fun token(): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("email", userEmail)
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() { application { testModule() } }

    private suspend fun ApplicationTestBuilder.canales(): ReminderChannels {
        val res = client.get("/api/reminders/channels") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ReminderChannels.serializer(), res.bodyAsText())
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * El estado de producción del dueño: `RESEND_API_KEY` puesta. El cliente tiene que poder
     * enterarse de que **sí** hay canal, que es justo lo que no podía antes.
     */
    @Test
    fun `con RESEND_API_KEY el server dice que hay correo y a que direccion sale`() = testApplication {
        System.setProperty("movi.resend.apiKey", "re_test_key")
        System.setProperty("movi.reminder.from", "movi <hola@movi.app>")
        wireApp()
        val c = canales()
        assertTrue(c.email, "con la clave puesta el barrido manda correo, así que hay canal")
        assertEquals(userEmail, c.emailTo, "sale a la dirección del usuario autenticado")
        assertFalse(c.emailSandbox)
    }

    /**
     * Sin clave no hay correo — y ahí el aviso ámbar del cliente sí es cierto. El endpoint tiene
     * que poder decir que no, o el arreglo sería «siempre hay correo», que es otra mentira.
     */
    @Test
    fun `sin RESEND_API_KEY el server dice que no hay correo y no inventa direccion`() = testApplication {
        System.clearProperty("movi.resend.apiKey")
        wireApp()
        val c = canales()
        assertFalse(c.email)
        assertNull(c.emailTo, "sin canal no se nombra ninguna dirección")
    }

    /**
     * `onboarding@resend.dev` es el remitente sin dominio verificado: entrega **solo** a la
     * dirección dueña de la cuenta de Resend. Es el estado real de producción hoy, y el cliente
     * lo usa para decir a quién alcanza en vez de negar la entrega.
     */
    @Test
    fun `el remitente de pruebas de Resend se marca como tal`() = testApplication {
        System.setProperty("movi.resend.apiKey", "re_test_key")
        System.setProperty("movi.reminder.from", "onboarding@resend.dev")
        wireApp()
        assertTrue(canales().emailSandbox)
    }

    @Test
    fun `un remitente con dominio propio no se marca como de pruebas`() = testApplication {
        System.setProperty("movi.resend.apiKey", "re_test_key")
        System.setProperty("movi.reminder.from", "movi <reminders@movi.app>")
        wireApp()
        assertFalse(canales().emailSandbox)
    }

    /** El número de días lo manda el server: el cliente lo tenía cableado en 3. */
    @Test
    fun `los dias de anticipacion salen de la configuracion del server`() = testApplication {
        System.setProperty("movi.reminder.leadDays", "7")
        wireApp()
        assertEquals(7, canales().leadDays)
    }

    @Test
    fun `sin claves VAPID el server dice que no puede empujar notificaciones`() = testApplication {
        System.clearProperty("movi.vapid.public")
        System.clearProperty("movi.vapid.private")
        wireApp()
        assertFalse(canales().push)
    }

    @Test
    fun `con claves VAPID el server dice que si puede empujar notificaciones`() = testApplication {
        System.setProperty("movi.vapid.public", "test-public-key")
        System.setProperty("movi.vapid.private", "test-private-key")
        wireApp()
        assertTrue(canales().push)
    }

    /** La dirección de correo es un dato del usuario: sin sesión no se contesta. */
    @Test
    fun `sin token el endpoint no contesta`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/reminders/channels").status)
    }

    /**
     * El endpoint y el barrido leen el MISMO objeto. Si algún día alguien le pone otra fuente a
     * uno de los dos, la respuesta podría decir «hay correo» sobre un barrido apagado — que es la
     * misma mentira de antes, con los papeles cambiados.
     */
    @Test
    fun `la condicion de -hay correo- es exactamente la del barrido`() {
        System.clearProperty("movi.resend.apiKey")
        assertFalse(ReminderConfig.emailEnabled())
        System.setProperty("movi.resend.apiKey", "re_test_key")
        assertTrue(ReminderConfig.emailEnabled())
    }
}
