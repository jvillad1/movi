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
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.currentMonthWindow
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.server.time.epochMillisToAppDateString
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.PeriodSettings

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
                // «Disponible»: el gasto variable descarta los movimientos atados a un sello.
                RecurringOccurrences, OccurrenceRejections,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
                // Ola 10: el resumen ahora lee las preferencias de categoría (esconder / tipo fijado).
                CategoryPrefs,
                // «Disponible»: el gasto variable descarta los movimientos atados a un sello.
                RecurringOccurrences, OccurrenceRejections,
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
        // Confirmado por default: la columna nace «UNCONFIRMED» y eso lo dejaría fuera de todas
        // las cifras. Lo pendiente se pide explícito (ver el test de «Por confirmar»).
        estado: String = "RECONCILED",
        traspaso: String? = null,
    ) = transaction {
        Events.insert {
            it[Events.transferId] = traspaso
            it[Events.reconciliationStatus] = estado
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

    private fun sms(
        id: String,
        state: String,
        uid: String = userId,
        cuando: String = "2026-08-01T10:00:00",
    ) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id]     = id
            it[SmsMessages.userId] = uid
            it[time]               = cuando
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

    /**
     * `usosRecientes`: **respalda con datos** qué tan viva está una categoría — a diferencia del
     * resto de `usedCategories`, que a propósito no filtra ni por fecha ni por anulados (ver el
     * KDoc de `usedCategories` en DashboardRoutes.kt).
     */
    @Test
    fun `usosRecientes cuenta los movimientos no anulados de los ultimos 60 dias, de cualquier tipo`() = testApplication {
        wireApp()
        val ahora = System.currentTimeMillis()
        val hace10Dias = ahora - 10L * 24 * 60 * 60 * 1000
        val hace61Dias = ahora - 61L * 24 * 60 * 60 * 1000

        event("r-gasto", savings, "EXPENSE", 10_000L, category = "Carro", timestamp = ahora)
        event("r-ingreso", savings, "INCOME", 20_000L, category = "Carro", timestamp = hace10Dias)
        event("r-anulado", savings, "EXPENSE", 30_000L, category = "Carro", timestamp = ahora)
        voidEvent("r-anulado")
        event("r-viejo", savings, "EXPENSE", 5_000L, category = "Carro", timestamp = hace61Dias)
        event("r-otra-categoria", savings, "EXPENSE", 1_000L, category = "Comida", timestamp = ahora)

        val usosPorCategoria = summary()["usedCategories"]!!.jsonArray
            .associate { entry ->
                val obj = entry.jsonObject
                obj["name"]!!.jsonPrimitive.content to (obj["usosRecientes"]?.jsonPrimitive?.int ?: 0)
            }

        assertEquals(2, usosPorCategoria["Carro"], "el gasto y el ingreso recientes cuentan; ni el anulado ni el de hace 61 días")
        assertEquals(1, usosPorCategoria["Comida"])
    }

    // ── Ola A: la cuenta más usada, para que «Agregar» no arranque en la primera alfabética ──────

    @Test
    fun `la cuenta mas usada es la de mas gastos en los ultimos 30 dias`() = testApplication {
        wireApp()
        val ahora = System.currentTimeMillis()
        event("m-1", savings, "EXPENSE", 10_000L, timestamp = ahora)
        event("m-2", savings, "EXPENSE", 20_000L, timestamp = ahora)
        event("m-3", card, "EXPENSE", 5_000L, timestamp = ahora)

        assertEquals(savings, summary()["cuentaMasUsada"]!!.jsonPrimitive.content)
    }

    @Test
    fun `los anulados no cuentan para la cuenta mas usada, y eso cambia quien gana`() = testApplication {
        wireApp()
        val ahora = System.currentTimeMillis()
        // Sin excluir los anulados, "card" ganaría 3 a 2. Con la exclusión, le quedan 1 y pierde.
        event("s-1", savings, "EXPENSE", 10_000L, timestamp = ahora)
        event("s-2", savings, "EXPENSE", 12_000L, timestamp = ahora)
        event("c-1", card, "EXPENSE", 5_000L, timestamp = ahora)
        event("c-2", card, "EXPENSE", 5_000L, timestamp = ahora)
        event("c-3", card, "EXPENSE", 5_000L, timestamp = ahora)
        voidEvent("c-2")
        voidEvent("c-3")

        assertEquals(savings, summary()["cuentaMasUsada"]!!.jsonPrimitive.content)
    }

    @Test
    fun `traspasos, ajustes y pagos de tarjeta no cuentan para la cuenta mas usada`() = testApplication {
        wireApp()
        val ahora = System.currentTimeMillis()
        // El único gasto que SÍ cuenta está en `card`; todo lo de `savings` es una categoría reservada.
        event("real", card, "EXPENSE", 5_000L, category = "Comida", timestamp = ahora)
        event("traspaso", savings, "EXPENSE", 100_000L, category = TRANSFER_CATEGORY, timestamp = ahora)
        event("ajuste", savings, "EXPENSE", 50_000L, category = ADJUSTMENT_CATEGORY, timestamp = ahora)
        event("pago-tc", savings, "EXPENSE", 30_000L, category = CARD_PAYMENT_CATEGORY, timestamp = ahora)

        assertEquals(card, summary()["cuentaMasUsada"]!!.jsonPrimitive.content)
    }

    @Test
    fun `un empate en cantidad de gastos lo rompe el movimiento mas reciente`() = testApplication {
        wireApp()
        val haceUnaHora = System.currentTimeMillis() - 60 * 60 * 1000
        val ahora = System.currentTimeMillis()
        event("e-viejo", savings, "EXPENSE", 10_000L, timestamp = haceUnaHora)
        event("e-nuevo", card, "EXPENSE", 10_000L, timestamp = ahora)

        assertEquals(
            card,
            summary()["cuentaMasUsada"]!!.jsonPrimitive.content,
            "empatan 1 a 1; gana la cuenta del movimiento más reciente",
        )
    }

    @Test
    fun `sin gastos en los ultimos 30 dias la cuenta mas usada es null`() = testApplication {
        wireApp()
        // Uno viejo (fuera de la ventana de 30 días) y un ingreso: ninguno cuenta como gasto reciente.
        event("viejo", savings, "EXPENSE", 10_000L, timestamp = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000)
        event("ingreso", savings, "INCOME", 3_000_000L, timestamp = System.currentTimeMillis())

        assertNull(summary()["cuentaMasUsada"])
    }

    /**
     * Lo que espera en «Por confirmar» no suma en el Inicio ni en el gastado de los presupuestos
     * (que leen este mismo `spentByCategory`): misma regla que el chip «Gastos» de Movimientos y que
     * `spentByCategoryForPeriod` del cliente. Antes el chip lo sacaba y el Inicio lo sumaba.
     */
    @Test
    fun `lo que espera en Por confirmar no suma en el gasto ni en el ingreso del mes`() = testApplication {
        wireApp()
        event("e-ok", savings, "EXPENSE", 40_000L, category = "Comida")
        event("e-pend", savings, "EXPENSE", 80_000L, category = "Comida", estado = "UNCONFIRMED")
        event("e-pend-cat", savings, "EXPENSE", 15_000L, category = "Ropa", estado = "UNCONFIRMED")
        event("e-sueldo", savings, "INCOME", 2_000_000L, category = "Sueldo")
        event("e-sueldo-pend", savings, "INCOME", 500_000L, category = "Sueldo", estado = "UNCONFIRMED")

        val body = summary()
        assertEquals(40_000L, body.long("monthSpent"))
        assertEquals(mapOf("Comida" to 40_000L), body.spentByCategory())
        assertEquals(2_000_000L, body.long("monthIncome"))
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

    // ── Cuándo llegó el último mensaje del banco (y si llegó alguno) ─────────────────

    /**
     * **La cifra que faltaba, y su ausencia costó semanas de trabajo a mano.** La captura de SMS
     * no entregó un solo mensaje durante varias entregas: `sms_messages` estaba vacía en
     * producción y los 73 movimientos del dueño eran `MANUAL`. Nadie se enteró porque el único
     * indicador vivía en la app de Android y él trabaja en la web.
     *
     * Sin mensajes no viaja ningún campo (`encodeDefaults=false`), y eso ES la respuesta: total
     * 0 y sin último. El cliente lo lee como «nunca llegó nada» (ver `CapturaDeSms` en :core).
     */
    @Test
    fun `sin un solo mensaje, el resumen dice cero y no inventa una fecha`() = testApplication {
        wireApp()
        val body = summary()
        assertEquals(0L, body.long("smsTotal"))
        assertNull(body["smsLastAt"], "no hay último mensaje que nombrar")
    }

    @Test
    fun `con un solo mensaje, ese es el ultimo`() = testApplication {
        wireApp()
        sms("s-1", SMS_STATE_PENDING, cuando = "2026-08-01 10:00")

        val body = summary()
        assertEquals(1L, body.long("smsTotal"))
        assertEquals("2026-08-01 10:00", body["smsLastAt"]!!.jsonPrimitive.content)
    }

    /**
     * Gana el más reciente, y **cuentan todos los estados**: la pregunta es si LLEGARON, no qué
     * se hizo con ellos. Un inbox entero confirmado no significa que la captura esté muda.
     */
    @Test
    fun `con varios gana el mas reciente, en cualquier estado`() = testApplication {
        wireApp()
        sms("s-1", SMS_STATE_PENDING, cuando = "2026-08-01 10:00")
        sms("s-2", SMS_STATE_CONFIRMED, cuando = "2026-09-03 07:15")
        sms("s-3", SMS_STATE_IGNORED, cuando = "2026-08-30 23:59")

        val body = summary()
        assertEquals(3L, body.long("smsTotal"), "confirmados e ignorados también llegaron")
        assertEquals(1L, body.long("pendingSms"), "y «por confirmar» sigue contando solo los pending")
        assertEquals("2026-09-03 07:15", body["smsLastAt"]!!.jsonPrimitive.content)
    }

    /**
     * El `time` es un varchar libre y conviven `"yyyy-MM-dd HH:mm"` con la variante ISO con 'T'.
     * Comparando strings crudos, `' '` va antes que `'T'` y el de las 09:00 le ganaría al de las
     * 10:00 del mismo día. El criterio vive en :core y es el mismo que usa la bandeja del
     * cliente — dos superficies que ordenan por su cuenta nombran mensajes distintos.
     */
    @Test
    fun `mezclar los dos formatos de fecha no desordena cual fue el ultimo`() = testApplication {
        wireApp()
        sms("s-espacio", SMS_STATE_PENDING, cuando = "2026-08-01 09:00")
        sms("s-iso", SMS_STATE_PENDING, cuando = "2026-08-01T10:00:00")

        assertEquals("2026-08-01T10:00:00", summary()["smsLastAt"]!!.jsonPrimitive.content)
    }

    /** El aviso del Inicio se puede callar, y ese silencio es de la cuenta (web y teléfono). */
    @Test
    fun `el silencio del aviso viaja en el resumen y es por usuario`() = testApplication {
        wireApp()
        transaction { Users.update({ Users.id eq userId }) { it[smsAlertMuted] = true } }

        assertEquals(true, summary()["smsAlertMuted"]?.jsonPrimitive?.content?.toBoolean())
        assertNull(summary(uid = otherUserId)["smsAlertMuted"], "el silencio de uno no calla al otro")
    }

    @Test
    fun `todo queda aislado por usuario`() = testApplication {
        wireApp()
        event("b-spent", "acc-b", "EXPENSE", 40_000L, uid = otherUserId)
        event("b-cand", "acc-b", "EXPENSE", 40_000L, category = "Otros", description = "Pago tarjeta", uid = otherUserId)
        sms("b-sms", SMS_STATE_PENDING, uid = otherUserId, cuando = "2026-09-05 08:00")

        val a = summary()
        assertEquals(0L, a.long("monthSpent"))
        assertEquals(0L, a.long("cardPaymentCandidates"))
        assertEquals(0L, a.long("pendingSms"))
        // Lo importante del aislamiento acá: los mensajes de OTRO no pueden hacerle creer a este
        // que su captura anduvo alguna vez.
        assertEquals(0L, a.long("smsTotal"))
        assertNull(a["smsLastAt"])

        val b = summary(uid = otherUserId)
        assertEquals(80_000L, b.long("monthSpent"))
        assertEquals(1L, b.long("cardPaymentCandidates"))
        assertEquals(1L, b.long("pendingSms"))
        assertEquals(1L, b.long("smsTotal"))
        assertEquals("2026-09-05 08:00", b["smsLastAt"]!!.jsonPrimitive.content)
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

    /**
     * **Esta ruta se quedó con el mes de calendario cuando nació el período financiero.**
     *
     * Con corte 1 —el default— las dos cuentas dan igual, así que nadie lo notó hasta que el
     * dueño puso corte 25 (su día de pago) y Presupuestos se partió en dos: las barras salían de
     * `spentByCategory` (mes civil) y la lista de movimientos de la misma pantalla usaba la
     * ventana del período. Un presupuesto con su único gasto el 27 de agosto decía «$0».
     *
     * El test no depende del día en que corra. Se apoya en la frontera del período: el gasto
     * puesto en el PRIMER milisegundo del período cuenta, y el puesto un milisegundo ANTES no.
     * Con corte 25 el arranque del período nunca es un día 1, así que en cualquier fecha al
     * menos una de las dos afirmaciones distingue la ventana correcta de la del mes civil.
     */
    @Test
    fun `el gasto por categoria usa la ventana del periodo del usuario, no el mes civil`() = testApplication {
        transaction {
            Users.update({ Users.id eq userId }) { it[periodCutoffDay] = 25 }
        }
        val inicioDelPeriodo = currentPeriodWindow(PeriodSettings(cutoffDay = 25)).startMillis
        event("ev-dentro", savings, "EXPENSE", 40_000L, category = "Fútbol", timestamp = inicioDelPeriodo)
        event("ev-fuera", savings, "EXPENSE", 999_000L, category = "Fútbol", timestamp = inicioDelPeriodo - 1)

        wireApp()
        val body = summary()

        assertEquals(mapOf("Fútbol" to 40_000L), body.spentByCategory())
        assertEquals(40_000L, body.long("monthSpent"))
    }

    /**
     * **El arranque propio de un período también llega al Inicio.**
     *
     * Desde que un período puede empezar antes o después del corte, leer solo `period_cutoff_day`
     * dejaba a cada pantalla con su propia ventana: Movimientos respetando la excepción y el Inicio
     * ignorándola, sobre la misma plata y el mismo mes. Es la contradicción que el período vino a
     * eliminar, reaparecida un nivel más abajo.
     *
     * El caso es el suyo: corte 25, pero este mes el sueldo entró el 24 y él lo declaró. El gasto
     * del 24 pertenece al período nuevo, y el Inicio tiene que contarlo.
     */
    @Test
    fun `el Inicio respeta el periodo que arranco otro dia`() = testApplication {
        val ajustes = PeriodSettings(cutoffDay = 25)
        val periodoEnCurso = periodoDe(AppClock.now().toInstant().toEpochMilli(), ajustes)
        // El período arranca un día ANTES de lo que manda el corte.
        val inicioNatural = ventanaDe(periodoEnCurso, ajustes).first
        // `java.time` y no `kotlinx.datetime`: esta suite corre del lado del server, donde el
        // segundo no está en el classpath.
        val unDiaAntes = java.time.Instant.ofEpochMilli(inicioNatural)
            .atZone(AppClock.zone).toLocalDate().minusDays(1)

        transaction {
            Users.update({ Users.id eq userId }) {
                it[periodCutoffDay] = 25
                it[periodStarts] = """{"${periodoEnCurso.prefijo}":"$unDiaAntes"}"""
            }
        }
        // Un gasto justo en el día que la excepción sumó al período.
        event("ev-del-dia-ganado", savings, "EXPENSE", 40_000L, category = "Fútbol", timestamp = inicioNatural - 1)

        wireApp()
        val body = summary()

        assertEquals(
            40_000L,
            body.long("monthSpent"),
            "sin leer los arranques propios, este gasto caía en el período anterior y el Inicio decía 0",
        )
    }
    /**
     * La tarjeta «Disponible»: el gasto variable del período, día por día. Lo que cuenta en
     * «Gastos» menos los pagos del checklist —el movimiento atado a un sello de «ya ocurrió» y la
     * cuota de un crédito—, que ya se restan como fijos.
     */
    @Test
    fun `el gasto variable del periodo deja afuera los pagos de los fijos`() = testApplication {
        val ahora = System.currentTimeMillis()
        event("e-almuerzo", savings, "EXPENSE", 30_000L, timestamp = ahora)
        event("e-compra-tarjeta", card, "EXPENSE", 50_000L, category = "Ropa", timestamp = ahora)
        event("e-arriendo", savings, "EXPENSE", 1_850_000L, category = "Vivienda", timestamp = ahora)
        event("e-cuota", savings, "EXPENSE", 900_000L, category = CUOTA_CATEGORY, timestamp = ahora)
        event("e-traspaso", savings, "EXPENSE", 5_000_000L, category = TRANSFER_CATEGORY, timestamp = ahora)
        event("e-sin-confirmar", savings, "EXPENSE", 80_000L, timestamp = ahora, estado = "UNCONFIRMED")
        event("e-anulado", savings, "EXPENSE", 70_000L, timestamp = ahora)
        voidEvent("e-anulado")
        event("e-otro-usuario", "acc-b", "EXPENSE", 999_000L, timestamp = ahora, uid = otherUserId)
        regla("rr_arriendo", "Arriendo", "Vivienda", 1_850_000L)
        sellar("rr_arriendo", "e-arriendo")

        wireApp()
        val gasto = summary()["gastoVariablePorDia"]!!.jsonObject.mapValues { it.value.jsonPrimitive.long }

        assertEquals(mapOf(epochMillisToAppDateString(ahora) to 80_000L), gasto)
    }

    /** Una regla que vence HOY, así su vencimiento cae en el período sea cual sea la fecha. */
    private fun regla(id: String, nombre: String, categoria: String, monto: Long) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[RecurringRules.userId] = this@DashboardRoutesTest.userId
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = AppClock.today().dayOfMonth
            it[type] = "EXPENSE"
        }
    }

    /** El sello de «ya ocurrió» del vencimiento de hoy, con o sin movimiento. */
    private fun sellar(ruleId: String, eventId: String?) = transaction {
        RecurringOccurrences.insert {
            it[RecurringOccurrences.userId] = this@DashboardRoutesTest.userId
            it[RecurringOccurrences.ruleId] = ruleId
            it[period] = java.time.YearMonth.from(AppClock.today()).toString()
            it[RecurringOccurrences.eventId] = eventId
            it[confirmedAt] = System.currentTimeMillis()
        }
    }

    /**
     * El caso del dueño: marcó «Ya lo pagué» sin elegir el movimiento. Ese pago ya está en los
     * fijos (el checklist lo cuenta por el monto de la regla) y no puede contar además como gasto
     * variable. Un segundo gimnasio del mismo monto, con la regla ya completa, sí cuenta.
     */
    @Test
    fun `un fijo sellado sin movimiento no cuenta dos veces, y un segundo pago igual si cuenta`() = testApplication {
        val ahora = System.currentTimeMillis()
        regla("rr_gym", "Gimnasio", "Gimnasio", 180_000L)
        sellar("rr_gym", eventId = null)
        event("e-gym", savings, "EXPENSE", 180_000L, category = "Gimnasio", description = "Gimnasio", timestamp = ahora)
        event("e-gym-2", savings, "EXPENSE", 180_000L, category = "Gimnasio", description = "Gimnasio", timestamp = ahora)
        event("e-almuerzo", savings, "EXPENSE", 30_000L, timestamp = ahora)

        wireApp()
        val gasto = summary()["gastoVariablePorDia"]!!.jsonObject.mapValues { it.value.jsonPrimitive.long }

        assertEquals(mapOf(epochMillisToAppDateString(ahora) to 210_000L), gasto)
    }

    /** Pendiente en el checklist = ya está en los fijos: su pago anotado tampoco es variable. */
    @Test
    fun `el pago anotado de un fijo sin marcar tampoco cuenta como variable`() = testApplication {
        val ahora = System.currentTimeMillis()
        regla("rr_tia", "Tía Caro", "Familia", 100_000L)
        event("e-tia", savings, "EXPENSE", 100_000L, category = "Familia", description = "Tía Caro", timestamp = ahora)

        wireApp()
        assertEquals(emptyMap(), summary()["gastoVariablePorDia"]!!.jsonObject)
    }

    /**
     * El APK que el dueño tiene instalado lee esta respuesta: el arreglo cambia CUÁNTO dice el
     * gasto variable, no la forma. Mismas claves que antes, y el mapa sigue siendo día → pesos.
     */
    @Test
    fun `la forma de la respuesta no cambia para un APK viejo`() = testApplication {
        event("e-sueldo", savings, "INCOME", 3_000_000L, category = "Sueldo")
        event("e-almuerzo", savings, "EXPENSE", 30_000L)
        wireApp()
        val body = summary()
        val conocidas = setOf(
            "scope", "month", "monthIncome", "monthSpent", "spentByCategory", "cardPaymentCandidates",
            "pendingSms", "smsTotal", "smsLastAt", "smsAlertMuted", "usedCategories", "gastoVariablePorDia",
        )
        // El Disponible que cuenta lo que tenías (APK 1.40+) solo AGREGA campos: un APK ≤ 1.39
        // los ignora y sigue leyendo los de siempre, con el mismo tipo y el mismo valor.
        // Y el patrimonio honesto (con los bienes) es un objeto nuevo, también agregado: el APK
        // viejo no lo pide y no le cambia nada de lo que ya leía.
        val nuevas = setOf(
            "saldoTuPlataAlInicio", "entradasDelPeriodo", "guardadoDelPeriodo", "pagosDeDeudaFueraDelChecklist",
            "patrimonio", "cuentaMasUsada",
        )
        assertEquals(emptySet(), body.keys - conocidas - nuevas)
        assertEquals(3_000_000L, body.long("monthIncome"))
        assertEquals(30_000L, body.long("monthSpent"))
        assertEquals(nuevas, body.keys intersect nuevas)
        body["gastoVariablePorDia"]!!.jsonObject.forEach { (dia, monto) ->
            java.time.LocalDate.parse(dia)
            monto.jsonPrimitive.long
        }
    }

    // ── El Disponible cuenta lo que tenías y lo que entró ────────────────────────

    private fun cuentaCondicionada(id: String, condicion: String) = transaction {
        Accounts.insert {
            it[Accounts.id]     = id
            it[Accounts.userId] = this@DashboardRoutesTest.userId
            it[name]            = id
            it[Accounts.type]   = "SAVINGS"
            it[balance]         = 0L
            it[conditionedTo]   = condicion
        }
    }

    /**
     * El caso del dueño en chico: lo que había en la cuenta libre antes del período, el préstamo
     * de su papá anotado como traspaso desde la cuenta del crédito, el colegio pagado desde Nu
     * (condicionada), los rendimientos de Nu (que no cuentan) y una plata apartada en Nu.
     */
    @Test
    fun `el Disponible trae lo que habia en Tu plata al empezar y lo que entro de afuera`() = testApplication {
        cuentaCondicionada("acc-nu", "Educación")
        val inicio = monthStartMillis()
        val antes = inicio - 24L * 60 * 60 * 1000
        val ahora = System.currentTimeMillis()

        // Antes del período: $1.000.000 − $200.000 − $100.000 sin confirmar (el saldo sí lo cuenta).
        event("a-apertura", savings, "INCOME", 1_000_000L, category = OPENING_CATEGORY, timestamp = antes)
        event("a-gasto", savings, "EXPENSE", 200_000L, timestamp = antes)
        event("a-sin-confirmar", savings, "EXPENSE", 100_000L, timestamp = antes, estado = "UNCONFIRMED")
        event("a-anulado", savings, "INCOME", 999_000L, timestamp = antes)
        voidEvent("a-anulado")
        event("a-usd", savings, "INCOME", 50L, currency = "USD", timestamp = antes)
        event("a-nu", "acc-nu", "INCOME", 5_000_000L, category = OPENING_CATEGORY, timestamp = antes)
        event("a-otro", "acc-b", "INCOME", 7_000_000L, timestamp = antes, uid = otherUserId)
        // El primer milisegundo del período ya es del período: es ingreso, no saldo al inicio.
        event("p-sueldo", savings, "INCOME", 3_000_000L, category = "Sueldo", timestamp = inicio)

        // En el período.
        event("p-techo-sale", loan, "EXPENSE", 10_000_000L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-techo")
        event("p-techo-entra", savings, "INCOME", 10_000_000L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-techo")
        event("p-colegio", "acc-nu", "EXPENSE", 3_000_000L, category = "Educación", timestamp = ahora)
        event("p-rendimientos", "acc-nu", "INCOME", 1_222_041L, category = "Rendimientos", timestamp = ahora)
        event("p-a-nu-sale", savings, "EXPENSE", 500_000L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-nu")
        event("p-a-nu-entra", "acc-nu", "INCOME", 500_000L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-nu")
        event("p-tarjeta", card, "EXPENSE", 50_000L, category = "Ropa", timestamp = ahora)

        wireApp()
        val body = summary()

        assertEquals(700_000L, body.long("saldoTuPlataAlInicio"))
        // El sueldo + el préstamo + el colegio que pagó Nu. Los rendimientos de Nu, no.
        assertEquals(3_000_000L + 10_000_000L + 3_000_000L, body.long("entradasDelPeriodo"))
        assertEquals(500_000L, body.long("guardadoDelPeriodo"))
    }

    /**
     * Los pagos de deuda que ni los fijos ni el gasto variable cuentan: una cuota anotada como
     * gasto que ningún ítem del checklist reclama («Crédito Papá») y el pago de una tarjeta sin
     * compras en el período (deuda de antes). La cuota del crédito que el checklist SÍ reclama —su
     * vencimiento es hoy y el pago salda ese período— no se resta otra vez.
     */
    @Test
    fun `el Disponible trae los pagos de deuda que ningun fijo cuenta`() = testApplication {
        val hoy = AppClock.today()
        transaction {
            Credits.insert {
                it[accountId] = loan
                it[userId] = this@DashboardRoutesTest.userId
                it[bank] = "Bancolombia"
                it[principal] = 2_000_000L
                it[rateEa] = 0.0
                it[termMonths] = 24
                it[installment] = 250_000L
                it[dayOfMonth] = hoy.dayOfMonth
                it[startDate] = hoy.minusYears(1).toString()
            }
        }
        val ahora = System.currentTimeMillis()
        // La cuota del crédito del checklist, con sus dos patas.
        event("c-cuota-sale", savings, "EXPENSE", 250_000L, category = CUOTA_CATEGORY, timestamp = ahora, traspaso = "t-cuota")
        event("c-cuota-entra", loan, "INCOME", 250_000L, category = CUOTA_CATEGORY, timestamp = ahora, traspaso = "t-cuota")
        // Una cuota suelta que nadie reclama.
        event("c-papa", savings, "EXPENSE", 4_280_000L, category = CUOTA_CATEGORY, description = "Crédito Papá", timestamp = ahora)
        // El pago mínimo de una tarjeta sin compras en el período, anotado como traspaso.
        event("c-amex-sale", savings, "EXPENSE", 1_008_902L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-amex")
        event("c-amex-entra", card, "INCOME", 1_008_902L, category = TRANSFER_CATEGORY, timestamp = ahora, traspaso = "t-amex")

        wireApp()
        val body = summary()

        assertEquals(4_280_000L + 1_008_902L, body.long("pagosDeDeudaFueraDelChecklist"))
    }
}
