package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AvisosDelMismoPago
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.RespuestaDelSync
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.cuantosPagosPorRevisar
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import kotlin.test.assertTrue

/**
 * # Un pago, una tarjeta — de punta a punta
 *
 * Los avisos suben por `POST /api/sms/sync` como los sube el teléfono (y el correo entrante), y se
 * leen por `GET /api/sms` como los lee la bandeja. Textos de ejemplo, con la forma de cada canal.
 */
class AvisosDelMismoPagoRoutesTest {

    private val testSecret = "test-secret-for-mismo-pago-tests-minimum-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-mismo-pago"
    private val b = "user-b-mismo-pago"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:mismo_pago_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, PushSubscriptions, KnownDestinations, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, SmsMessages, PushSubscriptions, KnownDestinations)
            for (id in listOf(a, b)) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@mismo-pago.test"
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
        .withClaim("userId", uid).withClaim("email", "$uid@mismo-pago.test")
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

    private fun aviso(id: String, bank: String, texto: String, time: String = "2026-09-25 09:15") =
        SmsMessage(id = id, time = time, bank = bank, text = texto, state = "", det = "")

    private val delSms = aviso("sms_rt_1", "85540", "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111, el 25/09/2026 a las 09:15.")
    private val deLaApp = aviso("notif_app", "Notificación · Bancolombia", "Compraste \$12.300,00 en PANADERIA LA ESQUINA con tu T.Deb *1111.")
    private val deWallet = aviso("notif_wallet", "Notificación · Google Wallet", "PANADERIA LA ESQUINA: COP12,300 with Debito Mastercard ••1111")

    private suspend fun ApplicationTestBuilder.subir(uid: String, vararg mensajes: SmsMessage): RespuestaDelSync =
        cliente().post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }.body()

    private suspend fun ApplicationTestBuilder.bandeja(uid: String): List<SmsMessage> =
        cliente().get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun ApplicationTestBuilder.postear(uid: String, ruta: String, cuerpo: Any? = null): HttpStatusCode =
        cliente().post(ruta) {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            if (cuerpo != null) {
                contentType(ContentType.Application.Json)
                setBody(cuerpo)
            }
        }.status

    @Test
    fun `SMS, notificacion y Google Wallet del mismo pago llegan como un pago de tres`() = testApplication {
        application { testModule() }
        val respuesta = subir(a, deWallet, delSms, deLaApp)

        assertEquals(3, respuesta.synced)
        // El teléfono avisa una vez: «Movi anotó $12.300», no tres.
        assertEquals(listOf("notif_wallet"), respuesta.porRevisar.map { it.id })

        val lista = bandeja(a)
        assertEquals(1, lista.map { it.grupoId }.distinct().size)
        assertEquals(setOf("notif_app", "notif_wallet", "sms_rt_1"), lista.single { it.id == "sms_rt_1" }.miembrosDelGrupo.toSet())
        assertEquals(1, cuantosPagosPorRevisar(lista))

        // El detalle de uno también lo sabe.
        val uno: SmsMessage = cliente().get("/api/sms/notif_app") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }.body()
        assertEquals(lista.first().grupoId, uno.grupoId)
    }

    @Test
    fun `el aviso que llega despues de otro del mismo pago no vuelve a sonar`() = testApplication {
        application { testModule() }
        assertEquals(listOf("notif_wallet"), subir(a, deWallet).porRevisar.map { it.id })
        assertEquals(emptyList(), subir(a, delSms).porRevisar.map { it.id })
        assertEquals(1, cuantosPagosPorRevisar(bandeja(a)))
    }

    @Test
    fun `el correo que llega tarde se junta con el SMS`() = testApplication {
        application { testModule() }
        val correo = aviso(
            "correo_1", "Correo · Bancolombia",
            "Bancolombia le informa compra por \$12.300,00 en PANADERIA LA ESQUINA con su T.Deb *1111.",
            time = "2026-09-25 09:55",
        )
        subir(a, delSms, correo)
        val lista = bandeja(a)
        assertEquals(setOf("sms_rt_1"), lista.map { it.grupoId }.toSet())
    }

    @Test
    fun `No son el mismo pago separa para siempre, aunque llegue otro aviso igual`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        val grupoId = bandeja(a).first().grupoId!!

        assertEquals(
            HttpStatusCode.NoContent,
            postear(a, "/api/sms/grupo/$grupoId/desagrupar", AvisosDelMismoPago(listOf("notif_wallet", "sms_rt_1", "notif_app"))),
        )
        assertTrue(bandeja(a).all { it.grupoId == null && it.parecidoA == null })
        assertEquals(3, cuantosPagosPorRevisar(bandeja(a)))

        // Un correo del mismo monto media hora después: no se junta con ninguno de los separados.
        val correo = aviso("correo_1", "Correo · Bancolombia", "Compra por \$12.300,00 en PANADERIA LA ESQUINA *1111.", time = "2026-09-25 09:45")
        subir(a, correo)
        assertTrue(bandeja(a).all { it.grupoId == null })
    }

    @Test
    fun `Este es otro pago saca a uno solo, y los demas siguen juntos`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        assertEquals(HttpStatusCode.NoContent, postear(a, "/api/sms/notif_app/no-es-el-mismo-pago"))
        val lista = bandeja(a).associateBy { it.id }
        assertNull(lista.getValue("notif_app").grupoId)
        assertEquals(lista.getValue("sms_rt_1").grupoId, lista.getValue("notif_wallet").grupoId)
        assertEquals(2, cuantosPagosPorRevisar(lista.values.toList()))
    }

    @Test
    fun `cada usuario ve solo sus pagos, y no puede separar los avisos de otro`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms)
        subir(b, deLaApp)
        // B tiene un solo aviso: no se junta con los de A aunque sean del mismo pago.
        assertNull(bandeja(b).single().grupoId)

        assertEquals(HttpStatusCode.NotFound, postear(b, "/api/sms/notif_wallet/no-es-el-mismo-pago"))
        assertEquals(HttpStatusCode.NotFound, postear(b, "/api/sms/grupo/sms_rt_1/desagrupar", AvisosDelMismoPago(listOf("sms_rt_1", "notif_wallet"))))
        assertEquals(1, bandeja(a).map { it.grupoId }.toSet().size)
        assertTrue(bandeja(a).all { it.grupoId != null })
    }

    /** Lo que deserializa el APK 1.66: los campos de antes, con `ignoreUnknownKeys`. */
    @Serializable
    private data class SmsDelApkViejo(
        val id: String, val time: String, val bank: String, val text: String, val state: String, val det: String,
        val parecidoA: String? = null,
    )

    @Test
    fun `un APK viejo sigue andando - sube sin los campos nuevos y lee la bandeja con la advertencia de antes`() = testApplication {
        application { testModule() }
        val crudo = """[{"id":"sms_rt_1","time":"2026-09-25 09:15","bank":"85540","text":"${delSms.text}","state":"","det":""},
            {"id":"notif_wallet","time":"2026-09-25 09:15","bank":"Notificación · Google Wallet","text":"${deWallet.text}","state":"","det":""}]"""
        val sync = cliente().post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(a)}")
            contentType(ContentType.Application.Json)
            setBody(crudo)
        }
        assertEquals(HttpStatusCode.OK, sync.status, sync.bodyAsText())

        val texto = cliente().get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }.bodyAsText()
        val viejos = Json { ignoreUnknownKeys = true }.decodeFromString<List<SmsDelApkViejo>>(texto)
        assertEquals(2, viejos.size)
        assertTrue(viejos.all { it.state == SMS_STATE_PENDING })
        // El APK viejo no sabe de pagos, pero sigue viendo «Parece el mismo pago que…».
        assertEquals(setOf("sms_rt_1", "notif_wallet"), viejos.mapNotNull { it.parecidoA }.toSet())

        // Y un cliente nuevo contra un server viejo: sin los campos, todo en su default.
        val sinCampos = Json { ignoreUnknownKeys = true }.decodeFromString<SmsMessage>(
            """{"id":"x","time":"2026-09-25 09:15","bank":"85540","text":"t","state":"pending","det":""}""",
        )
        assertNull(sinCampos.grupoId)
        assertEquals(emptyList(), sinCampos.miembrosDelGrupo)
        assertNull(sinCampos.yaAnotadoCon)
    }

    // ── Confirmar, ignorar, ya anotado ─────────────────────────────────────────

    private fun movimiento(uid: String, id: String = "ev_panaderia") = FinancialEvent(
        id = id, accountId = "cuenta-$uid", type = TransactionType.EXPENSE, amount = 12_300,
        category = "Comida", description = "PANADERIA LA ESQ", timestamp = 1_790_000_000_000L,
    )

    private suspend fun ApplicationTestBuilder.grupo(uid: String, grupoId: String): GrupoDeAvisos =
        cliente().get("/api/sms/grupo/$grupoId") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun ApplicationTestBuilder.confirmar(uid: String, grupoId: String, pedido: ConfirmarElMismoPago) =
        cliente().post("/api/sms/grupo/$grupoId/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }

    private fun movimientosDe(uid: String): Long = transaction { Events.selectAll().where { Events.userId eq uid }.count() }

    private fun enlaces(uid: String): Map<String, String?> = transaction {
        SmsMessages.selectAll().where { SmsMessages.userId eq uid }.associate { it[SmsMessages.id] to it[SmsMessages.eventoId] }
    }

    @Test
    fun `los avisos del pago traen primero el que mas dice`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        val g = grupo(a, bandeja(a).first().grupoId!!)
        // Los tres traen comercio y cuenta: gana el SMS, el registro del banco.
        assertEquals("sms_rt_1", g.propuestaDe)
        assertEquals("sms_rt_1", g.miembros.first().id)
        assertEquals(3, g.miembros.size)
        assertNull(g.yaAnotadoCon)
        assertEquals(HttpStatusCode.NotFound, cliente().get("/api/sms/grupo/no-existe") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }.status)
    }

    @Test
    fun `confirmar el pago crea un movimiento y cierra los tres avisos`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        val g = grupo(a, bandeja(a).first().grupoId!!)

        val respuesta = confirmar(a, g.grupoId, ConfirmarElMismoPago(g.miembros.map { it.id }, evento = movimiento(a)))
        assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
        val hecho: MismoPagoConfirmado = respuesta.body()
        assertTrue(hecho.creado)
        assertEquals("ev_panaderia", hecho.eventoId)
        assertEquals(3, hecho.cerrados.size)

        assertEquals(1, movimientosDe(a))
        assertTrue(bandeja(a).all { it.state == SMS_STATE_CONFIRMED })
        assertEquals(setOf("ev_panaderia"), enlaces(a).values.toSet())
        assertEquals(0, cuantosPagosPorRevisar(bandeja(a)))
    }

    @Test
    fun `el doble toque no crea dos movimientos`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        val g = grupo(a, bandeja(a).first().grupoId!!)
        val ids = g.miembros.map { it.id }

        // Dos toques: cada uno con su propio id de movimiento, como los arma la app.
        val primero: MismoPagoConfirmado = confirmar(a, g.grupoId, ConfirmarElMismoPago(ids, evento = movimiento(a, "ev_1"))).body()
        val segundo = confirmar(a, g.grupoId, ConfirmarElMismoPago(ids, evento = movimiento(a, "ev_2")))
        assertEquals(HttpStatusCode.OK, segundo.status)
        val repetido: MismoPagoConfirmado = segundo.body()

        assertTrue(primero.creado)
        assertEquals(false, repetido.creado)
        assertEquals("ev_1", repetido.eventoId)
        assertEquals(1, movimientosDe(a))
    }

    @Test
    fun `con un aviso ya confirmado, el pago se muestra ya anotado y se cierra sin crear nada`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        // Aprobó uno por el camino de antes (un APK viejo).
        assertEquals(HttpStatusCode.OK, cliente().post("/api/sms/notif_wallet/confirm") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }.status)

        val lista = bandeja(a).associateBy { it.id }
        assertEquals("notif_wallet", lista.getValue("sms_rt_1").yaAnotadoCon)
        assertEquals("notif_wallet", lista.getValue("notif_app").yaAnotadoCon)
        val g = grupo(a, lista.getValue("sms_rt_1").grupoId!!)
        assertEquals("notif_wallet", g.yaAnotadoCon)

        // «Cerrar»: sin movimiento.
        val cierre: MismoPagoConfirmado = confirmar(a, g.grupoId, ConfirmarElMismoPago(g.miembros.map { it.id })).body()
        assertEquals(false, cierre.creado)
        assertEquals(setOf("sms_rt_1", "notif_app"), cierre.cerrados.toSet())
        assertEquals(0, movimientosDe(a))
        assertEquals(0, cuantosPagosPorRevisar(bandeja(a)))
    }

    @Test
    fun `Es este enlaza los avisos al movimiento que ya estaba`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms)
        transaction {
            Events.insert {
                it[id] = "ev_a_mano"; it[userId] = a; it[accountId] = "cuenta-$a"; it[type] = "EXPENSE"; it[amount] = 12_300
                it[category] = "Comida"; it[description] = "Panadería"; it[merchant] = null; it[timestamp] = 1_790_000_000_000L
                it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
            }
        }
        val grupoId = bandeja(a).first().grupoId!!
        val hecho: MismoPagoConfirmado = confirmar(a, grupoId, ConfirmarElMismoPago(listOf("sms_rt_1", "notif_wallet"), eventoExistenteId = "ev_a_mano")).body()
        assertEquals(false, hecho.creado)
        assertEquals(1, movimientosDe(a))
        assertEquals(setOf("ev_a_mano"), enlaces(a).values.toSet())
    }

    @Test
    fun `sin movimiento ni nada anotado no se cierra, y un rechazo no deja nada a medias`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms)
        val grupoId = bandeja(a).first().grupoId!!
        val ids = listOf("sms_rt_1", "notif_wallet")
        assertEquals(HttpStatusCode.Conflict, confirmar(a, grupoId, ConfirmarElMismoPago(ids)).status)
        // La cuenta de otro usuario: 404, y ningún aviso quedó confirmado.
        assertEquals(HttpStatusCode.NotFound, confirmar(a, grupoId, ConfirmarElMismoPago(ids, evento = movimiento(b))).status)
        assertEquals(HttpStatusCode.BadRequest, confirmar(a, grupoId, ConfirmarElMismoPago(ids, evento = movimiento(a).copy(amount = 0))).status)
        assertEquals(0, movimientosDe(a))
        assertTrue(bandeja(a).all { it.state == SMS_STATE_PENDING })
    }

    @Test
    fun `ignorar el pago ignora todos sus avisos`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms, deLaApp)
        val grupoId = bandeja(a).first().grupoId!!
        assertEquals(HttpStatusCode.NoContent, postear(a, "/api/sms/grupo/$grupoId/ignorar", AvisosDelMismoPago(listOf("sms_rt_1", "notif_wallet", "notif_app"))))
        assertTrue(bandeja(a).all { it.state == SMS_STATE_IGNORED })
        assertEquals(0, cuantosPagosPorRevisar(bandeja(a)))
    }

    @Test
    fun `otro usuario no puede ver, confirmar ni ignorar el pago`() = testApplication {
        application { testModule() }
        subir(a, deWallet, delSms)
        val grupoId = bandeja(a).first().grupoId!!
        val ids = listOf("sms_rt_1", "notif_wallet")
        assertEquals(HttpStatusCode.NotFound, cliente().get("/api/sms/grupo/$grupoId") { header(HttpHeaders.Authorization, "Bearer ${token(b)}") }.status)
        assertEquals(HttpStatusCode.NotFound, confirmar(b, grupoId, ConfirmarElMismoPago(ids, evento = movimiento(b))).status)
        assertEquals(HttpStatusCode.NotFound, postear(b, "/api/sms/grupo/$grupoId/ignorar", AvisosDelMismoPago(ids)))
        assertEquals(0, movimientosDe(b))
        assertTrue(bandeja(a).all { it.state == SMS_STATE_PENDING })
    }
}
