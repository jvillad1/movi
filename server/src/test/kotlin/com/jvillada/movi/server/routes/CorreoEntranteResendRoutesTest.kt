package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.ContenidoDeResend
import com.jvillada.movi.server.correo.LectorDeCorreosRecibidos
import com.jvillada.movi.server.correo.SMS_EQUIVALENTE_CUOTA_DE_MANEJO
import com.jvillada.movi.server.correo.contenidoDeResend
import com.jvillada.movi.server.correo.eventoDeResend
import com.jvillada.movi.server.correo.firmarComoSvix
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
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
 * `POST /api/correo-entrante/resend`, extremo a extremo contra H2 y con un lector de Resend falso:
 * **ninguna prueba llama a la API de Resend**. Igual que la ruta de Postmark, se juzga sobre todo
 * por lo que NO escribe.
 */
class CorreoEntranteResendRoutesTest {

    private val testSecret = "test-secret-for-correo-resend-tests-minimum-32"
    private val secretoDeResend = "whsec_" + Base64.getEncoder().encodeToString("secreto-de-prueba-del-webhook-32b".toByteArray())

    private val userAId = "user-a-resend"
    private val userBId = "user-b-resend"
    private val dominio = "abc123.resend.app"
    private val direccionDeA get() = "alertas+${tokenDeCorreoDe(userAId)}@$dominio"
    private val direccionDeB get() = "alertas+${tokenDeCorreoDe(userBId)}@$dominio"

