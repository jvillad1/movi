package com.jvillada.movi.server.observabilidad

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.AlertasDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.TABLAS_DE_OBSERVABILIDAD
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.routes.adminServidorRoutes
import com.jvillada.movi.shared.model.ResumenDelServidor
import com.jvillada.movi.shared.model.SaludDelServidor
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Las corridas de los jobs, las dos alertas con su candado, y `/health/detalle` solo para un admin. */
class JobsAlertasYSaludTest {

    private val secreto = "test-secret-observabilidad-del-servidor-min-32"
    private val admin = "usr_admin_obs"
    private val otro = "usr_otro_obs"
    private val correos = mutableListOf<Pair<List<String>, String>>()

    @BeforeTest
    fun conectar() {
        Metricas.reiniciar()
        Alertas.reiniciar()
        Alertas.mandar = { para, asunto, _ -> correos += para to asunto; true }
        Database.connect(
            url = "jdbc:h2:mem:jobs_alertas_salud;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*TABLAS_DE_OBSERVABILIDAD, Users)
            SchemaUtils.create(Users, *TABLAS_DE_OBSERVABILIDAD)
            Users.insert { it[id] = admin; it[email] = "admin@movi.test"; it[name] = "Admin"; it[passwordHash] = "h" }
            Users.insert { it[id] = otro; it[email] = "otro@movi.test"; it[name] = "Otro"; it[passwordHash] = "h" }
        }
        System.setProperty("movi.admin.userIds", admin)
    }

    @AfterTest
    fun soltar() {
        System.clearProperty("movi.admin.userIds")
        Metricas.reiniciar()
        Alertas.reiniciar()
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    @Test
    fun `un job registra una corrida buena y una mala, y la mala vuelve a lanzar`() = runBlocking {
        assertEquals(7, correrJob("de-prueba") { 7 })
        val falla = IllegalStateException("se cayó con 1843014 en la mano")
        val lanzada = assertFailsWith<IllegalStateException> { correrJob("de-prueba") { throw falla } }
        // La misma: o ella, o la copia con su traza que arma kotlinx.coroutines al cruzar el `withContext`
        // (con las aserciones de la JVM encendidas, como en las pruebas), que la lleva como causa.
        assertTrue(lanzada === falla || lanzada.cause === falla)
        assertEquals(falla.message, lanzada.message)

        val corridas = transaction {
            CorridasDeJobs.selectAll().orderBy(CorridasDeJobs.inicio).map { Triple(it[CorridasDeJobs.nombre], it[CorridasDeJobs.bien], it[CorridasDeJobs.error]) }
        }
        assertEquals(2, corridas.size)
        assertEquals(Triple("de-prueba", true, null), corridas[0])
        assertEquals("de-prueba", corridas[1].first)
        assertFalse(corridas[1].second)
        assertEquals("IllegalStateException: se cayó con # en la mano", corridas[1].third)
    }

    @Test
    fun `lo que pasa dentro de un job dice de que job es`() = runBlocking {
        var donde: String? = null
        correrJob("recordatorios") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { donde = ElPedidoEnCurso.actual()?.donde }
        }
        assertEquals("job:recordatorios", donde)
    }

    // ── Alertas ──────────────────────────────────────────────────────────────

    @Test
    fun `un job que falla dos veces seguidas avisa una sola vez por hora`() = runBlocking {
        var ahora = 1_000_000L * Metricas.UN_MINUTO
        Metricas.reloj = { ahora }
        runCatching { correrJob("cierres") { error("uno") } }
        assertTrue(correos.isEmpty(), "una falla sola no avisa")
        ahora += 1_000
        runCatching { correrJob("cierres") { error("dos") } }
        assertEquals(1, correos.size)
        assertEquals(listOf("admin@movi.test"), correos.single().first)
        assertTrue("cierres" in correos.single().second)

        // La tercera, dentro de la hora: el candado no deja repetir.
        ahora += 30 * Metricas.UN_MINUTO
        runCatching { correrJob("cierres") { error("tres") } }
        assertEquals(1, correos.size)
        // Pasada la hora, sí.
        ahora += 31 * Metricas.UN_MINUTO
        runCatching { correrJob("cierres") { error("cuatro") } }
        assertEquals(2, correos.size)
        // Y una buena en el medio corta la racha.
        ahora += 1_000
        correrJob("cierres") { }
        ahora += 2 * Metricas.UNA_HORA
        runCatching { correrJob("cierres") { error("cinco") } }
        assertEquals(2, correos.size)
    }

    private fun minutoConErrores(inicio: Long, pedidos: Long, e5xx: Long) = transaction {
        MetricasDeRutas.insert {
            it[escala] = ESCALA_MINUTO; it[MetricasDeRutas.inicio] = inicio; it[metodo] = "GET"; it[ruta] = "/api/accounts"
            it[MetricasDeRutas.pedidos] = pedidos; it[errores4xx] = 0; it[errores5xx] = e5xx
            it[p50Ms] = 1; it[p95Ms] = 1; it[p99Ms] = 1; it[maximoMs] = 1; it[cubetas] = Cubetas.aTexto(LongArray(Cubetas.CUANTAS))
        }
    }

