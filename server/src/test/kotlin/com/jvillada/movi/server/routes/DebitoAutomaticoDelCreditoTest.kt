package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DEBITO_CON_LIBRANZA
import com.jvillada.movi.shared.model.DEBITO_CON_TERCERO
import com.jvillada.movi.shared.model.DEBITO_DESDE_UNA_DEUDA
import com.jvillada.movi.shared.model.DEBITO_SIN_CUENTA
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlin.test.assertNull

/**
 * # «El banco la cobra solo»: el dato del crédito
 *
 * `CreditTerms.debitoAutomaticoDesde` dice de qué cuenta debita el banco la cuota. Lo que se fija:
 * que se guarda y se lee; que un APK que no conoce el campo **no lo borra** al editar otra cosa
 * (el mismo agujero que ya cerraron `paidBy` y el seguro); que borrarlo sí lo borra; y que no se
 * puede apuntar a una cuenta ajena, a una deuda, ni convivir con la libranza o con «la paga otro».
 */
class DebitoAutomaticoDelCreditoTest {

    private val testSecret = "test-secret-for-debito-automatico-min-32-chars"
    private val duenoId = "user-dueno-debito"
    private val otroId = "user-otro-debito"
    private val json = Json { ignoreUnknownKeys = true }

    private val credito = "acc-loan-9695"
    private val ahorros = "acc-ahorros"
    private val tarjeta = "acc-tarjeta"
    private val ahorrosDelOtro = "acc-ahorros-otro"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:debito_automatico_credito;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Credits, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Credits)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@debito.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            fun cuenta(id: String, uid: String, nombre: String, tipo: String) = Accounts.insert {
                it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = tipo; it[currency] = "COP"
            }
            cuenta(credito, duenoId, "Libre inversión 9695", "LOAN")
            cuenta(ahorros, duenoId, "Bancolombia Ahorros", "SAVINGS")
            cuenta(tarjeta, duenoId, "Master Black", "CREDIT_CARD")
            cuenta(ahorrosDelOtro, otroId, "Ahorros del otro", "SAVINGS")
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@debito.test")
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

    /** El cuerpo de un PUT, con lo que haga falta agregado al final. Sin extras = un APK viejo. */
    private fun terminos(vararg extra: String) =
        """{"accountId":"$credito","bank":"Bancolombia","principal":40000000,"rateEa":24.5,"termMonths":60,
            "installment":1204064,"dayOfMonth":15,"startDate":"2025-03-15"
            ${if (extra.isEmpty()) "" else "," + extra.joinToString(",")}}"""

    private suspend fun ApplicationTestBuilder.put(cuerpo: String, uid: String = duenoId) =
        client.put("/api/credits/$credito") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.debitoGuardado(): String? {
        val res = client.get("/api/credits") { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(CreditSummary.serializer()), res.bodyAsText())
            .single { it.account.id == credito }.terms?.debitoAutomaticoDesde
    }

    @Test
    fun `se guarda y se lee`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.OK, put(terminos("\"debitoAutomaticoDesde\":\"$ahorros\"")).status)
        assertEquals(ahorros, debitoGuardado())
    }

    @Test
    fun `un APK viejo que edita la nota no borra el debito`() = testApplication {
        application { testModule() }
        put(terminos("\"debitoAutomaticoDesde\":\"$ahorros\""))
        // El APK instalado no conoce el campo: su cuerpo no trae la clave.
        assertEquals(HttpStatusCode.OK, put(terminos("\"notes\":\"cambié la nota\"")).status)
        assertEquals(ahorros, debitoGuardado(), "editar otra cosa desde un cliente viejo apagó el débito")
    }

    @Test
    fun `quitarlo desde la hoja si lo borra`() = testApplication {
        application { testModule() }
        put(terminos("\"debitoAutomaticoDesde\":\"$ahorros\""))
        assertEquals(HttpStatusCode.OK, put(terminos("\"debitoAutomaticoDesde\":null")).status)
        assertNull(debitoGuardado())
    }

    @Test
    fun `una cuenta ajena es como una que no existe`() = testApplication {
        application { testModule() }
        val res = put(terminos("\"debitoAutomaticoDesde\":\"$ahorrosDelOtro\""))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(DEBITO_SIN_CUENTA, res.bodyAsText())
        assertNull(debitoGuardado())
    }

    @Test
    fun `no se debita de otra deuda`() = testApplication {
        application { testModule() }
        val res = put(terminos("\"debitoAutomaticoDesde\":\"$tarjeta\""))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(DEBITO_DESDE_UNA_DEUDA, res.bodyAsText())
    }

    @Test
    fun `no convive con la libranza ni con la paga otro`() = testApplication {
        application { testModule() }
        val conLibranza = put(terminos("\"debitoAutomaticoDesde\":\"$ahorros\"", "\"payrollDeduction\":true"))
        assertEquals(HttpStatusCode.BadRequest, conLibranza.status)
        assertEquals(DEBITO_CON_LIBRANZA, conLibranza.bodyAsText())
        val conTercero = put(terminos("\"debitoAutomaticoDesde\":\"$ahorros\"", "\"paidBy\":\"Skandia\""))
        assertEquals(HttpStatusCode.BadRequest, conTercero.status)
        assertEquals(DEBITO_CON_TERCERO, conTercero.bodyAsText())
        assertNull(debitoGuardado())
    }

    @Test
    fun `el alta de un credito tambien lo valida`() = testApplication {
        application { testModule() }
        val malo = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Crediágil 3090","initialDebt":500000,"terms":${terminos("\"debitoAutomaticoDesde\":\"$tarjeta\"")}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, malo.status)
        val bueno = client.post("/api/credits") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Crediágil 3090","initialDebt":500000,"terms":${terminos("\"debitoAutomaticoDesde\":\"$ahorros\"")}}""")
        }
        assertEquals(HttpStatusCode.Created, bueno.status)
        assertEquals(ahorros, json.decodeFromString(CreditSummary.serializer(), bueno.bodyAsText()).terms?.debitoAutomaticoDesde)
    }
}
