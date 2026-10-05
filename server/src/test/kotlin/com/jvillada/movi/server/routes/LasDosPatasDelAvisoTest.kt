package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.signedDelta
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Al confirmar un aviso, Movi arma solo las dos patas
 *
 * Arreglo 1 de la auditoría de la ingesta: 24 «segundas patas» armadas a mano en 45 días. Un caso por
 * fila de la tabla del brief —el pago de una tarjeta, el retiro de la Fiducuenta hacia Ahorros, el
 * avance de la AMEX, la cuota de un crédito—, con sus categorías exactas, y además: que confirmar dos
 * veces no duplica, que un aviso suelto y un pago avisado varias veces pasan por la misma función, que
 * el APK 1.69 (sin cuerpo) sigue igual, y que una cuenta de otro usuario no se puede tocar.
 */
class LasDosPatasDelAvisoTest {

    private val testSecret = "test-secret-for-las-dos-patas-del-aviso-min-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-patas"
    private val otro = "user-otro-patas"

    private val ahorros = "acc-ahorros-patas"
    private val fiducuenta = "acc-fidu-patas"
    private val masterBlack = "acc-mb-patas"
    private val amex = "acc-amex-patas"
    private val vehiculo = "acc-vehiculo-patas"
    private val ajena = "acc-ajena-patas"

    private val hoy: LocalDate = AppClock.today()
    private val cuota = 4_178_163L

    private val textoDelPago = "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133"
    private val textoDelRetiro = "Bancolombia: Retiraste \$4.200.000 de tu cuenta *9586 Fiducuenta hacia la cuenta *02955068133"
    private val textoDelAvance = "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL desde tu T.Credito *9208 a la cuenta *8133."
    private val textoDeLaCuota = "Bancolombia: Pagaste \$4,178,163.00 a Banco de Occidente S A ATH desde tu producto 8133"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:las_dos_patas_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "patas@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Users.insert { it[id] = otro; it[email] = "otro@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            cuenta(ahorros, "Bancolombia Ahorros", "SAVINGS")
            cuenta(fiducuenta, "Fiducuenta 9586", "INVESTMENT")
            cuenta(masterBlack, "Master Black 3684", "CREDIT_CARD")
            cuenta(amex, "AMEX 9208", "CREDIT_CARD")
            cuenta(vehiculo, "Vehículo 8761", "LOAN")
            cuenta(ajena, "Ahorros de otro", "SAVINGS", dueno = otro)
            Credits.insert {
                it[accountId] = vehiculo
                it[userId] = uid
                it[bank] = "Banco de Occidente"
                it[principal] = 150_000_000L
                it[rateEa] = 18.0
                it[termMonths] = 72
                it[installment] = cuota
                it[dayOfMonth] = hoy.dayOfMonth
                it[startDate] = hoy.minusYears(2).toString()
            }
            Cards.insert {
                it[accountId] = masterBlack
                it[userId] = uid
                it[bank] = "Bancolombia"
                it[paymentDay] = hoy.dayOfMonth
            }
            evento("ev-saldo-ahorros", ahorros, "INCOME", 50_000_000L, "Saldo inicial", hoy.minusMonths(3))
            evento("ev-saldo-fidu", fiducuenta, "INCOME", 20_000_000L, "Saldo inicial", hoy.minusMonths(3))
            evento("ev-deuda-vehiculo", vehiculo, "EXPENSE", 120_000_000L, "Saldo inicial", hoy.minusYears(1))
            // La tarjeta tiene que deber algo para tener su pago del período.
            evento("ev-compra-mb", masterBlack, "EXPENSE", 2_000_000L, "Mercado", hoy.minusDays(20))

