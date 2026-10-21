package com.jvillada.movi.server.documentos

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.Migrations
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.exportar.TablaExportada
import com.jvillada.movi.server.exportar.leerDatosDe
import com.jvillada.movi.server.exportar.zipDeLosDatos
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.routes.guardarDocumento
import com.jvillada.movi.server.routes.huellaDelPapel
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.idDeComprobante
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.Date
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * # Los papeles se guardan cifrados (`documents.content`, `lecturas_de_papeles.datos`)
 *
 * Todo pasa por [Cifrado]: estas pruebas fijan el formato, la compatibilidad con las filas viejas en
 * claro, la migración del arranque, la clave equivocada, la deduplicación (que tiene que mirar el
 * contenido EN CLARO: el IV aleatorio hace que dos cifrados del mismo archivo nunca coincidan) y el
 * aislamiento entre usuarios.
 *
 * Ninguna toca la red: el lector de papeles es uno falso, igual que en `PapelesRoutesTest`. Cada
 * prueba fija su clave con [Cifrado.usarClaveParaPruebas] y `@AfterTest` la devuelve.
 */
class DocumentosCifradosTest {

    private val testSecret = "test-secret-for-documentos-cifrados-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-cifrado"
    private val otroId = "user-otro-cifrado"

    private val claveA = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val claveB = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 3).toByte() })

    /** Un «PDF» con texto reconocible: si aparece en la base, no se cifró. */
    private val secreto = "%PDF-1.4 EXTRACTO SECRETO saldo 12.345.678 cuenta 8133".encodeToByteArray()

    private val json = Json { ignoreUnknownKeys = true }

    private class LectorFalso(val queDice: QueDiceElPapel) : LectorDePapeles {
        var veces = 0
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel {
            veces++
            return queDice
        }
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>) =
            ClaudeStatementParser.Lectura.Ok(emptyList())
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>) =
            ClaudeStatementParser.Lectura.Ok(emptyList())
    }

    private val coomeva = ComprobanteLeido(
        monto = 138_600.0,
        tipo = TransactionType.EXPENSE,
        fecha = "2026-09-30",
        hora = "14:05",
        comercio = "Coomeva Medicina Prepagada",
        categoria = "Salud",
        banco = "Bancolombia",
        cuentaPropia = "8133",
        concepto = "Pago PSE",
    )

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-documentos-cifrados-jwtconfig-32")
        Cifrado.usarClaveParaPruebas(null)
        Database.connect(
            url = "jdbc:h2:mem:documentos_cifrados_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(LecturasDePapeles, Documents, SmsMessages, VoidEvents, Events, StatementImports, Accounts, Users, KnownDestinations)
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, SmsMessages, Documents, KnownDestinations, LecturasDePapeles)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@cifrado.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        Cifrado.usarLaDeLaConfiguracion()
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    // ── El helper ──────────────────────────────────────────────────────────────

    @Test
    fun `cifrar y descifrar devuelve lo mismo y lo guardado no trae el texto en claro`() {
        Cifrado.usarClaveParaPruebas(claveA)
        val guardado = Cifrado.cifrarBytes(secreto)
        assertTrue(Cifrado.estaCifrado(guardado))
        assertEquals("MOVI1", guardado.copyOfRange(0, 5).decodeToString())
        assertEquals(secreto.size + Cifrado.BYTES_DE_MAS, guardado.size)
        assertFalse("EXTRACTO SECRETO" in guardado.decodeToString())
        assertContentEquals(secreto, Cifrado.descifrarBytes(guardado))

        val texto = """{"comercio":"Coomeva Medicina Prepagada","monto":138600}"""
        val textoGuardado = Cifrado.cifrarTexto(texto)
        assertTrue(textoGuardado.startsWith("MOVI1:"))
        assertFalse("Coomeva" in textoGuardado)
        assertEquals(texto, Cifrado.descifrarTexto(textoGuardado))
    }

    @Test
    fun `el mismo archivo cifrado dos veces da bytes distintos`() {
        Cifrado.usarClaveParaPruebas(claveA)
        assertFalse(Cifrado.cifrarBytes(secreto).contentEquals(Cifrado.cifrarBytes(secreto)), "el IV tiene que ser aleatorio")
    }

    @Test
    fun `sin clave se guarda en claro y se lee igual`() {
        assertFalse(Cifrado.hayClave())
        assertContentEquals(secreto, Cifrado.cifrarBytes(secreto))
        assertContentEquals(secreto, Cifrado.descifrarBytes(secreto))
        assertEquals("{}", Cifrado.cifrarTexto("{}"))
        assertEquals("{}", Cifrado.descifrarTexto("{}"))
    }

    @Test
    fun `una fila vieja en claro se lee con la clave puesta`() {
        Cifrado.usarClaveParaPruebas(claveA)
        assertContentEquals(secreto, Cifrado.descifrarBytes(secreto))
        assertEquals("""{"a":1}""", Cifrado.descifrarTexto("""{"a":1}"""))
    }

    @Test
    fun `con otra clave o sin clave una fila cifrada da un error controlado`() {
        Cifrado.usarClaveParaPruebas(claveA)
        val bytes = Cifrado.cifrarBytes(secreto)
        val texto = Cifrado.cifrarTexto("{}")

        Cifrado.usarClaveParaPruebas(claveB)
        assertFailsWith<PapelIlegible> { Cifrado.descifrarBytes(bytes) }
        assertFailsWith<PapelIlegible> { Cifrado.descifrarTexto(texto) }

        Cifrado.usarClaveParaPruebas(null)
        assertFailsWith<PapelIlegible> { Cifrado.descifrarBytes(bytes) }
        assertFailsWith<PapelIlegible> { Cifrado.descifrarTexto(texto) }

        // Truncada o con el base64 roto: tampoco datos basura.
        Cifrado.usarClaveParaPruebas(claveA)
        assertFailsWith<PapelIlegible> { Cifrado.descifrarBytes(bytes.copyOfRange(0, 20)) }
        assertFailsWith<PapelIlegible> { Cifrado.descifrarTexto("MOVI1:%%%") }
    }

    @Test
    fun `una clave mal formada no cae a guardar en claro`() {
        assertFailsWith<IllegalStateException> { Cifrado.usarClaveParaPruebas("no es base64!") }
        assertFailsWith<IllegalStateException> {
            Cifrado.usarClaveParaPruebas(Base64.getEncoder().encodeToString(ByteArray(16)))
        }
    }

    // ── Las rutas ──────────────────────────────────────────────────────────────

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@cifrado.test")
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
        nombre: String = "extracto.pdf",
        contenido: ByteArray = secreto,
        mime: String = "application/pdf",
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

    /** Pide el enlace y baja el contenido: el camino del navegador entero. */
    private suspend fun ApplicationTestBuilder.descargar(uid: String, docId: String): Pair<HttpStatusCode, ByteArray> {
        val enlace = client.post("/api/documents/$docId/link") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        if (enlace.status != HttpStatusCode.OK) return enlace.status to ByteArray(0)
        val url = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(enlace.bodyAsText())!!.groupValues[1]
        val res = client.get(url)
        return res.status to res.readRawBytes()
    }

    private fun contenidoGuardado(docId: String): ByteArray = transaction {
        Documents.selectAll().where { Documents.id eq docId }.single()[Documents.content]
    }

    @Test
    fun `con clave se guarda cifrado y la descarga devuelve el original`() = testApplication {
        wireApp()
        Cifrado.usarClaveParaPruebas(claveA)
        val doc = subir(duenoId)

        val enLaBase = contenidoGuardado(doc)
        assertTrue(Cifrado.estaCifrado(enLaBase))
        assertFalse("EXTRACTO SECRETO" in enLaBase.decodeToString(), "el texto en claro quedó en la base")
        val tamano = transaction { Documents.selectAll().where { Documents.id eq doc }.single()[Documents.sizeBytes] }
        assertEquals(secreto.size.toLong(), tamano, "size_bytes es el tamaño en claro")

        val (status, bajado) = descargar(duenoId, doc)
        assertEquals(HttpStatusCode.OK, status)
        assertContentEquals(secreto, bajado)
    }

    @Test
    fun `sin clave se guarda en claro y se descarga igual`() = testApplication {
        wireApp()
        val doc = subir(duenoId)
        assertContentEquals(secreto, contenidoGuardado(doc))
        val (status, bajado) = descargar(duenoId, doc)
        assertEquals(HttpStatusCode.OK, status)
        assertContentEquals(secreto, bajado)
    }

    @Test
    fun `un documento viejo en claro se descarga con la clave puesta`() = testApplication {
        wireApp()
        val doc = subir(duenoId)
        Cifrado.usarClaveParaPruebas(claveA)
        val (status, bajado) = descargar(duenoId, doc)
        assertEquals(HttpStatusCode.OK, status)
        assertContentEquals(secreto, bajado)
    }

    @Test
    fun `con la clave cambiada se dice el motivo y no se sirven bytes basura`() = testApplication {
        wireApp()
        Cifrado.usarClaveParaPruebas(claveA)
        val doc = subir(duenoId)
        Cifrado.usarClaveParaPruebas(claveB)

        val enlace = client.post("/api/documents/$doc/link") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        val url = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(enlace.bodyAsText())!!.groupValues[1]
        val descarga = client.get(url)
        assertEquals(HttpStatusCode.ServiceUnavailable, descarga.status)
        assertEquals(PAPEL_CIFRADO_ILEGIBLE, descarga.bodyAsText())

        val leer = client.post("/api/documents/$doc/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.ServiceUnavailable, leer.status)
        assertEquals(PAPEL_CIFRADO_ILEGIBLE, leer.bodyAsText())

        val extracto = client.post("/api/documents/$doc/leer-extracto") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.ServiceUnavailable, extracto.status)
        assertEquals(PAPEL_CIFRADO_ILEGIBLE, extracto.bodyAsText())
    }

    @Test
    fun `reusar reconoce el mismo archivo con y sin clave`() = testApplication {
        wireApp()
        // Sin clave.
        val claro1 = subir(duenoId, reusar = true)
        assertEquals(claro1, subir(duenoId, reusar = true))

        // Con clave: el papel en claro de antes sigue siendo «el mismo archivo».
        Cifrado.usarClaveParaPruebas(claveA)
        assertEquals(claro1, subir(duenoId, reusar = true))

        // Y uno nuevo, guardado cifrado, también se reconoce aunque sus bytes en la base cambien.
        val otro = "%PDF-1.4 otro papel".encodeToByteArray()
        val cifrado1 = subir(duenoId, nombre = "otro.pdf", contenido = otro, reusar = true)
        assertTrue(Cifrado.estaCifrado(contenidoGuardado(cifrado1)))
        assertEquals(cifrado1, subir(duenoId, nombre = "otro.pdf", contenido = otro, reusar = true))

        // El mismo nombre y tamaño con otro contenido sigue siendo otro papel.
        val distinto = otro.copyOf().also { it[it.size - 1] = 'X'.code.toByte() }
        assertNotEquals(cifrado1, subir(duenoId, nombre = "otro.pdf", contenido = distinto, reusar = true))
    }

    @Test
    fun `entre usuarios no se cruza nada aunque el archivo sea el mismo`() = testApplication {
        wireApp()
        Cifrado.usarClaveParaPruebas(claveA)
        val delDueno = subir(duenoId, reusar = true)
        val delOtro = subir(otroId, reusar = true)
        assertNotEquals(delDueno, delOtro, "reusar no puede devolver el papel de otro")

        val (status, _) = descargar(otroId, delDueno)
        assertEquals(HttpStatusCode.NotFound, status)
        val (suyo, bajado) = descargar(otroId, delOtro)
        assertEquals(HttpStatusCode.OK, suyo)
        assertContentEquals(secreto, bajado)
    }

    @Test
    fun `la huella es la del contenido en claro y la lectura se guarda cifrada`() = testApplication {
        wireApp()
        Cifrado.usarClaveParaPruebas(claveA)
        val lector = LectorFalso(QueDiceElPapel.Comprobante(coomeva))
        LectorDePapeles.actual = lector
        val imagen = byteArrayOf(1, 2, 3, 4, 5, 6)
        val doc = subir(duenoId, nombre = "transferencia.png", contenido = imagen, mime = "image/png")

        val res = client.post("/api/documents/$doc/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        val (huella, datos) = transaction {
            LecturasDePapeles.selectAll().where { LecturasDePapeles.userId eq duenoId }.single()
                .let { it[LecturasDePapeles.huella] to it[LecturasDePapeles.datos] }
        }
        assertEquals(huellaDelPapel(imagen), huella, "la huella se calcula sobre los bytes en claro")
        assertTrue(datos.startsWith("MOVI1:"))
        assertFalse("Coomeva" in datos, "lo que leyó Claude quedó en claro")

        // La misma imagen subida otra vez (otro documento, otro IV): no vuelve a Claude.
        val copia = subir(duenoId, nombre = "copia.png", contenido = imagen, mime = "image/png")
        val otraVez = client.post("/api/documents/$copia/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, otraVez.status)
        assertEquals(1, lector.veces, "el mismo archivo se mandó dos veces a Claude")

        // La bandeja lee la propuesta de la lectura cifrada, no del parser de SMS.
        val parse = client.get("/api/sms/${idDeComprobante(doc)}/parse") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, parse.status)
        val propuesta = json.decodeFromString(ParsedSms.serializer(), parse.bodyAsText())
        assertEquals("Coomeva Medicina Prepagada", propuesta.merchant)
        assertEquals("Salud", propuesta.category)
    }

    @Test
    fun `sin clave la huella y la reutilizacion de la lectura funcionan igual`() = testApplication {
        wireApp()
        val lector = LectorFalso(QueDiceElPapel.Comprobante(coomeva))
        LectorDePapeles.actual = lector
        val imagen = byteArrayOf(9, 8, 7, 6)
        val doc = subir(duenoId, nombre = "a.png", contenido = imagen, mime = "image/png")
        client.post("/api/documents/$doc/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        val copia = subir(duenoId, nombre = "b.png", contenido = imagen, mime = "image/png")
        client.post("/api/documents/$copia/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(1, lector.veces)
        val datos = transaction { LecturasDePapeles.selectAll().single()[LecturasDePapeles.datos] }
        assertTrue(datos.startsWith("{"), "sin clave la lectura queda en claro, como antes")
    }

    // ── La migración del arranque ──────────────────────────────────────────────

    private fun guardar(uid: String, id: String, bytes: ByteArray) = transaction {
        guardarDocumento(
            uid,
            Documento(
                id = id, nombre = "$id.pdf", tipo = TipoDeDocumento.EXTRACTO, mimeType = "application/pdf",
                bytes = bytes.size.toLong(), subidoEn = 0,
            ),
            bytes,
        )
    }

    private fun lecturaEnClaro(uid: String, huella: String, datos: String) = transaction {
        LecturasDePapeles.insert {
            it[userId] = uid
            it[LecturasDePapeles.huella] = huella
            it[documentoId] = "doc-$huella"
            it[tipo] = "COMPROBANTE"
            it[LecturasDePapeles.datos] = datos
            it[modelo] = "prueba"
            it[leidoEn] = 0
        }
    }

    private fun todosLosContenidos(): Map<String, ByteArray> = transaction {
        Documents.selectAll().associate { it[Documents.id] to it[Documents.content] }
    }

    private fun todasLasLecturas(): Map<String, String> = transaction {
        LecturasDePapeles.selectAll().associate { "${it[LecturasDePapeles.userId]}/${it[LecturasDePapeles.huella]}" to it[LecturasDePapeles.datos] }
    }

    private fun migrar(lote: Int = 2) = transaction { with(Migrations) { cifrarLosPapelesEnClaro(lote) } }

    @Test
    fun `la migracion cifra lo viejo en lotes y la segunda corrida no cambia nada`() {
        val originales = (1..5).associate { n -> "doc-$n" to "%PDF papel $n del dueño".encodeToByteArray() }
        originales.forEach { (id, bytes) -> guardar(if (id == "doc-5") otroId else duenoId, id, bytes) }
        lecturaEnClaro(duenoId, "h1", """{"comercio":"Coomeva"}""")
        lecturaEnClaro(duenoId, "h2", """{"comercio":"Exito"}""")
        lecturaEnClaro(otroId, "h3", """{"comercio":"Rappi"}""")

        Cifrado.usarClaveParaPruebas(claveA)
        assertEquals(Migrations.PapelesCifrados(documentos = 5, lecturas = 3), migrar(lote = 2))

        val contenidos = todosLosContenidos()
        originales.forEach { (id, bytes) ->
            val guardado = contenidos.getValue(id)
            assertTrue(Cifrado.estaCifrado(guardado), id)
            assertContentEquals(bytes, Cifrado.descifrarBytes(guardado), id)
        }
        val lecturas = todasLasLecturas()
        assertTrue(lecturas.values.all { it.startsWith("MOVI1:") })
        assertEquals("""{"comercio":"Rappi"}""", Cifrado.descifrarTexto(lecturas.getValue("$otroId/h3")))

        // Idempotente: nada que cifrar, y los bytes no cambian (re-cifrar cambiaría el IV).
        assertEquals(Migrations.PapelesCifrados(0, 0), migrar())
        assertEquals(contenidos.mapValues { it.value.toList() }, todosLosContenidos().mapValues { it.value.toList() })
        assertEquals(lecturas, todasLasLecturas())
    }

    @Test
    fun `sin clave la migracion no toca nada`() {
        guardar(duenoId, "doc-claro", secreto)
        lecturaEnClaro(duenoId, "h1", "{}")
        assertEquals(Migrations.PapelesCifrados(0, 0), migrar())
        assertContentEquals(secreto, todosLosContenidos().getValue("doc-claro"))
        assertEquals("{}", todasLasLecturas().values.single())
    }

    @Test
    fun `la migracion no vuelve a cifrar una fila cifrada con otra clave`() {
        Cifrado.usarClaveParaPruebas(claveB)
        guardar(duenoId, "doc-con-b", secreto)
        val conB = todosLosContenidos().getValue("doc-con-b")

        Cifrado.usarClaveParaPruebas(claveA)
        assertEquals(Migrations.PapelesCifrados(0, 0), migrar())
        assertContentEquals(conB, todosLosContenidos().getValue("doc-con-b"))

        Cifrado.usarClaveParaPruebas(claveB)
        assertContentEquals(secreto, Cifrado.descifrarBytes(todosLosContenidos().getValue("doc-con-b")))
    }

    @Test
    fun `una fila en claro que el filtro por largo salta se sigue leyendo`() {
        transaction {
            Documents.insert {
                it[id] = "doc-raro"
                it[userId] = duenoId
                it[name] = "raro.pdf"
                it[kind] = "OTRO"
                it[mimeType] = "application/pdf"
                // Exactamente lo que mediría si estuviera cifrado: el filtro barato lo saltaría.
                it[sizeBytes] = (secreto.size - Cifrado.BYTES_DE_MAS).toLong()
                it[uploadedAt] = 0
                it[content] = secreto
            }
        }
        Cifrado.usarClaveParaPruebas(claveA)
        // El filtro por largo es una optimización: esta fila (rara a propósito) no la pasa y queda
        // en claro, pero se sigue leyendo bien. Lo que importa es que no rompa ni corrompa.
        migrar()
        assertContentEquals(secreto, Cifrado.descifrarBytes(todosLosContenidos().getValue("doc-raro")))
    }

    // ── El export ──────────────────────────────────────────────────────────────

    @Test
    fun `el export sale en claro y nunca con bytes cifrados`() {
        Cifrado.usarClaveParaPruebas(claveA)
        guardar(duenoId, "doc-export", secreto)
        lecturaEnClaro(duenoId, "h-export", Cifrado.cifrarTexto("""{"comercio":"Coomeva"}"""))

        // Si algún día las dos columnas cifradas entran al export, salen en claro.
        val conTodo = transaction {
            leerDatosDe(
                duenoId,
                listOf(
                    TablaExportada("documentos", Documents, Documents.userId),
                    TablaExportada("lecturas", LecturasDePapeles, LecturasDePapeles.userId),
                ),
            )
        }
        val docs = conTodo.single { it.nombre == "documentos" }
        val contenido = docs.filas.single()[docs.columnas.indexOf(Documents.content.name)] as ByteArray
        assertContentEquals(secreto, contenido)
        val lecturas = conTodo.single { it.nombre == "lecturas" }
        assertEquals("""{"comercio":"Coomeva"}""", lecturas.filas.single()[lecturas.columnas.indexOf(LecturasDePapeles.datos.name)])

        // Y el export de verdad (sin el binario) no trae nada cifrado: solo los metadatos, en claro.
        val zip = transaction { zipDeLosDatos(duenoId, leerDatosDe(duenoId, listOf(
            TablaExportada("documentos", Documents, Documents.userId, setOf(Documents.content)),
        )), "2026-10-20T00:00:00Z") }
        val textos = buildString {
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                while (z.nextEntry != null) append(z.readBytes().decodeToString())
            }
        }
        assertTrue("doc-export.pdf" in textos)
        assertFalse("MOVI1" in textos)
    }

    @Test
    fun `el export del dueno no trae la lectura del otro`() {
        Cifrado.usarClaveParaPruebas(claveA)
        lecturaEnClaro(duenoId, "h-mia", Cifrado.cifrarTexto("""{"comercio":"Mio"}"""))
        lecturaEnClaro(otroId, "h-ajena", Cifrado.cifrarTexto("""{"comercio":"Ajeno"}"""))
        val datos = transaction {
            leerDatosDe(duenoId, listOf(TablaExportada("lecturas", LecturasDePapeles, LecturasDePapeles.userId)))
        }.single()
        val columna = datos.columnas.indexOf(LecturasDePapeles.datos.name)
        assertEquals(listOf("""{"comercio":"Mio"}"""), datos.filas.map { it[columna] })
    }
}
