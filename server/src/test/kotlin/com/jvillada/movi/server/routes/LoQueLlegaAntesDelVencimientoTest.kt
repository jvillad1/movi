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
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RechazarOcurrenciaRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import io.ktor.server.testing.testApplication
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * **Lo que llega antes de su vencimiento** (1-oct-2026). La recarga de Glim vence el 2 y entró el 1
 * por $640.000 (la regla dice $621.788): Plan decía «llega mañana» y no proponía nada, porque
 * `resolverOcurrencias` no miraba movimientos de un vencimiento futuro. Ahora un concluyente se
 * empareja y un candidato se pregunta; sin nada parecido, sigue sin preguntar.
 *
 * Todos los vencimientos de este archivo caen MAÑANA (si mañana es otro mes, las pruebas no aplican).
 */
class LoQueLlegaAntesDelVencimientoTest {

    private val testSecret = "test-secret-para-emparejar-solo-minimo-32-ch"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val uid = "user-empareja-solo"
    private val email = "empareja@solo.test"
    private val bancolombia = "acc-bancolombia"
    private val glim = "acc-glim"

    /** La fecha civil de la app (Bogotá), la misma que usan los endpoints. */
    private val hoy: LocalDate = AppClock.today()

    private val diaDelVencimiento = hoy.plusDays(1).dayOfMonth

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:llega_antes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
                it[Users.email] = this@LoQueLlegaAntesDelVencimientoTest.email
                it[name] = "Dueño"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = bancolombia
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
            Accounts.insert {
                it[id] = glim
                it[userId] = uid
                it[name] = "Glim"
                it[type] = "SAVINGS"
            }
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private fun regla(
        id: String,
        nombre: String,
        categoria: String,
        monto: Long,
        tipo: String = "EXPENSE",
        cuenta: String? = bancolombia,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = diaDelVencimiento
            it[type] = tipo
            it[accountId] = cuenta
        }
    }

    private fun movimiento(
        id: String,
        nota: String,
        categoria: String,
        monto: Long,
        cuenta: String = bancolombia,
        tipo: String = "EXPENSE",
        fecha: LocalDate = hoy,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[Events.category] = categoria
            it[description] = nota
            it[timestamp] = appDateToEpochMillis(fecha)
        }
    }

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

    private suspend fun HttpClient.ocurrencias(): List<OccurrenceState> {
        val resp = get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${mintToken()}") }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body()
    }

    private suspend fun HttpClient.rechazar(ruleId: String, eventId: String): HttpStatusCode =
        post("/api/recurring-rules/$ruleId/occurrence/rechazo") {
            header(HttpHeaders.Authorization, "Bearer ${mintToken()}")
            contentType(ContentType.Application.Json)
            setBody(RechazarOcurrenciaRequest(eventId))
        }.status

    private fun mananaEsEsteMes() = assumeTrue(hoy.plusDays(1).monthValue == hoy.monthValue)

    @Test
    fun `un pago concluyente antes del vencimiento se empareja solo`() = testApplication {
        mananaEsEsteMes()
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)

        val estado = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertTrue(estado.occurred, "Pagó un día antes: ya está pagado")
        assertEquals("ev-mercado", estado.eventId)
        assertTrue(estado.automatica)
    }

    /** El caso real: misma categoría y cuenta, otro monto. No es concluyente, pero se pregunta. */
    @Test
    fun `la recarga de Glim que llego un dia antes se propone`() = testApplication {
        mananaEsEsteMes()
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-glim", nombre = "Recarga Glim", categoria = "Salario", monto = 621_788L, tipo = "INCOME", cuenta = glim)
        movimiento(
            id = "ev-glim", nota = "Recarga de beneficios · Mercado Libre Colombia Ltda", categoria = "Salario",
            monto = 640_000L, cuenta = glim, tipo = "INCOME",
        )

        val estado = assertNotNull(
            client.ocurrencias().firstOrNull { it.ruleId == "rr-glim" },
            "Ya pasó algo que se le parece: la fila tiene que preguntar, no decir «llega mañana»",
        )
        assertFalse(estado.occurred, "El monto no coincide: Movi pregunta, no decide")
        assertEquals(listOf("ev-glim"), estado.candidates.map { it.id })
    }

    @Test
    fun `sin nada parecido, lo que vence manana sigue sin preguntar`() = testApplication {
        mananaEsEsteMes()
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-otro", nota = "Cine", categoria = "Ocio", monto = 50_000L)

        assertTrue(
            client.ocurrencias().none { it.ruleId == "rr-mercado" },
            "Preguntar por algo que no ha pasado es ruido",
        )
    }
}
