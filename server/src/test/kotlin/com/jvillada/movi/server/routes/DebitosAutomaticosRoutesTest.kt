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
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.confirmacionDelDebito
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.NOTA_DEL_DEBITO_AUTOMATICO
import com.jvillada.movi.shared.model.textoDelDebitoAutomatico
import com.jvillada.movi.server.reminders.loadEventsBetween
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
 * # «Lo que el banco cobra solo»: la propuesta en «Por revisar»
 *
 * Una cuota con débito automático, vencida y sin movimiento, se propone armada. Lo que se fija: que
 * sale el día del vencimiento o después y nunca antes; que no sale si la cuota ya está pagada; que
 * recargar no la duplica; que confirmarla —por `POST /api/payments/installment`, el camino de
 * siempre— crea las dos patas y tilda la fila del período, con el monto pactado o con otro; que
 * «No se cobró» la saca de ese período y no de los siguientes; que cada dueño ve solo lo suyo; que
 * no toca la captura del banco; y que un aviso del banco por el mismo pago gana sobre la propuesta
 * (y, si llega después, encuentra el movimiento ya anotado).
 *
 * Las fechas fijas van por [debitosPorConfirmar] con un «hoy» elegido; las rutas, con el reloj de
 * verdad (crédito con día de pago = hoy).
 */
class DebitosAutomaticosRoutesTest {

    private val testSecret = "test-secret-for-debitos-automaticos-min-32-chars"
    private val duenoId = "user-dueno-deb"
    private val otroId = "user-otro-deb"
    private val json = Json { ignoreUnknownKeys = true }

    private val credito = "acc_loan_9695"
    private val ahorros = "acc_ahorros_8133"
    private val creditoDelOtro = "acc_loan_otro"
    private val ahorrosDelOtro = "acc_ahorros_otro"
    private val cuota = 1_204_064L

