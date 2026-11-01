package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.*
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.screens.seedScreens
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.debeAbrirseLaGuiaDePrimerUso
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Una cuenta creada a medias arranca (vuelta 96)
 *
 * El 1-nov-2026 una cuenta recién creada desde la web no podía entrar. Una cuenta así es normal: la
 * gente se registra, para y vuelve otro día. En la base queda con **todas** las columnas opcionales de
 * `users` en NULL (período, regla, `period_starts`, primer uso, ahorro, avisos…) y sin una sola fila en
 * ninguna otra tabla. Esta prueba arma justo eso, sin escribir nada más, y pide todo lo que el Inicio
 * y la guía de primer uso piden al arrancar: tiene que dar 200 (o el 204 de lo que no hay), nunca un
 * 500. Y el perfil tiene que salir con valores sanos, para que el cliente no reciba un `null` donde
 * espera un número.
 */
class LaCuentaAMedioCrearArrancaTest {

    private val secreto = "test-secret-la-cuenta-a-medio-crear-min-32"
    private val uid = "usr_a_medio_crear"

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, RecurringOccurrences,
        OccurrenceRejections, SmsMessages, Credits, Cards, Subscriptions, PushSubscriptions, Screens,
        PasswordResetTokens, CardPaymentDismissals, Goals, CategoryPrefs, Documents, StatementImportMatches, AiTurns,
        KnownDestinations, EnlacesCompartidos, LecturasDePapeles, ConversacionesDelAsistente, AccionesPropuestas,
        MemoriaDelAsistente, AnomaliasDescartadas, DestinosDescartados, DebitosDescartados, ReglasDeComercio,
        CierresEnviados, CierresDescartados, Apartados, InicioPersonalizado, Espacios, GastosDelEspacio,
        LiquidacionesDelEspacio, CorreosDescartados, CierresGuardados,
    )

    /** Lo que piden el Inicio, la guía de primer uso y la hoja «Agregar» al arrancar (logs de prod y del navegador). */
    private val rutasDelArranque = listOf(
        "/api/users/me",
        "/api/screens/dashboard",
        "/api/accounts",
        "/api/cards",
        "/api/credits",
        "/api/apartados",
        "/api/budgets",
        "/api/subscriptions",
        "/api/caja-proyectada/gasto-del-dia-a-dia",
        "/api/finance-summary?scope=SELF",
        "/api/anomalias?conDeudas=1&conRecurrentes=1",
        "/api/espacios/propuestas",
        "/api/debitos-automaticos?conEsperados=1",
        "/api/payments/upcoming?conAbonos=1",
        "/api/payments/occurrences?conAbonos=1",
        "/api/cuadres-por-aviso",
        "/api/cierre-del-periodo",
        "/api/events/by-day?ultimos=3",
        "/api/destinos/sugeridos",
        "/api/dashboard/summary?scope=SELF",
        "/api/recurring-rules?conAbonos=1",
        "/api/inicio/preferencias",
        "/api/categories",
        "/api/events",
        "/api/espacios",
        "/api/cuentas/de-cada-uso",
        "/api/categorias/memoria",
        "/api/extractos-por-subir",
        "/api/asistente/resumen",
    )

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:la_cuenta_a_medio_crear;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            // Solo lo obligatorio, como lo deja `POST /api/auth/register`: todo lo demás, NULL.
            Users.insert {
                it[id] = uid
                it[email] = "caro@movi.test"
                it[name] = "Carolina Restrepo"
                it[passwordHash] = "hash"
            }
        }
        // Lo global, como en cada arranque del server: la definición del Inicio no es del usuario.
        seedScreens()
    }

    @AfterTest fun tearDown() = RateLimiter.reset()

    private fun token(): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "caro@movi.test")
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

    @Test
    fun `todo lo que pide el arranque da 200 con una cuenta a medio crear`() = testApplication {
        application { modulo() }
        val fallas = rutasDelArranque.mapNotNull { ruta ->
            val res = client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token()}") }
            if (res.status == HttpStatusCode.OK || res.status == HttpStatusCode.NoContent) null
            else "$ruta → ${res.status} ${res.bodyAsText().take(200)}"
        }
        assertTrue(fallas.isEmpty(), "rutas del arranque que fallan con una cuenta a medio crear:\n" + fallas.joinToString("\n"))
    }

    @Test
    fun `el perfil sale con valores sanos y pide la guia de primer uso`() = testApplication {
        application { modulo() }
        val crudo = client.get("/api/users/me") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.bodyAsText()
        // Como lo lee la app: el mismo modelo de :core, sin tolerancias extra.
        val perfil = Json { ignoreUnknownKeys = true }.decodeFromString(UserProfile.serializer(), crudo)
        assertEquals(1, perfil.periodCutoffDay)
        assertTrue(perfil.periodStarts.isEmpty())
        assertEquals(null, perfil.primerUsoEn)
        assertTrue(perfil.avatarColor.isNotBlank())
        assertTrue(debeAbrirseLaGuiaDePrimerUso(perfil, emptyList()), "una cuenta sin nada tiene que caer en la guía")
    }

    @Test
    fun `el reporte de Algo fallo es publico en el server de verdad`() = testApplication {
        application { modulo() }
        val res = client.post("/api/errores-del-cliente") { setBody("""{"mensaje":"x","navegador":"y"}""") }
        assertEquals(HttpStatusCode.NoContent, res.status)
    }
}
