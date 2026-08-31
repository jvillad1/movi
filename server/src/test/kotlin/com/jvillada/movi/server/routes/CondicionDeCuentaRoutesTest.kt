package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El camino de escritura de `Account.condicionadaA`, de punta a punta.**
 *
 * El campo nació muerto: se podía guardar solo en el `POST` de creación, y la cuenta que lo
 * motivó —la pensión voluntaria del dueño en Skandia, $106.000.000 que solo puede retirar para
 * vivienda— **ya existía en producción**. Ni desplegando el cálculo del Inicio podía marcarla: el
 * único camino era tocar la base de datos a mano. La regla del proyecto es que no hay ajustes que
 * solo se cambien tocando código.
 *
 * Por eso este test es de RUTA y no de la función pura: lo que se rompía no era el cálculo (ese
 * ya tenía tests) sino la ausencia de una puerta por donde el dato entrara. Un test sobre
 * `heroBalance` no habría notado nada.
 */
class CondicionDeCuentaRoutesTest {

    private val testSecret = "test-secret-for-condicion-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userId = "user-condicion"
    private val userEmail = "condicion@movi.test"
    private val otroUserId = "user-ajeno"
    private val otroEmail = "ajeno@movi.test"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:condicion_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Cards, Goals, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, Cards, Goals,
            )
            Users.insert {
                it[id] = userId
                it[email] = userEmail
                it[name] = "Dueño"
                it[passwordHash] = "hash"
            }
            Users.insert {
                it[id] = otroUserId
                it[email] = otroEmail
                it[name] = "Otro"
                it[passwordHash] = "hash"
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
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
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

    /** Una cuenta creada SIN condición — como todas las que ya existen en producción. */
    private suspend fun ApplicationTestBuilder.crearSkandia() =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"acc-skandia","name":"Skandia pensión voluntaria",
                   "type":"INVESTMENT","balance":0}""",
            )
        }

    private suspend fun ApplicationTestBuilder.marcar(
        id: String,
        json: String,
        uid: String = userId,
        email: String = userEmail,
    ) = client.put("/api/accounts/$id/conditioned-to") {
        header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid, email)}")
        header(HttpHeaders.ContentType, "application/json")
        setBody(json)
    }

    private suspend fun ApplicationTestBuilder.condicionEnLaLista(id: String): String? =
        Json.parseToJsonElement(
            client.get("/api/accounts") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonArray
            .first { it.jsonObject["id"]!!.jsonPrimitive.content == id }
            .jsonObject["condicionadaA"]
            ?.jsonPrimitive?.contentOrNullSafe()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content

    @Test
    fun `una cuenta que YA existe se puede marcar, y la marca vuelve en el GET`() = testApplication {
        wireApp()
        crearSkandia()
        assertNull(condicionEnLaLista("acc-skandia"), "nace sin condición, como las de producción")

        val respuesta = marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""")
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals(
            "Vivienda",
            Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["condicionadaA"]!!.jsonPrimitive.content,
        )

        // Y sobrevive a la lectura que usa el Inicio para calcular «Tu plata».
        assertEquals("Vivienda", condicionEnLaLista("acc-skandia"))
    }

    @Test
    fun `vacio quita la condicion y esa plata vuelve a Tu plata`() = testApplication {
        wireApp()
        crearSkandia()
        marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""")

        marcar("acc-skandia", """{"condicionadaA":""}""")
        assertNull(condicionEnLaLista("acc-skandia"))

        // Y `null` significa lo mismo que `""` en esta ruta: hace UNA sola cosa, así que quien la
        // llama siempre está hablando de la condición (a diferencia del `accountId` de una regla
        // recurrente, donde `null` es «no lo toques»).
        marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""")
        marcar("acc-skandia", """{}""")
        assertNull(condicionEnLaLista("acc-skandia"))
    }

    @Test
    fun `un texto de mas de 60 caracteres se recorta en vez de rechazarse`() = testApplication {
        wireApp()
        crearSkandia()
        val largo = "V".repeat(80)

        val respuesta = marcar("acc-skandia", """{"condicionadaA":"$largo"}""")

        assertEquals(HttpStatusCode.OK, respuesta.status)
        // El largo es una limitación de la columna, no una regla que el dueño tenga que aprender.
        assertEquals(60, condicionEnLaLista("acc-skandia")?.length)
    }

    @Test
    fun `solo espacios es lo mismo que vacio`() = testApplication {
        wireApp()
        crearSkandia()

        marcar("acc-skandia", """{"condicionadaA":"   "}""")

        assertNull(condicionEnLaLista("acc-skandia"), "un espacio no puede sacar plata del balance")
    }

    @Test
    fun `no se puede marcar la cuenta de otro`() = testApplication {
        wireApp()
        crearSkandia()

        val respuesta = marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""", otroUserId, otroEmail)

        assertEquals(HttpStatusCode.NotFound, respuesta.status)
        assertNull(condicionEnLaLista("acc-skandia"))
    }

    @Test
    fun `una cuenta inexistente responde 404`() = testApplication {
        wireApp()

        assertEquals(
            HttpStatusCode.NotFound,
            marcar("acc-fantasma", """{"condicionadaA":"Vivienda"}""").status,
        )
    }

    @Test
    fun `la respuesta trae el saldo derivado de los eventos, no la columna cruda`() = testApplication {
        wireApp()
        crearSkandia()
        // La apertura, como la crea el cliente de verdad.
        client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"","accountId":"acc-skandia","type":"INCOME","amount":106000000,
                   "category":"Saldo inicial","description":"Saldo inicial","timestamp":0}""",
            )
        }

        val cuerpo = Json.parseToJsonElement(
            marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""").bodyAsText(),
        ).jsonObject

        // El cliente espeja esta respuesta tal cual en su fila local: si trajera el
        // `accounts.balance` crudo (0), el teléfono mostraría un saldo falso sin red.
        assertEquals(106_000_000L, cuerpo["balance"]!!.jsonPrimitive.long)
        assertEquals("Vivienda", cuerpo["condicionadaA"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Movi AI recibe la cuenta MARCADA y no como plata disponible`() = testApplication {
        wireApp()
        crearSkandia()
        client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"","accountId":"acc-skandia","type":"INCOME","amount":106000000,
                   "category":"Saldo inicial","description":"Saldo inicial","timestamp":0}""",
            )
        }
        marcar("acc-skandia", """{"condicionadaA":"Vivienda"}""")

        val contexto = runBlocking { buildUserContext(userId) }

        // Sin la marca, el asistente lee «Skandia (INVESTMENT): saldo $106.000.000» y la suma al
        // contestar «¿cuánta plata disponible tengo?» — el mismo error que el Inicio dejó de
        // cometer, ahora en su boca.
        assertTrue(
            contexto.contains("NO disponible: solo se puede usar para Vivienda"),
            "el contexto tiene que decir que esa plata está condicionada:\n$contexto",
        )
    }

    @Test
    fun `una cuenta sin condicion se sigue listando pelada para Movi AI`() = testApplication {
        wireApp()
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"acc-nequi","name":"Nequi","type":"SAVINGS","balance":0}""")
        }

        val contexto = runBlocking { buildUserContext(userId) }

        assertTrue(contexto.contains("- Nequi (SAVINGS): saldo"), contexto)
        assertTrue(!contexto.contains("NO disponible"), contexto)
    }
}
