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
import io.ktor.client.request.delete
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import com.jvillada.movi.server.time.appDateToEpochMillis
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-level tests for GET/POST-detect/PUT/DELETE /api/subscriptions (Task 3 of
 * SP-subscription-tracker). Same harness pattern as CreditRoutesTest.kt: H2
 * in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and the
 * full serialization+jwt+routing plugin chain wired through a local `wireApp()`.
 */
class SubscriptionRoutesTest {

    private val testSecret = "test-secret-for-subscription-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-subs"
    private val userBId = "user-b-subs"
    private val userAEmail = "a@subs.test"
    private val userBEmail = "b@subs.test"

    private val accountAId = "acc-tc-a"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:subscription_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

            // ── User A ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userAId
                it[email]        = userAEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
            // ── User B ────────────────────────────────────────────────────────
            Users.insert {
                it[id]           = userBId
                it[email]        = userBEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }

            // ── CREDIT_CARD account for A ────────────────────────────────────
            Accounts.insert {
                it[id]       = accountAId
                it[userId]   = userAId
                it[name]     = "Tarjeta de Crédito"
                it[type]     = "CREDIT_CARD"
                it[currency] = "COP"
            }

            // ── Netflix: HIGH confidence → AUTO ─────────────────────────────
            insertExpense("evt-netflix-1", "PAYU*NETFLIX", 44_900, "2026-04-14")
            insertExpense("evt-netflix-2", "PAYU*NETFLIX", 44_900, "2026-05-14")
            insertExpense("evt-netflix-3", "PAYU*NETFLIX", 44_900, "2026-06-14")

            // ── YouTube: MEDIUM confidence → CANDIDATE ──────────────────────
            insertExpense("evt-youtube-1", "Google YOUTUBE Mmbrshp", 26_900, "2026-05-10")
            insertExpense("evt-youtube-2", "Google YOUTUBE Mmbrshp", 26_900, "2026-06-10")

