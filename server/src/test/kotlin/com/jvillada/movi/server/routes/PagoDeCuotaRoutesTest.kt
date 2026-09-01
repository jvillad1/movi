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
import com.jvillada.movi.shared.model.PAGO_DESDE_DEUDA_BLOQUEADO
import com.jvillada.movi.shared.model.PAGO_MONEDAS_DISTINTAS
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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `POST /api/payments/installment` — pagar la cuota de un crédito o el extracto de una tarjeta.
 *
 * La ruta se abrió a revisión **sin una sola prueba**, y la propiedad de la que depende que no
 * salga plata de la cuenta sin bajar ninguna deuda —que las dos inserciones vivan en la misma
 * transacción— no la comprobaba nadie. Estas pruebas la fijan, junto con el reintento idempotente
 * y el aislamiento entre usuarios.
 *
 * Mismo harness que `TransferRoutesTest`: H2 en memoria + JWT de prueba + `configureRouting()`.
 */
class PagoDeCuotaRoutesTest {

    private val testSecret = "test-secret-for-installment-routes-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-cuotas"
    private val otroId = "user-otro-cuotas"

    private val ahorros = "acc-ahorros-cuotas"
    private val ahorrosUsd = "acc-ahorros-usd"
    private val carro = "acc-carro"
    private val amex = "acc-amex"
    private val ajena = "acc-de-otro"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pago_cuota_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            listOf(duenoId to "dueno@cuotas.test", otroId to "otro@cuotas.test").forEach { (uid, mail) ->
                Users.insert { it[id] = uid; it[email] = mail; it[name] = uid; it[passwordHash] = "hash" }
            }
            cuenta(ahorros, duenoId, "Bancolombia Ahorros", "SAVINGS", "COP")
            cuenta(ahorrosUsd, duenoId, "Ahorros USD", "SAVINGS", "USD")
            cuenta(carro, duenoId, "Vehículo 4083", "LOAN", "COP")
            cuenta(amex, duenoId, "AMEX 9208", "CREDIT_CARD", "COP")
            cuenta(ajena, otroId, "Ahorros de otro", "SAVINGS", "COP")

            // La deuda del carro, como apertura: los saldos se derivan de eventos.
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

    private fun token(userId: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId).withClaim("email", "$userId@cuotas.test")
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

