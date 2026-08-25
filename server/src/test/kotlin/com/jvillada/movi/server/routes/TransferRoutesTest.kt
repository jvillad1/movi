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
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_SUFFIX
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY_RESERVED
import com.jvillada.movi.shared.model.TRANSFER_ID_ALREADY_USED
import com.jvillada.movi.shared.model.TRANSFER_RECATEGORIZE_BLOCKED
import io.ktor.client.request.delete
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
import kotlin.test.assertNull
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

    /**
     * T1: `FinanceSummary.eventCount` es "cuántos movimientos anotó el dueño", y un traspaso es
     * UNO. Contaba dos —una fila por pata— mientras Movimientos, una pantalla más allá, mostraba
     * el mismo traspaso como un solo renglón. Ver [com.jvillada.movi.shared.model.movementCount].
     */
    @Test
    fun `un traspaso suma un movimiento al conteo, no dos`() = testApplication {
        wireApp()
        // La apertura de Ahorros ya está sembrada y no cuenta (F54): se arranca en cero.
        assertEquals(0, financeSummaryEventCount(), "la apertura de cuenta no es un movimiento")

        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }

        assertEquals(2L, eventCount() - 1L, "en la base siguen siendo dos filas")
        assertEquals(1, financeSummaryEventCount(), "pero para el dueño pasó una sola cosa")
    }

    private suspend fun ApplicationTestBuilder.financeSummaryEventCount(): Int =
        client.get("/api/finance-summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
            .let { Json.parseToJsonElement(it).jsonObject }["eventCount"]
            ?.jsonPrimitive?.long?.toInt() ?: 0

    // ── Borrar una de las dos cuentas ─────────────────────────────────────────

    /**
     * T2: el dueño traspasa de Ahorros al CDT y después cierra el CDT y lo borra. La pata del CDT
     * se va con la cuenta; la de Ahorros **sobrevive** —la plata salió de verdad y el saldo lo
     * dice— pero no puede quedarse como media pareja: sin `transferId`, sin la categoría
     * reservada y diciendo lo que pasó. Ver `desenlazarPatasHermanas` en `AccountRoutes.kt`.
     */
    @Test
    fun `borrar una cuenta suelta la pata hermana en vez de dejarla colgando`() = testApplication {
        wireApp()
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }

        val borrado = client.delete("/api/accounts/$cdtId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NoContent, borrado.status)

        val pata = transaction {
            Events.selectAll().where { Events.id eq "ev-from-1" }.single()
        }
        assertNull(pata[Events.transferId], "ya no es media pareja: nadie tiene que buscarle hermana")
        assertEquals(ORPHANED_LEG_CATEGORY, pata[Events.category])
        assertEquals("Traspaso a CDT$ORPHANED_LEG_SUFFIX", pata[Events.description])

        // El saldo de Ahorros NO se toca: los $250.000 salieron y no vuelven por borrar el CDT.
        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertEquals(750_000L, balanceOf(accounts, ahorrosId))

        // Y ahora sí es un gasto del mes: con el CDT fuera de Movi, esa plata salió del perímetro
        // que la app lleva.
        val summary = client.get("/api/dashboard/summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }
        assertEquals(250_000L, summary["monthSpent"]?.jsonPrimitive?.long ?: 0L)
    }

    @Test
    fun `la pata suelta cuenta como un movimiento y se puede recategorizar`() = testApplication {
        wireApp()
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }
        client.delete("/api/accounts/$cdtId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }

        assertEquals(1, financeSummaryEventCount(), "quedó un movimiento, no medio ni dos")

        // Lo que antes era imposible: la ruta rechazaba tocar cualquier pata de traspaso, así que
        // el dueño no tenía forma de sacar esa fila de «Traspaso».
        val recategorizada = client.put("/api/events/ev-from-1/category") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"category":"Ahorro"}""")
        }
        assertEquals(HttpStatusCode.OK, recategorizada.status)
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
     *
     * Y la respuesta es un 500, no un 409: el traspaso NO quedó registrado, así que decirle al
     * cliente «ya está» sería un «guardado» sobre la nada (el cliente trata la respuesta
     * idempotente como éxito y espeja las patas en su DB local).
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

        assertEquals(HttpStatusCode.InternalServerError, response.status)
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
        assertEquals(TRANSFER_RECATEGORIZE_BLOCKED, response.bodyAsText())

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
        assertEquals(TRANSFER_CATEGORY_RESERVED, response.bodyAsText())
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

    // ── C1: el reintento del dedo no puede duplicar el traspaso ───────────────

    /**
     * El escenario: el server commitea, la respuesta se pierde (timeout, cambio de red, la app
     * al fondo) y el dueño —que vio «revisa tu conexión»— vuelve a tocar Guardar. Con los MISMOS
     * ids, el segundo POST tiene que rebotar sin escribir nada: dos eventos en total, no cuatro,
     * y los saldos movidos UNA vez.
     *
     * Es la mitad server de la idempotencia; la otra mitad —que el cliente reintente con los
     * mismos ids en vez de fabricar unos nuevos— vive en `TransferForm`/`TransferIdsTest`.
     */
    @Test
    fun `el mismo traspaso mandado dos veces deja dos eventos, no cuatro`() = testApplication {
        wireApp()
        val body = transferBody()

        val primera = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val segunda = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, primera.status)
        // 200, no 409: el reintento devuelve las patas que ya estaban (idempotencia de verdad,
        // ver el test de más abajo). Lo que este test protege es lo de siempre — que no aparezca
        // un segundo par de patas ni se mueva el saldo dos veces.
        assertEquals(HttpStatusCode.OK, segunda.status)

        val patas = transaction {
            Events.selectAll().where { Events.transferId eq "tr-1" }.count()
        }
        assertEquals(2L, patas, "el reintento no puede agregar un segundo par de patas")

        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertEquals(750_000L, balanceOf(accounts, ahorrosId), "el saldo se movió una sola vez")
        assertEquals(250_000L, balanceOf(accounts, cdtId))
    }

    /**
     * T3, del lado del endpoint: el mismo `transferId` con OTROS ids de evento no es un reintento,
     * es un traspaso distinto pidiendo una identidad ocupada. Antes se dejaba pasar y ese id
     * terminaba con cuatro patas: nada podía volver a decir cuál compensaba a cuál.
     *
     * (La red del esquema es el índice único `(user_id, transfer_id, type)` que crea
     * `Migrations.createUniqueTransferLegIndex`; acá se prueba la puerta, no la red — este H2 se
     * levanta con `SchemaUtils.create` y no corre migraciones.)
     */
    @Test
    fun `reusar un transferId con otros ids de evento se rechaza`() = testApplication {
        wireApp()
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody())
        }

        val otro = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(fromEventId = "ev-otro-from", toEventId = "ev-otro-to", amount = 90_000L))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, otro.status)
        assertEquals(TRANSFER_ID_ALREADY_USED, otro.bodyAsText())

        val patas = transaction { Events.selectAll().where { Events.transferId eq "tr-1" }.count() }
        assertEquals(2L, patas, "el traspaso que ya estaba sigue teniendo dos patas, ni una más")

        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertEquals(750_000L, balanceOf(accounts, ahorrosId), "y ningún saldo se movió de más")
    }

    // ── m8: un id inválido se rechaza como tal, no como «ya registrado» ───────

    @Test
    fun `un id en blanco se rechaza con su motivo, no disfrazado de traspaso repetido`() = testApplication {
        wireApp()
        val before = eventCount()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(transferId = ""))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, eventCount())
    }

    @Test
    fun `un id absurdamente largo tampoco pasa por 409`() = testApplication {
        wireApp()
        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(fromEventId = "e".repeat(80)))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // ── m7: anular la pata hermana después de la cascada es 409, nunca 500 ────

    /**
     * La versión determinística de la carrera: dos dispositivos anulan las dos patas a la vez.
     * El segundo choca contra `uq_void_events_original_user` — antes eso salía como un 500 sin
     * atrapar, y el cliente perdedor lo reintentaba cada 30 segundos para siempre porque
     * `WalletRepositoryImpl.voidEvent` no miraba el status. Ahora es un 409 idempotente: la
     * anulación YA ocurrió, que es justo lo que el cliente quería.
     */
    @Test
    fun `anular la pata hermana despues de la cascada devuelve conflicto, no un error del server`() = testApplication {
        wireApp()
        crearTraspaso()
        client.post("/api/events/ev-from-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }

        val segunda = client.post("/api/events/ev-to-1/void") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }

        assertEquals(HttpStatusCode.Conflict, segunda.status)
        val anulaciones = transaction {
            VoidEvents.selectAll().where { VoidEvents.userId eq userAId }.count()
        }
        assertEquals(2L, anulaciones, "la cascada ya las había anulado a las dos; no se agrega una tercera")
    }

    // ── M2: importar un extracto no puede sacar una pata de la categoría ──────

    /**
     * El agujero que esto tapa: la reconciliación de extractos escribe la categoría con un
     * `Events.update` directo, sin pasar por la guarda de `PUT /api/events/{id}/category`. El
     * matcher empareja por monto + moneda + ±2 días **sin mirar la cuenta**, así que engancha la
     * pata de un traspaso; con «Confirmar todo» se aplica en bloque y sin que nadie lo lea.
     *
     * Si la pata sale de «Traspaso», `isCashFlow` vuelve a decir `true` y el egreso del mes se
     * infla con plata que nunca salió del bolsillo — con la hermana todavía excluida, así que ni
     * siquiera se compensa. La categoría de la pata no se toca.
     */
    @Test
    fun `reconciliar un extracto contra una pata de traspaso no le cambia la categoria`() = testApplication {
        wireApp()
        crearTraspaso()

        val response = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"statementId":"st-tr","accountId":"$ahorrosId","bankName":"Bancolombia","period":"2026-08",""" +
                    """"imports":[],"skipped":[],"reconciliations":[{"parsedId":"p-tr","existingEventId":"ev-from-1",""" +
                    """"parsed":{"id":"p-tr","date":"2026-08-23","merchant":"Éxito","amount":250000,""" +
                    """"currency":"COP","type":"EXPENSE","category":"Mercado","description":"COMPRA SUPERMERCADO",""" +
                    """"rawText":""},""" +
                    """"categorySource":"STATEMENT","descriptionSource":"STATEMENT","merchantSource":"STATEMENT",""" +
                    """"confirm":true}]}""",
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val categoria = transaction {
            Events.selectAll().where { Events.id eq "ev-from-1" }.single()[Events.category]
        }
        assertEquals(TRANSFER_CATEGORY, categoria, "la pata tiene que seguir fuera del flujo de caja")

        // Y el mes sigue sin enterarse, que es lo que en realidad se protege.
        val summary = client.get("/api/dashboard/summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }
        assertFalse(summary["spentByCategory"]?.jsonObject.orEmpty().containsKey("Mercado"))
    }

    /**
     * La cola de C1, del lado del server: el reintento con los mismos ids no devuelve un 409 seco
     * sino **las dos patas que ya están** — idempotencia de verdad. Así el cliente puede espejarlas
     * localmente sin inventar nada, y la app deja de decir «guardado» sobre una DB local vacía.
     */
    @Test
    fun `el reintento devuelve las patas que ya estaban, no un conflicto seco`() = testApplication {
        wireApp()
        val body = transferBody()
        client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val reintento = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, reintento.status)
        val json = Json.parseToJsonElement(reintento.bodyAsText()).jsonObject
        assertEquals("ev-from-1", json["from"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("ev-to-1", json["to"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("EXPENSE", json["from"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("INCOME", json["to"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val patas = transaction { Events.selectAll().where { Events.transferId eq "tr-1" }.count() }
        assertEquals(2L, patas)

        val accounts = client.get("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText()
        assertEquals(750_000L, balanceOf(accounts, ahorrosId), "el saldo se movió una sola vez")
    }

    /**
     * Y un choque que NO deja las dos patas no puede disfrazarse de éxito: antes cualquier
     * ExposedSQLException —un deadlock, una conexión caída, un serialization failure— salía como
     * «ya está registrado», y con el cliente tratando el 409 como éxito eso se convertía en un
     * «guardado» sobre la nada. Acá la pata de destino choca contra un id ajeno, así que el
     * traspaso NO quedó: tiene que ser un error de verdad.
     */
    @Test
    fun `un choque que no dejo las dos patas es un error, no un exito idempotente`() = testApplication {
        wireApp()
        transaction {
            Events.insert {
                it[id] = "ev-ocupado"
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

        val response = client.post("/api/transfers") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(transferBody(toEventId = "ev-ocupado"))
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val quedoAlgo = transaction {
            Events.selectAll().where { Events.transferId eq "tr-1" }.count()
        }
        assertEquals(0L, quedoAlgo, "no puede quedar media transferencia")
    }

    /**
     * M2, la otra mitad: **crear** un evento desde un extracto tampoco puede nacer en la categoría
     * reservada. `parsed.category` viene del cliente o del texto libre del parser LLM, así que un
     * «Traspaso» ahí dentro fabricaba un gasto real que quedaba fuera del mes sin ninguna pata
     * hermana que lo compensara.
     */
    @Test
    fun `un evento importado no puede nacer en la categoria reservada`() = testApplication {
        wireApp()
        val response = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"statementId":"st-cat","accountId":"$ahorrosId","bankName":"Bancolombia","period":"2026-08",""" +
                    """"reconciliations":[],"skipped":[],"imports":[{"id":"p-cat","date":"2026-08-20",""" +
                    """"merchant":"Éxito","amount":90000,"currency":"COP","type":"EXPENSE",""" +
                    """"category":"$TRANSFER_CATEGORY","description":"COMPRA","rawText":""}]}""",
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val categorias = transaction {
            Events.selectAll().where { Events.description eq "COMPRA" }.map { it[Events.category] }
        }
        assertEquals(1, categorias.size)
        assertTrue(categorias.none { it == TRANSFER_CATEGORY }, "nació en «${categorias.first()}», y eso está bien")

        // Y el gasto sigue contando en el mes, que es lo que se protege.
        val summary = client.get("/api/dashboard/summary") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }
        assertEquals(90_000L, summary["monthSpent"]?.jsonPrimitive?.long ?: 0L)
    }
}
