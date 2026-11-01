package com.jvillada.movi.server.documentos

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.ClavesDeExtractos
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.PdfsAbiertos
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.exportar.TABLAS_EXPORTADAS
import com.jvillada.movi.server.exportar.leerDatosDe
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.routes.PAPEL_CON_CLAVE
import com.jvillada.movi.shared.model.CLAVE_INCORRECTA
import com.jvillada.movi.shared.model.DEMASIADOS_INTENTOS_DE_CLAVE
import com.jvillada.movi.shared.model.EstadoDeLaClaveDeExtractos
import com.jvillada.movi.shared.model.INTENTOS_DE_CLAVE_POR_HORA
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.PDF_CON_CLAVE
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
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
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * # El PDF con clave se abre en Movi (vuelta 103)
 *
 * Todo con PDFs **generados acá** con PDFBox (nunca uno real): la clave de usuario es «1037585071».
 * Ninguna prueba llama a Anthropic: el lector es uno falso que dice «comprobante» y guarda el texto
 * que le llegó, para comprobar que lo leído es el contenido descifrado.
 *
 * Y en todas: **la clave no aparece** en ninguna respuesta, en el log ni en el export.
 */
class PdfConClaveTest {

    private val testSecret = "test-secret-for-pdf-con-clave-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val duenoId = "user-dueno-pdf-con-clave"
    private val otroId = "user-otro-pdf-con-clave"
    private val cedula = "1037585071"
    private val json = Json { ignoreUnknownKeys = true }

