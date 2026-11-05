package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conectarBaseDePrueba
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

/**
 * # Vuelta 127 · la notificación de Nu que manda el atajo del iPhone, de punta a punta
 *
 * La automatización «Notificación» de la app Nu le pasa al atajo lo que Nu publicó, y el filtro del atajo
 * (hecho para un SMS: que empiece por un banco y traiga monto) lo descartaba. Ahora entra con el rótulo
 * «Atajo · Notificación · Nu» y el texto del Android: `/parse` la lee igual, la misma por Android y por el
 * atajo es una sola, lo de Nu que no es un movimiento queda apartado, y lo personal se sigue descartando
 * sin el texto.
 */
class NotificacionDeNuPorElAtajoTest {

    private val testSecret = "test-secret-for-nu-por-el-atajo-minimum-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val duena = "user-caro-nu-atajo"
    private val json = Json { ignoreUnknownKeys = true }

    private val tituloDeLaCompra = "Compra aprobada por \$27.500,00"
    private val cuerpoDeLaCompra = "Tu compra en ANTHROPIC* CLAUDE SUB por \$27.500,00 con tu tarjeta terminada en 1336 ha sido APROBADA."

    /** Lo que sube el teléfono Android de esa misma notificación. */
    private val compraDelAndroid = textoDeUnaNotificacion(tituloDeLaCompra, cuerpoDeLaCompra)

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:nu_por_el_atajo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

    // ── La notificación por el atajo ─────────────────────────────────────────────────────────────

    @Test
    fun `una compra de Nu con solo el cuerpo entra con el rotulo nuevo, y parse la lee como la del Android`() = conApp {
        val codigo = crearCodigo()
        val r = avisar(codigo, cuerpoDeLaCompra)
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue("\"guardado\":true" in r.bodyAsText(), r.bodyAsText())

        val fila = filas().single()
        assertEquals(ORIGEN_DE_NU_POR_ATAJO, fila.bank)
        assertEquals(compraDelAndroid, fila.text, "el texto que sube un Android")
        assertEquals(SMS_STATE_PENDING, fila.state)

        val leida = parse(fila.id)
        assertEquals(HttpStatusCode.OK, leida.status, leida.bodyAsText())
        val parsed = leida.body<ParsedSms>()
        val delAndroid = assertNotNull(parseSms(compraDelAndroid, "Notificación · Nu"))
        assertEquals(27_500.0, parsed.amount)
        assertEquals(delAndroid.amount, parsed.amount)
        assertEquals(delAndroid.merchant, parsed.merchant)
        assertEquals(delAndroid.type, parsed.type)
        assertEquals("COP", parsed.currency)
    }

    @Test
    fun `con titulo, en dos renglones o con el nombre de la app delante, es la misma fila`() = conApp {
        val codigo = crearCodigo()
        for (enviado in listOf("$tituloDeLaCompra\n$cuerpoDeLaCompra", "Nu\n$tituloDeLaCompra $cuerpoDeLaCompra", "Nu: $compraDelAndroid")) {
            assertEquals(HttpStatusCode.Accepted, avisar(codigo, enviado).status)
        }
        assertEquals(listOf(compraDelAndroid), filas().map { it.text }, "las tres son la misma notificación")
    }

    @Test
    fun `la misma compra por el Android y por el atajo queda una sola, en los dos ordenes`() = conApp {
        val codigo = crearCodigo()
        // Primero el atajo, después el Android con la hora de esa notificación.
        avisar(codigo, cuerpoDeLaCompra)
        val delAtajo = filas().single()
        subirDelAndroid(SmsMessage(id = "notif_1111222233334444", time = delAtajo.time, bank = "Notificación · Nu", text = compraDelAndroid, state = "", det = ""))
        assertEquals(1, filas().size, "el Android no la repite: ${filas()}")

        // Ahora al revés, con otra compra: primero el Android, después el atajo.
        val otraCompra = textoDeUnaNotificacion(
            "Compra aprobada por \$12.000,00",
            "Tu compra en RAPPI por \$12.000,00 con tu tarjeta terminada en 1336 ha sido APROBADA.",
        )
        val ahora = momentoDelCorreo(null, ahora = System.currentTimeMillis())
        subirDelAndroid(SmsMessage(id = "notif_5555666677778888", time = ahora, bank = "Notificación · Nu", text = otraCompra, state = "", det = ""))
        val r = avisar(codigo, otraCompra.substringAfter(": "))
        assertTrue("\"guardado\":false" in r.bodyAsText(), r.bodyAsText())
        assertEquals(2, filas().size, "el atajo no la repite: ${filas()}")
    }

