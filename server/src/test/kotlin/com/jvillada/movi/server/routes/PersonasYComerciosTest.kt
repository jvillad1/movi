package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.alertaPostmark
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.Migrations
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.DestinosDescartados as Descartados
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.OrigenDelNombre
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RenombradosDelDestino
import com.jvillada.movi.shared.model.RenombrarMovimientos
import com.jvillada.movi.shared.model.RespuestaDelSync
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.idDeComprobante
import com.jvillada.movi.shared.model.numeros
import com.jvillada.movi.shared.model.todosLosIdentificadores
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Base64
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Personas y comercios», perfecto** (4-oct-2026): el tipo que Movi deduce al leer sin escribirlo
 * en la base, y unir dos fichas de la misma persona — con el aislamiento entre usuarios de siempre:
 * quien nombra la ficha de otro recibe «no existe» y nada cambia.
 *
 * Todo con textos sintéticos con la forma de los avisos de Bancolombia: ningún número ni nombre es
 * del dueño.
 */
class PersonasYComerciosTest {

    private val testSecret = "test-secret-for-terceros-a-la-mano-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val userA = "user-a-pyc"
    private val userB = "user-b-pyc"
    private val ahorrosDeA = "acc-ahorros-a"
    private val secretoDelCorreo = "un-secreto-de-correo-para-terceros"

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.secreto", secretoDelCorreo)
        System.setProperty("movi.correo.direccion", "base@inbound.test")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:personas_y_comercios_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages,
                Credits, Goals, KnownDestinations, DestinosDescartados, PushSubscriptions, Documents, LecturasDePapeles,
            )
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            listOf(userA, userB).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@terceros.test"; it[name] = uid; it[passwordHash] = "hash"
                }
            }
            Accounts.insert {
                it[id] = ahorrosDeA; it[userId] = userA; it[name] = "Ahorros 9999"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.secreto")
        System.clearProperty("movi.correo.direccion")
        RateLimiter.reset()
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@terceros.test")
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

    private fun ApplicationTestBuilder.tipado(): HttpClient {
        application { testModule() }
        return createClient { install(ContentNegotiation) { json(json) } }
    }

    private suspend fun HttpClient.crear(destino: DestinoConocido, uid: String = userA): DestinoConocido {
        val r = post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(destino)
        }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        return r.body()
    }

    private suspend fun HttpClient.agregar(id: String, identificador: IdentificadorDelDestino, mover: Boolean = false, uid: String = userA): HttpResponse =
        post("/api/destinos/$id/identificadores") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(AgregarIdentificador(identificador, mover))
        }

    private suspend fun HttpClient.destinos(uid: String = userA): List<DestinoConocido> =
        get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun HttpClient.sugeridos(uid: String = userA): List<DestinoSugerido> =
        get("/api/destinos/sugeridos") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun HttpClient.parse(smsId: String, uid: String = userA): ParsedSms =
        get("/api/sms/$smsId/parse") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private fun numero(v: String) = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, v)
    private fun llave(v: String) = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, v)

    private fun caro() = DestinoConocido(nombre = "Caro", numero = "55500001111", deQuien = "esposa")

    /** Hace [dias] días, en el formato de la columna `time`: lo reciente no depende del reloj de CI. */
    private fun haceDias(dias: Long): String =
        java.time.LocalDateTime.now().minusDays(dias).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun insertarAviso(id: String, texto: String, banco: String = "85540", uid: String = userA, hora: String = haceDias(1)) =
        transaction {
            SmsMessages.insert {
                it[SmsMessages.id] = id; it[userId] = uid; it[time] = hora; it[bank] = banco
                it[text] = texto; it[state] = "pending"; it[det] = ""
            }
        }

    private fun insertarMovimiento(id: String, descripcion: String, monto: Long, raw: String?, uid: String = userA, cuando: Long = 1_790_000_000_000L) =
        transaction {
            Events.insert {
                it[Events.id] = id; it[userId] = uid; it[accountId] = ahorrosDeA; it[type] = "EXPENSE"
                it[amount] = monto; it[category] = "Otros"; it[description] = descripcion; it[merchant] = descripcion
                it[timestamp] = cuando; it[eventSource] = "SMS"; it[rawPayload] = raw
            }
        }


    private suspend fun HttpClient.unir(id: String, con: String, uid: String = userA): HttpResponse =
        post("/api/destinos/$id/unir/$con") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }

    private fun tipoGuardado(id: String): String? = transaction {
        KnownDestinations.selectAll().where { KnownDestinations.id eq id }.single()[KnownDestinations.tipo]
    }

    private fun qr(llave: String, monto: String) =
        "Bancolombia: JUAN PRUEBA pagaste \$$monto por codigo QR desde tu cuenta *9999 a la llave $llave el 20/09/2026 a las 21:18."

    private fun transferencia(cuenta: String, monto: String) =
        "Bancolombia: Transferiste \$$monto desde tu cuenta *9999 a la cuenta *$cuenta el 03/10/2026 a las 17:55."

    // ── Persona o comercio, deducido al leer ────────────────────────────────────

    @Test
    fun `una llave pagada por QR se lee como comercio, sin escribir el tipo en la base`() = testApplication {
        val c = tipado()
        val cancha = c.crear(DestinoConocido(nombre = "Cancha", numero = "", llave = "3001112222"))
        insertarAviso("q1", qr("3001112222", "13,500.00"))
        insertarAviso("q2", qr("3001112222", "3,000.00"))
        val leida = c.destinos().single { it.id == cancha.id }
        assertEquals(TipoDeTercero.COMERCIO, leida.tipo)
        assertTrue(leida.tipoInferido, "lo dedujo Movi: nadie lo eligió")
        assertNull(tipoGuardado(cancha.id), "deducirlo al leer no escribe nada")
    }

    @Test
    fun `una cuenta a la que se transfiere se lee como persona`() = testApplication {
        val c = tipado()
        val obra = c.crear(DestinoConocido(nombre = "Contratista", numero = "00800000404"))
        insertarAviso("t1", transferencia("00800000404", "2,000,000"))
        val leida = c.destinos().single { it.id == obra.id }
        assertEquals(TipoDeTercero.PERSONA, leida.tipo)
        assertTrue(leida.tipoInferido)
    }

    @Test
    fun `una llave con forma de QR sin avisos se lee como comercio`() = testApplication {
        val c = tipado()
        val parqueadero = c.crear(DestinoConocido(nombre = "Parqueadero", numero = "", llave = "0088001122"))
        assertEquals(TipoDeTercero.COMERCIO, c.destinos().single { it.id == parqueadero.id }.tipo)
    }

    @Test
    fun `el tipo que elige el duenno gana sobre lo que dicen los avisos`() = testApplication {
        val c = tipado()
        val amigo = c.crear(DestinoConocido(nombre = "Amigo", numero = "", llave = "3001112222", tipo = TipoDeTercero.PERSONA))
        insertarAviso("q1", qr("3001112222", "13,500.00"))
        val leido = c.destinos().single { it.id == amigo.id }
        assertEquals(TipoDeTercero.PERSONA, leido.tipo)
        assertTrue(!leido.tipoInferido)
        assertEquals("PERSONA", tipoGuardado(amigo.id))
    }

    @Test
    fun `renombrar sin tocar el tipo deducido no lo vuelve elegido`() = testApplication {
        val c = tipado()
        val cancha = c.crear(DestinoConocido(nombre = "Cancha", numero = "", llave = "3001112222"))
        insertarAviso("q1", qr("3001112222", "13,500.00"))
        val leida = c.destinos().single()
        // Lo que manda la app: el tipo en null porque lo dedujo Movi y el dueño no lo tocó.
        val r = c.put("/api/destinos/${cancha.id}") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            contentType(ContentType.Application.Json)
            setBody(leida.copy(nombre = "Cancha El Gol", tipo = null))
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertNull(tipoGuardado(cancha.id))
        assertEquals(TipoDeTercero.COMERCIO, r.body<DestinoConocido>().tipo)
    }

    // ── Unir dos fichas ─────────────────────────────────────────────────────────

    @Test
    fun `unir pasa los datos a la que queda, mueve sus recurrentes y borra la otra sin tocar movimientos`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        val repetida = c.crear(DestinoConocido(nombre = "Carolina Prueba", numero = "", llave = "carolina prueba salazar", deQuien = null))
        transaction {
            RecurringRules.insert {
                it[id] = "r1"; it[userId] = userA; it[name] = "Mesada"; it[category] = "Casa"
                it[amount] = 500_000L; it[dayOfMonth] = 5; it[type] = "EXPENSE"; it[destinoConocidoId] = repetida.id
            }
        }
        insertarMovimiento("e1", "Transferencia a Caro", 2_000_000L, transferencia("55500001111", "2,000,000"))

        val r = c.unir(repetida.id, caro.id)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val unida = r.body<DestinoConocido>()
        assertEquals("Caro", unida.nombre, "queda el nombre de la ficha elegida")
        assertEquals("esposa", unida.deQuien)
        assertEquals(
            setOf("NUMERO:55500001111", "LLAVE:carolina prueba salazar"),
            unida.todosLosIdentificadores().map { "${it.tipo}:${it.valor}" }.toSet(),
        )
        assertEquals(listOf(caro.id), c.destinos().map { it.id }, "la repetida se borró")
        transaction {
            assertEquals(caro.id, RecurringRules.selectAll().single()[RecurringRules.destinoConocidoId])
            assertEquals(1, Events.selectAll().count().toInt(), "ningún movimiento se toca")
        }
        assertEquals(1, unida.cuantos)
    }

    @Test
    fun `unir con una ficha de otro usuario no existe y nada cambia`() = testApplication {
        val c = tipado()
        val deA = c.crear(caro())
        val deB = c.crear(DestinoConocido(nombre = "Ajena", numero = "77700003333"), uid = userB)

        assertEquals(HttpStatusCode.NotFound, c.unir(deA.id, deB.id).status, "A no puede llevar lo suyo a una ficha de B")
        assertEquals(HttpStatusCode.NotFound, c.unir(deB.id, deA.id).status, "A no puede traer la ficha de B")
        assertEquals(HttpStatusCode.NotFound, c.unir(deA.id, deB.id, uid = userB).status, "B no puede tocar la de A")

        assertEquals(listOf(deA.id), c.destinos().map { it.id })
        assertEquals(listOf(deB.id), c.destinos(userB).map { it.id })
        assertEquals(listOf("55500001111"), c.destinos().single().numeros())
        assertEquals(listOf("77700003333"), c.destinos(userB).single().numeros())
    }

    @Test
    fun `unir una ficha consigo misma se rechaza`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        assertEquals(HttpStatusCode.BadRequest, c.unir(caro.id, caro.id).status)
        assertEquals(1, c.destinos().size)
    }
}
