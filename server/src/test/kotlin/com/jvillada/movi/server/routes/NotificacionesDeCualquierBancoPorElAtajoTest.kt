package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.momentoDelCorreo
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AtajosDescartados
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CodigosDeCaptura
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
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CodigoDeCapturaNuevo
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.ORIGEN_DE_NU_POR_ATAJO
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.textoDeUnaNotificacion
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.MotivoDelAtajoDescartado
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.esUnAtajo
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * # Vuelta 137 · en el iPhone, las notificaciones de cualquier banco entran a Movi
 *
 * El atajo «Movi - Avisar una notificacion» manda `tipo` = `notificacion` y el dueño lo ata solo a disparadores
 * «Notificación» de las apps de sus bancos. El server guarda lo que trae un monto (Nu primero, con su texto y su
 * rótulo), con «Atajo · Notificación · <banco>», y lo demás se cuenta sin guardarse. `mensaje` no cambia.
 *
 * **Los textos de Davivienda, Nequi, Bancolombia y Lulo de esta prueba son INVENTADOS** (formas plausibles de una
 * notificación de app; ninguno salió de un teléfono): lo que mandan de verdad se anota en
 * `docs/lanzamiento/atajos-de-iphone.md`, «Vuelta 137».
 */
class NotificacionesDeCualquierBancoPorElAtajoTest {

    private val testSecret = "test-secret-for-notificaciones-por-el-atajo-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val duena = "user-caro-notificaciones-atajo"
    private val json = Json { ignoreUnknownKeys = true }

    // INVENTADAS: formas plausibles, no copiadas de un teléfono.
    private val deDavivienda = "Davivienda\nCompra por \$85.400 en EXITO COLOMBIA con tu tarjeta débito *1234."
    private val deNequi = "Nequi\n¡Te llegó plata! JUAN PEREZ te envió \$50.000."
    private val deBancolombia = "Bancolombia\nPagaste \$45.900 en RAPPI COLOMBIA con tu tarjeta *4321."
    private val sinBanco = "Compra aprobada\nCompraste \$32.000 en TIENDA D1 con tu tarjeta débito Lulo *9876."

