package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.correo.textoDePse
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La cuota del carro pagada por PSE, de punta a punta
 *
 * El pago llega dos veces: el SMS de Bancolombia («Pagaste $4,178,163.00 a Banco de Occidente S A ATH
 * desde tu producto 3333») y el correo de PSE («Empresa: Banco de Occidente (ATH)», «Descripción:
 * PAGO Banco de Occidente - Prestamo»). Se exige que:
 *
 *  1. los dos avisos se junten en un pago, y que la propuesta siga saliendo del SMS —el que nombra la
 *     cuenta— sin tocar `propuestaDelGrupo`;
 *  2. `/parse` de esa propuesta traiga el comercio, la nota y la categoría del correo de PSE, y el
 *     crédito «Vehículo 8761» como la deuda que abona, con la cuenta de los pagos anteriores;
 *  3. confirmar el pago como lo hace la app (un movimiento para los dos avisos + `vincular-deuda` con
 *     esa deuda) deje la cuota del período marcada.
 */
class ElPagoPorPseDePuntaAPuntaTest {

    private val testSecret = "test-secret-for-pago-por-pse-tests-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-pago-pse"

    private val ahorros = "acc-ahorros-pse"
    private val vehiculo = "acc-vehiculo-pse"
    private val hoy: LocalDate = AppClock.today()
    private val cuota = 4_178_163L

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pago_por_pse_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "pse@pago.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = ahorros; it[userId] = uid; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = "acc-otra-pse"; it[userId] = uid; it[name] = "AFC 9497"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = vehiculo; it[userId] = uid; it[name] = "Vehículo 8761"; it[type] = "LOAN" }
            Credits.insert {
                it[accountId] = vehiculo
                it[userId] = uid
                it[bank] = "Banco de Occidente"
                it[principal] = 150_000_000L
                it[rateEa] = 18.0
                it[termMonths] = 72
                it[installment] = cuota
                it[dayOfMonth] = hoy.dayOfMonth
                it[startDate] = hoy.minusYears(2).toString()
            }
            evento("ev-apertura", vehiculo, "EXPENSE", 120_000_000L, "Saldo inicial", hoy.minusYears(1))
            // La cuota de hace dos meses, pagada desde Bancolombia Ahorros con sus dos patas.
            evento("ev-vieja-dinero", ahorros, "EXPENSE", cuota, CUOTA_CATEGORY, hoy.minusMonths(2), "tr-vieja")
            evento("ev-vieja-deuda", vehiculo, "INCOME", 1_500_000L, CUOTA_CATEGORY, hoy.minusMonths(2), "tr-vieja")

