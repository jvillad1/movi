package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.EVENT_DATE_IN_FUTURE
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level tests for GET /api/events/card-payment-candidates (Task 2 de SP-ajustar-saldo).
 *
 * Mismo arnés que CreditRoutesTest: H2 en memoria (compat PostgreSQL), secreto JWT local,
 * cadena completa de plugins vía `wireApp()`.
 */
class EventRoutesTest {

    private val testSecret = "test-secret-for-event-routes-tests-minimum-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-events"
    private val userBId = "user-b-events"
    private val userAEmail = "a@events.test"
    private val userBEmail = "b@events.test"

    private val savingsAccountId    = "acc-savings-a"
    private val creditCardAccountId = "acc-cc-a"
    private val loanAccountId       = "acc-loan-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:event_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringOccurrences, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, RecurringOccurrences, SmsMessages, Credits,
                CardPaymentDismissals,
            )

            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }

            Accounts.insert {
                it[id]       = savingsAccountId
                it[userId]   = userAId
                it[name]     = "Ahorros"
                it[type]     = "SAVINGS"
                it[currency] = "COP"
            }
            Accounts.insert {
                it[id]       = creditCardAccountId
                it[userId]   = userAId
                it[name]     = "Tarjeta Bancolombia"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }
            Accounts.insert {
                it[id]       = loanAccountId
                it[userId]   = userAId
                it[name]     = "Libranza"
                it[type]     = "LOAN"
                it[currency] = "COP"
            }
        }
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private fun mintToken(userId: String, email: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    private fun tokenFor(userId: String): String =
        mintToken(userId, if (userId == userAId) userAEmail else userBEmail)

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

    // ── event seeding helper ─────────────────────────────────────────────────

    private fun seedEvent(
        id: String,
        userId: String,
        accountId: String,
        type: String,
        description: String,
        category: String,
        amount: Long = 100_000L,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        transaction {
            Events.insert {
                it[Events.id]                   = id
                it[Events.userId]               = userId
                it[Events.accountId]            = accountId
                it[Events.type]                 = type
                it[Events.amount]                = amount
                it[Events.currency]             = "COP"
                it[Events.category]             = category
                it[Events.description]          = description
                it[Events.timestamp]            = timestamp
                it[Events.eventSource]          = "STATEMENT"
                it[Events.reconciliationStatus] = "UNCONFIRMED"
            }
        }
    }

    private fun voidEvent(eventId: String, userId: String) {
        transaction {
            VoidEvents.insert {
                it[id]              = "void_$eventId"
                it[VoidEvents.userId] = userId
                it[originalEventId]  = eventId
                it[timestamp]        = System.currentTimeMillis()
            }
        }
    }

    private suspend fun ApplicationTestBuilder.candidatesFor(userId: String) =
        client.get("/api/events/card-payment-candidates") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
        }

    private suspend fun ApplicationTestBuilder.putCategory(id: String, category: String, userId: String) =
        client.put("/api/events/$id/category") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category"}""")
        }

    private suspend fun ApplicationTestBuilder.notCardPayment(id: String, userId: String) =
        client.post("/api/events/$id/not-card-payment") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
        }

    private suspend fun ApplicationTestBuilder.postEvent(userId: String, body: String) =
        client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * Ola 10: **una categoría reservada no se anota a mano.** `isCashFlow` las excluye por nombre,
     * así que un gasto real escrito como «Pago de tarjeta» se guardaba y desaparecía de los gastos
     * del mes sin decir nada. El campo de categoría avisaba, pero un cartel no es una guarda: se
     * cerraba el selector con la categoría puesta y el botón seguía habilitado.
     */
    @Test
    fun `un gasto MANUAL en una categoria reservada se rechaza`() = testApplication {
        wireApp()
        val res = postEvent(
            userAId,
            """{"id":"evt-res","accountId":"$savingsAccountId","type":"EXPENSE","amount":1000,
                "category":"Pago de tarjeta","description":"a mano","source":"MANUAL","timestamp":0}""",
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status, res.bodyAsText())
    }

    @Test
    fun `el pago de tarjeta que viene de un SMS si se acepta`() = testApplication {
        wireApp()
        // Por esta misma ruta llega el pago de tarjeta detectado en un mensaje del banco y
        // confirmado por el dueño (SmsRoutes.categoryFor + SMSReconcileScreen). Ahí la categoría
        // reservada es la CORRECTA, y un rechazo general habría roto ese flujo.
        val res = postEvent(
            userAId,
            """{"id":"evt-sms","accountId":"$savingsAccountId","type":"EXPENSE","amount":1000,
                "category":"Pago de tarjeta","description":"Pago autom TC","source":"SMS","timestamp":0}""",
        )
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
    }

    @Test
    fun `el evento de apertura de una cuenta sigue pasando, aunque sea MANUAL y reservado`() = testApplication {
        wireApp()
        // `openingEventFor` lo crea el propio cliente al abrir una cuenta con saldo: nace MANUAL y
        // con categoría reservada, y es correcto. Bloquearlo habría roto crear cuentas.
        val res = postEvent(
            userAId,
            """{"id":"evt-open","accountId":"$savingsAccountId","type":"INCOME","amount":50000,
                "category":"Saldo inicial","description":"Saldo inicial","source":"MANUAL","timestamp":0}""",
        )
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
    }

    @Test
    fun `propone un EXPENSE de cuenta de activo cuya descripcion matchea un patron`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        val res = candidatesFor(userAId)
        assertEquals(HttpStatusCode.OK, res.status)
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        assertEquals("evt-1", arr[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PAGO QR de un comercio real no es candidato aunque este en cuenta de activo`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-qr", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "PAGO QR Dogger", category = "Otros",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "PAGO QR es un gasto real, nunca debería proponerse como pago de tarjeta")
    }

    @Test
    fun `excluye eventos de cuentas de deuda aunque el texto matchee`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-cc", userId = userAId, accountId = creditCardAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        seedEvent(
            id = "evt-loan", userId = userAId, accountId = loanAccountId,
            type = "EXPENSE", description = "Pago a tarjeta", category = "Otros",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "CREDIT_CARD y LOAN no son cuentas de activo")
    }

    @Test
    fun `excluye eventos ya categorizados como Pago de tarjeta`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-already", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Pago de tarjeta",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "ya está bien categorizado, no hay nada que proponer")
    }

    @Test
    fun `excluye eventos anulados`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-voided", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        voidEvent("evt-voided", userAId)

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty())
    }

    @Test
    fun `excluye eventos INCOME aunque el texto matchee`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-income", userId = userAId, accountId = savingsAccountId,
            type = "INCOME", description = "Pago tarjeta de crédito", category = "Otros ingresos",
        )

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "el endpoint solo propone EXPENSE — el pago del extracto sale de la cuenta de ahorros")
    }

    @Test
    fun `usuario B no ve los candidatos de usuario A`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        assertEquals("[]", candidatesFor(userBId).bodyAsText())
    }

    /**
     * El endpoint PROPONE, no recategoriza: es la promesa central del diseño (Task 3 la usa
     * para el endpoint de confirmación). Este test cubre que un GET —incluso repetido— no
     * cambia ni la categoría ni ningún otro campo del evento en la base.
     */
    @Test
    fun `GET no modifica ningun evento`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-1", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        candidatesFor(userAId)
        candidatesFor(userAId)

        val row = transaction {
            Events.selectAll().where { Events.id eq "evt-1" }.single()
        }
        assertEquals("Otros", row[Events.category])
        assertEquals("Pago tarjeta de crédito", row[Events.description])
        assertEquals("EXPENSE", row[Events.type])
    }

    // ── PUT /api/events/{id}/category (Task 3 de SP-ajustar-saldo) ─────────────

    /**
     * El caso central del diseño: un pago del extracto cargado en la cuenta de ahorros
     * (cuenta de activo, donde por tipo de cuenta `countsAsCashFlow` daría `true`) deja de
     * contar como flujo de caja en cuanto se le pone la categoría "Pago de tarjeta" — la regla
     * de [com.jvillada.movi.shared.model.isCashFlow] gana sobre el tipo de cuenta.
     */
    @Test
    fun `PUT category actualiza la categoria y responde countsAsCashFlow derivado`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-pago", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        val res = putCategory("evt-pago", "Pago de tarjeta", userAId)
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Pago de tarjeta", body["category"]!!.jsonPrimitive.content)
        assertEquals(false, body["countsAsCashFlow"]!!.jsonPrimitive.boolean)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-pago" }.single() }
        assertEquals("Pago de tarjeta", row[Events.category])
    }

    @Test
    fun `PUT category responde 404 en vez de 403 si el evento es de otro usuario`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-a", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val res = putCategory("evt-a", "Pago de tarjeta", userBId)
        assertEquals(HttpStatusCode.NotFound, res.status)

        // el evento de A no cambió
        val row = transaction { Events.selectAll().where { Events.id eq "evt-a" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `PUT category responde 404 si el evento no existe`() = testApplication {
        wireApp()
        val res = putCategory("no-existe", "Pago de tarjeta", userAId)
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    /**
     * Hallazgo 3 de la revisión de `396a695`: un evento anulado no está disponible para
     * recategorizar, mismo criterio que `loadNonVoidedEventsIn` usa para los GET — ningún GET
     * vuelve a mostrar un evento anulado, así que el `countsAsCashFlow` de una respuesta 200 acá
     * no se vería en ninguna pantalla. Responde igual que "no existe": 404, sin tocar la fila.
     */
    @Test
    fun `PUT category responde 404 y no toca la fila si el evento esta anulado`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-voided-put", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )
        voidEvent("evt-voided-put", userAId)

        val res = putCategory("evt-voided-put", "Pago de tarjeta", userAId)
        assertEquals(HttpStatusCode.NotFound, res.status)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-voided-put" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `PUT category rechaza categoria vacia con 400`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-b", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val res = putCategory("evt-b", "", userAId)
        assertEquals(HttpStatusCode.BadRequest, res.status)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-b" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `PUT category rechaza categoria de mas de 60 caracteres con 400`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-c", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Compra", category = "Otros",
        )

        val tooLong = "x".repeat(61)
        val res = putCategory("evt-c", tooLong, userAId)
        assertEquals(HttpStatusCode.BadRequest, res.status)

        val row = transaction { Events.selectAll().where { Events.id eq "evt-c" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    // ── POST /api/events/{id}/not-card-payment ("No es") ───────────────────────

    @Test
    fun `descartar saca el evento del GET de candidatos`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-fp", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        assertEquals(1, Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray.size)

        val res = notCardPayment("evt-fp", userAId)
        assertEquals(HttpStatusCode.NoContent, res.status)

        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "un candidato descartado no debería volver a proponerse")
    }

    @Test
    fun `descartar no cambia la categoria del evento`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-fp2", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        notCardPayment("evt-fp2", userAId)

        // "No es" saca el candidato de la propuesta, pero el gasto real sigue contando como
        // flujo de caja del mes — es justo lo que hay que preservar en un falso positivo.
        val row = transaction { Events.selectAll().where { Events.id eq "evt-fp2" }.single() }
        assertEquals("Otros", row[Events.category])
    }

    @Test
    fun `descartar dos veces es 204 las dos`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-fp3", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        assertEquals(HttpStatusCode.NoContent, notCardPayment("evt-fp3", userAId).status)
        assertEquals(HttpStatusCode.NoContent, notCardPayment("evt-fp3", userAId).status)
    }

    @Test
    fun `descartar un evento de otro usuario es 404 y no escribe nada`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-fp4", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        val res = notCardPayment("evt-fp4", userBId)
        assertEquals(HttpStatusCode.NotFound, res.status)

        // Nada se escribió: el candidato de A sigue proponiéndose para A.
        val arr = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertEquals(1, arr.size)
    }

    @Test
    fun `descartar un evento inexistente es 404`() = testApplication {
        wireApp()
        val res = notCardPayment("no-existe", userAId)
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `descartar un evento anulado es 404 y no escribe nada`() = testApplication {
        wireApp()
        seedEvent(
            id = "evt-fp-voided", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        voidEvent("evt-fp-voided", userAId)

        val res = notCardPayment("evt-fp-voided", userAId)
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    /**
     * Aislamiento del filtro: que A descarte un candidato no puede afectar a B. Sin esto, un
     * `NOT IN` sin filtrar por usuario borraría el candidato de B también.
     */
    @Test
    fun `un evento descartado por A sigue siendo candidato para B si B tuviera uno igual`() = testApplication {
        wireApp()
        val savingsAccountB = "acc-savings-b"
        transaction {
            Accounts.insert {
                it[id]       = savingsAccountB
                it[userId]   = userBId
                it[name]     = "Ahorros B"
                it[type]     = "SAVINGS"
                it[currency] = "COP"
            }
        }
        seedEvent(
            id = "evt-a-fp", userId = userAId, accountId = savingsAccountId,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )
        seedEvent(
            id = "evt-b-fp", userId = userBId, accountId = savingsAccountB,
            type = "EXPENSE", description = "Pago tarjeta de crédito", category = "Otros",
        )

        assertEquals(HttpStatusCode.NoContent, notCardPayment("evt-a-fp", userAId).status)

        val arrA = Json.parseToJsonElement(candidatesFor(userAId).bodyAsText()).jsonArray
        assertTrue(arrA.isEmpty(), "el de A quedó descartado")

        val arrB = Json.parseToJsonElement(candidatesFor(userBId).bodyAsText()).jsonArray
        assertEquals(1, arrB.size, "el descarte de A no debe afectar el candidato de B")
        assertEquals("evt-b-fp", arrB[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    // ── POST /api/events — F12: lo anotado a mano nace confirmado ──────────────

    /**
     * El caso central de F12: un cliente que omite `reconciliationStatus` (como QuickAdd antes
     * del fix) hereda el default UNCONFIRMED del modelo — pero al venir con `source` MANUAL (el
     * otro default), el server lo corrige a RECONCILED. Sin esto, todo lo anotado a mano caía en
     * el filtro "Por confirmar" y desaparecía de "Gastos", que excluye lo pendiente.
     */
    @Test
    fun `evento manual sin reconciliationStatus explicito nace RECONCILED`() = testApplication {
        wireApp()
        val res = postEvent(
            userAId,
            """{"id":"","accountId":"$savingsAccountId","type":"EXPENSE","amount":25000,
                "category":"Comida","description":"Almuerzo","timestamp":0}""",
        )
        assertEquals(HttpStatusCode.Created, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("RECONCILED", body["reconciliationStatus"]!!.jsonPrimitive.content)

        val row = transaction { Events.selectAll().where { Events.accountId eq savingsAccountId }.single() }
        assertEquals("RECONCILED", row[Events.reconciliationStatus])
    }

    @Test
    fun `evento manual con UNCONFIRMED explicito tambien se corrige a RECONCILED`() = testApplication {
        wireApp()
        val res = postEvent(
            userAId,
            """{"id":"","accountId":"$savingsAccountId","type":"EXPENSE","amount":25000,
                "category":"Comida","description":"Almuerzo","source":"MANUAL",
                "reconciliationStatus":"UNCONFIRMED","timestamp":0}""",
        )
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("RECONCILED", body["reconciliationStatus"]!!.jsonPrimitive.content)
    }

    /**
     * El contraste que prueba que la corrección es específica de MANUAL: un SMS que todavía no
     * se revisó SÍ debe quedar "por confirmar" — esa es la razón de ser del estado. Corregirlo acá
     * también rompería el flujo de revisión de SMS/OCR/extracto.
     */
    @Test
    fun `evento de SMS con UNCONFIRMED se mantiene UNCONFIRMED`() = testApplication {
        wireApp()
        val res = postEvent(
            userAId,
            """{"id":"","accountId":"$savingsAccountId","type":"EXPENSE","amount":25000,
                "category":"Comida","description":"Almuerzo","source":"SMS",
                "reconciliationStatus":"UNCONFIRMED","timestamp":0}""",
        )
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        // El Json del server tiene encodeDefaults=false: UNCONFIRMED es el default de
        // reconciliationStatus, así que cuando el valor coincide con el default el campo no
        // viaja en el wire — ausencia de la clave es justamente "se quedó en su default".
        assertEquals("UNCONFIRMED", body["reconciliationStatus"]?.jsonPrimitive?.content ?: "UNCONFIRMED")

        // Confirmación real, sin depender del detalle de encodeDefaults: la fila en la DB.
        val row = transaction {
            Events.selectAll().where { Events.accountId eq savingsAccountId }.single()
        }
        assertEquals("UNCONFIRMED", row[Events.reconciliationStatus])
    }

    // ── Zona horaria ──────────────────────────────────────────────────────────

    /**
     * El server corre en UTC pero el dueño vive en Bogotá: un movimiento a las 11:30 pm del 31
     * de agosto (= 04:30Z del 1 de septiembre) tiene que aparecer en el día "2026-08-31" de
     * /by-day, no en el "2026-09-01". Es la misma fecha civil con la que el cliente arma
     * "este mes" (AppTimeZone en :core), así Inicio y Presupuestos muestran el mismo número.
     */
    @Test
    fun `by-day fecha los eventos con el día civil de Bogota, no el de UTC`() = testApplication {
        wireApp()
        val lateAugustBogota = java.time.Instant.parse("2026-09-01T04:30:00Z").toEpochMilli()
        seedEvent("ev-late", userAId, savingsAccountId, "EXPENSE", "Cena", "Comida", timestamp = lateAugustBogota)

        val res = client.get("/api/events/by-day") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val days = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(listOf("2026-08-31"), days.map { it.jsonObject["date"]!!.jsonPrimitive.content })
    }

    // ── PUT /api/events/{id}/timestamp — corregir la fecha ────────────────────

    private suspend fun ApplicationTestBuilder.setTimestamp(id: String, userId: String, ts: Long) =
        client.put("/api/events/$id/timestamp") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"timestamp":$ts}""")
        }

    /** El mediodía de Bogotá de hace [dias] días — lo mismo que arma `epochAlMediodia` en el cliente. */
    private fun mediodiaHace(dias: Long): Long =
        AppClock.today().minusDays(dias).atTime(12, 0).atZone(AppClock.zone).toInstant().toEpochMilli()

    private fun timestampDe(id: String): Long =
        transaction { Events.selectAll().where { Events.id eq id }.first()[Events.timestamp] }

    @Test
    fun `mover un gasto a ayer cambia su fecha y lo saca del dia de hoy`() = testApplication {
        wireApp()
        seedEvent("ev-hoy", userAId, savingsAccountId, "EXPENSE", "Tostao", "Comida")

        val ayer = mediodiaHace(1)
        val res = setTimestamp("ev-hoy", userAId, ayer)
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(ayer, Json.parseToJsonElement(res.bodyAsText()).jsonObject["timestamp"]!!.jsonPrimitive.long)
        assertEquals(ayer, timestampDe("ev-hoy"))

        // Y se ve donde el dueño lo va a buscar: /by-day lo agrupa bajo el día de AYER.
        val dias = client.get("/api/events/by-day") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        val fechas = Json.parseToJsonElement(dias.bodyAsText()).jsonArray
            .map { it.jsonObject["date"]!!.jsonPrimitive.content }
        assertEquals(listOf(AppClock.today().minusDays(1).toString()), fechas)
    }

    /**
     * La guarda del futuro. Un movimiento es plata que YA se movió: fecharlo mañana infla el
     * saldo y las cifras del mes con algo que no pasó — y es la misma regla que este server ya
     * aplica del otro lado al negarse a dar por ocurrido un vencimiento que no llegó.
     */
    @Test
    fun `una fecha futura se rechaza con 422 y no toca la fila`() = testApplication {
        wireApp()
        val original = mediodiaHace(3)
        seedEvent("ev-futuro", userAId, savingsAccountId, "EXPENSE", "Mercado", "Mercado", timestamp = original)

        val res = setTimestamp("ev-futuro", userAId, mediodiaHace(-1))
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(EVENT_DATE_IN_FUTURE, res.bodyAsText())
        assertEquals(original, timestampDe("ev-futuro"))
    }

    /** HOY sí entra: el corte es por día civil de Bogotá, no por instante (un reloj adelantado no traba nada). */
    @Test
    fun `hoy no cuenta como futuro`() = testApplication {
        wireApp()
        seedEvent("ev-hoy-ok", userAId, savingsAccountId, "EXPENSE", "Almuerzo", "Comida", timestamp = mediodiaHace(5))
        assertEquals(HttpStatusCode.OK, setTimestamp("ev-hoy-ok", userAId, mediodiaHace(0)).status)
    }

    @Test
    fun `un movimiento de otro usuario o anulado responde 404`() = testApplication {
        wireApp()
        seedEvent("ev-ajeno", userBId, savingsAccountId, "EXPENSE", "Ajeno", "Comida")
        seedEvent("ev-anulado", userAId, savingsAccountId, "EXPENSE", "Anulado", "Comida")
        voidEvent("ev-anulado", userAId)

        assertEquals(HttpStatusCode.NotFound, setTimestamp("ev-ajeno", userAId, mediodiaHace(1)).status)
        assertEquals(HttpStatusCode.NotFound, setTimestamp("ev-anulado", userAId, mediodiaHace(1)).status)
    }

    /**
     * **Las dos patas de un traspaso se mueven juntas.** Mover solo una dejaría la plata saliendo
     * un día y entrando otro, y en Movimientos el traspaso se partiría en dos renglones sueltos
     * (`collapseTransfers` agrupa dentro de un mismo día).
     */
    @Test
    fun `mover una pata de traspaso mueve tambien a su hermana`() = testApplication {
        wireApp()
        val original = System.currentTimeMillis()
        transaction {
            listOf("ev-tr-out" to "EXPENSE", "ev-tr-in" to "INCOME").forEach { (evId, tipo) ->
                Events.insert {
                    it[id]                   = evId
                    it[userId]               = userAId
                    it[accountId]            = savingsAccountId
                    it[type]                 = tipo
                    it[amount]               = 500_000L
                    it[currency]             = "COP"
                    it[category]             = "Traspaso"
                    it[description]          = "Traspaso"
                    it[timestamp]            = original
                    it[eventSource]          = "MANUAL"
                    it[reconciliationStatus] = "RECONCILED"
                    it[transferId]           = "tr-1"
                }
            }
        }

        val ayer = mediodiaHace(1)
        assertEquals(HttpStatusCode.OK, setTimestamp("ev-tr-out", userAId, ayer).status)
        assertEquals(ayer, timestampDe("ev-tr-out"))
        assertEquals(ayer, timestampDe("ev-tr-in"))
    }

    // ── El sello de recurrente, cuando la fecha se corrige ───────────────────

    private fun seedRegla(id: String, dayOfMonth: Int, name: String = "Arriendo") {
        transaction {
            RecurringRules.insert {
                it[RecurringRules.id]         = id
                it[RecurringRules.userId]     = userAId
                it[RecurringRules.name]       = name
                it[RecurringRules.category]   = "Vivienda"
                it[RecurringRules.amount]     = 1_800_000L
                it[RecurringRules.dayOfMonth] = dayOfMonth
                it[RecurringRules.type]       = "EXPENSE"
            }
        }
    }

    private fun sellar(ruleId: String, period: String, eventId: String?) {
        transaction {
            RecurringOccurrences.insert {
                it[userId]      = userAId
                it[RecurringOccurrences.ruleId]  = ruleId
                it[RecurringOccurrences.period]  = period
                it[RecurringOccurrences.eventId] = eventId
                it[confirmedAt] = System.currentTimeMillis()
            }
        }
    }

    private fun sellosDe(ruleId: String): Long = transaction {
        RecurringOccurrences.selectAll()
            .where { (RecurringOccurrences.userId eq userAId) and (RecurringOccurrences.ruleId eq ruleId) }
            .count()
    }

    /** El mediodía de Bogotá de una fecha concreta. */
    private fun mediodiaDe(fecha: java.time.LocalDate): Long =
        fecha.atTime(12, 0).atZone(AppClock.zone).toInstant().toEpochMilli()

    /**
     * **El mes de referencia de estos tests es siempre pasado.**
     *
     * Dos meses atrás, contados desde `AppClock.today()`. Fechas fijas («2026-08-05») convertirían
     * estos tests en bombas de tiempo: el mismo caso que hoy pasa se vuelve una fecha futura el
     * mes que viene y el endpoint responde 422 antes de llegar a mirar el sello. Relativo al reloj
     * no puede pasar.
     */
    private fun mesDePrueba(): java.time.YearMonth = java.time.YearMonth.from(AppClock.today()).minusMonths(2)

    /**
     * **Corregir la fecha adentro de la ventana no suelta nada.**
     *
     * Vencimiento el 5 → la ventana es `[1 .. 15]` del mismo mes: diez días para adelante, y hacia
     * atrás el piso en el primer día del mes que
     * [com.jvillada.movi.server.reminders.OCCURRENCE_WINDOW_DAYS] documenta. Este test fija los dos
     * bordes: sin él, un fix más simple («si cambia de mes, soltá») pasaría igual y le volvería a
     * preguntar al dueño por un mes que ya respondió cada vez que corrige un día.
     */
    @Test
    fun `mover la fecha adentro de la ventana NO suelta el sello`() = testApplication {
        wireApp()
        val mes = mesDePrueba()
        seedRegla("rule-arriendo", dayOfMonth = 5)
        seedEvent("ev-arriendo", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda",
            timestamp = mediodiaDe(mes.atDay(5)))
        sellar("rule-arriendo", mes.toString(), "ev-arriendo")

        // Borde de arriba (vencimiento + 10) y borde de abajo (el piso del mes).
        assertEquals(HttpStatusCode.OK, setTimestamp("ev-arriendo", userAId, mediodiaDe(mes.atDay(15))).status)
        assertEquals(1L, sellosDe("rule-arriendo"))
        assertEquals(HttpStatusCode.OK, setTimestamp("ev-arriendo", userAId, mediodiaDe(mes.atDay(1))).status)
        assertEquals(1L, sellosDe("rule-arriendo"))
    }

    /**
     * Un pago tarde que cruza al mes siguiente sigue sosteniendo su sello: la ventana llega diez
     * días DESPUÉS del vencimiento. Es la otra mitad del test de arriba, y la que prueba que la
     * regla no es «el mes».
     */
    @Test
    fun `un pago tarde que cae en el mes siguiente sigue sosteniendo el sello`() = testApplication {
        wireApp()
        val mes = mesDePrueba()
        val vencimiento = mes.atEndOfMonth()
        seedRegla("rule-arriendo", dayOfMonth = 31)
        seedEvent("ev-arriendo", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda",
            timestamp = mediodiaDe(vencimiento))
        sellar("rule-arriendo", mes.toString(), "ev-arriendo")

        val res = setTimestamp("ev-arriendo", userAId, mediodiaDe(vencimiento.plusDays(3)))
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1L, sellosDe("rule-arriendo"))
    }

    /**
     * El caso que costaba plata: el dueño selló un mes con un movimiento, después se dio cuenta de
     * que ese movimiento era del mes anterior y le corrigió la fecha. Antes, el mes quedaba dado
     * por pagado con una evidencia que el emparejador nunca habría propuesto — el arriendo dejaba
     * de contar en su mes Y Movi no lo volvía a recordar. Ahora el sello se suelta.
     */
    @Test
    fun `mover la fecha fuera de la ventana suelta el sello y el mes vuelve a quedar pendiente`() = testApplication {
        wireApp()
        val mes = mesDePrueba()
        seedRegla("rule-arriendo", dayOfMonth = 5)
        seedEvent("ev-arriendo", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda",
            timestamp = mediodiaDe(mes.atDay(5)))
        sellar("rule-arriendo", mes.toString(), "ev-arriendo")

        // El 15 del mes ANTERIOR: antes del piso. El emparejador nunca lo habría propuesto para
        // este periodo, así que no puede seguir sosteniendo su sello.
        val res = setTimestamp("ev-arriendo", userAId, mediodiaDe(mes.minusMonths(1).atDay(15)))
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0L, sellosDe("rule-arriendo"))
    }

    /**
     * Y el movimiento queda **libre**: antes seguía quemado por la fila vieja, así que sellar el
     * periodo que sí le correspondía daba 409 «ya está marcado como la ocurrencia de otro
     * periodo». Un movimiento que dejó de ser evidencia de un mes tiene que poder serlo del otro.
     */
    @Test
    fun `el movimiento liberado puede sellar el periodo que si le corresponde`() = testApplication {
        wireApp()
        val mes = mesDePrueba()
        val anterior = mes.minusMonths(1)
        seedRegla("rule-arriendo", dayOfMonth = 5)
        seedEvent("ev-arriendo", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda",
            timestamp = mediodiaDe(mes.atDay(5)))
        sellar("rule-arriendo", mes.toString(), "ev-arriendo")
        setTimestamp("ev-arriendo", userAId, mediodiaDe(anterior.atDay(5)))

        val res = client.post("/api/recurring-rules/rule-arriendo/occurrence") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"period":"$anterior","eventId":"ev-arriendo"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }

    /** Un sello sin movimiento («ya lo pagué») no depende de ninguna fecha y no se toca. */
    @Test
    fun `un sello sin movimiento no se ve afectado`() = testApplication {
        wireApp()
        seedRegla("rule-arriendo", dayOfMonth = 5)
        seedRegla("rule-luz", dayOfMonth = 5, name = "Luz")
        val mes = mesDePrueba()
        sellar("rule-luz", mes.toString(), null)
        seedEvent("ev-otro", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda",
            timestamp = mediodiaDe(mes.atDay(5)))

        setTimestamp("ev-otro", userAId, mediodiaDe(mes.minusMonths(3).atDay(1)))
        assertEquals(1L, sellosDe("rule-luz"))
    }

    /**
     * El GET que deja **avisar antes**: nombre del recurrente, periodo, y la ventana de fechas
     * que sostiene el sello. La ventana se manda calculada del server a propósito — es lógica del
     * emparejador y no puede tener una segunda versión en el cliente.
     */
    @Test
    fun `el GET de la marca devuelve el recurrente y la ventana que la sostiene`() = testApplication {
        wireApp()
        seedRegla("rule-arriendo", dayOfMonth = 5)
        val mes = mesDePrueba()
        seedEvent("ev-arriendo", userAId, savingsAccountId, "EXPENSE", "Arriendo", "Vivienda")
        sellar("rule-arriendo", mes.toString(), "ev-arriendo")

        val res = client.get("/api/events/ev-arriendo/occurrence") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Arriendo", body["ruleName"]!!.jsonPrimitive.content)
        assertEquals(mes.toString(), body["period"]!!.jsonPrimitive.content)
        // Vencimiento el 5, ventana de 10 días, con piso en el primer día del mes.
        assertEquals(mes.atDay(1).toString(), body["validFrom"]!!.jsonPrimitive.content)
        assertEquals(mes.atDay(15).toString(), body["validTo"]!!.jsonPrimitive.content)
    }

    /** «No hay marca» es una respuesta normal, no un error: 204 y no 404. */
    @Test
    fun `el GET de la marca responde 204 cuando el movimiento no esta sellado`() = testApplication {
        wireApp()
        seedEvent("ev-suelto", userAId, savingsAccountId, "EXPENSE", "Tostao", "Comida")
        val res = client.get("/api/events/ev-suelto/occurrence") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    // ── Guarda de cordura de año en el POST ──────────────────────────────────

    /**
     * No es la guarda de futuro (por esta ruta entran los SMS y los extractos con su propia
     * fecha): es el piso que impide que un epoch roto esconda un movimiento en 1970, donde nadie
     * lo va a ver nunca para arreglarlo.
     */
    @Test
    fun `POST rechaza un epoch de 1970 con 400 y no inserta nada`() = testApplication {
        wireApp()
        val res = client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"ev-1970","accountId":"$savingsAccountId","type":"EXPENSE","amount":1000,
                   "category":"Comida","description":"roto","timestamp":1000,"source":"SMS"}"""
                    .trimIndent().replace("\n", ""),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        val existe = transaction { Events.selectAll().where { Events.id eq "ev-1970" }.count() }
        assertEquals(0L, existe)
    }

    /** `timestamp` ausente/0 sigue cayendo en «ahora», que es el default histórico. */
    @Test
    fun `POST sin timestamp sigue guardando con la hora actual`() = testApplication {
        wireApp()
        val res = client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"ev-sin-fecha","accountId":"$savingsAccountId","type":"EXPENSE","amount":1000,
                   "category":"Comida","description":"ok","timestamp":0,"source":"MANUAL"}"""
                    .trimIndent().replace("\n", ""),
            )
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals(AppClock.today(), epochMillisToAppDate(timestampDe("ev-sin-fecha")))
    }
}
