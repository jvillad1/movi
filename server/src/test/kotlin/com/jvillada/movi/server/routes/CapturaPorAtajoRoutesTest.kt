package com.jvillada.movi.server.routes

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.AtajosDescartados
import com.jvillada.movi.server.db.CodigosDeCaptura
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.plugins.configureMonitoring
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.CodigoDeCapturaNuevo
import com.jvillada.movi.shared.model.ORIGEN_DE_APPLE_PAY
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 111 · los atajos del iPhone, extremo a extremo contra H2: el código de captura y
 * `POST /api/captura/atajo`. Como el correo entrante, una ruta pública que escribe en la bandeja de
 * alguien se juzga sobre todo por lo que rechaza.
 */
class CapturaPorAtajoRoutesTest {

    private val testSecret = "test-secret-for-captura-por-atajo-tests-minimum-32"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val duena = "user-caro-atajo"
    private val otra = "user-lina-atajo"
    private val json = Json { ignoreUnknownKeys = true }

    /** Un SMS real de Bancolombia (la forma de los fixtures del repo), sin fecha en el texto. */
    private val smsDeBancolombia = "Bancolombia: Compraste \$27.500,00 en RAPPI COLOMBIA con tu T.Cred *3684."

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:captura_por_atajo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(Users, SmsMessages, CodigosDeCaptura, AtajosDescartados, UsoDeLaApp)
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            listOf(duena, otra).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@atajo.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @AfterTest
    fun tearDown() = RateLimiter.reset()

