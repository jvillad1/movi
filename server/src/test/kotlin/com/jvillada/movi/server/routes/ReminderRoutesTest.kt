package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTTP-level isolation tests for the recurring-rule CRUD + upcoming-payments endpoint.
 * Reuses the same H2 in-memory harness as IsolationTest.
 */
class ReminderRoutesTest {

    private val testSecret = "test-secret-for-reminder-tests-minimum-32-chars"
    private val issuer    = "movi"
    private val audience  = "movi-client"

    private val userAId    = "user-a-reminder"
    private val userBId    = "user-b-reminder"
    private val userAEmail = "a@reminder.test"
    private val userBEmail = "b@reminder.test"
    private val ruleOwnedByA = "rule-a-reminder"
    private val accountOwnedByA = "acc-a-reminder"
    private val accountOwnedByB = "acc-b-reminder"

    /** La fecha civil de la app, la misma que usan los endpoints (Bogotá, no la del sistema). */
    private val hoy = AppClock.today()
    private val periodoDeHoy =
        hoy.year.toString().padStart(4, '0') + "-" + hoy.monthValue.toString().padStart(2, '0')

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:reminder_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, RecurringOccurrences, SmsMessages, Credits, Cards,
            )
            SchemaUtils.drop(Cards, Credits, RecurringOccurrences, RecurringRules, Users, Accounts)
            SchemaUtils.create(Users, Accounts, RecurringRules, RecurringOccurrences, Credits, Cards)

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
            // Ola 9 · D: una cuenta de A y otra de B, para probar que la cuenta de una regla
            // solo se guarda si es de quien la crea.
            Accounts.insert {
                it[id]     = accountOwnedByA
                it[userId] = userAId
                it[name]   = "Bancolombia A"
                it[type]   = "SAVINGS"
            }
            Accounts.insert {
                it[id]     = accountOwnedByB
                it[userId] = userBId
                it[name]   = "Nequi B"
                it[type]   = "SAVINGS"
            }
            RecurringRules.insert {
                it[id]         = ruleOwnedByA
                it[userId]     = userAId
                it[name]       = "Rent A"
                it[category]   = "Vivienda"
                it[amount]     = 1_500_000L
                it[dayOfMonth] = 5
                it[type]       = "EXPENSE"
            }
        }
    }

    private fun mintToken(userId: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
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
                    if (credential.payload.getClaim("userId").asString() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    /** User A POSTs a rule; User B PUT on A's id → 404 */
    @Test
    fun `user B cannot update user A's recurring rule`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User A posts a new rule
        val tokenA = mintToken(userAId, userAEmail)
        val newRule = RecurringRule("ignored", "Netflix", "Suscripción", 50_000, 15, TransactionType.EXPENSE)
        val createResp = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(newRule)
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val created = createResp.body<RecurringRule>()

        // User B tries to update that rule
        val tokenB = mintToken(userBId, userBEmail)
        val updateResp = client.put("/api/recurring-rules/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody(newRule.copy(name = "Hacked"))
        }
        assertEquals(HttpStatusCode.NotFound, updateResp.status,
            "User B must get 404 trying to update user A's rule")
    }

    /** User B DELETE on A's rule id → 404 */
    @Test
    fun `user B cannot delete user A's recurring rule`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        val tokenB = mintToken(userBId, userBEmail)
        val resp = client.delete("/api/recurring-rules/$ruleOwnedByA") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status,
            "User B must get 404 trying to delete user A's rule")
    }

    /** GET /api/payments/upcoming as B excludes A's rule */
    @Test
    fun `upcoming payments as user B excludes user A's rules`() = testApplication {
        application { testModule() }
        val tokenB = mintToken(userBId, userBEmail)
        val resp = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.isEmpty(), "User B's upcoming payments must be empty; got: ${resp.bodyAsText()}")
    }

    /** User A can see its own upcoming payments */
    @Test
    fun `upcoming payments as user A includes own rules`() = testApplication {
        application { testModule() }
        val tokenA = mintToken(userAId, userAEmail)
        val resp = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertEquals(1, arr.size, "User A should see exactly 1 upcoming payment; got: ${resp.bodyAsText()}")
    }

    /** A credit's installment enters /api/payments/upcoming as a virtual recurring rule. */
    @Test
    fun `upcoming payments include credit installments`() = testApplication {
        application { testModule() }
        transaction {
            Accounts.insert {
                it[id] = "acc-loan-up"; it[userId] = userAId
                it[name] = "Crédito Vehículo"; it[type] = "LOAN"
                it[balance] = 0; it[currency] = "COP"
            }
            Credits.insert {
                it[accountId] = "acc-loan-up"; it[userId] = userAId
                it[bank] = "Santander"; it[principal] = 160_000_000
                it[rateEa] = 21.56; it[termMonths] = 72
                it[installment] = 4_550_030; it[dayOfMonth] = 15
                it[startDate] = "2025-11-25"
            }
        }
        val tokenA = mintToken(userAId, userAEmail)
        val res = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val body = res.bodyAsText()
        assertTrue(body.contains("credit_acc-loan-up"), "expected virtual credit rule in: $body")
        assertTrue(body.contains("Cuota Crédito Vehículo"))
    }

    // ── remindMe: la casilla «Recordarme unos días antes» ─────────────────────

    /** Crear con la casilla desmarcada y releer: el valor tiene que sobrevivir el viaje. */
    @Test
    fun `una regla creada sin recordatorio se relee sin recordatorio`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val created = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule("ignored", "Gimnasio", "Salud", 120_000, 8, TransactionType.EXPENSE, remindMe = false),
            )
        }.body<RecurringRule>()
        assertFalse(created.remindMe, "la respuesta del POST ya debe reflejar la casilla desmarcada")

        val reread = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>().single { it.id == created.id }
        assertFalse(reread.remindMe, "el valor guardado tiene que sobrevivir la relectura")
    }

    /** Sin especificar nada, la regla nace avisando — el comportamiento de siempre. */
    @Test
    fun `una regla creada sin decir nada nace con el recordatorio prendido`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val created = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(RecurringRule("ignored", "Internet", "Servicios", 90_000, 20, TransactionType.EXPENSE))
        }.body<RecurringRule>()
        assertTrue(created.remindMe)

        val reread = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>().single { it.id == created.id }
        assertTrue(reread.remindMe)
    }

    /** Editar otra cosa no puede prender ni apagar el recordatorio por accidente. */
    @Test
    fun `editar una regla conserva el valor del recordatorio`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val created = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule("ignored", "Netflix", "Suscripción", 50_000, 15, TransactionType.EXPENSE, remindMe = false),
            )
        }.body<RecurringRule>()

        // La hoja de edición manda el valor que tenía cargado: solo cambia el monto.
        val updated = client.put("/api/recurring-rules/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(created.copy(amount = 60_000))
        }.body<RecurringRule>()
        assertFalse(updated.remindMe)

        val reread = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>().single { it.id == created.id }
        assertFalse(reread.remindMe, "editar el monto no puede reactivar el recordatorio")
        assertEquals(60_000L, reread.amount)
    }

    /** Y volver a marcarla lo vuelve a prender. */
    @Test
    fun `volver a marcar la casilla reactiva el recordatorio`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val created = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule("ignored", "Gym", "Salud", 100_000, 3, TransactionType.EXPENSE, remindMe = false),
            )
        }.body<RecurringRule>()

        client.put("/api/recurring-rules/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(created.copy(remindMe = true))
        }
        val reread = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>().single { it.id == created.id }
        assertTrue(reread.remindMe)
    }

    // ── Ola 9 · D: la cuenta del recurrente ──────────────────────────────────────────

    /** La cuenta viaja, se guarda y vuelve en el GET. */
    @Test
    fun `una regla guarda la cuenta cuando es del mismo usuario`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val creada = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", "Arriendo", "Vivienda", 1_800_000, 5,
                    TransactionType.EXPENSE, accountId = accountOwnedByA,
                ),
            )
        }.body<RecurringRule>()
        assertEquals(accountOwnedByA, creada.accountId)

        val listadas = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>()
        assertEquals(accountOwnedByA, listadas.first { it.id == creada.id }.accountId)
    }

    /**
     * Una cuenta que no es suya no se guarda — y la respuesta lo dice, en vez de devolver el id
     * que se pidió y hacerle creer al cliente que la regla tiene una cuenta que no tiene.
     */
    @Test
    fun `una cuenta ajena no se guarda, y la regla se crea igual sin cuenta`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val resp = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", "Arriendo", "Vivienda", 1_800_000, 5,
                    TransactionType.EXPENSE, accountId = accountOwnedByB,
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        assertEquals(null, resp.body<RecurringRule>().accountId)
    }

    /**
     * Ola 9 · D — **el APK que el dueño ya tiene instalado no puede borrarle la cuenta.**
     *
     * Ese cliente no conoce el campo, así que su PUT llega sin él (`null`). Si eso significara
     * «quitá la cuenta», corregir el monto desde el teléfono le borraría en silencio la cuenta
     * que puso desde la web. `null` = no lo toques.
     */
    @Test
    fun `un PUT sin accountId conserva la cuenta que ya tenia la regla`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val creada = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", "Arriendo", "Vivienda", 1_800_000, 5,
                    TransactionType.EXPENSE, accountId = accountOwnedByA,
                ),
            )
        }.body<RecurringRule>()

        // Cuerpo tal cual lo manda un cliente viejo: sin el campo `accountId` en el JSON.
        val cuerpoViejo = """
            {"id":"${creada.id}","name":"Arriendo","category":"Vivienda","amount":1900000,
             "dayOfMonth":5,"type":"EXPENSE","remindMe":true}
        """.trimIndent().replace("\n", "")
        val respuesta = client.put("/api/recurring-rules/${creada.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            setBody(TextContent(cuerpoViejo, ContentType.Application.Json))
        }
        assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
        assertEquals(accountOwnedByA, respuesta.body<RecurringRule>().accountId)

        val guardada = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>().first { it.id == creada.id }
        assertEquals(accountOwnedByA, guardada.accountId, "la cuenta sobrevive al cliente viejo")
        assertEquals(1_900_000L, guardada.amount, "y el cambio que sí pidió se guardó")
    }

    /** Y «Sin cuenta» (cadena vacía) sí la quita: es una elección explícita del dueño. */
    @Test
    fun `un PUT con accountId vacio quita la cuenta`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val creada = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", "Arriendo", "Vivienda", 1_800_000, 5,
                    TransactionType.EXPENSE, accountId = accountOwnedByA,
                ),
            )
        }.body<RecurringRule>()

        val respuesta = client.put("/api/recurring-rules/${creada.id}") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(creada.copy(accountId = ""))
        }
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals(null, respuesta.body<RecurringRule>().accountId)
    }

    /** Una regla vieja (sin cuenta) sigue leyéndose y editándose sin cuenta. */
    @Test
    fun `la cuenta es opcional - una regla sin cuenta se lee y se edita igual`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)

        val listadas = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<RecurringRule>>()
        val vieja = listadas.first { it.id == ruleOwnedByA }
        assertEquals(null, vieja.accountId)

        val editada = client.put("/api/recurring-rules/$ruleOwnedByA") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(vieja.copy(amount = 1_900_000L))
        }
        assertEquals(HttpStatusCode.OK, editada.status)
        assertEquals(null, editada.body<RecurringRule>().accountId)
    }

    // ── «Esto ya ocurrió» ────────────────────────────────────────────────────────────
    //
    // El caso del dueño, de punta a punta: su recurrente de ingreso aparecía vencido mientras el
    // movimiento ya estaba anotado, por un monto PARECIDO pero no igual (una retención).

    /**
     * Una regla cuyo día del mes es HOY, para que el periodo en juego sea siempre el mes en curso
     * corra el test el día que corra. Sin esto, un test con día fijo pasaría o fallaría según la
     * fecha — que es la clase de test que se termina borrando en vez de arreglando.
     */
    private suspend fun reglaDeHoy(
        client: io.ktor.client.HttpClient,
        token: String,
        nombre: String,
    ): RecurringRule =
        client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", nombre, "Salario", 5_000_000, hoy.dayOfMonth,
                    TransactionType.INCOME, accountId = accountOwnedByA,
                ),
            )
        }.body()

    private fun sembrarMovimiento(
        id: String,
        amount: Long = 4_780_000,
        category: String = "Salario",
        description: String = "Salario",
        accountId: String = accountOwnedByA,
        owner: String = userAId,
        transferId: String? = null,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[Events.userId] = owner
            it[Events.accountId] = accountId
            it[Events.type] = "INCOME"
            it[Events.amount] = amount
            it[Events.category] = category
            it[Events.description] = description
            it[Events.timestamp] = appDateToEpochMillis(hoy)
            it[Events.transferId] = transferId
        }
    }

    @Test
    fun `propone el movimiento parecido, confirmarlo cierra el periodo y deshacerlo lo reabre`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario ocurrencia")
        sembrarMovimiento("ev-ocurrencia-1")

        // 1. La app PROPONE — no marca nada sola. Y propone un monto que NO es el anotado.
        val propuesto = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<OccurrenceState>>().single { it.ruleId == regla.id }
        assertFalse(propuesto.occurred)
        assertEquals(periodoDeHoy, propuesto.period)
        assertTrue(
            propuesto.candidates.any { it.id == "ev-ocurrencia-1" },
            "el movimiento del mismo día por un monto parecido tiene que proponerse",
        )

        // 2. El dueño confirma.
        val marcado = client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy, eventId = "ev-ocurrencia-1"))
        }
        assertEquals(HttpStatusCode.Created, marcado.status)

        // 3. Ya no se lee como vencido: el vencimiento vigente rodó al mes que viene.
        val vencimiento = client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<UpcomingPayment>>().single { it.rule.id == regla.id }
        assertTrue(vencimiento.daysUntil > 0, "un periodo cerrado no puede seguir vencido")
        assertFalse(vencimiento.dueDate.startsWith(periodoDeHoy))

        // 4. Y queda visible como ocurrido, con el movimiento que lo respalda.
        val cerrado = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<OccurrenceState>>().single { it.ruleId == regla.id }
        assertTrue(cerrado.occurred)
        assertEquals("ev-ocurrencia-1", cerrado.eventId)
        assertTrue(cerrado.candidates.isEmpty(), "cerrado no se vuelve a ofrecer")

        // 5. Deshacer lo devuelve a pendiente.
        val deshecho = client.delete("/api/recurring-rules/${regla.id}/occurrence/$periodoDeHoy") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.NoContent, deshecho.status)
        val reabierto = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<OccurrenceState>>().single { it.ruleId == regla.id }
        assertFalse(reabierto.occurred)
    }

    /** El «ya me llegó» sin movimiento que emparejar: cierra igual, y se nota que no tiene respaldo. */
    @Test
    fun `se puede cerrar el periodo sin ningun movimiento`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario sin movimiento")

        val resp = client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy, eventId = null))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val estado = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<OccurrenceState>>().single { it.ruleId == regla.id }
        assertTrue(estado.occurred)
        assertEquals(null, estado.eventId)
    }

    /** Si el movimiento emparejado se anula, la ocurrencia deja de valer y vuelve a estar pendiente. */
    @Test
    fun `anular el movimiento reabre el periodo`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario anulable")
        sembrarMovimiento("ev-ocurrencia-anulada")

        client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy, eventId = "ev-ocurrencia-anulada"))
        }
        transaction {
            VoidEvents.insert {
                it[id] = "void-ocurrencia-anulada"
                it[userId] = userAId
                it[originalEventId] = "ev-ocurrencia-anulada"
                it[timestamp] = System.currentTimeMillis()
            }
        }
        val estado = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }.body<List<OccurrenceState>>().single { it.ruleId == regla.id }
        assertFalse(estado.occurred, "un movimiento anulado no puede seguir cerrando el mes")
    }

    @Test
    fun `un usuario no puede sellar el recurrente de otro`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenB = mintToken(userBId, userBEmail)
        val resp = client.post("/api/recurring-rules/$ruleOwnedByA/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `no se puede cerrar un periodo que todavia no llego`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario futuro")
        val futuro = hoy.plusMonths(6)
        val resp = client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(
                MarkOccurrenceRequest(
                    period = futuro.year.toString().padStart(4, '0') + "-" +
                        futuro.monthValue.toString().padStart(2, '0'),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `un movimiento de otro usuario no puede ser la ocurrencia`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario ajeno")
        sembrarMovimiento("ev-de-b", accountId = accountOwnedByB, owner = userBId)
        val resp = client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy, eventId = "ev-de-b"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `una pata de traspaso no puede ser la ocurrencia`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val tokenA = mintToken(userAId, userAEmail)
        val regla = reglaDeHoy(client, tokenA, "Salario traspaso")
        sembrarMovimiento("ev-pata-traspaso", transferId = "tr-1", category = "Traspaso")
        val resp = client.post("/api/recurring-rules/${regla.id}/occurrence") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = periodoDeHoy, eventId = "ev-pata-traspaso"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}
