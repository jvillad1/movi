package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.ASUNTO_CUOTA_DE_MANEJO
import com.jvillada.movi.server.correo.CUERPO_CUOTA_DE_MANEJO
import com.jvillada.movi.server.correo.alertaMailgunFormulario
import com.jvillada.movi.server.correo.alertaMailgunJson
import com.jvillada.movi.server.correo.alertaPostmark
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El webhook de las alertas del banco por correo, extremo a extremo contra H2.
 *
 * Lo que se afirma acá es, casi todo, **que no se escribió nada**: un webhook público que mete
 * filas en la bandeja de plata de alguien se juzga por lo que rechaza, no por lo que acepta.
 */
class CorreoEntranteRoutesTest {

    private val testSecret = "test-secret-for-correo-entrante-tests-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val secretoDelWebhook = "un-secreto-de-webhook-largo-y-random"
    private val userAId = "user-a-correo"
    private val userBId = "user-b-correo"

    private val direccionBase = "9f3cab@inbound.postmarkapp.com"
    private val direccionDeA get() = "9f3cab+${tokenDeCorreoDe(userAId)}@inbound.postmarkapp.com"

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.secreto", secretoDelWebhook)
        System.setProperty("movi.correo.direccion", direccionBase)
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:correo_entrante_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)
            SchemaUtils.drop(SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)
            Users.insert {
                it[id] = userAId
                it[email] = "a@correo.test"
                it[name] = "User A"
                it[passwordHash] = "hash-a"
            }
            Users.insert {
                it[id] = userBId
                it[email] = "b@correo.test"
                it[name] = "User B"
                it[passwordHash] = "hash-b"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.secreto")
        System.clearProperty("movi.correo.direccion")
        RateLimiter.reset()
    }

    private fun filas() = transaction {
        SmsMessages.selectAll().map {
            Triple(it[SmsMessages.userId], it[SmsMessages.bank], it[SmsMessages.text])
        }
    }

    private fun basic(secreto: String): String =
        "Basic " + Base64.getEncoder().encodeToString("movi:$secreto".toByteArray())

    private fun mintToken(userId: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private fun cuerpoJson(texto: String) = Json.parseToJsonElement(texto).jsonObject

    @Test
    fun `un correo con forma de Postmark deja una fila`() = testApplication {
        application { testModule() }
        val respuesta = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA))
        }
        assertEquals(HttpStatusCode.Accepted, respuesta.status)
        assertTrue(cuerpoJson(respuesta.bodyAsText())["guardado"]!!.jsonPrimitive.boolean)

        val filas = filas()
        assertEquals(1, filas.size)
        assertEquals(userAId, filas[0].first)
        assertEquals("Correo · Bancolombia", filas[0].second)
        assertTrue("21.640" in filas[0].third)
    }

    @Test
    fun `un correo con forma de Mailgun deja una fila, como formulario y como JSON`() = testApplication {
        application { testModule() }
        val comoFormulario = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(alertaMailgunFormulario(direccionDeA))
        }
        assertEquals(HttpStatusCode.Accepted, comoFormulario.status)
        assertEquals(1, filas().size)
        assertEquals(userAId, filas()[0].first)
        assertTrue("138,600.00" in filas()[0].third)

        // El mismo aviso en JSON y con otro Message-Id: mismo texto y mismo tiempo ⇒ dedupe.
        val comoJson = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaMailgunJson(direccionDeA))
        }
        assertEquals(HttpStatusCode.Accepted, comoJson.status)
        assertEquals(1, filas().size, "los dos payloads son el mismo correo")
    }

    @Test
    fun `el mismo correo dos veces deja una sola fila`() = testApplication {
        application { testModule() }
        repeat(2) {
            val r = client.post("/api/correo-entrante") {
                header(HttpHeaders.Authorization, basic(secretoDelWebhook))
                contentType(ContentType.Application.Json)
                setBody(alertaPostmark(direccionDeA))
            }
            assertEquals(HttpStatusCode.Accepted, r.status)
        }
        assertEquals(1, filas().size, "un reintento del proveedor no duplica")

        // Y tampoco lo duplica si el proveedor le cambia el Message-Id: la clave es texto + tiempo.
        val otroId = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA, messageId = "otro-id@bancolombia.com.co"))
        }
        assertEquals(HttpStatusCode.Accepted, otroId.status)
        assertTrue(!cuerpoJson(otroId.bodyAsText())["guardado"]!!.jsonPrimitive.boolean)
        assertEquals(1, filas().size)
    }

    @Test
    fun `sin secreto, con el secreto equivocado, no entra nada`() = testApplication {
        application { testModule() }
        val sinNada = client.post("/api/correo-entrante") {
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA))
        }
        assertEquals(HttpStatusCode.Unauthorized, sinNada.status)

        val equivocado = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic("no-es-el-secreto"))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA))
        }
        assertEquals(HttpStatusCode.Unauthorized, equivocado.status)

        assertEquals(emptyList(), filas(), "nada escribió nada")
    }

    @Test
    fun `sin la env var la puerta no existe`() = testApplication {
        System.clearProperty("movi.correo.secreto")
        application { testModule() }
        val r = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA))
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertEquals(emptyList(), filas())
    }

    @Test
    fun `un destinatario sin token, o de nadie, no escribe nada`() = testApplication {
        application { testModule() }
        val sinToken = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark("alertas@inbound.postmarkapp.com"))
        }
        assertEquals(HttpStatusCode.Accepted, sinToken.status)
        assertTrue(!cuerpoJson(sinToken.bodyAsText())["guardado"]!!.jsonPrimitive.boolean)

        val deNadie = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark("9f3cab+0000000000000000@inbound.postmarkapp.com"))
        }
        assertEquals(HttpStatusCode.Accepted, deNadie.status)
        assertTrue(!cuerpoJson(deNadie.bodyAsText())["guardado"]!!.jsonPrimitive.boolean)

        assertEquals(emptyList(), filas(), "no se le mete un movimiento a nadie por las dudas")
    }

    @Test
    fun `un cuerpo gigante se rechaza`() = testApplication {
        application { testModule() }
        val gigante = alertaPostmark(direccionDeA, cuerpo = "x".repeat(300_000))
        val r = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(gigante)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
        assertEquals(emptyList(), filas())
    }

    @Test
    fun `un cuerpo ilegible es un 400, no un 500`() = testApplication {
        application { testModule() }
        val r = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody("esto no es un correo")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(emptyList(), filas())
    }

    /**
     * Una alerta que no trae plata (un aviso de clave) igual tiene que quedar en la bandeja: es
     * exactamente lo que hace un SMS que no parsea, y el dueño la ignora con un toque. Perder el
     * aviso sería peor que mostrarlo sin propuesta.
     */
    @Test
    fun `una alerta que no parsea igual queda en la bandeja`() = testApplication {
        application { testModule() }
        val r = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(
                alertaPostmark(
                    direccionDeA,
                    asunto = "Bancolombia te informa",
                    cuerpo = "Bancolombia: tu clave fue actualizada.",
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertEquals(1, filas().size)
        assertTrue("clave fue actualizada" in filas()[0].third)
    }

    @Test
    fun `cada usuario tiene su direccion, y el correo cae en su bandeja`() = testApplication {
        application { testModule() }
        val paraB = "9f3cab+${tokenDeCorreoDe(userBId)}@inbound.postmarkapp.com"
        client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(paraB))
        }
        assertEquals(listOf(userBId), filas().map { it.first })
    }

    @Test
    fun `la direccion de reenvio se puede consultar con la sesion propia`() = testApplication {
        application { testModule() }
        val r = client.get("/api/correo-entrante/direccion") {
            header(HttpHeaders.Authorization, "Bearer ${mintToken(userAId, "a@correo.test")}")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val cuerpo = cuerpoJson(r.bodyAsText())
        assertEquals(tokenDeCorreoDe(userAId), cuerpo["token"]!!.jsonPrimitive.content)
        assertEquals(direccionDeA, cuerpo["direccion"]!!.jsonPrimitive.content)
        assertTrue(cuerpo["configurado"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `la direccion de reenvio exige sesion`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/correo-entrante/direccion").status)
    }

    @Test
    fun `el asunto y el cuerpo llegan juntos al texto guardado`() = testApplication {
        application { testModule() }
        client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, basic(secretoDelWebhook))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA))
        }
        val texto = filas().single().third
        assertTrue(texto.startsWith(ASUNTO_CUOTA_DE_MANEJO))
        assertTrue(CUERPO_CUOTA_DE_MANEJO.lineSequence().first() in texto)
        assertTrue("Vigilado" !in texto, "la firma se corta")
    }
}