    /** Lo que «tiene» Resend, por id, y los ids que se le pidieron. */
    private val correosEnResend = mutableMapOf<String, ContenidoDeResend>()
    private val pedidos = CopyOnWriteArrayList<String>()
    private val lectorOriginal = LectorDeCorreosRecibidos.actual

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.resend.secreto", secretoDeResend)
        System.setProperty("movi.correo.direccion", "alertas@$dominio")
        RateLimiter.reset()
        LectorDeCorreosRecibidos.actual = LectorDeCorreosRecibidos { id ->
            pedidos += id
            correosEnResend[id] ?: ContenidoDeResend.NoEncontrado
        }
        Database.connect(
            url = "jdbc:h2:mem:correo_resend_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)
            SchemaUtils.drop(SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)
            for ((id, correo) in listOf(userAId to "a@resend.test", userBId to "b@resend.test")) {
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
        System.clearProperty("movi.correo.resend.secreto")
        System.clearProperty("movi.correo.direccion")
        LectorDeCorreosRecibidos.actual = lectorOriginal
        RateLimiter.reset()
    }

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { JWTPrincipal(it.payload) }
            }
        }
        configureRouting()
    }

    private fun filas() = transaction {
        SmsMessages.selectAll().map {
            listOf(it[SmsMessages.userId], it[SmsMessages.bank], it[SmsMessages.text], it[SmsMessages.state])
        }
    }

    private fun guardado(r: String) = Json.parseToJsonElement(r).jsonObject["guardado"]!!.jsonPrimitive.boolean

    /** Postea [cuerpo] firmado como lo firma Resend; los parámetros permiten romper la firma. */
    private suspend fun HttpClient.webhook(
        cuerpo: String,
        secreto: String = secretoDeResend,
        timestamp: Long = System.currentTimeMillis() / 1000,
        firmar: Boolean = true,
        idDeEntrega: String = "msg_2Lh9KRb6pzN4n1s2",
    ): HttpResponse = post("/api/correo-entrante/resend") {
        contentType(ContentType.Application.Json)
        if (firmar) {
            header("svix-id", idDeEntrega)
            header("svix-timestamp", timestamp.toString())
            header("svix-signature", firmarComoSvix(secreto, idDeEntrega, timestamp.toString(), cuerpo.toByteArray()))
        }
        setBody(cuerpo)
    }

    @Test
    fun `una alerta de Bancolombia reenviada por Gmail cae en la bandeja del duenio del token`() = testApplication {
        application { testModule() }
        correosEnResend["56761188-7520-42d8-8898-ff6fc54ce618"] = ContenidoDeResend.Encontrado(
            contenidoDeResend(recibidoPara = listOf(direccionDeA), reenviadoA = direccionDeA),
        )
        val r = client.webhook(eventoDeResend(recibidoPara = listOf(direccionDeA)))
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue(guardado(r.bodyAsText()))

        val fila = filas().single()
        assertEquals(userAId, fila[0])
        assertEquals("Correo · Bancolombia", fila[1])
        assertTrue(SMS_EQUIVALENTE_CUOTA_DE_MANEJO in fila[2])
        assertEquals("pending", fila[3])
        assertEquals(listOf("56761188-7520-42d8-8898-ff6fc54ce618"), pedidos)
    }

    @Test
    fun `basta X-Forwarded-To cuando received_for no viene`() = testApplication {
        application { testModule() }
        correosEnResend["56761188-7520-42d8-8898-ff6fc54ce618"] =
            ContenidoDeResend.Encontrado(contenidoDeResend(reenviadoA = direccionDeA))
        val r = client.webhook(eventoDeResend())
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertEquals(listOf(userAId), filas().map { it[0] })
    }

    @Test
    fun `el mismo webhook reintentado deja una sola fila`() = testApplication {
        application { testModule() }
        correosEnResend["56761188-7520-42d8-8898-ff6fc54ce618"] =
            ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf(direccionDeA)))
        repeat(2) {
            assertEquals(HttpStatusCode.Accepted, client.webhook(eventoDeResend()).status)
        }
        assertEquals(1, filas().size)
    }

    @Test
    fun `firma invalida, sin firma, otro secreto o timestamp viejo son 401 y no escriben`() = testApplication {
        application { testModule() }
        correosEnResend["56761188-7520-42d8-8898-ff6fc54ce618"] =
            ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf(direccionDeA)))
        val evento = eventoDeResend()
        val ahora = System.currentTimeMillis() / 1000
        val otroSecreto = "whsec_" + Base64.getEncoder().encodeToString("otro-secreto-que-no-es-el-de-movi".toByteArray())

        assertEquals(HttpStatusCode.Unauthorized, client.webhook(evento, firmar = false).status, "sin firma")
        assertEquals(HttpStatusCode.Unauthorized, client.webhook(evento, secreto = otroSecreto).status, "otro secreto")
        assertEquals(HttpStatusCode.Unauthorized, client.webhook(evento, timestamp = ahora - 6 * 60).status, "viejo")
        assertEquals(HttpStatusCode.Unauthorized, client.webhook(evento, timestamp = ahora + 6 * 60).status, "del futuro")

        // Firmado bien pero con el cuerpo cambiado en el camino.
        val alterado = client.post("/api/correo-entrante/resend") {
            contentType(ContentType.Application.Json)
            header("svix-id", "msg_1")
            header("svix-timestamp", ahora.toString())
            header("svix-signature", firmarComoSvix(secretoDeResend, "msg_1", ahora.toString(), evento.toByteArray()))
            setBody(evento.replace("email.received", "email.received "))
        }
        assertEquals(HttpStatusCode.Unauthorized, alterado.status, "cuerpo alterado")

        assertEquals(emptyList(), filas())
        assertEquals(emptyList(), pedidos, "sin firma válida ni siquiera se le pregunta a Resend")
    }

    @Test
    fun `sin RESEND_WEBHOOK_SECRET la puerta no existe`() = testApplication {
        System.clearProperty("movi.correo.resend.secreto")
        application { testModule() }
        val r = client.webhook(eventoDeResend(recibidoPara = listOf(direccionDeA)))
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertEquals(emptyList(), filas())
        assertEquals(emptyList(), pedidos)
    }

    @Test
    fun `un evento que no es email received no hace nada`() = testApplication {
        application { testModule() }
        val r = client.webhook(eventoDeResend(tipo = "email.delivered", recibidoPara = listOf(direccionDeA)))
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(emptyList(), filas())
        assertEquals(emptyList(), pedidos)
    }

    @Test
    fun `sin token, o con el token de nadie, es 202 y no escribe`() = testApplication {
        application { testModule() }
        correosEnResend["sin-token"] = ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf("alertas@$dominio")))
        correosEnResend["de-nadie"] = ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf("alertas+0000000000000000@$dominio")))
        for (id in listOf("sin-token", "de-nadie")) {
            val r = client.webhook(eventoDeResend(idDelCorreo = id))
            assertEquals(HttpStatusCode.Accepted, r.status)
            assertTrue(!guardado(r.bodyAsText()))
        }
        assertEquals(emptyList(), filas())
    }

    @Test
    fun `si la API de Resend falla o no lo encuentra, no escribe y pide reintento`() = testApplication {
        application { testModule() }
        correosEnResend["se-cayo"] = ContenidoDeResend.Fallo(500)
        correosEnResend["sin-permiso"] = ContenidoDeResend.Fallo(401)
        // "no-existe" no está en el mapa: el lector falso contesta NoEncontrado (un 404).
        for (id in listOf("se-cayo", "sin-permiso", "no-existe")) {
            val r = client.webhook(eventoDeResend(idDelCorreo = id, recibidoPara = listOf(direccionDeA)))
            assertTrue(r.status.value in 500..599, "$id contestó ${r.status}: Resend solo reintenta un no-2xx")
        }
        correosEnResend["sin-clave"] = ContenidoDeResend.SinClave
        assertEquals(
            HttpStatusCode.ServiceUnavailable,
            client.webhook(eventoDeResend(idDelCorreo = "sin-clave", recibidoPara = listOf(direccionDeA))).status,
        )
        assertEquals(emptyList(), filas())
    }

    @Test
    fun `cada correo cae solo en la bandeja de su duenio`() = testApplication {
        application { testModule() }
        correosEnResend["para-b"] = ContenidoDeResend.Encontrado(
            contenidoDeResend(recibidoPara = listOf(direccionDeB), messageId = "<para-b@bancolombia.com.co>"),
        )
        correosEnResend["para-a"] = ContenidoDeResend.Encontrado(
            contenidoDeResend(
                recibidoPara = listOf(direccionDeA),
                messageId = "<para-a@bancolombia.com.co>",
                cuerpo = "Bancolombia: Compraste \$18.500,00 en TIENDA DE PRUEBA con tu T.Deb *1111, el 01/10/2026 a las 10:00.",
            ),
        )
        client.webhook(eventoDeResend(idDelCorreo = "para-b"))
        client.webhook(eventoDeResend(idDelCorreo = "para-a"))

        val porUsuario = filas().groupBy({ it[0] }, { it[2] })
        assertEquals(setOf(userAId, userBId), porUsuario.keys)
        assertTrue(porUsuario[userBId]!!.single().contains("21.640"))
        assertTrue(porUsuario[userAId]!!.single().contains("18.500"))
    }

    @Test
    fun `la ruta de Postmark no acepta la firma de Resend, ni la de Resend el secreto de Postmark`() = testApplication {
        System.setProperty("movi.correo.secreto", "secreto-de-postmark")
        try {
            application { testModule() }
            correosEnResend["56761188-7520-42d8-8898-ff6fc54ce618"] =
                ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf(direccionDeA)))
            val conBasic = client.post("/api/correo-entrante/resend") {
                header("Authorization", "Basic " + Base64.getEncoder().encodeToString("movi:secreto-de-postmark".toByteArray()))
                contentType(ContentType.Application.Json)
                setBody(eventoDeResend())
            }
            assertEquals(HttpStatusCode.Unauthorized, conBasic.status)
            assertEquals(emptyList(), filas())
        } finally {
            System.clearProperty("movi.correo.secreto")
        }
    }
}