            val hora = "$hoy 09:00"
            aviso("sms_pago_mb", textoDelPago, hora)
            aviso("sms_retiro_fidu", textoDelRetiro, hora)
            aviso("correo_avance", textoDelAvance, hora, banco = "Correo · Bancolombia")
            aviso("sms_cuota", textoDeLaCuota, hora)
            aviso("correo_cuota", "Pago PSE · Banco de Occidente · \$ 4.178.163,00", hora, banco = "Correo · PSE")
            aviso("sms_del_otro", textoDelPago, hora, dueno = otro)
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String, dueno: String = uid) {
        Accounts.insert { it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = tipo }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = "movimiento de prueba"
            it[timestamp] = appDateToEpochMillis(fecha)
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun aviso(id: String, texto: String, hora: String, banco: String = "85540", dueno: String = uid) {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = dueno
            it[time] = hora
            it[bank] = banco
            it[text] = texto
            it[state] = SMS_STATE_PENDING
            it[det] = ""
        }
    }

    private fun token(quien: String = uid): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", quien).withClaim("email", "$quien@movi.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
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

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private fun patas(
        operacion: OperacionDelAviso,
        origen: String,
        destino: String,
        monto: Long,
        clave: String,
    ) = DosPatasDelAviso(
        operacion = operacion,
        origenId = origen,
        destinoId = destino,
        monto = monto,
        timestamp = appDateToEpochMillis(hoy),
        transferId = "tr-$clave",
        origenEventId = "ev-$clave-sale",
        destinoEventId = "ev-$clave-entra",
    )

    private suspend fun ApplicationTestBuilder.confirmar(avisoId: String, cuerpo: DosPatasDelAviso, quien: String = uid): HttpResponse =
        cliente().post("/api/sms/$avisoId/confirm") {
            header(HttpHeaders.Authorization, "Bearer ${token(quien)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.confirmarElPago(ids: List<String>, cuerpo: DosPatasDelAviso): HttpResponse =
        cliente().post("/api/sms/grupo/${ids.first()}/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(ConfirmarElMismoPago(ids, patas = cuerpo))
        }

    /** Los movimientos vivos de [cuentaId], con `countsAsCashFlow` derivado como en cualquier lectura del server. */
    private fun movimientos(cuentaId: String): List<FinancialEvent> = transaction { loadNonVoidedEventsIn(uid, cuentaId) }

    private fun deEsteTraspaso(transferId: String): List<FinancialEvent> = transaction {
        loadNonVoidedEventsIn(uid).filter { it.transferId == transferId }
    }

    private fun estadoDelAviso(id: String): Pair<String, String?> = transaction {
        val fila = SmsMessages.selectAll().where { SmsMessages.id eq id }.single()
        fila[SmsMessages.state] to fila[SmsMessages.eventoId]
    }

    private fun cuantosMovimientos(): Long = transaction { Events.selectAll().where { Events.userId eq uid }.count() }

    private suspend fun ApplicationTestBuilder.ocurrencias(): List<OccurrenceState> {
        val r = cliente().get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status)
        return r.body()
    }

    // ── Un caso por fila ────────────────────────────────────────────────────────

    @Test
    fun `pago de tarjeta propia - sale de Ahorros con Pago de tarjeta, entra a la tarjeta como abono y marca su periodo`() = testApplication {
        application { testModule() }
        val antes = ocurrencias().firstOrNull { it.ruleId == "$CARD_RULE_PREFIX$masterBlack" }
        assertTrue(antes == null || !antes.occurred, "antes de confirmar, el pago de la tarjeta no está hecho")

        val r = confirmar("sms_pago_mb", patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, masterBlack, 386_902L, "mb"))
        assertEquals(HttpStatusCode.OK, r.status)
        val hecho = r.body<MismoPagoConfirmado>()
        assertTrue(hecho.creado)
        assertEquals("ev-mb-sale", hecho.eventoId, "el aviso queda con la pata que sale de la cuenta")
        assertEquals(listOf("ev-mb-sale", "ev-mb-entra"), hecho.patas)

        val (sale, entra) = deEsteTraspaso("tr-mb").partition { it.accountId == ahorros }.let { it.first.single() to it.second.single() }
        assertEquals(TransactionType.EXPENSE, sale.type)
        assertEquals(CARD_PAYMENT_CATEGORY, sale.category)
        assertEquals(386_902L, sale.amount)
        assertEquals(masterBlack, entra.accountId)
        assertEquals(TransactionType.INCOME, entra.type)
        assertEquals(CARD_PAYMENT_CATEGORY, entra.category)
        assertEquals(386_902L, entra.amount, "una tarjeta baja por el pago entero")
        assertFalse(sale.countsAsCashFlow, "pagar la tarjeta no es un gasto del mes")
        assertFalse(entra.countsAsCashFlow)
        assertEquals(EventSource.SMS, sale.source, "vino de un aviso, no se anotó a mano")
        assertEquals(EventSource.SMS, entra.source)
        assertEquals(textoDelPago, sale.rawPayload, "la pata del dinero guarda el texto del aviso")
        assertNull(entra.rawPayload)

        assertEquals(SMS_STATE_CONFIRMED to "ev-mb-sale", estadoDelAviso("sms_pago_mb"))
        transaction {
            assertNotNull(SmsMessages.selectAll().where { SmsMessages.id eq "sms_pago_mb" }.single()[SmsMessages.confirmadoEn])
        }
        val despues = assertNotNull(ocurrencias().firstOrNull { it.ruleId == "$CARD_RULE_PREFIX$masterBlack" })
        assertTrue(despues.occurred, "el pago de la tarjeta del período queda hecho")
        assertEquals("ev-mb-entra", despues.eventId)
    }

    @Test
    fun `traspaso Fiducuenta hacia Ahorros - dos patas con Traspaso, fuera del mes`() = testApplication {
        application { testModule() }
        val r = confirmar("sms_retiro_fidu", patas(OperacionDelAviso.TRASPASO, fiducuenta, ahorros, 4_200_000L, "fidu"))
        assertEquals(HttpStatusCode.OK, r.status)

        val legs = deEsteTraspaso("tr-fidu")
        val sale = legs.single { it.accountId == fiducuenta }
        val entra = legs.single { it.accountId == ahorros }
        assertEquals(TransactionType.EXPENSE, sale.type)
        assertEquals(TransactionType.INCOME, entra.type)
        assertEquals(TRANSFER_CATEGORY, sale.category)
        assertEquals(TRANSFER_CATEGORY, entra.category)
        assertEquals(4_200_000L, sale.amount)
        assertEquals(4_200_000L, entra.amount)
        assertFalse(sale.countsAsCashFlow || entra.countsAsCashFlow, "un traspaso no es ni gasto ni ingreso")
        assertEquals("Traspaso a Bancolombia Ahorros", sale.description)
        assertEquals(SMS_STATE_CONFIRMED to "ev-fidu-sale", estadoDelAviso("sms_retiro_fidu"))
    }

    @Test
    fun `avance de tarjeta - la tarjeta sube como Traspaso y Ahorros recibe un desembolso que si entra`() = testApplication {
        application { testModule() }
        val deudaAntes = movimientos(amex).sumOf { signedDelta(AccountType.CREDIT_CARD, it.type, it.amount) }

        val r = confirmar("correo_avance", patas(OperacionDelAviso.AVANCE, amex, ahorros, 6_200_000L, "avance"))
        assertEquals(HttpStatusCode.OK, r.status)
        val hecho = r.body<MismoPagoConfirmado>()
        assertEquals("ev-avance-entra", hecho.eventoId, "en un avance el aviso queda con la pata que entra a la cuenta")

        val deLaTarjeta = movimientos(amex).single { it.transferId == "tr-avance" }
        assertEquals(TransactionType.EXPENSE, deLaTarjeta.type)
        assertEquals(TRANSFER_CATEGORY, deLaTarjeta.category, "con desembolso contaría como gasto: en una tarjeta todo EXPENSE es flujo")
        assertFalse(deLaTarjeta.countsAsCashFlow, "la pata de la tarjeta no cuenta como gasto")
        assertEquals("Avance a Bancolombia Ahorros", deLaTarjeta.description)

        val deAhorros = movimientos(ahorros).single { it.transferId == "tr-avance" }
        assertEquals(TransactionType.INCOME, deAhorros.type)
        assertEquals(DESEMBOLSO_CATEGORY, deAhorros.category)
        assertTrue(deAhorros.countsAsCashFlow, "la plata del avance suma en «Entró» (decisión del dueño)")
        assertEquals("Avance desde AMEX 9208", deAhorros.description)

        val deudaDespues = movimientos(amex).sumOf { signedDelta(AccountType.CREDIT_CARD, it.type, it.amount) }
        assertEquals(deudaAntes + 6_200_000L, deudaDespues, "la deuda de la AMEX sube por el avance")
        assertEquals(SMS_STATE_CONFIRMED to "ev-avance-entra", estadoDelAviso("correo_avance"))
    }

    @Test
    fun `cuota de un credito propio - Cuota de credito, baja solo el capital y marca la cuota del periodo`() = testApplication {
        application { testModule() }
        val r = confirmar("sms_cuota", patas(OperacionDelAviso.CUOTA, ahorros, vehiculo, cuota, "cuota"))
        assertEquals(HttpStatusCode.OK, r.status)

        val legs = deEsteTraspaso("tr-cuota")
        val delDinero = legs.single { it.accountId == ahorros }
        val deLaDeuda = legs.single { it.accountId == vehiculo }
        assertEquals(CUOTA_CATEGORY, delDinero.category)
        assertEquals(CUOTA_CATEGORY, deLaDeuda.category)
        assertEquals(cuota, delDinero.amount, "sale la cuota entera")
        assertTrue(delDinero.countsAsCashFlow, "la cuota cuenta como gasto del mes")
        assertFalse(deLaDeuda.countsAsCashFlow)
        assertTrue(deLaDeuda.amount < cuota, "la deuda baja solo por el capital")
        assertEquals(cuota - deLaDeuda.amount, assertNotNull(deLaDeuda.noAmortiza), "lo que no amortizó queda guardado")

        val delPeriodo = assertNotNull(ocurrencias().firstOrNull { it.ruleId == "$CREDIT_RULE_PREFIX$vehiculo" })
        assertTrue(delPeriodo.occurred, "la cuota del período queda pagada, igual que con «vincular-deuda»")
        assertEquals("ev-cuota-entra", delPeriodo.eventId)
    }

    // ── Idempotencia ────────────────────────────────────────────────────────────

    @Test
    fun `confirmar dos veces no duplica y el doble toque contesta lo mismo`() = testApplication {
        application { testModule() }
        val pedido = patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, masterBlack, 386_902L, "doble")
        val antes = cuantosMovimientos()

        val primero = confirmar("sms_pago_mb", pedido).body<MismoPagoConfirmado>()
        val segundo = confirmar("sms_pago_mb", pedido)
        assertEquals(HttpStatusCode.OK, segundo.status)
        val repetido = segundo.body<MismoPagoConfirmado>()

        assertEquals(antes + 2, cuantosMovimientos(), "dos patas, no cuatro")
        assertTrue(primero.creado)
        assertFalse(repetido.creado)
        assertEquals(primero.eventoId, repetido.eventoId)
        assertEquals(primero.patas, repetido.patas)

        // Un segundo toque con OTROS ids (la app los renueva) tampoco crea nada: el aviso ya está anotado.
        val conOtrosIds = confirmar("sms_pago_mb", pedido.copy(transferId = "tr-x", origenEventId = "ev-x1", destinoEventId = "ev-x2"))
        assertEquals(HttpStatusCode.OK, conOtrosIds.status)
        assertEquals(primero.patas, conOtrosIds.body<MismoPagoConfirmado>().patas)
        assertEquals(antes + 2, cuantosMovimientos())
    }

    @Test
    fun `si las patas ya estaban escritas con esos ids, se cierra el aviso sin escribirlas otra vez`() = testApplication {
        application { testModule() }
        val pedido = patas(OperacionDelAviso.TRASPASO, fiducuenta, ahorros, 4_200_000L, "ya")
        // Las mismas patas, ya en la base (un intento anterior que escribió y no alcanzó a cerrar el aviso).
        confirmar("sms_retiro_fidu", pedido)
        transaction {
            SmsMessages.update({ SmsMessages.id eq "sms_retiro_fidu" }) {
                it[state] = SMS_STATE_PENDING
                it[eventoId] = null
            }
        }
        val antes = cuantosMovimientos()
        val r = confirmar("sms_retiro_fidu", pedido)
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(r.body<MismoPagoConfirmado>().creado)
        assertEquals(antes, cuantosMovimientos())
        assertEquals(SMS_STATE_CONFIRMED to "ev-ya-sale", estadoDelAviso("sms_retiro_fidu"))
    }

    // ── Una sola función para el aviso suelto y para el pago avisado varias veces ─

    @Test
    fun `el pago avisado varias veces escribe las mismas patas que el aviso suelto y cierra todos sus avisos`() = testApplication {
        application { testModule() }
        val suelto = confirmar("sms_cuota", patas(OperacionDelAviso.CUOTA, ahorros, vehiculo, cuota, "suelto"))
        assertEquals(HttpStatusCode.OK, suelto.status)
        val deUno = deEsteTraspaso("tr-suelto").map { Triple(it.accountId, it.type, it.category) }.toSet()

        // Otro pago de la misma cuota, avisado dos veces (SMS + correo de PSE), con la misma intención.
        transaction {
            aviso("sms_cuota_2", textoDeLaCuota, "$hoy 10:00")
            aviso("correo_cuota_2", "Pago PSE · Banco de Occidente · \$ 4.178.163,00", "$hoy 10:00", banco = "Correo · PSE")
        }
        val ids = listOf("sms_cuota_2", "correo_cuota_2")
        val enGrupo = confirmarElPago(ids, patas(OperacionDelAviso.CUOTA, ahorros, vehiculo, cuota, "grupo"))
        assertEquals(HttpStatusCode.OK, enGrupo.status)
        val hecho = enGrupo.body<MismoPagoConfirmado>()
        assertTrue(hecho.creado)
        assertEquals(ids.toSet(), hecho.cerrados.toSet())
        assertEquals(listOf("ev-grupo-sale", "ev-grupo-entra"), hecho.patas)
        val deDos = deEsteTraspaso("tr-grupo").map { Triple(it.accountId, it.type, it.category) }.toSet()
        assertEquals(deUno, deDos, "las mismas patas por los dos caminos")
        ids.forEach { assertEquals(SMS_STATE_CONFIRMED to "ev-grupo-sale", estadoDelAviso(it)) }

        // Y el doble toque del pago tampoco duplica.
        val antes = cuantosMovimientos()
        val otraVez = confirmarElPago(ids, patas(OperacionDelAviso.CUOTA, ahorros, vehiculo, cuota, "grupo"))
        assertEquals(HttpStatusCode.OK, otraVez.status)
        assertFalse(otraVez.body<MismoPagoConfirmado>().creado)
        assertEquals(antes, cuantosMovimientos())
    }

    // ── El APK 1.69 ─────────────────────────────────────────────────────────────

    @Test
    fun `el cliente viejo confirma sin cuerpo y no se crea nada en el server`() = testApplication {
        application { testModule() }
        val antes = cuantosMovimientos()
        val r = cliente().post("/api/sms/sms_pago_mb/confirm") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(antes, cuantosMovimientos(), "sin cuerpo, el server no arma patas: el APK ya creó su movimiento")
        assertEquals(SMS_STATE_CONFIRMED, estadoDelAviso("sms_pago_mb").first)

        // Y con el movimiento que dice la app (`?eventoId=`), como hace desde #435.
        transaction { evento("ev-de-la-app", ahorros, "EXPENSE", 4_200_000L, "Otros", hoy) }
        val conEvento = cliente().post("/api/sms/sms_retiro_fidu/confirm?eventoId=ev-de-la-app") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, conEvento.status)
        assertEquals(SMS_STATE_CONFIRMED to "ev-de-la-app", estadoDelAviso("sms_retiro_fidu"))
    }

    @Test
    fun `el pago avisado varias veces sigue aceptando un solo movimiento, como antes`() = testApplication {
        application { testModule() }
        val ids = listOf("sms_cuota", "correo_cuota")
        val evento = FinancialEvent(
            id = "ev-suelto-viejo", accountId = ahorros, type = TransactionType.EXPENSE, amount = cuota,
            category = CUOTA_CATEGORY, description = "Banco de Occidente", timestamp = appDateToEpochMillis(hoy),
        )
        val r = cliente().post("/api/sms/grupo/sms_cuota/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(ConfirmarElMismoPago(ids, evento = evento))
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val hecho = r.body<MismoPagoConfirmado>()
        assertEquals("ev-suelto-viejo", hecho.eventoId)
        assertTrue(hecho.patas.isEmpty(), "un movimiento suelto no tiene hermana")
    }

    // ── Aislamiento y reglas ────────────────────────────────────────────────────

    @Test
    fun `la cuenta de otro usuario no se puede usar, y el rechazo no deja nada a medias`() = testApplication {
        application { testModule() }
        val antes = cuantosMovimientos()
        val r = confirmar("sms_retiro_fidu", patas(OperacionDelAviso.TRASPASO, fiducuenta, ajena, 4_200_000L, "ajena"))
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals(antes, cuantosMovimientos())
        assertEquals(SMS_STATE_PENDING to null, estadoDelAviso("sms_retiro_fidu"), "el aviso sigue esperando")

        // Ni el aviso de otro.
        val ajeno = confirmar("sms_del_otro", patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, masterBlack, 386_902L, "aviso-ajeno"))
        assertEquals(HttpStatusCode.NotFound, ajeno.status)
        assertEquals(antes, cuantosMovimientos())

        // Y el dueño de ese aviso tampoco puede usar MIS cuentas desde su aviso.
        val alReves = confirmar("sms_del_otro", patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, masterBlack, 386_902L, "al-reves"), quien = otro)
        assertEquals(HttpStatusCode.NotFound, alReves.status)
        assertEquals(SMS_STATE_PENDING to null, transaction {
            val f = SmsMessages.selectAll().where { (SmsMessages.id eq "sms_del_otro") and (SmsMessages.userId eq otro) }.single()
            f[SmsMessages.state] to f[SmsMessages.eventoId]
        })
    }

    @Test
    fun `si la operacion no es la que dicen las cuentas, se rechaza`() = testApplication {
        application { testModule() }
        val antes = cuantosMovimientos()
        // Dice «cuota» pero el destino es una tarjeta.
        val r = confirmar("sms_pago_mb", patas(OperacionDelAviso.CUOTA, ahorros, masterBlack, 386_902L, "mal"))
        assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
        assertEquals(antes, cuantosMovimientos())
        assertEquals(SMS_STATE_PENDING, estadoDelAviso("sms_pago_mb").first)
    }
}
