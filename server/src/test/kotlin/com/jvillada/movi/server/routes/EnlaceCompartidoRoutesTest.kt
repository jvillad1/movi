package com.jvillada.movi.server.routes

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.compartir.TokenDeEnlace
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.EnlacesCompartidos
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.plugins.configureStatusPages
import com.jvillada.movi.shared.model.EnlaceCompartido
import com.jvillada.movi.shared.model.EnlaceCompartidoCreado
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compartir con un tercero, extremo a extremo contra H2.
 *
 * Un enlace compartido es una capacidad: quien tiene el token ve la plata del dueño. Casi todo lo que
 * se afirma acá es lo que ese token **no** deja pasar — a otro usuario, a un enlace vencido o
 * revocado, a la base en claro, a un log, a un número de cuenta completo.
 */
class EnlaceCompartidoRoutesTest {

    private val testSecret = "test-secret-for-enlaces-compartidos-tests-min-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val json = Json { ignoreUnknownKeys = true }

    private val duena = "user-a-compartir"
    private val otro = "user-b-compartir"

    private val tablas = arrayOf(
        Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
        Subscriptions, Goals, SmsMessages, EnlacesCompartidos,
    )

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:enlaces_compartidos_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            usuario(duena, "Juan Villada")
            usuario(otro, "Otra Persona")

            // Plata: $5.000.000 que entraron y $1.200.000 de mercado en el período, más una pensión
            // voluntaria condicionada.
            cuenta("ahorros", "Ahorros 0012345678", "SAVINGS")
            cuenta("pension", "Pensión voluntaria", "INVESTMENT", condicion = "Vivienda")
            evento("e1", "ahorros", "INCOME", 5_000_000, "Salario")
            evento("e2", "ahorros", "EXPENSE", 1_200_000, "Mercado")
            evento("e3", "pension", "INCOME", 106_000_000, "Aporte")

            // Deudas: una con el número de crédito completo en el nombre, a propósito, y una tarjeta
            // con el número de la tarjeta escrito con espacios.
            cuenta("hipoteca", "Hipoteca 9876543210123", "LOAN")
            evento("d1", "hipoteca", "EXPENSE", 300_000_000, "Deuda inicial")
            Credits.insert {
                it[accountId] = "hipoteca"
                it[userId] = duena
                it[bank] = "Banco Popular"
                it[principal] = 320_000_000
                it[rateEa] = 12.4
                it[termMonths] = 240
                it[installment] = 3_450_000
                it[dayOfMonth] = 5
                it[startDate] = "2020-01-05"
            }
            cuenta("visa", "Visa 4513 2200 1234 5678", "CREDIT_CARD")
            evento("d2", "visa", "EXPENSE", 2_500_000, "Deuda inicial")
            Cards.insert {
                it[accountId] = "visa"
                it[userId] = duena
                it[bank] = "Bancolombia"
                it[paymentDay] = 10
                it[pagoMinimo] = 250_000
            }

