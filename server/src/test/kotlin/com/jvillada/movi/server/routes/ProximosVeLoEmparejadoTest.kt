package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.queAvisarle
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.UpcomingPayment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **«Próximos» ve lo que Movi emparejó solo.**
 *
 * Visto en el teléfono del dueño el 24-sep: Plan · Próximos decía «Celular · Vencido hace 2 días ·
 * −$53.077» con el movimiento «Celular» de $52.990 del 22-sep vivo en la base, y el checklist del
 * período —`/api/payments/occurrences`— dándolo por pagado. El nombre pega, así que
 * `ocurrenciaConcluyente` lo empareja **derivado**, sin sello; y `/api/payments/upcoming` armaba sus
 * períodos ocurridos solo con los sellos (más las cuotas sintéticas pagadas), así que el
 * vencimiento no rodaba.
 *
 * Se exige acá que las dos respuestas digan lo mismo: lo que el checklist da por hecho rueda en
 * «Próximos», lo que el checklist pregunta (dos candidatos concluyentes) sigue como hoy, y el
 * barrido de recordatorios no avisa por correo algo que ya está pagado.
 */
class ProximosVeLoEmparejadoTest {

    private val testSecret = "test-secret-para-proximos-emparejados-32-ch"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val uid = "user-proximos-emparejado"
    private val email = "proximos@emparejado.test"
    private val cuenta = "acc-bancolombia"
    private val celular = "rr-celular"

