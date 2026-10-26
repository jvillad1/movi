package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.ReglasDeComercio
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeSaldo
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.cuentaNombrada
import com.jvillada.movi.shared.model.hayQueActualizarElSaldo
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
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
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # El extracto de Skandia o de la AFC propone el saldo, y «Actualizar el saldo» lo pone al corte
 *
 * Los papeles son sintéticos (la forma del texto de PDFBox, todo inventado). **Ninguna prueba llama
 * a Anthropic**: el lector de papeles es uno falso que falla si alguien le pregunta algo — estos
 * extractos se leen sin IA.
 */
class ExtractoDeSaldoRoutesTest {

    private val testSecret = "test-secret-for-extracto-saldo-min-32-chars"
    private val duenoId = "user-dueno-extracto-saldo"
    private val otroId = "user-otro-extracto-saldo"
    private val skandia = "acc-skandia"
    private val afc = "acc-afc-1111"
    private val ahorros = "acc-ahorros-8133"
    private val tarjeta = "acc-tarjeta"
    private val cuentaDelOtro = "acc-otro-skandia"
    private val json = Json { ignoreUnknownKeys = true }
    private val lectorDeVerdad = LectorDePapeles.actual

    private val textoDeSkandia = """
        1/11
        Resumen
        Productos Skandia
        PERSONA DE PRUEBA
        Agosto 2026
        Producto Contrato No. Objetivo Inversión Perfil Inversión Saldo
        Productos Voluntarios
        Fondo Voluntario de Pensión Skandia
        Multifund
        $12,345,678.90
        Contratos Bonificaciones OMNI 100000001111 $10,000,000.00
        Contratos Bonificaciones OMNI 100000002222 $2,345,678.90
        NOTAS DE INTERÉS
    """.trimIndent()

    private val textoDeDavibank = """
        ESTIMADO CLIENTE
        PERSONA DE PRUEBA
        PERIODO
        1 AL 31 AGO 2026
        CUENTA DE AHORROS
        No 9876541111
        RESUMEN CUENTA AHORROS
        SALDO ANTERIOR DEPOSITOS Y OTROS CREDITOS RETIROS Y OTROS DEBITOS NUEVO SALDO
        1.000.000,00 500.000,00 200.000,00 1.300.000,00
        DETALLE DE CUENTA
        www.davibank.com
    """.trimIndent()

    /** El corte de los dos papeles: el 31 de agosto, al final del día. */
    private val finDelCorte = appDateToEpochMillis(LocalDate.of(2026, 9, 1)) - 1

    @BeforeTest
    fun setUp() {
        LectorDePapeles.actual = LectorQueNoSeUsa
        Database.connect(
            url = "jdbc:h2:mem:extracto_saldo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(ReglasDeComercio, LecturasDePapeles, Documents, SmsMessages, KnownDestinations, VoidEvents, Events, StatementImports, Accounts, Users)
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, SmsMessages, KnownDestinations, Documents, LecturasDePapeles, ReglasDeComercio)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@saldo.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
            Accounts.insert { it[id] = skandia; it[userId] = duenoId; it[name] = "Skandia FPV"; it[type] = "INVESTMENT" }
            Accounts.insert { it[id] = afc; it[userId] = duenoId; it[name] = "AFC Davibank 1111"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros 8133"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = tarjeta; it[userId] = duenoId; it[name] = "Master 1111"; it[type] = "CREDIT_CARD" }
            Accounts.insert { it[id] = cuentaDelOtro; it[userId] = otroId; it[name] = "Skandia del otro"; it[type] = "INVESTMENT" }
            movimiento("ev-skandia-inicial", skandia, "INCOME", 12_000_000L, dia(2026, 7, 1))
            movimiento("ev-afc-inicial", afc, "INCOME", 1_000_000L, dia(2026, 7, 1))
        }
    }

    @AfterTest
    fun tearDown() {
        LectorDePapeles.actual = lectorDeVerdad
    }

    private fun dia(anio: Int, mes: Int, d: Int): Long = appDateToEpochMillis(LocalDate.of(anio, mes, d)) + 12 * 3_600_000L

