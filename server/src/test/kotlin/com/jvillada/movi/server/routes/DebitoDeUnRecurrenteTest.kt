package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.DEBITO_DE_REGLA_SIN_CUENTA
import com.jvillada.movi.shared.model.DEBITO_DE_UN_INGRESO
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.confirmacionDelRecurrente
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
 * # «El banco lo cobra solo» en una regla recurrente común
 *
 * El seguro o la suscripción que se debita sola: la regla lleva `seDebitaSolo` (con cuenta, solo en
 * un gasto, y un APK viejo no lo apaga), y el día que vence sin movimiento Movi lo propone armado.
 * Confirmarlo anota el gasto y sella el período **juntos**; dos veces no duplica; si el sello no se
 * puede poner, tampoco queda el gasto. Un movimiento que el emparejador ya reconoce —o que propone
 * como candidato— apaga la propuesta: ahí el checklist ya sabe o ya pregunta.
 */
class DebitoDeUnRecurrenteTest {

    private val testSecret = "test-secret-for-debito-recurrente-min-32-chars"
    private val duenoId = "user-dueno-rec"
    private val otroId = "user-otro-rec"
    private val json = Json { ignoreUnknownKeys = true }
    private val ahorros = "acc_ahorros_rec"
    private val seguro = 98_500L

