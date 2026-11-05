package com.jvillada.movi.server.routes

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
import com.jvillada.movi.server.db.StatementImportMatches
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TarjetaDelExtracto
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.DeudaDelExtracto
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.deudaParaCuadrar
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.jvillada.movi.shared.model.PistasDelPapel
import com.jvillada.movi.shared.model.ProductoDelPapel
import com.jvillada.movi.shared.model.huellaSha256
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * # Vuelta 129 · el extracto de una tarjeta dice que es de una tarjeta
 *
 * Una probadora sin su tarjeta Nu en Movi subió el extracto de la tarjeta: la revisión solo podía
 * proponerle «Nu Ahorros», por llamarse igual, y las compras habrían caído en la cuenta de ahorros.
 * Ahora la lectura dice que es de una tarjeta y lo que dice de ella (los últimos cuatro, el corte, el
 * pago y el cupo), para que la app la ofrezca crear ya llena. Solo a quien lo pide (`?conTarjeta=1`).
 *
 * Y la lectura devuelve la cuenta que el dueño le puso al papel al subirlo: la hoja dice «Los
 * movimientos del extracto se anotan en esta cuenta», y la revisión la usa.
 *
 * Ninguna prueba llama a Claude: el extracto de Nu se lee sin IA.
 */
class ElExtractoDeUnaTarjetaLoDiceTest {

    private val testSecret = "test-secret-for-tarjeta-del-extracto-min-32-chars"
    private val dueno = "user-tarjeta-del-extracto"
    private val ahorros = "acc-nu-ahorros-tarjeta"
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
        System.setProperty("movi.jwt.secret", "test-secret-for-tarjeta-del-extracto-jwt-32")
        LectorDePapeles.actual = NadieLlamaAClaude
        Database.connect(
            url = "jdbc:h2:mem:tarjeta_del_extracto;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                PdfsAbiertos, ClavesDeExtractos, LecturasDePapeles, Documents, SmsMessages, VoidEvents, Events,
                StatementImportMatches, StatementImports, Accounts, Users, KnownDestinations,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, StatementImportMatches, Events, VoidEvents, SmsMessages, Documents, KnownDestinations,
                LecturasDePapeles, ClavesDeExtractos, PdfsAbiertos,
            )
            Users.insert { it[id] = dueno; it[email] = "$dueno@test"; it[name] = dueno; it[passwordHash] = "hash" }
            Accounts.insert {
                it[id] = ahorros; it[userId] = dueno; it[name] = "Nu Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
    }