    private fun movimiento(id: String, cuenta: String, tipo: String, monto: Long, cuando: Long, uid: String = duenoId, categoria: String = "Ingreso") {
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo
            it[amount] = monto; it[currency] = "COP"; it[category] = categoria; it[description] = id
            it[timestamp] = cuando; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@saldo.test")
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

    /** Un lector que falla si alguien le pregunta algo: nada de esto va a Claude. */
    private object LectorQueNoSeUsa : LectorDePapeles {
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = fail("el extracto de saldo no se clasifica con IA")
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("el extracto de saldo no va a Claude")
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("el extracto de saldo no va a Claude")
    }

    /** El de antes: un cliente que no pide saldos sigue yendo a Claude con el texto. */
    private class LectorDeSiempre : LectorDePapeles {
        var leidos = 0
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = QueDiceElPapel.SinMovimiento
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura {
            leidos++
            return ClaudeStatementParser.Lectura.Ok(emptyList())
        }
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("no es una imagen")
    }

    private suspend fun ApplicationTestBuilder.subir(texto: String, conSaldos: Boolean = true, uid: String = duenoId, nombre: String = "Fondo_09_2026.txt"): HttpResponse =
        client.post("/api/statements/upload?conCreditos=1" + if (conSaldos) "&conSaldos=1" else "") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", texto.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                    },
                ),
            )
        }

    private suspend fun ApplicationTestBuilder.propuestaDe(texto: String, uid: String = duenoId): PropuestaDelExtractoDeSaldo {
        val res = subir(texto, uid = uid)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return assertNotNull(json.decodeFromString(StatementParseResult.serializer(), res.bodyAsText()).extractoDeSaldo)
    }

    private suspend fun ApplicationTestBuilder.actualizar(cuenta: String, resumen: ResumenDelExtractoDeSaldo, uid: String = duenoId): HttpResponse =
        client.post("/api/accounts/$cuenta/saldo-del-extracto") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResumenDelExtractoDeSaldo.serializer(), resumen))
        }

    private fun ajustesDe(cuenta: String) = transaction {
        Events.selectAll().where { (Events.accountId eq cuenta) and (Events.category eq ADJUSTMENT_CATEGORY) }.toList()
    }

    // ── La propuesta ────────────────────────────────────────────────────────

    @Test
    fun `Skandia - propone el saldo del extracto al lado del de Movi, en la cuenta Skandia`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeSkandia)

        assertEquals(ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO, p.resumen.de)
        assertEquals(12_345_679L, p.resumen.saldo)
        assertEquals("2026-08-31", p.resumen.fechaDeCorte)
        assertEquals(2, p.resumen.contratos.size)
        assertEquals(skandia, p.cuentaId)
        val cuenta = assertNotNull(cuentaNombrada(p))
        assertEquals(12_000_000L, cuenta.saldoEnMovi)
        assertTrue(hayQueActualizarElSaldo(p.resumen, cuenta))
        // Las opciones para elegir: dinero e inversión, la nombrada primero; ni la tarjeta ni lo del otro.
        assertEquals(listOf(skandia, ahorros, afc).toSet(), p.opciones.map { it.id }.toSet())
        assertEquals(skandia, p.opciones.first().id)
        // Se archivó en Documentos, como cualquier extracto.
        transaction { assertEquals(1L, Documents.selectAll().where { Documents.userId eq duenoId }.count()) }
    }

    @Test
    fun `AFC - la cuenta se resuelve por el numero, aunque una tarjeta lleve el mismo`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeDavibank)

        assertEquals(ExtractoDeSaldoDe.DAVIBANK_AHORROS, p.resumen.de)
        assertEquals("1111", p.resumen.numero)
        assertEquals(afc, p.cuentaId)
        assertEquals(1_000_000L, cuentaNombrada(p)?.saldoEnMovi)
    }

    @Test
    fun `sin diferencia no hay nada que actualizar`() = testApplication {
        application { testModule() }
        transaction { movimiento("ev-rendimientos", skandia, "INCOME", 345_679L, dia(2026, 8, 31)) }
        val p = propuestaDe(textoDeSkandia)
        assertEquals(12_345_679L, cuentaNombrada(p)?.saldoEnMovi)
        assertFalse(hayQueActualizarElSaldo(p.resumen, cuentaNombrada(p)))
    }

    @Test
    fun `el saldo de Movi es al corte y sin anulados`() = testApplication {
        application { testModule() }
        transaction {
            // Anulado: no cuenta.
            movimiento("ev-anulado", skandia, "INCOME", 999_000L, dia(2026, 8, 10))
            VoidEvents.insert {
                it[id] = "void-1"; it[userId] = duenoId; it[originalEventId] = "ev-anulado"; it[timestamp] = dia(2026, 8, 11)
            }
            // Después del corte: tampoco.
            movimiento("ev-septiembre", skandia, "INCOME", 50_000L, dia(2026, 9, 3))
        }
        val p = propuestaDe(textoDeSkandia)
        assertEquals(12_000_000L, cuentaNombrada(p)?.saldoEnMovi)
    }

    @Test
    fun `con dos cuentas Skandia no se adivina - el duenio elige`() = testApplication {
        application { testModule() }
        transaction {
            Accounts.insert { it[id] = "acc-skandia-2"; it[userId] = duenoId; it[name] = "Skandia cesantías"; it[type] = "INVESTMENT" }
        }
        val p = propuestaDe(textoDeSkandia)
        assertNull(p.cuentaId)
        assertTrue(p.opciones.any { it.id == skandia } && p.opciones.any { it.id == "acc-skandia-2" })
    }

    @Test
    fun `sin conSaldos, el extracto sigue el camino de siempre`() = testApplication {
        application { testModule() }
        val deSiempre = LectorDeSiempre()
        LectorDePapeles.actual = deSiempre
        val res = subir(textoDeDavibank, conSaldos = false)
        // Lo de antes: el texto va al importador con IA, y sin movimientos es el 422 de siempre.
        assertEquals(1, deSiempre.leidos)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status, res.bodyAsText())
    }

    @Test
    fun `cada usuario ve solo sus cuentas`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeSkandia, uid = otroId)
        assertEquals(cuentaDelOtro, p.cuentaId)
        assertEquals(listOf(cuentaDelOtro), p.opciones.map { it.id })
    }

    // ── Actualizar el saldo ─────────────────────────────────────────────────

    @Test
    fun `Actualizar escribe un solo ajuste fechado al corte, y es idempotente`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeSkandia)
        // Leer el extracto no escribió nada.
        assertTrue(ajustesDe(skandia).isEmpty())

        val res = actualizar(skandia, p.resumen)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val r = json.decodeFromString(AdjustAccountBalanceResponse.serializer(), res.bodyAsText())
        val ajuste = assertNotNull(r.adjustmentEvent)
        assertEquals(345_679L, ajuste.amount)
        assertEquals(finDelCorte, ajuste.timestamp)
        assertEquals(ADJUSTMENT_CATEGORY, ajuste.category)
        assertEquals(12_345_679L, r.account.balance)

        // El mismo extracto otra vez: nada nuevo.
        val otraVez = actualizar(skandia, p.resumen)
        assertEquals(HttpStatusCode.OK, otraVez.status, otraVez.bodyAsText())
        assertNull(json.decodeFromString(AdjustAccountBalanceResponse.serializer(), otraVez.bodyAsText()).adjustmentEvent)
        assertEquals(1, ajustesDe(skandia).size)

        // Y la propuesta ya no ofrece nada.
        assertFalse(hayQueActualizarElSaldo(p.resumen, cuentaNombrada(propuestaDe(textoDeSkandia))))
    }

    @Test
    fun `un ajuste anulado del mismo extracto no se vuelve a escribir solo`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeDavibank)
        val ajuste = assertNotNull(
            json.decodeFromString(AdjustAccountBalanceResponse.serializer(), actualizar(afc, p.resumen).bodyAsText()).adjustmentEvent,
        )
        transaction {
            VoidEvents.insert { it[id] = "void-ajuste"; it[userId] = duenoId; it[originalEventId] = ajuste.id; it[timestamp] = dia(2026, 9, 5) }
        }
        val otraVez = actualizar(afc, p.resumen)
        assertEquals(HttpStatusCode.OK, otraVez.status, otraVez.bodyAsText())
        assertNull(json.decodeFromString(AdjustAccountBalanceResponse.serializer(), otraVez.bodyAsText()).adjustmentEvent)
        assertEquals(1, ajustesDe(afc).size)
    }

    @Test
    fun `una cuenta cuadrada despues del corte no se toca`() = testApplication {
        application { testModule() }
        transaction { movimiento("ev-cuadre-sept", skandia, "INCOME", 10_000L, dia(2026, 9, 5), categoria = ADJUSTMENT_CATEGORY) }
        val p = propuestaDe(textoDeSkandia)
        val cuenta = assertNotNull(cuentaNombrada(p))
        assertTrue(cuenta.cuadradaDespues)
        assertFalse(hayQueActualizarElSaldo(p.resumen, cuenta))

        assertEquals(HttpStatusCode.Conflict, actualizar(skandia, p.resumen).status)
        assertEquals(1, ajustesDe(skandia).size, "solo el cuadre de septiembre")
    }

    @Test
    fun `no se actualiza la cuenta de otro, ni una deuda, ni con cifras raras`() = testApplication {
        application { testModule() }
        val p = propuestaDe(textoDeSkandia)

        assertEquals(HttpStatusCode.NotFound, actualizar(cuentaDelOtro, p.resumen).status)
        assertEquals(HttpStatusCode.NotFound, actualizar(skandia, p.resumen, uid = otroId).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, actualizar(tarjeta, p.resumen).status)
        assertEquals(HttpStatusCode.BadRequest, actualizar(skandia, p.resumen.copy(saldo = -1L)).status)
        assertEquals(HttpStatusCode.BadRequest, actualizar(skandia, p.resumen.copy(fechaDeCorte = "ayer")).status)
        assertEquals(HttpStatusCode.BadRequest, actualizar(skandia, p.resumen.copy(fechaDeCorte = "2999-01-31")).status)
        assertTrue(ajustesDe(skandia).isEmpty())
        assertTrue(ajustesDe(cuentaDelOtro).isEmpty())
        assertTrue(ajustesDe(tarjeta).isEmpty())
    }

    // ── Compartir con Movi ──────────────────────────────────────────────────

    @Test
    fun `compartido como PDF, se reconoce sin IA y trae la propuesta`() = testApplication {
        application { testModule() }
        val doc = subirDocumento(pdfConLineas(textoDeSkandia.lines()))

        val res = client.post("/api/documents/$doc/leer?conSaldos=1") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val p = assertNotNull(json.decodeFromString(LecturaDelPapel.serializer(), res.bodyAsText()).extracto?.extractoDeSaldo)
        assertEquals(skandia, p.cuentaId)
        assertEquals(12_345_679L, p.resumen.saldo)

        // La segunda vez sale de la lectura guardada; y un cliente que no pide saldos no la recibe.
        val otraVez = client.post("/api/documents/$doc/leer?conSaldos=1") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, otraVez.status)
        val viejo = client.post("/api/documents/$doc/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.UnprocessableEntity, viejo.status)
    }

    // ── Los movimientos de una cuenta de Bancolombia, también sin IA ──────────

    /** La lista de «Movimientos: Cuentas» de la Sucursal Virtual (cifras y comercios inventados). */
    private val movimientosDeBancolombia = """
        Sucursal Virtual Personas
        Movimientos: Cuentas
        Cuenta de Ahorros
        Ahorros 012 - 345678 - 33
        Fecha Descripción Referencia Valor
        06 sept 2026 ABONO INTERESES AHORROS $ 15,08
        05 sept 2026 COMPRA EN  DROGUERIA -$ 31.895,00
        05 sept 2026 PAGO QR CAFETERIA 0091234567 -$ 12.500,00
        31 ago 2026 IMPTO GOBIERNO 4X1000 -$ 4.615,85
    """.trimIndent()

    @Test
    fun `la lista de movimientos de Bancolombia se concilia sin IA, con los cargos del banco aparte`() = testApplication {
        application { testModule() }
        // La droguería ya estaba anotada ese día: se empareja, no se propone de nuevo.
        transaction { movimiento("ev-drogueria", ahorros, "EXPENSE", 31_895L, dia(2026, 9, 5), categoria = "Salud") }

        val res = subir(movimientosDeBancolombia, conSaldos = false, nombre = "Ahorros_8133_movimientos_09_2026.txt")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val r = json.decodeFromString(StatementParseResult.serializer(), res.bodyAsText())
        assertEquals("Bancolombia", r.bankName)
        assertTrue("8133" in r.numerosDeCuenta, r.numerosDeCuenta.toString())
        assertEquals(listOf("ev-drogueria"), r.matches.map { it.existingEventId })
        assertEquals(3, r.newTransactions.size)
        // El 4x1000 y los intereses llegan marcados como lo que el banco cobra o abona solo.
        assertEquals(2, r.cargosDelBanco.size)
    }

    @Test
    fun `compartida como PDF, la lista de Bancolombia se reconoce sin preguntarle a la IA`() = testApplication {
        application { testModule() }
        val doc = subirDocumento(pdfConLineas(movimientosDeBancolombia.lines()))

        val res = client.post("/api/documents/$doc/leer") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val extracto = assertNotNull(json.decodeFromString(LecturaDelPapel.serializer(), res.bodyAsText()).extracto)
        assertEquals(4, extracto.newTransactions.size)
    }

    private suspend fun ApplicationTestBuilder.subirDocumento(contenido: ByteArray): String {
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tipo", "OTRO")
                        append("file", contenido, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"Fondo_09_2026.pdf\"")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    },
                ),
            )
        }
        val cuerpo = res.bodyAsText()
        assertTrue(res.status == HttpStatusCode.Created || res.status == HttpStatusCode.OK, cuerpo)
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]
    }

    private fun pdfConLineas(lineas: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val pagina = PDPage()
            doc.addPage(pagina)
            PDPageContentStream(doc, pagina).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                cs.newLineAtOffset(40f, 740f)
                lineas.forEach { linea ->
                    cs.showText(linea)
                    cs.newLineAtOffset(0f, -14f)
                }
                cs.endText()
            }
            doc.save(out)
        }
        return out.toByteArray()
    }
}