            // Del otro usuario: no puede aparecer nunca en la página de la dueña.
            cuenta("ajena", "Cuenta secreta del otro", "SAVINGS", uid = otro)
            evento("x1", "ajena", "INCOME", 777_777_777, "Herencia", uid = otro)
        }
    }

    @AfterTest
    fun tearDown() {
        RateLimiter.reset()
    }

    // ── Arnés ───────────────────────────────────────────────────────────────

    private fun usuario(id: String, nombre: String) = Users.insert {
        it[Users.id] = id
        it[email] = "$id@compartir.test"
        it[name] = nombre
        it[passwordHash] = "hash"
    }

    private fun cuenta(id: String, nombre: String, tipo: String, condicion: String? = null, uid: String = duena) =
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = uid
            it[name] = nombre
            it[type] = tipo
            it[conditionedTo] = condicion
        }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, uid: String = duena) =
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = System.currentTimeMillis()
            it[reconciliationStatus] = "RECONCILED"
        }

    private fun jwtDe(uid: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@compartir.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.modulo() {
        configureSerialization()
        configureStatusPages()
        // Con el log de peticiones puesto, igual que en producción: una prueba de acá abajo lo lee.
        configureMonitoring()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { cred ->
                    if (cred.payload.getClaim("userId").asString() != null) JWTPrincipal(cred.payload) else null
                }
            }
        }
        configureRouting()
    }

    private fun conApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { modulo() }
        block()
    }

    private suspend fun HttpClient.crear(uid: String, cuerpo: String = """{"dias":7}"""): HttpResponse =
        post("/api/enlaces-compartidos") {
            header(HttpHeaders.Authorization, "Bearer ${jwtDe(uid)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun HttpClient.crearYTraerToken(uid: String = duena): Pair<EnlaceCompartidoCreado, String> {
        val r = crear(uid)
        assertEquals(HttpStatusCode.Created, r.status)
        val creado = json.decodeFromString(EnlaceCompartidoCreado.serializer(), r.bodyAsText())
        return creado to creado.ruta.substringAfter('#')
    }

    private suspend fun HttpClient.listar(uid: String): List<EnlaceCompartido> {
        val r = get("/api/enlaces-compartidos") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(uid)}") }
        assertEquals(HttpStatusCode.OK, r.status)
        return json.decodeFromString(r.bodyAsText())
    }

    private suspend fun HttpClient.ver(token: String): HttpResponse = post("/compartido") {
        contentType(ContentType.Text.Plain)
        setBody(token)
    }

    // ── Crear, listar, revocar: solo lo propio ──────────────────────────────

    @Test
    fun `crear devuelve la ruta con el token en el fragmento y nunca en la ruta`() = conApp {
        val (creado, token) = client.crearYTraerToken()
        assertTrue(creado.ruta.startsWith("/compartido#"), creado.ruta)
        assertTrue(TokenDeEnlace.tieneForma(token), "el token tiene la forma prometida: $token")
        assertEquals("resumen", creado.enlace.alcance)
        val sieteDias = 7L * 24 * 60 * 60 * 1000
        assertEquals(sieteDias, creado.enlace.venceEn - creado.enlace.creadoEn)
    }

    @Test
    fun `la base guarda el hash del token y nunca el token`() = conApp {
        val (creado, token) = client.crearYTraerToken()
        val fila = transaction {
            EnlacesCompartidos.selectAll().where { EnlacesCompartidos.id eq creado.enlace.id }.single()
        }
        assertEquals(TokenDeEnlace.hashDelToken(token), fila[EnlacesCompartidos.tokenHash])
        // Ninguna columna de ninguna fila lleva el token en claro.
        val todo = transaction {
            EnlacesCompartidos.selectAll().flatMap { f -> EnlacesCompartidos.columns.map { f[it]?.toString().orEmpty() } }
        }
        assertFalse(todo.any { token in it }, "el token en claro no puede estar en la base")
    }

    @Test
    fun `listar trae solo los propios y sin el token`() = conApp {
        val (_, tokenA1) = client.crearYTraerToken(duena)
        val (_, tokenA2) = client.crearYTraerToken(duena)
        client.crearYTraerToken(otro)

        assertEquals(2, client.listar(duena).size)
        assertEquals(1, client.listar(otro).size)

        val crudo = client.get("/api/enlaces-compartidos") {
            header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}")
        }.bodyAsText()
        assertFalse(tokenA1 in crudo || tokenA2 in crudo, "la lista no puede traer el token")
    }

    @Test
    fun `revocar solo lo propio, y un revocado sale de la lista`() = conApp {
        val (creado, _) = client.crearYTraerToken(duena)
        val id = creado.enlace.id

        val ajeno = client.delete("/api/enlaces-compartidos/$id") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(otro)}") }
        assertEquals(HttpStatusCode.NotFound, ajeno.status, "otro usuario no revoca lo ajeno")
        assertEquals(1, client.listar(duena).size)

        val propio = client.delete("/api/enlaces-compartidos/$id") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
        assertEquals(HttpStatusCode.NoContent, propio.status)
        assertEquals(0, client.listar(duena).size)

        val otraVez = client.delete("/api/enlaces-compartidos/$id") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
        assertEquals(HttpStatusCode.NotFound, otraVez.status)

        // Revocar no borra: la fila queda, con su sello.
        val revocadoEn = transaction {
            EnlacesCompartidos.selectAll().where { EnlacesCompartidos.id eq id }.single()[EnlacesCompartidos.revocadoEn]
        }
        assertNotNull(revocadoEn)
    }

    @Test
    fun `solo se aceptan las vigencias y los alcances que existen`() = conApp {
        assertEquals(HttpStatusCode.BadRequest, client.crear(duena, """{"dias":365}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.crear(duena, """{"dias":0}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.crear(duena, """{"dias":7,"alcance":"todo"}""").status)
        assertEquals(HttpStatusCode.Created, client.crear(duena, """{"dias":1}""").status)
        assertEquals(HttpStatusCode.Created, client.crear(duena, """{"dias":30,"alcance":"resumen"}""").status)
        // Sin cuerpo útil: los defaults (7 días, resumen).
        assertEquals(HttpStatusCode.Created, client.crear(duena, "{}").status)
    }

    @Test
    fun `las rutas del duenio piden sesion`() = conApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/enlaces-compartidos").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/enlaces-compartidos").status)
    }

    // ── La página pública ───────────────────────────────────────────────────

    @Test
    fun `con un token valido la pagina muestra el resumen de la duenia y cuenta la vista`() = conApp {
        val (creado, token) = client.crearYTraerToken()
        val r = client.ver(token)
        assertEquals(HttpStatusCode.OK, r.status)
        val html = r.bodyAsText()

        assertTrue("Juan Villada" in html, "dice quién comparte")
        assertTrue("Datos al " in html, "dice de cuándo son los datos")
        assertTrue("Este enlace vale hasta el " in html)
        assertTrue("Plata disponible" in html)
        // Tu plata = $5.000.000 − $1.200.000; la pensión condicionada va aparte.
        assertTrue("$3.800.000" in html, html)
        assertTrue("solo para Vivienda" in html)
        assertTrue("Patrimonio neto" in html)
        assertTrue("Mercado" in html, "en qué se fue")
        assertTrue("Banco Popular" in html && "12,4\u00A0%\u00A0EA" in html && "Cuota $3.450.000 al mes" in html, "la deuda con cuota y tasa")
        assertTrue("Pago mínimo $250.000" in html, "la tarjeta con su pago mínimo")
        assertTrue("Solo lectura · compartido desde Movi" in html)

        // Nada del otro usuario.
        assertFalse("Cuenta secreta del otro" in html || "777.777.777" in html)

        val fila = transaction {
            EnlacesCompartidos.selectAll().where { EnlacesCompartidos.id eq creado.enlace.id }.single()
        }
        assertEquals(1, fila[EnlacesCompartidos.vistas])
        assertNotNull(fila[EnlacesCompartidos.ultimaVista])
        val lista = client.listar(duena).single()
        assertEquals(1, lista.vistas)
    }

    @Test
    fun `la pagina no muestra numeros de cuenta completos`() = conApp {
        val (_, token) = client.crearYTraerToken()
        val html = client.ver(token).bodyAsText()
        listOf("9876543210123", "4513 2200 1234 5678", "4513", "0012345678", "22001234").forEach {
            assertFalse(it in html, "«$it» no puede salir en la página")
        }
        assertTrue("Hipoteca ••0123" in html, "la cola de cuatro sí, para saber de cuál se habla")
        assertTrue("Visa ••5678" in html)
    }

    @Test
    fun `vencido, revocado e inexistente contestan exactamente lo mismo`() = conApp {
        // Revocado.
        val (revocado, tokenRevocado) = client.crearYTraerToken()
        client.delete("/api/enlaces-compartidos/${revocado.enlace.id}") {
            header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}")
        }
        // Vencido: se crea bien y se le corre el vencimiento al pasado.
        val (vencido, tokenVencido) = client.crearYTraerToken()
        transaction {
            EnlacesCompartidos.update({ EnlacesCompartidos.id eq vencido.enlace.id }) {
                it[venceEn] = System.currentTimeMillis() - 1_000
            }
        }
        // Inexistente, con la forma correcta; y uno que ni siquiera tiene la forma.
        val inventado = TokenDeEnlace.nuevo()

        val respuestas = listOf(tokenRevocado, tokenVencido, inventado, "no-es-un-token", "").map { client.ver(it) }
        respuestas.forEach { assertEquals(HttpStatusCode.NotFound, it.status) }
        val cuerpos = respuestas.map { it.bodyAsText() }.toSet()
        assertEquals(1, cuerpos.size, "un solo cuerpo para todos los casos: $cuerpos")
        assertTrue("no está disponible" in cuerpos.single())
        val tipos = respuestas.map { it.headers[HttpHeaders.ContentType] }.toSet()
        assertEquals(1, tipos.size)

        // Y ninguno de ellos contó una vista.
        val vistas = transaction { EnlacesCompartidos.selectAll().sumOf { it[EnlacesCompartidos.vistas] } }
        assertEquals(0, vistas)
    }

    @Test
    fun `las respuestas de la pagina llevan las cabeceras de privacidad`() = conApp {
        val (_, token) = client.crearYTraerToken()
        val cascara = client.get("/compartido")
        val contenido = client.ver(token)
        val noDisponible = client.ver(TokenDeEnlace.nuevo())
        listOf(cascara, contenido, noDisponible).forEach { r ->
            assertEquals("no-store", r.headers[HttpHeaders.CacheControl])
            assertEquals("no-referrer", r.headers["Referrer-Policy"])
            assertTrue(r.headers["X-Robots-Tag"].orEmpty().contains("noindex"))
            assertTrue(r.headers["X-Robots-Tag"].orEmpty().contains("nofollow"))
            assertTrue(r.headers["Content-Security-Policy"].orEmpty().contains("frame-ancestors 'none'"))
            assertEquals("DENY", r.headers["X-Frame-Options"])
        }
        val html = cascara.bodyAsText()
        assertEquals(HttpStatusCode.OK, cascara.status)
        assertTrue("<meta name=\"robots\" content=\"noindex, nofollow" in html)
        // La cáscara no sabe de quién es nada.
        assertFalse("Juan Villada" in html)
    }

    @Test
    fun `el token no queda en ningun log`() = conApp {
        val raiz = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val registro = ListAppender<ILoggingEvent>().apply { start() }
        raiz.addAppender(registro)
        try {
            val (_, token) = client.crearYTraerToken()
            client.get("/compartido")
            assertEquals(HttpStatusCode.OK, client.ver(token).status)
            client.ver(TokenDeEnlace.nuevo())

            val lineas = registro.list.map { it.formattedMessage }
            // Que el log de peticiones está de verdad escuchando: si no, la prueba no prueba nada.
            assertTrue(lineas.any { "/compartido" in it }, "CallLogging tiene que haber registrado la vista: $lineas")
            assertFalse(lineas.any { token in it }, "el token apareció en el log: $lineas")
        } finally {
            raiz.detachAppender(registro)
        }
    }
}
