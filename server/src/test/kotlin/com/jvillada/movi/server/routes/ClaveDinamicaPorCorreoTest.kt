package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.alertaPostmark
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.CorreosDescartados
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 93 (revisión) · una clave dinámica que llega por correo no es un movimiento
 *
 * El filtro de Gmail reenvía a Movi **todo** el correo de los bancos, y entre eso vienen las claves
 * dinámicas y los códigos de verificación (`FRASES_DE_CODIGO` en `QueEsEsteMensaje.kt`). Tienen que
 * quedar apartados con su motivo y no esperar una decisión en «Por revisar». Por el camino real del
 * correo: el webhook, el mismo `guardarCorreoEntrante` de siempre.
 */
class ClaveDinamicaPorCorreoTest {

    private val testSecret = "test-secret-for-clave-dinamica-tests-minimum-32"
    private val secretoDelWebhook = "un-secreto-de-webhook-largo-y-random"
    private val userAId = "user-a-clave"
    private val direccionDeA get() = "9f3cab+${tokenDeCorreoDe(userAId)}@inbound.postmarkapp.com"
    private var contador = 0

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.secreto", secretoDelWebhook)
        System.setProperty("movi.correo.direccion", "9f3cab@inbound.postmarkapp.com")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:clave_dinamica_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions, CorreosDescartados)
            SchemaUtils.drop(CorreosDescartados, SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions, CorreosDescartados)
            Users.insert {
                it[id] = userAId
                it[email] = "a@clave.test"
                it[name] = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.secreto")
        System.clearProperty("movi.correo.direccion")
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

    private suspend fun io.ktor.client.HttpClient.llega(asunto: String, cuerpo: String) {
        contador++
        val r = post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, "Basic " + Base64.getEncoder().encodeToString("movi:$secretoDelWebhook".toByteArray()))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark(direccionDeA, asunto = asunto, cuerpo = cuerpo, messageId = "otp-$contador@bancolombia.com.co"))
        }
        assertEquals(HttpStatusCode.Accepted, r.status)
    }

    private fun filas() = transaction {
        SmsMessages.selectAll().map { Triple(it[SmsMessages.state], it[SmsMessages.motivoApartado], it[SmsMessages.text]) }
    }

    @Test
    fun `una clave dinamica por correo se aparta como codigo de verificacion y no espera decision`() = testApplication {
        application { testModule() }
        client.llega("Tu clave dinámica de Bancolombia", "Hola Juan, tu clave dinámica es 482913. Es personal e intransferible, no la compartas.")
        client.llega("Código de verificación", "Tu código de verificación es 553201. Vence en 5 minutos.")
        client.llega("Aprueba tu compra", "Para aprobar tu compra por \$85.000 usa la clave dinámica 774120. No la compartas con nadie.")
        client.llega("Tu OTP", "Usa el OTP 90311 para continuar con tu transacción.")
        val filas = filas()
        assertEquals(4, filas.size)
        for ((estado, motivo, texto) in filas) {
            assertEquals("ignored", estado, "no espera una decisión: $texto")
            assertEquals("CODIGO_DE_VERIFICACION", motivo, "queda apartado como código: $texto")
        }
    }

    @Test
    fun `una alerta de un movimiento del mismo banco sigue esperando decision`() = testApplication {
        application { testModule() }
        client.llega("Compra aprobada", "Bancolombia: Compraste \$21.640 en Rappi con tu tarjeta *4821 el 16/09/2026.")
        val (estado, motivo, _) = filas().single()
        assertEquals("pending", estado)
        assertEquals(null, motivo)
    }
}
