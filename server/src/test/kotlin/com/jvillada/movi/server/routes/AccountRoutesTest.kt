package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.delete
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
            SchemaUtils.drop(Cards, Goals, 
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, Cards, Goals,
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

    // ── F55: DELETE /api/accounts/{id} ──────────────────────────────────────────

    /**
     * Arma una cuenta LOAN con de todo lo que F55 tiene que barrer: un evento normal, un
     * evento anulado (deja fila en void_events), un evento marcado "No es pago de tarjeta"
     * (deja fila en card_payment_dismissals) y términos de crédito. Todo tiene que
     * desaparecer en un solo DELETE.
     */
    private fun seedAccountWithEverything(accountId: String, uid: String) {
        transaction {
            Accounts.insert {
                it[id]       = accountId
                it[userId]   = uid
                it[name]     = "Libranza"
                it[type]     = "LOAN"
                it[balance]  = 0
                it[currency] = "COP"
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-normal"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 10_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Cuota"
                it[Events.timestamp] = 0
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-anulado"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 5_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Duplicado"
                it[Events.timestamp] = 0
            }
            VoidEvents.insert {
                it[VoidEvents.id]              = "$accountId-void"
                it[VoidEvents.userId]          = uid
                it[VoidEvents.originalEventId] = "$accountId-ev-anulado"
                it[VoidEvents.timestamp]       = 0
            }
            // card_terms nació en esta misma ola, DESPUÉS del DELETE: la revisión encontró que
            // quedaba huérfano. Se siembra aunque la cuenta sea LOAN — al DELETE le da igual,
            // barre por accountId.
            Cards.insert {
                it[Cards.accountId]  = accountId
                it[Cards.userId]     = uid
                it[Cards.bank]       = "Banco"
                it[Cards.paymentDay] = 15
            }
            // goals nació en la Ola 6, también después del DELETE — mismo patrón que card_terms.
            Goals.insert {
                it[Goals.id]        = "$accountId-meta"
                it[Goals.userId]    = uid
                it[Goals.name]      = "Viaje"
                it[Goals.target]    = 1_000_000
                it[Goals.accountId] = accountId
                it[Goals.createdAt] = 0
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-tc"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 300_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Parece pago de tarjeta"
                it[Events.timestamp] = 0
            }
            CardPaymentDismissals.insert {
                it[CardPaymentDismissals.userId]  = uid
                it[CardPaymentDismissals.eventId] = "$accountId-ev-tc"
            }
            Credits.insert {
                it[Credits.accountId]   = accountId
                it[Credits.userId]      = uid
                it[Credits.bank]        = "Banco"
                it[Credits.principal]   = 1_000_000
                it[Credits.rateEa]      = 1.5
                it[Credits.termMonths]  = 12
                it[Credits.installment] = 90_000
                it[Credits.dayOfMonth]  = 5
                it[Credits.startDate]   = "2026-01-01"
            }
        }
    }

    private fun rowCounts(accountId: String): Quintuple =
        transaction {
            val eventIds = Events.selectAll().where { Events.accountId eq accountId }.map { it[Events.id] }
            Quintuple(
                accounts = Accounts.selectAll().where { Accounts.id eq accountId }.count().toInt(),
                events = eventIds.size,
                voidEvents = if (eventIds.isEmpty()) 0 else
                    VoidEvents.selectAll().where { VoidEvents.originalEventId inList eventIds }.count().toInt(),
                dismissals = if (eventIds.isEmpty()) 0 else
                    CardPaymentDismissals.selectAll().where { CardPaymentDismissals.eventId inList eventIds }.count().toInt(),
                credits = Credits.selectAll().where { Credits.accountId eq accountId }.count().toInt(),
                cards = Cards.selectAll().where { Cards.accountId eq accountId }.count().toInt(),
                goals = Goals.selectAll().where { Goals.accountId eq accountId }.count().toInt(),
            )
        }

    private data class Quintuple(val accounts: Int, val events: Int, val voidEvents: Int, val dismissals: Int, val credits: Int, val cards: Int, val goals: Int)

    @Test
    fun `DELETE borra la cuenta, sus eventos, anulaciones, dismissals y terminos de credito en una sola pasada`() = testApplication {
        wireApp()
        val accId = "acc-full-a"
        seedAccountWithEverything(accId, userId)
        assertEquals(Quintuple(1, 3, 1, 1, 1, 1, 1), rowCounts(accId), "el seed dejó todo lo que el DELETE tiene que barrer")

        val res = client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NoContent, res.status)

        assertEquals(Quintuple(0, 0, 0, 0, 0, 0, 0), rowCounts(accId), "no debe quedar NADA de la cuenta borrada")
    }

    @Test
    fun `DELETE de una cuenta inexistente es 404`() = testApplication {
        wireApp()
        val res = client.delete("/api/accounts/no-existe") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `el segundo DELETE sobre la misma cuenta es 404`() = testApplication {
        wireApp()
        val accId = "acc-twice"
        createAccount(accId, "SAVINGS", 0)
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
    }

    @Test
    fun `DELETE no borra cuentas de otro usuario ni deja borrar las propias de otro`() = testApplication {
        wireApp()
        val otherUserId = "user-b-accounts"
        transaction {
            Users.insert {
                it[id]           = otherUserId
                it[email]        = "b@accounts.test"
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
        val accId = "acc-full-a"
        seedAccountWithEverything(accId, userId)

        // User B intenta borrar la cuenta de A: 404 (no la ve, no es suya) y no toca nada.
        val otherToken = tokenFor(otherUserId, "b@accounts.test")
        val res = client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $otherToken") }
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(Quintuple(1, 3, 1, 1, 1, 1, 1), rowCounts(accId), "el intento de B no debe tocar nada de A")

        // A sí puede borrar la suya — y lo de B (si tuviera algo) quedaría intacto; acá alcanza
        // con confirmar que A borra la suya sin problema.
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
    }
}
