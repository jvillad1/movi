package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
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
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import com.jvillada.movi.shared.model.EL_EFECTIVO_NO_CAMBIA_DE_TIPO
import com.jvillada.movi.shared.model.UNA_DEUDA_NO_CAMBIA_DE_TIPO
import com.jvillada.movi.shared.model.UNA_INVERSION_NO_CAMBIA_DE_TIPO
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 98 · `PUT /api/accounts/{id}`: editar una cuenta
 *
 * El nombre con la regla de crear, el tipo solo donde no rompe nada, y «Saldo de hoy» como el MISMO
 * ajuste que «Cuadrar con el banco»: un solo movimiento visible, nunca dos por guardar dos veces, y
 * nunca un movimiento reescrito. Y lo que reconoce una cuenta por su nombre —la hermana en dólares de
 * un extracto, el saldo de Skandia— sigue reconociéndola después de renombrarla.
 *
 * Mismo arnés que `AccountRoutesTest`: H2 en memoria, JWT local, la cadena completa de plugins.
 */
class EditarCuentaRoutesTest {

    private val testSecret = "test-secret-for-editar-cuenta-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val duenoId = "user-dueno-editar"
    private val otroId = "user-otro-editar"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:editar_cuenta_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Apartados, Cards, Goals, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users, CardPaymentDismissals,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, CardPaymentDismissals, Cards, Goals, Apartados,
            )
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@editar.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
        }
    }

    private fun tokenFor(uid: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@editar.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    private suspend fun ApplicationTestBuilder.crear(
        id: String,
        nombre: String,
        tipo: String = "SAVINGS",
        moneda: String = "COP",
        uid: String = duenoId,
    ): HttpResponse = client.post("/api/accounts") {
        header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}")
        header(HttpHeaders.ContentType, "application/json")
        setBody("""{"id":"$id","name":"$nombre","type":"$tipo","balance":0,"currency":"$moneda"}""")
    }

    private suspend fun ApplicationTestBuilder.apertura(accountId: String, monto: Long) =
        client.post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(duenoId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"id":"","accountId":"$accountId","type":"INCOME","amount":$monto,
                    "category":"Saldo inicial","description":"Saldo inicial","timestamp":0}""",
            )
        }

    private suspend fun ApplicationTestBuilder.editar(id: String, cuerpo: String, uid: String = duenoId) =
        client.put("/api/accounts/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.leer(id: String): JsonObject =
        Json.parseToJsonElement(
            client.get("/api/accounts/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(duenoId)}") }.bodyAsText(),
        ).jsonObject

    private fun ajustesDe(accountId: String): Int = transaction {
        Events.selectAll().where { (Events.accountId eq accountId) and (Events.category eq ADJUSTMENT_CATEGORY) }.count().toInt()
    }

    private fun movimientosDe(accountId: String): List<Pair<String, Long>> = transaction {
        Events.selectAll().where { Events.accountId eq accountId }.map { it[Events.id] to it[Events.amount] }.sortedBy { it.first }
    }

    @Test
    fun `renombrar da 200 y solo cambia el nombre`() = testApplication {
        wireApp()
        crear("acc-papa", "Bancolombia Ahorros")
        apertura("acc-papa", 1_500_000L)
        val antes = leer("acc-papa")
        val movimientosAntes = movimientosDe("acc-papa")

        val res = editar("acc-papa", """{"name":"  Ahorros del papá  "}""")

        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val cuenta = Json.parseToJsonElement(res.bodyAsText()).jsonObject["account"]!!.jsonObject
        assertEquals("Ahorros del papá", cuenta["name"]!!.jsonPrimitive.content, "recortado, como al crear")
        val despues = leer("acc-papa")
        assertEquals("Ahorros del papá", despues["name"]!!.jsonPrimitive.content)
        for (campo in listOf("type", "currency", "balance")) {
            assertEquals(antes[campo], despues[campo], "«$campo» no se toca al renombrar")
        }
        assertEquals(movimientosAntes, movimientosDe("acc-papa"), "ningún movimiento cambia")
        assertEquals(
            "null",
            Json.parseToJsonElement(res.bodyAsText()).jsonObject["adjustmentEvent"]?.toString() ?: "null",
            "sin saldo no hay ajuste",
        )
    }

    @Test
    fun `la cuenta de otro usuario da 404 y no se toca`() = testApplication {
        wireApp()
        crear("acc-ajena", "Davivienda", uid = otroId)

        val res = editar("acc-ajena", """{"name":"Mía","saldoDeHoy":5000}""")

        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals("Davivienda", transaction { Accounts.selectAll().where { Accounts.id eq "acc-ajena" }.first()[Accounts.name] })
        assertEquals(0, ajustesDe("acc-ajena"))
    }

    @Test
    fun `un nombre vacio o largo da 400, con la misma regla que al crear`() = testApplication {
        wireApp()
        crear("acc-a", "Nequi")
        val largo = "x".repeat(101)

        val vacio = editar("acc-a", """{"name":"   "}""")
        val demasiado = editar("acc-a", """{"name":"$largo"}""")
        assertEquals(HttpStatusCode.BadRequest, vacio.status)
        assertEquals(HttpStatusCode.BadRequest, demasiado.status)

        // La misma regla, y el mismo texto, al crear.
        val crearVacio = crear("acc-b", "  ")
        val crearLargo = crear("acc-c", largo)
        assertEquals(HttpStatusCode.BadRequest, crearVacio.status)
        assertEquals(HttpStatusCode.BadRequest, crearLargo.status)
        assertEquals(vacio.bodyAsText(), crearVacio.bodyAsText())
        assertEquals(demasiado.bodyAsText(), crearLargo.bodyAsText())

        // Y cien justos pasan; un nombre repetido también, como al crear.
        assertEquals(HttpStatusCode.OK, editar("acc-a", """{"name":"${"y".repeat(100)}"}""").status)
        crear("acc-d", "Ahorros")
        assertEquals(HttpStatusCode.OK, editar("acc-a", """{"name":"Ahorros"}""").status)
    }

    @Test
    fun `el saldo de hoy escribe un solo ajuste, el mismo que cuadrar`() = testApplication {
        wireApp()
        crear("acc-s", "Davibank Ahorros")
        assertEquals(HttpStatusCode.Created, apertura("acc-s", 1_000_000L).status)
        val apertura = movimientosDe("acc-s")
        assertEquals(1_000_000L, leer("acc-s")["balance"]!!.jsonPrimitive.long)

        val res = editar("acc-s", """{"name":"Davibank Ahorros","saldoDeHoy":1250000}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(1_250_000L, leer("acc-s")["balance"]!!.jsonPrimitive.long)
        assertEquals(1, ajustesDe("acc-s"), "un solo ajuste")
        val ajuste = Json.parseToJsonElement(res.bodyAsText()).jsonObject["adjustmentEvent"]!!.jsonObject
        assertEquals(250_000L, ajuste["amount"]!!.jsonPrimitive.long)
        assertEquals("INCOME", ajuste["type"]!!.jsonPrimitive.content)
        assertTrue(ajuste["description"]!!.jsonPrimitive.content.startsWith("Ajuste al saldo del banco"), "la descripción del cuadre")

        // Dos guardados iguales no escriben dos ajustes, y el cuadre de siempre tampoco suma otro.
        assertEquals(HttpStatusCode.OK, editar("acc-s", """{"name":"Davibank Ahorros","saldoDeHoy":1250000}""").status)
        val cuadre = client.post("/api/accounts/acc-s/balance-adjustment") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(duenoId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"targetBalance":1250000}""")
        }
        assertEquals(HttpStatusCode.OK, cuadre.status)
        assertEquals(1, ajustesDe("acc-s"), "repetir no anota nada")

        // La apertura sigue intacta: el saldo cambió por un movimiento más, no reescribiendo uno.
        assertTrue(movimientosDe("acc-s").containsAll(apertura), "nunca se reescribe el saldo inicial")
    }

    @Test
    fun `el saldo sigue las reglas del cuadre`() = testApplication {
        wireApp()
        crear("acc-n", "Nu")
        crear("acc-loan", "Libranza 4818", tipo = "LOAN")
        assertEquals(HttpStatusCode.BadRequest, editar("acc-n", """{"name":"Nu","saldoDeHoy":-1}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, editar("acc-loan", """{"name":"Libranza","saldoDeHoy":100}""").status)
        assertEquals("Libranza 4818", leer("acc-loan")["name"]!!.jsonPrimitive.content, "rechazado = nada a medias")
    }

    @Test
    fun `el cambio de tipo prohibido se rechaza y el permitido pasa`() = testApplication {
        wireApp()
        crear("acc-ah", "Ahorros")
        crear("acc-tc", "Master Black 3684", tipo = "CREDIT_CARD")

        val aDeuda = editar("acc-ah", """{"name":"Ahorros","type":"LOAN"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, aDeuda.status)
        assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, aDeuda.bodyAsText())
        assertEquals(HttpStatusCode.UnprocessableEntity, editar("acc-tc", """{"name":"Master","type":"SAVINGS"}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, editar("acc-tc", """{"name":"Master","type":"LOAN"}""").status)
        assertEquals("SAVINGS", leer("acc-ah")["type"]!!.jsonPrimitive.content)
        assertEquals("Master Black 3684", leer("acc-tc")["name"]!!.jsonPrimitive.content, "nada a medias")

        // Inversión y efectivo tampoco: mueven plata (el disponible, los períodos) o lo que Movi hace.
        val aInversion = editar("acc-ah", """{"name":"Ahorros","type":"INVESTMENT"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, aInversion.status)
        assertEquals(UNA_INVERSION_NO_CAMBIA_DE_TIPO, aInversion.bodyAsText())
        val aEfectivo = editar("acc-ah", """{"name":"Ahorros","type":"CASH"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, aEfectivo.status)
        assertEquals(EL_EFECTIVO_NO_CAMBIA_DE_TIPO, aEfectivo.bodyAsText())
        crear("acc-ef", "Efectivo", tipo = "CASH")
        assertEquals(HttpStatusCode.UnprocessableEntity, editar("acc-ef", """{"name":"Efectivo","type":"SAVINGS"}""").status)
        assertEquals("SAVINGS", leer("acc-ah")["type"]!!.jsonPrimitive.content)

        // Entre Ahorros y Corriente sí.
        assertEquals(HttpStatusCode.OK, editar("acc-ah", """{"name":"Ahorros","type":"CHECKING"}""").status)
        assertEquals("CHECKING", leer("acc-ah")["type"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, editar("acc-ah", """{"name":"Ahorros","type":"SAVINGS"}""").status)
        assertEquals("SAVINGS", leer("acc-ah")["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `editar dos veces lo mismo no vuelve a sellar la cuenta`() = testApplication {
        wireApp()
        crear("acc-i", "Itaú")
        editar("acc-i", """{"name":"Itaú Ahorros"}""")
        val sello = transaction { Accounts.selectAll().where { Accounts.id eq "acc-i" }.first()[Accounts.lastEditedAt] }
        Thread.sleep(5)
        editar("acc-i", """{"name":"Itaú Ahorros"}""")
        assertEquals(sello, transaction { Accounts.selectAll().where { Accounts.id eq "acc-i" }.first()[Accounts.lastEditedAt] })
    }

    @Test
    fun `la importacion de extractos sigue encontrando la hermana en dolares tras renombrar`() = testApplication {
        wireApp()
        crear("acc-mb", "Master Black 3684", tipo = "CREDIT_CARD")
        crear("acc-mb-usd", "Master Black 3684 USD", tipo = "CREDIT_CARD", moneda = "USD")

        assertEquals(HttpStatusCode.OK, editar("acc-mb", """{"name":"Mastercard Black Bancolombia 3684"}""").status)

        val cuentas = transaction {
            Accounts.selectAll().where { Accounts.userId eq duenoId }.map {
                CuentaParaImportar(it[Accounts.id], it[Accounts.name], it[Accounts.type], it[Accounts.currency])
            }
        }
        val elegida = cuentas.first { it.id == "acc-mb" }
        assertEquals("acc-mb-usd", cuentaParaLaFila("USD", elegida, cuentas), "por los cuatro dígitos, que siguen en el nombre")
        assertEquals("acc-mb", cuentaParaLaFila("COP", elegida, cuentas))

        // Y el aviso de la hoja dice lo mismo que la importación: con un nombre sin los cuatro, la
        // hermana deja de emparejarse en los dos lados.
        val comoCuentas = transaction {
            Accounts.selectAll().where { Accounts.userId eq duenoId }.map { it.toAccount() }
        }
        val mb = comoCuentas.first { it.id == "acc-mb" }
        assertEquals(emptyList(), com.jvillada.movi.shared.model.hermanasQueSeDesemparejan(mb, "Mastercard 3684", comoCuentas))
        assertEquals(listOf("acc-mb-usd"), com.jvillada.movi.shared.model.hermanasQueSeDesemparejan(mb, "Tarjeta negra", comoCuentas).map { it.id })
        assertEquals(HttpStatusCode.OK, editar("acc-mb", """{"name":"Tarjeta negra"}""").status)
        val despues = transaction {
            Accounts.selectAll().where { Accounts.userId eq duenoId }.map {
                CuentaParaImportar(it[Accounts.id], it[Accounts.name], it[Accounts.type], it[Accounts.currency])
            }
        }
        assertEquals("acc-mb", cuentaParaLaFila("USD", despues.first { it.id == "acc-mb" }, despues), "la importación tampoco la empareja")
    }

    @Test
    fun `Skandia sigue recibiendo su extracto tras renombrar`() = testApplication {
        wireApp()
        crear("acc-sk", "Skandia", tipo = "INVESTMENT")
        crear("acc-otra", "Fiducuenta", tipo = "INVESTMENT")

        assertEquals(HttpStatusCode.OK, editar("acc-sk", """{"name":"Skandia pensión voluntaria"}""").status)

        val resumen = ResumenDelExtractoDeSaldo(ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO, saldo = 106_000_000L, fechaDeCorte = "2026-10-31")
        val propuesta = transaction { propuestaDelExtractoDeSaldo(duenoId, resumen) }
        assertEquals("acc-sk", propuesta.cuentaId, "la cuenta sigue nombrando a Skandia")
        assertEquals("Skandia pensión voluntaria", propuesta.opciones.first().nombre)
    }
}
