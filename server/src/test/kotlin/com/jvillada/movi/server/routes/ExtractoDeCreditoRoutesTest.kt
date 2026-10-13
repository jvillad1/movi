package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
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
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.MotivoDelDesglose
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.ResumenDelExtractoDeCredito
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.tasaMensualDeUnaEA
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.math.round
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # El extracto de un crédito propone actualizar Movi, y el interés del extracto gana
 *
 * Los números son los del extracto real del Libre inversión ·9695 (corte 20-ago-2026): capital
 * $40.710.555, interés de la cuota $594.567 (Movi estimaba ~$364.000), seguro $156.800, tasa
 * 11,27 %. **Ninguna prueba llama a Anthropic**: el extracto de un crédito se lee sin IA, y donde
 * importa la costura se pone un lector falso (`LectorDePapeles.actual`).
 */
class ExtractoDeCreditoRoutesTest {

    private val testSecret = "test-secret-for-extracto-credito-min-32-chars"
    private val duenoId = "user-dueno-extracto-credito"
    private val otroId = "user-otro-extracto-credito"
    private val credito = "acc-libre-9695"
    private val ahorros = "acc-ahorros-8133"
    private val json = Json { ignoreUnknownKeys = true }
    private val lectorDeVerdad = LectorDePapeles.actual

