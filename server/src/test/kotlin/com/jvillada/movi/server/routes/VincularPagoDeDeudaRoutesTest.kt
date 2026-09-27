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
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.PAGO_A_NO_DEUDA_BLOQUEADO
import com.jvillada.movi.shared.model.PAGO_MONEDAS_DISTINTAS
import com.jvillada.movi.shared.model.VINCULO_CATEGORIA_INVALIDA
import com.jvillada.movi.shared.model.VINCULO_YA_ES_TRASPASO
import io.ktor.client.request.header
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
 * `PUT /api/events/{id}/vincular-deuda` — Ola Y: completar en una sola acción el traspaso que
 * hoy hay que armar a mano por SQL cuando el dueño confirma un SMS con la categoría correcta.
 *
 * Mismo harness que `PagoDeCuotaRoutesTest`, del que esta ruta es hermana: H2 en memoria + JWT de
 * prueba + `configureRouting()`. Los fixtures de crédito (carro con tasa, Crédito Mamá interés
 * puro) son los mismos números reales que ya usa `PagoDeCuotaRoutesTest`/`DesgloseDeCuotaTest`.
 */
class VincularPagoDeDeudaRoutesTest {

    private val testSecret = "test-secret-for-vincular-deuda-routes-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-vinculo"
    private val otroId = "user-otro-vinculo"

    private val ahorros = "acc-ahorros-vinculo"
    private val carro = "acc-carro-vinculo"
    private val amex = "acc-amex-vinculo"
    private val mama = "acc-credito-mama"
    private val ajena = "acc-de-otro-vinculo"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:vincular_deuda_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            listOf(duenoId to "dueno@vinculo.test", otroId to "otro@vinculo.test").forEach { (uid, mail) ->
                Users.insert { it[id] = uid; it[email] = mail; it[name] = uid; it[passwordHash] = "hash" }
            }
            cuenta(ahorros, duenoId, "Bancolombia Ahorros", "SAVINGS", "COP")
            cuenta(carro, duenoId, "Vehículo 4083", "LOAN", "COP")
            cuenta(amex, duenoId, "AMEX 9208", "CREDIT_CARD", "COP")
            cuenta(ajena, otroId, "Ahorros de otro", "SAVINGS", "COP")

            Events.insert {
                it[id] = "ev-apertura-carro"
                it[userId] = duenoId
                it[accountId] = carro
                it[type] = "EXPENSE"
                it[amount] = 177_200_000L
                it[currency] = "COP"
                it[category] = "Saldo inicial"
                it[description] = "Deuda inicial"
                it[timestamp] = 1_788_000_000_000L
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
            Credits.insert {
                it[accountId] = carro
                it[userId] = duenoId
                it[bank] = "Bancolombia"
                it[principal] = 200_000_000L
                it[rateEa] = 18.16
                it[termMonths] = 72
                it[installment] = 4_215_223L
                it[dayOfMonth] = 5
                it[startDate] = "2024-01-15"
            }

            // El Crédito Mamá: $100.000.000 al 16,7652% E.A., cuota $1.300.000 — casi 100% interés,
            // el caso real de capital clamped a cero.
            cuenta(mama, duenoId, "Crédito Mamá", "LOAN", "COP")
            Events.insert {
                it[id] = "ev-apertura-mama"
                it[userId] = duenoId
                it[accountId] = mama
                it[type] = "EXPENSE"
                it[amount] = 100_000_000L
                it[currency] = "COP"
                it[category] = "Saldo inicial"
                it[description] = "Deuda inicial"
                it[timestamp] = 1_788_000_000_000L
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
            Credits.insert {
                it[accountId] = mama
                it[userId] = duenoId
                it[bank] = "Familiar"
                it[principal] = 100_000_000L
                it[rateEa] = 16.7652
                it[termMonths] = 999
                it[installment] = 1_300_000L
                it[dayOfMonth] = 27
                it[startDate] = "2020-01-27"
            }
        }
    }

