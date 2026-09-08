package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.UpcomingPayment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«"Ya ocurrieron · 1" esto es falso»** — el reclamo del dueño, probado extremo a extremo contra
 * los dos endpoints que lo pintaban mal.
 *
 * En su base había cuatro pagos registrados —dos cuotas de crédito y dos pagos de tarjeta, cada uno
 * con sus dos patas— y la app seguía diciendo «Vencido hace 5 días» y contando una sola ocurrencia.
 * Acá se arma esa misma situación con H2 y se exige que:
 *
 *  1. el vencimiento **ruede** al mes que viene (`/api/payments/upcoming`), sin ningún valor nuevo
 *     en `PaymentStatus` — el APK instalado tiene que seguir leyendo esta respuesta;
 *  2. la ocurrencia aparezca **derivada del movimiento** (`/api/payments/occurrences`), marcada
 *     como tal para que la pantalla no le ofrezca un «Deshacer» que no haría nada;
 *  3. y que lo que NO es un pago —la pata que sale de la cuenta de ahorros, un pago anulado— no
 *     apague nada. Callar una deuda real cuesta plata.
 */
class LaCuotaPagadaNoEstaVencidaTest {

    private val testSecret = "test-secret-for-cuota-pagada-tests-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val uid = "user-cuota-pagada"
    private val email = "cuota@pagada.test"
    private val cuentaDelCredito = "acc-credito-cuota"
    private val cuentaDeLaTarjeta = "acc-tarjeta-cuota"
    private val cuentaDeAhorros = "acc-ahorros-cuota"

    /** La fecha civil de la app, la misma que usan los endpoints (Bogotá, no la del sistema). */
    private val hoy: LocalDate = AppClock.today()

    /** El vencimiento del mes en curso cae HOY: sin pago, el estado es «vence hoy». */
    private val diaDelVencimiento = hoy.dayOfMonth
    private val venceHoy = hoy.toString()
    private val venceElMesQueViene =
        occurrenceInMonth(YearMonth.from(hoy).plusMonths(1), diaDelVencimiento).toString()
    private val periodoEnCurso = YearMonth.from(hoy).toString()

