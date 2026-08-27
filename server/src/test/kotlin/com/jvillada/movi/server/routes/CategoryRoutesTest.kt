package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `/api/categories` — la pantalla «Más → Categorías».
 *
 * Lo que estos tests protegen, en orden de gravedad:
 *
 * 1. **Las reservadas no se tocan.** `isCashFlow` las reconoce por su nombre exacto; renombrar
 *    una cambiaría las cifras de todos los meses del dueño de golpe.
 * 2. **La reescritura llega a las TRES tablas** (`financial_events`, `budgets`,
 *    `recurring_rules`) o a ninguna. Un rename que solo mueva los movimientos deja el presupuesto
 *    mirando a una categoría que ya no existe — el cruce es por nombre, no por id.
 * 3. **Esconder no borra nada.** Es la diferencia entre esta pantalla y un botón de borrar.
 *
 * Mismo arnés que BudgetRoutesTest: H2 en memoria (compat PostgreSQL), JWT local, cadena
 * completa de plugins vía `wireApp()`.
 */
class CategoryRoutesTest {

    private val testSecret = "test-secret-for-category-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-categories"
    private val userEmail = "a@categories.test"
    private val otherUserId = "user-b-categories"
    private val otherEmail = "b@categories.test"

    private val accountId = "acc-cat-1"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:category_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, CategoryPrefs,
            )

            for ((id, email, name) in listOf(
                Triple(userId, userEmail, "User A"),
                Triple(otherUserId, otherEmail, "User B"),
            )) {
                Users.insert {
                    it[Users.id]  = id
                    it[Users.email] = email
                    it[Users.name] = name
                    it[passwordHash] = "hash"
                }
            }
            Accounts.insert {
                it[id]     = accountId
                it[userId] = this@CategoryRoutesTest.userId
                it[name]   = "Ahorros"
                it[type]   = "SAVINGS"
            }
        }
    }

    // ── Sembrado ──────────────────────────────────────────────────────────────

    private fun seedEvent(
        id: String,
        category: String,
        amount: Long = 10_000,
        type: String = "EXPENSE",
        owner: String = userId,
        currency: String = "COP",
        timestamp: Long = System.currentTimeMillis(),
    ) = transaction {
        Events.insert {
            it[Events.id]          = id
            it[Events.userId]      = owner
            // Calificado: adentro del `insert` el receptor es `Events`, así que `accountId` sin
            // más resuelve a la COLUMNA y no a la propiedad de este test.
            it[Events.accountId]   = this@CategoryRoutesTest.accountId
            it[Events.type]        = type
            it[Events.amount]      = amount
            it[Events.currency]    = currency
            it[Events.category]    = category
            it[description]        = "mov $id"
            it[Events.timestamp]   = timestamp
        }
    }

    private fun seedBudget(category: String, limit: Long = 500_000, owner: String = userId) = transaction {
        Budgets.insert {
            it[userId]       = owner
            it[Budgets.category] = category
            it[monthlyLimit] = limit
        }
    }

    private fun seedRule(id: String, category: String, owner: String = userId) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id]       = id
            it[userId]                  = owner
            it[name]                    = "regla $id"
            it[RecurringRules.category] = category
            it[amount]                  = 50_000
            it[dayOfMonth]              = 5
            it[type]                    = "EXPENSE"
        }
    }

    private fun categoriaEnEventos(id: String): String? = transaction {
        Events.selectAll().where { Events.id eq id }.firstOrNull()?.get(Events.category)
    }

    private fun limiteDe(category: String, owner: String = userId): Long? = transaction {
        Budgets.selectAll()
            .where { (Budgets.userId eq owner) and (Budgets.category eq category) }
            .firstOrNull()?.get(Budgets.monthlyLimit)
    }

    private fun categoriaDeRegla(id: String): String? = transaction {
        RecurringRules.selectAll().where { RecurringRules.id eq id }.firstOrNull()?.get(RecurringRules.category)
    }

    // ── Arnés ─────────────────────────────────────────────────────────────────

    private fun tokenFor(userId: String, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private val token get() = tokenFor(userId, userEmail)

    private fun Application.testModule() {
        configureSerialization()
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
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

    private fun ApplicationTestBuilder.wireApp() { application { testModule() } }

    private suspend fun ApplicationTestBuilder.categorias(asToken: String = token) =
        Json.parseToJsonElement(
            client.get("/api/categories") { header(HttpHeaders.Authorization, "Bearer $asToken") }.bodyAsText(),
        ).jsonArray.map { it.jsonObject }

    // El Json del server tiene `encodeDefaults = false`: un `false`, un cero o el scope por
    // defecto NO viajan. Leer esas claves a lo bruto explota con NPE en el caso más común, así
    // que se leen con el mismo default que el modelo (ver CategoryUsage).
    private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.boolean ?: false
    private fun JsonObject.num(key: String): Int = this[key]?.jsonPrimitive?.int ?: 0
    private fun JsonObject.plata(key: String): Long = this[key]?.jsonPrimitive?.long ?: 0L
    private fun JsonObject.texto(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.nombre(): String = this["name"]!!.jsonPrimitive.content
    private fun JsonObject.alcance(): String = this["scope"]?.jsonPrimitive?.content ?: "CUSTOM"
    private fun List<JsonObject>.porNombre(name: String): JsonObject =
        firstOrNull { it.nombre() == name } ?: error("«$name» no está en la lista: ${map { it.nombre() }}")

    private suspend fun ApplicationTestBuilder.rename(from: String, to: String, asToken: String = token) =
        client.post("/api/categories/rename") {
            header(HttpHeaders.Authorization, "Bearer $asToken")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"from":"$from","to":"$to"}""")
        }

    private suspend fun ApplicationTestBuilder.merge(from: String, into: String, asToken: String = token) =
        client.post("/api/categories/merge") {
            header(HttpHeaders.Authorization, "Bearer $asToken")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"from":"$from","into":"$into"}""")
        }

    private suspend fun ApplicationTestBuilder.prefs(
        name: String,
        hidden: Boolean = false,
        pinnedType: String? = null,
        asToken: String = token,
    ) = client.put("/api/categories/prefs") {
        header(HttpHeaders.Authorization, "Bearer $asToken")
        header(HttpHeaders.ContentType, "application/json")
        val tipo = if (pinnedType == null) "null" else "\"$pinnedType\""
        setBody("""{"name":"$name","hidden":$hidden,"pinnedType":$tipo}""")
    }

    // ── La lista, con uso real ────────────────────────────────────────────────

    @Test
    fun `la lista trae el uso real de cada categoria`() = testApplication {
        wireApp()
        seedEvent("e1", "Comida", amount = 30_000)
        seedEvent("e2", "Comida", amount = 20_000)
        seedBudget("Comida", 500_000)
        seedRule("r1", "Comida")

        val comida = categorias().porNombre("Comida")
        assertEquals(2, comida.num("movements"))
        assertEquals(50_000L, comida.plata("total"))
        assertEquals(1, comida.num("budgets"))
        assertEquals(1, comida.num("recurringRules"))
        assertEquals("PREDEFINED", comida.alcance())
    }

    @Test
    fun `el catalogo entero aparece aunque no se haya usado nunca`() = testApplication {
        wireApp()
        // Para poder esconder «Freelance» sin haberla usado, tiene que estar en la lista.
        val nombres = categorias().map { it.nombre() }
        assertTrue("Freelance" in nombres, nombres.toString())
        assertTrue("Salario" in nombres)
    }

    @Test
    fun `una categoria propia aparece marcada como CUSTOM`() = testApplication {
        wireApp()
        seedEvent("e1", "Carro")
        val carro = categorias().porNombre("Carro")
        assertEquals("CUSTOM", carro.alcance())
        assertEquals(listOf("EXPENSE"), carro["usedTypes"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `los movimientos anulados no cuentan`() = testApplication {
        wireApp()
        seedEvent("e1", "Comida", amount = 30_000)
        seedEvent("e2", "Comida", amount = 20_000)
        transaction {
            VoidEvents.insert {
                it[id]              = "v1"
                it[userId]          = this@CategoryRoutesTest.userId
                it[originalEventId] = "e2"
                it[timestamp]       = System.currentTimeMillis()
            }
        }
        val comida = categorias().porNombre("Comida")
        assertEquals(1, comida.num("movements"))
        assertEquals(30_000L, comida.plata("total"))
    }

    @Test
    fun `los movimientos en otra moneda se cuentan aparte y no suman al total`() = testApplication {
        wireApp()
        seedEvent("e1", "Tecnología", amount = 100_000)
        seedEvent("e2", "Tecnología", amount = 50, currency = "USD")
        val tec = categorias().porNombre("Tecnología")
        assertEquals(1, tec.num("movements"))
        assertEquals(100_000L, tec.plata("total"))
        assertEquals(1, tec.num("otherCurrencyMovements"))
    }

    @Test
    fun `la lista no mezcla las categorias de otro usuario`() = testApplication {
        wireApp()
        seedEvent("e-otro", "SoloDeB", owner = otherUserId)
        assertTrue(categorias().none { it.nombre() == "SoloDeB" })
    }

    // ── Las reservadas: se ven, no se tocan ───────────────────────────────────

    @Test
    fun `las cuatro reservadas se listan marcadas y ninguna se puede renombrar ni unificar ni esconder`() =
        testApplication {
            wireApp()
            for (reservada in listOf(TRANSFER_CATEGORY, OPENING_CATEGORY, CARD_PAYMENT_CATEGORY, ORPHANED_LEG_CATEGORY)) {
                seedEvent("ev-${reservada.hashCode()}", reservada)
            }
            val lista = categorias()
            for (reservada in listOf(TRANSFER_CATEGORY, OPENING_CATEGORY, CARD_PAYMENT_CATEGORY, ORPHANED_LEG_CATEGORY)) {
                val fila = lista.porNombre(reservada)
                assertTrue(fila.flag("reserved"), "«$reservada» tiene que venir marcada")

                assertEquals(
                    HttpStatusCode.UnprocessableEntity, rename(reservada, "Otra cosa").status,
                    "renombrar «$reservada» tiene que rebotar",
                )
                assertEquals(
                    HttpStatusCode.UnprocessableEntity, merge(reservada, "Comida").status,
                    "unificar «$reservada» tiene que rebotar",
                )
                assertEquals(
                    HttpStatusCode.UnprocessableEntity, prefs(reservada, hidden = true).status,
                    "esconder «$reservada» tiene que rebotar",
                )
            }
        }

    @Test
    fun `tampoco se puede renombrar ni unificar HACIA una reservada`() = testApplication {
        wireApp()
        seedEvent("e1", "Carro")
        assertEquals(HttpStatusCode.UnprocessableEntity, rename("Carro", TRANSFER_CATEGORY).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, merge("Carro", CARD_PAYMENT_CATEGORY).status)
        // Y ni siquiera en minúsculas: sería fabricar un nombre a un carácter del reservado.
        assertEquals(HttpStatusCode.UnprocessableEntity, rename("Carro", "traspaso").status)
        assertEquals("Carro", categoriaEnEventos("e1"))
    }

    // ── Renombrar ─────────────────────────────────────────────────────────────

    @Test
    fun `renombrar reescribe movimientos, presupuesto y recurrentes de una sola vez`() = testApplication {
        wireApp()
        seedEvent("e1", "Trasnporte")
        seedEvent("e2", "Trasnporte")
        seedBudget("Trasnporte", 200_000)
        seedRule("r1", "Trasnporte")

        val res = rename("Trasnporte", "Transporte propio")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val cuerpo = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals(2, cuerpo.num("movements"))
        assertEquals(1, cuerpo.num("budgets"))
        assertEquals(1, cuerpo.num("recurringRules"))

        assertEquals("Transporte propio", categoriaEnEventos("e1"))
        assertEquals("Transporte propio", categoriaEnEventos("e2"))
        assertEquals(200_000, limiteDe("Transporte propio"))
        assertNull(limiteDe("Trasnporte"))
        assertEquals("Transporte propio", categoriaDeRegla("r1"))
    }

    @Test
    fun `renombrar una del catalogo se rechaza — el catalogo volveria a sugerir el nombre viejo`() =
        testApplication {
            wireApp()
            seedEvent("e1", "Comida")
            val res = rename("Comida", "Alimentación")
            assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
            assertEquals("Comida", categoriaEnEventos("e1"))
        }

    @Test
    fun `renombrar a un nombre que ya existe es 409 y no toca nada`() = testApplication {
        wireApp()
        seedEvent("e1", "Trasnporte")
        val res = rename("Trasnporte", "Transporte")   // «Transporte» está en el catálogo
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("Unificar"), res.bodyAsText())
        assertEquals("Trasnporte", categoriaEnEventos("e1"))
    }

    @Test
    fun `renombrar al mismo nombre es un no-op valido`() = testApplication {
        wireApp()
        seedEvent("e1", "Carro")
        assertEquals(HttpStatusCode.OK, rename("Carro", "Carro").status)
        assertEquals("Carro", categoriaEnEventos("e1"))
    }

    @Test
    fun `renombrar no toca los movimientos de otro usuario`() = testApplication {
        wireApp()
        seedEvent("e-mio", "Carro")
        seedEvent("e-suyo", "Carro", owner = otherUserId)
        assertEquals(HttpStatusCode.OK, rename("Carro", "Carro nuevo").status)
        assertEquals("Carro nuevo", categoriaEnEventos("e-mio"))
        assertEquals("Carro", categoriaEnEventos("e-suyo"))
    }

    @Test
    fun `renombrar mueve tambien la preferencia de tipo`() = testApplication {
        wireApp()
        seedEvent("e1", "Carro")
        assertEquals(HttpStatusCode.OK, prefs("Carro", pinnedType = "BOTH").status)
        assertEquals(HttpStatusCode.OK, rename("Carro", "Auto").status)

        val lista = categorias()
        assertEquals("BOTH", lista.porNombre("Auto").texto("pinnedType"))
        assertTrue(lista.none { it.nombre() == "Carro" })
    }

    // ── Unificar ──────────────────────────────────────────────────────────────

    @Test
    fun `unificar mueve todo al destino y esconde el origen si era del catalogo`() = testApplication {
        wireApp()
        // El caso del que salió todo: «Otros» y «Otros ingresos» son la misma idea partida en dos.
        seedEvent("e1", "Otros ingresos", type = "INCOME", amount = 80_000)
        val res = merge("Otros ingresos", "Otros")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals("Otros", categoriaEnEventos("e1"))

        val lista = categorias()
        val origen = lista.porNombre("Otros ingresos")
        assertTrue(origen.flag("hidden"), "la del catálogo se esconde para dejar de sugerirse")
        assertEquals(0, origen.num("movements"))
        assertEquals(1, lista.porNombre("Otros").num("movements"))
    }

    @Test
    fun `unificar una propia no le deja una fila fantasma — desaparece de la lista`() = testApplication {
        wireApp()
        seedEvent("e1", "Trasnporte")
        assertEquals(HttpStatusCode.OK, merge("Trasnporte", "Transporte").status)
        assertEquals("Transporte", categoriaEnEventos("e1"))
        assertTrue(categorias().none { it.nombre() == "Trasnporte" })
    }

    @Test
    fun `unificar dos categorias con presupuesto suma los dos limites en uno`() = testApplication {
        wireApp()
        // La categoría es la PK de `budgets`: sin resolver la colisión el UPDATE chocaría.
        seedBudget("Mercado", 300_000)
        seedBudget("Comida", 500_000)
        val res = merge("Mercado", "Comida")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertTrue(Json.parseToJsonElement(res.bodyAsText()).jsonObject.flag("budgetsMerged"))
        assertEquals(800_000, limiteDe("Comida"))
        assertNull(limiteDe("Mercado"))
    }

    @Test
    fun `unificar en si misma se rechaza`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, merge("Comida", "Comida").status)
    }

    @Test
    fun `unificar hacia un nombre que no existe se rechaza — eso seria renombrar`() = testApplication {
        wireApp()
        // Sin esta guarda, unificar «Comida» en un nombre nuevo era exactamente el renombrado de
        // una categoría del catálogo que `rename` rechaza con 422: la misma operación, por otra
        // puerta, esquivando el motivo (el catálogo volvería a sugerir el nombre viejo).
        seedEvent("e1", "Comida")
        val res = merge("Comida", "Alimentación")
        assertEquals(HttpStatusCode.NotFound, res.status, res.bodyAsText())
        assertEquals("Comida", categoriaEnEventos("e1"))
    }

    @Test
    fun `el total separa lo gastado de lo recibido`() = testApplication {
        wireApp()
        // Los importes se guardan en positivo y el signo lo lleva `type`: un solo total sumaba
        // gastos con ingresos y daba un número sin significado, justo en el caso «Ambos».
        seedEvent("e1", "Otros", amount = 100_000, type = "EXPENSE")
        seedEvent("e2", "Otros", amount = 30_000, type = "INCOME")
        val otros = categorias().porNombre("Otros")
        assertEquals(2, otros.num("movements"))
        assertEquals(100_000L, otros.plata("total"))
        assertEquals(30_000L, otros.plata("incomeTotal"))
        assertEquals(100_000L, otros.plata("monthTotal"))
        assertEquals(30_000L, otros.plata("monthIncomeTotal"))
    }

    @Test
    fun `el destino de una unificacion nunca queda escondido`() = testApplication {
        wireApp()
        // Si «Comida» estaba escondida y ahora recibe movimientos, dejarla escondida sería mandar
        // la historia del dueño a una categoría que la app no le va a volver a ofrecer nunca.
        assertEquals(HttpStatusCode.OK, prefs("Comida", hidden = true).status)
        seedEvent("e1", "Mercado")
        assertEquals(HttpStatusCode.OK, merge("Mercado", "Comida").status)
        assertFalse(categorias().porNombre("Comida").flag("hidden"))
    }

    // ── Esconder y fijar el tipo ──────────────────────────────────────────────

    @Test
    fun `esconder no borra ni un movimiento`() = testApplication {
        wireApp()
        seedEvent("e1", "Ropa", amount = 90_000)
        assertEquals(HttpStatusCode.OK, prefs("Ropa", hidden = true).status)

        val ropa = categorias().porNombre("Ropa")
        assertTrue(ropa.flag("hidden"))
        assertEquals(1, ropa.num("movements"))
        assertEquals(90_000L, ropa.plata("total"))
        assertEquals("Ropa", categoriaEnEventos("e1"))
    }

    @Test
    fun `volver a mostrar borra la preferencia en vez de guardar un default`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, prefs("Ropa", hidden = true).status)
        assertEquals(HttpStatusCode.OK, prefs("Ropa", hidden = false).status)
        assertFalse(categorias().porNombre("Ropa").flag("hidden"))
        val filas = transaction { CategoryPrefs.selectAll().where { CategoryPrefs.name eq "Ropa" }.count() }
        assertEquals(0L, filas, "una preferencia que no dice nada distinto del default no se guarda")
    }

    @Test
    fun `fijar el tipo en BOTH queda guardado`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, prefs("Otros", pinnedType = "BOTH").status)
        assertEquals("BOTH", categorias().porNombre("Otros").texto("pinnedType"))
    }

    @Test
    fun `un tipo desconocido se rechaza`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.BadRequest, prefs("Otros", pinnedType = "CUALQUIERA").status)
    }

    @Test
    fun `una categoria que solo existe por su preferencia sigue apareciendo en la lista`() = testApplication {
        wireApp()
        // Es el caso de esconder una del catálogo que nunca usó — la fila tiene que sobrevivir
        // para poder deshacerlo.
        assertEquals(HttpStatusCode.OK, prefs("Freelance", hidden = true).status)
        assertTrue(categorias().porNombre("Freelance").flag("hidden"))
    }

    @Test
    fun `las preferencias no se cruzan entre usuarios`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, prefs("Ropa", hidden = true).status)
        val delOtro = categorias(asToken = tokenFor(otherUserId, otherEmail)).porNombre("Ropa")
        assertFalse(delOtro.flag("hidden"))
    }

    // ── Lo que ve el Inicio ───────────────────────────────────────────────────

    @Test
    fun `el resumen del Inicio lleva las preferencias para que Agregar las respete`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, prefs("Otros", pinnedType = "BOTH").status)
        assertEquals(HttpStatusCode.OK, prefs("Ropa", hidden = true).status)

        val resumen = Json.parseToJsonElement(
            client.get("/api/dashboard/summary") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonObject
        val usadas = resumen["usedCategories"]!!.jsonArray.map { it.jsonObject }
        assertEquals("BOTH", usadas.porNombre("Otros").texto("pinnedType"))
        assertTrue(usadas.porNombre("Ropa").flag("hidden"))
    }
}
