package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.TipoDeAnomalia
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Lo que se sale de lo normal, en el server (Ola 4)
 *
 * Los detectores tienen sus pruebas en `:core`. Acá: que la ruta los alimente con el período del
 * dueño, que **un descarte no vuelva** a aparecer, que cada dueño vea y descarte solo lo suyo, y
 * que «lo normal» de una categoría salga de un período que Movi cubrió entero.
 *
 * Fechas desde el reloj de verdad con corte 1 (mes de calendario): corre cualquier día.
 */
class AnomaliasRoutesTest {

    private val testSecret = "test-secret-for-anomalias-tests-min-32-chars"
    private val duenoId = "user-dueno-anomalias"
    private val otroId = "user-otro-anomalias"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = AppClock.today()
    private val inicioDelMes: LocalDate = hoy.withDayOfMonth(1)
    private var n = 0

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:anomalias_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(AnomaliasDescartadas, Subscriptions, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Subscriptions, AnomaliasDescartadas)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@anomalias.test"; it[name] = uid; it[passwordHash] = "hash" }
                Accounts.insert { it[id] = "acc_$uid"; it[userId] = uid; it[name] = "Bancolombia"; it[type] = "SAVINGS"; it[balance] = 0L }
            }
        }
    }

    private fun anotar(uid: String, nombre: String, monto: Long, fecha: LocalDate, categoria: String = "Comida", tipo: String = "EXPENSE", hora: Long = 12) =
        transaction {
            Events.insert {
                it[id] = "ev_${uid}_${n++}"
                it[userId] = uid
                it[accountId] = "acc_$uid"
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = nombre
                it[timestamp] = appDateToEpochMillis(fecha) + hora * 3_600_000L
                it[reconciliationStatus] = "RECONCILED"
            }
        }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@anomalias.test")
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

    private suspend fun ApplicationTestBuilder.anomalias(uid: String): List<Anomalia> {
        val res = client.get("/api/anomalias") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(Anomalia.serializer()), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.descartar(uid: String, huella: String): HttpResponse =
        client.post("/api/anomalias/descartar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"huella":"$huella"}""")
        }

    /** Dos cobros iguales de Rappi hoy, en la misma cuenta. */
    private fun duplicadoDeHoy(uid: String) {
        anotar(uid, "RAPPI COLOMBIA", 45_900L, hoy, hora = 9)
        anotar(uid, "RAPPI COLOMBIA", 45_900L, hoy, hora = 13)
    }

    @Test
    fun `un cobro duplicado se avisa con sus dos movimientos`() = testApplication {
        application { testModule() }
        duplicadoDeHoy(duenoId)
        val aviso = anomalias(duenoId).single()
        assertEquals(TipoDeAnomalia.COBRO_DUPLICADO, aviso.tipo)
        assertEquals(2, aviso.evidencia.size)
        assertTrue(aviso.evidencia.all { it.cuenta == "Bancolombia" && it.monto == 45_900L })
    }

    @Test
    fun `un descarte no vuelve a aparecer`() = testApplication {
        application { testModule() }
        duplicadoDeHoy(duenoId)
        val huella = anomalias(duenoId).single().huella
        assertEquals(HttpStatusCode.NoContent, descartar(duenoId, huella).status)
        assertTrue(anomalias(duenoId).isEmpty(), "descartado no vuelve")
        // Descartar dos veces no rompe nada.
        assertEquals(HttpStatusCode.NoContent, descartar(duenoId, huella).status)
        assertTrue(anomalias(duenoId).isEmpty())
        // Un tercer cobro igual es otra situación: otra huella, vuelve a avisar.
        anotar(duenoId, "RAPPI COLOMBIA", 45_900L, hoy, hora = 18)
        assertEquals(3, anomalias(duenoId).single().evidencia.size)
    }

    @Test
    fun `cada duenio ve y descarta solo lo suyo`() = testApplication {
        application { testModule() }
        duplicadoDeHoy(duenoId)
        val huella = anomalias(duenoId).single().huella
        assertTrue(anomalias(otroId).isEmpty(), "los cobros del dueño no se le avisan al otro")
        // El otro «descarta» la huella del dueño: queda en SU lista, la del dueño sigue igual.
        assertEquals(HttpStatusCode.NoContent, descartar(otroId, huella).status)
        assertEquals(1, anomalias(duenoId).size)
    }

    @Test
    fun `sin huella o sin token no se descarta nada`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.BadRequest, descartar(duenoId, "").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/anomalias").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/anomalias/descartar").status)
    }

    @Test
    fun `una categoria por encima del mes anterior cubierto se avisa`() = testApplication {
        application { testModule() }
        val mesPasado = inicioDelMes.minusMonths(1)
        // Movi lleva sus cuentas desde antes del mes pasado: ese mes cuenta como «lo normal».
        anotar(duenoId, "Apertura", 10_000_000L, mesPasado.minusDays(5), OPENING_CATEGORY, tipo = "INCOME")
        anotar(duenoId, "CARULLA", 400_000L, mesPasado.plusDays(3))
        anotar(duenoId, "CARULLA", 1_000_000L, hoy, hora = 8)
        anotar(duenoId, "CARULLA", 600_000L, hoy, hora = 10)
        val aviso = anomalias(duenoId).single { it.tipo == TipoDeAnomalia.CATEGORIA_POR_ENCIMA }
        assertTrue("lo normal es \$400.000 (el período anterior)" in aviso.detalle, aviso.detalle)
    }

    @Test
    fun `sin un mes cubierto entero no hay normal contra que medir`() = testApplication {
        application { testModule() }
        // Empezó a usar Movi este mes: nada es «por encima» de nada.
        anotar(duenoId, "Apertura", 10_000_000L, inicioDelMes, OPENING_CATEGORY, tipo = "INCOME", hora = 1)
        anotar(duenoId, "CARULLA", 1_600_000L, hoy)
        assertTrue(anomalias(duenoId).none { it.tipo == TipoDeAnomalia.CATEGORIA_POR_ENCIMA })
    }
}
