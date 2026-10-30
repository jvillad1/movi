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
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AvisosDelMismoPago
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
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
 * # Vuelta 74 · «Es este» en un toque, y su «Deshacer»
 *
 * «Es este» vincula el aviso a un movimiento que ya estaba: **nunca crea uno**, y los movimientos y
 * las cifras quedan exactamente igual. Su «Deshacer» (`POST /api/sms/desvincular`) devuelve el aviso a
 * «Por revisar» sin tocar el movimiento. Y el «Deshacer» de un aviso que sí creó su movimiento es la
 * anulación de siempre (vuelta 67) más el mismo desvincular. Todo aislado por usuario.
 */
class EsEsteYSuDeshacerTest {

    private val testSecret = "test-secret-for-es-este-y-su-deshacer-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-es-este"
    private val b = "user-b-es-este"

    private val hora = "2026-10-29 13:05"
    private val textoCrepes =
        "Bancolombia: Compraste \$32.000,00 en CREPES Y WAFFLES OVIEDO con tu T.Deb *4057, el 29/10/2026 a las 13:05."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:es_este_y_su_deshacer_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, PushSubscriptions, KnownDestinations, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, SmsMessages, PushSubscriptions, KnownDestinations)
            for (id in listOf(a, b)) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@es-este.test"
                    it[name] = id
                    it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[Accounts.id] = "cuenta-$id"
                    it[userId] = id
                    it[name] = "Bancolombia Ahorros 4057"
                    it[type] = "SAVINGS"
                    it[balance] = 0L
                    it[currency] = "COP"
                }
            }
        }
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@es-este.test")
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

    private suspend fun ApplicationTestBuilder.esEste(uid: String, smsId: String, eventoId: String): HttpStatusCode =
        cliente().post("/api/sms/$smsId/confirm?eventoId=$eventoId") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        }.status

    private suspend fun ApplicationTestBuilder.desvincular(uid: String, vararg avisos: String): HttpStatusCode =
        cliente().post("/api/sms/desvincular") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(AvisosDelMismoPago(avisos.toList()))
        }.status

    private suspend fun ApplicationTestBuilder.anularPorLaApi(uid: String, eventoId: String): HttpStatusCode =
        cliente().post("/api/events/$eventoId/void") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        }.status

    private fun aviso(id: String) = SmsMessage(id = id, time = hora, bank = "85540", text = textoCrepes, state = "", det = "")

    private val momento = momentoDelSms(hora, ahora = System.currentTimeMillis())

    /** «Restaurantes · $32.000 · hoy»: lo que el dueño anotó a mano antes de que llegara el aviso. */
    private fun anotadoAMano(id: String, uid: String = a) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = "cuenta-$uid"
            it[type] = "EXPENSE"
            it[amount] = 32_000
            it[category] = "Restaurantes"
            it[description] = "Restaurantes"
            it[timestamp] = momento - 10 * 60_000L
            it[createdAt] = momento - 10 * 60_000L
        }
    }

    /** Lo que cuenta en las cifras del usuario: cuántos movimientos vivos y cuánto suman. */
    private fun cifras(uid: String = a): Pair<Int, Long> = transaction {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
        val vivos = Events.selectAll().where { Events.userId eq uid }.filter { it[Events.id] !in anulados }
        vivos.size to vivos.sumOf { it[Events.amount] }
    }

    private fun fila(smsId: String, uid: String = a) = transaction {
        SmsMessages.selectAll().where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }.single()
            .let { Triple(it[SmsMessages.state], it[SmsMessages.eventoId], it[SmsMessages.confirmadoEn]) }
    }

    @Test
    fun `Es este no crea un movimiento y las cifras siguen iguales`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_crepes")
        subir(a, aviso("sms_crepes"))
        val antes = cifras()

        assertEquals(HttpStatusCode.OK, esEste(a, "sms_crepes", "ev_crepes"))

        assertEquals(antes, cifras(), "ni un movimiento más ni un peso más")
        assertEquals(1, cifras().first)
        val (estado, evento, cuando) = fila("sms_crepes")
        assertEquals(SMS_STATE_CONFIRMED, estado)
        assertEquals("ev_crepes", evento, "el aviso queda apuntando al que ya estaba")
        assertNotNull(cuando)
    }

    @Test
    fun `Deshacer Es este devuelve el aviso a Por revisar sin tocar el movimiento`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_crepes")
        subir(a, aviso("sms_crepes"))
        esEste(a, "sms_crepes", "ev_crepes")
        val conElAviso = cifras()

        assertEquals(HttpStatusCode.NoContent, desvincular(a, "sms_crepes"))

        assertEquals(Triple(SMS_STATE_PENDING, null, null), fila("sms_crepes"))
        assertEquals(conElAviso, cifras(), "desvincular no mueve ninguna cifra")
        // Y se puede volver a decir «Es este».
        assertEquals(HttpStatusCode.OK, esEste(a, "sms_crepes", "ev_crepes"))
        assertEquals("ev_crepes", fila("sms_crepes").second)
    }

    @Test
    fun `un segundo Deshacer no cambia nada`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_crepes")
        subir(a, aviso("sms_crepes"))
        esEste(a, "sms_crepes", "ev_crepes")
        desvincular(a, "sms_crepes")

        assertEquals(HttpStatusCode.NotFound, desvincular(a, "sms_crepes"))
        assertEquals(SMS_STATE_PENDING, fila("sms_crepes").first)
    }

    @Test
    fun `otro usuario no puede desvincular el aviso`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_crepes")
        subir(a, aviso("sms_crepes"))
        esEste(a, "sms_crepes", "ev_crepes")
        val antes = fila("sms_crepes")

        assertEquals(HttpStatusCode.NotFound, desvincular(b, "sms_crepes"))

        assertEquals(antes, fila("sms_crepes"))
    }

    @Test
    fun `desvincular solo toca los avisos del que pide aunque tengan el mismo id`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_a", uid = a)
        anotadoAMano("ev_b", uid = b)
        subir(a, aviso("sms_crepes"))
        subir(b, aviso("sms_crepes"))
        esEste(a, "sms_crepes", "ev_a")
        esEste(b, "sms_crepes", "ev_b")

        desvincular(a, "sms_crepes")

        assertEquals(SMS_STATE_PENDING, fila("sms_crepes", a).first)
        assertEquals(Triple(SMS_STATE_CONFIRMED, "ev_b", fila("sms_crepes", b).third), fila("sms_crepes", b))
    }

    @Test
    fun `un aviso pendiente no se desvincula`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_crepes"))

        assertEquals(HttpStatusCode.NotFound, desvincular(a, "sms_crepes"))
        assertEquals(SMS_STATE_PENDING, fila("sms_crepes").first)
    }

    // ── Revisión de la vuelta 74: dos compras iguales ───────────────────────────────────────

    private fun crepesDe(id: String, hora: String) = SmsMessage(
        id = id, time = "2026-10-29 $hora", bank = "85540", state = "", det = "",
        text = "Bancolombia: Compraste \$32.000,00 en CREPES Y WAFFLES OVIEDO con tu T.Deb *4057, el 29/10/2026 a las $hora.",
    )

    private suspend fun ApplicationTestBuilder.coincidencias(uid: String, smsId: String): List<FinancialEvent> =
        cliente().get("/api/sms/$smsId/coincidencias") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    @Test
    fun `dos compras iguales - el movimiento de la primera no se ofrece ni se acepta para la segunda`() = testApplication {
        application { testModule() }
        anotadoAMano("ev_crepes")
        subir(a, crepesDe("sms_1245", "12:45"), crepesDe("sms_1258", "12:58"))
        assertEquals(listOf("ev_crepes"), coincidencias(a, "sms_1258").map { it.id }, "antes, las dos lo ven")

        assertEquals(HttpStatusCode.OK, esEste(a, "sms_1245", "ev_crepes"))

        assertEquals(emptyList(), coincidencias(a, "sms_1258").map { it.id }, "ya es de la primera: no se ofrece")
        assertEquals(HttpStatusCode.Conflict, esEste(a, "sms_1258", "ev_crepes"), "ni se acepta")
        assertEquals(Triple(SMS_STATE_PENDING, null, null), fila("sms_1258"), "la segunda compra sigue esperando")
        assertEquals("ev_crepes", fila("sms_1245").second)
        // Deshecha la primera, el movimiento vuelve a estar libre.
        desvincular(a, "sms_1245")
        assertEquals(listOf("ev_crepes"), coincidencias(a, "sms_1258").map { it.id })
    }

    @Test
    fun `un pago de tarjeta solo coincide con su categoria o la pata que entra a la tarjeta, una por traspaso`() {
        val tipos = mapOf("ahorros" to AccountType.SAVINGS, "efectivo" to AccountType.CASH, "nu" to AccountType.CREDIT_CARD)
        fun ev(id: String, cuenta: String, tipo: TransactionType, categoria: String, par: String? = null) = FinancialEvent(
            id = id, accountId = cuenta, type = tipo, amount = 50_000L, category = categoria, description = categoria,
            timestamp = momento, transferId = par,
        )
        val eventos = listOf(
            ev("tr_sale", "ahorros", TransactionType.EXPENSE, "Traspaso", par = "tr1"),
            ev("tr_entra", "efectivo", TransactionType.INCOME, "Traspaso", par = "tr1"),
            ev("mercado", "ahorros", TransactionType.EXPENSE, "Mercado"),
            ev("pago_sale", "ahorros", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, par = "tr2"),
            ev("pago_entra", "nu", TransactionType.INCOME, CARD_PAYMENT_CATEGORY, par = "tr2"),
        )
        val pago = ParsedSms(50_000.0, "Nu", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)
        assertEquals(listOf("pago_sale"), coincidenciasDelSms(pago, momento, eventos, tipos).map { it.id })
        // Sin la pata que sale, la que entra a la tarjeta también vale.
        assertEquals(listOf("pago_entra"), coincidenciasDelSms(pago, momento, eventos.filter { it.id != "pago_sale" }, tipos).map { it.id })
        // Un gasto nunca coincide con la pata que entra de un traspaso, y un traspaso es un candidato.
        val gasto = ParsedSms(50_000.0, "Exito", TransactionType.EXPENSE, "Mercado")
        val ids = coincidenciasDelSms(gasto, momento, eventos, tipos).map { it.id }
        assertTrue("tr_entra" !in ids && "pago_entra" !in ids, "ninguna pata que entra: $ids")
        assertEquals(1, ids.count { it.startsWith("tr_") }, "un candidato por traspaso")
    }

    @Test
    fun `Deshacer un aviso que creo su movimiento - se anula y el aviso vuelve`() = testApplication {
        application { testModule() }
        subir(a, aviso("sms_crepes"))
        val antes = cifras()
        // Confirmar crea el movimiento (como la app: `POST /api/events` y `…/confirm?eventoId=`).
        anotadoAMano("ev_del_aviso")
        esEste(a, "sms_crepes", "ev_del_aviso")
        assertEquals(antes.first + 1, cifras().first)

        // Deshacer: el desvincular y la anulación de siempre.
        assertEquals(HttpStatusCode.NoContent, desvincular(a, "sms_crepes"))
        assertEquals(HttpStatusCode.Created, anularPorLaApi(a, "ev_del_aviso"))

        assertEquals(antes, cifras(), "las cifras vuelven a lo de antes de confirmar")
        val (estado, evento, cuando) = fila("sms_crepes")
        assertEquals(SMS_STATE_PENDING, estado)
        assertNull(evento)
        assertNull(cuando)
    }
}
