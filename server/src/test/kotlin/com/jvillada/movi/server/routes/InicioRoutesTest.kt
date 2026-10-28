package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.InicioPersonalizado
import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.screens.seedScreens
import com.jvillada.movi.shared.model.DASHBOARD_LAYOUT_VERSION
import com.jvillada.movi.shared.model.PARA_REVISAR_COMPACTO
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * «Editar inicio» (Ola 3): `GET/PUT /api/inicio/preferencias` y cómo `GET /api/screens/dashboard` la
 * aplica al servir Hoy. Mismo arnés que `ScreenRoutesTest`: H2 en memoria propia, JWT de prueba y la
 * cadena entera de plugins.
 */
class InicioRoutesTest {

    private val testSecret = "test-secret-for-inicio-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userA = "user-a-inicio"
    private val userB = "user-b-inicio"

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:inicio_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(InicioPersonalizado, Screens, Users)
            SchemaUtils.create(Users, Screens, InicioPersonalizado)
            listOf(userA, userB).forEach { uid ->
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@inicio.test"
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
            seedScreens()
        }
    }

    private fun token(uid: String): String = "Bearer " + JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", uid)
        .withClaim("email", "$uid@inicio.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000L))
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

    private suspend fun ApplicationTestBuilder.guardar(uid: String, p: PreferenciasDelInicio): HttpResponse =
        client.put("/api/inicio/preferencias") {
            header(HttpHeaders.Authorization, token(uid))
            header(HttpHeaders.ContentType, "application/json")
            setBody(json.encodeToString(p))
        }

    private suspend fun ApplicationTestBuilder.leer(uid: String): PreferenciasDelInicio {
        val res = client.get("/api/inicio/preferencias") { header(HttpHeaders.Authorization, token(uid)) }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.hoy(uid: String, ifNoneMatch: Int? = null, crudo: Boolean = false): HttpResponse =
        client.get("/api/screens/dashboard" + if (crudo) "?crudo=1" else "") {
            header(HttpHeaders.Authorization, token(uid))
            ifNoneMatch?.let { header(HttpHeaders.IfNoneMatch, it.toString()) }
        }

    private suspend fun HttpResponse.definicion(): ScreenDefinition = json.decodeFromString(bodyAsText())

    private val ordenDeMovi = listOf("CUENTAS", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA")

    @Test
    fun `sin nada guardado devuelve las listas vacías y el orden de Movi`() = testApplication {
        wireApp()
        assertEquals(PreferenciasDelInicio(ordenDeMovi = ordenDeMovi), leer(userA))
    }

    @Test
    fun `guardar y leer, limpia lo que no es configurable`() = testApplication {
        wireApp()
        val res = guardar(
            userA,
            PreferenciasDelInicio(
                orden = listOf("GASTO_POR_CATEGORIA", "HERO_BALANCE", "GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO"),
                ocultas = listOf("CUENTAS", "BANNER", "ALERTS"),
                visibles = listOf("PATRIMONIO", "CUENTAS"),
            ),
        )
        assertEquals(HttpStatusCode.OK, res.status)
        val esperada = PreferenciasDelInicio(
            orden = listOf("GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO"),
            ocultas = listOf("CUENTAS"),
            visibles = listOf("PATRIMONIO"),
            ordenDeMovi = ordenDeMovi,
        )
        assertEquals(esperada, json.decodeFromString<PreferenciasDelInicio>(res.bodyAsText()))
        assertEquals(esperada, leer(userA))
    }

    @Test
    fun `Hoy sale con el orden, sin las ocultas y con las opcionales prendidas`() = testApplication {
        wireApp()
        guardar(
            userA,
            PreferenciasDelInicio(
                orden = listOf("GASTO_POR_CATEGORIA", "PATRIMONIO", "CHECKLIST_DEL_PERIODO", "CUENTAS"),
                ocultas = listOf("CUENTAS"),
                visibles = listOf("PATRIMONIO"),
            ),
        )
        val res = hoy(userA)
        assertEquals(HttpStatusCode.OK, res.status)
        val def = res.definicion()
        assertEquals(
            listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "GASTO_POR_CATEGORIA", "PATRIMONIO", "CHECKLIST_DEL_PERIODO", "ALERTS", "BANNER"),
            def.sections.map { it.type },
        )
        assertNotEquals(DASHBOARD_LAYOUT_VERSION, def.version, "la versión cambia: el 304 no puede dejar el Hoy viejo")

        // El cliente que tenía la versión global recibe la nueva; con la nueva, 304.
        assertEquals(HttpStatusCode.OK, hoy(userA, ifNoneMatch = DASHBOARD_LAYOUT_VERSION).status)
        assertEquals(HttpStatusCode.NotModified, hoy(userA, ifNoneMatch = def.version).status)

        // El Editor de pantallas pide la de todos.
        val cruda = hoy(userA, crudo = true).definicion()
        assertEquals(defaultDashboardDefinition().sections, cruda.sections)
        assertEquals(DASHBOARD_LAYOUT_VERSION, cruda.version)
    }

    @Test
    fun `una sección que la preferencia no nombra aparece en su lugar por defecto`() = testApplication {
        wireApp()
        // Después de que el dueño eligió, el Editor agregó una lista de enlaces después del héroe.
        val conEnlaces = listOf(defaultDashboardDefinition().sections.first(), ScreenSection(type = "LINK_LIST", title = "Enlaces")) +
            defaultDashboardDefinition().sections.drop(1)
        transaction {
            Screens.update({ Screens.slug eq "dashboard" }) {
                it[sectionsJson] = json.encodeToString(conEnlaces)
            }
        }
        guardar(userA, PreferenciasDelInicio(orden = listOf("GASTO_POR_CATEGORIA", "CHECKLIST_DEL_PERIODO", "CUENTAS")))
        assertEquals(
            listOf("HERO_BALANCE", "LINK_LIST", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "GASTO_POR_CATEGORIA", "CHECKLIST_DEL_PERIODO", "CUENTAS", "ALERTS", "BANNER"),
            hoy(userA).definicion().sections.map { it.type },
        )
    }

    @Test
    fun `la preferencia de uno no toca el Hoy del otro`() = testApplication {
        wireApp()
        guardar(userA, PreferenciasDelInicio(ocultas = listOf("CUENTAS"), visibles = listOf("PATRIMONIO")))
        assertEquals(PreferenciasDelInicio(ordenDeMovi = ordenDeMovi), leer(userB))
        val deB = hoy(userB).definicion()
        assertEquals(defaultDashboardDefinition().sections, deB.sections)
        assertEquals(DASHBOARD_LAYOUT_VERSION, deB.version)
        assertTrue(hoy(userA).definicion().sections.none { it.type == "CUENTAS" })
        assertTrue(hoy(userA).definicion().sections.any { it.type == "PATRIMONIO" })
        assertTrue(deB.sections.none { it.type == "PATRIMONIO" })
    }

    @Test
    fun `volver al orden de Movi borra la fila y Hoy vuelve a la versión global`() = testApplication {
        wireApp()
        guardar(userA, PreferenciasDelInicio(ocultas = listOf("CUENTAS")))
        assertEquals(1L, transaction { InicioPersonalizado.selectAll().count() })
        val res = guardar(userA, PreferenciasDelInicio())
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0L, transaction { InicioPersonalizado.selectAll().count() })
        val def = hoy(userA).definicion()
        assertEquals(DASHBOARD_LAYOUT_VERSION, def.version)
        assertEquals(defaultDashboardDefinition().sections, def.sections)
    }

    @Test
    fun `una preferencia ilegible o enorme se rechaza sin tocar nada`() = testApplication {
        wireApp()
        val ilegible = client.put("/api/inicio/preferencias") {
            header(HttpHeaders.Authorization, token(userA))
            header(HttpHeaders.ContentType, "application/json")
            setBody("{\"orden\": 3}")
        }
        assertEquals(HttpStatusCode.BadRequest, ilegible.status)
        val enorme = guardar(userA, PreferenciasDelInicio(orden = List(40) { "CUENTAS" }))
        assertEquals(HttpStatusCode.BadRequest, enorme.status)
        assertEquals(0L, transaction { InicioPersonalizado.selectAll().count() })
    }

    @Test
    fun `la línea compacta no se puede guardar en el Hoy de todos`() {
        // Es un tipo personal: si un Editor viejo guardara la definición de una persona, se rechaza.
        val conCompacto = defaultDashboardDefinition().sections.map {
            if (it.type == "ALERTS") ScreenSection(type = PARA_REVISAR_COMPACTO) else it
        }
        assertTrue(com.jvillada.movi.server.screens.validateDefinition(conCompacto) != null)
    }
}
