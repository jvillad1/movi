package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.idDeComprobante
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
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
 * # «Compartir con Movi»: `POST /api/documents/{id}/leer` (Ola 2)
 *
 * **Ninguna prueba llama a la API de Anthropic.** El lector es [LectorDePapeles.actual], y cada
 * prueba pone uno falso que cuenta cuántas veces lo llamaron: así se prueba también la regla del
 * costo —el mismo archivo no se manda dos veces—. `@AfterTest` devuelve el de verdad.
 *
 * Mismo harness que `DocumentRoutesTest`: H2 en memoria, JWT de prueba y `configureRouting()`.
 */
class PapelesRoutesTest {

    private val testSecret = "test-secret-for-papeles-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-papeles"
    private val otroId = "user-otro-papeles"
    private val ahorros = "acc-ahorros-papeles"

    private val json = Json { ignoreUnknownKeys = true }

    /** Un lector falso que dice lo que se le pida y cuenta las llamadas. */
    private class LectorFalso(
        var queDice: QueDiceElPapel = QueDiceElPapel.SinLlave,
        var filasDelExtracto: List<ParsedTransaction> = emptyList(),
    ) : LectorDePapeles {
        var vecesQueEs = 0
        var vecesExtracto = 0
        var ultimoContenido: ContenidoDelPapel? = null
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel {
            vecesQueEs++
            ultimoContenido = contenido
            return queDice
        }
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura {
            vecesExtracto++
            return ClaudeStatementParser.Lectura.Ok(filasDelExtracto)
        }
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura {
            vecesExtracto++
            return ClaudeStatementParser.Lectura.Ok(filasDelExtracto)
        }
    }

    private lateinit var lector: LectorFalso

    private val ayer: LocalDate = LocalDate.now(ZoneId.of("America/Bogota")).minusDays(1)

