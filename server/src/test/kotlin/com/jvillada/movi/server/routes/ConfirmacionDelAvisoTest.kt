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
import com.jvillada.movi.shared.model.AvisoConfirmadoSinMovimiento
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
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
 * # Con qué movimiento se confirmó cada aviso (auditoría de la ingesta, arreglo 6)
 *
 * 15 avisos confirmados se quedaron sin movimiento vivo y no había cómo saber por qué: `/confirm`
 * no guardaba con qué movimiento. Acá: lo guarda, lo deduce para un APK viejo cuando se puede, no lo
 * mueve en un segundo toque, y el cuadre encuentra los que quedaron colgando.
 */
class ConfirmacionDelAvisoTest {

    private val testSecret = "test-secret-for-confirmacion-del-aviso-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-confirmacion"
    private val b = "user-b-confirmacion"

    private val hora = "2026-09-25 09:15"
    private val textoTostao = "Bancolombia: Compraste \$15.100,00 en TOSTAO CAFE Y PAN con tu T.Deb *1111, el 25/09/2026 a las 09:15."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:confirmacion_del_aviso_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

    // El texto trae la fecha y la hora de SU `time`, como un SMS real de Bancolombia: dos avisos de
    // días distintos con el mismo texto (y la misma fecha adentro) serían el mismo SMS para el
    // dedupe (`isSameSms`, desde el viaje a Argentina del 5-oct-2026).
    private fun aviso(id: String, texto: String? = null, time: String = hora) =
        SmsMessage(id = id, time = time, bank = "85540", text = texto ?: textoTostaoDe(time), state = "", det = "")

    private fun textoTostaoDe(time: String): String {
        val (fecha, horaDelDia) = time.split(" ")
        val (anio, mes, dia) = fecha.split("-")
        return "Bancolombia: Compraste \$15.100,00 en TOSTAO CAFE Y PAN con tu T.Deb *1111, el $dia/$mes/$anio a las $horaDelDia."
    }

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

    @Test
    fun `confirmar guarda con que movimiento y cuando`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        movimiento("ev_1", texto = textoTostao)

        assertEquals(HttpStatusCode.OK, confirmar(a, "sms_1", "ev_1"))

        val (estado, evento, cuando) = fila("sms_1")
        assertEquals(SMS_STATE_CONFIRMED, estado)
        assertEquals("ev_1", evento)
        assertNotNull(cuando)
    }

    @Test
    fun `un segundo toque no cambia lo que ya se escribio`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        confirmar(a, "sms_1", "ev_1")
        val primero = fila("sms_1")

        Thread.sleep(5)
        confirmar(a, "sms_1", "ev_otro")

        assertEquals(primero, fila("sms_1"))
    }

    @Test
    fun `un APK viejo sin eventoId - el server lo deduce por el texto del aviso`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        movimiento("ev_otro_mismo_monto", minutosDespues = 60)
        movimiento("ev_del_sms", texto = textoTostao)

        confirmar(a, "sms_1")

        assertEquals("ev_del_sms", fila("sms_1").second, "el texto exacto en raw_payload gana sobre el monto")
    }

    @Test
    fun `un APK viejo con Es este - una sola coincidencia se deduce`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        movimiento("ev_a_mano")

        confirmar(a, "sms_1")

        assertEquals("ev_a_mano", fila("sms_1").second)
    }

    @Test
    fun `con dos coincidencias no se adivina`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        movimiento("ev_1")
        movimiento("ev_2", minutosDespues = 30)

        confirmar(a, "sms_1")

        val (estado, evento, cuando) = fila("sms_1")
        assertEquals(SMS_STATE_CONFIRMED, estado)
        assertNull(evento, "mejor no saber que enlazar con el equivocado")
        assertNotNull(cuando)
    }

    @Test
    fun `un movimiento que ya reclamo otro aviso no se deduce dos veces`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"), aviso("sms_2", time = "2026-09-26 09:15"))
        movimiento("ev_1")
        confirmar(a, "sms_1", "ev_1")

        confirmar(a, "sms_2")

        assertNull(fila("sms_2").second)
    }

    @Test
    fun `el movimiento de otro usuario no se escribe`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_1"))
        movimiento("ev_de_b", uid = b)

        confirmar(a, "sms_1", "ev_de_b")

        assertNull(fila("sms_1").second)
    }

    @Test
    fun `el cuadre encuentra los confirmados cuyo movimiento se anulo o no existe`() = testApplication {
        application { testModule() }
        subir(
            a,
            aviso("sms_vivo"),
            aviso("sms_anulado", time = "2026-09-26 09:15"),
            aviso("sms_borrado", time = "2026-09-27 09:15"),
        )
        movimiento("ev_vivo")
        movimiento("ev_anulado", minutosDespues = 60 * 24)
        confirmar(a, "sms_vivo", "ev_vivo")
        confirmar(a, "sms_anulado", "ev_anulado")
        confirmar(a, "sms_borrado", "ev_que_no_existe")
        anular("ev_anulado")

        val sueltos: List<AvisoConfirmadoSinMovimiento> = cliente().get("/api/sms/confirmados-sin-movimiento") {
            header(HttpHeaders.Authorization, "Bearer ${token(a)}")
        }.body()

        assertEquals(
            listOf("sms_borrado" to AvisoConfirmadoSinMovimiento.BORRADO, "sms_anulado" to AvisoConfirmadoSinMovimiento.ANULADO),
            sueltos.map { it.avisoId to it.porQue },
        )
        // Y cada usuario ve solo los suyos.
        val deB: List<AvisoConfirmadoSinMovimiento> = cliente().get("/api/sms/confirmados-sin-movimiento") {
            header(HttpHeaders.Authorization, "Bearer ${token(b)}")
        }.body()
        assertTrue(deB.isEmpty())
    }

    @Test
    fun `confirmar el mismo pago guarda cuando, una sola vez`() = testApplication {
        application { testModule() }
        val wallet = SmsMessage(
            id = "notif_wallet", time = hora, bank = "Notificación · Google Wallet",
            text = "TOSTAO CAFE Y PAN: COP15,100 with Debito Mastercard ••1111", state = "", det = "",
        )
        subir(a, aviso("sms_1"), wallet)
        val grupoId = "sms_1"
        movimiento("ev_1")
        suspend fun cerrar(evento: String) = cliente().post("/api/sms/grupo/$grupoId/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token(a)}")
            contentType(ContentType.Application.Json)
            setBody(ConfirmarElMismoPago(listOf("sms_1", "notif_wallet"), eventoExistenteId = evento))
        }.status

        assertEquals(HttpStatusCode.OK, cerrar("ev_1"))
        val primero = listOf(fila("sms_1"), fila("notif_wallet"))
        assertTrue(primero.all { it.second == "ev_1" && it.third != null })

        Thread.sleep(5)
        cerrar("ev_1")
        assertEquals(primero, listOf(fila("sms_1"), fila("notif_wallet")))
    }
}
