package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Goals
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Hallazgo Critical de la revisión de la Ola 1b: hasta acá, `POST /api/accounts` fabricaba un
 * evento "Saldo inicial"/"Deuda inicial" a partir de `body.balance`. Una cuenta creada offline
 * (`LocalRepository.createAccount`) sincroniza esa misma fila vía `SyncEngine.syncAccounts` con
 * el balance ya movido por eventos reales anotados antes del primer sync — si esta ruta seguía
 * fabricando la apertura a partir de ese balance, el ingreso/gasto real que `syncEvents` empuja
 * justo después se sumaba ENCIMA: doble conteo silencioso y permanente.
 *
 * La decisión (ver `openingEventFor` en :core): el cliente crea la apertura, explícita y una sola
 * vez, con su propio `POST /api/events`. Esta ruta deja de fabricar nada — la columna cruda
 * `accounts.balance` ya no importa, el balance que ve el cliente sale siempre de
 * `enrichWith`/`computeBalances`, derivado de eventos reales.
 *
 * Mismo arnés que CreditRoutesTest/FinanceRoutesTest: H2 en memoria (compat PostgreSQL), JWT
 * local, cadena completa de plugins vía `wireApp()`.
 */
class AccountRoutesTest {

    private val testSecret = "test-secret-for-account-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-accounts"
    private val userEmail = "a@accounts.test"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:account_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(Cards, Goals, 
                Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, Cards, Goals,
            )

            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "User A"
                it[passwordHash] = "hash-a"
            }
        }
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

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

    // ── Test application module ───────────────────────────────────────────────

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

    private suspend fun ApplicationTestBuilder.createAccount(id: String, type: String, balance: Long) =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"$id","name":"Cuenta","type":"$type","balance":$balance}""")
        }

    private suspend fun ApplicationTestBuilder.postOpeningEvent(
        accountId: String,
        type: String,
        amount: Long,
        description: String,
    ) = client.post("/api/events") {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.ContentType, "application/json")
        setBody(
            """{"id":"","accountId":"$accountId","type":"$type","amount":$amount,
                "category":"Saldo inicial","description":"$description","timestamp":0}""",
        )
    }

    private suspend fun ApplicationTestBuilder.accountBalance(id: String): Long =
        Json.parseToJsonElement(
            client.get("/api/accounts/$id") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject["balance"]!!.jsonPrimitive.long

    private suspend fun ApplicationTestBuilder.summary() =
        Json.parseToJsonElement(
            client.get("/api/finance-summary") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.eventCount(): Int =
        this["eventCount"]?.jsonPrimitive?.long?.toInt() ?: 0

    private fun eventsInDb(): Int = transaction { Events.selectAll().count().toInt() }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `POST crea la cuenta con el balance recibido pero no fabrica ningun evento`() = testApplication {
        wireApp()
        val res = createAccount("acc-savings", "SAVINGS", 1_000_000L)
        assertEquals(HttpStatusCode.Created, res.status)

        assertEquals(0, eventsInDb(), "crear la cuenta no debe insertar ninguna fila en events")
    }

    @Test
    fun `el balance derivado de la cuenta es 0 hasta que el cliente postea la apertura`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)

        assertEquals(
            0L,
            accountBalance("acc-savings"),
            "sin ningún evento, el balance derivado (enrichWith/computeBalances) tiene que ser 0 " +
                "aunque la fila cruda de accounts.balance haya llegado en 1.000.000",
        )
    }

    @Test
    fun `tras postear el evento de apertura el balance derivado queda en la cifra declarada`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)

        val evRes = postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")
        assertEquals(HttpStatusCode.Created, evRes.status)

        assertEquals(1_000_000L, accountBalance("acc-savings"))
    }

    @Test
    fun `la apertura posteada por el cliente sigue sin contar como ingreso del mes ni como movimiento`() =
        testApplication {
            wireApp()
            createAccount("acc-savings", "SAVINGS", 1_000_000L)
            postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")

            val body = summary()
            assertEquals(0L, body["ingresos"]!!.jsonPrimitive.long, "la apertura no es un ingreso del mes")
            assertEquals(0L, body["egresos"]!!.jsonPrimitive.long)
            assertEquals(
                0,
                body.eventCount(),
                "la apertura no cuenta como \"primer movimiento\" para la guía de primeros pasos",
            )
        }

    /**
     * Sin este caso, el fix de la Ola 1b (no fabricar la apertura en el server) podría revertirse
     * por accidente sin que ningún test lo note: si `POST /api/accounts` volviera a fabricar el
     * evento, este test vería DOS eventos en vez de uno tras el POST explícito del cliente —el
     * mismo doble conteo que el hallazgo Critical describe para el escenario offline.
     */
    @Test
    fun `crear la cuenta y postear la apertura no deja eventos duplicados`() = testApplication {
        wireApp()
        createAccount("acc-savings", "SAVINGS", 1_000_000L)
        postOpeningEvent("acc-savings", "INCOME", 1_000_000L, "Saldo inicial")

        assertEquals(1, eventsInDb(), "solo el evento que posteó el cliente — ninguno fabricado por el server")
    }

    // ── F55: DELETE /api/accounts/{id} ──────────────────────────────────────────

    /**
     * Arma una cuenta LOAN con de todo lo que F55 tiene que barrer: un evento normal, un
     * evento anulado (deja fila en void_events), un evento marcado "No es pago de tarjeta"
     * (deja fila en card_payment_dismissals) y términos de crédito. Todo tiene que
     * desaparecer en un solo DELETE.
     */
    private fun seedAccountWithEverything(accountId: String, uid: String) {
        transaction {
            Accounts.insert {
                it[id]       = accountId
                it[userId]   = uid
                it[name]     = "Libranza"
                it[type]     = "LOAN"
                it[balance]  = 0
                it[currency] = "COP"
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-normal"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 10_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Cuota"
                it[Events.timestamp] = 0
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-anulado"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 5_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Duplicado"
                it[Events.timestamp] = 0
            }
            VoidEvents.insert {
                it[VoidEvents.id]              = "$accountId-void"
                it[VoidEvents.userId]          = uid
                it[VoidEvents.originalEventId] = "$accountId-ev-anulado"
                it[VoidEvents.timestamp]       = 0
            }
            // card_terms nació en esta misma ola, DESPUÉS del DELETE: la revisión encontró que
            // quedaba huérfano. Se siembra aunque la cuenta sea LOAN — al DELETE le da igual,
            // barre por accountId.
            Cards.insert {
                it[Cards.accountId]  = accountId
                it[Cards.userId]     = uid
                it[Cards.bank]       = "Banco"
                it[Cards.paymentDay] = 15
            }
            // goals nació en la Ola 6, también después del DELETE — mismo patrón que card_terms.
            Goals.insert {
                it[Goals.id]        = "$accountId-meta"
                it[Goals.userId]    = uid
                it[Goals.name]      = "Viaje"
                it[Goals.target]    = 1_000_000
                it[Goals.accountId] = accountId
                it[Goals.createdAt] = 0
            }
            Events.insert {
                it[Events.id]        = "$accountId-ev-tc"
                it[Events.userId]    = uid
                it[Events.accountId] = accountId
                it[Events.type]      = "EXPENSE"
                it[Events.amount]    = 300_000
                it[Events.category]  = "Otros"
                it[Events.description] = "Parece pago de tarjeta"
                it[Events.timestamp] = 0
            }
            CardPaymentDismissals.insert {
                it[CardPaymentDismissals.userId]  = uid
                it[CardPaymentDismissals.eventId] = "$accountId-ev-tc"
            }
            Credits.insert {
                it[Credits.accountId]   = accountId
                it[Credits.userId]      = uid
                it[Credits.bank]        = "Banco"
                it[Credits.principal]   = 1_000_000
                it[Credits.rateEa]      = 1.5
                it[Credits.termMonths]  = 12
                it[Credits.installment] = 90_000
                it[Credits.dayOfMonth]  = 5
                it[Credits.startDate]   = "2026-01-01"
            }
        }
    }

    private fun rowCounts(accountId: String): Quintuple =
        transaction {
            val eventIds = Events.selectAll().where { Events.accountId eq accountId }.map { it[Events.id] }
            Quintuple(
                accounts = Accounts.selectAll().where { Accounts.id eq accountId }.count().toInt(),
                events = eventIds.size,
                voidEvents = if (eventIds.isEmpty()) 0 else
                    VoidEvents.selectAll().where { VoidEvents.originalEventId inList eventIds }.count().toInt(),
                dismissals = if (eventIds.isEmpty()) 0 else
                    CardPaymentDismissals.selectAll().where { CardPaymentDismissals.eventId inList eventIds }.count().toInt(),
                credits = Credits.selectAll().where { Credits.accountId eq accountId }.count().toInt(),
                cards = Cards.selectAll().where { Cards.accountId eq accountId }.count().toInt(),
                goals = Goals.selectAll().where { Goals.accountId eq accountId }.count().toInt(),
            )
        }

    private data class Quintuple(val accounts: Int, val events: Int, val voidEvents: Int, val dismissals: Int, val credits: Int, val cards: Int, val goals: Int)

    @Test
    fun `DELETE borra la cuenta, sus eventos, anulaciones, dismissals y terminos de credito en una sola pasada`() = testApplication {
        wireApp()
        val accId = "acc-full-a"
        seedAccountWithEverything(accId, userId)
        assertEquals(Quintuple(1, 3, 1, 1, 1, 1, 1), rowCounts(accId), "el seed dejó todo lo que el DELETE tiene que barrer")

        val res = client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NoContent, res.status)

        assertEquals(Quintuple(0, 0, 0, 0, 0, 0, 0), rowCounts(accId), "no debe quedar NADA de la cuenta borrada")
    }

    /**
     * Ola 9 · D: borrar la cuenta **no borra** el recurrente que la referenciaba — lo suelta.
     * Mismo criterio que la pata hermana de un traspaso: se suelta la referencia, no se destruye
     * el hecho. «Arriendo, día 5, $1.800.000» sigue siendo verdad sin la cuenta.
     */
    @Test
    fun `DELETE suelta las reglas recurrentes de la cuenta en vez de borrarlas`() = testApplication {
        wireApp()
        val accId = "acc-con-recurrente"
        seedAccountWithEverything(accId, userId)
        val uid = userId  // adentro del insert, `userId` resuelve a la COLUMNA, no a este campo
        transaction {
            RecurringRules.insert {
                it[RecurringRules.id]         = "rr-de-la-cuenta"
                it[RecurringRules.userId]     = uid
                it[RecurringRules.name]       = "Arriendo"
                it[RecurringRules.category]   = "Vivienda"
                it[RecurringRules.amount]     = 1_800_000
                it[RecurringRules.dayOfMonth] = 5
                it[RecurringRules.type]       = "EXPENSE"
                it[RecurringRules.accountId]  = accId
            }
        }

        val res = client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NoContent, res.status)

        val regla = transaction {
            RecurringRules.selectAll().where { RecurringRules.id eq "rr-de-la-cuenta" }.singleOrNull()
        }
        assertNotNull(regla, "la regla recurrente NO se borra con la cuenta")
        assertEquals("Arriendo", regla[RecurringRules.name])
        assertEquals(1_800_000L, regla[RecurringRules.amount], "el plan queda intacto")
        assertEquals(null, regla[RecurringRules.accountId], "y sin cuenta, que es la verdad")
    }

    @Test
    fun `DELETE de una cuenta inexistente es 404`() = testApplication {
        wireApp()
        val res = client.delete("/api/accounts/no-existe") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `el segundo DELETE sobre la misma cuenta es 404`() = testApplication {
        wireApp()
        val accId = "acc-twice"
        createAccount(accId, "SAVINGS", 0)
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
        assertEquals(HttpStatusCode.NotFound,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
    }

    @Test
    fun `DELETE no borra cuentas de otro usuario ni deja borrar las propias de otro`() = testApplication {
        wireApp()
        val otherUserId = "user-b-accounts"
        transaction {
            Users.insert {
                it[id]           = otherUserId
                it[email]        = "b@accounts.test"
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
        val accId = "acc-full-a"
        seedAccountWithEverything(accId, userId)

        // User B intenta borrar la cuenta de A: 404 (no la ve, no es suya) y no toca nada.
        val otherToken = tokenFor(otherUserId, "b@accounts.test")
        val res = client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $otherToken") }
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(Quintuple(1, 3, 1, 1, 1, 1, 1), rowCounts(accId), "el intento de B no debe tocar nada de A")

        // A sí puede borrar la suya — y lo de B (si tuviera algo) quedaría intacto; acá alcanza
        // con confirmar que A borra la suya sin problema.
        assertEquals(HttpStatusCode.NoContent,
            client.delete("/api/accounts/$accId") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
    }

    /**
     * Ola 11 — **`GET /api/accounts` devuelve un orden, no el que le quede cómodo al motor.**
     *
     * Sin `ORDER BY`, SQL no promete nada: el orden físico de las filas cambia después de un
     * UPDATE o un VACUUM. Y toda la app trataba esta lista como si tuviera orden (la hoja de
     * Agregar preseleccionaba `first()`), así que un detalle del motor decidía en qué cuenta se
     * anotaba la plata. Este test escribe las cuentas al revés del alfabeto —y encima le hace un
     * UPDATE a la primera, que es lo que en Postgres reescribe la fila al final de la tabla—
     * para que un `selectAll` sin orden tenga todas las chances de devolverlas mal.
     *
     * **Los nombres van con la caja mezclada a propósito.** El cliente ordena la misma lista en
     * SQLite, que compara con `BINARY` (todas las mayúsculas antes que cualquier minúscula), y
     * este server con la locale de Postgres, que ignora la caja: con `ORDER BY name` a secas,
     * «efectivo» en minúscula iba primero en la web y último en el teléfono, o sea que la app
     * preseleccionaba una cuenta distinta en cada lado. Por eso el orden es por `lower(name)` y
     * por eso este test lo ejercita con «efectivo» y no con «Efectivo».
     */
    @Test
    fun `GET ordena por nombre sin importar la caja, no por como las escribio la base`() = testApplication {
        wireApp()
        createNamedAccount("acc-3", "Nequi")
        createNamedAccount("acc-1", "efectivo")
        createNamedAccount("acc-2", "Bancolombia")
        // Un UPDATE sobre la que se insertó primero: en Postgres eso la reescribe y la manda al
        // final del orden físico. Con H2 el efecto no es idéntico, pero el ORDER BY tiene que
        // dar lo mismo en los dos.
        transaction {
            Accounts.update({ Accounts.id eq "acc-3" }) { it[balance] = 42L }
        }

        val nombres = Json.parseToJsonElement(
            client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

        // Con `ORDER BY name` a secas y una base que compara por byte, «efectivo» quedaría al
        // final; con `lower(name)`, va donde el dueño espera verla.
        assertEquals(listOf("Bancolombia", "efectivo", "Nequi"), nombres)
    }

    /**
     * **Renombrar una cuenta no puede devolverle al teléfono el saldo del día que se creó.**
     *
     * `PUT /{id}/name` contestaba con `toAccount()` a secas, o sea la columna cruda
     * `accounts.balance`: se escribe al crear la cuenta y **no se actualiza nunca más** —el saldo
     * de verdad se deriva de los eventos (`enrichWith`/`computeBalances`)—. Y el cliente espeja
     * esa respuesta en su fila local con `syncedAt = now` (`mirrorAccountLocally`), así que el
     * crédito del dueño, nacido en $257.000.000 y abonado hasta $200.000.000, volvía a los
     * $257.000.000 en el teléfono apenas se le corregía el nombre, y se seguía viendo así en
     * cada lectura que cayera en el respaldo local: sin red, o con el server más lento que
     * `PRESUPUESTO_DE_RED_MS`. El mismo defecto que `PUT /{id}/conditioned-to` ya tenía
     * documentado y arreglado — este era la copia que quedó.
     */
    @Test
    fun `PUT name contesta el saldo derivado de los eventos, no la columna de cuando se creo`() =
        testApplication {
            wireApp()
            // La cuenta nace declarando $257.000.000 en la columna cruda…
            createAccount("acc-libranza", "LOAN", 257_000_000L)
            // …y los eventos reales la dejan en $200.000.000 de deuda.
            postOpeningEvent("acc-libranza", "EXPENSE", 257_000_000L, "Deuda inicial")
            val abono = client.post("/api/events") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody(
                    """{"id":"","accountId":"acc-libranza","type":"INCOME","amount":57000000,
                        "category":"Otros","description":"Abono","timestamp":0}""",
                )
            }
            assertEquals(HttpStatusCode.Created, abono.status)
            assertEquals(200_000_000L, accountBalance("acc-libranza"), "el derivado, antes de renombrar")

            val res = client.put("/api/accounts/acc-libranza/name") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"name":"Libranza 4818"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)

            val cuerpo = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals("Libranza 4818", cuerpo["name"]!!.jsonPrimitive.content)
            assertEquals(
                200_000_000L,
                cuerpo["balance"]!!.jsonPrimitive.long,
                "con la columna cruda contestaría 257.000.000: el saldo del día que se creó",
            )
            // Y lo que el teléfono espeja tiene que ser lo mismo que contesta una lectura normal.
            assertEquals(accountBalance("acc-libranza"), cuerpo["balance"]!!.jsonPrimitive.long)
        }

    // ── POST /api/accounts es idempotente ───────────────────────────────────────

    /**
     * **Volver a mandar la misma cuenta no es un error, es la misma cuenta que vuelve.**
     *
     * Era un `INSERT` pelado contra una clave primaria que pone el cliente, así que el id repetido
     * salía por el 500 genérico. Y el reenvío pasa de verdad: `LocalRepository.createAccount` deja
     * la fila local sin sellar cuando el POST llegó pero la respuesta se perdió (se cortó la señal,
     * se murió el proceso), y `SyncEngine.syncAccounts` la reenvía cada 30 segundos — 500, log, sin
     * sellar, otra vez en 30 segundos, para siempre.
     *
     * Ahora contesta 200 con lo guardado, sin duplicar la fila y con los campos del reenvío. Y el
     * **saldo de la respuesta es el derivado de los eventos**, no la columna cruda `accounts.balance`
     * —el saldo del día que se creó—: el cliente espeja esta respuesta en su fila local
     * (`mirrorAccountLocally`), así que devolver el crudo le escribiría un saldo falso justo al
     * sellar. Es el mismo defecto que ya tenían `PUT /{id}/name` y `PUT /{id}/conditioned-to`.
     */
    @Test
    fun `POST del mismo id del mismo dueno contesta 200, no duplica la fila y actualiza los campos`() =
        testApplication {
            wireApp()
            assertEquals(HttpStatusCode.Created, createAccount("acc-reenvio", "SAVINGS", 1_000_000L).status)
            postOpeningEvent("acc-reenvio", "INCOME", 1_000_000L, "Saldo inicial")
            // Un gasto real: el derivado deja de coincidir con la columna cruda.
            client.post("/api/events") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody(
                    """{"id":"","accountId":"acc-reenvio","type":"EXPENSE","amount":400000,
                        "category":"Otros","description":"Mercado","timestamp":0}""",
                )
            }
            assertEquals(600_000L, accountBalance("acc-reenvio"), "el derivado, antes del reenvío")

            val res = client.post("/api/accounts") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"id":"acc-reenvio","name":"Ahorros Nequi","type":"SAVINGS","balance":1000000,"currency":"COP"}""")
            }

            assertEquals(HttpStatusCode.OK, res.status, "el reenvío no es un error")
            assertEquals(
                1,
                transaction { Accounts.selectAll().where { Accounts.id eq "acc-reenvio" }.count().toInt() },
                "una sola fila: el reenvío no duplica nada",
            )
            val cuerpo = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals("Ahorros Nequi", cuerpo["name"]!!.jsonPrimitive.content, "el reenvío es la versión vigente")
            assertEquals(
                600_000L,
                cuerpo["balance"]!!.jsonPrimitive.long,
                "con la columna cruda contestaría 1.000.000: el saldo del día que se creó",
            )
            assertEquals(accountBalance("acc-reenvio"), cuerpo["balance"]!!.jsonPrimitive.long)
            // Y los eventos siguen siendo los mismos: el reenvío no fabrica ninguna apertura.
            assertEquals(2, eventsInDb(), "el reenvío no crea eventos")
        }

    /** El reenvío vuelve a llegar y a llegar: el segundo y el tercero contestan lo mismo. */
    @Test
    fun `el reenvio se puede repetir sin que cambie el resultado`() = testApplication {
        wireApp()
        createAccount("acc-repetido", "CASH", 0L)
        repeat(3) {
            assertEquals(HttpStatusCode.OK, createAccount("acc-repetido", "CASH", 0L).status)
        }
        assertEquals(1, transaction { Accounts.selectAll().where { Accounts.id eq "acc-repetido" }.count().toInt() })
    }

    /**
     * **Un id de OTRO usuario es un choque real: 409 y no se escribe nada.**
     *
     * La clave primaria de `accounts` es el id a secas (no `id + user_id`), así que sin esta rama
     * el `INSERT` fallaría igual — pero con un 500 mudo. Y un `UPDATE` sin el filtro de dueño
     * sería peor todavía: reescribirle la cuenta a otro.
     */
    @Test
    fun `POST con un id que es de otro usuario contesta 409 y no toca nada`() = testApplication {
        wireApp()
        val otherUserId = "user-b-accounts"
        transaction {
            Users.insert {
                it[id]           = otherUserId
                it[email]        = "b@accounts.test"
                it[name]         = "User B"
                it[passwordHash] = "hash-b"
            }
        }
        createAccount("acc-de-a", "SAVINGS", 500_000L)

        val res = client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(otherUserId, "b@accounts.test")}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"acc-de-a","name":"Mia ahora","type":"CASH","balance":9,"currency":"USD"}""")
        }

        assertEquals(HttpStatusCode.Conflict, res.status)
        val fila = transaction { Accounts.selectAll().where { Accounts.id eq "acc-de-a" }.single() }
        assertEquals(userId, fila[Accounts.userId], "la cuenta sigue siendo de A")
        assertEquals("Cuenta", fila[Accounts.name], "nada de B se escribió")
        assertEquals("SAVINGS", fila[Accounts.type])
        assertEquals("COP", fila[Accounts.currency])
        assertEquals(500_000L, fila[Accounts.balance])
    }

    /**
     * **El reenvío no le borra al dueño la condición que marcó desde la web.**
     *
     * `SyncEngine.syncAccounts` arma la cuenta a mano, y un APK anterior a este arreglo la manda
     * sin `condicionadaA`: pisar la columna con el `null` del default le borraría la marca de
     * «solo para vivienda» que el dueño puso con `PUT /{id}/conditioned-to` —un dato que el
     * reenviante nunca tuvo—. Por eso el campo solo se toca si el pedido trae la clave, el mismo
     * criterio con el que `POST /api/events` mira si el cliente MANDÓ la moneda.
     */
    @Test
    fun `el reenvio sin la clave de condicion no borra la que ya estaba`() = testApplication {
        wireApp()
        createAccount("acc-skandia", "INVESTMENT", 106_000_000L)
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/accounts/acc-skandia/conditioned-to") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"condicionadaA":"Vivienda"}""")
            }.status,
        )

        // El reenvío de un cliente que no conoce el campo: la clave no viene.
        val res = createAccount("acc-skandia", "INVESTMENT", 106_000_000L)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            "Vivienda",
            transaction { Accounts.selectAll().where { Accounts.id eq "acc-skandia" }.single()[Accounts.conditionedTo] },
            "sin la clave en el pedido, la condición se queda como estaba",
        )
    }

    /** Y cuando SÍ la manda, manda: eso es lo que hace que la clave signifique algo. */
    @Test
    fun `el reenvio con la clave de condicion si la cambia`() = testApplication {
        wireApp()
        createAccount("acc-condicion", "INVESTMENT", 0L)

        val res = client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"acc-condicion","name":"Cuenta","type":"INVESTMENT","balance":0,"condicionadaA":"Vivienda"}""")
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            "Vivienda",
            transaction { Accounts.selectAll().where { Accounts.id eq "acc-condicion" }.single()[Accounts.conditionedTo] },
        )
    }

    private suspend fun ApplicationTestBuilder.createNamedAccount(id: String, name: String) =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"$id","name":"$name","type":"SAVINGS","balance":0}""")
        }

    // ── El reenvío contra el renombre de la web: quién gana ─────────────────────
    //
    // El agujero que quedó abierto cuando el `POST /api/accounts` se volvió idempotente: el POST
    // del teléfono LLEGA pero la respuesta no vuelve (se cortó la señal, se murió el proceso), así
    // que la fila local se queda sin sellar y `SyncEngine.syncAccounts` la reenvía cada 30 s. Si en
    // esa ventana el dueño renombró la cuenta **en la web** —«Libranza 4818» donde decía «4817»,
    // el dígito que la identifica contra el extracto— el upsert pisaba ese nombre sin decir nada.
    // Ver `Account.lastEditedAt` y `pisaElReenvio`, que es la misma función que decide para los
    // movimientos.

    /** El cuerpo tal como lo arma un cliente que conoce el campo: la clave viaja SIEMPRE. */
    private suspend fun ApplicationTestBuilder.reenviarCuenta(id: String, name: String, edicion: Long?) =
        client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"$id","name":"$name","type":"SAVINGS","balance":0,"currency":"COP",
                    "lastEditedAt":${edicion ?: "null"}}""",
            )
        }

    private suspend fun ApplicationTestBuilder.renombrar(id: String, name: String) =
        client.put("/api/accounts/$id/name") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"name":"$name"}""")
        }

    private fun nombreGuardado(id: String) =
        transaction { Accounts.selectAll().where { Accounts.id eq id }.single()[Accounts.name] }

    private fun edicionGuardadaDe(id: String) =
        transaction { Accounts.selectAll().where { Accounts.id eq id }.single()[Accounts.lastEditedAt] }

    /**
     * Lo primero que no se puede romper: una cuenta que el server NO tiene se inserta igual, traiga
     * la clave o no. Sin esto, la guarda nueva convertiría el camino normal —el alta— en un rechazo
     * silencioso.
     */
    @Test
    fun `la primera entrega de una cuenta nueva se sigue insertando`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, reenviarCuenta("acc-primera", "Nequi", null).status)
        assertEquals("Nequi", nombreGuardado("acc-primera"))
        assertEquals(null, edicionGuardadaDe("acc-primera"), "crearla no es editarla")
    }

    /**
     * El caso que dispara todo esto: el POST llegó, la respuesta no. El teléfono reenvía lo MISMO.
     * Tiene que ser un no-op tranquilo (200), no un error ni una fila de más — si no, el ciclo de
     * 30 s lo reintentaría para siempre.
     */
    @Test
    fun `un reenvio identico de la cuenta es un no-op, no un error`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, reenviarCuenta("acc-igual", "Nequi", null).status)
        val res = reenviarCuenta("acc-igual", "Nequi", null)

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(1, transaction { Accounts.selectAll().where { Accounts.id eq "acc-igual" }.count().toInt() })
        assertEquals("Nequi", nombreGuardado("acc-igual"))
    }

    /**
     * **El bug, con su dígito.** El teléfono sube «Libranza 4817» y no ve la respuesta; el dueño lo
     * corrige a «Libranza 4818» desde la web; el ciclo siguiente reenvía el nombre viejo. Antes
     * volvía el «4817» sin decir nada: ni un error, ni un aviso, la corrección deshecha.
     */
    @Test
    fun `el reenvio viejo no revierte el renombre hecho en la web`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, reenviarCuenta("acc-libranza-web", "Libranza 4817", null).status)
        assertEquals(HttpStatusCode.OK, renombrar("acc-libranza-web", "Libranza 4818").status)

        val reenvio = reenviarCuenta("acc-libranza-web", "Libranza 4817", null)

        assertEquals(HttpStatusCode.OK, reenvio.status, "el teléfono tiene que poder sellarla y dejar de reenviar")
        assertEquals("Libranza 4818", nombreGuardado("acc-libranza-web"), "el renombre de la web manda")
        assertEquals(
            "Libranza 4818",
            Json.parseToJsonElement(reenvio.bodyAsText()).jsonObject["name"]!!.jsonPrimitive.content,
            "y la respuesta dice la verdad de lo guardado, no el eco de lo que se mandó",
        )
    }

    /** La otra puerta que sella edad: marcar para qué sirve la plata de esa cuenta. */
    @Test
    fun `el reenvio viejo tampoco revierte la condicion marcada en la web`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, reenviarCuenta("acc-cond-web", "Skandia", null).status)
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/accounts/acc-cond-web/conditioned-to") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"condicionadaA":"Vivienda"}""")
            }.status,
        )

        // El reenvío trae la clave de condición en null Y es más viejo: no puede borrarla.
        val res = client.post("/api/accounts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"acc-cond-web","name":"Skandia","type":"SAVINGS","balance":0,
                    "condicionadaA":null,"lastEditedAt":null}""",
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            "Vivienda",
            transaction { Accounts.selectAll().where { Accounts.id eq "acc-cond-web" }.single()[Accounts.conditionedTo] },
        )
    }

    /**
     * **Y el lado que NO se puede romper por arreglar el otro:** renombrar sin señal una cuenta que
     * todavía no subió es un caso real del teléfono, y ese nombre tiene que ganarle a la copia más
     * vieja del server. Una guarda que hiciera perder siempre al reenvío sería el mismo bug al
     * revés.
     */
    @Test
    fun `el renombre hecho en el telefono sin senal si gana`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, reenviarCuenta("acc-tel", "Libranza 4817", null).status)
        assertEquals(HttpStatusCode.OK, renombrar("acc-tel", "Libranza 4818").status)

        // El dueño la renombra en el teléfono DESPUÉS (su reloj, un minuto más tarde).
        val despues = System.currentTimeMillis() + 60_000L
        assertEquals(HttpStatusCode.OK, reenviarCuenta("acc-tel", "Libranza del banco", despues).status)

        assertEquals("Libranza del banco", nombreGuardado("acc-tel"))
        assertEquals(despues, edicionGuardadaDe("acc-tel"), "y la próxima se compara contra ESTA")
    }

    /**
     * **El APK que el dueño tiene instalado (1.31) no manda la clave, y sigue pisando.** Es la
     * misma decisión que ya tomó `POST /api/events`, y por el mismo motivo: sin la clave no hay
     * forma de saber qué tan vieja es su copia, y tratar la ausencia como «editada en el año 0» la
     * haría perder SIEMPRE, incluso cuando es el renombre que él acaba de escribir sin señal. El
     * agujero se cierra para ese APK el día que instale el nuevo, no antes.
     */
    @Test
    fun `un APK viejo que no manda la clave se sigue atendiendo como antes`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, createNamedAccount("acc-apk-viejo", "Libranza 4817").status)
        assertEquals(HttpStatusCode.OK, renombrar("acc-apk-viejo", "Libranza 4818").status)

        assertEquals(HttpStatusCode.OK, createNamedAccount("acc-apk-viejo", "Libranza 4817").status)
        assertEquals("Libranza 4817", nombreGuardado("acc-apk-viejo"))
    }

    /** Renombrar sella la edad: sin eso el reenvío no tendría contra qué perder. */
    @Test
    fun `renombrar sella la edad de la version`() = testApplication {
        wireApp()
        createNamedAccount("acc-sello", "Libranza 4817")
        assertNull(edicionGuardadaDe("acc-sello"), "crearla no sella nada")

        assertEquals(HttpStatusCode.OK, renombrar("acc-sello", "Libranza 4818").status)
        assertNotNull(edicionGuardadaDe("acc-sello"))
    }

    // ── Cuadre de saldos ───────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.cuadrar(accountId: String, objetivo: Long) =
        client.post("/api/accounts/$accountId/balance-adjustment") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"targetBalance":$objetivo}""")
        }

    private fun JsonObject.evento(): JsonObject? = this["adjustmentEvent"] as? JsonObject

    /**
     * El caso que motivó la pantalla: los rendimientos de Nu que crecieron $745.856 sin un solo
     * mensaje del banco que capturar. Cuadrar deja el saldo en la cifra del banco **registrando un
     * movimiento**, no sobrescribiendo un número.
     */
    @Test
    fun `cuadrar una cuenta de ahorros la deja en el saldo del banco con un movimiento visible`() = testApplication {
        wireApp()
        createAccount("acc-nu", "SAVINGS", 0L)
        postOpeningEvent("acc-nu", "INCOME", 352_082L, "Saldo inicial")

        val res = cuadrar("acc-nu", 1_097_938L)
        assertEquals(HttpStatusCode.OK, res.status)

        val cuerpo = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val evento = assertNotNull(cuerpo.evento(), "tiene que venir el movimiento que se escribió")
        assertEquals("Ajuste de saldo", evento["category"]!!.jsonPrimitive.content)
        assertEquals("INCOME", evento["type"]!!.jsonPrimitive.content, "subir el saldo de un activo es un ingreso")
        assertEquals(745_856L, evento["amount"]!!.jsonPrimitive.long)
        assertEquals(1_097_938L, accountBalance("acc-nu"))
        assertEquals(2, eventsInDb(), "la apertura y el ajuste, nada más")
    }

    /** Una cuota de manejo que solo salió en el extracto: el saldo baja y el movimiento es un gasto. */
    @Test
    fun `cuadrar hacia abajo registra un gasto por la diferencia`() = testApplication {
        wireApp()
        createAccount("acc-fidu", "SAVINGS", 0L)
        postOpeningEvent("acc-fidu", "INCOME", 352_082L, "Saldo inicial")

        val cuerpo = Json.parseToJsonElement(cuadrar("acc-fidu", 270_730L).bodyAsText()).jsonObject
        assertEquals("EXPENSE", cuerpo.evento()!!["type"]!!.jsonPrimitive.content)
        assertEquals(81_352L, cuerpo.evento()!!["amount"]!!.jsonPrimitive.long)
        assertEquals(270_730L, accountBalance("acc-fidu"))
    }

    /**
     * **El ajuste no ensucia el período.** Es toda la honestidad de esta feature: corregir lo que
     * Movi creía no es plata que entró. El resumen tiene que seguir en cero después de un ajuste de
     * $745.856 sobre una cuenta de ahorros — donde, a diferencia de un crédito, el evento es un
     * INCOME común y lo único que lo deja afuera es su categoría reservada.
     */
    @Test
    fun `el ajuste no cuenta como ingreso ni como gasto del periodo`() = testApplication {
        wireApp()
        createAccount("acc-nu", "SAVINGS", 0L)

        cuadrar("acc-nu", 745_856L)

        val resumen = summary()
        assertEquals(0L, resumen["ingresos"]!!.jsonPrimitive.long, "un ajuste no es un ingreso")
        assertEquals(0L, resumen["egresos"]!!.jsonPrimitive.long)
        assertEquals(745_856L, accountBalance("acc-nu"), "pero el SALDO sí se movió")
    }

    /** Cuadrar contra la misma cifra no escribe nada: repetirlo es inofensivo. */
    @Test
    fun `cuadrar contra el mismo saldo no registra ningun movimiento`() = testApplication {
        wireApp()
        createAccount("acc-nu", "SAVINGS", 0L)
        postOpeningEvent("acc-nu", "INCOME", 352_082L, "Saldo inicial")

        val res = cuadrar("acc-nu", 352_082L)
        assertEquals(HttpStatusCode.OK, res.status)
        assertNull(Json.parseToJsonElement(res.bodyAsText()).jsonObject.evento())
        assertEquals(1, eventsInDb(), "solo la apertura")
    }

    /**
     * Las deudas se cuadran en Créditos, con su cuota y sus intereses a la vista — y una tarjeta no
     * se cuadra: el banco no muestra UN número para ella. La ruta lo dice en vez de escribir un
     * ajuste contra la cifra equivocada.
     */
    @Test
    fun `una tarjeta o un prestamo no se cuadran por esta ruta`() = testApplication {
        wireApp()
        createAccount("acc-card", "CREDIT_CARD", 0L)
        createAccount("acc-loan", "LOAN", 0L)

        assertEquals(HttpStatusCode.UnprocessableEntity, cuadrar("acc-card", 1_000L).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, cuadrar("acc-loan", 1_000L).status)
        assertEquals(0, eventsInDb())
    }

    /** Un monto negativo o absurdo es un dedazo, y se rechaza antes de escribir nada. */
    @Test
    fun `un saldo negativo o fuera de rango se rechaza`() = testApplication {
        wireApp()
        createAccount("acc-nu", "SAVINGS", 0L)

        assertEquals(HttpStatusCode.BadRequest, cuadrar("acc-nu", -1L).status)
        assertEquals(HttpStatusCode.BadRequest, cuadrar("acc-nu", 9_000_000_000_000L).status)
        assertEquals(0, eventsInDb())
    }

    /** Una cuenta que no existe (o que es de otro) no se cuadra. */
    @Test
    fun `cuadrar una cuenta ajena o inexistente es 404`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.NotFound, cuadrar("acc-de-nadie", 1_000L).status)
    }

    /**
     * **Cuándo se cuadró cada cuenta, derivado de los mismos movimientos.** Es lo que la pantalla
     * lee para decir «Cuadrada el 3 de agosto» y lo que el aviso del Inicio usa para saber cuáles
     * llevan demasiado. No hay columna nueva: sale de los eventos, así que no puede separarse de
     * ellos.
     */
    @Test
    fun `la cuenta dice cuando se cuadro por ultima vez y desde cuando existe`() = testApplication {
        wireApp()
        createAccount("acc-nu", "SAVINGS", 0L)
        postOpeningEvent("acc-nu", "INCOME", 352_082L, "Saldo inicial")

        val antes = Json.parseToJsonElement(
            client.get("/api/accounts/acc-nu") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonObject
        assertEquals(null, antes["lastAdjustmentAt"]?.jsonPrimitive?.longOrNull, "todavía no se cuadró nunca")
        assertNotNull(antes["firstEventAt"]?.jsonPrimitive?.longOrNull, "pero ya tiene movimientos")

        cuadrar("acc-nu", 1_097_938L)

        val despues = Json.parseToJsonElement(
            client.get("/api/accounts/acc-nu") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText(),
        ).jsonObject
        assertNotNull(despues["lastAdjustmentAt"]?.jsonPrimitive?.longOrNull, "ahora sí")
    }
}
