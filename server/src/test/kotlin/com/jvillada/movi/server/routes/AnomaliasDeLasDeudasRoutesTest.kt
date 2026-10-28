package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.cuotaMasCercana
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los avisos de las deudas, en el server
 *
 * Que la ruta los arme con el último período cerrado y las condiciones del crédito, que **solo los
 * reciba quien los pide** (`?conDeudas=1`: un APK instalado no conoce esos tipos y perdería la lista
 * entera), y que el «Está bien» de siempre los saque. Fechas desde el reloj de verdad, corte 1.
 */
class AnomaliasDeLasDeudasRoutesTest {

    private val testSecret = "test-secret-for-anomalias-deudas-min-32-chars"
    private val duenoId = "user-dueno-anomalias-deudas"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = AppClock.today()
    private val inicioDelMesPasado: LocalDate = hoy.withDayOfMonth(1).minusMonths(1)
    private var n = 0

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:anomalias_deudas;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(OccurrenceRejections, RecurringOccurrences, RecurringRules, AnomaliasDescartadas, Credits, Subscriptions, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Subscriptions, Credits, AnomaliasDescartadas, RecurringRules, RecurringOccurrences, OccurrenceRejections)
            Users.insert { it[id] = duenoId; it[email] = "$duenoId@deudas.test"; it[name] = duenoId; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = "lib"; it[userId] = duenoId; it[name] = "Libranza 4818"; it[type] = "LOAN" }
            Accounts.insert { it[id] = "c9695"; it[userId] = duenoId; it[name] = "Libre inversión 9695"; it[type] = "LOAN" }
        }
        // La libranza: abierta hace meses y subida $5,4 M por un ajuste el mes pasado.
        anotar("lib", 257_000_000L, inicioDelMesPasado.minusMonths(2), OPENING_CATEGORY)
        anotar("lib", 5_386_162L, inicioDelMesPasado.plusDays(10), ADJUSTMENT_CATEGORY)
        // El 9695: con el interés del extracto de la cuota que viene, muy por encima del estimado.
        anotar("c9695", 40_710_555L, inicioDelMesPasado.minusMonths(2), OPENING_CATEGORY)
        val cuota = cuotaMasCercana(hoy.year, hoy.monthValue, hoy.dayOfMonth, 15)
        transaction {
            Credits.insert {
                it[accountId] = "c9695"; it[userId] = duenoId; it[bank] = "Bancolombia"; it[principal] = 80_000_000L
                it[rateEa] = 11.27; it[termMonths] = 132; it[installment] = 1_204_064L; it[dayOfMonth] = 15
                it[startDate] = "2021-06-15"; it[interesReal] = 594_567L; it[interesRealCuota] = cuota
            }
        }
    }

    private fun anotar(cuenta: String, monto: Long, fecha: LocalDate, categoria: String) = transaction {
        Events.insert {
            it[id] = "ev_${n++}"; it[userId] = duenoId; it[accountId] = cuenta; it[type] = "EXPENSE"; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = "Movimiento"
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@deudas.test")
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

    private suspend fun ApplicationTestBuilder.anomalias(conDeudas: Boolean): List<Anomalia> {
        val res = client.get("/api/anomalias" + if (conDeudas) "?conDeudas=1" else "") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(Anomalia.serializer()), res.bodyAsText())
    }

    @Test
    fun `quien las pide recibe la deuda que crece y el interes lejos del estimado`() = testApplication {
        application { testModule() }
        val tipos = anomalias(conDeudas = true).associateBy { it.tipo }
        val crece = tipos.getValue(TipoDeAnomalia.DEUDA_QUE_CRECE)
        assertTrue(crece.huella.startsWith("deuda:lib:"), crece.huella)
        assertEquals(listOf(5_386_162L), crece.evidencia.map { it.monto })
        val interes = tipos.getValue(TipoDeAnomalia.INTERES_LEJOS_DEL_ESTIMADO)
        assertEquals(594_567L, interes.evidencia.first().monto)
    }

    @Test
    fun `un cliente viejo no las recibe`() = testApplication {
        application { testModule() }
        val tipos = anomalias(conDeudas = false).map { it.tipo }
        assertTrue(TipoDeAnomalia.DEUDA_QUE_CRECE !in tipos && TipoDeAnomalia.INTERES_LEJOS_DEL_ESTIMADO !in tipos, tipos.toString())
    }

    @Test
    fun `esta bien las saca como a las demas`() = testApplication {
        application { testModule() }
        anomalias(conDeudas = true).forEach { a ->
            val res = client.post("/api/anomalias/descartar") {
                header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
                contentType(ContentType.Application.Json)
                setBody("""{"huella":"${a.huella}"}""")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
        assertEquals(emptyList(), anomalias(conDeudas = true))
    }
}