    /** Todo lo que el server contestó en la prueba: la clave no puede estar en ninguno. */
    private val respuestas = mutableListOf<String>()
    private lateinit var log: ListAppender<ILoggingEvent>
    private val raiz = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    private class LectorFalso : LectorDePapeles {
        var veces = 0
        var ultimoTexto: String? = null
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel {
            veces++
            ultimoTexto = (contenido as? ContenidoDelPapel.Texto)?.texto
            return QueDiceElPapel.Comprobante(
                ComprobanteLeido(monto = 50_000.0, tipo = TransactionType.EXPENSE, fecha = "2026-09-30", comercio = "Tienda"),
            )
        }
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>) = ClaudeStatementParser.Lectura.Ok(emptyList())
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>) =
            ClaudeStatementParser.Lectura.Ok(emptyList())
    }

    private lateinit var lector: LectorFalso

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-pdf-con-clave-jwtconfig-32")
        Cifrado.usarClaveParaPruebas(Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 3).toByte() }))
        lector = LectorFalso()
        LectorDePapeles.actual = lector
        log = ListAppender<ILoggingEvent>().also { it.start() }
        raiz.addAppender(log)
        Database.connect(
            url = "jdbc:h2:mem:pdf_con_clave_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, StatementImports, Events, VoidEvents, SmsMessages, Documents, KnownDestinations,
                LecturasDePapeles, ClavesDeExtractos, PdfsAbiertos,
            )
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@pdf.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @AfterTest
    fun limpiar() {
        LectorDePapeles.actual = LectorDePapelesConClaude
        Cifrado.usarLaDeLaConfiguracion()
        raiz.detachAppender(log)
        // La regla de oro: la clave (y sus formas) no salió en ninguna respuesta ni en el log.
        val formas = listOf(cedula, "1.037.585.071", "otra-clave-9")
        val logs = log.list.joinToString("\n") { e -> e.formattedMessage + (e.throwableProxy?.message ?: "") }
        formas.forEach { f ->
            assertTrue(respuestas.none { it.contains(f) }, "la clave apareció en una respuesta")
            assertFalse(logs.contains(f), "la clave apareció en el log")
        }
    }

    // ── Sin clave, clave mala, clave buena ──────────────────────────────────────

    @Test
    fun `sin clave dice PDF_CON_CLAVE sin llamar al lector`() = testApplication {
        wireApp()
        val doc = subir(pdf(usuario = cedula))
        val res = leer(doc)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PDF_CON_CLAVE, res.cuerpo())
        assertEquals(0, lector.veces)
        // Una app vieja (sin `conClave=1`) recibe la frase de siempre, que ya no manda a imprimir.
        val vieja = leer(doc, conClave = false)
        assertEquals(PAPEL_CON_CLAVE, vieja.cuerpo())
        assertFalse(PAPEL_CON_CLAVE.contains("imprím"))
    }

    @Test
    fun `una clave mala dice CLAVE_INCORRECTA y no guarda nada`() = testApplication {
        wireApp()
        val original = pdf(usuario = cedula)
        val doc = subir(original)
        val res = leer(doc, clave = "otra-clave-9", recordar = true)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(CLAVE_INCORRECTA, res.cuerpo())
        assertTrue(contenidoEnClaro(doc).contentEquals(original), "el documento sigue siendo el original")
        assertEquals(0, filas(PdfsAbiertos) + filas(ClavesDeExtractos), "una clave mala no deja rastro")
        assertEquals(0, lector.veces)
    }

    @Test
    fun `con la clave buena se lee y queda descifrado y cifrado en reposo`() = testApplication {
        wireApp()
        val doc = subir(pdf(usuario = cedula, texto = "Pago en Tienda por 50.000"))
        val res = leer(doc, clave = cedula)
        assertEquals(HttpStatusCode.OK, res.status, res.cuerpo())
        val lectura = json.decodeFromString(LecturaDelPapel.serializer(), res.cuerpo())
        assertEquals(QueEsElPapel.COMPROBANTE, lectura.que)
        assertTrue(lector.ultimoTexto.orEmpty().contains("Pago en Tienda"), "lo leído es el contenido descifrado")

        val guardado = transaction { Documents.selectAll().single() }
        // Cifrado en reposo con la clave de Movi…
        assertTrue(Cifrado.estaCifrado(guardado[Documents.content]))
        // …y adentro, el PDF ya sin ninguna protección: se abre sin clave.
        val claro = Cifrado.descifrarBytes(guardado[Documents.content])
        Loader.loadPDF(claro).use { d ->
            assertFalse(d.isEncrypted)
            assertTrue(PDFTextStripper().getText(d).contains("Pago en Tienda"))
        }
        assertEquals(claro.size.toLong(), guardado[Documents.sizeBytes], "size_bytes es el del contenido en claro")
        // Sin «Recordarla», no se guarda ninguna clave.
        assertEquals(0, filas(ClavesDeExtractos))

        // Un reintento ya no pide clave, y no relee con el lector (la lectura quedó por la huella).
        val otraVez = leer(doc)
        assertEquals(HttpStatusCode.OK, otraVez.status)
        assertEquals(1, lector.veces)
    }

    @Test
    fun `el reintento no vuelve a subir y el mismo archivo con clave no se duplica`() = testApplication {
        wireApp()
        val original = pdf(usuario = cedula)
        val doc = subir(original, reusar = true)
        assertEquals(PDF_CON_CLAVE, leer(doc).cuerpo())
        assertEquals(HttpStatusCode.OK, leer(doc, clave = cedula).status)
        assertEquals(1, filas(Documents), "el reintento es sobre el documento guardado")

        // Compartir otra vez el MISMO archivo con clave: devuelve el documento ya abierto.
        val segunda = subir(original, reusar = true)
        assertEquals(doc, segunda)
        assertEquals(1, filas(Documents))
        assertEquals(HttpStatusCode.OK, leer(segunda).status, "ya abierto, no pide clave")
    }

    @Test
    fun `el tope de intentos por documento`() = testApplication {
        wireApp()
        val doc = subir(pdf(usuario = cedula))
        repeat(INTENTOS_DE_CLAVE_POR_HORA) {
            assertEquals(CLAVE_INCORRECTA, leer(doc, clave = "otra-clave-9").cuerpo())
        }
        val pasado = leer(doc, clave = cedula)
        assertEquals(HttpStatusCode.TooManyRequests, pasado.status)
        assertEquals(DEMASIADOS_INTENTOS_DE_CLAVE, pasado.cuerpo())
        assertEquals(0, filas(PdfsAbiertos), "pasado el tope ni la buena se prueba")
        // Otro documento no comparte el tope.
        val otro = subir(pdf(usuario = cedula, texto = "Otro papel"))
        assertEquals(HttpStatusCode.OK, leer(otro, clave = cedula).status)
    }

    @Test
    fun `un PDF solo con clave de propietario se lee sin pedir nada`() = testApplication {
        wireApp()
        val original = pdf(usuario = "", texto = "Pago con permisos")
        assertTrue(PdfConClave.puedeEstarCifrado(original))
        assertFalse(PdfConClave.pideClave(original))
        val doc = subir(original)
        val res = leer(doc)
        assertEquals(HttpStatusCode.OK, res.status, res.cuerpo())
        assertTrue(lector.ultimoTexto.orEmpty().contains("Pago con permisos"))
        assertTrue(contenidoEnClaro(doc).contentEquals(original), "no se reescribe: se lee como siempre")
    }

    @Test
    fun `un cuerpo que no se entiende es un 400 que no repite lo que llego`() = testApplication {
        wireApp()
        val doc = subir(pdf(usuario = cedula))
        val res = client.post("/api/documents/$doc/leer?conClave=1") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"clave": $cedula, "recordar": tal}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        respuestas += res.bodyAsText()
    }

    // ── Recordar ────────────────────────────────────────────────────────────────

    @Test
    fun `recordarla la guarda cifrada y el siguiente PDF se abre solo`() = testApplication {
        wireApp()
        assertEquals(EstadoDeLaClaveDeExtractos(guardada = false, sePuedeRecordar = true), estado())
        val uno = subir(pdf(usuario = cedula, texto = "Extracto de julio"))
        assertEquals(HttpStatusCode.OK, leer(uno, clave = cedula, recordar = true).status)

        val fila = transaction { ClavesDeExtractos.selectAll().single()[ClavesDeExtractos.clave] }
        assertTrue(Cifrado.estaCifrado(fila), "la clave se guarda cifrada")
        assertFalse(fila.contains(cedula))
        assertEquals(EstadoDeLaClaveDeExtractos(guardada = true, sePuedeRecordar = true), estado())

        val dos = subir(pdf(usuario = cedula, texto = "Extracto de agosto"))
        val res = leer(dos)
        assertEquals(HttpStatusCode.OK, res.status, "con la clave recordada se abre sin preguntar")
        assertTrue(lector.ultimoTexto.orEmpty().contains("Extracto de agosto"))
        // La clave recordada es de su dueño: a otro usuario no le abre nada.
        val ajeno = subir(pdf(usuario = cedula, texto = "Del otro"), uid = otroId)
        assertEquals(PDF_CON_CLAVE, leer(ajeno, uid = otroId).cuerpo())
    }

    @Test
    fun `las formas de la clave recordada`() = testApplication {
        wireApp()
        // Escrita con puntos, abre el PDF de la cédula sin puntos (y queda recordada así).
        val conPuntos = subir(pdf(usuario = cedula, texto = "Uno"))
        assertEquals(HttpStatusCode.OK, leer(conPuntos, clave = "1.037.585.071", recordar = true).status)
        // Un PDF cuya clave son los últimos 4, y otro con los últimos 6: se abren solos.
        val cuatro = subir(pdf(usuario = "5071", texto = "Dos"))
        assertEquals(HttpStatusCode.OK, leer(cuatro).status)
        val seis = subir(pdf(usuario = "585071", texto = "Tres"))
        assertEquals(HttpStatusCode.OK, leer(seis).status)
        assertEquals(listOf("1.037.585.071", "1037585071", "5071", "585071"), PdfConClave.variantes("1.037.585.071"))
    }

    @Test
    fun `una clave recordada que no sirve pide la clave`() = testApplication {
        wireApp()
        val uno = subir(pdf(usuario = cedula, texto = "Uno"))
        assertEquals(HttpStatusCode.OK, leer(uno, clave = cedula, recordar = true).status)
        val otro = subir(pdf(usuario = "otra-clave-9", texto = "Dos"))
        assertEquals(PDF_CON_CLAVE, leer(otro).cuerpo())
    }

    @Test
    fun `sin nada recordado no se prueba nada a ciegas`() = testApplication {
        wireApp()
        // La clave «1234» abriría con cualquier cosa que termine así, pero no hay nada recordado.
        val doc = subir(pdf(usuario = "5071"))
        assertEquals(PDF_CON_CLAVE, leer(doc).cuerpo())
        assertEquals(0, filas(PdfsAbiertos))
    }

    @Test
    fun `olvidarla la borra`() = testApplication {
        wireApp()
        val uno = subir(pdf(usuario = cedula))
        assertEquals(HttpStatusCode.OK, leer(uno, clave = cedula, recordar = true).status)
        assertEquals(1, filas(ClavesDeExtractos))
        val res = client.delete("/api/extractos/clave") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.NoContent, res.status)
        assertEquals(0, filas(ClavesDeExtractos))
        assertEquals(EstadoDeLaClaveDeExtractos(guardada = false, sePuedeRecordar = true), estado())
        val dos = subir(pdf(usuario = cedula, texto = "Dos"))
        assertEquals(PDF_CON_CLAVE, leer(dos).cuerpo(), "olvidada, se vuelve a pedir")
    }

    @Test
    fun `la clave no sale en el export`() = testApplication {
        wireApp()
        val uno = subir(pdf(usuario = cedula))
        assertEquals(HttpStatusCode.OK, leer(uno, clave = cedula, recordar = true).status)
        assertTrue(TABLAS_EXPORTADAS.none { it.tabla == ClavesDeExtractos || it.tabla == PdfsAbiertos })
        val exportado = transaction {
            leerDatosDe(duenoId, TABLAS_EXPORTADAS.filter { it.tabla == Users || it.tabla == Documents })
        }.toString()
        assertFalse(exportado.contains(cedula))
    }

    @Test
    fun `sin la clave de Movi no se ofrece recordarla ni se guarda`() = testApplication {
        wireApp()
        Cifrado.usarClaveParaPruebas(null)
        assertEquals(EstadoDeLaClaveDeExtractos(guardada = false, sePuedeRecordar = false), estado())
        val doc = subir(pdf(usuario = cedula))
        assertEquals(HttpStatusCode.OK, leer(doc, clave = cedula, recordar = true).status)
        assertEquals(0, filas(ClavesDeExtractos), "sin DOCUMENTS_ENCRYPTION_KEY no se guardan claves, jamás")
    }

    @Test
    fun `un documento ajeno ni se intenta`() = testApplication {
        wireApp()
        val doc = subir(pdf(usuario = cedula))
        val res = leer(doc, uid = otroId, clave = cedula)
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertNotEquals(0, filas(Documents))
        assertEquals(0, filas(PdfsAbiertos))
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────

    private fun token(uid: String = duenoId): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@pdf.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
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

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    private suspend fun HttpResponse.cuerpo(): String = bodyAsText().also { respuestas += it }

    private suspend fun ApplicationTestBuilder.subir(contenido: ByteArray, reusar: Boolean = false, uid: String = duenoId): String {
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tipo", "OTRO")
                        if (reusar) append("reusar", "true")
                        append("file", contenido, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"CuentaNu_2026-07.pdf\"")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    },
                ),
            )
        }
        val cuerpo = res.cuerpo()
        assertTrue(res.status == HttpStatusCode.Created || res.status == HttpStatusCode.OK, cuerpo)
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]
    }

    /** `POST /leer`, con la clave en el CUERPO (nunca en la URL), como la manda la app. */
    private suspend fun ApplicationTestBuilder.leer(
        doc: String,
        clave: String? = null,
        recordar: Boolean = false,
        conClave: Boolean = true,
        uid: String = duenoId,
    ): HttpResponse = client.post("/api/documents/$doc/leer" + if (conClave) "?conClave=1" else "") {
        header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        if (clave != null) {
            contentType(ContentType.Application.Json)
            setBody("""{"clave":"$clave","recordar":$recordar}""")
        }
    }.also { respuestas += it.bodyAsText() }

    private suspend fun ApplicationTestBuilder.estado(): EstadoDeLaClaveDeExtractos {
        val res = client.get("/api/extractos/clave") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        return json.decodeFromString(EstadoDeLaClaveDeExtractos.serializer(), res.cuerpo())
    }

    private fun filas(tabla: org.jetbrains.exposed.sql.Table): Int = transaction { tabla.selectAll().count().toInt() }

    private fun contenidoEnClaro(doc: String): ByteArray = transaction {
        Cifrado.descifrarBytes(Documents.selectAll().where { Documents.id eq doc }.single()[Documents.content])
    }

    /** Un PDF de una página con [texto]. [usuario] vacío = solo clave de propietario. */
    private fun pdf(usuario: String, texto: String = "Movimiento de prueba"): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val pagina = PDPage()
            doc.addPage(pagina)
            PDPageContentStream(doc, pagina).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText(texto)
                cs.endText()
            }
            val politica = StandardProtectionPolicy("propietario-del-banco", usuario, AccessPermission().apply { setCanModify(false) })
            politica.encryptionKeyLength = 128
            doc.protect(politica)
            doc.save(out)
        }
        return out.toByteArray()
    }
}