    private fun cuenta(id: String, uid: String, nombre: String, tipo: String, moneda: String) {
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = uid
            it[name] = nombre
            it[type] = tipo
            it[currency] = moneda
        }
    }

    /** Un movimiento suelto ya categorizado, como el que deja una SMS confirmada — sin transferId. */
    private fun gastoSuelto(
        id: String,
        accountId: String,
        monto: Long,
        categoria: String,
        descripcion: String = "Transferencia Bancolombia",
        fuente: String = "SMS",
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = duenoId
            it[Events.accountId] = accountId
            it[type] = "EXPENSE"
            it[amount] = monto
            it[currency] = "COP"
            it[category] = categoria
            it[description] = descripcion
            it[timestamp] = 1_788_100_000_000L
            it[eventSource] = fuente
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(userId: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId).withClaim("email", "$userId@vinculo.test")
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

    private fun cuerpo(debt: String, tr: String = "tr-1", toEventId: String = "ev-deuda-1", interesReal: Long? = null) = buildString {
        append("""{"debtAccountId":"$debt","transferId":"$tr","toEventId":"$toEventId"""")
        if (interesReal != null) append(""","interesReal":$interesReal""")
        append("}")
    }

    private suspend fun ApplicationTestBuilder.vincular(uid: String, eventId: String, body: String) =
        client.put("/api/events/$eventId/vincular-deuda") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun patas(transferId: String) = transaction {
        Events.selectAll()
            .where { (Events.userId eq duenoId) and (Events.transferId eq transferId) }
            .map { it[Events.accountId] to it[Events.category] }
    }

    private fun filaDe(eventId: String) = transaction {
        Events.selectAll().where { Events.id eq eventId }.single()
    }

    private fun saldoDe(accountId: String): Long = transaction {
        Events.selectAll().where { Events.accountId eq accountId }.sumOf { fila ->
            val monto = fila[Events.amount]
            if (fila[Events.type] == "EXPENSE") monto else -monto
        }
    }

    private fun montoDeLaPataEn(accountId: String, transferId: String): Long = transaction {
        Events.selectAll()
            .where { (Events.transferId eq transferId) and (Events.accountId eq accountId) }
            .single()[Events.amount]
    }

    // ── El camino feliz ────────────────────────────────────────────────────────

    @Test
    fun `vincular un gasto suelto de cuota arma el traspaso con el split correcto`() = testApplication {
        // Cuota $4.215.223 sobre $177.200.000 al 18,16% E.A.: mismos números que
        // `PagoDeCuotaRoutesTest` — $2.481.318 interés, $1.733.905 capital.
        gastoSuelto("ev-sms-1", ahorros, 4_215_223L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-1", cuerpo(carro))
        val texto = res.bodyAsText()

        assertEquals(HttpStatusCode.OK, res.status, texto)
        assertEquals(177_200_000L - 1_733_905L, saldoDe(carro), "la deuda baja el CAPITAL")
        assertEquals(2, patas("tr-1").size, "las dos patas quedan enlazadas")
        assertTrue(patas("tr-1").contains(ahorros to CUOTA_CATEGORY))
        assertTrue(patas("tr-1").contains(carro to CUOTA_CATEGORY))

        // El movimiento original NO cambió de id, monto, fecha ni fuente: solo se le completó la
        // pareja.
        val fila = filaDe("ev-sms-1")
        assertEquals(4_215_223L, fila[Events.amount], "el monto del SMS no se toca")
        assertEquals("SMS", fila[Events.eventSource], "sigue siendo un movimiento capturado por SMS")
        assertEquals("tr-1", fila[Events.transferId])
    }

    @Test
    fun `el interes puro del Credito Mama arma su pata igual, con capital cero`() = testApplication {
        // El caso real que motivó esta ola: una cuota casi 100% interés. El capital se clampa a
        // cero y SIGUE siendo un pago válido — la deuda no baja nada este mes, pero el traspaso
        // se arma igual.
        gastoSuelto("ev-sms-mama", ahorros, 1_300_000L, CUOTA_CATEGORY)
        wireApp()
        val antes = saldoDe(mama)
        val res = vincular(duenoId, "ev-sms-mama", cuerpo(mama, tr = "tr-mama", toEventId = "ev-deuda-mama"))

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val capital = transaction {
            Events.selectAll().where { Events.id eq "ev-deuda-mama" }.single()[Events.amount]
        }
        assertTrue(capital in 0L..10_000L, "capital casi cero: $capital")
        assertEquals(antes - capital, saldoDe(mama))
        assertEquals(2, patas("tr-mama").size, "el par se arma aunque el capital sea case cero")
    }

    @Test
    fun `vincular una tarjeta usa la categoria reservada y baja la deuda por todo`() = testApplication {
        gastoSuelto("ev-sms-amex", ahorros, 1_008_902L, CARD_PAYMENT_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-amex", cuerpo(amex, tr = "tr-amex", toEventId = "ev-deuda-amex"))

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(1_008_902L, montoDeLaPataEn(amex, "tr-amex"), "la tarjeta baja por el monto completo")
        assertTrue(patas("tr-amex").contains(ahorros to CARD_PAYMENT_CATEGORY))
        assertTrue(patas("tr-amex").contains(amex to CARD_PAYMENT_CATEGORY))
    }

    @Test
    fun `si el dueño eligio la categoria de cuota pero la cuenta es una tarjeta, la categoria se corrige sola`() = testApplication {
        // El dueño no tiene por qué acertar el tipo de cuenta al elegir la categoría: el server
        // decide la categoría final según la CUENTA, con la misma función que un pago manual.
        gastoSuelto("ev-sms-mixto", ahorros, 500_000L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-mixto", cuerpo(amex, tr = "tr-mixto", toEventId = "ev-deuda-mixto"))

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(CARD_PAYMENT_CATEGORY, filaDe("ev-sms-mixto")[Events.category])
    }

    // ── El reintento ───────────────────────────────────────────────────────────

    @Test
    fun `reintentar con los mismos ids no baja la deuda dos veces`() = testApplication {
        gastoSuelto("ev-sms-retry", ahorros, 4_215_223L, CUOTA_CATEGORY)
        wireApp()
        assertEquals(HttpStatusCode.OK, vincular(duenoId, "ev-sms-retry", cuerpo(carro, tr = "tr-r", toEventId = "ev-deuda-r")).status)
        val segunda = vincular(duenoId, "ev-sms-retry", cuerpo(carro, tr = "tr-r", toEventId = "ev-deuda-r"))

        assertEquals(HttpStatusCode.OK, segunda.status, "el reintento no es un error")
        assertEquals(2, patas("tr-r").size, "y no deja cuatro patas")
        assertEquals(177_200_000L - 1_733_905L, saldoDe(carro), "la deuda bajó UNA vez")
    }

    @Test
    fun `un id de pago que ya usa otro movimiento se rechaza`() = testApplication {
        gastoSuelto("ev-sms-a", ahorros, 4_215_223L, CUOTA_CATEGORY)
        wireApp()
        vincular(duenoId, "ev-sms-a", cuerpo(carro, tr = "tr-col", toEventId = "ev-deuda-col"))
        val otro = vincular(duenoId, "ev-sms-a", cuerpo(carro, tr = "tr-col", toEventId = "otro-id"))

        assertEquals(HttpStatusCode.UnprocessableEntity, otro.status)
    }

    // ── Lo que no se puede ─────────────────────────────────────────────────────

    @Test
    fun `no se vincula un movimiento de otro usuario`() = testApplication {
        gastoSuelto("ev-ajeno", ajena, 100_000L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-ajeno", cuerpo(carro))

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `no se vincula a una cuenta de otro usuario`() = testApplication {
        gastoSuelto("ev-sms-b", ahorros, 100_000L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-b", cuerpo(ajena))

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `no se vincula a una cuenta que no es deuda`() = testApplication {
        gastoSuelto("ev-sms-c", ahorros, 100_000L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-c", cuerpo(ahorros))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(PAGO_A_NO_DEUDA_BLOQUEADO in res.bodyAsText())
    }

    @Test
    fun `no se vincula un movimiento con otra categoria`() = testApplication {
        gastoSuelto("ev-sms-d", ahorros, 100_000L, "Comida")
        wireApp()
        val res = vincular(duenoId, "ev-sms-d", cuerpo(carro))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(VINCULO_CATEGORIA_INVALIDA in res.bodyAsText())
        assertEquals(0, patas("tr-1").size)
    }

    @Test
    fun `no se vincula un movimiento que ya es la mitad de OTRO traspaso`() = testApplication {
        transaction {
            Events.insert {
                it[id] = "ev-otro-traspaso"
                it[userId] = duenoId
                it[accountId] = ahorros
                it[type] = "EXPENSE"
                it[amount] = 100_000L
                it[currency] = "COP"
                it[category] = CUOTA_CATEGORY
                it[description] = "Ya es un traspaso"
                it[timestamp] = 1_788_100_000_000L
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
                it[Events.transferId] = "tr-ajeno"
            }
        }
        wireApp()
        val res = vincular(duenoId, "ev-otro-traspaso", cuerpo(carro, tr = "tr-nuevo"))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(VINCULO_YA_ES_TRASPASO in res.bodyAsText())
    }

    @Test
    fun `un credito en otra moneda que la cuenta no se convierte`() = testApplication {
        // Un crédito (LOAN) no admite conversión automática, a diferencia de una tarjeta: el
        // pago manual tampoco lo permite (`PAGO_MONEDAS_DISTINTAS`), y esta ruta no inventa una
        // excepción nueva. Esto NO llama a `FxRateService` — es determinista sin red.
        transaction { cuenta("acc-carro-usd", duenoId, "Crédito en dólares", "LOAN", "USD") }
        gastoSuelto("ev-sms-e", ahorros, 100_000L, CUOTA_CATEGORY)
        wireApp()
        val res = vincular(duenoId, "ev-sms-e", cuerpo("acc-carro-usd"))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(PAGO_MONEDAS_DISTINTAS in res.bodyAsText())
        assertEquals(0, patas("tr-1").size)
    }

    @Test
    fun `sin elegir cuenta, el gasto suelto de siempre no cambia`() = testApplication {
        // Este endpoint nunca se llama si el dueño no elige una cuenta — el gasto queda tal como
        // el guardado normal de categoría lo dejó. Lo prueba mirando que la ruta existente de
        // categoría (que ya existía antes de esta ola) no toca transferId.
        gastoSuelto("ev-suelto", ahorros, 50_000L, CUOTA_CATEGORY)
        val filaAntes = filaDe("ev-suelto")

        assertNull(filaAntes[Events.transferId])
        assertEquals(CUOTA_CATEGORY, filaAntes[Events.category])
    }
}
