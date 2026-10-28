package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CuentasDeCadaUso
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PagoDeLaOcurrenciaAnotado
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # «Anotar pago» desde la fila, por las rutas de verdad (vuelta 43)
 *
 * Con el reloj de verdad y vencimientos de HOY:
 *
 * - **una factura real** se paga por `POST /api/recurring-rules/{id}/occurrence/pago` (la hoja de
 *   Agregar en modo pago): 201, la ocurrencia queda con ese movimiento, el reintento contesta 200, la
 *   moneda que no viaja se toma de la cuenta, y un usuario ajeno recibe 404;
 * - **la cuota de un crédito y el pago de una tarjeta** se pagan desde su fila por la pestaña «Cuota»
 *   (`POST /api/payments/installment`, el camino de siempre): terminan saldados, con dos patas, y la
 *   ruta de las facturas los rechaza (400) para que nunca queden como un gasto suelto;
 * - **la cuenta de cada uso** (`GET /api/cuentas/de-cada-uso`) responde la última cuenta por categoría.
 */
class AnotarPagoDesdeLaFilaRutasTest {

    private val testSecret = "test-secret-for-anotar-pago-desde-la-fila-32"
    private val uid = "user-anotar-rutas"
    private val otro = "user-otro-rutas"
    private val json = Json { ignoreUnknownKeys = true }

    private val ahorros = "acc-ahorros-rutas"
    private val credito = "acc-credito-rutas"
    private val tarjeta = "acc-tarjeta-rutas"
    private val cuentaDelOtro = "acc-otro-rutas"

