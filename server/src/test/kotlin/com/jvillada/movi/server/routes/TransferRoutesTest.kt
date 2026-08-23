package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `POST /api/transfers` y lo que el traspaso obliga a cambiar alrededor: la anulación en
 * cascada, el bloqueo de recategorizar y la promesa central — los dos saldos se mueven, el
 * flujo del mes no se entera.
 *
 * Mismo armazón que [CreditRoutesTest]: H2 en memoria (modo PostgreSQL), un secreto JWT propio
 * del test y la cadena completa de plugins.
 */
class TransferRoutesTest {

    private val testSecret = "test-secret-for-transfer-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-transfers"
    private val userBId = "user-b-transfers"
    private val userAEmail = "a@transfers.test"
    private val userBEmail = "b@transfers.test"

    private val ahorrosId = "acc-ahorros"
    private val cdtId = "acc-cdt"
    private val efectivoUsdId = "acc-usd"
    private val tarjetaId = "acc-tarjeta"
    private val ajenaId = "acc-de-b"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:transfer_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Goals, Subscriptions, CardPaymentDismissals, Cards, Credits, SmsMessages,
                RecurringRules, VoidEvents, Events, StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                SmsMessages, Credits, Cards, CardPaymentDismissals, Subscriptions, Goals,
            )

            Users.insert {
                it[id] = userAId; it[email] = userAEmail; it[name] = "User A"; it[passwordHash] = "hash-a"
            }
            Users.insert {
                it[id] = userBId; it[email] = userBEmail; it[name] = "User B"; it[passwordHash] = "hash-b"
            }

            account(ahorrosId, userAId, "Ahorros", "SAVINGS", "COP")
            account(cdtId, userAId, "CDT", "INVESTMENT", "COP")
            account(efectivoUsdId, userAId, "Efectivo USD", "CASH", "USD")
            account(tarjetaId, userAId, "Visa", "CREDIT_CARD", "COP")
            account(ajenaId, userBId, "Ahorros de B", "SAVINGS", "COP")

            // Ahorros arranca con $1.000.000 declarados como apertura — un evento real, porque
            // los saldos se derivan de eventos.
            Events.insert {
                it[id] = "ev-apertura-ahorros"
                it[userId] = userAId
                it[accountId] = ahorrosId
                it[type] = "INCOME"
                it[amount] = 1_000_000L
                it[currency] = "COP"
                it[category] = "Saldo inicial"
                it[description] = "Saldo inicial"
                it[timestamp] = System.currentTimeMillis()
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
    }

    private fun account(id: String, uid: String, nameValue: String, typeValue: String, currencyValue: String) {
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = uid
            it[name] = nameValue
            it[type] = typeValue
            it[currency] = currencyValue
        }
    }

    // ── Harness ───────────────────────────────────────────────────────────────

    private fun mintToken(userId: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun tokenFor(userId: String): String =
        mintToken(userId, if (userId == userAId) userAEmail else userBEmail)

    private fun Application.testModule() {
        configureSerialization()
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
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

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    private fun transferBody(
        transferId: String = "tr-1",
        fromEventId: String = "ev-from-1",
        toEventId: String = "ev-to-1",
        fromAccountId: String = ahorrosId,
        toAccountId: String = cdtId,
        amount: Long = 250_000L,
        note: String? = null,
    ): String {
        val noteJson = note?.let { ""","note":"$it"""" } ?: ""
        return """{"transferId":"$transferId","fromEventId":"$fromEventId","toEventId":"$toEventId",""" +
            """"fromAccountId":"$fromAccountId","toAccountId":"$toAccountId","amount":$amount,""" +
            """"timestamp":${System.currentTimeMillis()}$noteJson}"""
    }

    private fun eventCount(): Long = transaction { Events.selectAll().count() }

    private fun balanceOf(accounts: String, id: String): Long =
        Json.parseToJsonElement(accounts).jsonArray
            .map { it.jsonObject }
            .single { it["id"]!!.jsonPrimitive.content == id }["balance"]!!.jsonPrimitive.long

    // ── El camino feliz ───────────────────────────────────────────────────────

    @Test
    fun `crea las dos patas en un solo POST`() = testApplication {
        wireApp()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(note = "apertura del CDT"))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val from = body["from"]!!.jsonObject
        val to = body["to"]!!.jsonObject

        assertEquals("EXPENSE", from["type"]!!.jsonPrimitive.content)
        assertEquals(ahorrosId, from["accountId"]!!.jsonPrimitive.content)
        assertEquals("INCOME", to["type"]!!.jsonPrimitive.content)
        assertEquals(cdtId, to["accountId"]!!.jsonPrimitive.content)
        assertEquals("tr-1", from["transferId"]!!.jsonPrimitive.content)
        assertEquals("tr-1", to["transferId"]!!.jsonPrimitive.content)
        assertEquals(TRANSFER_CATEGORY, from["category"]!!.jsonPrimitive.content)
        assertEquals(TRANSFER_CATEGORY, to["category"]!!.jsonPrimitive.content)
        assertEquals("Traspaso a CDT · apertura del CDT", from["description"]!!.jsonPrimitive.content)
        assertEquals("Traspaso desde Ahorros · apertura del CDT", to["description"]!!.jsonPrimitive.content)
        // La bandera derivada llega ya en false: si llegara en true, Análisis y Presupuestos
        // contarían el traspaso hasta el próximo GET.
        assertFalse(from["countsAsCashFlow"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(to["countsAsCashFlow"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `los dos saldos se mueven`() = testApplication {
        wireApp()
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }
        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()

        assertEquals(750_000L, balanceOf(accounts, ahorrosId))
        assertEquals(250_000L, balanceOf(accounts, cdtId))
    }

    /** La razón de existir de toda esta rama. */
    @Test
    fun `el traspaso no toca ingresos ni egresos del mes ni el gasto por categoria`() = testApplication {
        wireApp()
        val before = client.get("/api/dashboard/summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }

        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }

        val after = client.get("/api/dashboard/summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }

        assertEquals(before["monthIncome"], after["monthIncome"])
        assertEquals(before["monthSpent"], after["monthSpent"])
        assertEquals(before["spentByCategory"], after["spentByCategory"])
        // `?: 0L` porque kotlinx-serialization omite los campos que quedaron en su default:
        // que la clave ni aparezca es la forma más fuerte de decir "no se gastó nada".
        assertEquals(0L, after["monthSpent"]?.jsonPrimitive?.long ?: 0L)
        assertFalse(after["spentByCategory"]?.jsonObject.orEmpty().containsKey(TRANSFER_CATEGORY))
    }

    // ── Validaciones ──────────────────────────────────────────────────────────

    private fun assertRejected(expectedMessage: String, body: String) = testApplication {
        wireApp()
        val before = eventCount()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(expectedMessage, response.bodyAsText())
        assertEquals(before, eventCount(), "un traspaso rechazado no puede dejar ningún evento")
    }

    @Test
    fun `rechaza el traspaso a la misma cuenta`() =
        assertRejected(
            "El origen y el destino tienen que ser cuentas distintas",
            transferBody(toAccountId = ahorrosId),
        )

    @Test
    fun `rechaza un monto en cero`() =
        assertRejected("El monto tiene que ser mayor que cero", transferBody(amount = 0L))

    @Test
    fun `rechaza el traspaso entre monedas distintas`() =
        assertRejected(
            "Por ahora solo entre cuentas de la misma moneda",
            transferBody(toAccountId = efectivoUsdId),
        )

    @Test
    fun `rechaza una tarjeta como destino`() =
        assertRejected(
            "Las tarjetas y los préstamos se manejan en Créditos, no con un traspaso",
            transferBody(toAccountId = tarjetaId),
        )

    @Test
    fun `una cuenta que no existe da 404 y no deja nada`() = testApplication {
        wireApp()
        val before = eventCount()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(toAccountId = "acc-que-no-existe"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(before, eventCount())
    }

    @Test
    fun `no se puede traspasar a la cuenta de otro usuario`() = testApplication {
        wireApp()
        val before = eventCount()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(toAccountId = ajenaId))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(before, eventCount())
    }

    /**
     * Atomicidad: la segunda pata choca contra un id que ya existe, así que la primera —que ya
     * se había insertado dentro de la misma transacción— tiene que desaparecer con ella. Sin
     * esto quedaría plata saliendo de Ahorros sin entrar a ningún lado.
     */
    @Test
    fun `si una pata falla no queda ni la otra`() = testApplication {
        wireApp()
        transaction {
            Events.insert {
                it[id] = "ev-to-choque"
                it[userId] = userAId
                it[accountId] = cdtId
                it[type] = "INCOME"
                it[amount] = 1L
                it[currency] = "COP"
                it[category] = "Otros"
                it[description] = "ocupa el id"
                it[timestamp] = System.currentTimeMillis()
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
        val before = eventCount()

        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(toEventId = "ev-to-choque"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(before, eventCount())
        val quedoLaPrimera = transaction {
            Events.selectAll().where { Events.id eq "ev-from-1" }.count() > 0
        }
        assertFalse(quedoLaPrimera, "la pata de origen tenía que revertirse con la transacción")
    }

    // ── Anulación en cascada ──────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.crearTraspaso() {
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }
    }

    @Test
    fun `anular la pata de origen anula tambien la de destino`() = testApplication {
        wireApp()
        crearTraspaso()

        val response = client.post("/api/events/ev-from-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val events = client.get("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertFalse(events.contains("ev-from-1"))
        assertFalse(events.contains("ev-to-1"), "anular una pata sin la otra deja el saldo mintiendo")

        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertEquals(1_000_000L, balanceOf(accounts, ahorrosId))
        assertEquals(0L, balanceOf(accounts, cdtId))
    }

    @Test
    fun `anular la pata de destino tambien anula la de origen`() = testApplication {
        wireApp()
        crearTraspaso()

        client.post("/api/events/ev-to-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }

        val voided = transaction {
            VoidEvents.selectAll().where { VoidEvents.userId eq userAId }
                .map { it[VoidEvents.originalEventId] }.toSet()
        }
        assertTrue(voided.containsAll(setOf("ev-from-1", "ev-to-1")))
    }

    @Test
    fun `anular dos veces la misma pata sigue dando conflicto`() = testApplication {
        wireApp()
        crearTraspaso()
        client.post("/api/events/ev-from-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        val segunda = client.post("/api/events/ev-from-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.Conflict, segunda.status)
    }

    /** Un evento normal no arrastra nada: la cascada es solo para las patas enlazadas. */
    @Test
    fun `anular un evento sin traspaso no toca ningun otro`() = testApplication {
        wireApp()
        crearTraspaso()

        client.post("/api/events/ev-apertura-ahorros/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }

        val voided = transaction {
            VoidEvents.selectAll().where { VoidEvents.userId eq userAId }
                .map { it[VoidEvents.originalEventId] }.toSet()
        }
        assertEquals(setOf("ev-apertura-ahorros"), voided)
    }

    // ── Nadie entra ni sale de la categoría reservada ─────────────────────────

    @Test
    fun `no se puede recategorizar una pata de traspaso`() = testApplication {
        wireApp()
        crearTraspaso()

        val response = client.put("/api/events/ev-from-1/category") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"category":"Mercado"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            "Un traspaso no se puede recategorizar: es plata que se movió entre tus cuentas, no un gasto ni un ingreso. Si te equivocaste, anúlalo y vuelve a hacerlo.",
            response.bodyAsText(),
        )

        val sigueIgual = transaction {
            Events.selectAll().where { Events.id eq "ev-from-1" }.single()[Events.category]
        }
        assertEquals(TRANSFER_CATEGORY, sigueIgual)
    }

    @Test
    fun `no se puede convertir un movimiento cualquiera en pata de traspaso`() = testApplication {
        wireApp()
        val response = client.put("/api/events/ev-apertura-ahorros/category") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"category":"$TRANSFER_CATEGORY"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            "«Traspaso» es una categoría reservada: para mover plata entre tus cuentas usa Agregar → Traspaso.",
            response.bodyAsText(),
        )
    }

    @Test
    fun `POST de un evento suelto con transferId se rechaza`() = testApplication {
        wireApp()
        val before = eventCount()
        val response = client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"ev-suelto","accountId":"$ahorrosId","type":"EXPENSE","amount":1000,""" +
                    """"category":"$TRANSFER_CATEGORY","description":"medio traspaso",""" +
                    """"timestamp":${System.currentTimeMillis()},"transferId":"tr-falso"}""",
            )
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, eventCount())
    }
}