    /** La forma real de Nu (dígitos cambiados por 9), la que midió producción el 6-nov. */
    private val deNu = "Recibiste 999.999,99 en tu cuenta: Te llegó dinero de NOMBRE con tu llave."

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:notificaciones_por_el_atajo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations, DestinosDescartados,
            CodigosDeCaptura, AtajosDescartados, UsoDeLaApp,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert {
                it[id] = duena
                it[email] = "$duena@atajo.test"
                it[name] = duena
                it[passwordHash] = "hash"
            }
        }
    }

    @AfterTest
    fun tearDown() = RateLimiter.reset()

    @Test
    fun `las notificaciones de Davivienda, Nequi y la app Bancolombia se guardan con su banco y se leen`() = conApp {
        val codigo = crearCodigo()
        for (texto in listOf(deDavivienda, deNequi, deBancolombia)) {
            val r = avisar(codigo, texto)
            assertEquals(HttpStatusCode.Accepted, r.status)
            assertTrue("\"guardado\":true" in r.bodyAsText(), "$texto → ${r.bodyAsText()}")
        }
        val porBanco = filas().associateBy { it.bank }
        assertEquals(
            setOf("Atajo · Notificación · Davivienda", "Atajo · Notificación · Nequi", "Atajo · Notificación · Bancolombia"),
            porBanco.keys,
        )
        assertTrue(porBanco.values.all { it.state == SMS_STATE_PENDING }, "nada se aparta ni se anota: ${filas()}")
        // El texto, como lo sube un Android: «título: cuerpo».
        assertEquals(textoDeUnaNotificacion("Davivienda", deDavivienda.substringAfter('\n')), porBanco.getValue("Atajo · Notificación · Davivienda").text)
        // `parseSms` los lee con su monto, con el rótulo guardado.
        val montos = porBanco.mapValues { (_, f) -> parseSms(f.text, f.bank)?.amount }
        assertEquals(85_400.0, montos["Atajo · Notificación · Davivienda"])
        assertEquals(50_000.0, montos["Atajo · Notificación · Nequi"])
        assertEquals(45_900.0, montos["Atajo · Notificación · Bancolombia"])
        assertTrue(porBanco.keys.all { esUnAtajo(it) && esUnaNotificacion(it) })
    }

    @Test
    fun `sin el banco en el texto queda tu banco, y el comercio o la tarjeta no lo nombran`() = conApp {
        val codigo = crearCodigo()
        avisar(codigo, sinBanco)
        // Una notificación de Nu sin «Nu» delante que nombra a otro banco en el cuerpo: no es de Davivienda.
        avisar(codigo, "Pago aprobado por \$99.999,99\nPagaste en Banco Davivienda SA con tu cuenta de ahorros.")
        assertEquals(listOf("Atajo · Notificación · tu banco"), filas().map { it.bank }.distinct(), "${filas()}")
        assertEquals(setOf(32_000.0, 99_999.99), filas().mapNotNull { parseSms(it.text, it.bank)?.amount }.toSet())
    }

    @Test
    fun `la de Nu sin signo pesos entra con el rotulo y el texto de Nu`() = conApp {
        val codigo = crearCodigo()
        val r = avisar(codigo, deNu)
        assertTrue("\"guardado\":true" in r.bodyAsText(), r.bodyAsText())
        val fila = filas().single()
        assertEquals(ORIGEN_DE_NU_POR_ATAJO, fila.bank)
        assertEquals(deNu, fila.text)
        assertEquals(999_999.99, parseSms(fila.text, fila.bank)?.amount)
    }

    @Test
    fun `una notificacion sin monto se cuenta como sin forma de aviso y no se guarda`() = conApp {
        val codigo = crearCodigo()
        for (texto in listOf(
            "Davivienda\nTu tarjeta terminada en 1234 fue bloqueada por seguridad.",
            "Bancolombia\nTu clave dinámica es 123456. No la compartas.",
            "Nequi\n¡Este fin de semana hay descuentos para ti!",
        )) {
            val r = avisar(codigo, texto)
            assertEquals(HttpStatusCode.Accepted, r.status)
            assertTrue("\"guardado\":false" in r.bodyAsText(), r.bodyAsText())
        }
        assertTrue(filas().isEmpty(), "nada se guardó: ${filas()}")
        assertEquals(mapOf(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO.name to 3), descartados())
    }

    @Test
    fun `la misma notificacion dos veces deja una fila`() = conApp {
        val codigo = crearCodigo()
        avisar(codigo, deDavivienda)
        val r = avisar(codigo, deDavivienda)
        assertTrue("\"guardado\":false" in r.bodyAsText(), r.bodyAsText())
        // En dos renglones o ya con «: », es la misma.
        avisar(codigo, textoDeUnaNotificacion("Davivienda", deDavivienda.substringAfter('\n')))
        assertEquals(1, filas().size, "${filas()}")
    }

    @Test
    fun `por mensaje una notificacion de Davivienda que no empieza por el banco se sigue descartando`() = conApp {
        val codigo = crearCodigo()
        val noEmpiezaPorElBanco = "Compra aprobada\nCompraste \$85.400 en EXITO con tu tarjeta Davivienda *1234."
        val r = avisar(codigo, noEmpiezaPorElBanco, tipo = "mensaje")
        assertTrue("\"guardado\":false" in r.bodyAsText(), r.bodyAsText())
        assertTrue(filas().isEmpty())
        assertEquals(mapOf(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO.name to 1), descartados())
        // La misma, por el atajo de notificaciones, sí entra.
        assertTrue("\"guardado\":true" in avisar(codigo, noEmpiezaPorElBanco).bodyAsText())
    }

    @Test
    fun `el tracking la cuenta como notificacion, sin el texto`() = conApp {
        com.jvillada.movi.server.observabilidad.Observabilidad.olvidarLoRegistrado()
        val codigo = crearCodigo()
        avisar(codigo, "Davivienda\nCompra por \$41.234 en PANADERIA TRACKING con tu tarjeta *1234.")
        val eventos = transaction { UsoDeLaApp.selectAll().map { it[UsoDeLaApp.nombre] to it[UsoDeLaApp.propiedades] } }
        val captura = assertNotNull(eventos.firstOrNull { it.first == "CAPTURA_POR_ATAJO" }, "$eventos")
        assertTrue("GUARDADO" in captura.second && "NOTIFICACION" in captura.second, captura.second)
        assertTrue(eventos.none { "PANADERIA" in it.second || "41.234" in it.second }, "nunca el texto ni el monto: $eventos")
    }

    @Test
    fun `el atajo de notificaciones firmado se sirve con su nombre, tambien con tilde, y la lectura del codigo lo dice`() = conApp {
        val r = client.get(RUTA_DEL_ATAJO_DE_NOTIFICACIONES)
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("AEA1", String(r.body<ByteArray>().copyOfRange(0, 4)))
        assertTrue("filename=\"$ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES\"" in r.headers[HttpHeaders.ContentDisposition].orEmpty())
        val conTilde = client.get("/atajos/Movi%20-%20Avisar%20una%20notificaci%C3%B3n.shortcut")
        assertEquals(HttpStatusCode.OK, conTilde.status)
        assertTrue("filename=\"$ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES\"" in conTilde.headers[HttpHeaders.ContentDisposition].orEmpty())
        val leido = cliente().get("/api/captura/codigo") { autorizado() }.body<CodigoDeCaptura>()
        assertTrue(leido.atajoDeNotificaciones)
        assertFalse(leido.atajoDeApplePay)
    }

    @Test
    fun `un tipo que no existe sigue siendo 400`() = conApp {
        val codigo = crearCodigo()
        assertEquals(HttpStatusCode.BadRequest, avisar(codigo, deDavivienda, tipo = "otra-cosa").status)
    }

    // ── Lo de las pruebas ────────────────────────────────────────────────────────────────────────

    private data class Fila(val id: String, val time: String, val bank: String, val text: String, val state: String, val motivo: String?)

    private fun filas(): List<Fila> = transaction {
        SmsMessages.selectAll().where { SmsMessages.userId eq duena }.map {
            Fila(it[SmsMessages.id], it[SmsMessages.time], it[SmsMessages.bank], it[SmsMessages.text], it[SmsMessages.state], it[SmsMessages.motivoApartado])
        }
    }

    private fun conApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { modulo() }
        block()
    }

    private fun Application.modulo() {
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

    private fun token(): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", duena).withClaim("email", "$duena@atajo.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun io.ktor.client.request.HttpRequestBuilder.autorizado() = header(HttpHeaders.Authorization, "Bearer ${token()}")

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun ApplicationTestBuilder.crearCodigo(): String {
        val r = client.post("/api/captura/codigo") { autorizado() }
        assertEquals(HttpStatusCode.Created, r.status)
        return json.decodeFromString(CodigoDeCapturaNuevo.serializer(), r.bodyAsText()).codigo
    }

    private suspend fun ApplicationTestBuilder.avisar(codigo: String, texto: String, tipo: String = "notificacion"): HttpResponse =
        client.post("/api/captura/atajo") {
            header(HttpHeaders.Authorization, "Bearer $codigo")
            contentType(ContentType.Application.Json)
            setBody("{\"tipo\":\"$tipo\",\"texto\":${JsonPrimitive(texto)}}")
        }

    private fun descartados(): Map<String, Int> = transaction {
        AtajosDescartados.selectAll().groupBy({ it[AtajosDescartados.motivo] }, { it[AtajosDescartados.cuantos] })
            .mapValues { it.value.sum() }
    }

    private suspend fun ApplicationTestBuilder.subirDelAndroid(vararg mensajes: SmsMessage) {
        val r = cliente().post("/api/sms/sync") {
            autorizado()
            contentType(ContentType.Application.Json)
            setBody(mensajes.toList())
        }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    private suspend fun ApplicationTestBuilder.parse(id: String): HttpResponse = cliente().get("/api/sms/$id/parse") { autorizado() }
}