    private val hoy: LocalDate = AppClock.today()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:anotar_pago_rutas_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, SmsMessages, Credits, Cards, CardPaymentDismissals, Subscriptions, Goals, CategoryPrefs,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid, otro).forEach { u ->
                Users.insert { it[id] = u; it[email] = "$u@rutas.test"; it[name] = u; it[passwordHash] = "hash" }
            }
            cuenta(ahorros, uid, "Bancolombia Ahorros", "SAVINGS")
            cuenta(credito, uid, "Libre inversión 9695", "LOAN")
            cuenta(tarjeta, uid, "AMEX 9208", "CREDIT_CARD")
            cuenta(cuentaDelOtro, otro, "Ahorros del otro", "SAVINGS")
            // Una factura real que vence hoy: el arriendo, sin cuenta, en «Otros».
            RecurringRules.insert {
                it[RecurringRules.id] = "rr-arriendo"
                it[userId] = uid
                it[name] = "Arriendo"
                it[category] = "Otros"
                it[amount] = 1_800_000L
                it[dayOfMonth] = hoy.dayOfMonth
                it[type] = "EXPENSE"
            }
            // El crédito y la tarjeta, con su pago hoy. Los dos deben algo.
            Credits.insert {
                it[accountId] = credito
                it[userId] = uid
                it[bank] = "Bancolombia"
                it[principal] = 40_000_000L
                it[rateEa] = 24.5
                it[termMonths] = 60
                it[installment] = 1_204_064L
                it[dayOfMonth] = hoy.dayOfMonth
                it[startDate] = hoy.minusYears(1).toString()
            }
            Cards.insert {
                it[accountId] = tarjeta
                it[userId] = uid
                it[bank] = "American Express"
                it[paymentDay] = hoy.dayOfMonth
            }
            evento("ev-deuda-credito", credito, "EXPENSE", 30_000_000L, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-compra-tarjeta", tarjeta, "EXPENSE", 2_000_000L, "Mercado", hoy.minusDays(20))
        }
    }

    private fun cuenta(id: String, dueno: String, nombre: String, tipo: String) = Accounts.insert {
        it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = tipo; it[currency] = "COP"
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate) =
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = id
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }

    private fun token(u: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", u).withClaim("email", "$u@rutas.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private val ahoraMs: Long get() = appDateToEpochMillis(hoy) + 10 * 3_600_000L

    /** Lo que manda la hoja en modo pago: sin `currency` (el default no viaja) y sin período (vence hoy). */
    private fun cuerpoDelPago(id: String, cuenta: String = ahorros, periodo: String? = null) = """
        {${periodo?.let { "\"periodo\":\"$it\"," } ?: ""}"movimiento":{"id":"$id","accountId":"$cuenta","type":"EXPENSE",
         "amount":1800000,"category":"Otros","description":"Arriendo","source":"MANUAL",
         "reconciliationStatus":"RECONCILED","timestamp":$ahoraMs}}
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.pagarFactura(ruleId: String, cuerpo: String, u: String = uid): HttpResponse =
        client.post("/api/recurring-rules/$ruleId/occurrence/pago") {
            header(HttpHeaders.Authorization, "Bearer ${token(u)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.ocurrencias(): List<OccurrenceState> {
        val res = client.get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(ListSerializer(OccurrenceState.serializer()), res.bodyAsText())
    }

    // ── Una factura real ─────────────────────────────────────────────────────

    @Test
    fun `anotar pago crea el movimiento y salda la ocurrencia`() = testApplication {
        application { testModule() }
        val res = pagarFactura("rr-arriendo", cuerpoDelPago("ev-arriendo"))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        val anotado = json.decodeFromString(PagoDeLaOcurrenciaAnotado.serializer(), res.bodyAsText())
        assertEquals("ev-arriendo", anotado.movimiento.id)
        assertEquals("ev-arriendo", anotado.ocurrencia.eventId)
        assertEquals("COP", anotado.movimiento.currency, "sin moneda en el pedido, la de la cuenta")

        val estado = ocurrencias().single { it.ruleId == "rr-arriendo" }
        assertTrue(estado.occurred, "la fila pasa a «Pagado»")
        assertEquals("ev-arriendo", estado.eventId, "con su enlace al movimiento")
        assertEquals(1_800_000L, estado.montoDelPago, "y dice cuánto salió")

        val reintento = pagarFactura("rr-arriendo", cuerpoDelPago("ev-arriendo"))
        assertEquals(HttpStatusCode.OK, reintento.status, "el reintento contesta lo que quedó")
        assertEquals(1, transaction { Events.selectAll().where { Events.id eq "ev-arriendo" }.count() })
    }

    @Test
    fun `otro usuario recibe 404 y no escribe nada`() = testApplication {
        application { testModule() }
        val res = pagarFactura("rr-arriendo", cuerpoDelPago("ev-ajeno", cuenta = cuentaDelOtro), u = otro)
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(0, transaction { Events.selectAll().where { Events.id eq "ev-ajeno" }.count() })
    }

    @Test
    fun `una ocurrencia ya saldada contesta 409`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Created, pagarFactura("rr-arriendo", cuerpoDelPago("ev-1")).status)
        val otra = pagarFactura("rr-arriendo", cuerpoDelPago("ev-2"))
        assertEquals(HttpStatusCode.Conflict, otra.status)
        assertEquals(0, transaction { Events.selectAll().where { Events.id eq "ev-2" }.count() })
    }

    // ── La cuota de un crédito y el pago de una tarjeta ──────────────────────

    @Test
    fun `la ruta de las facturas no anota una cuota ni una tarjeta`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.BadRequest, pagarFactura("$CREDIT_RULE_PREFIX$credito", cuerpoDelPago("ev-c")).status)
        assertEquals(HttpStatusCode.BadRequest, pagarFactura("$CARD_RULE_PREFIX$tarjeta", cuerpoDelPago("ev-t")).status)
        assertEquals(0, transaction { Events.selectAll().where { (Events.id eq "ev-c") or (Events.id eq "ev-t") }.count() })
    }

    /** Lo que manda la pestaña «Cuota» que abre «Anotar pago» en la fila de una deuda. */
    private suspend fun ApplicationTestBuilder.pagarLaDeuda(deuda: String, monto: Long, tr: String): HttpResponse =
        client.post("/api/payments/installment") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"fromAccountId":"$ahorros","debtAccountId":"$deuda","amount":$monto,"timestamp":$ahoraMs,
                   "transferId":"$tr","fromEventId":"$tr-dinero","toEventId":"$tr-deuda"}""",
            )
        }

    private fun patas(tr: String) = transaction {
        Events.selectAll().where { (Events.userId eq uid) and (Events.transferId eq tr) }
            .map { it[Events.accountId] to it[Events.category] }
    }

    @Test
    fun `la cuota de un credito desde su fila queda saldada con dos patas`() = testApplication {
        application { testModule() }
        // Vuelta 49: vencida y sin pago, la cuota sale ABIERTA (sin candidatos): así cuenta como
        // pendiente en la lista y en el Disponible hasta que el movimiento la salde. Si todavía no
        // vence, no sale (la lista la ve por su vencimiento).
        assertTrue(
            ocurrencias().none { it.ruleId == "$CREDIT_RULE_PREFIX$credito" && (it.occurred || it.candidates.isNotEmpty()) },
            "sin pago, nada pagado ni propuesto",
        )

        val res = pagarLaDeuda(credito, 1_204_064L, "tr-cuota")
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())

        assertEquals(setOf(ahorros to CUOTA_CATEGORY, credito to CUOTA_CATEGORY), patas("tr-cuota").toSet())
        val estado = assertNotNull(ocurrencias().firstOrNull { it.ruleId == "$CREDIT_RULE_PREFIX$credito" })
        assertTrue(estado.occurred && estado.derivadaDeUnMovimiento, "saldada por la pata que baja la deuda")
        assertEquals("tr-cuota-deuda", estado.eventId)
    }

    @Test
    fun `el pago de una tarjeta desde su fila queda saldado con dos patas`() = testApplication {
        application { testModule() }
        val res = pagarLaDeuda(tarjeta, 500_000L, "tr-tarjeta")
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())

        assertEquals(setOf(ahorros to CARD_PAYMENT_CATEGORY, tarjeta to CARD_PAYMENT_CATEGORY), patas("tr-tarjeta").toSet())
        val estado = assertNotNull(ocurrencias().firstOrNull { it.ruleId == "$CARD_RULE_PREFIX$tarjeta" })
        assertTrue(estado.occurred && estado.derivadaDeUnMovimiento)
        assertEquals("tr-tarjeta-deuda", estado.eventId)
    }

    // ── La cuenta de cada uso ────────────────────────────────────────────────

    @Test
    fun `la cuenta de cada uso dice la ultima cuenta de cada categoria`() = testApplication {
        application { testModule() }
        transaction {
            evento("ev-mercado-viejo", tarjeta, "EXPENSE", 300_000L, "Mercado", hoy.minusDays(10))
            evento("ev-mercado-nuevo", ahorros, "EXPENSE", 250_000L, "Mercado", hoy.minusDays(2))
        }
        val res = client.get("/api/cuentas/de-cada-uso") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val cuentas = json.decodeFromString(CuentasDeCadaUso.serializer(), res.bodyAsText())
        assertEquals(ahorros, cuentas.porCategoria["EXPENSE:mercado"], "gana la más reciente")
    }
}
