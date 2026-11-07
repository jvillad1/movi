package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.espacios.resolverDeCategoriasDe
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 138 · lo que Movi propone dice el nombre que el dueño le puso
 *
 * Con «Transporte» (del catálogo) renombrada a «Movilidad», un aviso de Uber —que las palabras clave
 * leen como «Transporte»— se propone en «Movilidad», en Revisar (`/parse`) y en la bandeja. Sin el
 * alias, el parser volvía al nombre viejo por la espalda y el primer aviso confirmado partía la
 * categoría en dos. Falla sin el cambio (y, antes, renombrar ni siquiera se dejaba: 422).
 */
class LaCategoriaRenombradaEnLasPropuestasTest {

    private val secreto = "test-secret-for-la-categoria-renombrada-min-32"
    private val uid = "user-renombrada"
    private val ahorros = "acc-ahorros-4057"

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents,
        Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
        CategoryPrefs, RecurringOccurrences, OccurrenceRejections, Apartados,
        KnownDestinations, DestinosDescartados, Espacios, GastosDelEspacio, LiquidacionesDelEspacio,
        Documents, Subscriptions, AnomaliasDescartadas,
    )

    private val textoDeUber =
        "Bancolombia: Compraste \$18.500,00 en UBER RIDES con tu T.Deb *4057, el 30/10/2026 a las 15:10."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:la_categoria_renombrada_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "renombrada@movi.test"; it[name] = "Caro"; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = ahorros; it[userId] = uid; it[name] = "Bancolombia Ahorros 4057"; it[type] = "SAVINGS"; it[currency] = "COP" }
            SmsMessages.insert {
                it[id] = "sms-uber"
                it[userId] = uid
                it[time] = ZonedDateTime.now(ZoneId.of("America/Bogota")).minusHours(2)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                it[bank] = "85540"
                it[text] = textoDeUber
                it[state] = "pending"
                it[det] = ""
            }
        }
    }

    private fun token(): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "renombrada@movi.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(secreto))

    private fun Application.modulo() {
        configureSerialization()
        val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun ApplicationTestBuilder.parse(id: String): ParsedSms =
        cliente().get("/api/sms/$id/parse") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private suspend fun ApplicationTestBuilder.propuestas(): PropuestasDeLaBandeja =
        cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private suspend fun ApplicationTestBuilder.renombrar(from: String, to: String): HttpStatusCode =
        cliente().post("/api/categories/rename") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"from":"$from","to":"$to"}""")
        }.status

    @Test
    fun `un aviso que salia en una del catalogo sale con el nombre nuevo`() = testApplication {
        application { modulo() }
        assertEquals("Transporte", parse("sms-uber").category, "antes de renombrar, la del catálogo")

        assertEquals(HttpStatusCode.OK, renombrar("Transporte", "Movilidad"))
        assertEquals("Movilidad", parse("sms-uber").category)
        assertEquals("Movilidad", propuestas().propuestas.single().leido?.category)

        // Y al volver al nombre de Movi, el aviso vuelve a decir «Transporte».
        assertEquals(HttpStatusCode.OK, renombrar("Movilidad", "Transporte"))
        assertEquals("Transporte", parse("sms-uber").category)
    }

    /** El mismo hueco al unificar, cerrado con la misma función: las cifras no cambian. */
    @Test
    fun `un aviso de una del catalogo unificada sale con el destino`() = testApplication {
        application { modulo() }
        transaction {
            Events.insert {
                it[id] = "e-carro"
                it[userId] = uid
                it[accountId] = ahorros
                it[type] = "EXPENSE"
                it[amount] = 40_000
                it[currency] = "COP"
                it[category] = "Carro"
                it[description] = "Gasolina"
                it[timestamp] = System.currentTimeMillis() - 86_400_000L
            }
        }
        val res = cliente().post("/api/categories/merge") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"from":"Transporte","into":"Carro"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("Carro", parse("sms-uber").category)
    }

    @Test
    fun `el resolutor del hogar conectado tambien dice el nombre nuevo`() = testApplication {
        application { modulo() }
        assertEquals(HttpStatusCode.OK, renombrar("Vivienda", "Hogar"))
        val resolver = transaction { resolverDeCategoriasDe(uid) }
        assertEquals("Hogar", resolver("Vivienda"))
    }
}