    private val coomeva = ComprobanteLeido(
        monto = 138_600.0,
        tipo = TransactionType.EXPENSE,
        fecha = ayer.toString(),
        hora = "14:05",
        comercio = "Coomeva Medicina Prepagada",
        categoria = "Salud",
        banco = "Bancolombia",
        cuentaPropia = "8133",
        concepto = "Pago PSE",
    )

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-papeles-routes-jwtconfig-32")
        lector = LectorFalso()
        LectorDePapeles.actual = lector
        Database.connect(
            url = "jdbc:h2:mem:papeles_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(LecturasDePapeles, Documents, SmsMessages, VoidEvents, Events, StatementImports, Accounts, Users, KnownDestinations)
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, SmsMessages, Documents, KnownDestinations, LecturasDePapeles)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@papeles.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
            Accounts.insert {
                it[id] = ahorros
                it[userId] = duenoId
                it[name] = "Bancolombia Ahorros 8133"
                it[type] = "SAVINGS"
                it[currency] = "COP"
            }
        }
    }

    @AfterTest
    fun devolverElLector() {
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@papeles.test")
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

    private suspend fun ApplicationTestBuilder.subir(
        uid: String,
        nombre: String = "transferencia.png",
        contenido: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
        mime: String = "image/png",
        reusar: Boolean = false,
    ): String {
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tipo", "OTRO")
                        if (reusar) append("reusar", "true")
                        append("file", contenido, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                            append(HttpHeaders.ContentType, mime)
                        })
                    },
                ),
            )
        }
        val cuerpo = res.bodyAsText()
        assertTrue(res.status == HttpStatusCode.Created || res.status == HttpStatusCode.OK, cuerpo)
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]
    }

    private suspend fun ApplicationTestBuilder.leer(uid: String, docId: String, anotarAunque: Boolean = false) =
        client.post("/api/documents/$docId/leer" + if (anotarAunque) "?anotarAunque=true" else "") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        }

    private suspend fun ApplicationTestBuilder.lecturaDe(uid: String, docId: String, anotarAunque: Boolean = false): LecturaDelPapel {
        val res = leer(uid, docId, anotarAunque)
        val cuerpo = res.bodyAsText()
        assertEquals(HttpStatusCode.OK, res.status, cuerpo)
        return json.decodeFromString(LecturaDelPapel.serializer(), cuerpo)
    }

    private fun filasDeLaBandeja(uid: String) = transaction {
        SmsMessages.selectAll().where { SmsMessages.userId eq uid }.map { it[SmsMessages.id] to it[SmsMessages.text] }
    }

    // ── Un comprobante ─────────────────────────────────────────────────────────

    @Test
    fun `un comprobante deja una propuesta en Por revisar con lo que se leyo`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        val doc = subir(duenoId)

        val lectura = lecturaDe(duenoId, doc)

        assertEquals(QueEsElPapel.COMPROBANTE, lectura.que)
        assertEquals(idDeComprobante(doc), lectura.porRevisarId)
        assertEquals(138_600.0, lectura.leido?.amount)
        assertEquals("Coomeva Medicina Prepagada", lectura.leido?.merchant)
        assertEquals("Salud", lectura.leido?.category)
        assertEquals("$ayer 14:05", lectura.cuando)
        // La imagen viajó como imagen, al lector barato.
        assertTrue(lector.ultimoContenido is ContenidoDelPapel.Imagen)

        val filas = filasDeLaBandeja(duenoId)
        assertEquals(1, filas.size)
        val (id, texto) = filas.single()
        assertEquals(idDeComprobante(doc), id)
        assertTrue("Pagaste $138.600 a Coomeva Medicina Prepagada desde tu cuenta *8133" in texto, texto)

        // Y la bandeja de siempre la lee con lo que leyó Claude, no con el parser de SMS.
        val parse = client.get("/api/sms/$id/parse") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, parse.status)
        val propuesta = json.decodeFromString(ParsedSms.serializer(), parse.bodyAsText())
        assertEquals("Coomeva Medicina Prepagada", propuesta.merchant)
        assertEquals("Salud", propuesta.category)
        assertEquals(TransactionType.EXPENSE, propuesta.type)

        val bandeja = client.get("/api/sms") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }.bodyAsText()
        val mensajes = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SmsMessage.serializer()), bandeja)
        assertEquals("Comprobante · transferencia.png", mensajes.single().bank)
    }

    @Test
    fun `el mismo archivo no se manda dos veces a Claude`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        val doc = subir(duenoId)

        lecturaDe(duenoId, doc)
        val otraVez = lecturaDe(duenoId, doc)
        assertTrue(otraVez.yaEstabaEnLaBandeja)

        // Otro documento con los MISMOS bytes (compartido de nuevo, con otro nombre): tampoco.
        val copia = subir(duenoId, nombre = "copia.png")
        val deLaCopia = lecturaDe(duenoId, copia)
        assertEquals(idDeComprobante(doc), deLaCopia.porRevisarId)

        assertEquals(1, lector.vecesQueEs, "el lector se llamó más de una vez para el mismo archivo")
        assertEquals(1, filasDeLaBandeja(duenoId).size, "compartir dos veces no deja dos propuestas")
    }

    @Test
    fun `subir con reusar no duplica el documento`() = testApplication {
        wireApp()
        val uno = subir(duenoId, reusar = true)
        val dos = subir(duenoId, reusar = true)
        assertEquals(uno, dos)
        // El mismo nombre con otro contenido sí es otro papel.
        val tres = subir(duenoId, contenido = byteArrayOf(9, 9, 9, 9, 9), reusar = true)
        assertTrue(tres != uno)
    }

    // ── Ya lo tienes anotado ───────────────────────────────────────────────────

    @Test
    fun `si el movimiento ya esta anotado lo dice y no duplica`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        val momento = ayer.atTime(13, 0).atZone(ZoneId.of("America/Bogota")).toInstant().toEpochMilli()
        transaction {
            Events.insert {
                it[id] = "ev-coomeva"
                it[userId] = duenoId
                it[accountId] = ahorros
                it[type] = "EXPENSE"
                it[amount] = 138_600
                it[currency] = "COP"
                it[category] = "Salud"
                it[description] = "Coomeva"
                it[timestamp] = momento
            }
        }
        val doc = subir(duenoId)

        val lectura = lecturaDe(duenoId, doc)
        assertNull(lectura.porRevisarId)
        assertEquals(listOf("ev-coomeva"), lectura.yaAnotado.map { it.id })
        assertTrue(filasDeLaBandeja(duenoId).isEmpty(), "no se crea la propuesta de algo ya anotado")

        // «Anotarlo de todas formas»: la crea, y sin volver a leer el archivo.
        val igual = lecturaDe(duenoId, doc, anotarAunque = true)
        assertEquals(idDeComprobante(doc), igual.porRevisarId)
        assertEquals(1, filasDeLaBandeja(duenoId).size)
        assertEquals(1, lector.vecesQueEs)
    }

    // ── Aislamiento ────────────────────────────────────────────────────────────

    @Test
    fun `el documento de otro no se lee`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        val doc = subir(duenoId)

        assertEquals(HttpStatusCode.NotFound, leer(otroId, doc).status)
        assertEquals(0, lector.vecesQueEs)

        lecturaDe(duenoId, doc)
        val ajena = client.get("/api/sms/${idDeComprobante(doc)}/parse") { header(HttpHeaders.Authorization, "Bearer ${token(otroId)}") }
        assertEquals(HttpStatusCode.NotFound, ajena.status)
        assertTrue(filasDeLaBandeja(otroId).isEmpty())
    }

    @Test
    fun `la lectura guardada de uno no le sirve a otro`() = testApplication {
        // La huella es por dueño: que otro comparta la misma captura no le devuelve la propuesta
        // ni la lectura del primero.
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        lecturaDe(duenoId, subir(duenoId))
        val delOtro = subir(otroId)
        val suya = lecturaDe(otroId, delOtro)

        assertEquals(idDeComprobante(delOtro), suya.porRevisarId)
        assertEquals(2, lector.vecesQueEs)
        assertEquals(1, filasDeLaBandeja(otroId).size)
        assertEquals(1, filasDeLaBandeja(duenoId).size)
    }

    @Test
    fun `el telefono no puede subir un comprobante inventado`() = testApplication {
        wireApp()
        val res = client.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody("""[{"id":"cmp_doc_falso","time":"2026-09-30 10:00","bank":"Comprobante · x","text":"Pagaste $1.000","state":"pending","det":""}]""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(filasDeLaBandeja(duenoId).isEmpty())
    }

    // ── Confirmar enlaza el papel con el movimiento ────────────────────────────

    @Test
    fun `confirmar con el movimiento cuelga el documento de su cuenta`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Comprobante(coomeva)
        val doc = subir(duenoId)
        val id = lecturaDe(duenoId, doc).porRevisarId!!
        transaction {
            Events.insert {
                it[Events.id] = "ev-confirmado"
                it[userId] = duenoId
                it[accountId] = ahorros
                it[type] = "EXPENSE"
                it[amount] = 138_600
                it[currency] = "COP"
                it[category] = "Salud"
                it[description] = "Coomeva"
                it[timestamp] = System.currentTimeMillis()
            }
        }

        val res = client.post("/api/sms/$id/confirm?eventoId=ev-confirmado") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        transaction {
            assertEquals(SMS_STATE_CONFIRMED, SmsMessages.selectAll().where { SmsMessages.id eq id }.single()[SmsMessages.state])
            assertEquals(ahorros, Documents.selectAll().where { Documents.id eq doc }.single()[Documents.accountId])
            assertEquals(
                "ev-confirmado",
                LecturasDePapeles.selectAll().where { (LecturasDePapeles.userId eq duenoId) and (LecturasDePapeles.porRevisarId eq id) }
                    .single()[LecturasDePapeles.eventoId],
            )
        }
    }

    // ── Cuando no se puede leer: el archivo queda ──────────────────────────────

    @Test
    fun `sin la clave de Anthropic lo dice claro y el archivo queda guardado`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.SinLlave
        val doc = subir(duenoId)

        val res = leer(duenoId, doc)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PAPEL_SIN_LLAVE, res.bodyAsText())
        transaction { assertEquals(1L, Documents.selectAll().where { Documents.id eq doc }.count()) }
        // Y no queda una lectura guardada que tape un reintento con la clave puesta.
        transaction { assertEquals(0L, LecturasDePapeles.selectAll().count()) }
    }

    @Test
    fun `un PDF con contrasena se explica sin llamar al lector`() = testApplication {
        wireApp()
        val doc = subir(duenoId, nombre = "extracto.pdf", contenido = pdfConClave(), mime = "application/pdf")

        val res = leer(duenoId, doc)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PAPEL_CON_CLAVE, res.bodyAsText())
        assertEquals(0, lector.vecesQueEs + lector.vecesExtracto)
    }

    @Test
    fun `un archivo que no es foto ni PDF se explica`() = testApplication {
        wireApp()
        val doc = subir(duenoId, nombre = "contrato.docx", mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        val res = leer(duenoId, doc)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PAPEL_DE_OTRO_FORMATO, res.bodyAsText())
    }

    // ── Un extracto va por el importador de siempre ────────────────────────────

    @Test
    fun `un PDF con forma de extracto va al importador sin clasificarlo y se reusa`() = testApplication {
        wireApp()
        lector.filasDelExtracto = listOf(
            ParsedTransaction("t1", "2026-09-01", "Exito", 45_000, "COP", TransactionType.EXPENSE, "Mercado", "Compra", "01/09 EXITO 45.000"),
            ParsedTransaction("t2", "2026-09-02", "Uber", 18_500, "COP", TransactionType.EXPENSE, "Transporte", "Viaje", "02/09 UBER 18.500"),
        )
        val pdf = pdfConLineas(
            listOf(
                "Bancolombia - Extracto de ahorros",
                "01/09/2026 COMPRA EXITO 45.000",
                "02/09/2026 UBER 18.500",
                "03/09/2026 RAPPI 32.900",
                "04/09/2026 NOMINA 4.500.000",
                "05/09/2026 PAGO PSE 120.000",
            ),
        )
        val doc = subir(duenoId, nombre = "Extracto septiembre.pdf", contenido = pdf, mime = "application/pdf")

        val lectura = lecturaDe(duenoId, doc)
        assertEquals(QueEsElPapel.EXTRACTO, lectura.que)
        val extracto = assertNotNull(lectura.extracto)
        assertEquals(2, extracto.newTransactions.size)
        assertEquals(doc, extracto.documentoId, "el extracto se archiva en el mismo documento, no en uno nuevo")
        assertEquals(0, lector.vecesQueEs, "con forma de extracto no hace falta preguntar qué es")

        lecturaDe(duenoId, doc)
        assertEquals(1, lector.vecesExtracto, "la segunda vez se concilia con la lectura guardada")
        assertFalse(filasDeLaBandeja(duenoId).any(), "un extracto no deja propuestas sueltas en la bandeja")
    }

    @Test
    fun `una imagen que el lector llama extracto se lee con el importador`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Extracto
        lector.filasDelExtracto = listOf(
            ParsedTransaction("t1", "2026-09-01", "Exito", 45_000, "COP", TransactionType.EXPENSE, "Mercado", "Compra", ""),
        )
        val lectura = lecturaDe(duenoId, subir(duenoId, nombre = "movimientos.jpg", mime = "image/jpeg"))
        assertEquals(QueEsElPapel.EXTRACTO, lectura.que)
        assertEquals(1, lectura.extracto?.newTransactions?.size)
        assertEquals(1, lector.vecesQueEs)
        assertEquals(1, lector.vecesExtracto)
    }

    @Test
    fun `un extracto sin movimientos no abre la revision y no queda leido`() = testApplication {
        wireApp()
        // El extracto del crédito del vehículo: Movi lo toma por extracto y la lectura vuelve vacía.
        lector.filasDelExtracto = emptyList()
        val pdf = pdfConLineas(
            listOf(
                "Extracto credito vehiculo",
                "01/09/2026 CUOTA A PAGAR 1.250.000",
                "02/09/2026 SALDO CAPITAL 38.500.000",
                "03/09/2026 INTERESES 410.000",
                "04/09/2026 SEGURO 95.000",
            ),
        )
        val doc = subir(duenoId, nombre = "Vehiculos.pdf", contenido = pdf, mime = "application/pdf")

        val res = leer(duenoId, doc)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PAPEL_SIN_MOVIMIENTO, res.bodyAsText())
        assertEquals(1, lector.vecesExtracto)
        transaction {
            assertEquals(1L, Documents.selectAll().where { Documents.id eq doc }.count(), "el archivo queda en Documentos")
            assertEquals(0L, LecturasDePapeles.selectAll().count(), "una lectura vacía no se guarda")
            assertEquals(0L, StatementImports.selectAll().count())
        }
    }

    @Test
    fun `una imagen que el clasificador llama extracto pero no trae filas tampoco abre la revision`() = testApplication {
        wireApp()
        lector.queDice = QueDiceElPapel.Extracto
        lector.filasDelExtracto = emptyList()
        val res = leer(duenoId, subir(duenoId, nombre = "portal.png"))
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PAPEL_SIN_MOVIMIENTO, res.bodyAsText())
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    private fun pdfConLineas(lineas: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val pagina = PDPage()
            doc.addPage(pagina)
            PDPageContentStream(doc, pagina).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                cs.newLineAtOffset(50f, 700f)
                lineas.forEach { linea ->
                    cs.showText(linea)
                    cs.newLineAtOffset(0f, -16f)
                }
                cs.endText()
            }
            doc.save(out)
        }
        return out.toByteArray()
    }

    private fun pdfConClave(): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            val politica = StandardProtectionPolicy("dueno", "clave-del-banco", AccessPermission())
            politica.encryptionKeyLength = 128
            doc.protect(politica)
            doc.save(out)
        }
        return out.toByteArray()
    }
}