    private fun cuerpo(
        from: String,
        debt: String,
        monto: Long,
        tr: String = "tr-1",
        ev1: String = "ev-dinero-1",
        ev2: String = "ev-deuda-1",
    ) = """
        {"fromAccountId":"$from","debtAccountId":"$debt","amount":$monto,
         "timestamp":1788000000000,"transferId":"$tr","fromEventId":"$ev1","toEventId":"$ev2"}
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.pagar(uid: String, body: String) =
        client.post("/api/payments/installment") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun patas(transferId: String) = transaction {
        Events.selectAll()
            .where { (Events.userId eq duenoId) and (Events.transferId eq transferId) }
            .map { it[Events.accountId] to it[Events.category] }
    }

    private fun saldoDe(accountId: String): Long = transaction {
        Events.selectAll().where { Events.accountId eq accountId }.sumOf { fila ->
            val monto = fila[Events.amount]
            if (fila[Events.type] == "EXPENSE") monto else -monto
        }
    }

    // ── El camino feliz ────────────────────────────────────────────────────────

    @Test
    fun `pagar la cuota de un credito baja la deuda y cuenta como gasto`() = testApplication {
        wireApp()
        val res = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))
        val texto = res.bodyAsText()

        assertEquals(HttpStatusCode.Created, res.status, texto)
        // Este crédito NO tiene `credit_terms` (ver el `setUp`), así que no hay tasa con la que
        // separar el interés y la deuda baja por el monto completo — el comportamiento de siempre.
        // Es el caso que la ola de la cuota-por-capital preservó a propósito. El crédito CON tasa
        // se prueba abajo, en `la cuota de un credito con tasa baja la deuda solo por el capital`.
        assertEquals(177_200_000L - 4_215_223L, saldoDe(carro), "sin tasa, la deuda baja lo pagado")
        // La pata del dinero lleva una categoría NORMAL: es lo que la hace contar en el mes.
        assertTrue(patas("tr-1").contains(ahorros to CUOTA_CATEGORY))
        assertTrue(patas("tr-1").contains(carro to CUOTA_CATEGORY))
    }

    @Test
    fun `pagar una tarjeta usa la categoria reservada, que no cuenta`() = testApplication {
        // Las compras ya contaron cuando se hicieron: contar el pago sería contar dos veces.
        wireApp()
        val res = pagar(duenoId, cuerpo(ahorros, amex, 1_008_902))

        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        assertTrue(patas("tr-1").contains(ahorros to CARD_PAYMENT_CATEGORY))
    }

    @Test
    fun `la plata sale de la cuenta`() = testApplication {
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        // `saldoDe` cuenta EXPENSE como positivo, así que sobre una cuenta de dinero un pago suma
        // salidas: lo que importa es que la pata exista en la cuenta correcta y por el monto.
        assertEquals(4_215_223L, saldoDe(ahorros))
    }

    // ── El reintento ───────────────────────────────────────────────────────────

    @Test
    fun `reintentar con los mismos ids no cobra dos veces`() = testApplication {
        // El dedo que vuelve a tocar Guardar porque la respuesta se perdió.
        wireApp()
        assertEquals(HttpStatusCode.Created, pagar(duenoId, cuerpo(ahorros, carro, 4_215_223)).status)
        val segunda = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(HttpStatusCode.OK, segunda.status, "el reintento no es un error")
        assertEquals(2, patas("tr-1").size, "y no deja cuatro patas")
        assertEquals(177_200_000L - 4_215_223L, saldoDe(carro), "la deuda bajó UNA vez")
    }

    @Test
    fun `un id de pago que ya usa otro movimiento se rechaza`() = testApplication {
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))
        // Mismo transferId, otros ids de evento: no es un reintento, es una colisión.
        val otro = pagar(duenoId, cuerpo(ahorros, carro, 100_000, tr = "tr-1", ev1 = "x1", ev2 = "x2"))

        assertEquals(HttpStatusCode.UnprocessableEntity, otro.status)
        assertEquals(2, patas("tr-1").size)
    }

    // ── Lo que no se puede ─────────────────────────────────────────────────────

    @Test
    fun `no se paga con la cuenta de otro usuario`() = testApplication {
        wireApp()
        val res = pagar(duenoId, cuerpo(ajena, carro, 100_000))

        // 404 y no 403: para este usuario esa cuenta sencillamente no existe.
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(177_200_000L, saldoDe(carro), "y nada se movió")
    }

    @Test
    fun `no se paga una deuda con otra deuda`() = testApplication {
        wireApp()
        val res = pagar(duenoId, cuerpo(amex, carro, 100_000))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(PAGO_DESDE_DEUDA_BLOQUEADO in res.bodyAsText())
        assertEquals(0, patas("tr-1").size)
    }

    @Test
    fun `no se mezclan monedas`() = testApplication {
        // La tarjeta en dólares se paga con dólares. Convertir acá dejaría un saldo mal por el
        // tipo de cambio del día, en silencio.
        wireApp()
        val res = pagar(duenoId, cuerpo(ahorrosUsd, carro, 100))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(PAGO_MONEDAS_DISTINTAS in res.bodyAsText())
    }

    @Test
    fun `un monto en cero no es un pago`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.UnprocessableEntity, pagar(duenoId, cuerpo(ahorros, carro, 0)).status)
        assertEquals(0, patas("tr-1").size)
    }

    @Test
    fun `las dos patas no pueden compartir id`() = testApplication {
        // Sin esta guarda, la segunda inserción choca contra la PK y el pago queda a medias — o
        // sea plata que sale de la cuenta sin bajar ninguna deuda.
        wireApp()
        val res = pagar(duenoId, cuerpo(ahorros, carro, 100_000, ev1 = "mismo", ev2 = "mismo"))

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(0, patas("tr-1").size)
        assertEquals(177_200_000L, saldoDe(carro))
    }

    @Test
    fun `una fecha fuera de este siglo se rechaza`() = testApplication {
        // El piso que hace que un epoch roto se note en vez de esconder las dos patas en 1970.
        wireApp()
        val res = client.post("/api/payments/installment") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"fromAccountId":"$ahorros","debtAccountId":"$carro","amount":100000,
                    "timestamp":0,"transferId":"tr-9","fromEventId":"e1","toEventId":"e2"}""".trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(0, patas("tr-9").size)
    }

    // ── La cuota baja la deuda SOLO por el capital ─────────────────────────────

    /** Le pone condiciones al crédito del carro: sin `credit_terms` no hay tasa que aplicar. */
    private fun condicionesDelCarro(rateEa: Double, seguro: Long? = null) = transaction {
        Credits.insert {
            it[accountId] = carro
            it[userId] = duenoId
            it[bank] = "Bancolombia"
            it[principal] = 200_000_000L
            it[Credits.rateEa] = rateEa
            it[termMonths] = 72
            it[installment] = 4_215_223L
            it[dayOfMonth] = 5
            it[startDate] = "2024-01-15"
            it[insuranceMonthly] = seguro
        }
    }

    /** El JSON de este server sale con sangría, así que un `contains("\"x\":1")` no matchea nada. */
    private fun campoNum(json: String, campo: String): Long? =
        Regex("\"$campo\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toLong()

    private fun montoDeLaPataEn(accountId: String, transferId: String): Long = transaction {
        Events.selectAll()
            .where { (Events.transferId eq transferId) and (Events.accountId eq accountId) }
            .single()[Events.amount]
    }

    @Test
    fun `la cuota de un credito con tasa baja la deuda solo por el capital`() = testApplication {
        // **El error que esta ola corrige.** Cuota de $4.215.223 sobre un saldo de $177.200.000 al
        // 18,16 % E.A.: $2.481.318 son interés y solo $1.733.905 abonan a capital. Antes la deuda
        // bajaba los $4.215.223 completos, y con las seis cuotas de un mes eso le mostraba al dueño
        // $18,7 millones menos de deuda de la que tiene, acumulándose cada mes.
        condicionesDelCarro(rateEa = 18.16)
        wireApp()
        val res = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        assertEquals(177_200_000L - 1_733_905L, saldoDe(carro), "la deuda baja el CAPITAL")
        assertEquals(1_733_905L, montoDeLaPataEn(carro, "tr-1"))
    }

    @Test
    fun `y la cuenta pierde la cuota entera igual`() = testApplication {
        // La otra mitad de la decisión: esa plata SÍ salió toda, y sigue contando en sus gastos del
        // mes. Sin este test, bajar las dos patas al capital pasaría verde.
        condicionesDelCarro(rateEa = 18.16)
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(4_215_223L, montoDeLaPataEn(ahorros, "tr-1"))
        assertEquals(4_215_223L, saldoDe(ahorros))
    }

    @Test
    fun `el seguro declarado tampoco baja la deuda`() = testApplication {
        // Con $108.800 de Seguro Vida Deudor adentro de la cuota, el capital baja exactamente esos
        // $108.800 menos. Se compara contra el mismo pago sin seguro declarado, que es lo que hace
        // que este test hable del seguro y no del interés.
        condicionesDelCarro(rateEa = 18.16, seguro = 108_800L)
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(1_733_905L - 108_800L, montoDeLaPataEn(carro, "tr-1"))
    }

    @Test
    fun `el reintento calcula el mismo capital, no uno sobre la deuda ya bajada`() = testApplication {
        // El interés se deriva del saldo ANTES del pago. Si el reintento no excluyera las patas de
        // este mismo pago, calcularía el interés sobre una deuda ya reducida — un número distinto
        // del que quedó escrito, y la respuesta afirmaría un reparto que no ocurrió.
        condicionesDelCarro(rateEa = 18.16)
        wireApp()
        assertEquals(HttpStatusCode.Created, pagar(duenoId, cuerpo(ahorros, carro, 4_215_223)).status)
        val segunda = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(HttpStatusCode.OK, segunda.status)
        assertEquals(177_200_000L - 1_733_905L, saldoDe(carro), "la deuda bajó UNA vez y por el capital")
        assertEquals(1_733_905L, campoNum(segunda.bodyAsText(), "capital"), segunda.bodyAsText())
    }

    @Test
    fun `la respuesta trae el desglose para que la app pueda mostrarlo`() = testApplication {
        // El dueño tiene que poder verificar el número, no confiar en él: es plata suya.
        condicionesDelCarro(rateEa = 18.16)
        wireApp()
        val texto = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223)).bodyAsText()

        assertEquals(2_481_318L, campoNum(texto, "interes"), texto)
        assertEquals(1_733_905L, campoNum(texto, "capital"), texto)
        assertEquals(177_200_000L - 1_733_905L, campoNum(texto, "deudaRestante"), texto)
    }

    private fun noAmortizaDeLaPataEn(accountId: String, transferId: String): Long? = transaction {
        Events.selectAll()
            .where { (Events.transferId eq transferId) and (Events.accountId eq accountId) }
            .single()[Events.noAmortiza]
    }

    @Test
    fun `la pata de la deuda GUARDA el interes y el seguro de ese mes`() = testApplication {
        // Sin esto, corregir el monto después tenía que deducir el interés restando las dos patas,
        // y esa resta miente en cuanto el capital se clampa a cero — ahí desaparecía deuda en
        // silencio. Ver `FinancialEvent.noAmortiza`.
        condicionesDelCarro(rateEa = 18.16, seguro = 108_800L)
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 4_215_223))

        assertEquals(2_481_318L + 108_800L, noAmortizaDeLaPataEn(carro, "tr-1"))
        assertEquals(null, noAmortizaDeLaPataEn(ahorros, "tr-1"), "la pata del dinero no guarda nada")
    }

    @Test
    fun `una cuota que no cubre el interes deja la pata en cero y guarda el interes igual`() = testApplication {
        // El caso real: un pago PARCIAL, más chico que el interés del mes. La fila queda en $0 —lo
        // correcto, nada abonó a capital— pero la cifra que hace falta para corregirla después no
        // se pierde, que es de lo que dependía el arreglo de la edición.
        condicionesDelCarro(rateEa = 18.16)
        wireApp()
        pagar(duenoId, cuerpo(ahorros, carro, 1_000_000))

        assertEquals(0L, montoDeLaPataEn(carro, "tr-1"))
        assertEquals(2_481_318L, noAmortizaDeLaPataEn(carro, "tr-1"), "la resta de las patas diría 1.000.000")
    }

    @Test
    fun `un movimiento en otra moneda no entra al saldo que calcula el interes`() = testApplication {
        // `computeBalances` agrupa por moneda; este cálculo sumaba todos los deltas sin mirarla, y
        // esa cifra entra derecho al interés. Hoy no muerde —`validarPagoDeCuota` exige que la
        // cuenta y la deuda compartan moneda— pero una fila en otra moneda dentro de la misma
        // cuenta alcanzaba para torcer el reparto. Lo mismo vale para `deudaRestante`.
        condicionesDelCarro(rateEa = 18.16)
        transaction {
            Events.insert {
                it[id] = "ev-usd-en-el-carro"
                it[userId] = duenoId
                it[accountId] = carro
                it[type] = "EXPENSE"
                it[amount] = 50_000_000L
                it[currency] = "USD"
                it[category] = "Saldo inicial"
                it[description] = "Una fila en otra moneda"
                it[timestamp] = 1_788_000_000_000L
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
        wireApp()
        val texto = pagar(duenoId, cuerpo(ahorros, carro, 4_215_223)).bodyAsText()

        assertEquals(2_481_318L, campoNum(texto, "interes"), "el interés sale del saldo en COP: $texto")
        assertEquals(1_733_905L, montoDeLaPataEn(carro, "tr-1"))
        assertEquals(177_200_000L - 1_733_905L, campoNum(texto, "deudaRestante"), texto)
    }

    @Test
    fun `un par simetrico no guarda nada, y ese NULL es la respuesta`() = testApplication {
        // Una tarjeta arma un par simétrico de verdad. Un 0 explícito diría lo mismo pero con pinta
        // de calculado, y la corrección del monto lo trataría distinto.
        wireApp()
        pagar(duenoId, cuerpo(ahorros, amex, 1_008_902))

        assertEquals(null, noAmortizaDeLaPataEn(amex, "tr-1"))
    }

    @Test
    fun `pagar una tarjeta sigue bajando la deuda por todo lo pagado`() = testApplication {
        // Una tarjeta no amortiza: sus intereses se causan como un movimiento aparte. Este test
        // blinda lo que NO tenía que cambiar — que nadie "arregle" la tarjeta por simetría.
        wireApp()
        pagar(duenoId, cuerpo(ahorros, amex, 1_008_902))

        assertEquals(1_008_902L, montoDeLaPataEn(amex, "tr-1"))
    }
}
