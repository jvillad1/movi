package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `POST /api/credits/{id}/payroll-deduction` — la cuota que paga otro (la nómina, Skandia, un
 * familiar).
 *
 * **El segundo camino que escribe una cuota, y el que la ola de la cuota-por-capital se saltó.**
 * `POST /api/payments/installment` baja la deuda solo por el capital desde el PR #127; esta ruta
 * seguía bajándola por la cuota ENTERA: la libranza ·4818 perdía $6.040.259 de deuda por mes cuando
 * solo $2.394.248 abonan a capital. Este proyecto ya aplicó un arreglo a 1 de 3 endpoints una vez;
 * estas pruebas fijan que los dos caminos reparten igual, y que los dos aceptan el interés real.
 *
 * No había una sola prueba de esta ruta. Mismo harness que `PagoDeCuotaRoutesTest`.
 */
class CuotaAjenaRoutesTest {

    private val testSecret = "test-secret-for-cuota-ajena-routes-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-libranza"
    private val libranza = "acc-libranza-4818"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuota_ajena_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Goals, Subscriptions, CardPaymentDismissals, Cards, Credits, SmsMessages,
                RecurringRules, VoidEvents, Events, StatementImports, Budgets, Accounts, Users, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                SmsMessages, Credits, Cards, CardPaymentDismissals, Subscriptions, Goals, CategoryPrefs,
            )
            Users.insert { it[id] = duenoId; it[email] = "dueno@libranza.test"; it[name] = duenoId; it[passwordHash] = "hash" }
            Accounts.insert {
                it[id] = libranza
                it[userId] = duenoId
                it[name] = "Libranza 4818"
                it[type] = "LOAN"
                it[currency] = "COP"
            }
            // La deuda de la libranza como apertura: $262.386.162, la cifra de `DesgloseDeCuotaTest`.
            Events.insert {
                it[id] = "ev-apertura-libranza"
                it[userId] = duenoId
                it[accountId] = libranza
                it[type] = "EXPENSE"
                it[amount] = 262_386_162L
                it[currency] = "COP"
                it[category] = "Saldo inicial"
                it[description] = "Deuda inicial"
                it[timestamp] = 1_788_000_000_000L
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
    }

    /** Las condiciones de la libranza: $6.040.259 de cuota al 18,01 % E.A., descontada de la nómina. */
    private fun condiciones(rateEa: Double = 18.01, seguro: Long? = null) = transaction {
        Credits.insert {
            it[accountId] = libranza
            it[userId] = duenoId
            it[bank] = "Bancolombia"
            it[principal] = 283_000_000L
            it[Credits.rateEa] = rateEa
            it[termMonths] = 84
            it[installment] = 6_040_259L
            it[dayOfMonth] = 30
            it[startDate] = "2025-01-30"
            it[payrollDeduction] = true
            it[insuranceMonthly] = seguro
        }
    }

    private fun token(userId: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId).withClaim("email", "$userId@libranza.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    /** Como lo manda el botón de la app: sin cuerpo y sin `Content-Type`. */
    private suspend fun ApplicationTestBuilder.registrarSinCuerpo() =
        client.post("/api/credits/$libranza/payroll-deduction") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
        }

    private suspend fun ApplicationTestBuilder.registrarCon(json: String) =
        client.post("/api/credits/$libranza/payroll-deduction") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private fun filasDeLaCuota() = transaction {
        Events.selectAll()
            .where { (Events.accountId eq libranza) and (Events.id neq "ev-apertura-libranza") }
            .map { Triple(it[Events.amount], it[Events.noAmortiza], it[Events.description]) }
    }

    private fun deuda(): Long = transaction {
        Events.selectAll().where { Events.accountId eq libranza }.sumOf { fila ->
            val monto = fila[Events.amount]
            if (fila[Events.type] == "EXPENSE") monto else -monto
        }
    }

    private fun campoNum(json: String, campo: String): Long? =
        Regex("\"$campo\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toLong()

    @Test
    fun `sin cuerpo, la cuota de la nomina baja la deuda solo por el capital estimado`() = testApplication {
        // Antes bajaba $6.040.259. Solo $2.394.248 abonan a capital; $3.646.011 son interés.
        condiciones()
        wireApp()
        val res = registrarSinCuerpo()
        val texto = res.bodyAsText()

        assertEquals(HttpStatusCode.OK, res.status, texto)
        val (monto, noAmortiza, concepto) = filasDeLaCuota().single()
        assertEquals(2_394_248L, monto, "la fila vale el capital, no la cuota")
        assertEquals(3_646_011L, noAmortiza, "y guarda lo que no amortizó, como la otra ruta")
        assertEquals(262_386_162L - 2_394_248L, deuda())
        assertTrue("abono a capital de una cuota de $6.040.259" in concepto, "el concepto lo dice: $concepto")
        // Lo que la app espeja en el teléfono es exactamente la fila que se escribió.
        assertEquals(2_394_248L, campoNum(texto, "amount"), texto)
        assertEquals(3_646_011L, campoNum(texto, "noAmortiza"), texto)
    }

    @Test
    fun `el seguro declarado tampoco baja la deuda por esta puerta`() = testApplication {
        condiciones(seguro = 100_000L)
        wireApp()
        registrarSinCuerpo()

        val (monto, noAmortiza, _) = filasDeLaCuota().single()
        assertEquals(2_394_248L - 100_000L, monto)
        assertEquals(3_646_011L + 100_000L, noAmortiza)
    }

    @Test
    fun `con el interes real en el cuerpo, se usa ese en vez de la estimacion`() = testApplication {
        condiciones()
        wireApp()
        val res = registrarCon("""{"interesReal":3900000}""")

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val (monto, noAmortiza, _) = filasDeLaCuota().single()
        assertEquals(6_040_259L - 3_900_000L, monto)
        assertEquals(3_900_000L, noAmortiza)
    }

    @Test
    fun `un interes real que no cabe en la cuota se rechaza y no escribe nada`() = testApplication {
        condiciones()
        wireApp()
        val res = registrarCon("""{"interesReal":6100000}""")
        val texto = res.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status, texto)
        assertTrue("6.100.000" in texto && "6.040.259" in texto, texto)
        assertEquals(0, filasDeLaCuota().size)
        assertEquals(262_386_162L, deuda())
    }

    @Test
    fun `un cuerpo JSON vacio es lo mismo que sin cuerpo`() = testApplication {
        condiciones()
        wireApp()
        registrarCon("{}")

        assertEquals(2_394_248L, filasDeLaCuota().single().first)
    }

    @Test
    fun `sin tasa la deuda baja por la cuota entera y no se inventa nada`() = testApplication {
        // Mismo criterio que la otra ruta: sin tasa no se puede separar, y no se inventa un
        // interés plausible. `noAmortiza` queda en NULL, que es lo que un par simétrico es.
        condiciones(rateEa = 0.0)
        wireApp()
        registrarSinCuerpo()

        val (monto, noAmortiza, concepto) = filasDeLaCuota().single()
        assertEquals(6_040_259L, monto)
        assertNull(noAmortiza)
        assertEquals("Cuota descontada de la nómina", concepto)
    }

    // ── De qué mes es la cuota, y volver a registrarla ────────────────────────────────────────

    @Test
    fun `la cuota que se registra es la ultima que ya vencio, no la del mes de hoy`() {
        val dia30 = 30
        assertEquals("2026-08", mesDeLaCuotaVencida(java.time.LocalDate.of(2026, 9, 2), dia30))
        assertEquals("2026-09", mesDeLaCuotaVencida(java.time.LocalDate.of(2026, 9, 30), dia30))
        // Febrero no tiene 30: la cuota vence el último día y ese día ya cuenta.
        assertEquals("2026-02", mesDeLaCuotaVencida(java.time.LocalDate.of(2026, 2, 28), dia30))
    }

    @Test
    fun `registrar dos veces el mismo mes avisa sin tocar la deuda, y una anulada se puede rehacer`() = testApplication {
        condiciones()
        wireApp()
        assertEquals(HttpStatusCode.OK, registrarSinCuerpo().status)
        val tras1 = deuda()

        val otraVez = registrarSinCuerpo()
        assertEquals(HttpStatusCode.Conflict, otraVez.status)
        assertTrue("Ya está registrada" in otraVez.bodyAsText())
        assertEquals(tras1, deuda(), "el segundo toque no baja la deuda")

        // Anular la del mes y volver a registrarla: antes el id seguía ocupado y daba 500.
        val id = transaction {
            Events.selectAll().where { (Events.accountId eq libranza) and (Events.id neq "ev-apertura-libranza") }.single()[Events.id]
        }
        transaction {
            VoidEvents.insert {
                it[VoidEvents.id] = "void-$id"
                it[VoidEvents.userId] = duenoId
                it[VoidEvents.originalEventId] = id
                it[VoidEvents.timestamp] = System.currentTimeMillis()
            }
        }
        assertEquals(HttpStatusCode.OK, registrarSinCuerpo().status)
    }

    @Test
    fun `un mes futuro o mal escrito se rechaza`() = testApplication {
        condiciones()
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, registrarCon("""{"periodo":"2999-01"}""").status)
        assertEquals(HttpStatusCode.BadRequest, registrarCon("""{"periodo":"agosto"}""").status)
        assertTrue(filasDeLaCuota().isEmpty())
    }

    /**
     * **Dos cuotas atrasadas registradas el mismo día no se comen el interés entre sí.** Antes las dos
     * filas quedaban fechadas hoy y la segunda veía el interés de la primera como «ya cobrado este
     * mes»: su capital salía con la cuota entera y la deuda bajaba ~$3,6M de más.
     */
    @Test
    fun `registrar hoy dos meses atrasados cobra el interes de cada cuota`() = testApplication {
        condiciones()
        wireApp()
        val hoy = com.jvillada.movi.server.time.AppClock.today()
        val haceDos = java.time.YearMonth.from(hoy).minusMonths(2).toString()
        val haceUno = java.time.YearMonth.from(hoy).minusMonths(1).toString()
        assertEquals(HttpStatusCode.OK, registrarCon("""{"periodo":"$haceDos"}""").status)
        val res = registrarCon("""{"periodo":"$haceUno"}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val filas = filasDeLaCuota()
        assertEquals(2, filas.size)
        filas.forEach { (capital, noAmortiza, _) ->
            assertTrue(capital < 6_040_259L, "cada cuota abona solo su capital, no la cuota entera: $capital")
            assertTrue((noAmortiza ?: 0L) > 3_000_000L, "y cobra su propio interés: $noAmortiza")
        }
        // Y cada fila queda en el mes de su cuota, no en el de hoy.
        val meses = transaction {
            Events.selectAll().where { (Events.accountId eq libranza) and (Events.id neq "ev-apertura-libranza") }
                .map { java.time.YearMonth.from(com.jvillada.movi.server.time.epochMillisToAppDate(it[Events.timestamp])).toString() }
                .toSet()
        }
        assertEquals(setOf(haceDos, haceUno), meses)
    }
}
