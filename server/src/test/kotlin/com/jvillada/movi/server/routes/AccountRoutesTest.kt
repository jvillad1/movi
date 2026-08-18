package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hallazgo Critical de la revisión de la Ola 1b: hasta acá, `POST /api/accounts` fabricaba un
 * evento "Saldo inicial"/"Deuda inicial" a partir de `body.balance`. Una cuenta creada offline
 * (`LocalRepository.createAccount`) sincroniza esa misma fila vía `SyncEngine.syncAccounts` con
 * el balance ya movido por eventos reales anotados antes del primer sync — si esta ruta seguía
 * fabricando la apertura a partir de ese balance, el ingreso/gasto real que `syncEvents` empuja
 * justo después se sumaba ENCIMA: doble conteo silencioso y permanente.
 *
 * La decisión (ver `openingEventFor` en :core): el cliente crea la apertura, explícita y una sola
 * vez, con su propio `POST /api/events`. Esta ruta deja de fabricar nada — la columna cruda
 * `accounts.balance` ya no importa, el balance que ve el cliente sale siempre de
 * `enrichWith`/`computeBalances`, derivado de eventos reales.
 *
 * Mismo arnés que CreditRoutesTest/FinanceRoutesTest: H2 en memoria (compat PostgreSQL), JWT
 * local, cadena completa de plugins vía `wireApp()`.
 */
class AccountRoutesTest {

    private val testSecret = "test-secret-for-account-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-accounts"
    private val userEmail = "a@accounts.test"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:account_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals,
            )

            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

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

    private suspend fun ApplicationTestBuilder.createAccount(id: String, type: String, balance: Long) =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"$id","name":"Cuenta","type":"$type","balance":$balance}""")
        }

    private suspend fun ApplicationTestBuilder.postOpeningEvent(
        accountId: String,
        type: String,
        amount: Long,
        description: String,
    ) = client.post("/api/events") {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.ContentType, "application/json")
        setBody(
            """{"id":"","accountId":"$accountId","type":"$type","amount":$amount,
                "category":"Saldo inicial","description":"$description","timestamp":0}""",
        )
    }

    private suspend fun ApplicationTestBuilder.accountBalance(id: String): Long =
        Json.parseToJsonElement(
            client.get("/api/accounts/$id") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject["balance"]!!.jsonPrimitive.long

    private suspend fun ApplicationTestBuilder.summary() =
        Json.parseToJsonElement(
            client.get("/api/finance-summary") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.eventCount(): Int =
        this["eventCount"]?.jsonPrimitive?.long?.toInt() ?: 0

    private fun eventsInDb(): Int = transaction { Events.selectAll().count().toInt() }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `POST crea la cuenta con el balance recibido pero no fabrica ningun evento`() = testApplication {
        wireApp()
        val res = createAccount("acc-savings", "SAVINGS", 1_000_000L)
        assertEquals(HttpStatusCode.Created, res.status)

        assertEquals(0, eventsInDb(), "crear la cuenta no debe insertar ninguna fila en events")
    }

    @Test
    fun `el balance derivado de la cuenta es 0 hasta que el cliente postea la apertura`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)

        assertEquals(
            0L,
            accountBalance("acc-savings"),
            "sin ningún evento, el balance derivado (enrichWith/computeBalances) tiene que ser 0 " +
                "aunque la fila cruda de accounts.balance haya llegado en 1.000.000",
        )
    }

    @Test
    fun `tras postear el evento de apertura el balance derivado queda en la cifra declarada`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)

        val evRes = postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")
        assertEquals(HttpStatusCode.Created, evRes.status)

        assertEquals(1_000_000L, accountBalance("acc-savings"))
    }

    @Test
    fun `la apertura posteada por el cliente sigue sin contar como ingreso del mes ni como movimiento`() =
        testApplication {
            wireApp()
            createAccount("acc-savings", "SAVINGS", 1_000_000L)
            postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")

            val body = summary()
            assertEquals(0L, body["ingresos"]!!.jsonPrimitive.long, "la apertura no es un ingreso del mes")
            assertEquals(0L, body["egresos"]!!.jsonPrimitive.long)
            assertEquals(
                0,
                body.eventCount(),
                "la apertura no cuenta como \"primer movimiento\" para la guía de primeros pasos",
            )
        }

    /**
     * Sin este caso, el fix de la Ola 1b (no fabricar la apertura en el server) podría revertirse
     * por accidente sin que ningún test lo note: si `POST /api/accounts` volviera a fabricar el
     * evento, este test vería DOS eventos en vez de uno tras el POST explícito del cliente —el
     * mismo doble conteo que el hallazgo Critical describe para el escenario offline.
     */
    @Test
    fun `crear la cuenta y postear la apertura no deja eventos duplicados`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)
        postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")

        assertEquals(1, eventsInDb(), "solo el evento que posteó el cliente — ninguno fabricado por el server")
    }
}
