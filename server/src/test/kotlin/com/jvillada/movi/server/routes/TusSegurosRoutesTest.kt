package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.SegurosQuePagas
import com.jvillada.movi.shared.model.problemaDelEvento
import com.jvillada.movi.shared.model.tramoDeLoQuePagasEnSeguros
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
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
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
import kotlin.test.assertTrue

/**
 * # Vuelta 132 · `GET /api/seguros`, «No es un seguro», «Sí» y «Me interesa», del lado del server
 *
 * La ruta arma el resultado en cada lectura con las fuentes de verdad (el crédito con su seguro, los
 * cargos de la tarjeta, una EPS de Sura que no es concluyente) y lo que dijo el dueño vive en
 * `anomalias_descartadas`. El evento de interés lleva solo cuántos y el tramo. Sin la ruta, cada prueba
 * de HTTP da 404 y `segurosDe` no compila.
 */
class TusSegurosRoutesTest {

    private val testSecret = "test-secret-for-tus-seguros-min-32-chars-ok"
    private val dueno = "user-dueno-seguros"
    private val otro = "user-otro-seguros"
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Observabilidad.olvidarLoRegistrado()
        Database.connect(
            url = "jdbc:h2:mem:tus_seguros_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(UsoDeLaApp, AnomaliasDescartadas, Subscriptions, RecurringRules, Credits, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Credits, RecurringRules, Subscriptions, AnomaliasDescartadas, UsoDeLaApp)
            listOf(dueno, otro).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@seguros.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            cuenta("acc_vehiculo", dueno, "Vehículo 4083", "LOAN")
            cuenta("acc_master", dueno, "Master Black", "CREDIT_CARD")
            cuenta("acc_ahorros", dueno, "Ahorros 8133", "SAVINGS")
            cuenta("acc_otro", otro, "Ahorros del otro", "SAVINGS")
            evento(dueno, "ev_apertura", "acc_vehiculo", "EXPENSE", 90_000_000L, "Deuda inicial", "Saldo inicial", diasAtras = 200)
            Credits.insert {
                it[accountId] = "acc_vehiculo"; it[userId] = dueno; it[bank] = "Occidente"; it[principal] = 120_000_000L
                it[rateEa] = 17.4; it[termMonths] = 72; it[installment] = 4_101_122L; it[dayOfMonth] = 3
                it[startDate] = "2025-01-03"; it[remindMe] = true; it[insuranceMonthly] = 89_100L
            }
            // Seis meses del seguro de la tarjeta, el primero de cada mes.
            (0 until 6).forEach { i ->
                val fecha = AppClock.today().withDayOfMonth(1).minusMonths(i.toLong())
                evento(dueno, "ev_seg_$i", "acc_master", "EXPENSE", 32_000L, "Otros", "SEGURO VIDA DEUDOR TC", fecha)
            }
            evento(dueno, "ev_eps", "acc_ahorros", "EXPENSE", 380_000L, "Salud", "PAGO SURA EPS", diasAtras = 4)
            // Lo del otro: un seguro que el dueño nunca tiene que ver.
            evento(otro, "ev_otro_seg", "acc_otro", "EXPENSE", 777_000L, "Otros", "SEGURO ALLIANZ HOGAR", diasAtras = 3)
        }
    }

    private fun cuenta(id: String, uid: String, nombre: String, tipo: String) = Accounts.insert {
        it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = tipo; it[currency] = "COP"; it[balance] = 0L
    }

    private fun evento(uid: String, id: String, cuenta: String, tipo: String, monto: Long, categoria: String, descripcion: String, diasAtras: Int) =
        evento(uid, id, cuenta, tipo, monto, categoria, descripcion, AppClock.today().minusDays(diasAtras.toLong()))

    private fun evento(uid: String, id: String, cuenta: String, tipo: String, monto: Long, categoria: String, descripcion: String, fecha: LocalDate) =
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }

    private suspend fun ApplicationTestBuilder.leer(uid: String = dueno, pantalla: Boolean = false): SegurosQuePagas {
        val res = client.get("/api/seguros" + if (pantalla) "?pantalla=1" else "") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(SegurosQuePagas.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.responder(ruta: String, clave: String, uid: String = dueno, meses: Int? = null) =
        client.post("/api/seguros/$ruta") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(if (meses == null) """{"clave":"$clave"}""" else """{"clave":"$clave","meses":$meses}""")
        }

    private fun huellasDeSeguros(): Set<String> = transaction {
        AnomaliasDescartadas.selectAll().map { it[AnomaliasDescartadas.huella] }.toSet()
    }

    private fun eventosDeUso(nombre: String): List<Map<String, String>> = transaction {
        UsoDeLaApp.selectAll().where { UsoDeLaApp.nombre eq nombre }.map { fila ->
            (Json.parseToJsonElement(fila[UsoDeLaApp.propiedades]) as JsonObject).mapValues { it.value.jsonPrimitive.content }
        }
    }

    @Test
    fun `sin sesion contesta 401`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/seguros").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/seguros/no-es").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/seguros/si-es").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/seguros/cada-cuanto").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/seguros/me-interesa").status)
    }

    @Test
    fun `el duenio ve su credito, su tarjeta y la EPS por confirmar`() = testApplication {
        application { testModule() }
        val r = leer()
        assertEquals(setOf("credito:acc_vehiculo", "acc_master:seguro_vida_deudor_tc"), r.seguros.map { it.clave }.toSet())
        assertEquals(89_100L + 32_000L, r.alMes)
        assertEquals(1, r.porConfirmar.size)
        assertFalse(r.meInteresa)
    }

    @Test
    fun `lo de otro usuario no se ve`() = testApplication {
        application { testModule() }
        val delDueno = leer()
        assertTrue(delDueno.seguros.none { it.cuentaId == "acc_otro" } && delDueno.porConfirmar.none { it.cuentaId == "acc_otro" })
        val delOtro = leer(otro)
        assertEquals(listOf("acc_otro"), delOtro.seguros.map { it.cuentaId })
        // Un solo cobro sin señal de cada cuánto: no se inventa que es mensual (C1).
        assertTrue(delOtro.seguros.single().pagadoUnaVez)
        assertEquals(0L, delOtro.alMes)
        assertEquals(777_000L, delOtro.enPagosDeUnaVez)
        // El otro no puede descartar un seguro del dueño: para él, esa clave no existe.
        assertEquals(HttpStatusCode.NotFound, responder("no-es", "credito:acc_vehiculo", uid = otro).status)
        assertEquals(2, leer().seguros.size)
    }

    @Test
    fun `no es un seguro lo saca, y es idempotente`() = testApplication {
        application { testModule() }
        repeat(2) { assertEquals(HttpStatusCode.OK, responder("no-es", "acc_master:seguro_vida_deudor_tc").status) }
        val r = leer()
        assertEquals(listOf("credito:acc_vehiculo"), r.seguros.map { it.clave })
        assertEquals(89_100L, r.alMes)
        assertEquals(1L, transaction { AnomaliasDescartadas.selectAll().count() })
    }

    @Test
    fun `si es un seguro lo cuenta, es idempotente, y la ultima respuesta gana`() = testApplication {
        application { testModule() }
        val eps = leer().porConfirmar.single().clave
        repeat(2) { assertEquals(HttpStatusCode.OK, responder("si-es", eps).status) }
        val confirmado = leer()
        assertTrue(confirmado.porConfirmar.isEmpty())
        assertTrue(confirmado.seguros.single { it.clave == eps }.confirmado)
        // Confirmado pero pagado una vez: va aparte, no «al mes».
        assertEquals(89_100L + 32_000L, confirmado.alMes)
        assertEquals(380_000L, confirmado.enPagosDeUnaVez)
        assertEquals(1L, transaction { AnomaliasDescartadas.selectAll().count() })
        // Después «No es»: sale de todo y queda una sola huella.
        assertEquals(HttpStatusCode.OK, responder("no-es", eps).status)
        val descartado = leer()
        assertTrue(descartado.seguros.none { it.clave == eps } && descartado.porConfirmar.isEmpty())
        assertEquals(1L, transaction { AnomaliasDescartadas.selectAll().count() })
    }

    @Test
    fun `una clave que no existe o vacia no guarda nada`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.NotFound, responder("no-es", "acc_ahorros:panaderia").status)
        assertEquals(HttpStatusCode.BadRequest, responder("si-es", "").status)
        assertEquals(0L, transaction { AnomaliasDescartadas.selectAll().count() })
    }

    @Test
    fun `me interesa se registra una vez, sin montos ni comercios`() = testApplication {
        application { testModule() }
        repeat(2) {
            val res = client.post("/api/seguros/me-interesa") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
        Observabilidad.olvidarLoRegistrado()
        client.post("/api/seguros/me-interesa") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }
        val eventos = eventosDeUso(EventoDeUso.SEGUROS_ME_INTERESA.name)
        assertEquals(1, eventos.size)
        val datos = eventos.single()
        assertEquals(setOf(ClaveDeUso.CUANTOS.clave, ClaveDeUso.TRAMO.clave), datos.keys)
        assertEquals("2", datos[ClaveDeUso.CUANTOS.clave])
        assertEquals(tramoDeLoQuePagasEnSeguros(121_100L), datos[ClaveDeUso.TRAMO.clave])
        val todo = transaction { UsoDeLaApp.selectAll().joinToString(" ") { it[UsoDeLaApp.propiedades] + it[UsoDeLaApp.ruta] } }
        listOf("121100", "121.100", "89100", "32000", "Master", "SEGURO", "Vehículo", "Sura", "EPS").forEach {
            assertFalse(it in todo, "«$it» se coló: $todo")
        }
        assertTrue(problemaDelEvento(EventoDeUso.SEGUROS_ME_INTERESA.name, datos) == null)
        assertTrue(leer().meInteresa)
        // Es un estado del dueño, no la telemetría: aunque el registro de uso se borre, se recuerda.
        transaction { UsoDeLaApp.deleteAll() }
        assertTrue(leer().meInteresa)
        assertTrue("seguros-me-interesa" in huellasDeSeguros())
    }

    @Test
    fun `cada cuanto vuelve mensual o anual un pago de una vez, gana la ultima y la respuesta trae el resultado`() = testApplication {
        application { testModule() }
        val eps = leer().porConfirmar.single().clave
        responder("si-es", eps)
        val mensual = json.decodeFromString(SegurosQuePagas.serializer(), responder("cada-cuanto", eps, meses = 1).bodyAsText())
        assertEquals(89_100L + 32_000L + 380_000L, mensual.alMes, "la respuesta ya trae el total nuevo")
        assertEquals(0L, mensual.enPagosDeUnaVez)
        repeat(2) { assertEquals(HttpStatusCode.OK, responder("cada-cuanto", eps, meses = 12).status) }
        val anual = leer()
        assertEquals(89_100L + 32_000L + 31_667L, anual.alMes)
        assertEquals(setOf("seguro-si:$eps", "seguro-cada:$eps:12"), huellasDeSeguros())
        assertEquals(HttpStatusCode.BadRequest, responder("cada-cuanto", eps, meses = 7).status)
        assertEquals(HttpStatusCode.BadRequest, responder("cada-cuanto", eps).status)
        assertEquals(HttpStatusCode.NotFound, responder("cada-cuanto", "acc_ahorros:nada", meses = 1).status)
    }

    @Test
    fun `al abrir la pantalla queda un SEGUROS_VISTOS por dia, y la fila de Tu no lo escribe`() = testApplication {
        application { testModule() }
        leer()
        assertTrue(eventosDeUso(EventoDeUso.SEGUROS_VISTOS.name).isEmpty())
        leer(pantalla = true)
        leer(pantalla = true)
        val vistos = eventosDeUso(EventoDeUso.SEGUROS_VISTOS.name)
        assertEquals(1, vistos.size)
        assertEquals(setOf(ClaveDeUso.CUANTOS.clave, ClaveDeUso.TRAMO.clave), vistos.single().keys)
    }

    @Test
    fun `con mandar datos de uso apagado no se escribe el evento, pero la eleccion si`() = testApplication {
        application { testModule() }
        transaction { Users.update({ Users.id eq dueno }) { it[mandarUso] = false } }
        client.post("/api/seguros/me-interesa") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }
        leer(pantalla = true)
        assertTrue(transaction { UsoDeLaApp.selectAll().count() } == 0L)
        // La elección sí se guarda: no es telemetría.
        assertTrue(leer().meInteresa)
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@seguros.test")
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
}
