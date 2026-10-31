package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.ContenidoDeResend
import com.jvillada.movi.server.correo.LectorDeCorreosRecibidos
import com.jvillada.movi.server.correo.ListadoDeResend
import com.jvillada.movi.server.correo.MAX_TEXTO_DE_CORREO
import com.jvillada.movi.server.correo.RecibidoEnResend
import com.jvillada.movi.server.correo.alertaPostmark
import com.jvillada.movi.server.correo.contenidoDeResend
import com.jvillada.movi.server.correo.eventoDeResend
import com.jvillada.movi.server.correo.firmarComoSvix
import com.jvillada.movi.server.correo.leerListadoDeResend
import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.CorreosDescartados
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CorreosDescartados as CorreosDescartadosDelWire
import com.jvillada.movi.shared.model.MotivoDeCorreoDescartado
import com.jvillada.movi.shared.model.textoDeCorreosDescartados
import io.ktor.client.HttpClient
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
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 90 · el correo del banco no se pierde en silencio
 *
 * Lo que el webhook contestaba con un 202 sin escribir nada ahora deja su fila en `correos_descartados`
 * (sin el cuerpo); una alerta larga se guarda con su monto; y un reproceso diario recoge lo que Resend
 * recibió y el webhook nunca entregó. Ninguna prueba toca la red: Resend es un lector falso.
 */
class CorreosQueNoSePierdenTest {

    private val testSecret = "test-secret-for-correos-que-no-se-pierden-32"
    private val secretoDePostmark = "un-secreto-de-webhook-largo-y-random-v90"
    private val secretoDeResend = "whsec_" + Base64.getEncoder().encodeToString("secreto-de-prueba-del-webhook-v90".toByteArray())
    private val userAId = "user-a-v90"
    private val userBId = "user-b-v90"
    private val dominio = "abc123.resend.app"
    private val direccionDeA get() = "alertas+${tokenDeCorreoDe(userAId)}@$dominio"

    private val enResend = mutableMapOf<String, ContenidoDeResend>()
    private val pedidos = CopyOnWriteArrayList<String>()
    private var listado: ListadoDeResend = ListadoDeResend.SinClave
    private val lectorOriginal = LectorDeCorreosRecibidos.actual

