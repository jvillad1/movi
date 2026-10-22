package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.patrimonioDe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
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
import kotlin.test.assertTrue

/**
 * # El patrimonio en el tiempo (`GET /api/patrimonio/historia`)
 *
 * Lo que cuidan estas pruebas, con un «hoy» fijo (el 20 de octubre de 2026) salvo la de la ruta:
 *
 * 1. Que cada cierre de mes sea **exactamente** `patrimonioDe` sobre las cuentas con el saldo que
 *    tenían a esa hora: dos cuentas de dinero, una deuda (que resta) y un movimiento anulado (que no
 *    cuenta en ningún mes).
 * 2. Que un mes que cerró antes del primer movimiento **no aparezca**: no se dibuja como cero.
 * 3. Que el punto de hoy sea el patrimonio de hoy, y que un bien cuente con su valor en todos.
 * 4. Que un dueño no vea la historia de otro.
 */
class HistoriaDelPatrimonioTest {

    private val testSecret = "test-secret-for-historia-del-patrimonio-min-32"
    private val duenoId = "user-dueno-historia"
    private val otroId = "user-otro-historia"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = LocalDate.of(2026, 10, 20)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:historia_patrimonio_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@historia.test"; it[name] = uid; it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun cuenta(uid: String, id: String, tipo: AccountType, bien: Long? = null) = transaction {
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = uid
            it[name] = id
            it[type] = tipo.name
            it[balance] = 0L
            if (bien != null) {
                it[assetKind] = "INMUEBLE"
                it[assetValue] = bien
            }
        }
    }

    private fun anotar(uid: String, id: String, cuenta: String, tipo: String, monto: Long, fecha: LocalDate) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[currency] = "COP"
            it[category] = "Varios"
            it[description] = id
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun anular(uid: String, eventoId: String) = transaction {
        VoidEvents.insert {
            it[id] = "void_$eventoId"; it[userId] = uid; it[originalEventId] = eventoId; it[timestamp] = 0L
        }
    }

    private fun historia(uid: String, hoy: LocalDate = this.hoy): HistoriaDelPatrimonio =
        transaction { historiaDelPatrimonioSinSuspender(uid, hoy, usdToCop = 0.0, conDolares = false) }

    private fun acc(id: String, tipo: AccountType, saldo: Long) = Account(id = id, name = id, type = tipo, balance = saldo)

    /** El dueño de las pruebas: empieza en julio, con ahorros, una billetera y una tarjeta. */
    private fun duenoConTresMeses() {
        cuenta(duenoId, "ahorros", AccountType.SAVINGS)
        cuenta(duenoId, "billetera", AccountType.CASH)
        cuenta(duenoId, "tarjeta", AccountType.CREDIT_CARD)
        anotar(duenoId, "sueldo-jul", "ahorros", "INCOME", 5_000_000L, LocalDate.of(2026, 7, 10))
        anotar(duenoId, "efectivo-jul", "billetera", "INCOME", 300_000L, LocalDate.of(2026, 7, 12))
        anotar(duenoId, "compra-ago", "tarjeta", "EXPENSE", 800_000L, LocalDate.of(2026, 8, 3))
        anotar(duenoId, "mercado-ago", "ahorros", "EXPENSE", 450_000L, LocalDate.of(2026, 8, 20))
        // Un gasto de $9.999.999 que se anuló: no puede aparecer en ningún mes.
        anotar(duenoId, "anulado-ago", "ahorros", "EXPENSE", 9_999_999L, LocalDate.of(2026, 8, 25))
        anular(duenoId, "anulado-ago")
        anotar(duenoId, "pago-tarjeta-sep", "tarjeta", "INCOME", 800_000L, LocalDate.of(2026, 9, 5))
        anotar(duenoId, "pago-desde-ahorros-sep", "ahorros", "EXPENSE", 800_000L, LocalDate.of(2026, 9, 5))
        anotar(duenoId, "sueldo-oct", "ahorros", "INCOME", 5_000_000L, LocalDate.of(2026, 10, 10))
    }

    @Test
    fun `cada cierre de mes es patrimonioDe con los saldos de ese dia, sin el anulado y con la deuda restando`() {
        duenoConTresMeses()
        val h = historia(duenoId)

        // Julio, agosto, septiembre y hoy: los meses antes de julio no tienen datos.
        assertEquals(listOf("2026-07", "2026-08", "2026-09", "2026-10"), h.puntos.map { it.mes })
        assertEquals(listOf("2026-07-31", "2026-08-31", "2026-09-30", "2026-10-20"), h.puntos.map { it.fecha })
        assertEquals(listOf(false, false, false, true), h.puntos.map { it.esHoy })

        val julio = patrimonioDe(
            listOf(acc("ahorros", AccountType.SAVINGS, 5_000_000L), acc("billetera", AccountType.CASH, 300_000L), acc("tarjeta", AccountType.CREDIT_CARD, 0L)),
        ).neto
        val agosto = patrimonioDe(
            listOf(acc("ahorros", AccountType.SAVINGS, 4_550_000L), acc("billetera", AccountType.CASH, 300_000L), acc("tarjeta", AccountType.CREDIT_CARD, 800_000L)),
        ).neto
        val septiembre = patrimonioDe(
            listOf(acc("ahorros", AccountType.SAVINGS, 3_750_000L), acc("billetera", AccountType.CASH, 300_000L), acc("tarjeta", AccountType.CREDIT_CARD, 0L)),
        ).neto
        val hoyNeto = patrimonioDe(
            listOf(acc("ahorros", AccountType.SAVINGS, 8_750_000L), acc("billetera", AccountType.CASH, 300_000L), acc("tarjeta", AccountType.CREDIT_CARD, 0L)),
        ).neto
        assertEquals(listOf(julio, agosto, septiembre, hoyNeto), h.puntos.map { it.neto })
        // Las cifras a mano, para que la prueba no dependa solo de la función que prueba.
        assertEquals(listOf(5_300_000L, 4_050_000L, 4_050_000L, 9_050_000L), h.puntos.map { it.neto })
        assertTrue(!h.conLaTrmDeHoy)
    }

    @Test
    fun `un mes que cerro antes del primer movimiento no aparece, aunque entre en los doce`() {
        cuenta(duenoId, "ahorros", AccountType.SAVINGS)
        // El primer movimiento es del 31 de agosto a mediodía: julio cerró antes, agosto no.
        anotar(duenoId, "primero", "ahorros", "INCOME", 1_000_000L, LocalDate.of(2026, 8, 31))
        val h = historia(duenoId)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), h.puntos.map { it.mes })
        assertTrue(h.puntos.none { it.neto == 0L }, "Ningún mes se dibuja como cero: ${h.puntos}")
    }

    @Test
    fun `sin movimientos no hay historia`() {
        cuenta(duenoId, "ahorros", AccountType.SAVINGS)
        assertTrue(historia(duenoId).puntos.isEmpty())
    }

    @Test
    fun `con mas de doce meses de historia se muestran los ultimos doce cierres y hoy`() {
        cuenta(duenoId, "ahorros", AccountType.SAVINGS)
        anotar(duenoId, "viejo", "ahorros", "INCOME", 1_000_000L, LocalDate.of(2024, 1, 15))
        val h = historia(duenoId)
        assertEquals(13, h.puntos.size)
        assertEquals("2025-10", h.puntos.first().mes)
        assertEquals("2026-09", h.puntos[11].mes)
        assertTrue(h.puntos.all { it.neto == 1_000_000L })
    }

    @Test
    fun `un bien cuenta con su valor de hoy en todos los puntos`() {
        duenoConTresMeses()
        cuenta(duenoId, "casa", AccountType.INVESTMENT, bien = 1_000_000_000L)
        val conCasa = historia(duenoId).puntos.map { it.neto }
        assertEquals(listOf(5_300_000L, 4_050_000L, 4_050_000L, 9_050_000L).map { it + 1_000_000_000L }, conCasa)
    }

    @Test
    fun `cada duenio ve solo su historia`() {
        duenoConTresMeses()
        cuenta(otroId, "ahorros-otro", AccountType.SAVINGS)
        anotar(otroId, "herencia", "ahorros-otro", "INCOME", 777_000_000L, LocalDate.of(2025, 1, 10))

        testApplication {
            application { testModule() }
            fun token(uid: String) = JWT.create()
                .withIssuer("movi").withAudience("movi-client")
                .withClaim("userId", uid).withClaim("email", "$uid@historia.test")
                .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
                .sign(Algorithm.HMAC256(testSecret))

            val delDueno = client.get("/api/patrimonio/historia") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
            assertEquals(HttpStatusCode.OK, delDueno.status)
            val h = json.decodeFromString(HistoriaDelPatrimonio.serializer(), delDueno.bodyAsText())
            assertTrue(h.puntos.isNotEmpty())
            assertTrue(h.puntos.none { it.neto >= 777_000_000L }, "La herencia del otro no puede aparecer: ${h.puntos}")
            // Con el reloj de verdad, el último punto es hoy y es el patrimonio de hoy del dueño.
            assertEquals(AppClock.today().toString(), h.puntos.last().fecha)
            assertEquals(9_050_000L, h.puntos.last().neto)

            val delOtro = client.get("/api/patrimonio/historia") { header(HttpHeaders.Authorization, "Bearer ${token(otroId)}") }
            val o = json.decodeFromString(HistoriaDelPatrimonio.serializer(), delOtro.bodyAsText())
            assertTrue(o.puntos.all { it.neto == 777_000_000L }, "El otro ve solo lo suyo: ${o.puntos}")

            val sinToken = client.get("/api/patrimonio/historia")
            assertEquals(HttpStatusCode.Unauthorized, sinToken.status)
        }
    }

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
