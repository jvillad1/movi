package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_DESDE_CON_LIBRANZA
import com.jvillada.movi.shared.model.CUOTA_DESDE_CON_TERCERO
import com.jvillada.movi.shared.model.CUOTA_DESDE_SIN_CUENTA
import com.jvillada.movi.shared.model.CUOTA_DESDE_UNA_DEUDA
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.fijosDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 141 · la cuota de la Hipoteca 1254 sale de la AFC (issue #701)
 *
 * `CreditTerms.sePagaDesde` dice de qué cuenta paga el dueño la cuota. Lo que se fija: que el `PUT` lo
 * guarda, lo conserva (un APK viejo) y lo borra; que no convive con «la paga otro» ni con la libranza y no
 * apunta a una cuenta ajena ni a una deuda (422); y el caso del dueño: con la AFC condicionada, la cuota sale
 * en `/api/payments/upcoming` marcada, anotarla desde la AFC la tilda, y lo que alimenta «Te quedan» no se mueve.
 */
class LaCuotaDesdeLaAfcTest {

    private val testSecret = "test-secret-for-la-cuota-desde-la-afc-min-32-chars"
    private val uid = "user-afc"
    private val otroId = "user-otro-afc"
    private val json = Json { ignoreUnknownKeys = true }

    private val hipoteca = "acc_1254"
    private val afc = "acc_afc_9497"
    private val ahorros = "acc_8133"
    private val tarjeta = "acc_nu"
    private val ahorrosDelOtro = "acc_otro"
    private val cuota = 9_145_256L

    /** El 20 de octubre (corte el 1: el período es octubre); la cuota vence el 25. */
    private val hoy: LocalDate = LocalDate.of(2026, 10, 20)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:la_cuota_desde_la_afc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, Credits, Cards, KnownDestinations,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid, otroId).forEach { u ->
                Users.insert {
                    it[id] = u; it[email] = "$u@afc.test"; it[name] = u; it[passwordHash] = "h"; it[periodCutoffDay] = 1
                }
            }
            fun cuenta(id: String, dueno: String, nombre: String, tipo: String, condicion: String? = null) = Accounts.insert {
                it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = tipo; it[currency] = "COP"
                it[conditionedTo] = condicion
            }
            cuenta(hipoteca, uid, "Hipoteca 1254", "LOAN")
            cuenta(afc, uid, "AFC Davibank 9497", "SAVINGS", condicion = "vivienda")
            cuenta(ahorros, uid, "Bancolombia 8133", "SAVINGS")
            cuenta(tarjeta, uid, "Nu", "CREDIT_CARD")
            cuenta(ahorrosDelOtro, otroId, "Ahorros del otro", "SAVINGS")
        }
        // La plata de cada cuenta, antes del período: $20M en la AFC, $5M en Bancolombia, la deuda.
        apertura("ap_afc", afc, "INCOME", 20_000_000L)
        apertura("ap_8133", ahorros, "INCOME", 5_000_000L)
        apertura("ap_hip", hipoteca, "EXPENSE", 300_000_000L)
    }

    private fun apertura(id: String, cuenta: String, tipo: String, monto: Long) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = "Saldo inicial"; it[description] = "Saldo inicial"
            it[timestamp] = appDateToEpochMillis(LocalDate.of(2026, 9, 1)) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(u: String = uid) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", u).withClaim("email", "$u@afc.test")
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

    /** El cuerpo de un PUT, con lo que haga falta agregado al final. Sin extras = un APK viejo. */
    private fun terminos(vararg extra: String) =
        """{"accountId":"$hipoteca","bank":"Davibank","principal":400000000,"rateEa":12.0,"termMonths":240,
            "installment":$cuota,"dayOfMonth":25,"startDate":"2020-01-15"
            ${if (extra.isEmpty()) "" else "," + extra.joinToString(",")}}"""

    private suspend fun ApplicationTestBuilder.put(cuerpo: String) =
        client.put("/api/credits/$hipoteca") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.guardada(): String? {
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(CreditSummary.serializer()), res.bodyAsText())
            .single { it.account.id == hipoteca }.terms?.sePagaDesde
    }

    @Test
    fun `el PUT guarda, conserva y borra la cuenta de la cuota`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.OK, put(terminos("\"sePagaDesde\":\"$afc\"")).status)
        assertEquals(afc, guardada())
        // Un APK viejo no conoce el campo: editar la nota no lo borra.
        assertEquals(HttpStatusCode.OK, put(terminos("\"notes\":\"la de la casa\"")).status)
        assertEquals(afc, guardada(), "editar otra cosa desde un cliente viejo borró la cuenta de la cuota")
        // Con `null`, se borra.
        assertEquals(HttpStatusCode.OK, put(terminos("\"sePagaDesde\":null")).status)
        assertNull(guardada())
    }

    @Test
    fun `no convive con la paga otro ni con la libranza`() = testApplication {
        application { testModule() }
        val conTercero = put(terminos("\"sePagaDesde\":\"$afc\"", "\"paidBy\":\"Skandia\""))
        assertEquals(HttpStatusCode.UnprocessableEntity, conTercero.status)
        assertEquals(CUOTA_DESDE_CON_TERCERO, conTercero.bodyAsText())
        val conLibranza = put(terminos("\"sePagaDesde\":\"$afc\"", "\"payrollDeduction\":true"))
        assertEquals(HttpStatusCode.UnprocessableEntity, conLibranza.status)
        assertEquals(CUOTA_DESDE_CON_LIBRANZA, conLibranza.bodyAsText())
        assertNull(guardada())
    }

    @Test
    fun `no apunta a una cuenta ajena ni a una deuda`() = testApplication {
        application { testModule() }
        val ajena = put(terminos("\"sePagaDesde\":\"$ahorrosDelOtro\""))
        assertEquals(HttpStatusCode.UnprocessableEntity, ajena.status)
        assertEquals(CUOTA_DESDE_SIN_CUENTA, ajena.bodyAsText())
        val deuda = put(terminos("\"sePagaDesde\":\"$tarjeta\""))
        assertEquals(HttpStatusCode.UnprocessableEntity, deuda.status)
        assertEquals(CUOTA_DESDE_UNA_DEUDA, deuda.bodyAsText())
        assertNull(guardada())
    }

    // ── El caso del dueño ──────────────────────────────────────────────────────

    private fun checklist() = runBlocking {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        checklistDelPeriodo(
            upcoming = proximosPagos(uid, hoy),
            ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
            periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes),
            settings = ajustes,
        )
    }

    private fun servidor() = runBlocking {
        val reglasDeCredito = loadCreditRulePairs(uid).map { it.first }
        transaction {
            val periodo = ajustesDelPeriodoSinSuspender(uid)
            val dias = diasDelPeriodo(hoy, periodo)
            disponibleDelServidor(
                uid = uid, hoy = hoy, periodo = periodo,
                monthStart = appDateToEpochMillis(dias.start),
                monthEnd = appDateToEpochMillis(dias.endInclusive.plusDays(1)),
                voidedIds = emptySet(), reglasDeCredito = reglasDeCredito,
            )
        }
    }

    /** Lo que queda del período sin el gasto variable: lo que el server manda para «Te quedan» menos los fijos del cliente. */
    private fun loQueQueda(): Long {
        val s = servidor()
        return s.plata.total - s.pagosDeDeudaFueraDelChecklist - fijosDelPeriodo(checklist()) - s.gastoVariablePorDia.values.sum()
    }

    @Test
    fun `con la AFC la cuota sale en Pagos, se anota desde la AFC, se tilda y Te quedan no se mueve`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.OK, put(terminos("\"sePagaDesde\":\"$afc\"")).status)

        // `/api/payments/upcoming` la trae, marcada.
        val proximos = client.get("/api/payments/upcoming") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, proximos.status)
        val laCuota = json.decodeFromString(ListSerializer(UpcomingPayment.serializer()), proximos.bodyAsText())
            .single { it.rule.id == "$CREDIT_RULE_PREFIX$hipoteca" }
        assertEquals(true, laCuota.rule.saleDeFueraDeTuPlata)
        assertEquals(afc, laCuota.rule.sePagaDesde)

        // Pendiente: en la lista, marcada, y fuera de los fijos y de «Falta por pagar».
        val antes = checklist()
        val fila = antes.single { it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" }
        assertTrue(fila.saleDeFueraDeTuPlata)
        assertEquals(afc, fila.sePagaDesde)
        assertEquals(0L, fijosDelPeriodo(antes))
        assertEquals(0L, faltaPorPagar(antes))
        val quedaAntes = loQueQueda()
        assertEquals(5_000_000L, quedaAntes, "Te quedan habla de Bancolombia, no de la AFC")

        // Anotar el pago desde la AFC: el pago de cuota de siempre, dos patas.
        val pago = client.post("/api/payments/installment") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"fromAccountId":"$afc","debtAccountId":"$hipoteca","amount":$cuota,
                    "timestamp":${appDateToEpochMillis(LocalDate.of(2026, 10, 18)) + 12 * 3_600_000L},
                    "transferId":"t_cuota","fromEventId":"ev_afc","toEventId":"ev_hip"}""",
            )
        }
        assertTrue(pago.status.value in 200..201, pago.bodyAsText())
        val patas = transaction {
            Events.selectAll().where { (Events.userId eq uid) and (Events.transferId eq "t_cuota") }.associate { it[Events.id] to it }
        }
        assertEquals(cuota, patas.getValue("ev_afc")[Events.amount], "sale de la AFC la cuota entera")
        assertEquals(CUOTA_CATEGORY, patas.getValue("ev_afc")[Events.category])
        assertEquals("INCOME", patas.getValue("ev_hip")[Events.type], "y la deuda baja, como con cualquier cuota")
        assertTrue(patas.getValue("ev_hip")[Events.amount] in 1..cuota)

        // Se tilda (`/api/payments/occurrences` la da por pagada) y sigue sin sumar.
        val ocurrencia = runBlocking { ocurrenciasDelPeriodo(uid, hoy) }.single { it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" }
        assertTrue(ocurrencia.occurred, "el pago de octubre salda la cuota de octubre")
        val despues = checklist()
        assertTrue(despues.single { it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" }.hecho)
        assertEquals(0L, fijosDelPeriodo(despues))
        // El server no se lo reconoce a los fijos: queda en «otros pagos de deuda» y lo compensa
        // `pagadoDesdeFuera` (la plata salió de afuera).
        assertEquals(cuota, servidor().pagosDeDeudaFueraDelChecklist)
        assertEquals(quedaAntes, loQueQueda(), "anotar el pago desde la AFC no cambia «Te quedan»")
    }

    @Test
    fun `con una cuenta de Tu plata, la cuota cuenta como siempre`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.OK, put(terminos("\"sePagaDesde\":\"$ahorros\"")).status)
        val lista = checklist()
        val fila = lista.single { it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" }
        assertEquals(false, fila.saleDeFueraDeTuPlata)
        assertEquals(ahorros, fila.sePagaDesde)
        assertEquals(cuota, fijosDelPeriodo(lista))
        assertEquals(cuota, faltaPorPagar(lista))
        assertEquals(5_000_000L - cuota, loQueQueda())
    }
}