    @Test
    fun `la alerta de 5xx se dispara una vez y el candado de una hora funciona`() = runBlocking {
        val minuto = 2_000_000L * Metricas.UN_MINUTO
        // 30 pedidos en los últimos 5 minutos, 4 con 5xx (13 %): pasa el umbral del 10 %.
        (1..5).forEach { minutoConErrores(minuto - it * Metricas.UN_MINUTO, 6, if (it <= 2) 2 else 0) }
        assertTrue(Alertas.revisarLosErrores(minuto + 2_000))
        assertEquals(1, correos.size)
        assertTrue(Alertas.revisarLosErrores(minuto + Metricas.UN_MINUTO + 2_000).not(), "el candado no deja repetir")
        assertEquals(1, correos.size)
        assertEquals(1L, transaction { AlertasDelServidor.selectAll().count() })
        // Una hora y un minuto después, con los mismos errores, vuelve a avisar.
        val despues = minuto + Metricas.UNA_HORA + Metricas.UN_MINUTO
        (1..5).forEach { minutoConErrores(despues - it * Metricas.UN_MINUTO, 6, 2) }
        assertTrue(Alertas.revisarLosErrores(despues + 2_000))
        assertEquals(2, correos.size)
    }

    @Test
    fun `pocos pedidos o pocos errores no alertan`() {
        assertFalse(Alertas.pasaElUmbral(pedidos = 10, e5xx = 9), "con menos de 20 pedidos no se alerta")
        assertFalse(Alertas.pasaElUmbral(pedidos = 100, e5xx = 10), "10 % justo no pasa")
        assertTrue(Alertas.pasaElUmbral(pedidos = 100, e5xx = 11))
        assertFalse(Alertas.encendidas("apagadas"))
        assertFalse(Alertas.encendidas("off"))
        assertTrue(Alertas.encendidas(null))
    }

    // ── /health/detalle y /api/admin/servidor ────────────────────────────────

    private fun token(uid: String): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client").withClaim("userId", uid)
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000)).sign(Algorithm.HMAC256(secreto))

    private fun conLaApp(bloque: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            authentication {
                jwt("jwt") {
                    verifier(JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { authenticate("jwt") { adminServidorRoutes() } }
        }
        bloque()
    }

    @Test
    fun `health detalle contesta 403 a quien no es admin y 401 sin sesion`() = conLaApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/health/detalle").status)
        listOf("/health/detalle", "/api/admin/servidor/resumen", "/api/admin/servidor/servicios", "/api/admin/servidor/jobs", "/api/admin/servidor/salud")
            .forEach { ruta ->
                val r = client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(otro)}") }
                assertEquals(HttpStatusCode.Forbidden, r.status, ruta)
            }
    }

    @Test
    fun `health detalle dice que variables estan pero nunca su valor`() = conLaApp {
        val r = client.get("/health/detalle") { header(HttpHeaders.Authorization, "Bearer ${token(admin)}") }
        assertEquals(HttpStatusCode.OK, r.status)
        val salud = Json { ignoreUnknownKeys = true }.decodeFromString(SaludDelServidor.serializer(), r.bodyAsText())
        assertTrue(salud.baseOk)
        assertNotNull(salud.baseMs)
        assertEquals(LecturasDelPanel.VARIABLES_CRITICAS, salud.variables.map { it.nombre })
        assertTrue(salud.uptimeMs > 0 && salud.memoriaMaximaMb > 0)
    }

    @Test
    fun `la salud con secretos de verdad no saca ninguno`() = runBlocking {
        val secretos = mapOf("JWT_SECRET" to "super-secreto-de-prueba-123", "ANTHROPIC_API_KEY" to "sk-ant-de-prueba-456")
        val salud = LecturasDelPanel.salud(estaDefinida = { !secretos[it].isNullOrBlank() })
        val json = Json.encodeToString(SaludDelServidor.serializer(), salud)
        secretos.values.forEach { assertFalse(it in json, "el valor de un secreto salió en la salud") }
        assertEquals(true, salud.variables.single { it.nombre == "JWT_SECRET" }.presente)
        assertEquals(false, salud.variables.single { it.nombre == "RESEND_API_KEY" }.presente)
    }

    @Test
    fun `el resumen de un admin junta las horas y ordena las rutas lentas`() = runBlocking {
        val ahora = 3_000_000L * Metricas.UN_MINUTO + 10_000
        Metricas.reloj = { ahora - 2 * Metricas.UN_MINUTO }
        repeat(20) { Metricas.registrarPedido("GET", "/api/rapida", 200, 5, usuario = "usr_1") }
        repeat(3) { Metricas.registrarPedido("POST", "/api/lenta", 500, 3_000, usuario = "usr_2") }
        VolcadoDeMetricas.volcar(ahora)

        val r: ResumenDelServidor = LecturasDelPanel.resumen(24, ahora)
        assertEquals(24, r.serie.size)
        assertEquals(23L, r.pedidos)
        assertEquals(3L, r.errores5xx)
        assertEquals("/api/lenta", r.masLentas.first().ruta)
        assertEquals(listOf("/api/lenta"), r.conMas5xx.map { it.ruta })
        assertEquals(2, r.usuariosPicoPorMinuto)
        assertEquals(23L, r.serie.sumOf { it.pedidos })
    }
}
