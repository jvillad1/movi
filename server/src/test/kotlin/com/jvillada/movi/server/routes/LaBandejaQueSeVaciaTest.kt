package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
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
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.momentoDelSms
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La bandeja que se vacía sola, del lado del server
 *
 * `GET /api/sms/propuestas` trae, para cada pago pendiente, **la misma** propuesta de
 * `/api/sms/{id}/parse` y **las mismas** coincidencias de `/coincidencias` —con las que la app decide
 * qué confirma desde la fila y en el lote— y confirmar desde la fila va por los endpoints de siempre,
 * con la misma idempotencia. Los SMS llevan su propia fecha en el texto (el dedupe de #444).
 */
class LaBandejaQueSeVaciaTest {

    private val testSecret = "test-secret-for-la-bandeja-que-se-vacia-min-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-bandeja"
    private val otro = "user-otro-bandeja"
    private val ahorros = "acc-ahorros-bandeja"
    private val delOtro = "acc-del-otro-bandeja"

    private val dia = LocalDate.of(2026, 10, 10)

    private val textoDelCafe = "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10."
    private val textoDelExito = "Bancolombia: Compraste \$80.000,00 en EXITO LAURELES con tu T.Deb *8133, el 10/10/2026 a las 09:30."
    private val textoACaro = "Bancolombia: Transferiste \$150.000 desde tu cuenta *8133 a la cuenta *31973270756 el 10/10/2026 a las 10:00."
    private val delPagoSms = "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ con tu T.Deb *8133, el 10/10/2026 a las 11:15."
    private val delPagoApp = "Compraste \$12.300,00 en PANADERIA LA ESQUINA con tu T.Deb *8133."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:bandeja_que_se_vacia_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations, DestinosDescartados,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "bandeja@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Users.insert { it[id] = otro; it[email] = "otro@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = ahorros; it[userId] = uid; it[name] = "Bancolombia Ahorros 8133"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = delOtro; it[userId] = otro; it[name] = "Ahorros 8133"; it[type] = "SAVINGS" }
            evento("ev-saldo", "INCOME", 5_000_000L, "Saldo inicial", "Saldo inicial", null, dia.minusMonths(2))
            // La memoria: el café ya se anotó como «Comida».
            evento("ev-cafe-antes", "EXPENSE", 12_000L, "Comida", "Tostao", "TOSTAO CAFE", dia.minusDays(20))
            // «¿Ya lo anotaste?»: el Éxito de $80.000 ya está a mano.
            evento("ev-exito-a-mano", "EXPENSE", 80_000L, "Mercado", "Éxito", null, dia)

            aviso("sms_cafe", textoDelCafe, "2026-10-10 08:10")
            aviso("sms_exito", textoDelExito, "2026-10-10 09:30")
            aviso("sms_a_caro", textoACaro, "2026-10-10 10:00")
            aviso("sms_pago", delPagoSms, "2026-10-10 11:15")
            aviso("notif_pago", delPagoApp, "2026-10-10 11:16", banco = "Notificación · Bancolombia")
            aviso("sms_del_otro", textoDelCafe, "2026-10-10 08:10", dueno = otro)
        }
    }

    private fun evento(id: String, tipo: String, monto: Long, categoria: String, descripcion: String, comercio: String?, fecha: LocalDate) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = ahorros
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = descripcion
            it[merchant] = comercio
            it[timestamp] = appDateToEpochMillis(fecha) + 9 * 3_600_000L
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

    private suspend fun ApplicationTestBuilder.propuestas(quien: String = uid): PropuestasDeLaBandeja {
        val respuesta = cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token(quien)}") }
        assertEquals(HttpStatusCode.OK, respuesta.status)
        return respuesta.body()
    }

    private suspend fun ApplicationTestBuilder.parse(id: String): ParsedSms =
        cliente().get("/api/sms/$id/parse") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private suspend fun ApplicationTestBuilder.coincidencias(id: String): List<FinancialEvent> =
        cliente().get("/api/sms/$id/coincidencias") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    /** El movimiento que arma la fila: el mismo de Reconciliar (`movimientoConfirmadoDelSms`). */
    private fun movimientoDelCafe(id: String) = FinancialEvent(
        id = id,
        accountId = ahorros,
        type = TransactionType.EXPENSE,
        amount = 46_000L,
        category = "Comida",
        description = "TOSTAO CAFE",
        merchant = "TOSTAO CAFE",
        timestamp = momentoDelSms("2026-10-10 08:10", ahora = System.currentTimeMillis()),
        source = EventSource.SMS,
        rawPayload = textoDelCafe,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun estado(id: String): Pair<String, String?> = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq id }.first().let { it[SmsMessages.state] to it[SmsMessages.eventoId] }
    }

    private fun cuantosConEsteMonto(monto: Long): Int = transaction {
        Events.selectAll().where { (Events.userId eq uid) and (Events.amount eq monto) }.count().toInt()
    }

    @Test
    fun `una propuesta por pago, la misma de parse y de coincidencias`() = testApplication {
        application { testModule() }
        val leidas = propuestas()

        // Cuatro pagos: el café, el Éxito, el envío a Caro y la panadería avisada dos veces.
        assertEquals(setOf("sms_cafe", "sms_exito", "sms_a_caro"), leidas.propuestas.filter { it.grupoId == null }.map { it.avisoId }.toSet())
        val delPago = leidas.propuestas.single { it.grupoId != null }
        assertTrue(delPago.avisoId in setOf("sms_pago", "notif_pago"))
        assertEquals(4, leidas.propuestas.size)

        // La misma propuesta que Reconciliar: la memoria dijo «Comida» y lo explica.
        val cafe = leidas.propuestas.single { it.avisoId == "sms_cafe" }
        assertEquals(parse("sms_cafe"), cafe.leido)
        assertEquals("Comida", cafe.leido!!.category)
        assertNotNull(cafe.leido!!.aprendidoDe)
        assertTrue(cafe.coincidencias.isEmpty())
        assertFalse(cafe.destinoSinNombre)

        // «¿Ya lo anotaste?» viaja con la propuesta: la fila no puede ofrecer «Confirmar».
        val exito = leidas.propuestas.single { it.avisoId == "sms_exito" }
        assertEquals(coincidencias("sms_exito").map { it.id }, exito.coincidencias.map { it.id })
        assertEquals(listOf("ev-exito-a-mano"), exito.coincidencias.map { it.id })

        // Una cuenta que nadie guardó: primero hay que decir de quién es.
        assertTrue(leidas.propuestas.single { it.avisoId == "sms_a_caro" }.destinoSinNombre)

        assertTrue("Comida" in leidas.categoriasDelDueno)
        assertNotNull(leidas.inicioDeLasCuentas[ahorros])
    }

    @Test
    fun `cada usuario ve solo sus avisos`() = testApplication {
        application { testModule() }
        val delOtroLeidas = propuestas(otro)
        assertEquals(listOf("sms_del_otro"), delOtroLeidas.propuestas.map { it.avisoId })
        assertTrue(propuestas().propuestas.none { it.avisoId == "sms_del_otro" })
        // Y su memoria no se mezcla: el otro nunca anotó «Comida».
        assertTrue("Comida" !in delOtroLeidas.categoriasDelDueno)
        assertNull(delOtroLeidas.propuestas.single().leido!!.aprendidoDe)
    }

    @Test
    fun `confirmar desde la fila va por el mismo camino que Reconciliar y es idempotente`() = testApplication {
        application { testModule() }
        // El camino de un aviso suelto: el movimiento y el aviso con él. Dos veces, como un doble toque.
        repeat(2) {
            val creado = cliente().post("/api/events") {
                header(HttpHeaders.Authorization, "Bearer ${token()}")
                contentType(ContentType.Application.Json)
                setBody(movimientoDelCafe("ev-fila-cafe"))
            }
            assertTrue(creado.status.isSuccess(), creado.status.toString())
            val confirmado = cliente().post("/api/sms/sms_cafe/confirm?eventoId=ev-fila-cafe") {
                header(HttpHeaders.Authorization, "Bearer ${token()}")
            }
            assertEquals(HttpStatusCode.OK, confirmado.status)
        }
        assertEquals(1, cuantosConEsteMonto(46_000L))
        assertEquals(SMS_STATE_CONFIRMED to "ev-fila-cafe", estado("sms_cafe"))
        // Un tercer confirmar con OTRO movimiento no mueve el que quedó.
        cliente().post("/api/sms/sms_cafe/confirm?eventoId=ev-otro") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(SMS_STATE_CONFIRMED to "ev-fila-cafe", estado("sms_cafe"))
        // Y sale de la bandeja.
        assertTrue(propuestas().propuestas.none { it.avisoId == "sms_cafe" })
    }

    @Test
    fun `confirmar un pago avisado dos veces desde la fila crea un solo movimiento aunque se toque dos veces`() = testApplication {
        application { testModule() }
        val grupo = propuestas().propuestas.single { it.grupoId != null }
        val miembros = listOf("sms_pago", "notif_pago")
        val evento = movimientoDelCafe("ev-fila-panaderia").copy(amount = 12_300L, description = "PANADERIA", merchant = "PANADERIA", rawPayload = delPagoSms)
        val respuestas = (1..2).map {
            cliente().post("/api/sms/grupo/${grupo.grupoId}/confirmar") {
                header(HttpHeaders.Authorization, "Bearer ${token()}")
                contentType(ContentType.Application.Json)
                setBody(ConfirmarElMismoPago(miembros, evento = evento))
            }.body<MismoPagoConfirmado>()
        }
        assertEquals(listOf(true, false), respuestas.map { it.creado })
        assertEquals(1, cuantosConEsteMonto(12_300L))
        miembros.forEach { assertEquals(SMS_STATE_CONFIRMED to "ev-fila-panaderia", estado(it)) }
        assertTrue(propuestas().propuestas.none { it.grupoId == grupo.grupoId })
    }

    @Test
    fun `un aviso de otro usuario no se confirma desde la fila`() = testApplication {
        application { testModule() }
        val respuesta = cliente().post("/api/sms/grupo/sms_del_otro/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(ConfirmarElMismoPago(listOf("sms_del_otro"), evento = movimientoDelCafe("ev-ajeno")))
        }
        assertEquals(HttpStatusCode.NotFound, respuesta.status)
        assertEquals(SMS_STATE_PENDING to null, estado("sms_del_otro"))
    }

    @Test
    fun `un envio a alguien guardado toma el motivo del ultimo envio por el mismo monto`() = testApplication {
        application { testModule() }
        transaction {
            KnownDestinations.insert {
                it[id] = "dst_caro"; it[userId] = uid; it[nombre] = "Caro"; it[numero] = "31973270756"; it[createdAt] = 0L
            }
            // Dos envíos anteriores a Caro, renombrados por el dueño: el de $150.000 fue el mercado.
            Events.insert {
                it[id] = "ev-caro-mercado"; it[userId] = uid; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = 150_000L
                it[category] = "Mercado"; it[description] = "Mercado"
                it[rawPayload] = "Bancolombia: Transferiste \$150.000 desde tu cuenta *8133 a la cuenta *31973270756 el 10/09/2026."
                it[timestamp] = appDateToEpochMillis(dia.minusDays(30)); it[eventSource] = "SMS"; it[reconciliationStatus] = "RECONCILED"
            }
            Events.insert {
                it[id] = "ev-caro-tia"; it[userId] = uid; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = 500_000L
                it[category] = "Tía"; it[description] = "Mesada tía"
                it[rawPayload] = "Bancolombia: Transferiste \$500.000 desde tu cuenta *8133 a la cuenta *31973270756 el 01/10/2026."
                it[timestamp] = appDateToEpochMillis(dia.minusDays(9)); it[eventSource] = "SMS"; it[reconciliationStatus] = "RECONCILED"
            }
        }
        val leido = parse("sms_a_caro")
        assertEquals("Mercado", leido.category)
        assertEquals("La última vez que le mandaste \$150.000 a Caro fue Mercado", leido.aprendidoDe)
        // Y en la bandeja: ya tiene nombre, monto, cuenta y motivo.
        val propuesta = propuestas().propuestas.single { it.avisoId == "sms_a_caro" }
        assertEquals("Mercado", propuesta.leido!!.category)
        assertFalse(propuesta.destinoSinNombre)
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299
}
