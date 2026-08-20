package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Base64
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level tests para F32 (imágenes en el chat de Movi AI): POST /api/ai/chat con un adjunto
 * inválido tiene que responder 422 con un mensaje claro, SIN llamar a Claude — por eso corre
 * sin ANTHROPIC_API_KEY (no hay red en tests). El camino feliz (imagen válida contra Claude
 * real) no se testea acá, ver ola6-t3-report.md.
 *
 * Mismo harness que StatementRoutesTest.kt / ScreenRoutesTest.kt: H2 en memoria + JWT de
 * prueba + configureRouting() completo.
 */
class AiChatImageRoutesTest {

    private val testSecret = "test-secret-for-ai-chat-image-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-ai-chat-image"
    private val userAEmail = "a@ai-chat-image.test"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:ai_chat_image_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions,
            )

            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    private fun tokenFor(userId: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", userAEmail)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

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

    private fun chatBody(imageBase64: String, imageMime: String) = """
        {"messages":[{"role":"USER","content":"¿Qué opinas de esto?","imageBase64":"$imageBase64","imageMime":"$imageMime"}]}
    """.trimIndent()

    @Test
    fun `imagen con mime no soportado responde 422 con mensaje claro`() = testApplication {
        wireApp()
        val res = client.post("/api/ai/chat") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(chatBody(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), "application/pdf"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("no soportado"))
    }

    @Test
    fun `imagen que supera 5 MB decodificados responde 422`() = testApplication {
        wireApp()
        // 6 MB de ceros — el límite es sobre el tamaño YA decodificado, no sobre el base64.
        val bigBytes = ByteArray(6 * 1024 * 1024)
        val res = client.post("/api/ai/chat") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(chatBody(Base64.getEncoder().encodeToString(bigBytes), "image/png"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("5 MB"))
    }

    @Test
    fun `imagen con base64 ilegible responde 422 en vez de 500`() = testApplication {
        wireApp()
        val res = client.post("/api/ai/chat") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(chatBody("no-es-base64-válido!!", "image/png"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    @Test
    fun `mensaje sin imagen no se ve afectado por la validación`() = testApplication {
        wireApp()
        // validateChatImages ignora por completo un mensaje sin adjunto — nunca debe
        // devolver 422 acá, sin importar si ANTHROPIC_API_KEY está configurada en el
        // entorno (eso decide 503 vs seguir a Claude, no es lo que este test protege).
        val res = client.post("/api/ai/chat") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"messages":[{"role":"USER","content":"hola"}]}""")
        }
        assertTrue(res.status != HttpStatusCode.UnprocessableEntity)
    }
}