    /** El día en que el dueño lo vio: octubre ya había arrancado, por excepción, el 24-sep. */
    private val elDiaDelDueno: LocalDate = LocalDate.of(2026, 9, 24)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:proximos_emparejado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, Credits, Cards,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            Users.insert {
                it[id] = uid
                it[Users.email] = this@ProximosVeLoEmparejadoTest.email
                it[name] = "Dueño"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = cuenta
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    /** El período real del dueño: corte 25 y octubre arrancando el 24-sep. */
    private fun conElPeriodoDelDueno() = transaction {
        Users.update({ Users.id eq uid }) {
            it[Users.periodCutoffDay] = 25
            it[Users.periodStarts] = """{"2026-10":"2026-09-24"}"""
        }
    }

    /** «Celular», día [dia], $53.077, categoría Celular — la regla tal como la tiene el dueño. */
    private fun reglaCelular(dia: Int) = transaction {
        RecurringRules.insert {
            it[id] = celular
            it[userId] = uid
            it[name] = "Celular"
            it[category] = "Celular"
            it[amount] = 53_077L
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = cuenta
        }
    }

    /** Un pago «Celular» de $52.990: el nombre pega, el monto no — como el del dueño. */
    private fun pagoCelular(id: String, fecha: LocalDate) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = "EXPENSE"
            it[amount] = 52_990L
            it[category] = "Celular"
            it[description] = "Celular"
            it[timestamp] = appDateToEpochMillis(fecha) + 15 * 3_600_000L
        }
    }

    private fun proximoDelCelular(hoy: LocalDate): UpcomingPayment =
        runBlocking { proximosPagos(uid, hoy) }.first { it.rule.id == celular }

    private fun checklistDelCelular(hoy: LocalDate): OccurrenceState? = transaction {
        estadosDeLasOcurrenciasReales(uid, hoy, ajustesDelPeriodoSinSuspender(uid))
    }.firstOrNull { it.ruleId == celular }

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

    // ── El endpoint, con el día de hoy ───────────────────────────────────────

    /**
     * Por HTTP y con el reloj de verdad, para probar el cableado de la ruta: un pago de hace dos
     * días que el checklist empareja solo **no sale vencido** en «Próximos», y su vencimiento rueda
     * al mes siguiente. Hace dos días está siempre dentro de la gracia, así que sin el arreglo sale
     * OVERDUE cualquier día que corra.
     */
    @Test
    fun `un pago emparejado solo no sale vencido y su vencimiento rueda`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val hoy = AppClock.today()
        val haceDos = hoy.minusDays(2)
        reglaCelular(dia = haceDos.dayOfMonth)
        pagoCelular("ev-celular", haceDos)

        val estado = client.ocurrencias().first { it.ruleId == celular }
        assertTrue(estado.occurred && estado.automatica, "El checklist lo da por pagado, emparejado solo")

        val pago = client.upcoming().first { it.rule.id == celular }
        assertNotEquals(PaymentStatus.OVERDUE, pago.status, "Pagado y emparejado: no está vencido")
        assertTrue(
            LocalDate.parse(pago.dueDate).isAfter(hoy),
            "El vencimiento rueda al siguiente, como con un sello; llegó ${pago.dueDate}",
        )
        assertEquals(0L, transaction { RecurringOccurrences.selectAll().count() }, "Rodar no escribe sellos")
    }

    // ── El caso del dueño, con su día y su período ───────────────────────────

    /**
     * El 24-sep, con octubre arrancando ese mismo día por excepción y corte 25: el pago del 22-sep
     * cierra el vencimiento del 22-sep (sello `"2026-09"`), y «Próximos» pasa al 22-oct.
     */
    @Test
    fun `el Celular del duenho no sale vencido con octubre arrancando el 24`() {
        conElPeriodoDelDueno()
        reglaCelular(dia = 22)
        pagoCelular("ev-celular", LocalDate.of(2026, 9, 22))

        val estado = checklistDelCelular(elDiaDelDueno)
        assertTrue(estado != null && estado.occurred, "El checklist del dueño ya lo daba por pagado")
        assertEquals("2026-09", estado.period)

        val pago = proximoDelCelular(elDiaDelDueno)
        assertEquals("2026-10-22", pago.dueDate, "Tiene que coincidir con el checklist: el de septiembre ya está")
        assertEquals(PaymentStatus.UPCOMING, pago.status)
    }

    /** El control del anterior: sin el pago, el 22-sep sigue vencido — el arreglo no apaga deudas. */
    @Test
    fun `sin el pago el Celular sigue vencido`() {
        conElPeriodoDelDueno()
        reglaCelular(dia = 22)

        val pago = proximoDelCelular(elDiaDelDueno)
        assertEquals("2026-09-22", pago.dueDate)
        assertEquals(PaymentStatus.OVERDUE, pago.status)
    }

    /**
     * **Con dudas no cuenta.** Dos pagos «Celular» en la ventana: el checklist pregunta cuál es, y
     * «Próximos» sigue diciendo lo de hoy — vencido — hasta que el dueño conteste.
     */
    @Test
    fun `con dos candidatos concluyentes sigue vencido`() {
        conElPeriodoDelDueno()
        reglaCelular(dia = 22)
        pagoCelular("ev-celular-1", LocalDate.of(2026, 9, 22))
        pagoCelular("ev-celular-2", LocalDate.of(2026, 9, 20))

        val estado = checklistDelCelular(elDiaDelDueno)
        assertTrue(estado != null && !estado.occurred, "Con dos concluyentes Movi pregunta")

        val pago = proximoDelCelular(elDiaDelDueno)
        assertEquals("2026-09-22", pago.dueDate)
        assertEquals(PaymentStatus.OVERDUE, pago.status)
    }

    // ── El barrido de recordatorios ──────────────────────────────────────────

    /**
     * El correo de «vence» es la misma afirmación que «Vencido hace 2 días» en la pantalla: el
     * barrido no puede avisar un pago que el checklist ya da por hecho.
     */
    @Test
    fun `el barrido no avisa una regla ya emparejada`() {
        conElPeriodoDelDueno()
        reglaCelular(dia = 22)
        pagoCelular("ev-celular", LocalDate.of(2026, 9, 22))

        val aviso = runBlocking { queAvisarle(uid, elDiaDelDueno, leadDays = 3) }
        assertFalse(aviso.reglas.any { it.id == celular }, "Ya está pagado: no hay nada que avisar")
    }

    /** El control: sin el pago, el barrido sí lo avisa. Sin esto, la prueba de arriba no probaría nada. */
    @Test
    fun `el barrido si avisa la regla sin pagar`() {
        conElPeriodoDelDueno()
        reglaCelular(dia = 22)

        val aviso = runBlocking { queAvisarle(uid, elDiaDelDueno, leadDays = 3) }
        assertTrue(aviso.reglas.any { it.id == celular }, "Vencido y sin pagar: se avisa")
    }
}
