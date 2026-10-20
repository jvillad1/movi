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
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MOTIVO_DEVUELTO
import com.jvillada.movi.shared.model.NoSePudoLeerElAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.comoLectura
import com.jvillada.movi.shared.model.momentoDelSms
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
 * # «Anotarlo igual»: un aviso que Movi no entiende, con monto, no deja al dueño trabado
 *
 * El 20-oct-2026 un aviso de Nu con un formato nuevo dejó Reconciliar con el botón apagado y
 * «Ignorar» como única salida. Acá: un aviso ilegible que trae un monto lo ofrece (con su moneda, su
 * fecha y el tipo propuesto); lo apartado, lo rechazado y lo que no es un movimiento no; sin monto,
 * nada; confirmarlo por el camino de siempre escribe un solo movimiento; la memoria lo recuerda; y
 * cada usuario ve solo lo suyo.
 *
 * Los textos son inventados con la forma de un aviso de Nu que Movi no conoce (ni «compra aprobada»,
 * ni «enviaste», ni «te llegó dinero»), y cada uno trae su fecha adentro: el dedupe del sync compara
 * el texto y el tiempo (#444).
 */
class AnotarloIgualTest {

    private val testSecret = "test-secret-for-anotarlo-igual-minimum-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-anotarlo-igual"
    private val b = "user-b-anotarlo-igual"
    private val deNu = "Notificación · Nu"

    private val movimosHora = "2026-10-18 10:12"
    private val movimos = "Nu: Movimos \$45.000,00 desde tu cuenta el 18/10/2026 a las 10:12. Si no reconoces este movimiento, escríbenos."
    private val entraronHora = "2026-10-18 16:40"
    private val entraron = "Nu: ¡Te llegó plata! Entraron \$250.000,00 a tu cuenta el 18/10/2026 a las 16:40."
    private val enUsd = "Nu: Movimos USD20,00 desde tu cuenta el 18/10/2026 a las 11:00."
    private val sinMonto = "Nu: Tu tarjeta física ya va en camino y llega el 25/10/2026."
    private val promocion = "Nu: Aprovecha 20% de dcto en tu próxima compra de \$100.000,00 hasta el 31/10/2026. Aplican TyC."
    private val recordatorio = "Nu: Tienes un pago por \$138.600,00 de tu tarjeta. Completa tu pago antes del 25/10/2026."
    private val rechazo = "Nu: Tu compra en RAPPI por \$45.000,00 del 18/10/2026 fue rechazada."
    private val ampliacion = "Bancolombia: realizaste la ampliacion de plazo de tu tarjeta por USD 1,202.49 el 18/10/2026."
    private val cajita = "Nu: Tu Cajita generó \$3.250,00 el 18/10/2026."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:anotarlo_igual_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            for (id in listOf(a, b)) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@anotarlo.test"
                    it[name] = id
                    it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[Accounts.id] = "nu-$id"
                    it[userId] = id
                    it[name] = "Nu"
                    it[type] = "SAVINGS"
                    it[balance] = 0L
                    it[currency] = "COP"
                }
            }
        }
    }

    // ── El criterio, sin base ───────────────────────────────────────────────────

    private fun pendiente(texto: String, origen: String = deNu) =
        SmsMessage(id = "x", time = movimosHora, bank = origen, text = texto, state = SMS_STATE_PENDING, det = "")

    @Test
    fun `un aviso ilegible con monto trae el monto, la moneda y el tipo`() {
        assertNull(parseSms(movimos, deNu), "si Movi ya lo leyera, esta prueba no probaría nada")
        val sinLeer = assertNotNull(loQueTraeUnAvisoSinLeer(pendiente(movimos)))
        assertEquals(45_000.0, sinLeer.monto)
        assertEquals("COP", sinLeer.moneda)
        assertEquals(TransactionType.EXPENSE, sinLeer.tipo)

        assertEquals("USD", loQueTraeUnAvisoSinLeer(pendiente(enUsd))?.moneda)
    }

    @Test
    fun `te llego es un ingreso, lo demas un gasto`() {
        assertNull(parseSms(entraron, deNu))
        assertEquals(TransactionType.INCOME, loQueTraeUnAvisoSinLeer(pendiente(entraron))?.tipo)
    }

    @Test
    fun `lo que no es un movimiento no se ofrece, aunque siga pendiente`() {
        for (texto in listOf(promocion, recordatorio, rechazo, ampliacion, cajita)) {
            assertNull(loQueTraeUnAvisoSinLeer(pendiente(texto)), "se ofreció anotar: $texto")
        }
    }

    @Test
    fun `sin monto, nada`() {
        assertNull(loQueTraeUnAvisoSinLeer(pendiente(sinMonto)))
    }

    @Test
    fun `lo apartado, ignorado o ya confirmado no se ofrece`() {
        val aviso = pendiente(movimos)
        assertNull(loQueTraeUnAvisoSinLeer(aviso.copy(state = SMS_STATE_IGNORED, apartadoPor = "PROMOCION")))
        assertNull(loQueTraeUnAvisoSinLeer(aviso.copy(state = SMS_STATE_IGNORED)))
        assertNull(loQueTraeUnAvisoSinLeer(aviso.copy(state = SMS_STATE_CONFIRMED)))
    }

    // ── Por la ruta ─────────────────────────────────────────────────────────────

    @Test
    fun `parse con anotarloIgual trae lo que trae el aviso, con la fecha del aviso`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))

        val respuesta = parse(a, "notif_movimos")

        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        val cuerpo = respuesta.body<NoSePudoLeerElAviso>()
        assertEquals(NO_TRAE_UN_MOVIMIENTO, cuerpo.motivo)
        val sinLeer = assertNotNull(cuerpo.sinLeer)
        assertEquals(45_000.0, sinLeer.monto)
        assertEquals("COP", sinLeer.moneda)
        assertEquals(TransactionType.EXPENSE, sinLeer.tipo)
        assertEquals(momentoDelSms(movimosHora, ahora = System.currentTimeMillis()), sinLeer.momento)
        assertNull(sinLeer.categoria, "sin memoria no se inventa una categoría")
    }

    @Test
    fun `sin el parametro el 422 sigue siendo el texto de siempre`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))

        val respuesta = cliente().get("/api/sms/notif_movimos/parse") { autorizado(a) }

        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        assertEquals(NO_TRAE_UN_MOVIMIENTO, respuesta.bodyAsText(), "el APK instalado muestra este texto tal cual")
    }

    @Test
    fun `lo apartado al llegar no trae monto para anotar`() = testApplication {
        application { testModule() }
        subir(
            a,
            aviso("notif_promo", promocion, "2026-10-18 09:00"),
            aviso("notif_recordatorio", recordatorio, "2026-10-18 09:05"),
            aviso("notif_rechazo", rechazo, "2026-10-18 09:10"),
        )

        for (id in listOf("notif_promo", "notif_recordatorio", "notif_rechazo")) {
            assertEquals(SMS_STATE_IGNORED, estado(id).first, "$id no se apartó al llegar")
            val cuerpo = parse(a, id).body<NoSePudoLeerElAviso>()
            assertNull(cuerpo.sinLeer, "$id ofreció anotarlo igual")
        }
    }

    @Test
    fun `un rechazo devuelto con Era un movimiento tampoco se anota`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_rechazo", rechazo, "2026-10-18 09:10"))
        val devuelto = cliente().post("/api/sms/notif_rechazo/era-un-movimiento") { autorizado(a) }
        assertEquals(HttpStatusCode.NoContent, devuelto.status)
        assertEquals(SMS_STATE_PENDING, estado("notif_rechazo").first)
        assertEquals(MOTIVO_DEVUELTO, motivo("notif_rechazo"))

        assertNull(parse(a, "notif_rechazo").body<NoSePudoLeerElAviso>().sinLeer, "lo que no pasó no se anota")
    }

    @Test
    fun `sin monto, el 422 no trae nada para anotar`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_tarjeta", sinMonto, "2026-10-18 12:00"))
        assertEquals(SMS_STATE_PENDING, estado("notif_tarjeta").first)

        val cuerpo = parse(a, "notif_tarjeta").body<NoSePudoLeerElAviso>()

        assertNull(cuerpo.sinLeer)
    }

    @Test
    fun `la bandeja lo trae sin lectura, y la fila lo manda a Revisar`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))

        val propuesta = propuestas(a).propuestas.single { it.avisoId == "notif_movimos" }

        assertNull(propuesta.leido, "sin lectura la fila no tiene qué confirmar")
        assertEquals(NO_TRAE_UN_MOVIMIENTO, propuesta.noSePudoLeer)
        assertEquals(45_000.0, propuesta.sinLeer?.monto)
    }

    @Test
    fun `confirmarlo por el camino de siempre escribe un solo movimiento, aunque se toque dos veces`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))
        val sinLeer = assertNotNull(parse(a, "notif_movimos").body<NoSePudoLeerElAviso>().sinLeer)
        val evento = movimientoDe(sinLeer, id = "ev_anotado_igual", categoria = "Familia", comercio = "Carolina R.")

        // Lo que hace `anotarElMovimientoDelAviso`: el movimiento y después el aviso, con su id.
        repeat(2) {
            val creado = cliente().post("/api/events") {
                autorizado(a)
                contentType(ContentType.Application.Json)
                setBody(evento)
            }
            assertTrue(creado.status.isSuccessLike(), "POST /api/events: ${creado.status} ${creado.bodyAsText()}")
            assertEquals(HttpStatusCode.OK, confirmar(a, "notif_movimos", "ev_anotado_igual"))
        }
        // Un tercer toque que llega tarde con otro id no mueve el enlace.
        assertEquals(HttpStatusCode.OK, confirmar(a, "notif_movimos", "ev_otro"))

        val movimientos = eventosDe(a)
        assertEquals(listOf("ev_anotado_igual"), movimientos.map { it.first })
        assertEquals(45_000L, movimientos.single().second)
        assertEquals(SMS_STATE_CONFIRMED to "ev_anotado_igual", estado("notif_movimos"))
        // Ya confirmado, no se vuelve a ofrecer.
        assertNull(parse(a, "notif_movimos").body<NoSePudoLeerElAviso>().sinLeer)
    }

    @Test
    fun `ya anotado a mano, Ya lo anotaste lo encuentra con el monto que trae`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))
        transaction {
            Events.insert {
                it[id] = "ev_a_mano"
                it[userId] = a
                it[accountId] = "nu-$a"
                it[type] = "EXPENSE"
                it[amount] = 45_000L
                it[category] = "Familia"
                it[description] = "A Caro"
                it[timestamp] = momentoDelSms(movimosHora, System.currentTimeMillis()) + 3_600_000L
            }
        }

        val iguales = cliente().get("/api/sms/notif_movimos/coincidencias") { autorizado(a) }.body<List<FinancialEvent>>()

        assertEquals(listOf("ev_a_mano"), iguales.map { it.id })
    }

    @Test
    fun `la categoria que eligio la vez pasada vuelve en el aviso siguiente igual`() = testApplication {
        application { testModule() }
        val primero = "Nu: Movimos \$32.500,00 en DONDE JUANCHO 33 el 18/10/2026 a las 08:10."
        val segundo = "Nu: Movimos \$18.000,00 en DONDE JUANCHO 33 el 19/10/2026 a las 08:05."
        subir(a, aviso("notif_pan_1", primero, "2026-10-18 08:10"))
        val sinLeer = assertNotNull(parse(a, "notif_pan_1").body<NoSePudoLeerElAviso>().sinLeer)
        assertEquals("DONDE JUANCHO 33", sinLeer.comercio, "el lector genérico encontró el nombre")
        assertNull(sinLeer.categoria)

        cliente().post("/api/events") {
            autorizado(a)
            contentType(ContentType.Application.Json)
            setBody(movimientoDe(sinLeer, id = "ev_pan_1", categoria = "Comida"))
        }
        confirmar(a, "notif_pan_1", "ev_pan_1")

        subir(a, aviso("notif_pan_2", segundo, "2026-10-19 08:05"))
        val siguiente = assertNotNull(parse(a, "notif_pan_2").body<NoSePudoLeerElAviso>().sinLeer)
        assertEquals(18_000.0, siguiente.monto)
        assertEquals("Comida", siguiente.categoria)
        assertNotNull(siguiente.aprendidoDe)
    }

    @Test
    fun `cada usuario ve solo sus avisos`() = testApplication {
        application { testModule() }
        subir(a, aviso("notif_movimos", movimos, movimosHora))

        assertEquals(HttpStatusCode.NotFound, parse(b, "notif_movimos").status)
        assertTrue(propuestas(b).propuestas.none { it.avisoId == "notif_movimos" })
        assertEquals(HttpStatusCode.NotFound, confirmar(b, "notif_movimos", "ev_de_b"))
        assertEquals(SMS_STATE_PENDING, estado("notif_movimos").first)
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun HttpStatusCode.isSuccessLike() = value in 200..299

    /** El movimiento que arma Reconciliar al anotarlo igual (`movimientoConfirmadoDelSms` con [comoLectura]). */
    private fun movimientoDe(sinLeer: AvisoSinLeer, id: String, categoria: String, comercio: String? = null): FinancialEvent {
        val leido = sinLeer.comoLectura()
        val nombre = comercio ?: leido.merchant
        return FinancialEvent(
            id = id,
            accountId = "nu-$a",
            type = leido.type,
            amount = leido.amount.toLong(),
            currency = leido.currency,
            category = categoria,
            description = nombre,
            merchant = nombre,
            source = EventSource.SMS,
            rawPayload = "texto del aviso",
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            timestamp = sinLeer.momento,
        )
    }

    private fun aviso(id: String, texto: String, time: String) =
        SmsMessage(id = id, time = time, bank = deNu, text = texto, state = "", det = "")

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@anotarlo.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun io.ktor.client.request.HttpRequestBuilder.autorizado(uid: String) =
        header(HttpHeaders.Authorization, "Bearer ${token(uid)}")

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
        val respuesta = cliente().post("/api/sms/sync") {
            autorizado(uid)
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }
        assertEquals(HttpStatusCode.OK, respuesta.status)
    }

    private suspend fun ApplicationTestBuilder.parse(uid: String, id: String): HttpResponse =
        cliente().get("/api/sms/$id/parse?anotarloIgual=1") { autorizado(uid) }

    private suspend fun ApplicationTestBuilder.propuestas(uid: String): PropuestasDeLaBandeja {
        val respuesta = cliente().get("/api/sms/propuestas") { autorizado(uid) }
        assertEquals(HttpStatusCode.OK, respuesta.status)
        return respuesta.body()
    }

    private suspend fun ApplicationTestBuilder.confirmar(uid: String, smsId: String, eventoId: String): HttpStatusCode =
        cliente().post("/api/sms/$smsId/confirm?eventoId=$eventoId") { autorizado(uid) }.status

    private fun estado(smsId: String): Pair<String, String?> = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq smsId }.single()
            .let { it[SmsMessages.state] to it[SmsMessages.eventoId] }
    }

    private fun motivo(smsId: String): String? = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq smsId }.single()[SmsMessages.motivoApartado]
    }

    private fun eventosDe(uid: String): List<Pair<String, Long>> = transaction {
        Events.selectAll().where { Events.userId eq uid }.map { it[Events.id] to it[Events.amount] }
    }
}
