package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.ai.contextoDelPeriodoDe
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.parteFijaDelChecklist
import com.jvillada.movi.server.reminders.queAvisarle
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 72 · una regla pausada no cuenta
 *
 * «Pausar» deja la regla en `recurring_rules.pausada_desde`. Desde ese día, ninguna ocurrencia sin
 * pagar existe: no sale en `/api/payments/upcoming`, en `/api/payments/occurrences`, en la caja, en el
 * barrido de recordatorios, en los débitos automáticos ni en Movi AI, y del gasto variable no se absorbe
 * nada por ella. **Lo ya pagado queda** (la fila pagada del período sigue en el checklist), y
 * «Reanudar» la devuelve tal cual. Cada dueño pausa solo lo suyo, y un APK viejo no la reanuda al editar.
 *
 * El mes de calendario como período; «hoy» fijo el 29 de octubre de 2026, salvo Movi AI, que lee el
 * reloj de verdad.
 */
class ReglaPausadaTest {

    private val testSecret = "test-secret-for-regla-pausada-min-32-chars!"
    private val duenoId = "user-dueno-pausa"
    private val otroId = "user-otro-pausa"
    private val ahorros = "acc_ahorros_pausa"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = LocalDate.of(2026, 10, 29)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:regla_pausada_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Apartados, Users, Accounts, Events, VoidEvents, Budgets, Documents, RecurringRules,
                RecurringOccurrences, OccurrenceRejections, Credits, Cards, Subscriptions, Goals, SmsMessages,
                KnownDestinations, DebitosDescartados,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@pausa.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            Accounts.insert {
                it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS"
                it[currency] = "COP"; it[balance] = 0L
            }
        }
    }

    // ── Armado ──────────────────────────────────────────────────────────────

    private fun regla(
        id: String,
        nombre: String,
        dia: Int,
        monto: Long,
        pausadaDesde: String? = null,
        debita: Boolean = false,
        categoria: String = nombre,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = duenoId; it[name] = nombre; it[category] = categoria
            it[amount] = monto; it[dayOfMonth] = dia; it[type] = "EXPENSE"; it[accountId] = ahorros
            it[seDebitaSolo] = debita
            it[RecurringRules.pausadaDesde] = pausadaDesde
        }
    }

    private fun gasto(id: String, monto: Long, fecha: LocalDate, descripcion: String, categoria: String = descripcion) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = duenoId; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun pausarEnLaBase(id: String, desde: String?) = transaction {
        RecurringRules.update({ RecurringRules.id eq id }) { it[pausadaDesde] = desde }
    }

    private fun proximos(dia: LocalDate = hoy) = runBlocking { proximosPagos(duenoId, dia) }
    private fun ocurrencias(dia: LocalDate = hoy) = runBlocking { ocurrenciasDelPeriodo(duenoId, dia) }

    private fun checklist(dia: LocalDate = hoy) = checklistDelPeriodo(
        upcoming = proximos(dia),
        ocurrencias = ocurrencias(dia),
        periodo = periodoDe(appDateToEpochMillis(dia) + 12 * 3_600_000L, PeriodSettings()),
        settings = PeriodSettings(),
    )

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@pausa.test")
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

    private suspend fun ApplicationTestBuilder.enviar(ruta: String, cuerpo: String = "{}", uid: String = duenoId, metodo: String = "POST"): HttpResponse {
        val armar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return if (metodo == "PUT") client.put(ruta, armar) else client.post(ruta, armar)
    }

    private fun pausadaDe(id: String): String? = transaction {
        RecurringRules.selectAll().where { RecurringRules.id eq id }.single()[RecurringRules.pausadaDesde]
    }

    // ── La API ──────────────────────────────────────────────────────────────

    @Test
    fun `pausar y reanudar por la API, cada duenho lo suyo`() = testApplication {
        application { testModule() }
        regla("rr_gym", "Gimnasio", 5, 180_000)

        val ajeno = enviar("/api/recurring-rules/rr_gym/pausar", uid = otroId)
        assertEquals(HttpStatusCode.NotFound, ajeno.status)
        assertNull(pausadaDe("rr_gym"), "otro usuario no puede pausar la regla del dueño")

        val pausa = enviar("/api/recurring-rules/rr_gym/pausar", """{"desde":"2026-10-29"}""")
        assertEquals(HttpStatusCode.OK, pausa.status, pausa.bodyAsText())
        assertEquals("2026-10-29", json.decodeFromString(RecurringRule.serializer(), pausa.bodyAsText()).pausadaDesde)
        assertEquals("2026-10-29", pausadaDe("rr_gym"))

        // Sin fecha, desde hoy.
        enviar("/api/recurring-rules/rr_gym/reanudar")
        enviar("/api/recurring-rules/rr_gym/pausar")
        assertEquals(AppClock.today().toString(), pausadaDe("rr_gym"))

        // La lista trae la regla con su pausa (Pagos la pinta en «Pausados»).
        val lista = client.get("/api/recurring-rules?conAbonos=1") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        val reglas = json.decodeFromString(ListSerializer(RecurringRule.serializer()), lista.bodyAsText())
        assertNotNull(reglas.single().pausadaDesde)

        val malaFecha = enviar("/api/recurring-rules/rr_gym/pausar", """{"desde":"29/10/2026"}""")
        assertEquals(HttpStatusCode.BadRequest, malaFecha.status)

        assertEquals(HttpStatusCode.NotFound, enviar("/api/recurring-rules/rr_gym/reanudar", uid = otroId).status)
        val reanudar = enviar("/api/recurring-rules/rr_gym/reanudar")
        assertEquals(HttpStatusCode.OK, reanudar.status)
        assertNull(pausadaDe("rr_gym"))
    }

    @Test
    fun `editar desde un APK viejo no reanuda la regla`() = testApplication {
        application { testModule() }
        regla("rr_gym", "Gimnasio", 5, 180_000, pausadaDesde = "2026-10-29")
        val editar = enviar(
            "/api/recurring-rules/rr_gym",
            """{"id":"rr_gym","name":"Gimnasio","category":"Gimnasio","amount":190000,"dayOfMonth":5,"type":"EXPENSE"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, editar.status, editar.bodyAsText())
        assertEquals("2026-10-29", pausadaDe("rr_gym"))
        assertEquals("2026-10-29", json.decodeFromString(RecurringRule.serializer(), editar.bodyAsText()).pausadaDesde)
        // Y un alta nunca nace pausada, aunque el cuerpo lo diga.
        val alta = enviar(
            "/api/recurring-rules",
            """{"id":"","name":"Netflix","category":"Suscripciones","amount":40000,"dayOfMonth":3,"type":"EXPENSE","pausadaDesde":"2026-10-01"}""",
        )
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        assertNull(json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText()).pausadaDesde)
    }

    // ── Upcoming, ocurrencias y el checklist ───────────────────────────────────

    @Test
    fun `una pausada sin pagar no sale en upcoming, ocurrencias ni el checklist, y reanudar la devuelve`() {
        regla("rr_carro", "Carro", 20, 9_000, pausadaDesde = "2026-10-29")
        regla("rr_arriendo", "Arriendo", 30, 1_800_000)

        assertTrue(proximos().none { it.rule.id == "rr_carro" })
        assertTrue(ocurrencias().none { it.ruleId == "rr_carro" })
        assertEquals(listOf("rr_arriendo"), checklist().map { it.ruleId })

        pausarEnLaBase("rr_carro", null)
        assertTrue(proximos().any { it.rule.id == "rr_carro" })
        assertTrue(checklist().any { it.ruleId == "rr_carro" && !it.pagado }, "reanudada, vuelve a deber el de octubre")
    }

    @Test
    fun `lo ya pagado queda en la lista del periodo`() {
        regla("rr_gym", "Gimnasio", 5, 180_000, pausadaDesde = "2026-10-29")
        gasto("ev_gym", 180_000, LocalDate.of(2026, 10, 5), "Gimnasio")

        assertTrue(proximos().none { it.rule.id == "rr_gym" }, "noviembre ya cae en la pausa")
        val estado = ocurrencias().single { it.ruleId == "rr_gym" }
        assertTrue(estado.occurred)
        assertEquals("rr_gym", estado.reglaPausada?.id)
        val fila = checklist().single { it.ruleId == "rr_gym" }
        assertTrue(fila.pagado)
        assertEquals("ev_gym", fila.eventId)
    }

    @Test
    fun `un movimiento posterior a la pausa no lo toma una regla pausada`() {
        regla("rr_mercado", "Mercado", 30, 600_000, pausadaDesde = "2026-10-27")
        gasto("ev_mercado", 600_000, LocalDate.of(2026, 10, 28), "Mercado")
        assertTrue(ocurrencias().none { it.ruleId == "rr_mercado" })
        assertTrue(checklist().none { it.ruleId == "rr_mercado" })
    }

    @Test
    fun `una pausa que empieza despues no cambia lo de hoy, pero el periodo siguiente no la cuenta`() {
        regla("rr_luz", "Luz", 30, 120_000, pausadaDesde = "2026-11-15")
        assertTrue(proximos().any { it.rule.id == "rr_luz" }, "el vencimiento del 30 de octubre todavía corre")
        val siguiente = proximos().single { it.rule.id == "rr_luz" }.siguienteVencimiento
        assertNull(siguiente, "el 30 de noviembre ya cae en la pausa")
    }

    // ── La caja, los recordatorios, los débitos y el gasto variable ───────────

    @Test
    fun `la caja no resta una regla pausada`() {
        regla("rr_internet", "Internet", 30, 95_000, pausadaDesde = "2026-10-29")
        regla("rr_arriendo", "Arriendo", 30, 1_800_000)
        val caja = runBlocking { cajaProyectadaDe(duenoId, hoy) }
        assertTrue(caja.pagos.none { it.ruleId == "rr_internet" })
        assertTrue(caja.pagos.any { it.ruleId == "rr_arriendo" })
    }

    @Test
    fun `el barrido no avisa una regla pausada`() {
        regla("rr_internet", "Internet", 30, 95_000, pausadaDesde = "2026-10-29")
        regla("rr_arriendo", "Arriendo", 30, 1_800_000)
        val avisa = runBlocking { queAvisarle(duenoId, hoy, leadDays = 3) }.reglas.map { it.id }
        assertEquals(listOf("rr_arriendo"), avisa)
    }

    @Test
    fun `los debitos automaticos no proponen una regla pausada`() {
        regla("rr_seguro", "Seguro Sura", 10, 98_500, pausadaDesde = "2026-10-29", debita = true, categoria = "Seguros")
        val ahora = appDateToEpochMillis(hoy) + 12 * 3_600_000L
        assertTrue(runBlocking { debitosPorConfirmar(duenoId, hoy, ahora) }.isEmpty())
        pausarEnLaBase("rr_seguro", null)
        assertEquals("rr_seguro", runBlocking { debitosPorConfirmar(duenoId, hoy, ahora) }.single().ruleId)
    }

    @Test
    fun `el gasto variable no se absorbe por una regla pausada`() {
        val pausada = RecurringRule("rr_mercado", "Mercado", "Mercado", 600_000, 28, com.jvillada.movi.shared.model.TransactionType.EXPENSE, pausadaDesde = "2026-10-27")
        val evento = com.jvillada.movi.shared.model.FinancialEvent(
            id = "ev_mercado", accountId = ahorros, type = com.jvillada.movi.shared.model.TransactionType.EXPENSE,
            amount = 600_000, category = "Mercado", description = "Mercado",
            timestamp = appDateToEpochMillis(LocalDate.of(2026, 10, 28)) + 12 * 3_600_000L,
            reconciliationStatus = com.jvillada.movi.shared.model.ReconciliationStatus.RECONCILED,
        )
        fun parte(regla: RecurringRule) = parteFijaDelChecklist(
            reglas = listOf(regla), sellos = emptyList(), ocurridos = emptyMap(), eventos = listOf(evento),
            hoy = hoy, settings = PeriodSettings(),
        )
        assertTrue(parte(pausada).isEmpty(), "pausada, el Mercado sigue siendo gasto variable")
        assertEquals(600_000L, parte(pausada.copy(pausadaDesde = null))["ev_mercado"], "activa, lo absorbe como siempre")
    }

    @Test
    fun `Tus periodos no lista lo pendiente de una pausada, y lo pagado si`() {
        regla("rr_carro", "Carro", 20, 9_000, pausadaDesde = "2026-10-29")
        regla("rr_gym", "Gimnasio", 5, 180_000, pausadaDesde = "2026-10-29")
        gasto("ev_gym", 180_000, LocalDate.of(2026, 10, 5), "Gimnasio")
        val octubre = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, PeriodSettings())
        val fijos = transaction {
            pagosFijosDelPeriodo(duenoId, octubre, hoy, PeriodSettings(), inicioDeLaHistoria = LocalDate.of(2026, 1, 1))
        }.map { it.ruleId }
        assertEquals(listOf("rr_gym"), fijos)
    }

    // ── Movi AI ───────────────────────────────────────────────────────────────

    @Test
    fun `Movi AI no cuenta una regla pausada`() {
        val hoyDeVerdad = AppClock.today()
        regla("rr_internet", "Internet", 28, 95_000, pausadaDesde = hoyDeVerdad.toString())
        regla("rr_arriendo", "Arriendo", 28, 1_800_000)
        val nombres = runBlocking { contextoDelPeriodoDe(duenoId) }.recurrentes.map { it.nombre }
        assertTrue("Arriendo" in nombres, nombres.toString())
        assertFalse("Internet" in nombres, nombres.toString())
    }
}