    /** El extracto real, sin los datos personales (nombre, dirección, identificación). */
    private val textoDelExtracto = """
        ESTADO DE CUENTA
        PERSONA DE PRUEBA
        OBLIGACIÓN Nº:    290109695
        FECHA DE PAGO:    9/15/2026
        LÍNEA DE CRÉDITO PREAPROBADO LIBRE INVERSION
        CONCEPTO ABONO ANTERIOR CUOTA A PAGAR
        ABONO A CAPITAL 1,032,007.00 452,697.00
        INTERÉS CORRIENTE 136,857.00 594,567.00
        INTERÉS MORA .00
        SEGURO VIDA 35,200.00 156,800.00
        OTROS CONCEPTOS .00 .00
        TOTAL 1,204,064.00 1,204,064.00
        41,093,905.00
        SALDO DE CRÉDITO
        FECHA CORTE EXTRACTO 8/20/2026
        FECHA ÚLTIMO PAGO 7/26/2026
        SALDO DE CAPITAL 40,710,555.00
        TASA DE INTERÉS E.A. 11.27
        CUOTA NÚMERO 060
        TASA MORA A LA FECHA 29.63
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:extracto_credito;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Documents, Goals, Subscriptions, CardPaymentDismissals, Cards, Credits, SmsMessages,
                RecurringRules, VoidEvents, Events, StatementImports, Budgets, Accounts, Users, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                SmsMessages, Credits, Cards, CardPaymentDismissals, Subscriptions, Goals, CategoryPrefs, Documents,
            )
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@extracto.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
            Accounts.insert { it[id] = credito; it[userId] = duenoId; it[name] = "Libre inversión 9695"; it[type] = "LOAN" }
            Accounts.insert { it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros 8133"; it[type] = "SAVINGS" }
            // La deuda que Movi cree tener: $41.000.000, abierta antes del corte.
            Events.insert {
                it[id] = "ev-apertura"; it[userId] = duenoId; it[accountId] = credito; it[type] = "EXPENSE"
                it[amount] = 41_000_000L; it[currency] = "COP"; it[category] = "Saldo inicial"; it[description] = "Deuda inicial"
                it[timestamp] = dia(2026, 8, 1); it[reconciliationStatus] = "RECONCILED"
            }
            Credits.insert {
                it[accountId] = credito; it[userId] = duenoId; it[bank] = "Bancolombia"; it[principal] = 80_000_000L
                it[rateEa] = 11.27; it[termMonths] = 132; it[installment] = 1_204_064L; it[dayOfMonth] = 15
                it[startDate] = "2021-06-15"; it[insuranceMonthly] = 124_800L
            }
        }
    }

    @AfterTest
    fun tearDown() {
        LectorDePapeles.actual = lectorDeVerdad
    }

    private fun dia(anio: Int, mes: Int, d: Int): Long = appDateToEpochMillis(LocalDate.of(anio, mes, d)) + 12 * 3_600_000L

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@extracto.test")
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

    private suspend fun ApplicationTestBuilder.subir(conCreditos: Boolean, texto: String = textoDelExtracto): HttpResponse =
        client.post("/api/statements/upload" + if (conCreditos) "?conCreditos=1" else "") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", texto.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"CC_9695_09_2026.txt\"")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                    },
                ),
            )
        }

    private suspend fun ApplicationTestBuilder.aceptar(resumen: ResumenDelExtractoDeCredito, uid: String = duenoId): HttpResponse =
        client.post("/api/credits/$credito/extracto") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResumenDelExtractoDeCredito.serializer(), resumen))
        }

    private suspend fun ApplicationTestBuilder.pagarLaCuota(cuando: Long, tr: String): PagoDeCuotaResult {
        val res = client.post("/api/payments/installment") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"fromAccountId":"$ahorros","debtAccountId":"$credito","amount":1204064,"timestamp":$cuando,
                   "transferId":"$tr","fromEventId":"$tr-dinero","toEventId":"$tr-deuda"}""",
            )
        }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        return json.decodeFromString(PagoDeCuotaResult.serializer(), res.bodyAsText())
    }

    private fun estimadoSobre(saldo: Long): Long = round(saldo.toDouble() * tasaMensualDeUnaEA(11.27)).toLong()

    /** Un lector que no sabe leer nada más que lo que le dicen: si la ruta pide otra cosa, falla. */
    private class LectorFalso(private val resumen: ResumenDelExtractoDeCredito?) : LectorDePapeles {
        var leidos = 0
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = fail("no se clasifica un extracto subido")
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("el extracto de un crédito no va a Claude")
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura =
            fail("el extracto de un crédito no va a Claude")
        override suspend fun leerElExtractoDeUnCredito(texto: String): ResumenDelExtractoDeCredito? = resumen.also { leidos++ }
    }

    @Test
    fun `con el lector falso, la lectura arma la propuesta con lo que dice Movi al lado`() = testApplication {
        application { testModule() }
        val falso = LectorFalso(
            ResumenDelExtractoDeCredito(
                numero = "290109695", saldoCapital = 40_000_000L, interesDelPeriodo = 500_000L, seguro = 130_000L,
                tasaEa = 12.0, fechaDePago = "2026-09-15", fechaDeCorte = "2026-08-20",
            ),
        )
        LectorDePapeles.actual = falso
        val res = subir(conCreditos = true)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val p = assertNotNull(json.decodeFromString(StatementParseResult.serializer(), res.bodyAsText()).extractoDeCredito)
        assertEquals(1, falso.leidos)
        assertEquals(credito, p.cuentaId, "el crédito se encuentra por los últimos cuatro de la obligación")
        assertEquals(41_000_000L, p.saldoEnMovi)
        assertEquals(estimadoSobre(41_000_000L), p.interesEstimado)
        assertEquals(124_800L, p.seguroEnMovi)
        assertEquals(11.27, p.tasaEnMovi)
        assertEquals(500_000L, p.resumen.interesDelPeriodo)
    }

    @Test
    fun `el extracto real de Bancolombia se lee sin IA`() = testApplication {
        application { testModule() }
        val res = subir(conCreditos = true)
        val resultado = json.decodeFromString(StatementParseResult.serializer(), res.bodyAsText())
        assertTrue(resultado.newTransactions.isEmpty())
        val r = assertNotNull(resultado.extractoDeCredito).resumen
        assertEquals(40_710_555L, r.saldoCapital)
        assertEquals(594_567L, r.interesDelPeriodo)
        assertEquals(156_800L, r.seguro)
        assertEquals(11.27, r.tasaEa)
        assertEquals(1_204_064L, r.cuota)
        assertEquals("2026-09-15", r.fechaDePago)
        assertEquals("2026-08-20", r.fechaDeCorte)
    }

    @Test
    fun `sin conCreditos, un cliente viejo recibe el rechazo de siempre`() = testApplication {
        application { testModule() }
        val falso = LectorFalso(ResumenDelExtractoDeCredito(saldoCapital = 1L))
        LectorDePapeles.actual = falso
        val res = subir(conCreditos = false)
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.bodyAsText().contains("resumen de crédito"))
        assertEquals(0, falso.leidos)
    }

    @Test
    fun `leer no actualiza nada`() = testApplication {
        application { testModule() }
        subir(conCreditos = true)
        val ajustes = transaction { Events.selectAll().where { Events.category eq ADJUSTMENT_CATEGORY }.count() }
        assertEquals(0L, ajustes)
        assertEquals(124_800L, transaction { Credits.selectAll().single()[Credits.insuranceMonthly] })
    }

    @Test
    fun `actualizar escribe las condiciones y el ajuste al corte, y el interes del extracto gana en el pago`() = testApplication {
        application { testModule() }
        val resultado = json.decodeFromString(StatementParseResult.serializer(), subir(conCreditos = true).bodyAsText())
        val resumen = assertNotNull(resultado.extractoDeCredito).resumen

        val res = aceptar(resumen)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val resumenDelCredito = json.decodeFromString(CreditSummary.serializer(), res.bodyAsText())
        val terms = assertNotNull(resumenDelCredito.terms)
        assertEquals(156_800L, terms.insuranceMonthly)
        assertEquals(11.27, terms.rateEa)
        assertEquals(594_567L, terms.interesReal)
        assertEquals("2026-09", terms.interesRealCuota)
        assertEquals(40_710_555L, resumenDelCredito.account.balance, "la deuda quedó en el capital del extracto")
        val ajuste = assertNotNull(resumenDelCredito.adjustmentEvent)
        assertEquals(289_445L, ajuste.amount)
        assertEquals(ADJUSTMENT_CATEGORY, ajuste.category)
        assertEquals(appDateToEpochMillis(LocalDate.of(2026, 8, 21)) - 1, ajuste.timestamp, "fechado al final del corte")

        // La cuota del 15-sep usa el interés del extracto: el capital da EXACTO el del banco.
        val pago = pagarLaCuota(dia(2026, 9, 15), "tr-sep")
        val desglose = assertNotNull(pago.desglose)
        assertEquals(MotivoDelDesglose.INTERES_REAL, desglose.motivo)
        assertEquals(594_567L, desglose.interes)
        assertEquals(156_800L, desglose.seguro)
        assertEquals(452_697L, desglose.capital, "abono a capital del extracto: 452.697")

        // La de octubre ya no es la cuota del extracto: vuelve a estimar.
        val octubre = assertNotNull(pagarLaCuota(dia(2026, 10, 15), "tr-oct").desglose)
        assertEquals(MotivoDelDesglose.AMORTIZA, octubre.motivo)
        assertEquals(estimadoSobre(40_710_555L - 452_697L), octubre.interes)
    }

    @Test
    fun `actualizar dos veces no escribe dos ajustes`() = testApplication {
        application { testModule() }
        val resumen = assertNotNull(
            json.decodeFromString(StatementParseResult.serializer(), subir(conCreditos = true).bodyAsText()).extractoDeCredito,
        ).resumen
        aceptar(resumen)
        val segunda = json.decodeFromString(CreditSummary.serializer(), aceptar(resumen).bodyAsText())
        assertNull(segunda.adjustmentEvent)
        assertEquals(1L, transaction { Events.selectAll().where { Events.category eq ADJUSTMENT_CATEGORY }.count() })
    }

    @Test
    fun `otro duenio no actualiza un credito ajeno`() = testApplication {
        application { testModule() }
        val res = aceptar(ResumenDelExtractoDeCredito(saldoCapital = 1L, fechaDeCorte = "2026-08-20"), uid = otroId)
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(124_800L, transaction { Credits.selectAll().where { (Credits.accountId eq credito) and (Credits.userId eq duenoId) }.single()[Credits.insuranceMonthly] })
    }

    @Test
    fun `un cuerpo absurdo se rechaza`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.BadRequest, aceptar(ResumenDelExtractoDeCredito()).status)
        assertEquals(HttpStatusCode.BadRequest, aceptar(ResumenDelExtractoDeCredito(tasaEa = 250.0)).status)
        assertEquals(HttpStatusCode.BadRequest, aceptar(ResumenDelExtractoDeCredito(saldoCapital = -5L)).status)
    }
}
