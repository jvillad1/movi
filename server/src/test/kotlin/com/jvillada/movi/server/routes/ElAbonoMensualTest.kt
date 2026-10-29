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
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.cajaProyectada
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.fijosDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.client.request.delete
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 63 · el abono mensual a una deuda: un recordatorio que se tilda con su traspaso
 *
 * «Abono extra a Vehículo 4083 · $500.000», el día 15. La regla lleva `haciaDeuda`; su ocurrencia es
 * el traspaso hacia esa deuda (la única regla que acepta uno), Movi la tilda sola a ±5 %, y pendiente
 * no resta de los fijos, de «Falta por pagar» ni de la caja. Hecha, el traspaso resta una sola vez,
 * como «otro pago de deuda».
 */
class ElAbonoMensualTest {

    private val testSecret = "test-secret-for-el-abono-mensual-min-32-chars"
    private val uid = "user-abono"
    private val json = Json { ignoreUnknownKeys = true }
    private val ahorros = "acc_ahorros_abono"
    private val vehiculo = "acc_v4083"
    private val abono = 500_000L

    /** El 20 de octubre: el abono del 15 ya venció. */
    private val hoy: LocalDate = LocalDate.of(2026, 10, 20)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:abono_mensual_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert {
                it[id] = uid; it[email] = "abono@movi.test"; it[name] = "Dueño"; it[passwordHash] = "h"
                it[periodCutoffDay] = 1
            }
            Accounts.insert {
                it[id] = ahorros; it[userId] = uid; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
            Accounts.insert {
                it[id] = vehiculo; it[userId] = uid; it[name] = "Vehículo 4083"; it[type] = "LOAN"; it[currency] = "COP"
            }
        }
    }

    // ── Armado ──────────────────────────────────────────────────────────────────

    private fun reglaDelAbono(dia: Int = 15, id: String = "rr_abono") = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = uid; it[name] = "Abono extra a Vehículo 4083"
            it[category] = TRANSFER_CATEGORY; it[amount] = abono; it[dayOfMonth] = dia; it[type] = "EXPENSE"
            it[accountId] = ahorros; it[haciaDeuda] = vehiculo
        }
    }

    private fun pata(id: String, cuenta: String, tipo: String, monto: Long, fecha: LocalDate, categoria: String, traspaso: String) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = "Abono extraordinario"
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"; it[transferId] = traspaso
        }
    }

    /** Un traspaso de Ahorros a la deuda: la pata que sale es `sale-<t>`. */
    private fun traspaso(t: String, monto: Long, fecha: LocalDate, hacia: String = vehiculo, categoria: String = TRANSFER_CATEGORY) {
        pata("sale-$t", ahorros, "EXPENSE", monto, fecha, categoria, t)
        pata("entra-$t", hacia, "INCOME", monto, fecha, categoria, t)
    }

    private fun estados() = transaction { estadosDeLasOcurrenciasReales(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) }

    private fun regla(): RecurringRule = transaction { RecurringRules.selectAll().single().toRule() }

    private fun token() = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "abono@movi.test")
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

    private suspend fun ApplicationTestBuilder.enviar(ruta: String, cuerpo: String, metodo: String = "POST"): HttpResponse {
        val armar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return when (metodo) {
            "PUT" -> client.put(ruta, armar)
            "DELETE" -> client.delete(ruta, armar)
            else -> client.post(ruta, armar)
        }
    }

    private fun reglaJson(vararg extra: String) =
        """{"id":"","name":"Abono extra a Vehículo 4083","category":"Traspaso","amount":500000,"dayOfMonth":15,"type":"EXPENSE"
            ${if (extra.isEmpty()) "" else "," + extra.joinToString(",")}}"""

    // ── Alta, edición y borrado ─────────────────────────────────────────────────

    @Test
    fun `la regla del abono se crea con su deuda, un APK viejo no se la quita y se borra como las demas`() = testApplication {
        application { testModule() }
        val alta = enviar("/api/recurring-rules", reglaJson("\"accountId\":\"$ahorros\"", "\"haciaDeuda\":\"$vehiculo\""))
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        assertEquals(vehiculo, json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText()).haciaDeuda)
        val id = regla().id
        assertEquals(vehiculo, regla().haciaDeuda)

        // El APK instalado no conoce el campo: corrige el monto y la deuda se queda.
        val viejo = enviar("/api/recurring-rules/$id", reglaJson().replace("500000", "600000"), metodo = "PUT")
        assertEquals(HttpStatusCode.OK, viejo.status, viejo.bodyAsText())
        assertEquals(600_000L, regla().amount)
        assertEquals(vehiculo, regla().haciaDeuda, "editar desde un cliente viejo le quitó la deuda")

        // Un abono no se debita solo, aunque lo pidan.
        enviar("/api/recurring-rules/$id", reglaJson("\"accountId\":\"$ahorros\"", "\"seDebitaSolo\":true"), metodo = "PUT")
        assertEquals(false, regla().seDebitaSolo)

        // Una cuenta que no es deuda no es un destino de abono: se guarda sin deuda.
        val aAhorros = enviar("/api/recurring-rules/$id", reglaJson("\"haciaDeuda\":\"$ahorros\""), metodo = "PUT")
        assertEquals(HttpStatusCode.OK, aAhorros.status)
        assertNull(regla().haciaDeuda)

        // Borrarla se lleva sus sellos.
        enviar("/api/recurring-rules/$id", reglaJson("\"haciaDeuda\":\"$vehiculo\""), metodo = "PUT")
        transaction {
            RecurringOccurrences.insert {
                it[userId] = uid; it[ruleId] = id; it[period] = "2026-10"; it[eventId] = null; it[confirmedAt] = 1L
            }
        }
        assertEquals(HttpStatusCode.NoContent, enviar("/api/recurring-rules/$id", "", metodo = "DELETE").status)
        assertEquals(0L, transaction { RecurringRules.selectAll().count() + RecurringOccurrences.selectAll().count() })
    }

    // ── El sello con un traspaso ────────────────────────────────────────────────

    @Test
    fun `el abono se sella con el traspaso hacia su deuda, y solo el abono acepta un traspaso`() = testApplication {
        application { testModule() }
        val hoyReal = AppClock.today()
        reglaDelAbono(dia = 1)
        transaction {
            RecurringRules.insert {
                it[id] = "rr_arriendo"; it[userId] = uid; it[name] = "Arriendo"; it[category] = "Vivienda"
                it[amount] = abono; it[dayOfMonth] = 1; it[type] = "EXPENSE"; it[accountId] = ahorros
            }
        }
        traspaso("t1", 420_000L, hoyReal)
        traspaso("cuota", abono, hoyReal, categoria = CUOTA_CATEGORY)
        val periodo = periodOf(hoyReal)
        fun sellar(regla: String, evento: String) =
            runBlocking { enviar("/api/recurring-rules/$regla/occurrence", """{"period":"$periodo","eventId":"$evento"}""") }

        assertEquals(HttpStatusCode.BadRequest, sellar("rr_arriendo", "sale-t1").status, "un traspaso no es el arriendo")
        assertEquals(HttpStatusCode.BadRequest, sellar("rr_abono", "sale-cuota").status, "la pata de una cuota no es el abono")
        assertEquals(HttpStatusCode.BadRequest, sellar("rr_abono", "entra-t1").status, "se sella con la plata que salió")
        // A otro monto también: lo confirma el dueño (el ±5 % es solo para tildarlo sola).
        val bien = sellar("rr_abono", "sale-t1")
        assertEquals(HttpStatusCode.Created, bien.status, bien.bodyAsText())
        val sello = transaction { RecurringOccurrences.selectAll().single() }
        assertEquals("sale-t1", sello[RecurringOccurrences.eventId])
    }

    // ── El tilde automático ─────────────────────────────────────────────────────

    @Test
    fun `Movi lo tilda sola con el traspaso a mas o menos 5 por ciento, y a otro monto pregunta`() {
        reglaDelAbono()
        traspaso("t1", 480_000L, LocalDate.of(2026, 10, 16))
        val fila = estados().single { it.ruleId == "rr_abono" }
        assertTrue(fila.occurred)
        assertTrue(fila.automatica)
        assertEquals("sale-t1", fila.eventId)
        assertEquals(480_000L, fila.montoDelPago)
    }

    @Test
    fun `a otro monto o a otra deuda no se tilda`() {
        reglaDelAbono()
        transaction {
            Accounts.insert {
                it[id] = "acc_libre"; it[userId] = uid; it[name] = "Libre inversión"; it[type] = "LOAN"; it[currency] = "COP"
            }
        }
        traspaso("corto", 300_000L, LocalDate.of(2026, 10, 16))
        traspaso("otra", abono, LocalDate.of(2026, 10, 17), hacia = "acc_libre")
        val fila = estados().single { it.ruleId == "rr_abono" }
        assertFalse(fila.occurred)
        assertEquals(listOf("sale-corto"), fila.candidates.map { it.id }, "el de otro monto se propone; el de otra deuda, no")
    }

    // ── No es un fijo ───────────────────────────────────────────────────────────

    private fun checklist() = runBlocking {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        checklistDelPeriodo(
            upcoming = proximosPagos(uid, hoy),
            ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
            periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes),
            settings = ajustes,
        )
    }

    private fun servidor() = transaction {
        val periodo = ajustesDelPeriodoSinSuspender(uid)
        val dias = diasDelPeriodo(hoy, periodo)
        disponibleDelServidor(
            uid = uid, hoy = hoy, periodo = periodo,
            monthStart = appDateToEpochMillis(dias.start),
            monthEnd = appDateToEpochMillis(dias.endInclusive.plusDays(1)),
            voidedIds = emptySet(), reglasDeCredito = emptyList(),
        )
    }

    @Test
    fun `pendiente no suma en los fijos ni en la caja, y Te quedan no baja`() {
        reglaDelAbono(dia = 25)
        val filas = checklist()
        val fila = filas.single { it.ruleId == "rr_abono" }
        assertTrue(fila.esAbono)
        assertFalse(fila.pagado)
        assertEquals(0L, fijosDelPeriodo(filas))
        assertEquals(0L, faltaPorPagar(filas))
        val caja = cajaProyectada(5_000_000L, filas, gastoDiario = null, hoy = hoy.toString(), ultimoDia = "2026-10-31")!!
        assertEquals(5_000_000L, caja.alCierre.saldo, "la caja no lo da por hecho")
        assertEquals(0L, servidor().pagosDeDeudaFueraDelChecklist, "sin traspaso, nada sale de «Te quedan»")
    }

    @Test
    fun `hecho, el traspaso resta una sola vez como otro pago de deuda`() {
        reglaDelAbono()
        traspaso("t1", abono, LocalDate.of(2026, 10, 16))
        val filas = checklist()
        val fila = filas.single { it.ruleId == "rr_abono" }
        assertTrue(fila.pagado, "el traspaso lo tildó")
        assertEquals(0L, fijosDelPeriodo(filas), "no es un fijo ni hecho")
        assertEquals(abono, servidor().pagosDeDeudaFueraDelChecklist, "el traspaso baja «Te quedan» una vez")
        assertEquals(0L, servidor().gastoVariablePorDia.values.sum())
    }
}
