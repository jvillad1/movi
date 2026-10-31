package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CanalDeCaptura
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.idDeComprobante
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
import com.jvillada.movi.shared.model.textoDeOrigenMudo
import com.jvillada.movi.shared.model.MOTIVO_DEVUELTO
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Banco mudo» en el server (Ola 2)
 *
 * La regla es de `:core` (`BancoMudoTest`); acá se prueba lo que le pone el server: que lee los
 * días que eligió el dueño (y que `0` lo apaga), que no cuenta los comprobantes compartidos, que
 * cada dueño ve solo lo suyo, y que los días se validan al guardarlos.
 */
class BancoMudoRoutesTest {

    private val testSecret = "test-secret-for-banco-mudo-tests-min-32-chars"
    private val duenoId = "user-dueno-mudo"
    private val otroId = "user-otro-mudo"
    private val json = Json { ignoreUnknownKeys = true }
    private val bogota = ZoneId.of("America/Bogota")
    private val formato = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-banco-mudo-jwtconfig-32")
        Database.connect(
            url = "jdbc:h2:mem:banco_mudo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, Users)
            SchemaUtils.create(Users, SmsMessages)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@mudo.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@mudo.test")
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

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    /** Un mensaje de [bank] de hace [diasAtras] días y un minuto (días enteros, a cualquier hora que corra la prueba). */
    private fun sembrar(
        uid: String,
        bank: String,
        diasAtras: Long,
        id: String = "sms_${uid}_${bank}_$diasAtras",
        texto: String = "Compraste $10.000 en algo",
        motivo: String? = null,
    ) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = ZonedDateTime.now(bogota).minusDays(diasAtras).minusMinutes(1).format(formato)
            it[SmsMessages.bank] = bank
            it[text] = texto
            it[state] = SMS_STATE_CONFIRMED
            it[motivoApartado] = motivo
            it[det] = ""
        }
    }

    private val glim = "Notificación · Glim"
    private val compraDeGlim = "Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim en TOSTAO CAFE Y PAN."
    private val rechazoDeGlim = "Fondos insuficientes: Se rechazó tu pago por \$17.150,00 COP."
    private val promoDeGlim = "Usa tu Glim y obtén 20% de dcto en restaurantes aliados. Aplican TyC."

    /** Glim avisó compras todos los días por tres semanas y dejó de hacerlo hace cinco días. */
    private fun glimConComprasHastaHaceCinco(uid: String) =
        (5L..25L).forEach { sembrar(uid, glim, it, id = "glim_compra_$it", texto = compraDeGlim) }

    /** Bancolombia escribió todos los días durante tres semanas y calló hace cuatro. */
    private fun bancolombiaCalladoHaceCuatroDias(uid: String) = (4L..25L).forEach { sembrar(uid, "85540", it) }

    private suspend fun ApplicationTestBuilder.mudos(uid: String): List<OrigenMudo> {
        val res = client.get("/api/sms/origenes-mudos") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(OrigenMudo.serializer()), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.ponerDias(uid: String, dias: Int) =
        client.put("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"diasParaBancoMudo":$dias}""")
        }

    @Test
    fun `un banco regular que se callo cuatro dias avisa`() = testApplication {
        wireApp()
        bancolombiaCalladoHaceCuatroDias(duenoId)
        val mudos = mudos(duenoId)
        assertEquals(1, mudos.size)
        assertEquals("Bancolombia", mudos.single().nombre)
        assertEquals(CanalDeCaptura.SMS, mudos.single().canal)
        assertEquals(4, mudos.single().diasSinCaptura)
    }

    @Test
    fun `cada duenio ve solo lo suyo`() = testApplication {
        wireApp()
        bancolombiaCalladoHaceCuatroDias(duenoId)
        assertTrue(mudos(otroId).isEmpty())
    }

    @Test
    fun `un comprobante compartido hoy no hace ver viva la captura`() = testApplication {
        wireApp()
        bancolombiaCalladoHaceCuatroDias(duenoId)
        sembrar(duenoId, "Comprobante · recibo.jpg", 0, id = idDeComprobante("doc_hoy"))
        assertEquals(1, mudos(duenoId).size)
    }

    @Test
    fun `los dias los elige el duenio y cero lo apaga`() = testApplication {
        wireApp()
        bancolombiaCalladoHaceCuatroDias(duenoId)

        val perfil = json.decodeFromString(UserProfile.serializer(), ponerDias(duenoId, 7).bodyAsText())
        assertEquals(7, perfil.diasParaBancoMudo)
        assertTrue(mudos(duenoId).isEmpty(), "cuatro días no alcanzan si eligió siete")

        ponerDias(duenoId, 0)
        assertTrue(mudos(duenoId).isEmpty())

        assertEquals(HttpStatusCode.BadRequest, ponerDias(duenoId, 31).status)
        assertEquals(HttpStatusCode.BadRequest, ponerDias(duenoId, -1).status)
    }

    // ── Vuelta 90 · vivo es que lleguen movimientos ─────────────────────────────

    @Test
    fun `un origen que solo manda rechazos y promociones esta mudo, con el motivo`() = testApplication {
        wireApp()
        glimConComprasHastaHaceCinco(duenoId)
        // Después de la última compra siguen llegando avisos todos los días… pero ninguno es una compra.
        (0L..4L).forEach { sembrar(duenoId, glim, it, id = "glim_rechazo_$it", texto = rechazoDeGlim) }
        sembrar(duenoId, glim, 1, id = "glim_promo_1", texto = promoDeGlim)
        // Una promoción que Movi ya apartó al llegar cuenta por su motivo guardado.
        sembrar(duenoId, glim, 2, id = "glim_promo_2", texto = "Cualquier cosa", motivo = "PROMOCION")

        val mudo = mudos(duenoId).single()
        assertEquals("Glim", mudo.nombre)
        assertEquals(CanalDeCaptura.NOTIFICACION, mudo.canal)
        assertEquals(5, mudo.diasSinCaptura, "los días se cuentan desde la última compra, no desde el último aviso")
        assertEquals("llegan rechazos y promociones", mudo.motivo)
        assertEquals(
            "Hace 5 días las notificaciones de Glim no traen movimientos: llegan rechazos y promociones. Revisa la captura",
            textoDeOrigenMudo(mudo),
        )
    }

    @Test
    fun `con compras el origen esta vivo aunque tambien lleguen rechazos`() = testApplication {
        wireApp()
        glimConComprasHastaHaceCinco(duenoId)
        (0L..4L).forEach { sembrar(duenoId, glim, it, id = "glim_rechazo_$it", texto = rechazoDeGlim) }
        sembrar(duenoId, glim, 0, id = "glim_compra_hoy", texto = compraDeGlim)
        assertTrue(mudos(duenoId).isEmpty())
    }

    @Test
    fun `lo que el duenio devolvio como movimiento cuenta como movimiento`() = testApplication {
        wireApp()
        glimConComprasHastaHaceCinco(duenoId)
        sembrar(duenoId, glim, 0, id = "glim_devuelto", texto = rechazoDeGlim, motivo = MOTIVO_DEVUELTO)
        assertTrue(mudos(duenoId).isEmpty(), "«Era un movimiento» gana sobre lo que lee Movi")
    }

    @Test
    fun `un silencio total sigue sin motivo, como antes`() = testApplication {
        wireApp()
        bancolombiaCalladoHaceCuatroDias(duenoId)
        assertNull(mudos(duenoId).single().motivo)
    }
}
