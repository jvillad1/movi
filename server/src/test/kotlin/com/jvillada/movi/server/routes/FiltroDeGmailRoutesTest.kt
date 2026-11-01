package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.CODIGO_DE_REENVIO_DE_EJEMPLO
import com.jvillada.movi.server.correo.asuntoDeConfirmacionDeReenvio
import com.jvillada.movi.server.correo.cuerpoDeConfirmacionDeReenvio
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.CorreosDescartados
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.correo.momentoDelCorreo
import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.FiltroDeGmail
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/correo-entrante/filtro` (vuelta 93): autenticado, y solo habla de quien pregunta.
 */
class FiltroDeGmailRoutesTest {

    private val testSecret = "test-secret-for-filtro-de-gmail-tests-minimum-32"
    private val userAId = "user-a-filtro"
    private val userBId = "user-b-filtro"
    private val dominio = "abc123.resend.app"
    private val direccionDeA get() = "alertas+${tokenDeCorreoDe(userAId)}@$dominio"

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.direccion", "alertas@$dominio")
        System.setProperty("movi.correo.secreto", "un-secreto-de-webhook-largo-y-random")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:filtro_de_gmail_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions, CorreosDescartados)
            SchemaUtils.drop(CorreosDescartados, SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions, CorreosDescartados)
            for ((id, correo) in listOf(userAId to "a@filtro.test", userBId to "b@filtro.test")) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = correo
                    it[name] = id
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.direccion")
        System.clearProperty("movi.correo.secreto")
        RateLimiter.reset()
    }

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { if (it.payload.getClaim("userId").asString() != null) JWTPrincipal(it.payload) else null }
            }
        }
        configureRouting()
    }

    private fun token(userId: String): String =
        JWT.create().withIssuer("movi").withAudience("movi-client")
            .withClaim("userId", userId).withClaim("email", "$userId@filtro.test")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun guardarLaConfirmacion(
        uid: String,
        id: String = "cmp-$uid",
        haceMs: Long = 60_000,
        asunto: String = asuntoDeConfirmacionDeReenvio(),
    ) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = momentoDelCorreo(null, System.currentTimeMillis() - haceMs)
            it[bank] = "Correo · Gmail Team"
            it[text] = textoDelCorreo(asunto, cuerpoDeConfirmacionDeReenvio("alertas+x@$dominio"))
            it[state] = "ignored"
            it[det] = ""
            it[motivoApartado] = "CONFIRMACION_DE_REENVIO"
        }
    }

    private suspend fun io.ktor.client.HttpClient.filtro(uid: String): FiltroDeGmail {
        val r = get("/api/correo-entrante/filtro") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, r.status)
        return Json { ignoreUnknownKeys = true }.decodeFromString(r.bodyAsText())
    }

    @Test
    fun `exige sesion`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/correo-entrante/filtro").status)
    }

    @Test
    fun `lleva la direccion propia en el XML y la busqueda con los remitentes`() = testApplication {
        application { testModule() }
        val f = client.filtro(userAId)
        assertTrue(f.configurado)
        assertEquals(direccionDeA, f.direccion)
        assertTrue(f.busquedas.isNotEmpty() && f.busquedas.all { it.startsWith("from:(") && it.endsWith(")") })
        assertTrue("notificacionesbancolombia.com" in f.busquedas.joinToString(" "))
        val xml = assertNotNull(f.xml)
        assertTrue("value='$direccionDeA'" in xml, "el reenvío va a la dirección de quien pregunta")
        assertFalse(tokenDeCorreoDe(userBId) in xml, "y no a la de nadie más")
        assertTrue(f.remitentes.any { it.verificado })
    }

    @Test
    fun `sin la direccion configurada entrega la busqueda pero no el XML`() = testApplication {
        application { testModule() }
        System.clearProperty("movi.correo.direccion")
        val f = client.filtro(userAId)
        assertNull(f.direccion)
        assertNull(f.xml)
        assertTrue(f.busquedas.isNotEmpty())
    }

    @Test
    fun `el codigo de confirmacion es el del correo de Gmail y solo si es de quien pregunta`() = testApplication {
        application { testModule() }
        assertNull(client.filtro(userAId).codigoDeConfirmacion, "todavía no llegó")
        guardarLaConfirmacion(userBId)
        assertNull(client.filtro(userAId).codigoDeConfirmacion, "el código de otro no se ve")
        guardarLaConfirmacion(userAId)
        assertEquals(CODIGO_DE_REENVIO_DE_EJEMPLO, client.filtro(userAId).codigoDeConfirmacion)
    }

    @Test
    fun `un codigo de hace una semana ya no se ofrece`() = testApplication {
        application { testModule() }
        guardarLaConfirmacion(userAId, haceMs = 7L * 86_400_000)
        assertNull(client.filtro(userAId).codigoDeConfirmacion)
    }

    @Test
    fun `entre dos confirmaciones sale el codigo de la mas reciente`() = testApplication {
        application { testModule() }
        guardarLaConfirmacion(userAId, id = "vieja", haceMs = 86_400_000, asunto = "(#111111111) Gmail Forwarding Confirmation")
        guardarLaConfirmacion(userAId, id = "nueva", haceMs = 60_000)
        assertEquals(CODIGO_DE_REENVIO_DE_EJEMPLO, client.filtro(userAId).codigoDeConfirmacion)
    }
}