    /** El 10 de octubre de 2026: vence el seguro (día 10). */
    private val dia10: LocalDate = LocalDate.of(2026, 10, 10)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:debito_recurrente_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                DebitosDescartados, SmsMessages, KnownDestinations, OccurrenceRejections, RecurringOccurrences,
                RecurringRules, Cards, Credits, VoidEvents, Events, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations, SmsMessages, DebitosDescartados,
            )
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@rec.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            Accounts.insert {
                it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS"
                it[currency] = "COP"; it[balance] = 0L
            }
        }
    }

    private fun reglaConDebito(dia: Int = 10, debita: Boolean = true, id: String = "rr_seguro") = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = duenoId; it[name] = "Seguro Sura"; it[category] = "Seguros"
            it[amount] = seguro; it[dayOfMonth] = dia; it[type] = "EXPENSE"; it[accountId] = ahorros
            it[seDebitaSolo] = debita
        }
    }

    private fun gasto(id: String, monto: Long, fecha: LocalDate, descripcion: String, categoria: String = "Seguros") = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = duenoId; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun propuestas(hoy: LocalDate, uid: String = duenoId) =
        runBlocking { debitosPorConfirmar(uid, hoy, appDateToEpochMillis(hoy) + 12 * 3_600_000L) }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@rec.test")
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

    private suspend fun ApplicationTestBuilder.enviar(ruta: String, cuerpo: String, uid: String = duenoId, metodo: String = "POST"): HttpResponse {
        val armar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return if (metodo == "PUT") client.put(ruta, armar) else client.post(ruta, armar)
    }

    private suspend fun ApplicationTestBuilder.confirmar(pedido: ConfirmarDebitoAutomatico, uid: String = duenoId) =
        enviar("/api/debitos-automaticos/confirmar", json.encodeToString(ConfirmarDebitoAutomatico.serializer(), pedido), uid)

    private fun eventos(): List<FinancialEvent> = transaction { loadEventsBetween(duenoId, 0L, Long.MAX_VALUE) }

    private fun regla(): RecurringRule = transaction {
        RecurringRules.selectAll().single().toRule()
    }

    // ── La marca en la regla ────────────────────────────────────────────────────

    private fun reglaJson(vararg extra: String) =
        """{"id":"","name":"Seguro Sura","category":"Seguros","amount":98500,"dayOfMonth":10,"type":"EXPENSE"
            ${if (extra.isEmpty()) "" else "," + extra.joinToString(",")}}"""

    @Test
    fun `se marca con cuenta, y un APK viejo no la apaga`() = testApplication {
        application { testModule() }
        val alta = enviar("/api/recurring-rules", reglaJson("\"accountId\":\"$ahorros\"", "\"seDebitaSolo\":true"))
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val id = regla().id
        assertEquals(true, regla().seDebitaSolo)
        // El APK instalado no conoce el campo: corrige el monto y manda el cuerpo sin la clave.
        val viejo = enviar("/api/recurring-rules/$id", reglaJson("\"amount\":99000").replace("\"amount\":98500,", ""), metodo = "PUT")
        assertEquals(HttpStatusCode.OK, viejo.status, viejo.bodyAsText())
        assertEquals(true, regla().seDebitaSolo, "editar desde un cliente viejo apagó el débito")
        // Y desmarcarlo desde la hoja sí lo apaga.
        enviar("/api/recurring-rules/$id", reglaJson("\"seDebitaSolo\":false"), metodo = "PUT")
        assertEquals(false, regla().seDebitaSolo)
    }

    @Test
    fun `sin cuenta o en un ingreso no se puede marcar`() = testApplication {
        application { testModule() }
        val sinCuenta = enviar("/api/recurring-rules", reglaJson("\"seDebitaSolo\":true"))
        assertEquals(HttpStatusCode.BadRequest, sinCuenta.status)
        assertEquals(DEBITO_DE_REGLA_SIN_CUENTA, sinCuenta.bodyAsText())
        val ingreso = enviar(
            "/api/recurring-rules",
            reglaJson("\"accountId\":\"$ahorros\"", "\"seDebitaSolo\":true").replace("\"EXPENSE\"", "\"INCOME\""),
        )
        assertEquals(HttpStatusCode.BadRequest, ingreso.status)
        assertEquals(DEBITO_DE_UN_INGRESO, ingreso.bodyAsText())
        assertEquals(0L, transaction { RecurringRules.selectAll().count() })
    }

    // ── La propuesta ────────────────────────────────────────────────────────────

    @Test
    fun `sale el dia que vence, sin movimiento, y no antes`() {
        reglaConDebito()
        assertTrue(propuestas(dia10.minusDays(1)).isEmpty())
        val d = propuestas(dia10).single()
        assertEquals(OrigenDelDebito.RECURRENTE, d.origen)
        assertEquals("rr_seguro", d.ruleId)
        assertEquals("2026-10", d.periodo)
        assertEquals(seguro, d.monto)
        assertEquals(ahorros, d.cuentaId)
        assertTrue(d.pataDelDineroId.startsWith("ev_deb_"))
        assertEquals(d, propuestas(dia10).single(), "recargar no la duplica ni le cambia el id")
    }

    @Test
    fun `una regla sin la marca no se propone`() {
        reglaConDebito(debita = false)
        assertTrue(propuestas(dia10).isEmpty())
    }

    @Test
    fun `si ya hay un movimiento que la empareja, no se propone`() {
        reglaConDebito()
        gasto("ev_seguro_octubre", seguro, dia10, "Seguro Sura")
        assertTrue(propuestas(dia10).isEmpty(), "el emparejador ya lo reconoce por el nombre")
    }

    @Test
    fun `si hay un candidato, pregunta el checklist y no la propuesta`() {
        reglaConDebito()
        // Misma categoría y cuenta, otro monto y otro nombre: no es concluyente, pero es candidato.
        gasto("ev_raro", 120_000L, dia10, "Pago PSE")
        assertTrue(propuestas(dia10).isEmpty(), "proponer anotar otro sería invitar a un duplicado")
    }

    // ── Confirmar ───────────────────────────────────────────────────────────────

    @Test
    fun `confirmarla anota el gasto y sella el periodo con el`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        reglaConDebito(dia = hoy.dayOfMonth)
        val d = propuestasHttp().single()
        val res = confirmar(assertNotNull(confirmacionDelRecurrente(d, seguro)))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())

        val gasto = eventos().single { it.id == d.pataDelDineroId }
        assertEquals(TransactionType.EXPENSE, gasto.type)
        assertEquals(seguro, gasto.amount)
        assertEquals("Seguros", gasto.category)
        assertEquals(ahorros, gasto.accountId)
        assertEquals(appDateToEpochMillis(hoy) + 12 * 3_600_000L, gasto.timestamp)

        val fila = ocurrencias().single { it.ruleId == "rr_seguro" }
        assertTrue(fila.occurred)
        assertEquals(d.pataDelDineroId, fila.eventId, "la fila la tilda el movimiento confirmado")
        assertTrue(propuestasHttp().isEmpty())
    }

    @Test
    fun `confirmar dos veces devuelve el mismo gasto`() = testApplication {
        application { testModule() }
        reglaConDebito(dia = AppClock.today().dayOfMonth)
        val pedido = assertNotNull(confirmacionDelRecurrente(propuestasHttp().single(), seguro))
        assertEquals(HttpStatusCode.Created, confirmar(pedido).status)
        val otra = confirmar(pedido.copy(monto = 1L))
        assertEquals(HttpStatusCode.OK, otra.status)
        assertEquals(seguro, json.decodeFromString(FinancialEvent.serializer(), otra.bodyAsText()).amount, "contesta lo que quedó")
        assertEquals(1, eventos().count { it.id == pedido.eventoId })
    }

    @Test
    fun `cambiar el monto anota lo que cobro el banco`() = testApplication {
        application { testModule() }
        reglaConDebito(dia = AppClock.today().dayOfMonth)
        val d = propuestasHttp().single()
        assertEquals(HttpStatusCode.Created, confirmar(assertNotNull(confirmacionDelRecurrente(d, 103_200L))).status)
        assertEquals(103_200L, eventos().single { it.id == d.pataDelDineroId }.amount)
        assertTrue(propuestasHttp().isEmpty())
    }

    @Test
    fun `si el sello no se puede poner, tampoco queda el gasto`() = testApplication {
        application { testModule() }
        reglaConDebito(dia = AppClock.today().dayOfMonth)
        val d = propuestasHttp().single()
        // Un período que todavía no vence: el sello lo rechaza y la transacción se deshace entera.
        val futuro = AppClock.today().plusMonths(2).toString().take(7)
        val res = confirmar(assertNotNull(confirmacionDelRecurrente(d, seguro)).copy(periodo = futuro))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(eventos().none { it.id == d.pataDelDineroId }, "un gasto sin su sello volvería a proponerse")
    }

    @Test
    fun `otro duenio no puede confirmar lo ajeno`() = testApplication {
        application { testModule() }
        reglaConDebito(dia = AppClock.today().dayOfMonth)
        val pedido = assertNotNull(confirmacionDelRecurrente(propuestasHttp().single(), seguro))
        assertEquals(HttpStatusCode.NotFound, confirmar(pedido, uid = otroId).status)
        assertTrue(eventos().isEmpty())
        assertTrue(propuestas(AppClock.today(), uid = otroId).isEmpty())
    }

    private suspend fun ApplicationTestBuilder.propuestasHttp(): List<DebitoAutomaticoPorConfirmar> {
        val res = client.get("/api/debitos-automaticos") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(DebitoAutomaticoPorConfirmar.serializer()), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.ocurrencias(): List<OccurrenceState> {
        val res = client.get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        return json.decodeFromString(ListSerializer(OccurrenceState.serializer()), res.bodyAsText())
    }
}