    @AfterTest
    fun devolverElLector() {
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    private fun token(): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", dueno).withClaim("email", "$dueno@test")
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

    private fun archivo(campos: Map<String, String> = emptyMap()) = MultiPartFormDataContent(
        formData {
            campos.forEach { (k, v) -> append(k, v) }
            append("file", EXTRACTO_DE_LA_TARJETA_NU.toByteArray(), Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"Nu_2026-10-12.csv\"")
                append(HttpHeaders.ContentType, "text/csv")
            })
        },
    )

    private suspend fun ApplicationTestBuilder.subirAlImportador(query: String): StatementParseResult {
        val res = client.post("/api/statements/upload$query") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody(archivo())
        }
        val cuerpo = res.bodyAsText()
        assertEquals(HttpStatusCode.OK, res.status, cuerpo)
        return json.decodeFromString(StatementParseResult.serializer(), cuerpo)
    }

    @Test
    fun `el extracto de la tarjeta de Nu dice que es de una tarjeta, y lo que dice de ella`() = testApplication {
        wireApp()
        val leido = subirAlImportador("?conTarjeta=1")
        assertEquals(
            TarjetaDelExtracto(
                banco = "Nu", ultimosCuatro = "1111", diaDeCorte = 12, diaDePago = 3, cupo = 3_000_000,
                // Lo que se debía al corte: el pago mínimo ($101.240) más la deuda restante ($310.000).
                deudaAlCorte = 411_240, fechaDeCorte = "2026-10-12",
            ),
            leido.tarjeta,
        )
    }

    @Test
    fun `a un APK viejo no se le manda`() = testApplication {
        wireApp()
        assertNull(subirAlImportador("").tarjeta)
    }

    @Test
    fun `leer el papel devuelve la cuenta que se le puso al subirlo`() = testApplication {
        wireApp()
        val subida = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody(archivo(mapOf("tipo" to "EXTRACTO", "accountId" to ahorros)))
        }
        val cuerpo = subida.bodyAsText()
        assertEquals(HttpStatusCode.Created, subida.status, cuerpo)
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]

        val porDocumentos = client.post("/api/documents/$id/leer-extracto?conTarjeta=1") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val leido = json.decodeFromString(StatementParseResult.serializer(), porDocumentos.bodyAsText())
        assertEquals(ahorros, leido.cuentaDelPapel)
        assertNotNull(leido.tarjeta, "y que es de una tarjeta, para que la revisión lo advierta")

        // Por «Compartir con Movi» igual.
        val compartido = client.post("/api/documents/$id/leer?conTarjeta=1") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val lectura = json.decodeFromString(LecturaDelPapel.serializer(), compartido.bodyAsText())
        assertEquals(ahorros, lectura.extracto?.cuentaDelPapel)
        assertEquals("Nu", lectura.extracto?.tarjeta?.banco)
    }

    // ── Vuelta 129 · lo que Movi ya sabe de un archivo, por su huella ──────────────

    private suspend fun ApplicationTestBuilder.loQueSeSabe(cuerpo: String) = client.post("/api/documents/lo-que-se-sabe") {
        header(HttpHeaders.Authorization, "Bearer ${token()}")
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    @Test
    fun `lo que se sabe de un archivo ya leido, por su huella`() = testApplication {
        wireApp()
        val huella = huellaSha256(EXTRACTO_DE_LA_TARJETA_NU.toByteArray())
        // Antes de leerlo, Movi no sabe nada.
        assertEquals(HttpStatusCode.NoContent, loQueSeSabe("""{"huella":"$huella"}""").status)

        val subida = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody(archivo(mapOf("tipo" to "OTRO")))
        }
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(subida.bodyAsText())!!.groupValues[1]
        // «Compartir con Movi» lo lee y guarda la lectura (con la huella del server).
        assertEquals(HttpStatusCode.OK, client.post("/api/documents/$id/leer") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.status)

        val res = loQueSeSabe("""{"huella":"$huella"}""")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            PistasDelPapel(banco = "Nu", numerosDeCuenta = listOf("1111", "2222"), producto = ProductoDelPapel.TARJETA),
            json.decodeFromString(PistasDelPapel.serializer(), res.bodyAsText()),
        )
    }

    @Test
    fun `sin una huella valida, 400`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, loQueSeSabe("""{"huella":"no-es-una-huella"}""").status)
        assertEquals(HttpStatusCode.BadRequest, loQueSeSabe("{}").status)
    }

    // ── Revisión · cuadrar la deuda de la tarjeta con el extracto ─────────────────

    private val tarjeta = "acc-nu-tarjeta"

    private fun crearLaTarjeta() = transaction {
        Accounts.insert {
            it[id] = tarjeta; it[userId] = dueno; it[name] = "Nu Tarjeta 1111"; it[type] = "CREDIT_CARD"; it[currency] = "COP"
        }
    }

    private suspend fun ApplicationTestBuilder.cuadrar(cuenta: String, deuda: DeudaDelExtracto) =
        client.post("/api/accounts/$cuenta/deuda-del-extracto") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(DeudaDelExtracto.serializer(), deuda))
        }

    /**
     * La tarjeta recién creada nace en $0, el importe le suma las compras del período y el ajuste la
     * deja debiendo exactamente lo del extracto al corte: nada cuenta dos veces. El segundo «Cuadrar»
     * no escribe otro ajuste.
     */
    @Test
    fun `cuadrar la deuda deja la tarjeta en lo que dice el extracto, sin contar dos veces`() = testApplication {
        wireApp()
        crearLaTarjeta()
        val leido = subirAlImportador("?conTarjeta=1")
        val decision = ImportDecision(
            statementId = leido.statementId, accountId = tarjeta, bankName = leido.bankName, period = leido.period,
            imports = leido.newTransactions, reconciliations = emptyList(), skipped = emptyList(),
        )
        val importe = client.post("/api/statements/import") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ImportDecision.serializer(), decision))
        }
        assertEquals(HttpStatusCode.OK, importe.status, importe.bodyAsText())

        val deuda = assertNotNull(leido.tarjeta?.deudaParaCuadrar())
        val primera = cuadrar(tarjeta, deuda)
        assertEquals(HttpStatusCode.OK, primera.status, primera.bodyAsText())
        val respuesta = json.decodeFromString(AdjustAccountBalanceResponse.serializer(), primera.bodyAsText())
        assertEquals(411_240L, respuesta.account.balance, "la deuda del extracto, no la del extracto más las compras")
        assertNotNull(respuesta.adjustmentEvent)

        val segunda = json.decodeFromString(AdjustAccountBalanceResponse.serializer(), cuadrar(tarjeta, deuda).bodyAsText())
        assertNull(segunda.adjustmentEvent, "cuadrar dos veces no escribe dos ajustes")
        assertEquals(411_240L, segunda.account.balance)
    }

    @Test
    fun `la deuda de un extracto no se cuadra en una cuenta de ahorros`() = testApplication {
        wireApp()
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            cuadrar(ahorros, DeudaDelExtracto(deuda = 411_240, fechaDeCorte = "2026-10-12")).status,
        )
    }

    // ── Revisión · una lectura vieja de Nu se relee sin IA ──────────────────────────

    @Test
    fun `una lectura de Nu guardada sin la tarjeta se relee sin IA y se guarda la nueva`() = testApplication {
        wireApp()
        val subida = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody(archivo(mapOf("tipo" to "OTRO")))
        }
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(subida.bodyAsText())!!.groupValues[1]
        val huella = huellaSha256(EXTRACTO_DE_LA_TARJETA_NU.toByteArray())
        // La lectura de antes de esta vuelta: el lector de Nu ya leía las filas, pero no decía la tarjeta.
        val vieja = ExtractoLeido(bankName = "Nu", filas = emptyList(), esFamirios = false, numerosDeCuenta = listOf("1111"))
        transaction {
            LecturasDePapeles.insert {
                it[userId] = dueno; it[LecturasDePapeles.huella] = huella; it[documentoId] = id; it[tipo] = "EXTRACTO"
                it[datos] = json.encodeToString(ExtractoLeido.serializer(), vieja); it[modelo] = "x"; it[leidoEn] = 1L
            }
        }

        val res = client.post("/api/documents/$id/leer?conTarjeta=1") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        val lectura = json.decodeFromString(LecturaDelPapel.serializer(), res.bodyAsText())
        assertEquals("Nu", lectura.extracto?.tarjeta?.banco)

        val guardada = transaction {
            LecturasDePapeles.selectAll().where { LecturasDePapeles.huella eq huella }.single()[LecturasDePapeles.datos]
        }
        assertNotNull(json.decodeFromString(ExtractoLeido.serializer(), guardada).tarjeta, "la lectura nueva queda guardada")
    }

}
