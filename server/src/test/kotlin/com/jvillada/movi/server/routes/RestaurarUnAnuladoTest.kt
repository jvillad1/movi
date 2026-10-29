package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue

/**
 * # Vuelta 67 · un anulado se restaura
 *
 * Anular deja una fila en `void_events` y toda suma la excluye. Restaurar (`DELETE
 * /api/events/{id}/void`) borra esa fila y nada más: el movimiento vuelve a contar como antes. Un
 * traspaso son dos patas: anular una anula las dos, y restaurar una restaura las dos. Solo del dueño.
 */
class RestaurarUnAnuladoTest {

    private val testSecret = "test-secret-para-restaurar-un-anulado-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-restaurar"
    private val otro = "user-otro-restaurar"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:restaurar_un_anulado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(Users, Accounts, Events, VoidEvents, SmsMessages)
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "restaurar@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Users.insert { it[id] = otro; it[email] = "otro-restaurar@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            Accounts.insert { it[Accounts.id] = "ahorros"; it[userId] = uid; it[name] = "Ahorros"; it[type] = "SAVINGS" }
            Accounts.insert { it[Accounts.id] = "cdt"; it[userId] = uid; it[name] = "CDT"; it[type] = "INVESTMENT" }
            evento("ev-saldo", "ahorros", "INCOME", 1_000_000L, "Saldo inicial")
            evento("ev-almuerzo", "ahorros", "EXPENSE", 40_000L, "Restaurantes")
            evento("tr-sale", "ahorros", "EXPENSE", 300_000L, "Traspaso", transferId = "tr-1")
            evento("tr-entra", "cdt", "INCOME", 300_000L, "Traspaso", transferId = "tr-1")
        }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, transferId: String? = null) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = 1_800_000_000_000L
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
            it[Events.transferId] = transferId
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

    private suspend fun ApplicationTestBuilder.anular(id: String, quien: String = uid): HttpResponse =
        cliente().post("/api/events/$id/void?reason=Duplicado") { header(HttpHeaders.Authorization, "Bearer ${token(quien)}") }

    private suspend fun ApplicationTestBuilder.restaurar(id: String, quien: String = uid): HttpResponse =
        cliente().delete("/api/events/$id/void") { header(HttpHeaders.Authorization, "Bearer ${token(quien)}") }

    private suspend fun ApplicationTestBuilder.anulados(quien: String = uid): List<MovimientoAnulado> =
        cliente().get("/api/events/anulados") { header(HttpHeaders.Authorization, "Bearer ${token(quien)}") }.body()

    private fun marcas(): Set<String> = transaction {
        VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    }

    /** El saldo de una cuenta sin anulados: la regla de siempre. */
    private fun saldo(cuenta: String): Long = transaction {
        val fuera = marcas()
        Events.selectAll().where { (Events.userId eq uid) and (Events.accountId eq cuenta) }
            .filter { it[Events.id] !in fuera }
            .sumOf { if (it[Events.type] == "INCOME") it[Events.amount] else -it[Events.amount] }
    }

    @Test
    fun `restaurar devuelve el movimiento a las cifras, como antes de anularlo`() = testApplication {
        application { testModule() }
        val antes = saldo("ahorros")
        assertEquals(HttpStatusCode.Created, anular("ev-almuerzo").status)
        assertEquals(antes + 40_000L, saldo("ahorros"))
        val lista = anulados()
        assertEquals(listOf("ev-almuerzo"), lista.map { it.evento.id })
        assertEquals("Duplicado", lista.single().motivo)

        assertEquals(HttpStatusCode.OK, restaurar("ev-almuerzo").status)
        assertEquals(antes, saldo("ahorros"))
        assertTrue(anulados().isEmpty())
        // Lo que no está anulado no se restaura.
        assertEquals(HttpStatusCode.NotFound, restaurar("ev-almuerzo").status)
    }

    @Test
    fun `restaurar una pata de un traspaso restaura las dos`() = testApplication {
        application { testModule() }
        val ahorros = saldo("ahorros")
        val cdt = saldo("cdt")
        assertEquals(HttpStatusCode.Created, anular("tr-entra").status)
        assertEquals(setOf("tr-sale", "tr-entra"), marcas(), "anular una pata anula las dos")
        assertEquals(HttpStatusCode.OK, restaurar("tr-sale").status)
        assertTrue(marcas().isEmpty(), "restaurar una restaura las dos")
        assertEquals(ahorros, saldo("ahorros"))
        assertEquals(cdt, saldo("cdt"))
    }

    @Test
    fun `aislamiento - nadie restaura ni ve lo anulado de otro`() = testApplication {
        application { testModule() }
        anular("ev-almuerzo")
        assertEquals(HttpStatusCode.NotFound, restaurar("ev-almuerzo", quien = otro).status)
        assertTrue(anulados(quien = otro).isEmpty())
        assertEquals(setOf("ev-almuerzo"), marcas(), "sigue anulado")
    }

    @Test
    fun `el aviso confirmado dice su movimiento y a quien fue`() = testApplication {
        application { testModule() }
        val texto = "Bancolombia: Pagaste \$980.000,00 a Banco de Occidente S A ATH desde tu producto 4057"
        val confirmado = SmsMessage(id = "sms-1", time = "2026-10-28 23:45", bank = "85540", text = texto, state = SMS_STATE_CONFIRMED, det = "", eventoId = "ev-almuerzo")
        val aviso = transaction { conLoQueDice(uid, listOf(confirmado)) }.single()
        assertEquals("ev-almuerzo", aviso.eventoId)
        assertEquals("Restaurantes", aviso.comercio, "el concepto del movimiento que anotó")
        assertEquals(980_000.0, aviso.monto)
        // Anulado el movimiento, la fila ya no lo abre y el comercio sale del texto.
        anular("ev-almuerzo")
        val despues = transaction { conLoQueDice(uid, listOf(confirmado)) }.single()
        assertEquals(null, despues.eventoId)
        assertTrue(despues.comercio.orEmpty().contains("Occidente", ignoreCase = true), despues.comercio)
        // El de otro usuario no se mira.
        assertEquals(null, transaction { conLoQueDice(otro, listOf(confirmado)) }.single().eventoId)
    }
}
