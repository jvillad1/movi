package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.ai.textoDeLaCaja
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
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoVariableDeUnPeriodo
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La caja proyectada en el server (Ola 4)
 *
 * La aritmética está en `:core` (`CajaProyectadaTest`). Acá: el supuesto del gasto del día a día
 * (de qué períodos sale, y que un período que Movi no cubrió entero no cuenta), que cada dueño ve
 * solo lo suyo, que Movi AI arma la caja con la misma lista del período, y que su texto dice de
 * dónde sale cada cifra. Ninguna prueba llama a Anthropic.
 *
 * Las fechas salen del reloj de verdad (corte 1 = mes de calendario): la prueba corre cualquier día.
 */
class CajaProyectadaRoutesTest {

    private val testSecret = "test-secret-for-caja-proyectada-min-32-chars"
    private val duenoId = "user-dueno-caja"
    private val otroId = "user-otro-caja"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = AppClock.today()
    private val inicioDelMesPasado: LocalDate = hoy.withDayOfMonth(1).minusMonths(1)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:caja_proyectada_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                KnownDestinations, OccurrenceRejections, RecurringOccurrences, RecurringRules, Cards, Credits,
                VoidEvents, Events, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations,
            )
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@caja.test"; it[name] = uid; it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[id] = "acc_$uid"; it[userId] = uid; it[name] = "Bancolombia"; it[type] = "SAVINGS"; it[balance] = 0L
                }
            }
        }
    }

    private fun anotar(uid: String, id: String, monto: Long, fecha: LocalDate, categoria: String, tipo: String = "EXPENSE") = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = "acc_$uid"
            it[type] = tipo
            it[amount] = monto
            it[currency] = "COP"
            it[category] = categoria
            it[description] = id
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@caja.test")
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

    private suspend fun ApplicationTestBuilder.gasto(uid: String): GastoDelDiaADia {
        val res = client.get("/api/caja-proyectada/gasto-del-dia-a-dia") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(GastoDelDiaADia.serializer(), res.bodyAsText())
    }

    /** El dueño lleva Movi desde antes del mes pasado: ese mes cerrado cuenta entero. */
    private fun duenoConUnMesCerrado() {
        anotar(duenoId, "apertura", 10_000_000L, inicioDelMesPasado.minusDays(3), OPENING_CATEGORY, tipo = "INCOME")
        anotar(duenoId, "mercado", 600_000L, inicioDelMesPasado.plusDays(4), "Comida")
        anotar(duenoId, "taxi", 300_000L, inicioDelMesPasado.plusDays(9), "Transporte")
    }

    @Test
    fun `el gasto del dia a dia es el del mes cerrado, sobre sus dias`() = testApplication {
        application { testModule() }
        duenoConUnMesCerrado()
        val gasto = gasto(duenoId)
        val dias = inicioDelMesPasado.lengthOfMonth()
        assertEquals(900_000L / dias, gasto.porDia)
        assertEquals(1, gasto.periodos.size)
        assertEquals(dias, gasto.periodos.single().dias)
        assertEquals(900_000L, gasto.periodos.single().total)
    }

    @Test
    fun `un mes que Movi no cubrio entero no cuenta`() = testApplication {
        application { testModule() }
        // Empezó a usar Movi a mitad del mes pasado: dos semanas anotadas de cuatro no son su gasto.
        anotar(duenoId, "apertura", 10_000_000L, inicioDelMesPasado.plusDays(10), OPENING_CATEGORY, tipo = "INCOME")
        anotar(duenoId, "mercado", 600_000L, inicioDelMesPasado.plusDays(12), "Comida")
        assertNull(gasto(duenoId).porDia)
    }

    @Test
    fun `cada duenio ve solo su gasto`() = testApplication {
        application { testModule() }
        duenoConUnMesCerrado()
        assertNull(gasto(otroId).porDia, "el otro no tiene ni un movimiento: no hereda el supuesto del dueño")
        assertTrue(gasto(duenoId).porDia != null)
    }

    @Test
    fun `sin token no hay caja`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/caja-proyectada/gasto-del-dia-a-dia").status)
    }

    /** Movi AI arma la caja con la lista del período: un pago de hoy, pendiente, sale hoy. */
    @Test
    fun `la caja de movi ai resta lo pendiente de la lista del periodo`() {
        anotar(duenoId, "apertura", 5_000_000L, hoy, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            RecurringRules.insert {
                it[id] = "rr_gym"; it[userId] = duenoId; it[name] = "Gimnasio"; it[category] = "Salud"
                it[amount] = 180_000L; it[dayOfMonth] = hoy.dayOfMonth; it[type] = "EXPENSE"
            }
            // La del otro dueño no puede colarse en la caja de este.
            RecurringRules.insert {
                it[id] = "rr_otro"; it[userId] = otroId; it[name] = "Arriendo del otro"; it[category] = "Vivienda"
                it[amount] = 2_000_000L; it[dayOfMonth] = hoy.dayOfMonth; it[type] = "EXPENSE"
            }
        }
        val d = runBlocking { cajaProyectadaDe(duenoId, hoy) }
        val caja = assertNotNull(d.caja)
        assertEquals(5_000_000L, caja.tuPlataHoy)
        assertEquals(hoy.toString(), caja.dias.first().fecha)
        assertEquals(hoy.withDayOfMonth(hoy.lengthOfMonth()).toString(), caja.alCierre.fecha)
        assertEquals(listOf("Gimnasio"), caja.dias.first().movimientos.map { it.nombre })
        assertEquals(4_820_000L, caja.alCierre.saldo, "sin período cerrado no hay gasto del día a día que restar")
        assertNull(d.gasto.porDia)
    }

    @Test
    fun `el texto para movi ai dice de donde sale cada cifra y lo que falta`() {
        val texto = textoDeLaCaja(
            CajaDelDueno(
                caja = CajaProyectada(
                    tuPlataHoy = 1_000_000L,
                    gastoDiario = 100_000L,
                    dias = listOf(
                        DiaDeLaCaja("2026-10-02", 1_000_000L),
                        DiaDeLaCaja("2026-10-17", -3_201_123L, listOf(MovimientoDeLaCaja("Cuota Vehiculo 8761", -4_101_123L))),
                    ),
                    sinContar = listOf("Pago tarjeta AMEX: falta el pago mínimo"),
                ),
                pagos = emptyList(),
                gasto = GastoDelDiaADia(100_000L, listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31))),
            ),
        )
        assertTrue("Tu plata hoy: 1000000" in texto, texto)
        assertTrue("SUPUESTO: gasta como siempre, 100000 por día" in texto, texto)
        assertTrue("2026-09: 3100000 en 31 días" in texto, texto)
        assertTrue("NO ENTRAN" in texto && "AMEX: falta el pago mínimo" in texto, texto)
        assertTrue("PRIMER DÍA EN NEGATIVO: 2026-10-17, queda en -3201123 por Cuota Vehiculo 8761 (4101123)" in texto, texto)
    }
}