    @Test
    fun `el pago de la tarjeta y el deposito entran, y un rechazo y la tarjeta verificada quedan apartados`() = conApp {
        val codigo = crearCodigo()
        avisar(codigo, "¡Bravo! Pagaste tu tarjeta de crédito Nu\nRecibimos tu pago por \$1.306.937,91. En un rato podrás verlo en tu app.")
        avisar(codigo, "Recibimos tu depósito por 1.500.000,00\n¡No te preocupes! En unas horas podrás revisar la actualización en tu app.")
        avisar(codigo, "¡Uy! Algo salió mal con tu pago\nTu pago por \$45.000,00 no fue aprobado. Revisa el cupo de tu tarjeta.")
        // «Tarjeta verificada» no trae monto: se reconoce solo con «Nu» delante.
        avisar(codigo, "Nu\nTarjeta verificada\nANTHROPIC verificó tu tarjeta terminada en 1336 para tus pagos.")

        val porTexto = filas().associateBy { it.text.substringBefore(':') }
        assertEquals(4, porTexto.size, "${filas()}")
        assertTrue(porTexto.values.all { it.bank == ORIGEN_DE_NU_POR_ATAJO })
        assertEquals(SMS_STATE_PENDING, porTexto.getValue("¡Bravo! Pagaste tu tarjeta de crédito Nu").state)
        assertEquals(SMS_STATE_PENDING, porTexto.getValue("Recibimos tu depósito por 1.500.000,00").state)
        val rechazo = porTexto.getValue("¡Uy! Algo salió mal con tu pago")
        assertEquals(SMS_STATE_IGNORED to MotivoDeApartado.NO_PASO.name, rechazo.state to rechazo.motivo)
        val verificada = porTexto.getValue("Tarjeta verificada")
        assertEquals(SMS_STATE_IGNORED, verificada.state, "la tarjeta verificada no es un movimiento")
        assertNotNull(verificada.motivo)

        // El pago de la tarjeta se lee como el de un Android.
        val pago = porTexto.getValue("¡Bravo! Pagaste tu tarjeta de crédito Nu")
        assertEquals(parseSms(pago.text, "Notificación · Nu"), parseSms(pago.text, pago.bank))
        assertEquals(1_306_937.91, parseSms(pago.text, pago.bank)?.amount)
    }

    @Test
    fun `el tracking cuenta la notificacion aparte del SMS, sin el texto`() = conApp {
        // El tracking recuerda en memoria lo que ya escribió en este proceso: otra compra, y la memoria limpia.
        com.jvillada.movi.server.observabilidad.Observabilidad.olvidarLoRegistrado()
        val codigo = crearCodigo()
        avisar(codigo, "Tu compra en PANADERIA TRACKING por \$41.234,00 con tu tarjeta terminada en 1336 ha sido APROBADA.")
        val eventos = transaction { UsoDeLaApp.selectAll().map { it[UsoDeLaApp.nombre] to it[UsoDeLaApp.propiedades] } }
        val captura = assertNotNull(eventos.firstOrNull { it.first == "CAPTURA_POR_ATAJO" }, "$eventos")
        assertTrue("GUARDADO" in captura.second && "NOTIFICACION" in captura.second, captura.second)
        assertTrue(eventos.none { "PANADERIA" in it.second || "41.234" in it.second }, "nunca el texto ni el monto: $eventos")
    }

    /**
     * Vuelta 91 por el iPhone: el SMS de Bancolombia del traspaso a Nu y el depósito que avisa Nu son UN pago
     * (`Leido.seJuntaCon`): el depósito cuenta como la notificación de Nu de un Android.
     */
    @Test
    fun `el deposito de Nu por el atajo se junta con el SMS del traspaso`() = conApp {
        val codigo = crearCodigo()
        avisar(codigo, "Bancolombia: Pagaste \$1,500,000.00 a NU Compania de Financiamiento desde tu producto 8133.")
        avisar(codigo, "Recibimos tu depósito por 1.500.000,00\n¡No te preocupes! En unas horas podrás revisar la actualización en tu app.")
        val bandeja = transaction { bandejaConLosPagos(duena, System.currentTimeMillis() + 60_000) }
        assertEquals(setOf("Atajo · Bancolombia", ORIGEN_DE_NU_POR_ATAJO), bandeja.map { it.bank }.toSet(), "$bandeja")
        val grupos = bandeja.map { it.grupoId }.toSet()
        assertEquals(1, grupos.size, "el SMS y el depósito son el mismo pago: $bandeja")
        assertNotNull(grupos.single())
    }