    private fun jwtDe(uid: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@atajo.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.modulo() {
        configureSerialization()
        // Con el log de peticiones puesto, como en producción: una prueba lo lee.
        configureMonitoring()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { cred -> if (cred.payload.getClaim("userId").asString() != null) JWTPrincipal(cred.payload) else null }
            }
        }
        configureRouting()
    }

    private fun conApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { modulo() }
        block()
    }

    private suspend fun HttpClient.crearCodigo(uid: String = duena): String {
        val r = post("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(uid)}") }
        assertEquals(HttpStatusCode.Created, r.status)
        return json.decodeFromString(CodigoDeCapturaNuevo.serializer(), r.bodyAsText()).codigo
    }

    private suspend fun HttpClient.leerCodigo(uid: String = duena): CodigoDeCaptura {
        val r = get("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(uid)}") }
        assertEquals(HttpStatusCode.OK, r.status)
        return json.decodeFromString(CodigoDeCaptura.serializer(), r.bodyAsText())
    }

    private suspend fun HttpClient.avisar(codigo: String?, cuerpo: String): HttpResponse =
        post("/api/captura/atajo") {
            if (codigo != null) header(HttpHeaders.Authorization, "Bearer $codigo")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private fun mensaje(texto: String, remitente: String? = null): String =
        buildString {
            append("{\"tipo\":\"mensaje\",\"texto\":")
            append(kotlinx.serialization.json.JsonPrimitive(texto).toString())
            if (remitente != null) append(",\"remitente\":\"$remitente\"")
            append("}")
        }

    private fun pago(comercio: String, monto: String, tarjeta: String = "Master Black") =
        """{"tipo":"transaccion","comercio":"$comercio","monto":"$monto","tarjeta":"$tarjeta"}"""

    private fun filas(uid: String = duena) = transaction {
        SmsMessages.selectAll().where { SmsMessages.userId eq uid }.map {
            Triple(it[SmsMessages.bank], it[SmsMessages.text], it[SmsMessages.state])
        }
    }

    // ── El código ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `crear, revocar y volver a crear deja el anterior en 401`() = conApp {
        val primero = client.crearCodigo()
        assertEquals(HttpStatusCode.Accepted, client.avisar(primero, mensaje(smsDeBancolombia)).status)

        val r = client.delete("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertFalse(client.leerCodigo().vigente)
        assertEquals(HttpStatusCode.Unauthorized, client.avisar(primero, mensaje(smsDeBancolombia)).status)

        val segundo = client.crearCodigo()
        val tercero = client.crearCodigo()
        // Crear otro revoca el anterior: solo el último sirve.
        assertEquals(HttpStatusCode.Unauthorized, client.avisar(primero, mensaje("Bancolombia: Compraste \$1.000,00 en A")).status)
        assertEquals(HttpStatusCode.Unauthorized, client.avisar(segundo, mensaje("Bancolombia: Compraste \$2.000,00 en B")).status)
        assertEquals(HttpStatusCode.Accepted, client.avisar(tercero, mensaje("Bancolombia: Compraste \$3.000,00 en C")).status)
        assertEquals(1, transaction { CodigosDeCaptura.selectAll().where { CodigosDeCaptura.revocadoEn.isNull() }.count() })
    }

    @Test
    fun `dos Crear a la vez dejan un solo codigo vigente`() {
        kotlinx.coroutines.runBlocking {
            (1..8).map {
                async(kotlinx.coroutines.Dispatchers.IO) { crearCodigoDeCaptura(duena, System.currentTimeMillis()) }
            }.awaitAll()
        }
        val vigentes = transaction {
            CodigosDeCaptura.selectAll().where { (CodigosDeCaptura.userId eq duena) and CodigosDeCaptura.revocadoEn.isNull() }.count()
        }
        assertEquals(1, vigentes)
        assertEquals(8, transaction { CodigosDeCaptura.selectAll().count() })
    }

    @Test
    fun `el codigo no se guarda en claro y la lectura no lo devuelve`() = conApp {
        val codigo = client.crearCodigo()
        val guardado = transaction {
            CodigosDeCaptura.selectAll().flatMap { fila -> CodigosDeCaptura.columns.map { fila[it]?.toString().orEmpty() } }
        }
        assertFalse(guardado.any { codigo in it }, "el código quedó en claro en la base")
        val r = client.get("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
        assertFalse(codigo in r.bodyAsText())
        val leido = client.leerCodigo()
        assertTrue(leido.vigente)
        assertNotNull(leido.creadoEn)
        assertNull(leido.ultimoUso, "todavía no llegó ningún aviso")

        client.avisar(codigo, mensaje(smsDeBancolombia))
        assertNotNull(client.leerCodigo().ultimoUso, "el aviso anota el último uso")
    }

    @Test
    fun `el codigo de captura no abre ninguna ruta de la sesion`() = conApp {
        val codigo = client.crearCodigo()
        val r = client.get("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer $codigo") }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    // ── La ruta pública ────────────────────────────────────────────────────────────────────────

    @Test
    fun `sin codigo, con uno inventado o con uno revocado da 401 y no escribe nada`() = conApp {
        assertEquals(HttpStatusCode.Unauthorized, client.avisar(null, mensaje(smsDeBancolombia)).status)
        assertEquals(HttpStatusCode.Unauthorized, client.avisar("corto", mensaje(smsDeBancolombia)).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.avisar(com.jvillada.movi.server.compartir.TokenDeEnlace.nuevo(), mensaje(smsDeBancolombia)).status,
        )
        // Un JWT de sesión no es un código de captura.
        assertEquals(HttpStatusCode.Unauthorized, client.avisar(jwtDe(duena), mensaje(smsDeBancolombia)).status)
        assertTrue(filas().isEmpty())
    }

    @Test
    fun `un SMS de Bancolombia entra a la bandeja de su duena, pendiente y con su origen`() = conApp {
        val codigo = client.crearCodigo()
        val r = client.avisar(codigo, mensaje(smsDeBancolombia, remitente = "85540"))
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue("\"guardado\":true" in r.bodyAsText())
        assertEquals(listOf(Triple("Atajo · Bancolombia", smsDeBancolombia, SMS_STATE_PENDING)), filas())
        assertTrue(filas(otra).isEmpty(), "nada en la bandeja de otra persona")
    }

    @Test
    fun `el tracking del server registra la captura sin el texto`() = conApp {
        // Otro texto y otra cuenta: el tracking recuerda en memoria lo que ya escribió en este proceso.
        val codigo = client.crearCodigo(otra)
        val texto = "Bancolombia: Compraste \$41.234,00 en PANADERIA TRACKING con tu T.Deb *1111."
        client.avisar(codigo, mensaje(texto))
        val eventos = transaction { UsoDeLaApp.selectAll().map { it[UsoDeLaApp.nombre] to it[UsoDeLaApp.propiedades] } }
        val captura = eventos.firstOrNull { it.first == "CAPTURA_POR_ATAJO" }
        assertNotNull(captura, "el tracking del server registra la captura: $eventos")
        assertTrue("GUARDADO" in captura.second)
        assertTrue(eventos.none { "PANADERIA" in it.second || "41.234" in it.second }, "nunca el texto ni el monto: $eventos")
    }

    @Test
    fun `el banco sale del texto si el remitente no lo dice, y el remitente crudo nunca se guarda`() = conApp {
        val codigo = client.crearCodigo()
        val texto = "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA."
        client.avisar(codigo, mensaje(texto, remitente = "Mamá"))
        assertEquals("Atajo · Davivienda", filas().single().first)
        client.avisar(codigo, mensaje("Movimiento en tu cuenta nubank de \$5.000", remitente = "Tía Rosa"))
        assertTrue(filas().none { "Tía Rosa" in it.first })
    }

    @Test
    fun `un SMS personal no se guarda, solo se cuenta sin texto`() = conApp {
        val codigo = client.crearCodigo()
        val r = client.avisar(codigo, mensaje("Hola, ya llegué a la casa. Te quiero", remitente = "Mamá"))
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue("\"guardado\":false" in r.bodyAsText())
        assertTrue(filas().isEmpty())
        val contados = transaction {
            AtajosDescartados.selectAll().map { Triple(it[AtajosDescartados.userId], it[AtajosDescartados.motivo], it[AtajosDescartados.cuantos]) }
        }
        assertEquals(listOf(Triple(duena, "NO_ES_DEL_BANCO", 1)), contados)
        client.avisar(codigo, mensaje("¿Vienes a comer?"))
        assertEquals(2, transaction { AtajosDescartados.selectAll().single()[AtajosDescartados.cuantos] })
        // La app lo lee contado por motivo, solo lo propio.
        val leido = client.get("/api/captura/descartados") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
        assertEquals(HttpStatusCode.OK, leido.status)
        val resumen = json.decodeFromString(com.jvillada.movi.shared.model.ResumenDeAtajosDescartados.serializer(), leido.bodyAsText())
        assertEquals(listOf(com.jvillada.movi.shared.model.GrupoDeAtajosDescartados("NO_ES_DEL_BANCO", 2)), resumen.grupos)
        val deOtra = client.get("/api/captura/descartados") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(otra)}") }
        assertTrue(json.decodeFromString(com.jvillada.movi.shared.model.ResumenDeAtajosDescartados.serializer(), deOtra.bodyAsText()).grupos.isEmpty())
    }

    @Test
    fun `un codigo de remitente del banco pasa el filtro aunque el texto no nombre al banco`() = conApp {
        val codigo = client.crearCodigo()
        client.avisar(codigo, mensaje("Transferiste \$50.000 desde tu cuenta *1234", remitente = "891333"))
        assertEquals("Atajo · Bancolombia", filas().single().first)
    }

    @Test
    fun `el mismo SMS mandado dos veces queda una sola vez`() = conApp {
        val codigo = client.crearCodigo()
        assertTrue("\"guardado\":true" in client.avisar(codigo, mensaje(smsDeBancolombia)).bodyAsText())
        val segunda = client.avisar(codigo, mensaje(smsDeBancolombia))
        assertEquals(HttpStatusCode.Accepted, segunda.status)
        assertTrue("\"guardado\":false" in segunda.bodyAsText())
        assertEquals(1, filas().size)
    }

    @Test
    fun `una promocion del banco entra apartada, como por SMS`() = conApp {
        val codigo = client.crearCodigo()
        val promo = "Bancolombia te regala 20% dcto en Rappi. Aplican TyC. Ingresa a bancolombia.com"
        // Vuelta 113: sin remitente y sin monto no es un movimiento: no se guarda, solo se cuenta.
        assertTrue("\"guardado\":false" in client.avisar(codigo, mensaje(promo)).bodyAsText())
        assertTrue(filas().isEmpty())
        // Con el código del banco de remitente entra, y apartada.
        client.avisar(codigo, mensaje(promo, remitente = "85540"))
        val (_, _, estado) = filas().single()
        assertEquals("ignored", estado)
    }

    /**
     * **Vuelta 113 · un SMS personal nunca se guarda.** El atajo firmado no manda el remitente: sin él,
     * solo entra un texto que empieza por el banco y trae un monto, como firman los avisos. Con un
     * remitente que es un número personal no entra nada, aunque el texto sea idéntico a un aviso.
     */
    @Test
    fun `sin remitente entra el aviso del banco, y un SMS personal o de un celular solo se cuenta`() = conApp {
        val codigo = client.crearCodigo()
        assertTrue("\"guardado\":true" in client.avisar(codigo, mensaje(smsDeBancolombia)).bodyAsText())
        assertEquals(1, filas().size)

        val personal = client.avisar(codigo, mensaje("Hola, te pagué por Bancolombia lo del almuerzo, \$25.000"))
        assertEquals(HttpStatusCode.Accepted, personal.status)
        assertTrue("\"guardado\":false" in personal.bodyAsText())

        val otro = "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 09:12."
        for (celular in listOf("3001234567", "+57 300 123 4567", "573001234567")) {
            assertTrue("\"guardado\":false" in client.avisar(codigo, mensaje(otro, remitente = celular)).bodyAsText())
        }
        assertEquals(1, filas().size, "un SMS personal llegó a la bandeja")
        assertEquals(4, transaction { AtajosDescartados.selectAll().single()[AtajosDescartados.cuantos] })

        assertTrue("\"guardado\":true" in client.avisar(codigo, mensaje(otro, remitente = "85540")).bodyAsText())
        assertEquals(2, filas().size)
    }

    @Test
    fun `un pago con Apple Pay se lee con el monto en varios formatos`() = conApp {
        val codigo = client.crearCodigo()
        client.avisar(codigo, pago("Rappi", "\$27.500,00"))
        client.avisar(codigo, pago("Carulla", "COP 27,500.00"))
        client.avisar(codigo, pago("Exito", "COP 27.500"))
        val textos = filas().map { it.second }.sorted()
        assertEquals(
            listOf(
                "Apple Pay · Carulla · \$27.500 · Master Black",
                "Apple Pay · Exito · \$27.500 · Master Black",
                "Apple Pay · Rappi · \$27.500 · Master Black",
            ),
            textos,
        )
        assertTrue(filas().all { it.first == ORIGEN_DE_APPLE_PAY && it.third == SMS_STATE_PENDING })
        filas().forEach { (bank, texto, _) ->
            val leido = parseSms(texto, bank)
            assertNotNull(leido)
            assertEquals(27_500.0, leido.amount)
            assertEquals("COP", leido.currency)
        }
    }

    @Test
    fun `un monto que no se puede leer con seguridad deja el pago sin monto en la bandeja`() = conApp {
        val codigo = client.crearCodigo()
        client.avisar(codigo, pago("Amazon", "€ 12,00"))
        val (_, texto, estado) = filas().single()
        assertEquals("Apple Pay · Amazon · sin monto · Master Black", texto)
        assertEquals(SMS_STATE_PENDING, estado)
        assertNull(parseSms(texto, ORIGEN_DE_APPLE_PAY), "sin monto no se propone un movimiento")
    }

    @Test
    fun `Apple Pay y el SMS del mismo cobro se juntan en un solo pago`() = conApp {
        val codigo = client.crearCodigo()
        client.avisar(codigo, mensaje(smsDeBancolombia, remitente = "85540"))
        client.avisar(codigo, pago("Rappi", "27.500,00"))
        val bandeja = transaction { bandejaConLosPagos(duena, System.currentTimeMillis() + 60_000) }
        assertEquals(2, bandeja.size)
        val grupos = bandeja.map { it.grupoId }.toSet()
        assertEquals(1, grupos.size, "los dos avisos son el mismo pago: $bandeja")
        assertNotNull(grupos.single())
    }

    @Test
    fun `con el balde de fallidos lleno, un pedido con codigo valido entra`() = conApp {
        val codigo = client.crearCodigo()
        repeat(MAX_FALLIDOS_POR_MINUTO_POR_IP) {
            assertEquals(HttpStatusCode.Unauthorized, client.avisar(null, mensaje(smsDeBancolombia)).status)
        }
        // Pasado el tope, los que no traen un código válido reciben 429…
        assertEquals(HttpStatusCode.TooManyRequests, client.avisar(null, mensaje(smsDeBancolombia)).status)
        assertEquals(
            HttpStatusCode.TooManyRequests,
            client.avisar(com.jvillada.movi.server.compartir.TokenDeEnlace.nuevo(), mensaje(smsDeBancolombia)).status,
        )
        // …pero el atajo de verdad no comparte ese balde.
        val r = client.avisar(codigo, mensaje(smsDeBancolombia))
        assertEquals(HttpStatusCode.Accepted, r.status)
        assertTrue("\"guardado\":true" in r.bodyAsText())
    }

    @Test
    fun `el balde de fallidos es por la direccion que vio el borde, la de mas a la derecha`() = conApp {
        suspend fun sinCodigoDesde(xff: String) = client.post("/api/captura/atajo") {
            header("X-Forwarded-For", xff)
            contentType(ContentType.Application.Json)
            setBody(mensaje(smsDeBancolombia))
        }.status
        repeat(MAX_FALLIDOS_POR_MINUTO_POR_IP) { assertEquals(HttpStatusCode.Unauthorized, sinCodigoDesde("9.9.9.$it, 1.1.1.1")) }
        // Cambiar lo de la izquierda no saca a nadie del balde: cuenta el valor del borde.
        assertEquals(HttpStatusCode.TooManyRequests, sinCodigoDesde("8.8.8.8, 1.1.1.1"))
        assertEquals(HttpStatusCode.Unauthorized, sinCodigoDesde("1.1.1.1, 2.2.2.2"))
    }

    @Test
    fun `el limite por codigo corta con 429`() = conApp {
        val codigo = client.crearCodigo()
        repeat(MAX_AVISOS_POR_MINUTO_POR_CODIGO) { i ->
            assertEquals(HttpStatusCode.Accepted, client.avisar(codigo, mensaje("Bancolombia: Compraste \$${i + 1}.000,00 en X")).status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.avisar(codigo, mensaje(smsDeBancolombia)).status)
        // El 429 por código se cuenta, sin el texto.
        val contados = transaction { AtajosDescartados.selectAll().map { it[AtajosDescartados.motivo] to it[AtajosDescartados.cuantos] } }
        assertEquals(listOf("DEMASIADOS" to 1), contados)
    }

    @Test
    fun `un cuerpo de mas de 16 KB da 413 y no escribe nada`() = conApp {
        val codigo = client.crearCodigo()
        val grande = mensaje("Bancolombia " + "x".repeat(MAX_CUERPO_DEL_ATAJO))
        assertEquals(HttpStatusCode.PayloadTooLarge, client.avisar(codigo, grande).status)
        assertTrue(filas().isEmpty())
    }

    @Test
    fun `un cuerpo que no es JSON o sin tipo da 400`() = conApp {
        val codigo = client.crearCodigo()
        assertEquals(HttpStatusCode.BadRequest, client.avisar(codigo, "no es json").status)
        assertEquals(HttpStatusCode.BadRequest, client.avisar(codigo, """{"texto":"Bancolombia"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.avisar(codigo, """{"tipo":"otro"}""").status)
    }

    @Test
    fun `el codigo nunca aparece en un log`() = conApp {
        val raiz = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val registro = ListAppender<ILoggingEvent>().apply { start() }
        raiz.addAppender(registro)
        try {
            val codigo = client.crearCodigo()
            client.avisar(codigo, mensaje(smsDeBancolombia))
            client.delete("/api/captura/codigo") { header(HttpHeaders.Authorization, "Bearer ${jwtDe(duena)}") }
            client.avisar(codigo, mensaje(smsDeBancolombia))
            val lineas = registro.list.map { it.formattedMessage }
            assertTrue(lineas.any { "/api/captura/atajo" in it }, "CallLogging tiene que haber registrado el aviso: $lineas")
            assertFalse(lineas.any { codigo in it }, "el código apareció en el log: $lineas")
            assertFalse(lineas.any { "RAPPI COLOMBIA" in it }, "el texto del aviso apareció en el log: $lineas")
        } finally {
            raiz.detachAppender(registro)
        }
    }

    // ── Los atajos firmados ────────────────────────────────────────────────────────────────────

    @Test
    fun `el atajo de mensajes firmado se sirve y el de Apple Pay no, y la lectura del codigo lo dice`() = conApp {
        val r = client.get("/atajos/movi-mensaje.shortcut")
        assertEquals(HttpStatusCode.OK, r.status)
        val bytes = r.body<ByteArray>()
        assertEquals("AEA1", String(bytes.copyOfRange(0, 4)), "un atajo firmado empieza con la cabecera AEA1")
        assertTrue(r.headers[HttpHeaders.ContentDisposition].orEmpty().contains("movi-mensaje.shortcut"))
        assertEquals(HttpStatusCode.NotFound, client.get("/atajos/movi-apple-pay.shortcut").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/atajos/..%2Fapplication.conf").status)
        val leido = client.leerCodigo()
        assertTrue(leido.atajoDeMensajes)
        assertFalse(leido.atajoDeApplePay)
    }
}