    /** Un Resend falso: lo que «tiene» por id y lo que lista. Cuenta lo que se le pidió. */
    private val lectorFalso = object : LectorDeCorreosRecibidos {
        override suspend fun traer(idDelCorreo: String): ContenidoDeResend {
            pedidos += idDelCorreo
            return enResend[idDelCorreo] ?: ContenidoDeResend.NoEncontrado
        }

        override suspend fun listarRecibidos(desde: Instant): ListadoDeResend = listado
    }

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.secreto", secretoDePostmark)
        System.setProperty("movi.correo.resend.secreto", secretoDeResend)
        System.setProperty("movi.correo.direccion", "alertas@$dominio")
        RateLimiter.reset()
        LectorDeCorreosRecibidos.actual = lectorFalso
        Database.connect(
            url = "jdbc:h2:mem:correos_que_no_se_pierden;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(CorreosDescartados, SmsMessages, PushSubscriptions, Users)
            SchemaUtils.create(Users, SmsMessages, PushSubscriptions, CorreosDescartados)
            for (id in listOf(userAId, userBId)) {
                Users.insert { it[Users.id] = id; it[email] = "$id@v90.test"; it[name] = id; it[passwordHash] = "h" }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.secreto")
        System.clearProperty("movi.correo.resend.secreto")
        System.clearProperty("movi.correo.direccion")
        System.clearProperty("movi.admin.userIds")
        LectorDeCorreosRecibidos.actual = lectorOriginal
        RateLimiter.reset()
    }

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { JWTPrincipal(it.payload) }
            }
        }
        configureRouting()
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@v90.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun basic() = "Basic " + Base64.getEncoder().encodeToString("movi:$secretoDePostmark".toByteArray())

    private suspend fun HttpClient.postmark(cuerpo: String): HttpResponse = post("/api/correo-entrante") {
        header(HttpHeaders.Authorization, basic())
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    private suspend fun HttpClient.webhookDeResend(cuerpo: String): HttpResponse {
        val ts = (System.currentTimeMillis() / 1000).toString()
        return post("/api/correo-entrante/resend") {
            contentType(ContentType.Application.Json)
            header("svix-id", "msg_v90")
            header("svix-timestamp", ts)
            header("svix-signature", firmarComoSvix(secretoDeResend, "msg_v90", ts, cuerpo.toByteArray()))
            setBody(cuerpo)
        }
    }

    private fun bandeja() = transaction {
        SmsMessages.selectAll().map { Triple(it[SmsMessages.userId], it[SmsMessages.text], it[SmsMessages.idDelProveedor]) }
    }

    private data class Descarte(val usuario: String, val motivo: String, val dominio: String?, val idDelProveedor: String?)

    private fun descartados() = transaction {
        CorreosDescartados.selectAll().map {
            Descarte(it[CorreosDescartados.userId], it[CorreosDescartados.motivo], it[CorreosDescartados.dominio], it[CorreosDescartados.idDelProveedor])
        }
    }

    // ── Lo descartado queda anotado ─────────────────────────────────────────────

    @Test
    fun `un correo sin codigo queda anotado como descartado y no entra a la bandeja`() = testApplication {
        application { testModule() }
        val r = client.postmark(alertaPostmark("alertas@inbound.postmarkapp.com"))
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue(bandeja().isEmpty())
        val d = descartados().single()
        assertEquals("", d.usuario, "no es de nadie")
        assertEquals(MotivoDeCorreoDescartado.SIN_CODIGO.name, d.motivo)
        assertEquals("notificacionesbancolombia.com", d.dominio, "del remitente solo el dominio")
        // Y nada del correo: ni el texto ni la dirección.
        val crudo = transaction { CorreosDescartados.selectAll().single().toString() }
        assertTrue("21.640" !in crudo && "juan@gmail.com" !in crudo && "alertasynotificaciones" !in crudo, crudo)
    }

    @Test
    fun `un codigo que no es de nadie tambien queda anotado`() = testApplication {
        application { testModule() }
        client.postmark(alertaPostmark("alertas+0000000000000000@inbound.postmarkapp.com"))
        assertEquals(MotivoDeCorreoDescartado.CODIGO_DE_NADIE.name, descartados().single().motivo)
        assertTrue(bandeja().isEmpty())
    }

    @Test
    fun `un correo gigante o ilegible queda anotado`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.PayloadTooLarge, client.postmark(alertaPostmark(direccionDeA, cuerpo = "x".repeat(300_000))).status)
        assertEquals(HttpStatusCode.BadRequest, client.postmark("esto no es un correo").status)
        assertEquals(
            setOf(MotivoDeCorreoDescartado.DEMASIADO_GRANDE.name, MotivoDeCorreoDescartado.ILEGIBLE.name),
            descartados().map { it.motivo }.toSet(),
        )
        assertTrue(bandeja().isEmpty())
    }

    // ── La alerta larga no pierde el monto ──────────────────────────────────────

    @Test
    fun `una alerta larga y atribuible se guarda con su monto`() = testApplication {
        application { testModule() }
        val relleno = "Este mensaje es confidencial y para uso exclusivo de su destinatario. ".repeat(25)
        val cuerpo = "Hola Juan,\n\n$relleno\n\nBancolombia: Compraste COP86.900,00 en TIENDA LA 14 con tu T.Cred *3684, el 30/10/2026 a las 12:10.\n\nGracias."
        assertTrue(cuerpo.length > MAX_TEXTO_DE_CORREO + 200, "el monto queda pasado el corte de siempre")
        assertEquals(HttpStatusCode.Accepted, client.postmark(alertaPostmark(direccionDeA, asunto = "Alerta de compra", cuerpo = cuerpo)).status)
        val (uid, texto, _) = bandeja().single()
        assertEquals(userAId, uid)
        assertTrue(texto.length <= MAX_TEXTO_DE_CORREO)
        assertTrue(texto.startsWith("Alerta de compra: … "), texto)
        assertTrue("Compraste COP86.900,00 en TIENDA LA 14" in texto, texto)
        assertEquals(86_900.0, assertNotNull(parseSms(texto, "Correo · Bancolombia")).amount)
    }

    @Test
    fun `una alerta corta se guarda igual que siempre`() {
        assertEquals("Alerta: Compraste \$5.000 en X", textoDelCorreo("Alerta", "Compraste \$5.000 en X"))
        // Sin ningún monto, el corte de siempre.
        assertEquals("y".repeat(MAX_TEXTO_DE_CORREO), textoDelCorreo("", "y".repeat(3_000)))
    }

    // ── Resend: lo que cabe, y el reproceso ─────────────────────────────────────

    @Test
    fun `un correo de Resend demasiado grande se guarda con el asunto del evento`() = testApplication {
        application { testModule() }
        enResend["56761188-7520-42d8-8898-ff6fc54ce618"] = ContenidoDeResend.DemasiadoGrande
        val r = client.webhookDeResend(eventoDeResend(recibidoPara = listOf(direccionDeA)))
        assertEquals(HttpStatusCode.Accepted, r.status)
        val (uid, texto, idDelProveedor) = bandeja().single()
        assertEquals(userAId, uid)
        assertEquals("Bancolombia te informa", texto, "lo que cabía: el asunto")
        assertEquals("56761188-7520-42d8-8898-ff6fc54ce618", idDelProveedor)
        assertTrue(descartados().isEmpty())
    }

    private fun recibido(id: String) = RecibidoEnResend(
        id = id,
        destinatarios = listOf(direccionDeA),
        asunto = "Bancolombia te informa",
        remitente = "alertasynotificaciones@notificacionesbancolombia.com",
        recibidoEn = Instant.now(),
    )

    @Test
    fun `el reproceso trae una sola vez lo que el webhook no entrego`() = testApplication {
        application { testModule() }
        val llego = "aaaaaaaa-0000-0000-0000-000000000001"
        val faltante = "bbbbbbbb-0000-0000-0000-000000000002"
        enResend[llego] = ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf(direccionDeA), messageId = "<llego@banco>"))
        enResend[faltante] = ContenidoDeResend.Encontrado(
            contenidoDeResend(
                recibidoPara = listOf(direccionDeA),
                messageId = "<faltante@banco>",
                cuerpo = "Bancolombia: Pagaste \$138,600.00 a Salud Total S A desde tu producto 3333 el 05/09/2026 17:16:47.",
            ),
        )
        // El primero sí llegó por el webhook.
        assertEquals(HttpStatusCode.Accepted, client.webhookDeResend(eventoDeResend(idDelCorreo = llego, recibidoPara = listOf(direccionDeA))).status)
        pedidos.clear()

        listado = ListadoDeResend.Recibidos(listOf(recibido(llego), recibido(faltante)))
        assertEquals(1, runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), lectorFalso) })
        assertEquals(listOf(faltante), pedidos.toList(), "solo se pide el que falta")
        assertEquals(2, bandeja().size)
        assertEquals(setOf(llego, faltante), bandeja().map { it.third }.toSet())

        // Una segunda pasada no pide ni duplica nada.
        pedidos.clear()
        assertEquals(0, runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), lectorFalso) })
        assertTrue(pedidos.isEmpty())
        assertEquals(2, bandeja().size)
    }

    @Test
    fun `lo que ya se descarto no se vuelve a reprocesar`() = testApplication {
        application { testModule() }
        val ajeno = "cccccccc-0000-0000-0000-000000000003"
        enResend[ajeno] = ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf("alertas@$dominio"), messageId = "<ajeno@banco>"))
        listado = ListadoDeResend.Recibidos(listOf(recibido(ajeno).copy(destinatarios = listOf("alertas@$dominio"))))
        runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), lectorFalso) }
        assertEquals(MotivoDeCorreoDescartado.SIN_CODIGO.name, descartados().single().motivo)
        assertEquals(ajeno, descartados().single().idDelProveedor)
        pedidos.clear()
        runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), lectorFalso) }
        assertTrue(pedidos.isEmpty())
        assertEquals(1, descartados().size)
    }

    @Test
    fun `sin la clave de Resend el reproceso no hace nada`() {
        listado = ListadoDeResend.SinClave
        enResend["dddddddd-0000-0000-0000-000000000004"] = ContenidoDeResend.Encontrado(contenidoDeResend(recibidoPara = listOf(direccionDeA)))
        assertEquals(0, runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), lectorFalso) })
        assertTrue(pedidos.isEmpty())
        assertTrue(bandeja().isEmpty())
        // Y un lector que solo sabe traer (el de cualquier prueba vieja) tampoco lista nada.
        val soloTrae = LectorDeCorreosRecibidos { ContenidoDeResend.NoEncontrado }
        assertEquals(0, runBlocking { reprocesarCorreosDeResend(System.currentTimeMillis(), soloTrae) })
    }

    @Test
    fun `la pagina de Resend se lee sin red`() {
        val (correos, hayMas) = assertNotNull(
            leerListadoDeResend(
                """{"object":"list","has_more":true,"data":[
                  {"id":"56761188-7520-42d8-8898-ff6fc54ce618","to":["a@b.co"],"from":"Banco <x@banco.co>",
                   "created_at":"2026-10-30 14:02:11.123+00","subject":"Alerta"},
                  {"id":"../../malo","to":[],"created_at":"2026-10-30T14:02:11Z"}
                ]}""",
            ),
        )
        assertTrue(hayMas)
        val c = correos.single()
        assertEquals(listOf("a@b.co"), c.destinatarios)
        assertEquals(Instant.parse("2026-10-30T14:02:11.123Z"), c.recibidoEn)
        assertNull(leerListadoDeResend("no es json"))
    }

    // ── Mostrarlo ───────────────────────────────────────────────────────────────

    private suspend fun HttpClient.verDescartados(uid: String): CorreosDescartadosDelWire {
        val r = get("/api/correo-entrante/descartados") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, r.status)
        return Json { ignoreUnknownKeys = true }.decodeFromString(CorreosDescartadosDelWire.serializer(), r.bodyAsText())
    }

    @Test
    fun `cada uno ve lo suyo, y lo de nadie solo un admin y solo contado`() = testApplication {
        application { testModule() }
        System.setProperty("movi.admin.userIds", userAId)
        runBlocking {
            registrarCorreoDescartado(null, MotivoDeCorreoDescartado.SIN_CODIGO, "alertas@banco.co")
            registrarCorreoDescartado(null, MotivoDeCorreoDescartado.SIN_CODIGO, "alertas@banco.co")
            registrarCorreoDescartado(userBId, MotivoDeCorreoDescartado.ILEGIBLE, "x@banco.co", idDelProveedor = "e1")
            registrarCorreoDescartado(userBId, MotivoDeCorreoDescartado.DUPLICADO, "x@banco.co", idDelProveedor = "e2")
            // Más viejo que la ventana: no cuenta.
            registrarCorreoDescartado(userBId, MotivoDeCorreoDescartado.ILEGIBLE, ahora = System.currentTimeMillis() - 40L * 86_400_000L)
        }
        val deB = client.verDescartados(userBId)
        assertNull(deB.sinDueno, "lo de nadie no se le muestra a cualquiera")
        assertEquals(1, deB.propios.single().cuantos, "sin las copias ni lo viejo")
        assertEquals("1 correo tuyo no se pudo guardar en los últimos 30 días: no se pudo leer.", textoDeCorreosDescartados(deB))

        val deA = client.verDescartados(userAId)
        assertTrue(deA.propios.isEmpty())
        assertEquals(2, assertNotNull(deA.sinDueno).single().cuantos)
        assertEquals(
            "2 correos no se pudieron atribuir en los últimos 30 días: la dirección no trae el código de Movi.",
            textoDeCorreosDescartados(deA),
        )
        // El wire no lleva dominios ni ids.
        val crudo = client.get("/api/correo-entrante/descartados") { header(HttpHeaders.Authorization, "Bearer ${token(userAId)}") }.bodyAsText()
        assertTrue("banco.co" !in crudo, crudo)
    }

    @Test
    fun `sin nada descartado no hay linea`() = testApplication {
        application { testModule() }
        assertNull(textoDeCorreosDescartados(client.verDescartados(userAId)))
    }
}
