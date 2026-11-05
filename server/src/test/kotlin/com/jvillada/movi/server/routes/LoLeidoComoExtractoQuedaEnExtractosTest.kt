package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conectarBaseDePrueba
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.ClavesDeExtractos
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.Migrations
import com.jvillada.movi.server.db.MigracionesHechas
import com.jvillada.movi.server.db.PdfsAbiertos
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImportMatches
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.TipoDeDocumento
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Vuelta 129 · lo que se leyó o se importó como extracto queda en «Extractos»
 *
 * Una probadora compartió con Movi tres extractos de su cuenta Nu («CuentaNu_CRS119_2026-09.pdf») y
 * uno de Bancolombia: Movi los guardó con el tipo que adivina el nombre («Otro»), los leyó como
 * extracto, dos se importaron… y los cuatro siguieron en «Otros · 4». Leer o importar un extracto
 * ahora deja el papel como extracto, con su mes y la cuenta del importe; y una migración arregla
 * los que ya se habían leído.
 *
 * **Ninguna prueba llama a Claude**: el papel es el extracto de la tarjeta de Nu, que se lee sin IA,
 * y el lector de prueba falla si alguien lo llama.
 */
class LoLeidoComoExtractoQuedaEnExtractosTest {

    private val testSecret = "test-secret-for-extractos-que-quedan-min-32-chars"
    private val dueno = "user-extractos-que-quedan"
    private val otro = "user-otro-extractos-que-quedan"
    private val ahorros = "acc-nu-ahorros-129"
    private val json = Json { ignoreUnknownKeys = true }

