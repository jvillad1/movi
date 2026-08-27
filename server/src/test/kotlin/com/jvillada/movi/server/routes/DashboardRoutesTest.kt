package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.currentMonthWindow
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * `GET /api/dashboard/summary`: el Inicio deja de bajarse colecciones enteras (todos los SMS,
 * todos los candidatos a pago de tarjeta, todos los eventos de la historia) para sacar un
 * número. Estos tests fijan que los números del resumen son EXACTAMENTE los que la pantalla
 * calculaba antes del lado del cliente — misma regla `isCashFlow` (apertura y pago de tarjeta
 * fuera, LOAN fuera, solo COP), mismo `looksLikeCardPayment`, mismo `SMS_STATE_PENDING` del inbox —
 * y que todo queda aislado por usuario.
 *
 * Mismo arnés que AccountRoutesTest/FinanceRoutesTest: H2 en memoria (compat PostgreSQL),
 * JWT local, cadena completa de plugins vía `wireApp()`.
 */
class DashboardRoutesTest {

    private val testSecret = "test-secret-for-dashboard-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-dashboard"
    private val otherUserId = "user-b-dashboard"

    private val savings = "acc-savings"
    private val card    = "acc-card"
    private val loan    = "acc-loan"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:dashboard_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
                // Ola 10: el resumen ahora lee las preferencias de categoría (esconder / tipo fijado).
                CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
                // Ola 10: el resumen ahora lee las preferencias de categoría (esconder / tipo fijado).
                CategoryPrefs,
            )
            listOf(userId to "a@dashboard.test", otherUserId to "b@dashboard.test").forEach { (id, mail) ->
                Users.insert {
                    it[Users.id]     = id
                    it[email]        = mail
                    it[name]         = id
                    it[passwordHash] = "hash"
                }
            }
            account(savings, "SAVINGS", userId)
            account(card, "CREDIT_CARD", userId)
            account(loan, "LOAN", userId)
            account("acc-b", "SAVINGS", otherUserId)
        }
    }

    private fun account(id: String, type: String, uid: String) {
        Accounts.insert {
            it[Accounts.id]     = id
            it[Accounts.userId] = uid
            it[name]            = id
            it[Accounts.type]   = type
            it[balance]         = 0L
        }
    }

    private fun event(
        id: String,
        accountId: String,
        type: String,
        amount: Long,
        category: String = "Comida",
        description: String = "Almuerzo",
        currency: String = "COP",
        timestamp: Long = System.currentTimeMillis(),
        uid: String = userId,
    ) = transaction {
        Events.insert {
            it[Events.id]          = id
            it[Events.userId]      = uid
            it[Events.accountId]   = accountId
            it[Events.type]        = type
            it[Events.amount]      = amount
            it[Events.currency]    = currency
            it[Events.category]    = category
            it[Events.description] = description
            it[Events.timestamp]   = timestamp
        }
    }

    private fun voidEvent(eventId: String, uid: String = userId) = transaction {
        VoidEvents.insert {
            it[id]              = "void_$eventId"
            it[VoidEvents.userId] = uid
            it[originalEventId] = eventId
            it[timestamp]       = System.currentTimeMillis()
        }
    }

    private fun sms(id: String, state: String, uid: String = userId) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id]     = id
            it[SmsMessages.userId] = uid
            it[time]               = "2026-08-01T10:00:00"
            it[bank]               = "Bancolombia"
            it[text]               = "Compra \$50.000 en Netflix"
            it[SmsMessages.state]  = state
            it[det]                = ""
        }
    }

    /** Primer milisegundo del mes en curso en la zona de la app (Bogotá) — la misma convención que `finance-summary`. */
    private fun monthStartMillis(): Long = currentMonthWindow().startMillis

    /** El día 11 del mes anterior en la zona de la app — bien adentro del mes pasado, lejos del borde. */
    private fun lastMonthMillis(): Long =
        AppClock.now().withDayOfMonth(1).minusMonths(1).plusDays(10)
            .toInstant().toEpochMilli()

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private fun tokenFor(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", "$userId@dashboard.test")
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
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload) else null
                }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    private suspend fun ApplicationTestBuilder.summary(uid: String = userId, query: String = ""): JsonObject {
        val res = client.get("/api/dashboard/summary$query") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}")
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject
    }

    // El Json del server tiene encodeDefaults=false: un cero no viaja. Misma convención que
    // FinanceRoutesTest.eventCount().
    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.long ?: 0L
    private fun JsonObject.spentByCategory(): Map<String, Long> =
        this["spentByCategory"]?.jsonObject?.mapValues { it.value.jsonPrimitive.long } ?: emptyMap()

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `gasto e ingreso del mes siguen isCashFlow - apertura, pago de tarjeta y LOAN quedan fuera`() = testApplication {
        wireApp()
        event("e-income", savings, "INCOME", 3_000_000L, category = "Sueldo")
        event("e-food", savings, "EXPENSE", 120_000L, category = "Comida")
        event("e-food-2", savings, "EXPENSE", 30_000L, category = "Comida")
        event("e-transport", card, "EXPENSE", 50_000L, category = "Transporte")   // compra con tarjeta: sí es gasto
        event("e-opening", savings, "INCOME", 9_000_000L, category = OPENING_CATEGORY)
        event("e-card-pay", savings, "EXPENSE", 1_000_000L, category = CARD_PAYMENT_CATEGORY, description = "PAGO AUTOM TC")
        event("e-card-pay-in", card, "INCOME", 1_000_000L, category = CARD_PAYMENT_CATEGORY)
        event("e-loan", loan, "INCOME", 60_000_000L, category = "Ajuste")
        event("e-usd", savings, "EXPENSE", 100L, category = "Comida", currency = "USD")

        val body = summary()
        assertEquals(3_000_000L, body.long("monthIncome"), "solo el sueldo: ni la apertura ni el ajuste del crédito")
        assertEquals(200_000L, body.long("monthSpent"), "comida + compra con tarjeta; el pago del extracto no se duplica")
        assertEquals(mapOf("Comida" to 150_000L, "Transporte" to 50_000L), body.spentByCategory())
    }

    // ── Ola 9 · A2: las categorías ya usadas viajan con el resumen ───────────────────

    /**
     * Van acá y no en un endpoint propio: el Inicio ya pide esta respuesta, así que «Agregar»
     * tiene las categorías propias del dueño sin una llamada nueva. **Sin filtrar por mes**: una
     * categoría propia sigue siendo suya aunque no la haya usado este mes.
     */
    @Test
    fun `el resumen trae las categorias usadas, con su tipo y de toda la historia`() = testApplication {
        wireApp()
        event("u-1", savings, "EXPENSE", 40_000L, category = "Carro")
        event("u-2", savings, "EXPENSE", 60_000L, category = "Carro")
        event("u-3", savings, "INCOME", 3_000_000L, category = "Nómina")
        // De los dos lados: la categoría queda con los dos tipos.
        event("u-4", savings, "EXPENSE", 10_000L, category = "Ajustes")
        event("u-5", savings, "INCOME", 10_000L, category = "Ajustes")
        // De un mes viejo: igual cuenta como categoría conocida.
        event("u-6", savings, "EXPENSE", 20_000L, category = "Colegio", timestamp = lastMonthMillis())
        // De otro usuario: no se filtra a este.
        event("u-7", savings, "EXPENSE", 20_000L, category = "Ajeno", uid = otherUserId)

        val usadas = summary()["usedCategories"]!!.jsonArray
            .associate { entry ->
                val obj = entry.jsonObject
                obj["name"]!!.jsonPrimitive.content to
                    (obj["types"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet())
            }

        assertEquals(setOf("EXPENSE"), usadas["Carro"], "una sola entrada por categoría, sin repetir")
        assertEquals(setOf("INCOME"), usadas["Nómina"])
        assertEquals(setOf("EXPENSE", "INCOME"), usadas["Ajustes"])
        assertEquals(setOf("EXPENSE"), usadas["Colegio"], "el mes viejo también cuenta")
        assertFalse("Ajeno" in usadas.keys, "las categorías de otro usuario no se filtran acá")
    }

    @Test
    fun `eventos anulados y de otros meses no cuentan`() = testApplication {
        wireApp()
        event("e-this", savings, "EXPENSE", 10_000L)
        event("e-voided", savings, "EXPENSE", 99_000L)
        voidEvent("e-voided")
        event("e-last-month", savings, "EXPENSE", 77_000L, timestamp = lastMonthMillis())
        // Bordes de la ventana [monthStart, monthEnd): el último milisegundo del mes anterior
        // queda FUERA y el primer milisegundo del mes en curso ENTRA.
        event("e-edge-out", savings, "EXPENSE", 5_000L, timestamp = monthStartMillis() - 1)
        event("e-edge-in", savings, "EXPENSE", 3_000L, timestamp = monthStartMillis())

        val body = summary()
        assertEquals(13_000L, body.long("monthSpent"))
        assertEquals(mapOf("Comida" to 13_000L), body.spentByCategory())
        val now = AppClock.now()
        assertEquals("${now.year}-${now.monthValue.toString().padStart(2, '0')}", body["month"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cuenta candidatos a pago de tarjeta con la misma regla que el GET de candidatos`() = testApplication {
        wireApp()
        event("c-1", savings, "EXPENSE", 500_000L, category = "Otros", description = "PAGO AUTOM TC VISA")
        event("c-2", savings, "EXPENSE", 500_000L, category = "Otros", description = "Abono tarjeta Master")
        event("c-already", savings, "EXPENSE", 500_000L, category = CARD_PAYMENT_CATEGORY, description = "Pago tarjeta") // ya categorizado
        event("c-qr", savings, "EXPENSE", 20_000L, category = "Otros", description = "PAGO QR tienda")                     // falso positivo que NO debe contar
        event("c-from-card", card, "EXPENSE", 500_000L, category = "Otros", description = "Pago tarjeta")                   // no sale de una cuenta de activo
        event("c-income", savings, "INCOME", 500_000L, category = "Otros", description = "Pago tarjeta")                    // no es egreso
        event("c-voided", savings, "EXPENSE", 500_000L, category = "Otros", description = "Pago tarjeta")
        voidEvent("c-voided")
        event("c-dismissed", savings, "EXPENSE", 500_000L, category = "Otros", description = "Pago tarjeta")
        val uid = userId
        transaction {
            CardPaymentDismissals.insert {
                it[CardPaymentDismissals.userId] = uid
                it[eventId] = "c-dismissed"
            }
        }

        assertEquals(2L, summary().long("cardPaymentCandidates"))
    }

    @Test
    fun `cuenta solo los SMS en estado pending`() = testApplication {
        wireApp()
        sms("s-1", SMS_STATE_PENDING)
        sms("s-2", SMS_STATE_PENDING)
        sms("s-3", SMS_STATE_CONFIRMED)
        sms("s-4", SMS_STATE_IGNORED)

        assertEquals(2L, summary().long("pendingSms"))
    }

    /**
     * El bug que cerró este test: la ingesta escribía `"new"` y todos los lectores filtraban
     * por `"pending"`, así que un SMS capturado de verdad nunca disparaba la alerta del Inicio
     * ni se podía abrir en la bandeja. Se ejercita el camino COMPLETO — sync real, no un insert
     * a mano — porque el bug vivía justo en la costura entre quien escribe y quien lee.
     */
    @Test
    fun `un SMS recien sincronizado cuenta en pendingSms y deja de contar al confirmarlo`() = testApplication {
        wireApp()
        val id = "sms_rt_deadbeefdeadbeef"
        val body = """[{"id":"$id","time":"2026-08-01 10:00","bank":"Bancolombia",""" +
            """"text":"Compra por ${'$'}50.000 en Netflix","state":"","det":""}]"""

        val sync = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, sync.status, sync.bodyAsText())

        // 1. El Inicio lo cuenta.
        assertEquals(1L, summary().long("pendingSms"), "el SMS recién capturado tiene que contar como pendiente")

        // 2. La bandeja lo muestra en el estado que habilita «Revisar».
        val inbox = client.get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}") }
        assertEquals(HttpStatusCode.OK, inbox.status)
        val row = Json.parseToJsonElement(inbox.bodyAsText()).jsonArray.single().jsonObject
        assertEquals(id, row["id"]!!.jsonPrimitive.content)
        assertEquals(SMS_STATE_PENDING, row["state"]!!.jsonPrimitive.content)

        // 3. Tras confirmarlo deja de contar.
        val confirm = client.post("/api/sms/$id/confirm") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
        }
        assertEquals(HttpStatusCode.OK, confirm.status)
        assertEquals(0L, summary().long("pendingSms"))
    }

    @Test
    fun `todo queda aislado por usuario`() = testApplication {
        wireApp()
        event("b-spent", "acc-b", "EXPENSE", 40_000L, uid = otherUserId)
        event("b-cand", "acc-b", "EXPENSE", 40_000L, category = "Otros", description = "Pago tarjeta", uid = otherUserId)
        sms("b-sms", SMS_STATE_PENDING, uid = otherUserId)

        val a = summary()
        assertEquals(0L, a.long("monthSpent"))
        assertEquals(0L, a.long("cardPaymentCandidates"))
        assertEquals(0L, a.long("pendingSms"))

        val b = summary(uid = otherUserId)
        assertEquals(80_000L, b.long("monthSpent"))
        assertEquals(1L, b.long("cardPaymentCandidates"))
        assertEquals(1L, b.long("pendingSms"))
    }

    @Test
    fun `sin datos responde en ceros y con el scope pedido - un scope desconocido es 400`() = testApplication {
        wireApp()
        val body = summary(query = "?scope=family")
        assertEquals("FAMILY", body["scope"]!!.jsonPrimitive.content)
        assertEquals(0L, body.long("monthSpent"))
        assertEquals(emptyMap(), body.spentByCategory())

        val bad = client.get("/api/dashboard/summary?scope=nope") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userId)}")
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }
}