            val hora = "$hoy 00:00"
            aviso("sms_rt_occidente", "85540", "Bancolombia: Pagaste \$4,178,163.00 a Banco de Occidente S A ATH desde tu producto 3333 el ${hoy}.", hora)
            aviso(
                "correo_pse_occidente", "Correo · PSE",
                textoDePse(valor = "\$ 4.178.163,00", empresa = "Banco de Occidente (ATH)", descripcion = "PAGO Banco de Occidente - Prestamo"),
                hora,
            )
        }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate, transfer: String? = null) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = "movimiento de prueba"
            it[timestamp] = appDateToEpochMillis(fecha)
            it[transferId] = transfer
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun aviso(id: String, banco: String, texto: String, hora: String) {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = hora
            it[bank] = banco
            it[text] = texto
            it[state] = SMS_STATE_PENDING
            it[det] = ""
        }
    }

    private fun token(): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "pse@pago.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private suspend inline fun <reified T> ApplicationTestBuilder.leer(ruta: String): T {
        val r = cliente().get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status, ruta)
        return r.body()
    }

    @Test
    fun `el SMS y el correo de PSE son un pago, y la propuesta toma la cuenta del SMS y el resto del PSE`() = testApplication {
        application { testModule() }
        val bandeja = leer<List<SmsMessage>>("/api/sms")
        val grupoId = assertNotNull(bandeja.first { it.id == "correo_pse_occidente" }.grupoId, "el correo de PSE se juntó con el SMS")
        assertEquals(setOf("sms_rt_occidente", "correo_pse_occidente"), bandeja.first { it.id == "sms_rt_occidente" }.miembrosDelGrupo.toSet())

        val grupo = leer<GrupoDeAvisos>("/api/sms/grupo/$grupoId")
        assertEquals("sms_rt_occidente", grupo.propuestaDe, "la propuesta sigue saliendo del aviso que nombra la cuenta")

        val p = leer<ParsedSms>("/api/sms/${grupo.propuestaDe}/parse")
        assertEquals(cuota.toDouble(), p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("Banco de Occidente", p.merchant, "el comercio del correo de PSE")
        assertEquals("PAGO Banco de Occidente - Prestamo", p.nota)
        assertEquals(CUOTA_CATEGORY, p.category)
        assertEquals(vehiculo, p.deudaSugeridaId)
        assertEquals(ahorros, p.cuentaSugeridaId)
        assertEquals("La de tus pagos a Vehículo 8761", p.cuentaSugeridaPor)
        assertNull(p.identificadorDelDestino)

        // El correo de PSE solo, leído por su cuenta, dice lo mismo.
        val solo = leer<ParsedSms>("/api/sms/correo_pse_occidente/parse")
        assertEquals(vehiculo, solo.deudaSugeridaId)
        assertEquals(ahorros, solo.cuentaSugeridaId)
    }

    @Test
    fun `confirmar la cuota propuesta con su credito marca la fila del periodo`() = testApplication {
        application { testModule() }
        val reglaDelCredito = "$CREDIT_RULE_PREFIX$vehiculo"
        val antes = leer<List<OccurrenceState>>("/api/payments/occurrences").firstOrNull { it.ruleId == reglaDelCredito }
        assertTrue(antes == null || !antes.occurred, "antes de confirmar la cuota del período no está pagada")

        val grupoId = assertNotNull(leer<List<SmsMessage>>("/api/sms").first { it.id == "sms_rt_occidente" }.grupoId)
        val grupo = leer<GrupoDeAvisos>("/api/sms/grupo/$grupoId")
        val p = leer<ParsedSms>("/api/sms/${grupo.propuestaDe}/parse")

        // Lo que hace la app al confirmar: un movimiento para los dos avisos…
        val evento = FinancialEvent(
            id = "ev-cuota-pse", accountId = assertNotNull(p.cuentaSugeridaId), type = p.type, amount = cuota,
            currency = p.currency, category = p.category, description = "${p.merchant} · ${p.nota}", merchant = p.merchant,
            timestamp = appDateToEpochMillis(hoy),
        )
        val confirmado = cliente().post("/api/sms/grupo/$grupoId/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(ConfirmarElMismoPago(grupo.miembros.map { it.id }, evento = evento))
        }
        assertEquals(HttpStatusCode.OK, confirmado.status)
        assertTrue(confirmado.body<MismoPagoConfirmado>().creado)

        // …y el traspaso de dos patas con la deuda que propuso Movi.
        val vinculo = cliente().put("/api/events/ev-cuota-pse/vincular-deuda") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(VincularPagoDeDeudaRequest(debtAccountId = assertNotNull(p.deudaSugeridaId), transferId = "tr-pse", toEventId = "ev-cuota-pse-deuda"))
        }
        assertEquals(HttpStatusCode.OK, vinculo.status)

        val despues = leer<List<OccurrenceState>>("/api/payments/occurrences").firstOrNull { it.ruleId == reglaDelCredito }
        assertNotNull(despues, "la cuota del período aparece")
        assertTrue(despues.occurred, "y está pagada")
        assertEquals(YearMonth.from(hoy).toString(), despues.period)
        assertEquals("ev-cuota-pse-deuda", despues.eventId)
    }
}