            // ── EXITO: single occurrence → not detected ─────────────────────
            insertExpense("evt-exito-1", "EXITO COUNTRY", 312_400, "2026-06-02")
        }
    }

    private fun insertExpense(id: String, desc: String, amount: Long, tsIso: String) {
        Events.insert {
            it[Events.id]                   = id
            it[Events.userId]               = userAId
            it[Events.accountId]            = accountAId
            it[Events.type]                 = "EXPENSE"
            it[Events.amount]               = amount
            it[Events.currency]             = "COP"
            it[Events.category]             = "Otros"
            it[Events.description]          = desc
            it[Events.timestamp]            = appDateToEpochMillis(LocalDate.parse(tsIso))
            it[Events.eventSource]          = "STATEMENT"
            it[Events.reconciliationStatus] = "UNCONFIRMED"
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

    // ── Tests ──────────────────────────────────────────────────────────────────

    // F39: nada nace activo — ni siquiera la confianza HIGH de netflix (3 ocurrencias) lo
    // salta directo a AUTO como pasaba antes. Las dos entran CANDIDATE y el total mensual
    // arranca en 0 hasta que el dueño confirma alguna desde la pantalla Recurrentes.
    @Test
    fun `detect creates only CANDIDATE subscriptions, regardless of confidence`() = testApplication {
        wireApp()
        val res = client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val subs = body["subscriptions"]!!.jsonArray
        assertEquals(2, subs.size)
        val byKey = subs.associateBy { it.jsonObject["merchantKey"]!!.jsonPrimitive.content }
        assertEquals("CANDIDATE", byKey["netflix"]!!.jsonObject["status"]!!.jsonPrimitive.content, "HIGH confidence ya no salta a AUTO")
        assertEquals("CANDIDATE", byKey["youtube"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        // total mensual = solo AUTO+CONFIRMED → ninguna todavía
        assertEquals(0L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    // Blinda que una fila AUTO vieja (de antes de este cambio, sembrada directo en la DB
    // porque ya no hay forma de producirla vía detect) no se resetea a CANDIDATE en el
    // próximo scan — se sigue tratando como confirmada, igual que CONFIRMED.
    @Test
    fun `a legacy AUTO row survives re-detect without downgrading to CANDIDATE`() = testApplication {
        wireApp()
        transaction {
            Subscriptions.insert {
                it[id]          = "sub-legacy-netflix"
                it[userId]      = userAId
                it[merchantKey] = "netflix"
                it[displayName] = "Netflix"
                it[amount]      = 44_900
                it[currency]    = "COP"
                it[dayOfMonth]  = 14
                it[status]      = "AUTO"
                it[confidence]  = "HIGH"
                it[firstSeen]   = 0
                it[lastSeen]    = 0
                it[occurrences] = 3
                it[accountId]   = accountAId
            }
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val netflix = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        assertEquals("AUTO", netflix["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `re-detect is idempotent`() = testApplication {
        wireApp()
        repeat(2) { client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") } }
        val res = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(2, Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
    }

    // Reproduce el doble-tap en "Re-escanear": N detects concurrentes para el mismo
    // usuario pueden ver `existing` sin la fila (check-then-insert) y competir por el
    // mismo (userId, merchantKey, currency). Antes de este fix, el perdedor de la
    // carrera contra uq_subscriptions_user_merchant_currency devolvía un 500 sin
    // manejar. La carrera no siempre se dispara en una sola corrida (depende del
    // scheduler de corrutinas/hilos de H2), así que este test es un smoke guard: si
    // la carrera ocurre debe resolverse en 200s, y si no ocurre el resultado también
    // debe ser correcto — nunca debe quedar en rojo por flakiness.
    @Test
    fun `concurrent detects for the same user never 500 and converge to one row per subscription`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val responses = coroutineScope {
            (1..4).map {
                async {
                    client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer $token") }
                }
            }.awaitAll()
        }
        responses.forEach { assertEquals(HttpStatusCode.OK, it.status) }

        val after = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, after.status)
        assertEquals(2, Json.parseToJsonElement(after.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
    }

    @Test
    fun `dismissed stays dismissed after re-detect`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val netflix = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        val id = netflix["id"]!!.jsonPrimitive.content
        val dismissed = netflix.toMutableMap().apply { put("status", JsonPrimitive("DISMISSED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(dismissed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val after = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        assertEquals("DISMISSED", after["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `confirmed is not downgraded by re-detect and total includes it`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val youtube = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        val id = youtube["id"]!!.jsonPrimitive.content
        val confirmed = youtube.toMutableMap().apply { put("status", JsonPrimitive("CONFIRMED")) }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(confirmed)))
        }
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val body = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject
        val after = body["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "youtube" }.jsonObject
        assertEquals("CONFIRMED", after["status"]!!.jsonPrimitive.content)
        // F39: netflix (HIGH) ya no salta a AUTO — se queda CANDIDATE hasta que alguien la
        // confirme, así que el total acá es SOLO youtube (la única CONFIRMED).
        assertEquals(26_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    @Test
    fun `user B has no subscriptions and cannot edit A's`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val bList = client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        assertEquals(0, Json.parseToJsonElement(bList.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray.size)
        val aSub = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject
        val id = aSub["id"]!!.jsonPrimitive.content
        val put = client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(aSub.toMutableMap())))
        }
        assertEquals(HttpStatusCode.NotFound, put.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }.status)
    }

    // FamiriosParser.kt stampa sus agregados mensuales con description = "Famirios · $label ·
    // $mes $año" — un EXPENSE por categoría×mes, monto estable, cumple toda la heurística del
    // detector pero es una línea de presupuesto, no una suscripción real. El handler debe
    // filtrarlos por ese prefijo antes de detectar.
    @Test
    fun `Famirios budget aggregates are excluded from detection`() = testApplication {
        wireApp()
        transaction {
            insertExpense("evt-famirios-1", "Famirios · Arriendo · abril 2026", 1_800_000, "2026-04-30")
            insertExpense("evt-famirios-2", "Famirios · Arriendo · mayo 2026", 1_800_000, "2026-05-30")
            insertExpense("evt-famirios-3", "Famirios · Arriendo · junio 2026", 1_800_000, "2026-06-30")
        }
        val res = client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        val subs = Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray
        val keys = subs.map { it.jsonObject["merchantKey"]!!.jsonPrimitive.content }
        assertEquals(setOf("netflix", "youtube"), keys.toSet())
    }

    @Test
    fun `DELETE removes and second delete is 404`() = testApplication {
        wireApp()
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val id = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/subscriptions/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.status)
    }

    // ── F38: alta manual ─────────────────────────────────────────────────────

    @Test
    fun `POST creates a manual subscription that is CONFIRMED and counts in the monthly total`() = testApplication {
        wireApp()
        val post = client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"displayName":"Gimnasio","amount":90000,"currency":"COP","dayOfMonth":3}""")
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val created = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals("CONFIRMED", created["status"]!!.jsonPrimitive.content, "la creó el dueño — no hay nada que confirmar")
        assertEquals("manual_gimnasio", created["merchantKey"]!!.jsonPrimitive.content)

        val body = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject
        val subs = body["subscriptions"]!!.jsonArray
        val gym = subs.first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "manual_gimnasio" }.jsonObject
        assertEquals("Gimnasio", gym["displayName"]!!.jsonPrimitive.content)
        assertEquals(90_000L, body["monthlyTotalCop"]!!.jsonPrimitive.long, "activa desde el alta — cuenta en el total mensual")
    }

    @Test
    fun `a re-scan does not touch or duplicate a manual subscription`() = testApplication {
        wireApp()
        client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"displayName":"Gimnasio","amount":90000,"currency":"COP","dayOfMonth":3}""")
        }
        // Re-escanea (también detecta netflix/youtube desde los eventos sembrados en setUp).
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }

        val subs = Json.parseToJsonElement(
            client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText()
        ).jsonObject["subscriptions"]!!.jsonArray
        val gymRows = subs.filter { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "manual_gimnasio" }
        assertEquals(1, gymRows.size, "el detector no debe crear una segunda fila")
        val gym = gymRows[0].jsonObject
        assertEquals("CONFIRMED", gym["status"]!!.jsonPrimitive.content, "el re-scan no la degrada a CANDIDATE")
        assertEquals(90_000L, gym["amount"]!!.jsonPrimitive.long, "el re-scan no le pisa el monto que puso el dueño")
    }

    @Test
    fun `POST rejects a blank name, non-positive amount and unknown currency`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"displayName":"  ","amount":90000,"currency":"COP","dayOfMonth":3}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"displayName":"Gimnasio","amount":0,"currency":"COP","dayOfMonth":3}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"displayName":"Gimnasio","amount":90000,"currency":"EUR","dayOfMonth":3}""")
        }.status)
    }

    @Test
    fun `POST with a repeated name and currency is 409`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val body = """{"displayName":"Gimnasio","amount":90000,"currency":"COP","dayOfMonth":3}"""
        client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        val second = client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    /**
     * Ola 8: el choque se mide contra lo que el dueño PUEDE VER. Antes, «Quitar» dejaba la fila
     * en DISMISSED —invisible en la lista y sin forma de recuperarla— pero seguía contando como
     * duplicado: volver a contratar el servicio y anotarlo de nuevo daba 409 sobre algo que no
     * estaba en ninguna parte, y la única salida era cambiarle el nombre.
     */
    @Test
    fun `re-creating a subscription that was removed is allowed, not 409`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val body = """{"displayName":"Claude","amount":20,"currency":"USD","dayOfMonth":7}"""

        val first = client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, first.status)
        val id = Json.parseToJsonElement(first.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        // El dueño la quita. (Una detectada se marca DISMISSED; se simula ese estado a mano
        // porque es el que dejaba la fila bloqueando el alta.)
        transaction {
            Subscriptions.update({ Subscriptions.id eq id }) { it[status] = "DISMISSED" }
        }

        // Vuelve a contratarlo y lo anota otra vez: tiene que poder.
        val again = client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, again.status)

        // Y queda UNA sola fila viva, no la nueva encima del cadáver de la vieja.
        val res = client.get("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val subs = Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(1, subs.size)
        assertEquals("CONFIRMED", subs[0].jsonObject["status"]!!.jsonPrimitive.content)
    }

    // ── Ola 16: mensual o anual ──────────────────────────────────────────────
    //
    // Los montos son los cobros reales que el dueño está por cargar. Sin periodicidad, NBA
    // ($112.900/año) y HBO Max ($369.900/año) le habrían dicho que gasta $482.800 TODOS LOS MESES
    // en ellos, cuando la plata real es $40.234 — doce veces de más sobre su propio dinero.

    private suspend fun ApplicationTestBuilder.crearSuscripcion(token: String, cuerpo: String) =
        client.post("/api/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.listar(token: String) = Json.parseToJsonElement(
        client.get("/api/subscriptions") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText()
    ).jsonObject

    @Test
    fun `el total mensual prorratea un cobro anual en vez de contarlo entero`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        crearSuscripcion(token, """{"displayName":"HBO Max Platinum","amount":369900,"currency":"COP","dayOfMonth":28,"periodicidad":"ANUAL"}""")
        crearSuscripcion(token, """{"displayName":"NBA League Pass","amount":112900,"currency":"COP","dayOfMonth":4,"periodicidad":"ANUAL"}""")
        crearSuscripcion(token, """{"displayName":"Google One","amount":79000,"currency":"COP","dayOfMonth":15}""")

        val body = listar(token)
        // 30.825 (HBO, división exacta) + 9.409 (NBA, redondeado hacia arriba) + 79.000.
        assertEquals(119_234L, body["monthlyTotalCop"]!!.jsonPrimitive.long)

        // Y lo GUARDADO sigue siendo el cobro real, el que el dueño puede buscar en el extracto:
        // el prorrateado es una cuenta de Movi y no vive en ninguna fila.
        val porNombre = body["subscriptions"]!!.jsonArray
            .associateBy { it.jsonObject["displayName"]!!.jsonPrimitive.content }
        assertEquals(369_900L, porNombre["HBO Max Platinum"]!!.jsonObject["amount"]!!.jsonPrimitive.long)
        assertEquals("ANUAL", porNombre["HBO Max Platinum"]!!.jsonObject["periodicidad"]!!.jsonPrimitive.content)
    }

    /**
     * El alta que manda un APK anterior a la Ola 16: sin la clave. Tiene que significar mensual —
     * es lo único que esa hoja sabía anotar— y por lo tanto contar entero, como contaba siempre.
     */
    @Test
    fun `un alta sin periodicidad es mensual y cuenta entera`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val creada = crearSuscripcion(token, """{"displayName":"Gimnasio","amount":90000,"currency":"COP","dayOfMonth":3}""")
        assertEquals(HttpStatusCode.Created, creada.status)
        assertEquals(
            "MENSUAL",
            Json.parseToJsonElement(creada.bodyAsText()).jsonObject["periodicidad"]!!.jsonPrimitive.content,
        )
        assertEquals(90_000L, listar(token)["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    /**
     * Una fila que YA estaba en producción antes de que existiera la columna. Se siembra sin
     * tocar `periodicidad` justamente para ejercitar el default de la columna, que es toda la
     * migración que este proyecto tiene (no hay archivos de migración: `createMissingTablesAndColumns`
     * corre en cada arranque).
     */
    @Test
    fun `una fila sembrada sin periodicidad se lee como mensual`() = testApplication {
        wireApp()
        transaction {
            Subscriptions.insert {
                it[id]          = "sub-vieja-spotify"
                it[userId]      = userAId
                it[merchantKey] = "spotify"
                it[displayName] = "Spotify"
                it[amount]      = 16_900
                it[currency]    = "COP"
                it[dayOfMonth]  = 9
                it[status]      = "CONFIRMED"
                it[confidence]  = "HIGH"
                it[firstSeen]   = 0
                it[lastSeen]    = 0
                it[occurrences] = 5
                it[accountId]   = null
            }
        }
        val body = listar(tokenFor(userAId))
        val spotify = body["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "spotify" }.jsonObject
        assertEquals("MENSUAL", spotify["periodicidad"]!!.jsonPrimitive.content)
        assertEquals(16_900L, body["monthlyTotalCop"]!!.jsonPrimitive.long, "vale lo que siempre valió")
    }

    @Test
    fun `el PUT guarda la periodicidad y el total la respeta`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val creada = Json.parseToJsonElement(
            crearSuscripcion(token, """{"displayName":"HBO Max Platinum","amount":369900,"currency":"COP","dayOfMonth":28}""").bodyAsText()
        ).jsonObject
        assertEquals("MENSUAL", creada["periodicidad"]!!.jsonPrimitive.content)
        assertEquals(369_900L, listar(token)["monthlyTotalCop"]!!.jsonPrimitive.long)

        // El dueño se da cuenta de que se lo cobran una vez al año y lo corrige.
        val id = creada["id"]!!.jsonPrimitive.content
        val corregida = creada.toMutableMap().apply { put("periodicidad", JsonPrimitive("ANUAL")) }
        val put = client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(corregida)))
        }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals("ANUAL", Json.parseToJsonElement(put.bodyAsText()).jsonObject["periodicidad"]!!.jsonPrimitive.content)
        assertEquals(30_825L, listar(token)["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    /**
     * **El APK viejo no puede volver mensual un cobro anual.**
     *
     * Un cliente anterior a la Ola 16 no conoce el campo, así que cualquier PUT suyo —quitar la
     * suscripción, corregirle el monto— manda un cuerpo SIN la clave. Si el server usara el
     * default del objeto deserializado, HBO Max volvería a valer $369.900 al mes sin que el dueño
     * hubiera cambiado nada: el número plausible que aparece solo, que es la peor forma de un
     * error de plata. La ruta mira las claves del JSON crudo, igual que `PUT /api/credits/{id}`.
     */
    @Test
    fun `un PUT de un cliente viejo no le borra la periodicidad anual`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        val creada = Json.parseToJsonElement(
            crearSuscripcion(token, """{"displayName":"HBO Max Platinum","amount":369900,"currency":"COP","dayOfMonth":28,"periodicidad":"ANUAL"}""").bodyAsText()
        ).jsonObject
        val id = creada["id"]!!.jsonPrimitive.content

        // Exactamente lo que manda el APK 1.17: todos los campos que conoce, ninguno más.
        val cuerpoDelApkViejo = JsonObject(creada.filterKeys { it != "periodicidad" })
        val put = client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), cuerpoDelApkViejo))
        }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals(
            "ANUAL",
            Json.parseToJsonElement(put.bodyAsText()).jsonObject["periodicidad"]!!.jsonPrimitive.content,
            "el cliente no habló de periodicidad: no hay nada que cambiar",
        )
        assertEquals(30_825L, listar(token)["monthlyTotalCop"]!!.jsonPrimitive.long)
    }

    /**
     * El barrido no puede pisar lo que decidió el dueño. Es el mismo razonamiento que ya protege
     * al DISMISSED y al alta manual: la periodicidad no se toca en ninguna rama de
     * `SubscriptionSync`, ni siquiera en el refresco completo de una CANDIDATE.
     */
    @Test
    fun `un re-scan no vuelve mensual una suscripcion que el dueno marco anual`() = testApplication {
        wireApp()
        val token = tokenFor(userAId)
        // Se marca ANUAL una fila DETECTADA (clave `netflix`, no `manual_*`), que es la única que
        // el barrido sí toca — con una manual la guarda del prefijo ya la dejaría afuera sola.
        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer $token") }
        val netflix = listar(token)["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        val id = netflix["id"]!!.jsonPrimitive.content
        val anual = netflix.toMutableMap().apply {
            put("status", JsonPrimitive("CONFIRMED"))
            put("periodicidad", JsonPrimitive("ANUAL"))
        }
        client.put("/api/subscriptions/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(anual)))
        }

        client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer $token") }

        val despues = listar(token)["subscriptions"]!!.jsonArray
            .first { it.jsonObject["merchantKey"]!!.jsonPrimitive.content == "netflix" }.jsonObject
        assertEquals("ANUAL", despues["periodicidad"]!!.jsonPrimitive.content)
    }

    /** Y lo que el detector crea por su cuenta sigue siendo mensual: agrupa por MES. */
    @Test
    fun `lo que detecta el barrido nace mensual`() = testApplication {
        wireApp()
        val res = client.post("/api/subscriptions/detect") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val subs = Json.parseToJsonElement(res.bodyAsText()).jsonObject["subscriptions"]!!.jsonArray
        assertEquals(2, subs.size)
        subs.forEach {
            assertEquals("MENSUAL", it.jsonObject["periodicidad"]!!.jsonPrimitive.content)
        }
    }
}
