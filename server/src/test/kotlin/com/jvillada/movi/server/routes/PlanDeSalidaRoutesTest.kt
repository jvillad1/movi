package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.ai.LlamadaDeHerramienta
import com.jvillada.movi.server.ai.SIMULAR_ABONO
import com.jvillada.movi.server.ai.ejecutarHerramienta
import com.jvillada.movi.server.ai.textoDelPlanDeSalida
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.planDeSalida
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El plan de salida de deudas en el server (Ola 4)
 *
 * La cuenta es de `:core` (`PlanDeSalidaTest`). Acá: la tasa nueva de la tarjeta se guarda, sobrevive
 * a un cliente que no la conoce y rechaza un dedo que se fue; la herramienta de Movi AI usa las deudas
 * del dueño y de nadie más; y su texto trae el orden, el ahorro y lo que falta. Sin Anthropic.
 */
class PlanDeSalidaRoutesTest {

    private val testSecret = "test-secret-for-plan-de-salida-min-32-chars"
    private val duenoId = "user-dueno-salida"
    private val otroId = "user-otro-salida"
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:plan_de_salida_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Cards, Credits, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Credits, Cards)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@salida.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
            // La Master Black del dueño: $2.000.000 de deuda, términos sin tasa (como las de producción).
            Accounts.insert { it[id] = "tc_dueno"; it[userId] = duenoId; it[name] = "Master Black"; it[type] = "CREDIT_CARD"; it[balance] = 0L }
            Events.insert {
                it[id] = "ap_tc"; it[userId] = duenoId; it[accountId] = "tc_dueno"; it[type] = "EXPENSE"; it[amount] = 2_000_000L
                it[currency] = "COP"; it[category] = OPENING_CATEGORY; it[description] = "Saldo inicial"; it[timestamp] = 1_780_000_000_000L
                it[reconciliationStatus] = "RECONCILED"
            }
            Cards.insert {
                it[accountId] = "tc_dueno"; it[userId] = duenoId; it[bank] = "Bancolombia"; it[paymentDay] = 2
                it[pagoMinimo] = 150_000L
            }
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@salida.test")
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

    private suspend fun ApplicationTestBuilder.ponerTerminos(uid: String, cuerpo: String) =
        client.put("/api/cards/tc_dueno") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.tarjetas(uid: String): List<CardSummary> {
        val res = client.get("/api/cards") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(CardSummary.serializer()), res.bodyAsText())
    }

    @Test
    fun `la tasa de la tarjeta se guarda y un cliente que no la conoce no la borra`() = testApplication {
        application { testModule() }
        val conTasa = ponerTerminos(duenoId, """{"accountId":"","bank":"Bancolombia","paymentDay":2,"pagoMinimo":150000,"tasaEa":29.6}""")
        assertEquals(HttpStatusCode.OK, conTasa.status, conTasa.bodyAsText())
        assertEquals(29.6, tarjetas(duenoId).single().terms?.tasaEa)

        // Un APK anterior a la Ola 4 cambia el día de pago sin mandar la clave: la tasa se queda.
        ponerTerminos(duenoId, """{"accountId":"","bank":"Bancolombia","paymentDay":5,"pagoMinimo":150000}""")
        val despues = tarjetas(duenoId).single().terms!!
        assertEquals(5, despues.paymentDay)
        assertEquals(29.6, despues.tasaEa)

        // Borrarla a propósito sí la borra.
        ponerTerminos(duenoId, """{"accountId":"","bank":"Bancolombia","paymentDay":5,"pagoMinimo":150000,"tasaEa":null}""")
        assertNull(tarjetas(duenoId).single().terms!!.tasaEa)
    }

    @Test
    fun `una tasa imposible se rechaza`() = testApplication {
        application { testModule() }
        val res = ponerTerminos(duenoId, """{"accountId":"","bank":"Bancolombia","paymentDay":2,"tasaEa":296}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `el otro no puede ponerle tasa a la tarjeta del duenio`() = testApplication {
        application { testModule() }
        val res = ponerTerminos(otroId, """{"accountId":"","bank":"X","paymentDay":2,"tasaEa":10}""")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `movi ai simula con las deudas del duenio y la tarjeta sin tasa queda afuera`() {
        val delDueno = runBlocking { ejecutarHerramienta(duenoId, LlamadaDeHerramienta("t1", SIMULAR_ABONO, mapOf("abono_mensual" to "500000"))) }
        assertTrue("Master Black (saldo 2000000 COP): falta la tasa" in delDueno, delDueno)
        assertTrue("no una recomendación financiera" in delDueno, delDueno)
        val delOtro = runBlocking { ejecutarHerramienta(otroId, LlamadaDeHerramienta("t2", SIMULAR_ABONO, emptyMap())) }
        assertFalse("Master Black" in delOtro, "las deudas del dueño no se le cuelan al otro: $delOtro")
    }

    @Test
    fun `con la tasa cargada la tarjeta entra al calculo`() = testApplication {
        application { testModule() }
        ponerTerminos(duenoId, """{"accountId":"","bank":"Bancolombia","paymentDay":2,"pagoMinimo":150000,"tasaEa":29.6}""")
        val texto = runBlocking { ejecutarHerramienta(duenoId, LlamadaDeHerramienta("t1", SIMULAR_ABONO, mapOf("abono_mensual" to "500000"))) }
        assertTrue("1. Master Black: saldo 2000000, tasa 29.6 % EA" in texto, texto)
        assertTrue("Ahorra" in texto, texto)
    }

    @Test
    fun `el texto trae el orden, el ahorro, lo que falta y lo ajeno`() {
        val deudas = listOf(
            DeudaParaSalir("c1", "Crediágil 3090", TipoDeDeuda.CREDITO, 507_553L, 29.64, cuota = 60_000L),
            DeudaParaSalir("c2", "Libre inversión 9695", TipoDeDeuda.CREDITO, 40_104_518L, 11.27, cuota = 1_204_064L, noAmortiza = 124_800L),
            DeudaParaSalir("t1", "AMEX", TipoDeDeuda.TARJETA, 19_000_000L, null, cuota = null),
            DeudaParaSalir("c3", "Libranza 4818", TipoDeDeuda.CREDITO, 262_386_162L, 18.01, cuota = 6_040_259L, quienLaPaga = "tu nómina"),
        )
        val plan = planDeSalida(deudas, 500_000L, EstrategiaDeSalida.AVALANCHA)
        val otra = planDeSalida(deudas, 500_000L, EstrategiaDeSalida.BOLA_DE_NIEVE)
        val texto = textoDelPlanDeSalida(plan, otra, PeriodoFinanciero(2026, 10))
        assertTrue(texto.indexOf("1. Crediágil 3090") < texto.indexOf("2. Libre inversión 9695"), texto)
        assertTrue("Interés total que ahorra el abono" in texto, texto)
        assertTrue("AMEX (saldo 19000000 COP): falta la tasa" in texto, texto)
        assertTrue("Libranza 4818 (saldo 262386162): la paga tu nómina" in texto, texto)
        assertTrue("bola de nieve (menor saldo primero), el orden sería Crediágil 3090 → Libre inversión 9695" in texto, texto)
    }
}
