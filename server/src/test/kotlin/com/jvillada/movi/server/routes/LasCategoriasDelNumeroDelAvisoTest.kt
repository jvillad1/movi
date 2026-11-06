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
import com.jvillada.movi.shared.model.CategoriaDelNumero
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.TipoDeTercero
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.select
import com.jvillada.movi.shared.model.ParsedSms
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
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** # Las categorías del número en la propuesta del aviso: la llave con dos historias trae las dos. */
class LasCategoriasDelNumeroDelAvisoTest {

    private val testSecret = "test-secret-for-categorias-del-numero-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val a = "user-a-numero"

    private val llave = "0099887766"
    private val merchantQr = "Pago QR · llave $llave"
    private val textoQr = "Bancolombia: pagaste \$9,000.00 por codigo QR desde tu cuenta *8133 a la llave $llave el 29/09/2026 a las 12:45. Con codigo QR es facil y de una."
    private val textoQrViejo = textoQr.replace("29/09/2026", "01/09/2026")
    private val momento = momentoDelSms("2026-09-01 12:45", System.currentTimeMillis())
    private var contador = 0

    @BeforeTest
    fun setUp() {
        contador = 0
        Database.connect(
            url = "jdbc:h2:mem:categorias_del_numero_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, PushSubscriptions, KnownDestinations, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, SmsMessages, PushSubscriptions, KnownDestinations)
            Users.insert {
                it[id] = a
                it[email] = "$a@numero.test"
                it[name] = a
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = "cuenta-$a"
                it[userId] = a
                it[name] = "Ahorros 8133"
                it[type] = "SAVINGS"
                it[balance] = 0L
                it[currency] = "COP"
            }
        }
    }

    private fun token(): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", a).withClaim("email", "$a@numero.test")
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

    private suspend fun ApplicationTestBuilder.subir(vararg mensajes: SmsMessage) {
        cliente().post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }
    }

    private suspend fun ApplicationTestBuilder.leer(smsId: String): ParsedSms =
        cliente().get("/api/sms/$smsId/parse") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private fun aviso(id: String, texto: String = textoQr, time: String = "2026-09-29 12:45") =
        SmsMessage(id = id, time = time, bank = "85540", text = texto, state = "", det = "")

    private fun movimiento(
        categoria: String,
        merchant: String?,
        descripcion: String = merchant ?: "Pago",
        crudo: String? = null,
        minutosAtras: Int = 0,
    ): String {
        val ev = "ev_${contador++}"
        transaction {
            Events.insert {
                it[id] = ev
                it[userId] = a
                it[accountId] = "cuenta-$a"
                it[type] = "EXPENSE"
                it[amount] = 9_000
                it[category] = categoria
                it[description] = descripcion
                it[Events.merchant] = merchant
                it[timestamp] = momento - minutosAtras * 60_000L
                it[rawPayload] = crudo
            }
        }
        return ev
    }

    private fun anular(eventoId: String) = transaction {
        VoidEvents.insert {
            it[id] = "void-$eventoId"
            it[userId] = a
            it[originalEventId] = eventoId
            it[timestamp] = System.currentTimeMillis()
        }
    }

    private fun sembrarDosHistorias(): List<String> {
        val ids = mutableListOf<String>()
        repeat(3) { ids += movimiento("Parqueadero", merchantQr, minutosAtras = 10 + it) }
        repeat(2) { ids += movimiento("Gasolina", merchantQr, minutosAtras = 100 + it) }
        return ids
    }

    @Test
    fun `el aviso a una llave con dos historias trae las dos, en orden`() = testApplication {
        application { testModule() }
        sembrarDosHistorias()
        subir(aviso("sms_1"))

        val p = leer("sms_1")

        assertEquals(listOf(CategoriaDelNumero("Parqueadero", 3), CategoriaDelNumero("Gasolina", 2)), p.categoriasDelNumero)
        assertEquals("Parqueadero", p.category)
        assertEquals("Así lo anotaste 3 veces", p.aprendidoDe)
    }

    @Test
    fun `con una sola historia el campo va vacio`() = testApplication {
        application { testModule() }
        repeat(3) { movimiento("Parqueadero", merchantQr, minutosAtras = 10 + it) }
        subir(aviso("sms_1"))

        assertTrue(leer("sms_1").categoriasDelNumero.isEmpty())
    }

    @Test
    fun `lo anotado con nombre cuenta por el texto del banco`() = testApplication {
        application { testModule() }
        repeat(3) { movimiento("Parqueadero", "Parqueadero Calle 85", crudo = textoQrViejo, minutosAtras = 10 + it) }
        repeat(2) { movimiento("Gasolina", merchantQr, minutosAtras = 100 + it) }
        subir(aviso("sms_1"))

        val p = leer("sms_1")

        assertEquals(
            listOf(CategoriaDelNumero("Parqueadero", 3), CategoriaDelNumero("Gasolina", 2)),
            p.categoriasDelNumero,
        )
    }

    @Test
    fun `los anulados no cuentan`() = testApplication {
        application { testModule() }
        repeat(3) { movimiento("Parqueadero", merchantQr, minutosAtras = 10 + it) }
        val gasolina = List(2) { movimiento("Gasolina", merchantQr, minutosAtras = 100 + it) }
        gasolina.forEach(::anular)
        subir(aviso("sms_1"))

        assertTrue(leer("sms_1").categoriasDelNumero.isEmpty())
    }

    @Test
    fun `el pago de tarjeta no lo lleva`() = testApplication {
        application { testModule() }
        sembrarDosHistorias()
        subir(aviso("sms_1", texto = "Bancolombia: Pago autom TC *1234 por \$9.000 el 29/09/2026 a la llave $llave."))

        assertTrue(leer("sms_1").categoriasDelNumero.isEmpty())
    }

    @Test
    fun `la nota no vuelve como nombre`() = testApplication {
        application { testModule() }
        movimiento("Parqueadero", merchantQr, descripcion = "$merchantQr · lo de ayer", minutosAtras = 5)
        movimiento("Parqueadero", null, descripcion = "Parqueadero Calle 85", crudo = textoQrViejo, minutosAtras = 50000)
        subir(aviso("sms_1"))

        val p = leer("sms_1")

        assertFalse(p.merchant.contains("lo de ayer"), "el nombre propuesto: ${p.merchant}")
        assertEquals(merchantQr, p.merchant)
    }

    @Test
    fun `sin renombrar ni nota, el aviso trae el comercio entero con su llave`() = testApplication {
        application { testModule() }
        repeat(2) { movimiento("Parqueadero", merchantQr, descripcion = merchantQr, minutosAtras = 5 + it) }
        subir(aviso("sms_1"))

        assertEquals(merchantQr, leer("sms_1").merchant)
    }

    @Test
    fun `renombrado por un tercero, el siguiente aviso trae el nombre sin la nota`() = testApplication {
        application { testModule() }
        movimiento("Parqueadero", merchantQr, descripcion = "$merchantQr · parqueadero del centro", minutosAtras = 5)
        val c = cliente()
        val r = c.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(DestinoConocido(nombre = "Parqueadero Calle 85", numero = "", llave = llave, tipo = TipoDeTercero.COMERCIO))
        }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        val descripcion = transaction { Events.select(Events.description).single()[Events.description] }
        assertEquals("Parqueadero Calle 85 · parqueadero del centro", descripcion)
        subir(aviso("sms_1"))

        assertEquals("Parqueadero Calle 85", leer("sms_1").merchant)
    }
}
