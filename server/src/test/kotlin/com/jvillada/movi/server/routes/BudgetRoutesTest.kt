package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.delete
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

/**
 * F17: `PUT /api/budgets/{category}/rename` — la categoría es la PK de `budgets`
 * (userId+category), así que renombrar es borrar e insertar en una transacción, conservando
 * el límite. Mismo arnés que AccountRoutesTest/FinanceRoutesTest: H2 en memoria (compat
 * PostgreSQL), JWT local, cadena completa de plugins vía `wireApp()`.
 */
class BudgetRoutesTest {

    private val testSecret = "test-secret-for-budget-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-budgets"
    private val userEmail = "a@budgets.test"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:budget_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals,
            )

            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    private fun tokenFor(userId: String, email: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    private val token get() = tokenFor(userId, userEmail)

    private fun Application.testModule() {
        configureSerialization()

        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
            }
        }

        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    private suspend fun ApplicationTestBuilder.postBudget(category: String, limit: Long) =
        client.post("/api/budgets") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category","monthlyLimit":$limit}""")
        }

    /**
     * Renombrar por el camino de hoy: los dos nombres en el CUERPO. La ruta vieja con el nombre
     * pegado sigue viva por el APK instalado y tiene su propia prueba más abajo.
     */
    private suspend fun ApplicationTestBuilder.rename(category: String, newCategory: String, asToken: String = token) =
        client.post("/api/budgets/rename") {
            header(HttpHeaders.Authorization, "Bearer $asToken")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category","newCategory":"$newCategory"}""")
        }

    private suspend fun ApplicationTestBuilder.editBudget(category: String, limit: Long) =
        client.put("/api/budgets") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category","monthlyLimit":$limit}""")
        }

    private suspend fun ApplicationTestBuilder.removeBudget(category: String) =
        client.post("/api/budgets/delete") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"$category"}""")
        }

    private suspend fun ApplicationTestBuilder.budgets(): List<kotlinx.serialization.json.JsonObject> =
        Json.parseToJsonElement(
            client.get("/api/budgets") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonArray.map { it.jsonObject }

    /** Crear y editar un límite siguen la misma regla que el monto de un movimiento. */
    @Test
    fun `crear o editar con limite en cero, negativo o sin categoria es 400`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, postBudget("Mercado", 0).status)
        assertEquals(HttpStatusCode.BadRequest, postBudget("Mercado", -1).status)
        assertEquals(HttpStatusCode.BadRequest, postBudget("   ", 500_000).status)
        assertEquals(0, budgets().size)

        assertEquals(HttpStatusCode.Created, postBudget("  Mercado  ", 500_000).status)
        assertEquals("Mercado", budgets()[0]["category"]!!.jsonPrimitive.content, "se guarda recortada, como al renombrar")
        val put = client.put("/api/budgets/Mercado") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"Mercado","monthlyLimit":0}""")
        }
        assertEquals(HttpStatusCode.BadRequest, put.status)
        assertEquals(500_000L, budgets()[0]["monthlyLimit"]!!.jsonPrimitive.long)
    }

    @Test
    fun `rename borra el nombre viejo, crea el nuevo y conserva el limite`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)

        val res = rename("Mercado", "Supermercado")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("Supermercado", body["category"]!!.jsonPrimitive.content)
        assertEquals(500_000L, body["monthlyLimit"]!!.jsonPrimitive.long)

        val list = budgets()
        assertEquals(1, list.size, "no debe quedar la fila vieja ni sobrar ninguna")
        assertEquals("Supermercado", list[0]["category"]!!.jsonPrimitive.content)
        assertEquals(500_000L, list[0]["monthlyLimit"]!!.jsonPrimitive.long)
    }

    @Test
    fun `rename de una categoria que no existe es 404`() = testApplication {
        wireApp()
        val res = rename("NoExiste", "Otra")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `rename a un nombre que ya esta en uso es 409 y no toca ninguna de las dos filas`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)
        postBudget("Salud", 200_000)

        val res = rename("Mercado", "Salud")
        assertEquals(HttpStatusCode.Conflict, res.status)

        val list = budgets().associateBy { it["category"]!!.jsonPrimitive.content }
        assertEquals(2, list.size, "las dos filas originales se quedan intactas")
        assertEquals(500_000L, list["Mercado"]!!["monthlyLimit"]!!.jsonPrimitive.long)
        assertEquals(200_000L, list["Salud"]!!["monthlyLimit"]!!.jsonPrimitive.long)
    }

    @Test
    fun `rename al mismo nombre es un no-op valido`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)
        val res = rename("Mercado", "Mercado")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1, budgets().size)
    }

    @Test
    fun `rename no toca los presupuestos de otro usuario`() = testApplication {
        wireApp()
        val otherUserId = "user-b-budgets"
        val otherEmail = "b@budgets.test"
        transaction {
            Users.insert {
                it[id]           = otherUserId
                it[email]        = otherEmail
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
        postBudget("Mercado", 500_000)

        val res = rename("Mercado", "Supermercado", asToken = tokenFor(otherUserId, otherEmail))
        assertEquals(HttpStatusCode.NotFound, res.status, "B no tiene presupuesto \"Mercado\" — no puede renombrar el de A")

        val list = budgets()
        assertEquals(1, list.size)
        assertEquals("Mercado", list[0]["category"]!!.jsonPrimitive.content, "el de A sigue con su nombre viejo")
    }

    // ── El nombre viaja en el cuerpo ──────────────────────────────────────────
    // Cuando el nombre iba en la ruta, «Luz/Agua» se creaba bien (el POST siempre lo mandó en
    // el cuerpo) y después era 404 para siempre: son dos segmentos y `{category}` hace coincidir
    // uno solo. «50%» directamente no sobrevivía al decode del path.

    @Test
    fun `un presupuesto con barra en el nombre se edita, se renombra y se borra`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, postBudget("Luz/Agua", 300_000).status)

        assertEquals(HttpStatusCode.OK, editBudget("Luz/Agua", 450_000).status)
        assertEquals(450_000L, budgets().single()["monthlyLimit"]!!.jsonPrimitive.long)

        assertEquals(HttpStatusCode.OK, rename("Luz/Agua", "Servicios/Casa").status)
        assertEquals("Servicios/Casa", budgets().single()["category"]!!.jsonPrimitive.content)
        assertEquals(450_000L, budgets().single()["monthlyLimit"]!!.jsonPrimitive.long, "renombrar conserva el límite")

        assertEquals(HttpStatusCode.NoContent, removeBudget("Servicios/Casa").status)
        assertEquals(0, budgets().size)
    }

    @Test
    fun `un presupuesto con porcentaje en el nombre se edita, se renombra y se borra`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, postBudget("Ahorro 50%", 200_000).status)

        assertEquals(HttpStatusCode.OK, editBudget("Ahorro 50%", 250_000).status)
        assertEquals(250_000L, budgets().single()["monthlyLimit"]!!.jsonPrimitive.long)

        assertEquals(HttpStatusCode.OK, rename("Ahorro 50%", "Ahorro 100%").status)
        assertEquals("Ahorro 100%", budgets().single()["category"]!!.jsonPrimitive.content)

        assertEquals(HttpStatusCode.NoContent, removeBudget("Ahorro 100%").status)
        assertEquals(0, budgets().size)
    }

    @Test
    fun `editar o borrar un presupuesto que no existe sigue siendo 404`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.NotFound, editBudget("Luz/Agua", 1_000).status)
        assertEquals(HttpStatusCode.NotFound, removeBudget("Luz/Agua").status)
        assertEquals(HttpStatusCode.NotFound, rename("Luz/Agua", "Otra").status)
    }

    @Test
    fun `editar sin categoria en el cuerpo es 400 y con limite en cero tambien`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)
        assertEquals(HttpStatusCode.BadRequest, editBudget("   ", 1_000).status)
        assertEquals(HttpStatusCode.BadRequest, removeBudget("   ").status)
        assertEquals(HttpStatusCode.BadRequest, editBudget("Mercado", 0).status)
        assertEquals(500_000L, budgets().single()["monthlyLimit"]!!.jsonPrimitive.long)
    }

    /**
     * El APK que el dueño tiene instalado manda el nombre en la ruta. Esas tres rutas se quedan
     * y hacen lo mismo — con nombres sin «/» ni «%», que es todo lo que ese APK podía manejar.
     */
    @Test
    fun `las rutas viejas con el nombre en la ruta siguen funcionando`() = testApplication {
        wireApp()
        postBudget("Mercado", 500_000)

        val editado = client.put("/api/budgets/Mercado") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"category":"Mercado","monthlyLimit":600000}""")
        }
        assertEquals(HttpStatusCode.OK, editado.status)
        assertEquals(600_000L, budgets().single()["monthlyLimit"]!!.jsonPrimitive.long)

        val renombrado = client.put("/api/budgets/Mercado/rename") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"newCategory":"Supermercado"}""")
        }
        assertEquals(HttpStatusCode.OK, renombrado.status)
        assertEquals("Supermercado", budgets().single()["category"]!!.jsonPrimitive.content)

        val borrado = client.delete("/api/budgets/Supermercado") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, borrado.status)
        assertEquals(0, budgets().size)
    }
}
