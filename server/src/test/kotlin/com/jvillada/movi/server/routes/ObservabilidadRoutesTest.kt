package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.FallosDeLaApp
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.EventoDeUsoEnviado
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.LoteDeUso
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.RespuestaDeUso
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 100 · `POST /api/fallos` (pública, token opcional), `POST /api/uso` (en lotes, lista
 * cerrada), las dos de admin, la retención de 90 días y el costo de contar el uso. Ver
 * [Observabilidad] y `ObservabilidadRoutes.kt`.
 */
class ObservabilidadRoutesTest {

    private val secreto = "test-secret-for-observabilidad-tests-min-32-chars"
    private val json = Json { ignoreUnknownKeys = true }
    private val usuario = "usr-obs"
    private val admin = "usr-admin-obs"
    private val dia = 86_400_000L

    private val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()

    @BeforeTest
    fun setUp() {
        RateLimiter.reset()
        System.setProperty("movi.admin.userIds", admin)
        Database.connect(
            url = "jdbc:h2:mem:observabilidad_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(FallosDeLaApp, UsoDeLaApp, Users)
            SchemaUtils.create(Users, FallosDeLaApp, UsoDeLaApp)
            listOf(usuario, admin).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@obs.test"; it[name] = uid; it[passwordHash] = "h" }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        RateLimiter.reset()
        System.clearProperty("movi.admin.userIds")
    }

    private fun token(uid: String) = JWT.create().withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withExpiresAt(Date(System.currentTimeMillis() + 60_000)).sign(Algorithm.HMAC256(secreto))

    private fun usuarioDePrueba(encabezado: String?): String? = runCatching {
        verificador.verify(encabezado!!.removePrefix("Bearer ")).getClaim("userId").asString()
    }.getOrNull()

    private fun Application.modulo() {
        configureSerialization()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { c -> c.payload.getClaim("userId").asString()?.let { JWTPrincipal(c.payload) } }
            }
        }
        routing {
            fallosRoutes(usuarioDelToken = ::usuarioDePrueba)
            erroresDelClienteRoutes()
            authenticate("jwt") { usoRoutes() }
        }
    }

    private fun ApplicationTestBuilder.app() = application { modulo() }

    private fun fallo(id: String = "f-1", mensaje: String = "For input string: \"27500000\"") = FalloDeLaApp(
        id = id, plataforma = "android", version = "1.90", tipo = "NumberFormatException", mensaje = mensaje,
        stack = "java.lang.NumberFormatException: $mensaje\n\tat com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt:43)",
        hilo = "main", modelo = "samsung SM-A155M", sistema = "Android 14", ocurridoEn = System.currentTimeMillis(),
    )

    private fun filasDeFallos() = transaction { FallosDeLaApp.selectAll().toList() }

    // ── /api/fallos ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `un fallo sin token se guarda sin duenio y limpio`() = testApplication {
        app()
        val res = client.post("/api/fallos") { setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo())) }
        assertEquals(HttpStatusCode.NoContent, res.status)
        val fila = filasDeFallos().single()
        assertEquals("", fila[FallosDeLaApp.userId])
        assertFalse("27500000" in fila[FallosDeLaApp.mensaje], fila[FallosDeLaApp.mensaje])
        assertFalse("27500000" in fila[FallosDeLaApp.stack])
        assertEquals("SmsSyncWorker", fila[FallosDeLaApp.componente])
        assertTrue(fila[FallosDeLaApp.huella].startsWith("NumberFormatException · com.jvillada.movi.sms.SmsSyncWorker"))
    }

    @Test
    fun `con token se atribuye, y uno invalido no lo rechaza`() = testApplication {
        app()
        client.post("/api/fallos") {
            header(HttpHeaders.Authorization, "Bearer ${token(usuario)}")
            setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo("f-a")))
        }.also { assertEquals(HttpStatusCode.NoContent, it.status) }
        client.post("/api/fallos") {
            header(HttpHeaders.Authorization, "Bearer no-es-un-token")
            setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo("f-b")))
        }.also { assertEquals(HttpStatusCode.NoContent, it.status) }
        val duenos = filasDeFallos().associate { it[FallosDeLaApp.id] to it[FallosDeLaApp.userId] }
        assertEquals(mapOf("f-a" to usuario, "f-b" to ""), duenos)
    }

    @Test
    fun `el mismo reporte dos veces se guarda una`() = testApplication {
        app()
        repeat(2) { client.post("/api/fallos") { setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo())) } }
        assertEquals(1, filasDeFallos().size)
    }

    @Test
    fun `demasiado grande es 413 y no guarda nada`() = testApplication {
        app()
        val res = client.post("/api/fallos") {
            setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo().copy(stack = "x".repeat(MAX_CUERPO_DEL_FALLO))))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, res.status)
        assertTrue(filasDeFallos().isEmpty())
    }

    @Test
    fun `el limite por IP frena una rafaga`() = testApplication {
        app()
        val estados = (1..FALLOS_POR_VENTANA + 5).map { i ->
            client.post("/api/fallos") { setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo("f-$i"))) }.status
        }
        assertEquals(FALLOS_POR_VENTANA, estados.count { it == HttpStatusCode.NoContent })
        assertEquals(HttpStatusCode.TooManyRequests, estados.last())
    }

    @Test
    fun `un cuerpo que no es un fallo es 400`() = testApplication {
        app()
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/fallos") { setBody("no") }.status)
    }

    @Test
    fun `el Algo fallo de la web entra a la misma tabla`() = testApplication {
        app()
        client.post("/api/errores-del-cliente") { setBody("""{"mensaje":"TypeError: monto 1234567 null","navegador":"Safari","usuario":"$usuario"}""") }
        val fila = filasDeFallos().single()
        assertEquals("web", fila[FallosDeLaApp.plataforma])
        assertEquals("TypeError", fila[FallosDeLaApp.tipo])
        assertFalse("1234567" in fila[FallosDeLaApp.mensaje])
        assertEquals(usuario, fila[FallosDeLaApp.userId])
    }

    // ── /api/uso ─────────────────────────────────────────────────────────────────────────────

    private fun evento(id: String, nombre: String, props: Map<String, String> = emptyMap(), ruta: String = "Dashboard", sec: Int = 1) =
        EventoDeUsoEnviado(id, nombre, props, ruta, sesionId = "s1", secuencia = sec, plataforma = "android", version = "1.90", ocurridoEn = System.currentTimeMillis())

    private suspend fun ApplicationTestBuilder.mandar(lote: LoteDeUso, uid: String = usuario) = client.post("/api/uso") {
        header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        setBody(json.encodeToString(LoteDeUso.serializer(), lote))
    }

    @Test
    fun `un lote guarda lo valido y descarta lo que no esta en la lista`() = testApplication {
        app()
        val lote = LoteDeUso(
            listOf(
                evento("e1", "PANTALLA", mapOf("pantalla" to "PorRevisar"), ruta = "Dashboard>PorRevisar"),
                evento("e2", "POR_REVISAR", mapOf("resultado" to "CONFIRMADO", "clase" to "AVISO", "aviso_id" to "sms_rt_9c1d2e3f4a5b6c7d"), sec = 2),
                evento("e3", "PANTALLA", mapOf("monto" to "27500"), sec = 3),
                evento("e4", "COMPRA", sec = 4),
                evento("e5", "MOVI_AI", mapOf("origen" to "cuanto gaste en rappi"), sec = 5),
            ),
        )
        val res = mandar(lote)
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(RespuestaDeUso(guardados = 2, descartados = 3), json.decodeFromString<RespuestaDeUso>(res.bodyAsText()))
        // Reenviar el mismo lote no duplica.
        assertEquals(0, json.decodeFromString<RespuestaDeUso>(mandar(lote).bodyAsText()).guardados)
        val filas = transaction { UsoDeLaApp.selectAll().toList() }
        assertEquals(2, filas.size)
        val pantalla = filas.first { it[UsoDeLaApp.id] == "e1" }
        assertEquals("PANTALLA", pantalla[UsoDeLaApp.tipo])
        assertEquals("PorRevisar", pantalla[UsoDeLaApp.detalle])
        assertEquals("Dashboard>PorRevisar", pantalla[UsoDeLaApp.ruta])
        assertEquals("s1", pantalla[UsoDeLaApp.sesionId])
        assertEquals("ACCION", filas.first { it[UsoDeLaApp.id] == "e2" }[UsoDeLaApp.tipo])
    }

    @Test
    fun `uso sin sesion es 401`() = testApplication {
        app()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/uso") { setBody("""{"eventos":[]}""") }.status)
    }

    // ── Admin ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un no admin recibe 403 en las dos de admin`() = testApplication {
        app()
        for (ruta in listOf(
            "/api/admin/fallos", "/api/admin/uso", "/api/admin/uso/usuarios",
            "/api/admin/uso/usuarios/$usuario/sesiones", "/api/admin/uso/sesiones/s1/eventos",
        )) {
            assertEquals(HttpStatusCode.Forbidden, client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(usuario)}") }.status, ruta)
            assertEquals(HttpStatusCode.OK, client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(admin)}") }.status, ruta)
        }
    }

    @Test
    fun `los fallos de la semana se agrupan por huella`() = testApplication {
        app()
        listOf("f1" to "1.89", "f2" to "1.90").forEach { (id, v) ->
            client.post("/api/fallos") {
                header(HttpHeaders.Authorization, "Bearer ${token(usuario)}")
                setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo(id).copy(version = v)))
            }
        }
        client.post("/api/fallos") { setBody(json.encodeToString(FalloDeLaApp.serializer(), fallo("f3").copy(tipo = "IllegalStateException"))) }
        val r = json.decodeFromString<ResumenDeFallos>(client.get("/api/admin/fallos") { header(HttpHeaders.Authorization, "Bearer ${token(admin)}") }.bodyAsText())
        assertEquals(2, r.grupos.size)
        val grande = r.grupos.first()
        assertEquals(2, grande.cuantos)
        assertEquals(1, grande.usuarios)
        assertEquals(listOf("1.90", "1.89"), grande.versiones)
    }

    // ── Retención ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `lo de mas de 90 dias se borra y lo reciente queda`(): Unit = runBlocking {
        val ahora = System.currentTimeMillis()
        Observabilidad.guardarFallo(fallo("viejo"), usuario, ahora - 91 * dia)
        Observabilidad.guardarFallo(fallo("nuevo"), usuario, ahora - 89 * dia)
        Observabilidad.guardarUso(usuario, listOf(evento("u-viejo", "CUENTA_CREADA")), ahora - 91 * dia)
        Observabilidad.guardarUso(usuario, listOf(evento("u-nuevo", "CUENTA_CREADA")), ahora - 1 * dia)
        assertEquals(2, Observabilidad.borrarLoViejo(ahora))
        assertEquals(listOf("nuevo"), filasDeFallos().map { it[FallosDeLaApp.id] })
        assertEquals(listOf("u-nuevo"), transaction { UsoDeLaApp.selectAll().map { it[UsoDeLaApp.id] } })
    }

    @Test
    fun `la captura activa se escribe una vez por dia y origen`(): Unit = runBlocking {
        val ahora = System.currentTimeMillis()
        repeat(3) { Observabilidad.registrarCapturaActiva(usuario, "SMS", ahora) }
        Observabilidad.registrarCapturaActiva(usuario, "CORREO", ahora)
        Observabilidad.registrarCapturaActiva(usuario, "texto libre", ahora)
        val filas = transaction { UsoDeLaApp.selectAll().map { it[UsoDeLaApp.detalle] to it[UsoDeLaApp.plataforma] } }
        assertEquals(setOf("SMS" to "server", "CORREO" to "server"), filas.toSet())
    }

    // ── Lo que se cuenta ─────────────────────────────────────────────────────────────────────

    /** Inserta directo, con la hora que se quiera: [diasAtras] desde hoy. */
    private fun sembrar(
        uid: String, nombre: String, diasAtras: Int, detalle: String? = null, valor: Long? = null, n: Int = 1,
        plataforma: String = "android", sesion: String = "s-$uid-$diasAtras", segundosDespues: Long = 0,
    ) {
        val ahora = System.currentTimeMillis()
        val hora = ahora - diasAtras * dia + segundosDespues * 1000
        val d = Observabilidad.diaDe(hora)
        transaction {
            UsoDeLaApp.batchInsert((1..n).toList(), shouldReturnGeneratedValues = false) { i ->
                this[UsoDeLaApp.id] = "$nombre-$diasAtras-$detalle-$i-${System.nanoTime()}"
                this[UsoDeLaApp.userId] = uid
                this[UsoDeLaApp.tipo] = "ACCION"
                this[UsoDeLaApp.nombre] = nombre
                this[UsoDeLaApp.ruta] = ""
                this[UsoDeLaApp.sesionId] = sesion
                this[UsoDeLaApp.secuencia] = i
                this[UsoDeLaApp.propiedades] = "{}"
                this[UsoDeLaApp.detalle] = detalle
                this[UsoDeLaApp.valor] = valor
                this[UsoDeLaApp.plataforma] = plataforma
                this[UsoDeLaApp.version] = "1.90"
                this[UsoDeLaApp.ocurridoEn] = hora
                this[UsoDeLaApp.dia] = d
                this[UsoDeLaApp.semana] = Observabilidad.semanaDe(d)
                this[UsoDeLaApp.recibidoEn] = hora
            }
        }
    }

    @Test
    fun `activos, embudo, adopcion, sesiones y cohortes`(): Unit = runBlocking {
        // A: hoy y hace 14 días (empezó hace dos semanas y volvió). B: solo hoy. C: solo hace 14 días.
        sembrar("A", "PANTALLA", 0, "Dashboard")
        sembrar("A", "PANTALLA", 14, "Dashboard")
        sembrar("B", "PANTALLA", 0, "PorRevisar")
        sembrar("C", "PANTALLA", 14, "Dashboard")
        sembrar("A", "GUIA_PASO", 14, "BIENVENIDA")
        sembrar("C", "GUIA_PASO", 14, "BIENVENIDA")
        sembrar("A", "GUIA_PASO", 14, "PERIODO")
        sembrar("A", "MOVI_AI", 0, "LIBRE")
        // Sesiones de hoy: A una de 60 s; B dos, de 120 s y 300 s (del primer evento al último).
        sembrar("A", "SEGUNDO_PLANO", 0, sesion = "s-A-0", segundosDespues = 60)
        sembrar("B", "PANTALLA", 0, "Plan", sesion = "b2")
        sembrar("B", "SEGUNDO_PLANO", 0, sesion = "s-B-0", segundosDespues = 120)
        sembrar("B", "SEGUNDO_PLANO", 0, sesion = "b2", segundosDespues = 300)
        // La captura la escribe el server: no hace activo a D.
        sembrar("D", "CAPTURA_ACTIVA", 0, "SMS", plataforma = "server")

        val r = Observabilidad.resumenDeUso(System.currentTimeMillis())
        assertEquals(2, r.activosHoy)
        assertEquals(2, r.activos7)
        assertEquals(3, r.activos30)
        assertEquals(30, r.activosPorDia.size)
        assertEquals("Dashboard", r.pantallas.first().nombre)
        assertEquals(listOf(2, 1, 0), r.embudo.take(3).map { it.usuarios })
        assertEquals(33, r.adopcion.first { it.funcion == "MOVI_AI" }.porcentaje)
        assertEquals(0, r.adopcion.first { it.funcion == "CAPTURA_ACTIVA" }.usuarios, "D no es un activo")
        // Sesiones de 30 días: A hoy (60 s) y hace 14 (0 s), C hace 14 (0 s), B dos (120 y 300): mediana 60.
        assertEquals(60L, r.duracionMedianaSegundos)
        assertEquals(1.7, r.sesionesPorUsuario)
        // La cohorte de hace dos semanas: A y C; A volvió en la semana 2.
        val cohorte = r.cohortes.first { it.usuarios == 2 }
        assertEquals(50, cohorte.semana2)
        assertNull(cohorte.semana4, "la semana 4 de esa cohorte todavía no pasó")
        assertNotNull(cohorte.semana1)
    }

    @Test
    fun `el visor da los usuarios, sus sesiones y la linea de tiempo en orden`() = testApplication {
        app()
        mandar(
            LoteDeUso(
                listOf(
                    evento("v3", "TOQUE", mapOf("accion" to "SIGUIENTE"), ruta = "PrimerUso(paso 2)", sec = 3),
                    evento("v1", "SESION_EMPEZADA", mapOf("origen" to "FRIO"), ruta = "", sec = 1),
                    evento("v2", "PANTALLA", mapOf("pantalla" to "PrimerUso"), ruta = "PrimerUso(paso 1)", sec = 2),
                ),
            ),
        )
        val adminHeader = "Bearer ${token(admin)}"
        val usuarios = json.decodeFromString<List<com.jvillada.movi.shared.model.UsuarioConUso>>(
            client.get("/api/admin/uso/usuarios") { header(HttpHeaders.Authorization, adminHeader) }.bodyAsText(),
        )
        assertEquals(listOf(usuario), usuarios.map { it.id })
        assertEquals("$usuario@obs.test", usuarios.single().correo)
        val sesiones = json.decodeFromString<List<com.jvillada.movi.shared.model.SesionDeUso>>(
            client.get("/api/admin/uso/usuarios/$usuario/sesiones") { header(HttpHeaders.Authorization, adminHeader) }.bodyAsText(),
        )
        assertEquals(3, sesiones.single().eventos)
        assertEquals("android", sesiones.single().plataforma)
        val linea = json.decodeFromString<List<com.jvillada.movi.shared.model.EventoDelRecorrido>>(
            client.get("/api/admin/uso/sesiones/s1/eventos") { header(HttpHeaders.Authorization, adminHeader) }.bodyAsText(),
        )
        assertEquals(listOf("SESION_EMPEZADA", "PANTALLA", "TOQUE"), linea.map { it.nombre })
        assertEquals(listOf("SISTEMA", "PANTALLA", "ACCION"), linea.map { it.tipo })
        assertEquals(mapOf("accion" to "SIGUIENTE"), linea.last().datos)
        assertEquals("PrimerUso(paso 2)", linea.last().ruta)
    }

    /**
     * **El costo**: un usuario de prueba con 60.000 eventos en 90 días, más 200 usuarios con un poco de
     * todo. Lo que se mide es la consulta entera de la pantalla de admin; el número queda en la salida
     * de la prueba. El tope es holgado a propósito (la máquina de CI no es esta): lo que se vigila es
     * que no se vuelva cuadrático.
     */
    @Test
    fun `contar el uso de un usuario con muchos eventos es barato`(): Unit = runBlocking {
        for (d in 0 until 90) sembrar("pesado", "PANTALLA", d, "Dashboard", n = 600)
        for (u in 1..200) {
            sembrar("u$u", "PANTALLA", u % 60, "Plan", n = 5)
            sembrar("u$u", "SEGUNDO_PLANO", u % 60, segundosDespues = 90L + u)
        }
        val inicio = System.nanoTime()
        val r = Observabilidad.resumenDeUso(System.currentTimeMillis())
        val ms = (System.nanoTime() - inicio) / 1_000_000
        println("ObservabilidadRoutesTest · resumenDeUso sobre ${90 * 600 + 200 * 6} eventos: $ms ms (server: ${r.milisegundos} ms)")
        assertTrue(r.activos30 > 100)
        assertTrue(ms < 20_000, "contar el uso tardó $ms ms")
    }
}