    private object NadieLlamaAClaude : LectorDePapeles {
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = fail("se llamó a Claude")
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("se llamó a Claude")
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("se llamó a Claude")
    }

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-extractos-que-quedan-jwt-32")
        LectorDePapeles.actual = NadieLlamaAClaude
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:extractos_que_quedan;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                MigracionesHechas, PdfsAbiertos, ClavesDeExtractos, LecturasDePapeles, Documents, SmsMessages, VoidEvents, Events,
                StatementImportMatches, StatementImports, Accounts, Users, KnownDestinations,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, StatementImportMatches, Events, VoidEvents, SmsMessages, Documents,
                KnownDestinations, LecturasDePapeles, ClavesDeExtractos, PdfsAbiertos, MigracionesHechas,
            )
            listOf(dueno, otro).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@test"; it[name] = uid; it[passwordHash] = "hash"
                }
            }
            Accounts.insert {
                it[id] = ahorros; it[userId] = dueno; it[name] = "Nu Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
    }

    @AfterTest
    fun devolverElLector() {
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    /** Lo guarda como lo guarda «Compartir con Movi» con un nombre que no dice «extracto»: «Otro». */
    private suspend fun ApplicationTestBuilder.subirComoOtro(nombre: String = "CuentaNu_CRS119_2026-10.csv", tipo: String = "OTRO"): String {
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token(dueno)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tipo", tipo)
                        append("file", EXTRACTO_DE_LA_TARJETA_NU.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                            append(HttpHeaders.ContentType, "text/csv")
                        })
                    },
                ),
            )
        }
        val cuerpo = res.bodyAsText()
        assertEquals(HttpStatusCode.Created, res.status, cuerpo)
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]
    }

    private suspend fun ApplicationTestBuilder.documentos(): List<Documento> {
        val cuerpo = client.get("/api/documents") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }.bodyAsText()
        return json.decodeFromString(ListSerializer(Documento.serializer()), cuerpo)
    }

    @Test
    fun `un papel guardado como Otro que se lee como extracto pasa a Extractos, con su mes`() = testApplication {
        wireApp()
        val id = subirComoOtro()
        assertEquals(TipoDeDocumento.OTRO, documentos().single().tipo)

        val res = client.post("/api/documents/$id/leer-extracto") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        val doc = documentos().single()
        assertEquals(id, doc.id, "no se archiva una segunda copia")
        assertEquals(TipoDeDocumento.EXTRACTO, doc.tipo)
        assertTrue(!doc.periodo.isNullOrBlank(), "el extracto queda con su mes")
    }

    @Test
    fun `compartido con Movi, lo que lee leer queda en Extractos`() = testApplication {
        wireApp()
        val id = subirComoOtro()

        val res = client.post("/api/documents/$id/leer") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        assertEquals(TipoDeDocumento.EXTRACTO, documentos().single { it.id == id }.tipo)
    }

    @Test
    fun `lo que se importa queda como extracto y con la cuenta del importe`() = testApplication {
        wireApp()
        val id = subirComoOtro()
        val decision = ImportDecision(
            statementId = "st-129", accountId = ahorros, bankName = "Nu", period = "Octubre 2026",
            imports = emptyList(), reconciliations = emptyList(), skipped = emptyList(), documentoId = id,
        )
        val res = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${token(dueno)}")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ImportDecision.serializer(), decision))
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        val doc = documentos().single()
        assertEquals(TipoDeDocumento.EXTRACTO, doc.tipo)
        assertEquals(ahorros, doc.accountId)
        assertEquals("Octubre 2026", doc.periodo)
    }

    /** Revisión: lo que el dueño clasificó como nómina o contrato no se reclasifica al leerlo ni al importarlo. */
    @Test
    fun `una nomina leida e importada como extracto sigue siendo nomina`() = testApplication {
        wireApp()
        val id = subirComoOtro(tipo = "NOMINA")
        assertEquals(HttpStatusCode.OK, client.post("/api/documents/$id/leer-extracto") { header(HttpHeaders.Authorization, "Bearer ${token(dueno)}") }.status)
        val decision = ImportDecision(
            statementId = "st-129n", accountId = ahorros, bankName = "Nu", period = "Octubre 2026",
            imports = emptyList(), reconciliations = emptyList(), skipped = emptyList(), documentoId = id,
        )
        client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${token(dueno)}")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ImportDecision.serializer(), decision))
        }
        val doc = documentos().single()
        assertEquals(TipoDeDocumento.NOMINA, doc.tipo)
        assertEquals(null, doc.periodo, "a una nómina no se le pone el mes del extracto")
        assertEquals(ahorros, doc.accountId, "la cuenta del importe sí, como antes")
    }

    @Test
    fun `la migracion pasa a Extractos lo que ya se leyo como extracto, y nada mas`() {
        transaction {
            fun papel(id: String, uid: String, tipo: String) = Documents.insert {
                it[Documents.id] = id; it[userId] = uid; it[name] = "$id.pdf"; it[kind] = tipo
                it[mimeType] = "application/pdf"; it[sizeBytes] = 3; it[uploadedAt] = 1L; it[content] = byteArrayOf(1, 2, 3)
            }
            fun lectura(uid: String, docId: String, tipo: String) = LecturasDePapeles.insert {
                it[userId] = uid; it[huella] = "h-$docId"; it[documentoId] = docId; it[LecturasDePapeles.tipo] = tipo
                it[datos] = "{}"; it[modelo] = "sin-ia"; it[leidoEn] = 1L
            }
            papel("doc-extracto", dueno, "OTRO"); lectura(dueno, "doc-extracto", "EXTRACTO")
            papel("doc-comprobante", dueno, "OTRO"); lectura(dueno, "doc-comprobante", "COMPROBANTE")
            // Una nómina que alguien clasificó a mano no se mueve, aunque Movi la haya leído.
            papel("doc-nomina", dueno, "NOMINA"); lectura(dueno, "doc-nomina", "EXTRACTO")
            // La lectura de otro usuario no toca un documento con el mismo id de otro dueño.
            papel("doc-ajeno", otro, "OTRO"); lectura(dueno, "doc-ajeno", "EXTRACTO")

            assertEquals(1, with(Migrations) { losPapelesLeidosComoExtractoSonExtractos() })
            val tipos = Documents.selectAll().associate { it[Documents.id] to it[Documents.kind] }
            assertEquals("EXTRACTO", tipos["doc-extracto"])
            assertEquals("OTRO", tipos["doc-comprobante"])
            assertEquals("NOMINA", tipos["doc-nomina"])
            assertEquals("OTRO", tipos["doc-ajeno"])
            // Corre una sola vez: lo que el dueño pase a «Otro» después se queda ahí.
            Documents.update({ Documents.id eq "doc-extracto" }) { it[kind] = "OTRO" }
            assertEquals(0, with(Migrations) { losPapelesLeidosComoExtractoSonExtractos() })
            assertEquals("OTRO", Documents.selectAll().where { Documents.id eq "doc-extracto" }.single()[Documents.kind])
        }
    }
}
