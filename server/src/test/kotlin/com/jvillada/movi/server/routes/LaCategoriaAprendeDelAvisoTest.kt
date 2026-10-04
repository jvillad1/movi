package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AvisoPendienteParecido
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
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
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Que la categoría aprenda del aviso (auditoría de la ingesta, arreglo 7)
 *
 * La llave de Las Doce salió en 11 avisos y Movi nunca la aprendió: los movimientos que el dueño
 * anotó a mano no guardaban el texto del banco. Acá: confirmar un aviso con un movimiento ya anotado
 * le deja el comercio del banco, el aviso siguiente sale con su categoría, la memoria le gana a las
 * palabras clave, y «Agregar» encuentra el aviso pendiente con el mismo monto.
 */
class LaCategoriaAprendeDelAvisoTest {

    private val testSecret = "test-secret-for-confirmacion-del-aviso-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-aprende"
    private val b = "user-b-aprende"

    private val hora = "2026-09-25 09:15"
    private val textoTostao = "Bancolombia: Compraste \$15.100,00 en TOSTAO CAFE Y PAN con tu T.Deb *1111, el 25/09/2026 a las 09:15."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:la_categoria_aprende_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, PushSubscriptions, KnownDestinations, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, SmsMessages, PushSubscriptions, KnownDestinations)
            for (id in listOf(a, b)) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@confirmacion.test"
                    it[name] = id
                    it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[Accounts.id] = "cuenta-$id"
                    it[userId] = id
                    it[name] = "Ahorros 1111"
                    it[type] = "SAVINGS"
                    it[balance] = 0L
                    it[currency] = "COP"
                }
            }
        }
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@confirmacion.test")
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

    private suspend fun ApplicationTestBuilder.subir(uid: String, vararg mensajes: SmsMessage) {
        cliente().post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }
    }

    private suspend fun ApplicationTestBuilder.confirmar(uid: String, smsId: String, eventoId: String? = null): HttpStatusCode =
        cliente().post("/api/sms/$smsId/confirm" + (eventoId?.let { "?eventoId=$it" } ?: "")) {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        }.status

    private fun aviso(id: String, texto: String = textoTostao, time: String = hora) =
        SmsMessage(id = id, time = time, bank = "85540", text = texto, state = "", det = "")

    private val momento = momentoDelSms(hora, ahora = System.currentTimeMillis())

    private fun movimiento(id: String, uid: String = a, monto: Long = 15_100, texto: String? = null, minutosDespues: Int = 5) =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = "cuenta-$uid"
                it[type] = "EXPENSE"
                it[amount] = monto
                it[category] = "Comida"
                it[description] = "Tostao"
                it[timestamp] = momento + minutosDespues * 60_000L
                it[rawPayload] = texto
                it[createdAt] = momento + minutosDespues * 60_000L
            }
        }

    private fun anular(eventoId: String, uid: String = a) = transaction {
        VoidEvents.insert {
            it[id] = "void-$eventoId"
            it[userId] = uid
            it[originalEventId] = eventoId
            it[timestamp] = System.currentTimeMillis()
        }
    }

    private fun fila(smsId: String, uid: String = a) = transaction {
        SmsMessages.selectAll().where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }.single()
            .let { Triple(it[SmsMessages.state], it[SmsMessages.eventoId], it[SmsMessages.confirmadoEn]) }
    }

    private val textoLasDoce = "Bancolombia: JUAN CAMILO VILLADA RAMIREZ pagaste \$22,000.00 por codigo QR desde tu cuenta *8133 " +
        "a la llave 0047142708 el 29/09/2026 a las 12:45. Con codigo QR es facil y de una."

    private fun movimientoConMerchant(id: String, merchant: String?) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = a
            it[accountId] = "cuenta-$a"
            it[type] = "EXPENSE"
            it[amount] = 22_000
            it[category] = "Comida"
            it[description] = "Las Doce"
            it[Events.merchant] = merchant
            it[timestamp] = momentoDelSms("2026-09-29 12:45", System.currentTimeMillis())
        }
    }

    private fun merchantDe(id: String) = transaction {
        Events.selectAll().where { Events.id eq id }.single()[Events.merchant]
    }

    private suspend fun ApplicationTestBuilder.leer(smsId: String): ParsedSms =
        cliente().get("/api/sms/$smsId/parse") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }.body()

    @Test
    fun `Es este le deja al movimiento el comercio del banco, y el aviso siguiente ya sale con su categoria`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1", texto = textoLasDoce, time = "2026-09-29 12:45"))
        movimientoConMerchant("ev_las_doce", merchant = null)

        assertEquals(HttpStatusCode.OK, confirmar(a, "sms_1", "ev_las_doce"))
        assertEquals("Pago QR · llave 0047142708", merchantDe("ev_las_doce"))

        // El de la semana siguiente, a la misma llave.
        subir(a, aviso("sms_2", texto = textoLasDoce.replace("29/09/2026", "06/10/2026"), time = "2026-10-06 12:45"))
        val propuesta = leer("sms_2")
        assertEquals("Comida", propuesta.category)
        assertEquals("Las Doce", propuesta.merchant, "la llave no se lee; vuelve el nombre que él le puso")
    }

    @Test
    fun `el comercio que el movimiento ya tenia no se pisa`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1", texto = textoLasDoce, time = "2026-09-29 12:45"))
        movimientoConMerchant("ev_las_doce", merchant = "Lo que escribió el banco antes")

        confirmar(a, "sms_1", "ev_las_doce")

        assertEquals("Lo que escribió el banco antes", merchantDe("ev_las_doce"))
    }

    @Test
    fun `la memoria del dueno le gana a las palabras clave`() {
        val farmatodo = ParsedSms(15_000.0, "FARMATODO ALTO DE PA", TransactionType.EXPENSE, "Salud")
        val memoria = MemoriaDeCategorias.de(listOf(AnotacionPasada("FARMATODO ALTO DE PALM", "Farmatodo", "Comida", 10)))

        assertEquals("Comida", conLoQueMoviRecuerda(farmatodo, memoria).category, "él compra comida en Farmatodo")
    }

    @Test
    fun `sin memoria del comercio, las palabras clave hablan en su vocabulario`() {
        val carulla = ParsedSms(80_000.0, "CARULLA RINCON OVIED", TransactionType.EXPENSE, "Comida")
        val usaMercado = MemoriaDeCategorias.de(listOf(AnotacionPasada("Tienda", "Tienda", "Mercado", 10)))

        assertEquals("Mercado", conLoQueMoviRecuerda(carulla, usaMercado).category)
        assertEquals("Comida", conLoQueMoviRecuerda(carulla, MemoriaDeCategorias.vacia).category)
    }

    private fun haceHoras(horas: Long): String =
        java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Bogota")).minusHours(horas)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private suspend fun ApplicationTestBuilder.parecidos(monto: Long, tipo: String = "EXPENSE"): List<AvisoPendienteParecido> =
        cliente().get("/api/sms/pendientes-parecidos?monto=$monto&tipo=$tipo&moneda=COP") {
            header(HttpHeaders.Authorization, "Bearer ${token(a)}")
        }.body()

    @Test
    fun `Agregar encuentra el aviso pendiente con el mismo monto de las ultimas 48 horas`() = testApplication {
        application { testModule() }
        subir(
            a,
            aviso("sms_hace_2h", time = haceHoras(2)),
            aviso("sms_otro_monto", texto = textoTostao.replace("15.100", "16.100"), time = haceHoras(1)),
            aviso("sms_hace_3_dias", time = haceHoras(72)),
            aviso("sms_ya_confirmado", time = haceHoras(5)),
        )
        confirmar(a, "sms_ya_confirmado", "ev_x")

        val encontrados = parecidos(15_100)

        assertEquals(listOf("sms_hace_2h"), encontrados.map { it.id })
        assertEquals("TOSTAO CAFE Y PAN", encontrados.single().comercio)
        assertTrue(parecidos(15_100, tipo = "INCOME").isEmpty(), "otro tipo no es este")
        assertTrue(parecidos(99_999).isEmpty())
    }

    @Test
    fun `un pago avisado dos veces se ofrece una sola vez`() = testApplication {
        application { testModule() }
        val hora = haceHoras(1)
        val wallet = SmsMessage(
            id = "notif_wallet", time = hora, bank = "Notificación · Google Wallet",
            text = "TOSTAO CAFE Y PAN: COP15,100 with Debito Mastercard ••1111", state = "", det = "",
        )
        subir(a, aviso("sms_1", time = hora), wallet)

        assertEquals(1, parecidos(15_100).size)
    }

    @Test
    fun `sin monto o con un tipo que no existe es un 400`() = testApplication {
        application { testModule() }
        val sinMonto = cliente().get("/api/sms/pendientes-parecidos?tipo=EXPENSE") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        val tipoRaro = cliente().get("/api/sms/pendientes-parecidos?monto=1&tipo=GASTO") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.BadRequest, sinMonto.status)
        assertEquals(HttpStatusCode.BadRequest, tipoRaro.status)
    }
}