    private val reglaDelCredito = "$CREDIT_RULE_PREFIX$cuentaDelCredito"
    private val reglaDeLaTarjeta = "$CARD_RULE_PREFIX$cuentaDeLaTarjeta"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuota_pagada_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, Credits, Cards,
            )
            SchemaUtils.drop(
                Cards, Credits, RecurringOccurrences, RecurringRules, VoidEvents, Events, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, Credits, Cards,
            )

            Users.insert {
                it[id] = uid
                it[Users.email] = this@LaCuotaPagadaNoEstaVencidaTest.email
                it[name] = "Dueño"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = cuentaDeAhorros
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
            Accounts.insert {
                it[id] = cuentaDelCredito
                it[userId] = uid
                it[name] = "Crediágil 3090"
                it[type] = "LOAN"
            }
            Accounts.insert {
                it[id] = cuentaDeLaTarjeta
                it[userId] = uid
                it[name] = "AMEX 9208"
                it[type] = "CREDIT_CARD"
            }
            Credits.insert {
                it[accountId] = cuentaDelCredito
                it[userId] = uid
                it[bank] = "Bancolombia"
                it[principal] = 2_000_000L
                it[rateEa] = 22.0
                it[termMonths] = 67
                it[installment] = 26_485L
                it[dayOfMonth] = diaDelVencimiento
                // Un desembolso viejo: la regla ya está corriendo (ver `ruleIsActiveOn`).
                it[startDate] = hoy.minusYears(2).toString()
            }
            Cards.insert {
                it[accountId] = cuentaDeLaTarjeta
                it[userId] = uid
                it[bank] = "American Express"
                it[paymentDay] = diaDelVencimiento
            }
            // La tarjeta tiene que DEBER algo para tener regla: una tarjeta en $0 no genera
            // ningún próximo pago (ver `loadCardRulePairs`).
            evento(
                id = "ev-compra-tarjeta", cuenta = cuentaDeLaTarjeta, categoria = "Mercado",
                tipo = "EXPENSE", monto = 5_000_000L, fecha = hoy.minusDays(20),
            )
        }
    }

    private fun evento(
        id: String,
        cuenta: String,
        categoria: String,
        tipo: String,
        monto: Long,
        fecha: LocalDate,
        transfer: String? = null,
    ) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[Events.category] = categoria
            it[description] = "movimiento de prueba"
            it[timestamp] = appDateToEpochMillis(fecha)
            it[transferId] = transfer
        }
    }

    /** Las dos patas de un pago de cuota, tal como las escribe `pagoDeCuotaLegs`. */
    private fun registrarPagoDeCuota(fecha: LocalDate = hoy) = transaction {
        evento(
            id = "ev-cuota-dinero", cuenta = cuentaDeAhorros, categoria = CUOTA_CATEGORY,
            tipo = "EXPENSE", monto = 26_485L, fecha = fecha, transfer = "tr-cuota",
        )
        evento(
            id = "ev-cuota-deuda", cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY,
            tipo = "INCOME", monto = 12_157L, fecha = fecha, transfer = "tr-cuota",
        )
    }

    private fun registrarPagoDeTarjeta(fecha: LocalDate = hoy) = transaction {
        evento(
            id = "ev-tarjeta-dinero", cuenta = cuentaDeAhorros, categoria = CARD_PAYMENT_CATEGORY,
            tipo = "EXPENSE", monto = 1_008_902L, fecha = fecha, transfer = "tr-tarjeta",
        )
        evento(
            id = "ev-tarjeta-deuda", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
            tipo = "INCOME", monto = 1_008_902L, fecha = fecha, transfer = "tr-tarjeta",
        )
    }

    private fun mintToken(): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", uid)
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
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private suspend fun HttpClient.upcoming(): List<UpcomingPayment> {
        val resp = get("/api/payments/upcoming") { header(HttpHeaders.Authorization, "Bearer ${mintToken()}") }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body()
    }

    private suspend fun HttpClient.ocurrencias(): List<OccurrenceState> {
        val resp = get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${mintToken()}") }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body()
    }

    // ── El reclamo ────────────────────────────────────────────────────────────

    /** El control: sin el pago registrado, la cuota vence hoy. Todo lo demás se lee contra esto. */
    @Test
    fun `sin el pago registrado la cuota vence hoy`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }

        val pago = client.upcoming().first { it.rule.id == reglaDelCredito }
        assertEquals(venceHoy, pago.dueDate)
        assertTrue(client.ocurrencias().none { it.ruleId == reglaDelCredito })
    }

    @Test
    fun `la cuota registrada rueda el vencimiento al mes que viene`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        registrarPagoDeCuota()

        val pago = client.upcoming().first { it.rule.id == reglaDelCredito }
        assertEquals(
            venceElMesQueViene, pago.dueDate,
            "La cuota está pagada: su vencimiento vigente es el del mes que viene, no hoy",
        )
    }

    @Test
    fun `la cuota registrada aparece como ocurrida y derivada del movimiento`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        registrarPagoDeCuota()

        val estado = client.ocurrencias().firstOrNull { it.ruleId == reglaDelCredito }
        assertNotNull(estado, "«Ya ocurrieron» tiene que incluir la cuota que ya se pagó")
        assertTrue(estado.occurred)
        assertEquals(periodoEnCurso, estado.period)
        // El movimiento que la prueba es la pata que BAJÓ LA DEUDA, no la que sacó la plata.
        assertEquals("ev-cuota-deuda", estado.eventId)
        assertTrue(
            estado.derivadaDeUnMovimiento,
            "Sin esta marca la pantalla le ofrecería un «Deshacer» que no puede hacer nada",
        )
    }

    @Test
    fun `el pago de la tarjeta tambien cuenta`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        registrarPagoDeTarjeta()

        val pago = client.upcoming().first { it.rule.id == reglaDeLaTarjeta }
        assertEquals(venceElMesQueViene, pago.dueDate)
        val estado = client.ocurrencias().firstOrNull { it.ruleId == reglaDeLaTarjeta }
        assertNotNull(estado)
        assertEquals("ev-tarjeta-deuda", estado.eventId)
        assertTrue(estado.derivadaDeUnMovimiento)
    }

    // ── Lo que no puede apagar un aviso ───────────────────────────────────────

    /**
     * **Un pago anulado no pagó nada.** Es el mismo criterio que sostiene los sellos a mano
     * (`loadOccurredBy`): la evidencia se verifica en la lectura, así que anular el movimiento
     * devuelve la cuota a pendiente sin que nadie tenga que acordarse de tocar otra tabla.
     */
    @Test
    fun `un pago anulado no salda la cuota`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        registrarPagoDeCuota()
        transaction {
            VoidEvents.insert {
                it[id] = "void-1"
                it[userId] = uid
                it[originalEventId] = "ev-cuota-deuda"
                it[timestamp] = System.currentTimeMillis()
            }
        }

        val pago = client.upcoming().first { it.rule.id == reglaDelCredito }
        assertEquals(venceHoy, pago.dueDate, "Anulado el movimiento, la cuota vuelve a estar pendiente")
        assertTrue(client.ocurrencias().none { it.ruleId == reglaDelCredito })
    }

    /**
     * **La plata saliendo de la cuenta de ahorros no prueba que la deuda haya bajado.** Lleva la
     * misma categoría que la pata buena, así que sin el filtro por cuenta un gasto suelto anotado
     * «Cuota de crédito» apagaría el aviso de una cuota que nadie pagó.
     */
    @Test
    fun `la pata del dinero sola no salda la cuota`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        transaction {
            evento(
                id = "ev-solo-dinero", cuenta = cuentaDeAhorros, categoria = CUOTA_CATEGORY,
                tipo = "EXPENSE", monto = 26_485L, fecha = hoy,
            )
        }

        val pago = client.upcoming().first { it.rule.id == reglaDelCredito }
        assertEquals(venceHoy, pago.dueDate)
        assertTrue(client.ocurrencias().none { it.ruleId == reglaDelCredito })
    }

    /**
     * **Una regla sintética nunca sale ABIERTA de este endpoint**, ni con el pago ni sin él.
     *
     * Si saliera, la pantalla pintaría su propuesta con el botón «Ya lo pagué» — que en una regla
     * sintética responde 400: un control muerto. Y si el server lo aceptara sería el segundo
     * mecanismo de sellado que este endpoint existe para no tener.
     */
    @Test
    fun `una regla sintetica nunca sale abierta de occurrences`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }

        val sinPago = client.ocurrencias()
        registrarPagoDeCuota()
        val conPago = client.ocurrencias()

        (sinPago + conPago)
            .filter { it.ruleId.startsWith(CREDIT_RULE_PREFIX) || it.ruleId.startsWith(CARD_RULE_PREFIX) }
            .forEach {
                assertTrue(it.occurred, "Una regla sintética solo puede salir de acá si YA está pagada")
                assertTrue(it.candidates.isEmpty(), "Una sintética no propone candidatos: no se sella a mano")
            }
    }

    /** Y una ocurrencia de regla real sigue sin decir que viene de un movimiento derivado. */
    @Test
    fun `una regla real no se marca como derivada`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        transaction {
            RecurringRules.insert {
                it[id] = "rr-arriendo"
                it[userId] = uid
                it[name] = "Arriendo"
                it[category] = "Vivienda"
                it[amount] = 1_800_000L
                it[dayOfMonth] = 1
                it[type] = "EXPENSE"
            }
        }

        val estado = client.ocurrencias().firstOrNull { it.ruleId == "rr-arriendo" }
        assertNotNull(estado)
        assertFalse(estado.occurred)
        assertFalse(estado.derivadaDeUnMovimiento)
        assertNull(estado.eventId)
    }
}
