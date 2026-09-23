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
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DashboardSummary
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Los bienes, de punta a punta en el server**: crearlos, actualizarles el avalúo, y que sumen al
 * patrimonio sin tocar «Tu plata» — con los números del dueño al 23-sep.
 *
 * Lo que estas pruebas cuidan, en orden de lo que costaría romperlo:
 *
 * 1. Que el cable de un bien diga `balance = 0` (y el valor solo en `bien`): es lo único que
 *    impide que el APK viejo del dueño muestre «Tu plata $1.412 millones». Ver `Account.bien`.
 * 2. Que el resumen del Inicio y el contexto de Movi AI cuenten la casa en el patrimonio y en
 *    ningún otro lado, con la MISMA regla (`patrimonioDe`).
 * 3. Que las rutas no dejen escribir un bien roto ni deshacerlo por la puerta de atrás (el
 *    reenvío del teléfono, un cuadre).
 */
class BienesRoutesTest {

    private val testSecret = "test-secret-for-bienes-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userId = "user-bienes"
    private val userEmail = "bienes@movi.test"
    private val otroUserId = "user-otro-bienes"
    private val otroEmail = "otro-bienes@movi.test"

    private val json = Json { ignoreUnknownKeys = true }

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages,
        Credits, CardPaymentDismissals, Cards, Goals, Documents, RecurringOccurrences,
        OccurrenceRejections, Subscriptions, CategoryPrefs, PushSubscriptions,
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:bienes_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            listOf(userId to userEmail, otroUserId to otroEmail).forEach { (id, mail) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = mail
                    it[name] = id
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun tokenFor(uid: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", uid)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private val token get() = tokenFor(userId, userEmail)

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, tok: String = token): HttpResponse =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $tok")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.putJson(path: String, body: String, tok: String = token): HttpResponse =
        client.put(path) {
            header(HttpHeaders.Authorization, "Bearer $tok")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.cuentas(): List<Account> =
        json.decodeFromString(
            ListSerializer(Account.serializer()),
            client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.resumen(): DashboardSummary =
        json.decodeFromString(
            DashboardSummary.serializer(),
            client.get("/api/dashboard/summary") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.cuenta(id: String, nombre: String, tipo: String, condicion: String? = null) =
        postJson(
            "/api/accounts",
            """{"id":"$id","name":"$nombre","type":"$tipo","balance":0${condicion?.let { ",\"condicionadaA\":\"$it\"" } ?: ""}}""",
        )

    /** Un movimiento viejo (1-ene-2026), anterior a cualquier período: es «lo que tenías». */
    private suspend fun ApplicationTestBuilder.movimiento(cuenta: String, tipo: String, monto: Long, categoria: String = "Saldo inicial") =
        postJson(
            "/api/events",
            """{"id":"","accountId":"$cuenta","type":"$tipo","amount":$monto,
               "category":"$categoria","description":"$categoria","timestamp":1767225600000}""",
        )

    private suspend fun ApplicationTestBuilder.crearCasa(deudaId: String? = "acc-1254"): HttpResponse =
        postJson(
            "/api/accounts",
            """{"id":"acc-casa","name":"Casa Almendros","type":"SAVINGS","balance":999,
               "condicionadaA":"Vivienda",
               "bien":{"clase":"INMUEBLE","valor":1411903920,"valorAl":"2026-08-28"
               ${deudaId?.let { ",\"deudaId\":\"$it\"" } ?: ""}}}""",
        )

    /** Las cuentas del dueño al 23-sep: $558.350 de plata, $116,2M condicionados y $2.191M de deuda. */
    private suspend fun ApplicationTestBuilder.cargarAlDueno() {
        cuenta("acc-nu", "Nu", "SAVINGS")
        movimiento("acc-nu", "INCOME", 558_350L)
        cuenta("acc-skandia", "Skandia", "INVESTMENT", condicion = "Vivienda")
        movimiento("acc-skandia", "INCOME", 116_200_000L)
        cuenta("acc-1254", "Hipoteca 1254", "LOAN")
        movimiento("acc-1254", "EXPENSE", 1_030_600_000L)
        cuenta("acc-resto", "Otras deudas", "LOAN")
        movimiento("acc-resto", "EXPENSE", 1_160_400_000L)
    }

    @Test
    fun `la casa se crea como bien y viaja en cero, con el valor solo adentro del bien`() = testApplication {
        wireApp()
        cargarAlDueno()

        val creada = crearCasa()
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())

        val casa = cuentas().single { it.id == "acc-casa" }
        // La forma la fuerza el server, aunque el cliente mande otra: INVESTMENT, sin saldo, sin condición.
        assertEquals("INVESTMENT", casa.type.name)
        assertEquals(0L, casa.balance)
        assertNull(casa.estimatedTotalCop)
        assertNull(casa.condicionadaA)
        val bien = assertNotNull(casa.bien)
        assertEquals("INMUEBLE", bien.clase)
        assertEquals(1_411_903_920L, bien.valor)
        assertEquals("2026-08-28", bien.valorAl)
        assertEquals("acc-1254", bien.deudaId)
    }

    @Test
    fun `el resumen del Inicio suma la casa al patrimonio y no a Tu plata`() = testApplication {
        wireApp()
        cargarAlDueno()
        val antes = resumen()
        crearCasa()
        // Un movimiento contra la casa (el APK viejo la ofrece como una inversión) no la vuelve plata.
        movimiento("acc-casa", "INCOME", 7_000_000L, "Otros")

        val despues = resumen()
        val p = assertNotNull(despues.patrimonio)

        assertEquals(558_350L, p.tuPlata)
        assertEquals(116_200_000L, p.condicionado)
        assertEquals("Vivienda", p.condicionadoA)
        assertEquals(1_411_903_920L, p.bienes)
        assertEquals(2_191_000_000L, p.deudas)
        assertEquals(-662_337_730L, p.neto)
        assertEquals(-2_074_241_650L, assertNotNull(antes.patrimonio).neto, "la media foto de antes")
        // «Lo que tenías al empezar el período» tampoco ve la casa (ni su movimiento).
        assertEquals(antes.saldoTuPlataAlInicio, despues.saldoTuPlataAlInicio)
        assertEquals(558_350L, despues.saldoTuPlataAlInicio)
        // Y la lista de cuentas dice lo mismo: la casa sigue en cero.
        assertEquals(0L, cuentas().single { it.id == "acc-casa" }.balance)
    }

    @Test
    fun `actualizar el avaluo cambia el valor y sella la edicion`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        val r = putJson(
            "/api/accounts/acc-casa/bien",
            """{"bien":{"clase":"INMUEBLE","valor":1500000000,"valorAl":"2027-08-28"}}""",
        )
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val respuesta = json.decodeFromString(Account.serializer(), r.bodyAsText())
        assertEquals(1_500_000_000L, respuesta.bien?.valor)
        assertEquals(0L, respuesta.balance)
        assertNotNull(respuesta.lastEditedAt)
        // Sin `deudaId` en el pedido la deuda se quita: viaja el bien entero, no un parche.
        assertNull(respuesta.bien?.deudaId)
        assertEquals(1_500_000_000L, assertNotNull(resumen().patrimonio).bienes)
    }

    @Test
    fun `lo que no se puede guardar se rechaza con la misma frase que la hoja`() = testApplication {
        wireApp()
        cargarAlDueno()

        val sinValor = postJson(
            "/api/accounts",
            """{"id":"acc-x","name":"Casa","type":"INVESTMENT","balance":0,"bien":{"clase":"INMUEBLE","valor":0}}""",
        )
        assertEquals(HttpStatusCode.BadRequest, sinValor.status)
        assertEquals("Escribe cuánto vale", sinValor.bodyAsText())

        val deudaQueNoEsDeuda = crearCasa(deudaId = "acc-nu")
        assertEquals(HttpStatusCode.BadRequest, deudaQueNoEsDeuda.status)
        assertTrue(cuentas().none { it.id == "acc-casa" }, "no se escribió nada")

        crearCasa()
        val fechaRota = putJson("/api/accounts/acc-casa/bien", """{"bien":{"clase":"INMUEBLE","valor":1,"valorAl":"28/08"}}""")
        assertEquals(HttpStatusCode.BadRequest, fechaRota.status)
    }

    @Test
    fun `la ruta del bien solo toca bienes, y solo los tuyos`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        val noEsBien = putJson("/api/accounts/acc-nu/bien", """{"bien":{"clase":"OTRO","valor":10}}""")
        assertEquals(HttpStatusCode.Conflict, noEsBien.status, "convertir plata en bien escondería su saldo")

        val ajena = putJson(
            "/api/accounts/acc-casa/bien",
            """{"bien":{"clase":"OTRO","valor":10}}""",
            tok = tokenFor(otroUserId, otroEmail),
        )
        assertEquals(HttpStatusCode.NotFound, ajena.status)
        assertEquals(1_411_903_920L, cuentas().single { it.id == "acc-casa" }.bien?.valor)
    }

    @Test
    fun `el reenvio de un APK viejo sin la clave no deshace el bien`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        // Lo que manda un cliente que no sabe de bienes: la misma cuenta, sin `bien`.
        val reenvio = postJson("/api/accounts", """{"id":"acc-casa","name":"Casa Almendros","type":"INVESTMENT","balance":0}""")
        assertEquals(HttpStatusCode.OK, reenvio.status)

        assertEquals(1_411_903_920L, cuentas().single { it.id == "acc-casa" }.bien?.valor)
    }

