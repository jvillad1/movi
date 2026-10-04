package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Migrations.apartarLosPendientesQueNoSonMovimientos
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.MOTIVO_DEVUELTO
import com.jvillada.movi.shared.model.RespuestaDelSync
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.idDeComprobante
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Los mensajes que no son movimientos llegan apartados, y se pueden devolver
 *
 * El recorrido entero del 3-oct-2026: un código de verificación sube por `POST /api/sms/sync` junto
 * con una compra; la compra queda esperando en «Por revisar» y el código se guarda `ignored` con su
 * motivo, que el historial muestra. «Era un movimiento» lo devuelve, y ni la pasada del arranque lo
 * vuelve a apartar. Todo, por usuario.
 */
class MensajesApartadosTest {

    private val testSecret = "test-secret-for-apartados-tests-minimum-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-apartados"
    private val b = "user-b-apartados"

    private val compra = "Bancolombia: Compraste \$23.400,00 en TIENDA DE PRUEBA con tu T.Deb *1111, el 01/10/2026 a las 10:00."
    private val codigo = "Bancolombia: Tu clave dinamica es 482913. Es personal e intransferible."
    private val promo = "Bancolombia: hasta 40% dcto en tecnología pagando con nuestras tarjetas. Aplican TyC"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:mensajes_apartados_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions)
            for (id in listOf(a, b)) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@apartados.test"
                    it[name] = id
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@apartados.test")
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

    private fun sms(id: String, texto: String, time: String = "2026-10-01 10:00") =
        SmsMessage(id = id, time = time, bank = "85540", text = texto, state = "", det = "")

    private suspend fun ApplicationTestBuilder.subir(uid: String, vararg mensajes: SmsMessage): RespuestaDelSync =
        cliente().post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }.body()

    private suspend fun ApplicationTestBuilder.bandeja(uid: String): List<SmsMessage> =
        cliente().get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun ApplicationTestBuilder.eraUnMovimiento(uid: String, id: String): HttpStatusCode =
        cliente().post("/api/sms/$id/era-un-movimiento") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.status

    @Test
    fun `lo que no es movimiento entra apartado con su motivo, y no queda por revisar`() = testApplication {
        application { testModule() }

        val respuesta = subir(
            a,
            sms("sms_rt_compra", compra),
            sms("sms_rt_codigo", codigo, time = "2026-10-01 10:05"),
            sms("sms_rt_promo", promo, time = "2026-10-01 10:10"),
        )

        // Los tres se guardan: apartar no es perder.
        assertEquals(3, respuesta.synced)
        // El «Movi anotó» del teléfono habla solo de la compra.
        assertEquals(listOf("sms_rt_compra"), respuesta.porRevisar.map { it.id })

        val lista = bandeja(a).associateBy { it.id }
        assertEquals(SMS_STATE_PENDING, lista.getValue("sms_rt_compra").state)
        assertNull(lista.getValue("sms_rt_compra").apartadoPor)
        assertEquals(SMS_STATE_IGNORED, lista.getValue("sms_rt_codigo").state)
        assertEquals("CODIGO_DE_VERIFICACION", lista.getValue("sms_rt_codigo").apartadoPor)
        assertEquals("PROMOCION", lista.getValue("sms_rt_promo").apartadoPor)
        // «N por revisar» cuenta los pendientes: uno.
        assertEquals(1, lista.values.count { it.state == SMS_STATE_PENDING })
    }

    @Test
    fun `Era un movimiento lo devuelve a la bandeja, y la pasada del arranque no lo vuelve a apartar`() = testApplication {
        application { testModule() }
        subir(a, sms("sms_codigo", codigo))

        assertEquals(HttpStatusCode.NoContent, eraUnMovimiento(a, "sms_codigo"))

        val devuelto = bandeja(a).single()
        assertEquals(SMS_STATE_PENDING, devuelto.state)
        assertNull(devuelto.apartadoPor, "la marca DEVUELTO no viaja al cliente")
        assertEquals(MOTIVO_DEVUELTO, transaction { SmsMessages.selectAll().single()[SmsMessages.motivoApartado] })

        assertEquals(0, transaction { apartarLosPendientesQueNoSonMovimientos() })
        assertEquals(SMS_STATE_PENDING, bandeja(a).single().state)
        // Ya no está apartado: devolverlo otra vez no tiene sentido.
        assertEquals(HttpStatusCode.Conflict, eraUnMovimiento(a, "sms_codigo"))
    }

    @Test
    fun `lo que ignoro el dueno no se devuelve, y lo de otro usuario no existe`() = testApplication {
        application { testModule() }
        subir(a, sms("sms_compra", compra), sms("sms_codigo", codigo, time = "2026-10-01 10:05"))
        cliente().post("/api/sms/sms_compra/ignore") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }

        assertEquals(HttpStatusCode.Conflict, eraUnMovimiento(a, "sms_compra"), "lo ignoró él: se queda así")
        assertEquals(HttpStatusCode.NotFound, eraUnMovimiento(b, "sms_codigo"), "B no ve los mensajes de A")
        assertEquals(emptyList(), bandeja(b))
        assertEquals("CODIGO_DE_VERIFICACION", bandeja(a).single { it.id == "sms_codigo" }.apartadoPor)
    }

    @Test
    fun `la pasada del arranque aparta los pendientes viejos y no toca nada mas`() {
        transaction {
            fun fila(id: String, uid: String, texto: String, estado: String, motivo: String? = null) = SmsMessages.insert {
                it[SmsMessages.id] = id
                it[userId] = uid
                it[time] = "2026-09-01 10:00"
                it[bank] = "85540"
                it[text] = texto
                it[state] = estado
                it[motivoApartado] = motivo
                it[det] = ""
            }
            fila("viejo_codigo_a", a, codigo, SMS_STATE_PENDING)
            fila("viejo_promo_b", b, promo, SMS_STATE_PENDING)
            fila("viejo_compra", a, compra, SMS_STATE_PENDING)
            fila("confirmado_raro", a, promo, SMS_STATE_CONFIRMED)
            fila("devuelto", a, codigo, SMS_STATE_PENDING, MOTIVO_DEVUELTO)
            fila(idDeComprobante("doc1"), a, promo, SMS_STATE_PENDING)
        }

        assertEquals(2, transaction { apartarLosPendientesQueNoSonMovimientos() })
        assertEquals(0, transaction { apartarLosPendientesQueNoSonMovimientos() }, "idempotente")

        val estados = transaction {
            SmsMessages.selectAll().associate { it[SmsMessages.id] to (it[SmsMessages.state] to it[SmsMessages.motivoApartado]) }
        }
        assertEquals(SMS_STATE_IGNORED to "CODIGO_DE_VERIFICACION", estados["viejo_codigo_a"])
        assertEquals(SMS_STATE_IGNORED to "PROMOCION", estados["viejo_promo_b"])
        assertEquals(SMS_STATE_PENDING to null, estados["viejo_compra"])
        assertEquals(SMS_STATE_CONFIRMED to null, estados["confirmado_raro"], "nunca se toca un confirmado")
        assertEquals(SMS_STATE_PENDING to MOTIVO_DEVUELTO, estados["devuelto"], "el dueño ya dijo que era un movimiento")
        assertEquals(SMS_STATE_PENDING to null, estados[idDeComprobante("doc1")], "lo que él compartió se revisa")
    }
}
