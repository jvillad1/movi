package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AvatarPalette
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.jvillada.movi.shared.model.problemaDeLaQuincena

/**
 * Vuelta 105 · `PUT /api/users/me` con la frecuencia del período (mes o quincena). Mismo arnés que
 * [UserRoutesTest].
 */
class PeriodoQuincenalEnElPerfilTest {

    private val testSecret = "test-secret-for-user-routes-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private val userId = "user-a-profile"
    private val userEmail = "a@profile.test"
    private val userPassword = "una-contrasena-larga-y-tranquila"

    @BeforeTest
    fun setUp() {
        // Las pruebas de cambio de contraseña hacen `POST /api/auth/login` de verdad, y ese token
        // lo firma `JwtConfig` (no el `testSecret` de arriba). Sin esta línea la clase dependía de
        // que otra del mismo fork hubiera resuelto antes el `lazy` del secreto, o de que existiera
        // un `server/.env`: en un worktree nuevo, o corriéndola sola, fallaba con «JWT_SECRET not
        // set» — y como Gradle corre primero las clases que fallaron, seguía fallando. Misma
        // escotilla que `AuthRoutesTest` y `DocumentRoutesTest`.
        System.setProperty("movi.jwt.secret", "test-secret-for-quincena-jwtconfig-min-32-chars")
        RateLimiter.reset()
        Database.connect(
            url    = "jdbc:h2:mem:periodo_quincenal_perfil_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            // Las dos tablas de al lado están porque cambiar la contraseña ahora también sella
            // los enlaces de recuperación pendientes y suelta las suscripciones push de la
            // cuenta — sin ellas el endpoint fallaría acá con «table not found».
            SchemaUtils.drop(PushSubscriptions, PasswordResetTokens, Users)
            SchemaUtils.create(Users, PasswordResetTokens, PushSubscriptions)
            Users.insert {
                it[id]           = userId
                it[email]        = userEmail
                it[name]         = "Juan"
                it[passwordHash] = BCrypt.withDefaults().hashToString(BCRYPT_COST, userPassword.toCharArray())
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
        RateLimiter.reset()
    }

    // ── JWT / harness ────────────────────────────────────────────────────────

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
        val verifier  = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
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

    private suspend fun ApplicationTestBuilder.getProfile() =
        client.get("/api/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun ApplicationTestBuilder.putProfile(body: String) =
        client.put("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }


    private fun fila() = transaction { Users.selectAll().where { Users.id eq userId }.single() }

    private suspend fun ApplicationTestBuilder.perfil() = Json.parseToJsonElement(getProfile().bodyAsText()).jsonObject

    @Test
    fun `sin elegir, el perfil dice MENSUAL y nada mas`() = testApplication {
        wireApp()
        val p = perfil()
        // MENSUAL es el default y no viaja (`encodeDefaults` apagado): el perfil de un mensual es,
        // byte por byte, el de antes.
        assertEquals(null, p["periodFrequency"])
        assertEquals(null, p["periodSecondDay"])
        // La base no se tocó: NULL es MENSUAL.
        assertEquals(null, fila()[Users.periodFrequency])
    }

    @Test
    fun `elegir la quincena del 15 y el ultimo dia la guarda y el perfil la devuelve`() = testApplication {
        wireApp()
        val res = putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15,"periodSecondDay":31,"periodWeekendRule":"DIA_HABIL_ANTERIOR"}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val p = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("QUINCENAL", p["periodFrequency"]!!.jsonPrimitive.content)
        assertEquals("31", p["periodSecondDay"]!!.jsonPrimitive.content)
        assertEquals(15, fila()[Users.periodCutoffDay])
        // Y el server cuenta por quincenas con esos ajustes.
        val ajustes = ajustesDelPeriodoSinSuspenderEnTransaccion()
        assertTrue(ajustes.esQuincenal)
        assertEquals(31, ajustes.segundoDia)
    }

    @Test
    fun `una quincena imposible se rechaza con la frase de la hoja`() = testApplication {
        wireApp()
        val res = putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":16,"periodSecondDay":31}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(problemaDeLaQuincena(16, 31), res.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15}""").status)
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodFrequency":"SEMANAL"}""").status)
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodSecondDay":40}""").status)
        assertEquals(null, fila()[Users.periodFrequency], "nada se guardó")
    }

    /**
     * El APK viejo no sabe de quincenas: solo manda `periodCutoffDay` (y excepciones «AAAA-MM»). No puede
     * dejar una quincena imposible ni escribir claves de mes en una quincena.
     */
    @Test
    fun `un APK viejo no rompe la quincena`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15,"periodSecondDay":31}""").status)
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodCutoffDay":25}""").status)
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodStarts":{"2026-11":"2026-10-30"}}""").status)
        // Un corte que sigue armando quincenas sanas sí pasa.
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodCutoffDay":14}""").status)
        assertEquals("QUINCENAL", fila()[Users.periodFrequency])
    }

    @Test
    fun `las excepciones de la quincena se escriben con su dia de arranque`() = testApplication {
        wireApp()
        putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15,"periodSecondDay":31}""")
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodStarts":{"2026-10-31":"2026-10-30"}}""").status)
        // Un día que no arranca ninguna de estas quincenas se poda (no tumba el mapa entero), y una
        // clave que no es una fecha sí se rechaza.
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodStarts":{"2026-10-20":"2026-10-19","2026-11-15":"2026-11-12"}}""").status)
        assertEquals("""{"2026-11-15":"2026-11-12"}""", fila()[Users.periodStarts])
        assertEquals(HttpStatusCode.BadRequest, putProfile("""{"periodStarts":{"2026-11":"2026-10-30"}}""").status)
    }

    /**
     * Revisión de la 105: cambiar los días de la quincena (también un APK viejo que solo manda el corte)
     * deja excepciones con claves que ya no arrancan ninguna quincena. Se podan al guardar, y «Empezar
     * la quincena hoy» —que manda el mapa entero— nunca queda en un 400 permanente.
     */
    @Test
    fun `cambiar los dias de la quincena poda las excepciones viejas y empezar hoy sigue funcionando`() = testApplication {
        wireApp()
        putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15,"periodSecondDay":31}""")
        putProfile("""{"periodStarts":{"2026-10-31":"2026-10-30","2026-11-15":"2026-11-12"}}""")
        // Como lo haría un APK viejo: solo el corte. La quincena pasa a 14 y 31.
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodCutoffDay":14}""").status)
        assertEquals("""{"2026-10-31":"2026-10-30"}""", fila()[Users.periodStarts], "la del 15 ya no es una quincena")
        // Un cliente que todavía tenía el mapa viejo y le suma la de hoy: pasa, sin la clave muerta.
        assertEquals(
            HttpStatusCode.OK,
            putProfile("""{"periodStarts":{"2026-10-31":"2026-10-30","2026-11-15":"2026-11-12","2026-11-14":"2026-11-12"}}""").status,
        )
        assertEquals("""{"2026-10-31":"2026-10-30","2026-11-14":"2026-11-12"}""", fila()[Users.periodStarts])
    }

    @Test
    fun `cambiar de frecuencia vacia las excepciones a mano`() = testApplication {
        wireApp()
        putProfile("""{"periodCutoffDay":25,"periodStarts":{"2026-11":"2026-10-23"}}""")
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodFrequency":"QUINCENAL","periodCutoffDay":15,"periodSecondDay":31}""").status)
        assertTrue(perfil()["periodStarts"]?.jsonObject?.isEmpty() ?: true, "las excepciones del mes se vaciaron")
        // Volver al mes también las vacía, y el segundo día queda guardado para la próxima vez.
        putProfile("""{"periodStarts":{"2026-10-31":"2026-10-30"}}""")
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodFrequency":"MENSUAL","periodCutoffDay":25}""").status)
        val p = perfil()
        assertEquals(null, p["periodFrequency"], "MENSUAL, el default, no viaja")
        assertEquals("MENSUAL", fila()[Users.periodFrequency])
        assertTrue(p["periodStarts"]?.jsonObject?.isEmpty() ?: true)
    }

    @Test
    fun `un mensual que guarda su corte de siempre no cambia nada mas`() = testApplication {
        wireApp()
        putProfile("""{"periodCutoffDay":25,"periodStarts":{"2026-11":"2026-10-23"}}""")
        assertEquals(HttpStatusCode.OK, putProfile("""{"periodCutoffDay":25,"periodWeekendRule":"DIA_HABIL_ANTERIOR"}""").status)
        val f = fila()
        assertEquals(null, f[Users.periodFrequency], "la frecuencia no se escribe si no viaja")
        assertEquals("""{"2026-11":"2026-10-23"}""", f[Users.periodStarts])
    }

    private fun ajustesDelPeriodoSinSuspenderEnTransaccion() =
        transaction { com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender(userId) }
}