    @Test
    fun `un bien no se cuadra con un movimiento`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        val cuadre = postJson("/api/accounts/acc-casa/balance-adjustment", """{"targetBalance":1411903920}""")

        assertEquals(HttpStatusCode.UnprocessableEntity, cuadre.status)
        assertEquals(0L, cuentas().single { it.id == "acc-casa" }.balance)
    }

    @Test
    fun `borrar la deuda suelta el bien, que sigue valiendo lo mismo`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        val borrada = client.delete("/api/accounts/acc-1254") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NoContent, borrada.status)

        val casa = cuentas().single { it.id == "acc-casa" }
        assertNull(casa.bien?.deudaId)
        assertEquals(1_411_903_920L, casa.bien?.valor)
    }

    @Test
    fun `Movi AI recibe la casa como bien y el patrimonio ya partido`() = testApplication {
        wireApp()
        cargarAlDueno()
        crearCasa()

        val contexto = runBlocking { buildUserContext(userId) }

        assertTrue(
            "- Casa Almendros (BIEN · Inmueble): vale \$1411903920 según el avalúo del 2026-08-28 — es un bien, NO plata disponible" in contexto,
            contexto,
        )
        assertTrue("lo financia «Hipoteca 1254», que debe \$1030600000: lo suyo de verdad son \$381303920" in contexto, contexto)
        assertTrue("== Patrimonio ==" in contexto, contexto)
        assertTrue("- Tu plata (disponible para usar): \$558350" in contexto, contexto)
        assertTrue("- Bienes (inmuebles, vehículos; no es plata): \$1411903920" in contexto, contexto)
        assertTrue("- Deudas: \$2191000000" in contexto, contexto)
        assertTrue("- Patrimonio neto (tu plata + plata con destino + bienes − deudas): \$-662337730" in contexto, contexto)
        // La casa no aparece como una inversión con saldo que el modelo pueda sumar a la plata.
        assertTrue("Casa Almendros (INVESTMENT)" !in contexto, contexto)
    }
}