    /** Segunda revisión de la 127 · «no ha sido aprobada» se guarda apartada y no se propone como gasto. */
    @Test
    fun `una compra de Nu que no ha sido aprobada queda apartada y no se propone`() = conApp {
        val codigo = crearCodigo()
        val noPaso = "Tu compra en RAPPI por \$45.000,00 con tu tarjeta terminada en 1336 no ha sido aprobada."
        avisar(codigo, noPaso)
        val fila = filas().single()
        assertEquals(ORIGEN_DE_NU_POR_ATAJO, fila.bank)
        assertEquals(noPaso, fila.text, "no se le pone «Compra aprobada»")
        assertEquals(SMS_STATE_IGNORED to MotivoDeApartado.NO_PASO.name, fila.state to fila.motivo)
        assertEquals(null, parseSms(fila.text, fila.bank), "no se propone como gasto")
        assertEquals(HttpStatusCode.UnprocessableEntity, parse(fila.id).status)
    }

    // ── Lo de antes, igual ───────────────────────────────────────────────────────────────────────

    /**
     * Revisión de la vuelta 127 · lo que se parece a una notificación de Nu sin serlo —una frase de Nu sin
     * monto, o sin una seña que solo escribe Nu— se sigue descartando **sin guardar el texto**: el atajo casi
     * nunca manda remitente, así que nada más lo protege.
     */
    @Test
    fun `un texto personal sigue descartado y sin texto guardado`() = conApp {
        val codigo = crearCodigo()
        val personales = listOf(
            "Nu: ¿vienes a comer?",
            "Hola, ya te pagué los \$20.000 del almuerzo",
            "Algo nuevo de Nu para 2026: mira lo que preparamos para ti.",
            "Te dejé tu cajita en la portería",
            "Mi compra rechazada otra vez jaja",
            "Mi mamá dice que te llegó dinero de la tía",
            "Recibimos tu pago por \$85.000. Gracias, Claro",
            "Tu compra en EXITO por \$50.000 con tu tarjeta terminada en 1234",
            "Recibimos tu depósito del arriendo",
            // Segunda revisión: con «nu»/«Nu» delante, citado en un chat, y un título inventado.
            "Nu te llegó dinero de mi mamá?",
            "nu, tu cajita quedó en la portería",
            "Nu: tu cajita quedó en la portería",
            "jaja mira: $cuerpoDeLaCompra ¿vienes mañana?",
            "Recibiste 300.000 en tu cuenta. Te llegó dinero de mi papá",
        )
        for (texto in personales) {
            val r = avisar(codigo, texto)
            assertEquals(HttpStatusCode.Accepted, r.status)
            assertTrue("\"guardado\":false" in r.bodyAsText(), "$texto → ${r.bodyAsText()}")
        }
        assertTrue(filas().isEmpty(), "nada se guardó: ${filas()}")
        val contados = transaction { AtajosDescartados.selectAll().sumOf { it[AtajosDescartados.cuantos] } }
        assertEquals(personales.size, contados)
    }

    @Test
    fun `un SMS de Bancolombia por el atajo sigue siendo un SMS`() = conApp {
        val codigo = crearCodigo()
        val sms = "Bancolombia: Recibimos tu pago por \$1.000.000,00 a tu tarjeta *9208."
        avisar(codigo, sms)
        assertEquals(listOf("Atajo · Bancolombia" to sms), filas().map { it.bank to it.text })
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

    private suspend fun ApplicationTestBuilder.avisar(codigo: String, texto: String): HttpResponse =
        client.post("/api/captura/atajo") {
            header(HttpHeaders.Authorization, "Bearer $codigo")
            contentType(ContentType.Application.Json)
            setBody("{\"tipo\":\"mensaje\",\"texto\":${JsonPrimitive(texto)}}")
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