    /** El 15 de julio de 2026, ya pasado (no se confirma lo que no venció): vence la cuota del 9695. */
    private val dia15: LocalDate = LocalDate.of(2026, 7, 15)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:debitos_automaticos_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
                Users.insert { it[id] = uid; it[email] = "$uid@deb.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            cuenta(credito, duenoId, "Libre inversión 9695", "LOAN")
            cuenta(ahorros, duenoId, "Bancolombia Ahorros", "SAVINGS")
            cuenta(creditoDelOtro, otroId, "Crédito del otro", "LOAN")
            cuenta(ahorrosDelOtro, otroId, "Ahorros del otro", "SAVINGS")
            // La deuda viva, para que el pago de la cuota tenga de dónde bajar.
            evento(duenoId, "apertura_9695", credito, "EXPENSE", 30_000_000L, "Deuda inicial", LocalDate.of(2026, 1, 15))
            evento(otroId, "apertura_otro", creditoDelOtro, "EXPENSE", 5_000_000L, "Deuda inicial", LocalDate.of(2026, 1, 15))
        }
    }

    private fun cuenta(id: String, uid: String, nombre: String, tipo: String) = Accounts.insert {
        it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = tipo; it[currency] = "COP"; it[balance] = 0L
    }

    private fun evento(uid: String, id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate) =
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = id
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }

    /** El crédito del dueño, que el banco debita de Ahorros el [dia]. */
    private fun creditoConDebito(dia: Int = 15, uid: String = duenoId, cuentaCredito: String = credito, desde: String? = ahorros) =
        transaction {
            Credits.insert {
                it[accountId] = cuentaCredito; it[userId] = uid; it[bank] = "Bancolombia"; it[principal] = 40_000_000L
                it[rateEa] = 24.5; it[termMonths] = 60; it[installment] = cuota; it[dayOfMonth] = dia
                it[startDate] = "2026-01-15"; it[remindMe] = true; it[debitoAutomaticoDesde] = desde
            }
        }

    private fun propuestas(hoy: LocalDate, uid: String = duenoId, ahora: Long = appDateToEpochMillis(hoy) + 12 * 3_600_000L) =
        runBlocking { debitosPorConfirmar(uid, hoy, ahora) }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@deb.test")
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

    /** «Sí, se cobró»: lo mismo que manda la tarjeta de «Por revisar». */
    private suspend fun ApplicationTestBuilder.pagar(pedido: ConfirmarDebitoAutomatico, uid: String = duenoId): HttpResponse =
        client.post("/api/debitos-automaticos/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ConfirmarDebitoAutomatico.serializer(), pedido))
        }

    private suspend fun ApplicationTestBuilder.descartar(d: DebitoAutomaticoPorConfirmar, uid: String = duenoId): HttpResponse =
        client.post("/api/debitos-automaticos/descartar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"ruleId":"${d.ruleId}","periodo":"${d.periodo}"}""")
        }

    private fun eventosDelDueno(): List<FinancialEvent> = transaction {
        loadEventsBetween(duenoId, 0L, Long.MAX_VALUE)
    }

    // ── Cuándo sale ─────────────────────────────────────────────────────────────

    @Test
    fun `sale el dia del vencimiento, armada, y no antes`() {
        creditoConDebito()
        assertTrue(propuestas(dia15.minusDays(1)).isEmpty(), "el banco no debita antes del vencimiento")
        val d = propuestas(dia15).single()
        assertEquals(OrigenDelDebito.CUOTA_DE_CREDITO, d.origen)
        assertEquals("credit_$credito", d.ruleId)
        assertEquals("2026-07", d.periodo)
        assertEquals("2026-07-15", d.vence)
        assertEquals(cuota, d.monto)
        assertEquals(ahorros, d.cuentaId)
        assertEquals(credito, d.deudaId)
        assertEquals(
            "Débito automático: Cuota Libre inversión 9695 · \$1.204.064 desde Bancolombia Ahorros — ¿se cobró?",
            textoDelDebitoAutomatico(d),
        )
        assertEquals(1, propuestas(dia15.plusDays(3)).size, "días después sigue esperando la respuesta")
    }

    /**
     * **Un crédito pagado no se propone**, aunque siga marcado «El banco la cobra solo»: con la deuda en
     * cero (o a favor) el banco no tiene qué debitar.
     */
    @Test
    fun `un credito con la deuda en cero no se propone aunque tenga el debito marcado`() {
        creditoConDebito()
        transaction {
            evento(duenoId, "pago_total_9695", credito, "INCOME", 30_000_000L, "Abono a capital", LocalDate.of(2026, 6, 1))
        }
        assertTrue(propuestas(dia15).isEmpty(), "en cero")
        transaction {
            evento(duenoId, "pago_de_mas_9695", credito, "INCOME", 10_000L, "Abono a capital", LocalDate.of(2026, 6, 2))
        }
        assertTrue(propuestas(dia15).isEmpty(), "a favor")
    }

    @Test
    fun `un credito con algo por pagar todavia se propone`() {
        creditoConDebito()
        transaction {
            evento(duenoId, "casi_todo_9695", credito, "INCOME", 29_999_000L, "Abono a capital", LocalDate.of(2026, 6, 1))
        }
        assertEquals(1, propuestas(dia15).size, "le quedan \$1.000: el banco todavía cobra")
    }

    @Test
    fun `un credito sin debito automatico no se propone`() {
        creditoConDebito(desde = null)
        assertTrue(propuestas(dia15).isEmpty())
    }

    @Test
    fun `recargar no la duplica ni le cambia los ids`() {
        creditoConDebito()
        val primera = propuestas(dia15)
        val segunda = propuestas(dia15)
        assertEquals(1, segunda.size)
        assertEquals(primera, segunda)
    }

    // ── Confirmar ───────────────────────────────────────────────────────────────

    @Test
    fun `confirmarla crea la cuota de dos patas y tilda el periodo`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val d = propuestas(dia15).single()
        val res = pagar((confirmacionDelDebito(d, d.monto)))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())

        val patas = eventosDelDueno().filter { it.transferId == d.transferId }
        assertEquals(2, patas.size)
        val dinero = patas.single { it.accountId == ahorros }
        assertEquals(TransactionType.EXPENSE, dinero.type)
        assertEquals(cuota, dinero.amount)
        assertEquals(CUOTA_CATEGORY, dinero.category)
        assertEquals(EventSource.MANUAL, dinero.source, "lo anotó el dueño: no se hace pasar por un aviso del banco")
        assertTrue(dinero.description.endsWith(NOTA_DEL_DEBITO_AUTOMATICO), dinero.description)
        assertEquals(appDateToEpochMillis(dia15) + 12 * 3_600_000L, dinero.timestamp, "se anota el día que debitó el banco")
        val deuda = patas.single { it.accountId == credito }
        assertEquals(TransactionType.INCOME, deuda.type)

        assertTrue(propuestas(dia15).isEmpty(), "pagada, la propuesta se va")
        assertTrue(propuestas(dia15.plusDays(2)).isEmpty())
    }

    @Test
    fun `la fila de pagos del periodo queda tildada por el movimiento`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        creditoConDebito(dia = hoy.dayOfMonth)
        val d = propuestasHttp(this).single()
        assertEquals(hoy.toString(), d.vence)
        // Antes del vencimiento el banco no cobra: el server no deja confirmar el del mes que viene.
        val proximo = hoy.plusMonths(1).toString().take(7)
        assertEquals(HttpStatusCode.BadRequest, pagar(confirmacionDelDebito(d, d.monto).copy(periodo = proximo)).status)
        assertEquals(HttpStatusCode.Created, pagar((confirmacionDelDebito(d, d.monto))).status)

        val ocurrencias = client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
        }
        val fila = json.decodeFromString(ListSerializer(OccurrenceState.serializer()), ocurrencias.bodyAsText())
            .single { it.ruleId == "credit_$credito" }
        assertTrue(fila.occurred)
        assertTrue(fila.derivadaDeUnMovimiento, "la tilda el movimiento, no una marca")
        assertEquals(d.pataDeLaDeudaId, fila.eventId)
        assertTrue(propuestasHttp(this).isEmpty())
    }

    @Test
    fun `confirmar dos veces no duplica`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val pedido = (confirmacionDelDebito(propuestas(dia15).single(), cuota))
        assertEquals(HttpStatusCode.Created, pagar(pedido).status)
        assertEquals(HttpStatusCode.OK, pagar(pedido).status, "el doble toque contesta lo que ya quedó")
        assertEquals(2, eventosDelDueno().count { it.transferId == pedido.transferId })
    }

    @Test
    fun `cambiar el monto anota lo que cobro el banco`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val d = propuestas(dia15).single()
        assertEquals(HttpStatusCode.Created, pagar((confirmacionDelDebito(d, 1_250_000L))).status)
        val dinero = eventosDelDueno().single { it.transferId == d.transferId && it.accountId == ahorros }
        assertEquals(1_250_000L, dinero.amount)
        assertTrue(propuestas(dia15).isEmpty(), "con otro monto también salda el período")
    }

    @Test
    fun `un pago anulado devuelve la propuesta con ids nuevos`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val d = propuestas(dia15).single()
        pagar((confirmacionDelDebito(d, cuota)))
        transaction {
            eventosDelDueno().filter { it.transferId == d.transferId }.forEach { pata ->
                VoidEvents.insert {
                    it[id] = "void_${pata.id}"; it[userId] = duenoId; it[originalEventId] = pata.id
                    it[reason] = "prueba"; it[timestamp] = System.currentTimeMillis()
                }
            }
        }
        val otra = propuestas(dia15).single()
        assertTrue(otra.transferId != d.transferId, "con los ids viejos el pago anulado contestaría por el nuevo")
        assertEquals(HttpStatusCode.Created, pagar((confirmacionDelDebito(otra, cuota))).status)
        assertTrue(propuestas(dia15).isEmpty())
    }

    // ── No se cobró ─────────────────────────────────────────────────────────────

    @Test
    fun `no se cobro la saca de ese periodo y no de los siguientes`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val d = propuestas(dia15).single()
        assertEquals(HttpStatusCode.NoContent, descartar(d).status)
        assertEquals(HttpStatusCode.NoContent, descartar(d).status, "idempotente")
        assertTrue(propuestas(dia15).isEmpty())
        assertTrue(propuestas(dia15.plusDays(4)).isEmpty())
        assertEquals("2026-08", propuestas(dia15.plusMonths(1)).single().periodo)
        assertTrue(eventosDelDueno().none { it.category == CUOTA_CATEGORY }, "decir que no, no anota nada")
    }

    // ── Cada dueño lo suyo ──────────────────────────────────────────────────────

    @Test
    fun `cada duenio ve y descarta solo lo suyo`() = testApplication {
        application { testModule() }
        creditoConDebito()
        assertTrue(propuestas(dia15, uid = otroId).isEmpty())
        val d = propuestas(dia15).single()
        // El otro dice «no se cobró» sobre la regla del dueño: queda a su nombre y no le saca nada.
        assertEquals(HttpStatusCode.NoContent, descartar(d, uid = otroId).status)
        assertEquals(1, propuestas(dia15).size)
        // Y con los ids de la propuesta del dueño, el otro no puede anotarle la cuota.
        assertEquals(HttpStatusCode.NotFound, pagar((confirmacionDelDebito(d, cuota)), uid = otroId).status)
        assertEquals(1, propuestas(dia15).size)
    }

    @Test
    fun `sin token no hay propuestas`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/debitos-automaticos").status)
    }

    // ── La captura y los avisos del banco ───────────────────────────────────────

    @Test
    fun `no cuenta como captura del banco`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        creditoConDebito(dia = hoy.dayOfMonth)
        val d = propuestasHttp(this).single()
        pagar((confirmacionDelDebito(d, cuota)))
        // Ni proponerla ni confirmarla escribe un aviso: «banco mudo» y la captura no la ven.
        assertEquals(0L, transaction { SmsMessages.selectAll().count() })
        val bandeja = client.get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals("[]", bandeja.bodyAsText().trim())
        val mudos = client.get("/api/sms/origenes-mudos") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals("[]", mudos.bodyAsText().trim())
    }

    @Test
    fun `un aviso del banco pendiente por el mismo pago gana sobre la propuesta`() {
        creditoConDebito()
        transaction {
            SmsMessages.insert {
                it[id] = "sms_debito"; it[userId] = duenoId; it[time] = "2026-07-15 06:10"; it[bank] = "85540"
                it[text] = "Bancolombia: Pagaste \$1,204,064.00 a Bancolombia Credito desde tu producto 8133 el 15/07/2026 06:10:00."
                it[state] = SMS_STATE_PENDING; it[det] = "Bancolombia Credito"
            }
        }
        assertTrue(propuestas(dia15).isEmpty(), "el aviso ya está en la bandeja: dos tarjetas para un pago no")
        // Tres días después, el aviso sigue pendiente y sigue ganando.
        assertTrue(propuestas(dia15.plusDays(3)).isEmpty())
    }

    @Test
    fun `el aviso que llega despues encuentra la cuota ya anotada`() = testApplication {
        application { testModule() }
        creditoConDebito()
        val d = propuestas(dia15).single()
        pagar((confirmacionDelDebito(d, cuota)))
        // El correo de Bancolombia llega al otro día: «¿Ya lo anotaste?» tiene que ver la pata del dinero.
        val leido = assertNotNull(
            parseSms("Bancolombia: Pagaste \$1,204,064.00 a Bancolombia Credito desde tu producto 8133 el 15/07/2026 06:10:00."),
        )
        val momento = appDateToEpochMillis(dia15.plusDays(1)) + 9 * 3_600_000L
        val iguales = coincidenciasDelSms(leido, momento, eventosDelDueno())
        assertEquals(d.pataDelDineroId, iguales.firstOrNull()?.id)
    }

    private suspend fun propuestasHttp(app: ApplicationTestBuilder): List<DebitoAutomaticoPorConfirmar> {
        val res = app.client.get("/api/debitos-automaticos") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(DebitoAutomaticoPorConfirmar.serializer()), res.bodyAsText())
    }
}
