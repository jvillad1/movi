package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `GET /api/budgets/propuestas`: lo que Presupuestos vacío le propone a quien todavía no tiene
 * ninguno, sacado del gasto del período ANTERIOR. La regla pura (tope redondeado, orden, tope de
 * cuatro) la prueba `PropuestaDePresupuestoTest` en :core; acá, que la lectura cuente lo mismo que
 * «Gastos»: el período del dueño, sin anulados, sin traspasos ni pagos de tarjeta, sin «Por
 * confirmar», sin cuota, solo pesos y solo lo suyo.
 *
 * Mismo arnés que DashboardRoutesTest: H2 en memoria (compat PostgreSQL), JWT local.
 */
class PropuestasDePresupuestoRoutesTest {

    private val testSecret = "test-secret-for-propuestas-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-propuestas"
    private val otherUserId = "user-b-propuestas"

    private val savings = "acc-savings-prop"
    private val card = "acc-card-prop"

    /** El corte del dueño: 25. Con corte 1 el período es el mes civil y el caso no distingue nada. */
    private val ajustes = PeriodSettings(cutoffDay = 25)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:propuestas_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Budgets, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Budgets)
            listOf(userId to "a@propuestas.test", otherUserId to "b@propuestas.test").forEach { (id, mail) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = mail
                    it[name] = id
                    it[passwordHash] = "hash"
                }
            }
            // Los dos con corte 25, para que «adentro()» caiga en el período anterior de ambos.
            Users.update({ Users.id eq userId }) { it[periodCutoffDay] = 25 }
            Users.update({ Users.id eq otherUserId }) { it[periodCutoffDay] = 25 }
            account(savings, "SAVINGS", userId)
            account(card, "CREDIT_CARD", userId)
            account("acc-b-prop", "SAVINGS", otherUserId)
        }
    }

    private fun account(id: String, type: String, uid: String) {
        Accounts.insert {
            it[Accounts.id] = id
            it[Accounts.userId] = uid
            it[name] = id
            it[Accounts.type] = type
            it[balance] = 0L
        }
    }

    private fun event(
        id: String,
        amount: Long,
        category: String,
        timestamp: Long,
        accountId: String = savings,
        type: String = "EXPENSE",
        currency: String = "COP",
        uid: String = userId,
        estado: String = "RECONCILED",
    ) = transaction {
        Events.insert {
            it[Events.reconciliationStatus] = estado
            it[Events.id] = id
            it[Events.userId] = uid
            it[Events.accountId] = accountId
            it[Events.type] = type
            it[Events.amount] = amount
            it[Events.currency] = currency
            it[Events.category] = category
            it[Events.description] = category
            it[Events.timestamp] = timestamp
        }
    }

    private fun anular(eventId: String) = transaction {
        VoidEvents.insert {
            it[id] = "void_$eventId"
            it[VoidEvents.userId] = this@PropuestasDePresupuestoRoutesTest.userId
            it[originalEventId] = eventId
            it[timestamp] = System.currentTimeMillis()
        }
    }

    private fun presupuesto(category: String) = transaction {
        Budgets.insert {
            it[Budgets.userId] = this@PropuestasDePresupuestoRoutesTest.userId
            it[Budgets.category] = category
            it[monthlyLimit] = 500_000L
        }
    }

    private fun tokenFor(uid: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", uid)
            .withClaim("email", "$uid@propuestas.test")
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    private suspend fun ApplicationTestBuilder.propuestas(uid: String = userId): List<PropuestaDePresupuesto> {
        val res = client.get("/api/budgets/propuestas") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return Json { ignoreUnknownKeys = true }.decodeFromString(res.bodyAsText())
    }

    /** La ventana del período anterior al de hoy, con el corte 25 (y lo que tenga declarado [s]). */
    private fun ventanaAnterior(s: PeriodSettings = ajustes): LongRange =
        ventanaDe(periodoAnterior(periodoDe(AppClock.now().toInstant().toEpochMilli(), s)), s)

    /** Un instante bien adentro del período anterior: una semana después de su arranque. */
    private fun adentro(): Long = ventanaAnterior().first + 7L * 24 * 60 * 60 * 1000

    @Test
    fun `usa el periodo anterior con el corte del dueno y dice sus fechas`() = testApplication {
        val v = ventanaAnterior()
        event("e-primer-ms", 40_000L, "Fútbol", timestamp = v.first)
        event("e-ultimo-ms", 30_000L, "Fútbol", timestamp = v.last)
        event("e-antes", 999_000L, "Fútbol", timestamp = v.first - 1)
        event("e-periodo-en-curso", 888_000L, "Fútbol", timestamp = v.last + 1)

        wireApp()
        val lista = propuestas()

        assertEquals(1, lista.size)
        assertEquals("Fútbol", lista[0].category)
        assertEquals(70_000.0, lista[0].gastado)
        assertEquals(70_000.0, lista[0].amount)
        assertEquals(epochMillisToAppDateString(v.first), lista[0].desde)
        assertEquals(epochMillisToAppDateString(v.last), lista[0].hasta)
    }

    /**
     * El caso del dueño: corte 25, pero el período pasado arrancó un día antes porque el sueldo
     * entró el 24 y él lo declaró. El gasto de ese día es del período pasado.
     */
    @Test
    fun `respeta el periodo anterior que arranco otro dia`() = testApplication {
        val anterior = periodoAnterior(periodoDe(AppClock.now().toInstant().toEpochMilli(), ajustes))
        val inicioNatural = ventanaDe(anterior, ajustes).first
        val unDiaAntes = java.time.Instant.ofEpochMilli(inicioNatural).atZone(AppClock.zone).toLocalDate().minusDays(1)
        transaction {
            Users.update({ Users.id eq userId }) { it[periodStarts] = """{"${anterior.prefijo}":"$unDiaAntes"}""" }
        }
        event("e-dia-ganado", 50_000L, "Comida", timestamp = inicioNatural - 1)

        wireApp()
        val lista = propuestas()

        assertEquals(listOf("Comida"), lista.map { it.category })
        assertEquals(unDiaAntes.toString(), lista[0].desde)
    }

    @Test
    fun `deja fuera anulados, traspasos, pagos de tarjeta, cuota, Por confirmar, otra moneda y lo que ya tiene presupuesto`() =
        testApplication {
            val t = adentro()
            event("e-comida", 120_000L, "Comida", timestamp = t)
            event("e-anulado", 900_000L, "Comida", timestamp = t)
            anular("e-anulado")
            event("e-traspaso", 5_000_000L, TRANSFER_CATEGORY, timestamp = t)
            event("e-pago-tarjeta", 2_000_000L, CARD_PAYMENT_CATEGORY, timestamp = t)
            // El abono a la tarjeta llega como INCOME en la cuenta de la tarjeta: no es gasto.
            event("e-abono-tarjeta", 2_000_000L, "Otros", timestamp = t, accountId = card, type = "INCOME")
            event("e-cuota", 900_000L, CUOTA_CATEGORY, timestamp = t)
            event("e-sin-confirmar", 800_000L, "Hija", timestamp = t, estado = "UNCONFIRMED")
            event("e-dolares", 700_000L, "Viajes", timestamp = t, currency = "USD")
            event("e-mercado", 600_000L, "Mercado", timestamp = t)
            presupuesto("Mercado")
            // Una compra con tarjeta SÍ es gasto: es el momento en que se consumió.
            event("e-compra-tarjeta", 99_001L, "Ropa", timestamp = t, accountId = card)

            wireApp()
            val lista = propuestas()

            assertEquals(listOf("Comida", "Ropa"), lista.map { it.category })
            assertEquals(listOf(120_000.0, 99_001.0), lista.map { it.gastado })
            assertEquals(listOf(120_000.0, 100_000.0), lista.map { it.amount })
        }

    @Test
    fun `propone las cuatro de mas gasto, en orden, con el tope redondeado`() = testApplication {
        val t = adentro()
        event("e1", 1_020_000L, "Mercado", timestamp = t)
        event("e2", 99_001L, "Fútbol", timestamp = t)
        event("e3", 400_000L, "Hija", timestamp = t)
        event("e4", 820_000L, "Comida", timestamp = t)
        event("e5", 50_000L, "Ropa", timestamp = t)

        wireApp()
        val lista = propuestas()

        assertEquals(listOf("Mercado", "Comida", "Hija", "Fútbol"), lista.map { it.category })
        assertEquals(listOf(1_050_000.0, 820_000.0, 400_000.0, 100_000.0), lista.map { it.amount })
    }

    @Test
    fun `sin gasto en el periodo anterior no propone nada, y lo de otro usuario no se mezcla`() = testApplication {
        event("e-otro", 500_000L, "Comida", timestamp = adentro(), accountId = "acc-b-prop", uid = otherUserId)

        wireApp()

        assertEquals(emptyList(), propuestas())
        assertEquals(listOf("Comida"), propuestas(otherUserId).map { it.category })
    }
}
