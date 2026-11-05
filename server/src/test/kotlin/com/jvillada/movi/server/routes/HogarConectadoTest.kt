package com.jvillada.movi.server.routes

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.cuenta.borrarTodoLoDe
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.EspaciosConectados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.InvitacionesAEspacio
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurrentesDelHogar
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.InvitacionCreada
import com.jvillada.movi.shared.model.InvitacionVista
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.netoDe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import com.jvillada.movi.server.cuenta.TABLAS_DEL_USUARIO
import com.jvillada.movi.server.cuenta.TABLAS_DE_DOS_USUARIOS
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
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
 * # El hogar conectado en el server (vuelta 131)
 *
 * Juan ([juan]) tiene «Casa con Caro» con su tercero `dst_caro`; Caro ([caro]) también usa Movi. Juan la
 * invita con un código, ella acepta y desde ahí cada uno ve lo que el otro reparta en ese grupo, y nada
 * más. Cada uno sigue siendo dueño de sus filas.
 */
class HogarConectadoTest {

    private val secreto = "test-secret-for-hogar-conectado-tests-min-32-chars"
    private val json = Json { ignoreUnknownKeys = true }
    private val juan = "user-juan-hc"
    private val caro = "user-caro-hc"
    private val otra = "user-otra-hc"

    /** Las de las rutas y, para borrar una cuenta entera, todas las del borrado. */
    private val tablas get() = (listOf(
        Users, Accounts, StatementImports, Events, VoidEvents,
        Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
        CategoryPrefs, RecurringOccurrences, OccurrenceRejections, Apartados,
        KnownDestinations, Espacios, GastosDelEspacio, LiquidacionesDelEspacio,
        Documents, Subscriptions, AnomaliasDescartadas, RecurrentesDelHogar,
        InvitacionesAEspacio, EspaciosConectados,
    ) + TABLAS_DEL_USUARIO.map { it.tabla } + TABLAS_DE_DOS_USUARIOS.map { it.tabla }).distinctBy { it.tableName }.toTypedArray()

    @BeforeTest
    fun setUp() {
        IntentosDeCodigo.reset()
        Database.connect(
            url = "jdbc:h2:mem:hogar_conectado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            listOf(juan to "Juan Villada", caro to "Caro Restrepo", otra to "Otra Persona").forEach { (id, nombre) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@hc.test"
                    it[name] = nombre
                    it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[Accounts.id] = "acc-$id"
                    it[userId] = id
                    it[name] = "Ahorros de $nombre"
                    it[type] = "SAVINGS"
                    it[currency] = "COP"
                }
            }
            listOf(Triple("dst_caro", juan, "Caro"), Triple("dst_mama", juan, "Mamá"), Triple("dst_juan_de_otra", otra, "Juan")).forEach { (id, uid, nombre) ->
                KnownDestinations.insert {
                    it[KnownDestinations.id] = id
                    it[userId] = uid
                    it[KnownDestinations.nombre] = nombre
                    it[numero] = ""
                    it[createdAt] = 0L
                }
            }
        }
    }

    @AfterTest
    fun tearDown() = IntentosDeCodigo.reset()

    /**
     * Ahora. Lo del otro lado cuenta desde que se conectaron (`desde`), así que lo que se anota en una
     * prueba después de conectar lleva la hora del momento, no una fija de antes.
     */
    private val hoy: Long get() = System.currentTimeMillis()

    private fun evento(id: String, monto: Long, uid: String, categoria: String = "Mercado", descripcion: String = "Mercado", momento: Long = hoy) =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = "acc-$uid"
                it[type] = "EXPENSE"
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = descripcion
                it[timestamp] = momento
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
            }
        }

    private fun token(uid: String): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@hc.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000))
        .sign(Algorithm.HMAC256(secreto))

    private fun Application.modulo() {
        configureSerialization()
        install(CallLogging) { level = Level.INFO }
        val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun conApp(bloque: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { modulo() }
        bloque()
    }

    private suspend fun ApplicationTestBuilder.post(ruta: String, cuerpo: String, uid: String): HttpResponse =
        client.post(ruta) {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.get(ruta: String, uid: String): HttpResponse =
        client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }

    private suspend fun ApplicationTestBuilder.borrar(ruta: String, uid: String): HttpResponse =
        client.delete(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }

    private suspend fun ApplicationTestBuilder.crearLaCasa(id: String = "esp_casa", miembros: String = """[{"id":"dst_caro"}]""") =
        assertEquals(HttpStatusCode.Created, post("/api/espacios", """{"id":"$id","nombre":"Casa con Caro","miembros":$miembros}""", juan).status)

    private suspend fun ApplicationTestBuilder.invitar(espacio: String = "esp_casa", uid: String = juan): InvitacionCreada {
        val r = post("/api/espacios/$espacio/invitacion", "{}", uid)
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        return json.decodeFromString(r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.aceptar(codigo: String, uid: String = caro): HttpResponse =
        post("/api/espacios/invitaciones/aceptar", """{"codigo":"$codigo"}""", uid)

    private suspend fun ApplicationTestBuilder.ver(codigo: String, uid: String = caro): HttpResponse =
        post("/api/espacios/invitaciones/ver", """{"codigo":"$codigo"}""", uid)

    /** Juan crea la casa, invita y Caro acepta: devuelve el grupo nuevo de Caro. */
    private suspend fun ApplicationTestBuilder.conectar(): Espacio {
        crearLaCasa()
        val r = aceptar(invitar().codigo)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.detalle(espacio: String, uid: String): DetalleDelEspacio {
        val r = get("/api/espacios/$espacio", uid)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(r.bodyAsText())
    }

    // ── El código ────────────────────────────────────────────────────────────

    @Test
    fun `invitar, ver y aceptar crea el grupo de Caro y conecta los dos`() = conApp {
        crearLaCasa()
        val inv = invitar()
        assertEquals(6, inv.codigo.length)
        assertTrue(inv.venceEn > System.currentTimeMillis() + 6L * 24 * 60 * 60 * 1000, "vence a los 7 días")
        assertEquals(HttpStatusCode.OK, get("/api/espacios/esp_casa/invitacion", juan).status, "la pendiente")

        // El campo acepta minúsculas, espacios y guiones.
        val escrito = inv.codigo.lowercase().chunked(3).joinToString(" - ")
        val vista = ver(escrito)
        assertEquals(HttpStatusCode.OK, vista.status, vista.bodyAsText())
        assertEquals(InvitacionVista(nombreDelGrupo = "Casa con Caro", quienInvita = "Juan"), json.decodeFromString<InvitacionVista>(vista.bodyAsText()))

        val r = aceptar(escrito)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val deCaro: Espacio = json.decodeFromString(r.bodyAsText())
        assertEquals("Casa con Caro", deCaro.nombre)
        assertEquals(listOf(MIEMBRO_YO, deCaro.conexion!!.miembroId), deCaro.miembros.map { it.id })
        assertEquals("Juan", deCaro.conexion!!.nombre, "un tercero nuevo con el nombre del perfil de quien invita")

        val deJuan: List<Espacio> = json.decodeFromString(get("/api/espacios", juan).bodyAsText())
        assertEquals("dst_caro", deJuan.single().conexion!!.miembroId)
        assertEquals("Caro", deJuan.single().conexion!!.nombre)
        assertEquals(HttpStatusCode.NoContent, get("/api/espacios/esp_casa/invitacion", juan).status, "ya no hay pendiente")
        assertEquals(1L, transaction { EspaciosConectados.selectAll().count() })
    }

    @Test
    fun `quien acepta reusa su tercero con el mismo nombre normalizado`() = conApp {
        crearLaCasa()
        val r = aceptar(invitar().codigo, uid = otra)
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("dst_juan_de_otra", json.decodeFromString<Espacio>(r.bodyAsText()).conexion!!.miembroId)
    }

    @Test
    fun `vencido, usado, cancelado o reemplazado dan el mismo 404, y el propio 400`() = conApp {
        crearLaCasa()
        val primero = invitar()
        assertEquals(HttpStatusCode.BadRequest, ver(primero.codigo, uid = juan).status, "el propio")
        assertEquals(HttpStatusCode.BadRequest, aceptar(primero.codigo, uid = juan).status, "el propio")

        val segundo = invitar()
        val reemplazado = ver(primero.codigo)
        assertEquals(HttpStatusCode.NotFound, reemplazado.status, "generar otro invalida el anterior")

        assertEquals(HttpStatusCode.NoContent, borrar("/api/espacios/esp_casa/invitacion", juan).status)
        val cancelado = ver(segundo.codigo)
        assertEquals(HttpStatusCode.NotFound, cancelado.status)
        assertEquals(reemplazado.bodyAsText(), cancelado.bodyAsText(), "el mismo 404")

        val tercero = invitar()
        transaction { InvitacionesAEspacio.update({ InvitacionesAEspacio.canceladaEn.isNull() }) { it[venceEn] = System.currentTimeMillis() - 1 } }
        assertEquals(HttpStatusCode.NotFound, ver(tercero.codigo).status, "vencido")

        val cuarto = invitar()
        assertEquals(HttpStatusCode.OK, aceptar(cuarto.codigo).status)
        assertEquals(HttpStatusCode.NotFound, aceptar(cuarto.codigo, uid = otra).status, "usado")
        assertEquals(HttpStatusCode.NotFound, ver(cuarto.codigo, uid = otra).status, "usado")
        assertEquals(HttpStatusCode.NotFound, ver("ZZZZZZ").status, "inválido")
    }

    @Test
    fun `un grupo de tres no se invita y uno conectado tampoco`() = conApp {
        crearLaCasa(id = "esp_tres", miembros = """[{"id":"dst_caro"},{"id":"dst_mama"}]""")
        val tres = post("/api/espacios/esp_tres/invitacion", "{}", juan)
        assertEquals(HttpStatusCode.BadRequest, tres.status)
        assertEquals(POR_AHORA_GRUPOS_DE_DOS, tres.bodyAsText())

        conectar()
        assertEquals(HttpStatusCode.Conflict, post("/api/espacios/esp_casa/invitacion", "{}", juan).status)
        // Un grupo conectado no cambia de personas.
        assertEquals(
            HttpStatusCode.Conflict,
            post("/api/espacios", """{"id":"esp_casa","nombre":"Casa","miembros":[{"id":"dst_caro"},{"id":"dst_mama"}]}""", juan).status,
        )
        assertEquals(HttpStatusCode.OK, post("/api/espacios", """{"id":"esp_casa","nombre":"La casa","miembros":[{"id":"dst_caro"}]}""", juan).status)
    }

    @Test
    fun `aceptar un grupo ya conectado o si ya estan conectados en otro grupo da 409`() = conApp {
        conectar()
        // Otro grupo de Juan con Caro: un hogar por pareja.
        crearLaCasa(id = "esp_viaje")
        val viaje = invitar("esp_viaje")
        assertEquals(HttpStatusCode.Conflict, aceptar(viaje.codigo).status)

        // El grupo de la invitación ya quedó conectado (por otro camino): 409.
        val paraOtra = invitar("esp_viaje")
        transaction {
            EspaciosConectados.insert {
                it[id] = "con_a_mano"
                it[userA] = juan; it[espacioA] = "esp_viaje"; it[miembroA] = "dst_caro"
                it[userB] = "alguien"; it[espacioB] = "esp_x"; it[miembroB] = "dst_x"
                it[desde] = 0L
            }
        }
        assertEquals(HttpStatusCode.Conflict, aceptar(paraOtra.codigo, uid = otra).status)
    }

    @Test
    fun `diez codigos fallidos en una hora y el siguiente es 429, aunque sea bueno`() = conApp {
        crearLaCasa()
        val bueno = invitar()
        repeat(10) { assertEquals(HttpStatusCode.NotFound, ver("ZZZZZ$it".take(6)).status) }
        assertEquals(HttpStatusCode.TooManyRequests, ver(bueno.codigo).status)
        assertEquals(HttpStatusCode.TooManyRequests, aceptar(bueno.codigo).status)
        // Es por persona: otra sigue pudiendo.
        assertEquals(HttpStatusCode.OK, ver(bueno.codigo, uid = otra).status)
    }

    @Test
    fun `dos aceptaciones a la vez - una gana y la otra no encuentra nada`() = conApp {
        crearLaCasa()
        val inv = invitar()
        val estados = coroutineScope {
            listOf(caro, otra).map { uid -> async { aceptar(inv.codigo, uid).status } }.map { it.await() }
        }
        assertEquals(setOf(HttpStatusCode.OK, HttpStatusCode.NotFound), estados.toSet(), "$estados")
        assertEquals(1L, transaction { EspaciosConectados.selectAll().count() })
        assertEquals(1L, transaction { Espacios.selectAll().where { Espacios.userId neq juan }.count() }, "un solo grupo nuevo")
    }

    @Test
    fun `el codigo no queda en la base ni en el log`() {
        val raiz = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val lo = ListAppender<ILoggingEvent>().apply { start() }
        raiz.addAppender(lo)
        try {
            var codigo = ""
            conApp {
                crearLaCasa()
                codigo = invitar().codigo
                ver(codigo)
                aceptar(codigo)
            }
            val enLaBase = transaction {
                listOf(InvitacionesAEspacio, EspaciosConectados).flatMap { t -> t.selectAll().flatMap { fila -> t.columns.map { fila[it]?.toString().orEmpty() } } }
            }
            assertTrue(enLaBase.isNotEmpty())
            assertTrue(enLaBase.none { codigo in it.uppercase() }, "el código quedó en la base")
            assertTrue(lo.list.isNotEmpty(), "el log se capturó")
            assertTrue(lo.list.none { codigo in it.formattedMessage.uppercase() }, "el código quedó en el log")
        } finally {
            raiz.detachAppender(lo)
        }
    }

    // ── Lo que se ve ─────────────────────────────────────────────────────────

    @Test
    fun `Juan reparte y Caro lo ve anotado por Juan, sin el movimiento, y su mitad cuenta el dia del gasto`() = conApp {
        val deCaro = conectar()
        // Caro tiene su «mercado», en minúscula: su parte cuenta ahí.
        evento("e_suyo", 1_000, caro, categoria = "mercado")
        val momento = System.currentTimeMillis()
        evento("e_mercado_juan", 240_001, juan, momento = momento)
        assertEquals(HttpStatusCode.Created, post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado_juan","gastoId":"g_juan"}""", juan).status)

        val enCaro = detalle(deCaro.id, caro)
        val g = enCaro.gastos.single()
        val juanEnCaro = deCaro.conexion!!.miembroId
        assertEquals("g_juan", g.id)
        assertNull(g.eventoId, "nunca cruza un id de movimiento de otra cuenta")
        assertEquals(juanEnCaro, g.anotadoPor)
        assertEquals(juanEnCaro, g.pagadoPor)
        assertEquals(mapOf(juanEnCaro to 120_001L, MIEMBRO_YO to 120_000L), g.partes)
        assertFalse(get("/api/espacios/${deCaro.id}", caro).bodyAsText().contains("e_mercado_juan"))

        val tuParte: List<FinancialEvent> = json.decodeFromString(get("/api/espacios/tu-parte", caro).bodyAsText())
        assertEquals(120_000L, tuParte.single().amount)
        assertEquals(momento, tuParte.single().timestamp, "cuenta el día del gasto")
        assertEquals("mercado", tuParte.single().category, "la de Caro con el mismo nombre normalizado")

        // Y el saldo es espejo: Caro le debe a Juan lo que Juan dice que Caro le debe.
        val enJuan = detalle("esp_casa", juan)
        assertEquals(120_000L, enJuan.saldo.netoDe(MIEMBRO_YO))
        assertEquals(-120_000L, enCaro.saldo.netoDe(MIEMBRO_YO))
    }

    @Test
    fun `lo que anota Caro suma en Juan, con montos impares el saldo es espejo al peso`() = conApp {
        val deCaro = conectar()
        val juanEnCaro = deCaro.conexion!!.miembroId
        evento("e_caro", 99_999, caro, categoria = "Comida rara")
        post("/api/espacios/${deCaro.id}/compartir", """{"eventoId":"e_caro","gastoId":"g_caro_1"}""", caro)
        post("/api/espacios/${deCaro.id}/gastos", """{"id":"g_caro_2","pagadoPor":"$juanEnCaro","monto":33333,"categoria":"Mercado","fecha":$hoy}""", caro)
        post("/api/espacios/${deCaro.id}/liquidaciones", """{"id":"l_caro","de":"yo","a":"$juanEnCaro","monto":7777}""", caro)
        evento("e_juan", 12_345, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_juan","gastoId":"g_juan"}""", juan)

        val enJuan = detalle("esp_casa", juan)
        val enCaro = detalle(deCaro.id, caro)
        assertEquals(3, enJuan.gastos.size)
        assertEquals(1, enJuan.liquidaciones.size)
        assertEquals("dst_caro", enJuan.liquidaciones.single().anotadoPor)
        assertNull(enJuan.liquidaciones.single().eventoId)
        val x = enJuan.saldo.netoDe(MIEMBRO_YO)
        assertTrue(x != 0L)
        assertEquals(-x, enCaro.saldo.netoDe(MIEMBRO_YO), "espejo al peso")
        assertTrue(enJuan.propuestas.isEmpty(), "lo de Caro no le propone a Juan vincular nada")

        val tuParteJuan: List<FinancialEvent> = json.decodeFromString(get("/api/espacios/tu-parte", juan).bodyAsText())
        assertEquals(49_999L, tuParteJuan.single { it.id == "espacio:g_caro_1" }.amount)
        assertEquals("Otros", tuParteJuan.single { it.id == "espacio:g_caro_1" }.category, "Juan no tiene «Comida rara»")
        assertTrue(tuParteJuan.none { it.id == "espacio:g_caro_2" }, "lo que Caro anotó que pagó Juan no es «tu parte de otro»")
    }

    @Test
    fun `lo de Caro de antes de conectarse no aparece, y tras desconectar nada del otro lado`() = conApp {
        val deCaro = conectar()
        val juanEnCaro = deCaro.conexion!!.miembroId
        val ayer = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        evento("e_viejo", 50_000, caro, momento = ayer)
        evento("e_nuevo", 60_000, caro)
        assertEquals(HttpStatusCode.Created, post("/api/espacios/${deCaro.id}/compartir", """{"eventoId":"e_viejo","gastoId":"g_viejo"}""", caro).status)
        assertEquals(HttpStatusCode.Created, post("/api/espacios/${deCaro.id}/compartir", """{"eventoId":"e_nuevo","gastoId":"g_nuevo"}""", caro).status)
        evento("e_juan", 10_000, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_juan"}""", juan)
        assertEquals(listOf("g_nuevo"), detalle("esp_casa", juan).gastos.filter { it.anotadoPor != null }.map { it.id })
        assertTrue(juanEnCaro.isNotBlank())

        assertEquals(HttpStatusCode.NoContent, borrar("/api/espacios/${deCaro.id}/conexion", caro).status, "cualquiera de los dos desconecta")
        val juanDespues = detalle("esp_casa", juan)
        assertTrue(juanDespues.gastos.all { it.anotadoPor == null })
        assertNull(juanDespues.espacio.conexion)
        val caroDespues = detalle(deCaro.id, caro)
        assertEquals(setOf("g_viejo", "g_nuevo"), caroDespues.gastos.map { it.id }.toSet(), "desconectar no borra nada de nadie")
        assertNull(caroDespues.espacio.conexion)
        val tuParte: List<FinancialEvent> = json.decodeFromString(get("/api/espacios/tu-parte", juan).bodyAsText())
        assertTrue(tuParte.isEmpty())
        assertEquals(HttpStatusCode.NotFound, borrar("/api/espacios/esp_casa/conexion", juan).status, "ya no hay conexión")
    }

    // ── Lo ajeno y lo privado ────────────────────────────────────────────────

    @Test
    fun `Caro no borra un gasto ni un saldado de Juan, y la fila sigue`() = conApp {
        val deCaro = conectar()
        evento("e_juan", 40_000, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_juan","gastoId":"g_juan"}""", juan)
        post("/api/espacios/esp_casa/gastos", """{"id":"g_otro","pagadoPor":"dst_caro","monto":8000,"categoria":"Mercado"}""", juan)
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"l_juan","de":"dst_caro","a":"yo","monto":5000}""", juan)

        assertEquals(HttpStatusCode.NotFound, borrar("/api/espacios/${deCaro.id}/gastos/g_otro", caro).status)
        assertEquals(HttpStatusCode.NotFound, borrar("/api/espacios/esp_casa/gastos/g_otro", caro).status)
        assertEquals(HttpStatusCode.NotFound, borrar("/api/espacios/${deCaro.id}/compartir/e_juan", caro).status)
        assertEquals(HttpStatusCode.NotFound, borrar("/api/espacios/${deCaro.id}/liquidaciones/l_juan", caro).status)
        assertEquals(2L, transaction { GastosDelEspacio.selectAll().where { GastosDelEspacio.userId eq juan }.count() })
        assertEquals(1L, transaction { LiquidacionesDelEspacio.selectAll().count() })
        assertEquals(3, detalle(deCaro.id, caro).gastos.size + detalle(deCaro.id, caro).liquidaciones.size)
    }

    @Test
    fun `Caro no ve nada de Juan que no este repartido en ese grupo`() = conApp {
        val deCaro = conectar()
        evento("e_secreto", 777_000, juan, descripcion = "Regalo sorpresa")
        evento("e_casa", 20_000, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_casa"}""", juan)
        // Otro grupo de Juan, con su mamá, con un gasto repartido.
        post("/api/espacios", """{"id":"esp_mama","nombre":"Con mamá","miembros":[{"id":"dst_mama"}]}""", juan)
        evento("e_mama", 90_000, juan, descripcion = "Farmacia de mamá")
        post("/api/espacios/esp_mama/compartir", """{"eventoId":"e_mama"}""", juan)

        val todoLoDeCaro = listOf(
            get("/api/espacios", caro), get("/api/espacios/${deCaro.id}", caro), get("/api/espacios/tu-parte", caro),
            get("/api/espacios/propuestas", caro), get("/api/accounts", caro),
        ).joinToString("\n") { runBlocking { it.bodyAsText() } }
        listOf("e_secreto", "Regalo sorpresa", "e_mama", "Farmacia de mamá", "esp_mama", "Con mamá", "acc-$juan", "Ahorros de Juan", "e_casa")
            .forEach { assertFalse(it in todoLoDeCaro, "Caro ve «$it»") }
        assertEquals(HttpStatusCode.NotFound, get("/api/espacios/esp_casa", caro).status)
        assertEquals(HttpStatusCode.NotFound, get("/api/espacios/esp_mama", caro).status)
        assertTrue(runBlocking { loadNonVoidedEvents(caro) }.none { it.id.startsWith("e_") }, "ningún movimiento de Juan")
        assertEquals(1, detalle(deCaro.id, caro).gastos.size, "solo lo repartido en ese grupo")
    }

    // ── Borrar ───────────────────────────────────────────────────────────────

    @Test
    fun `borrar la cuenta de Juan borra la conexion y Caro sigue con su grupo, sin lo de Juan`() = conApp {
        val deCaro = conectar()
        evento("e_juan", 40_000, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_juan"}""", juan)
        evento("e_caro", 10_000, caro)
        assertEquals(HttpStatusCode.Created, post("/api/espacios/${deCaro.id}/compartir", """{"eventoId":"e_caro","gastoId":"g_caro"}""", caro).status)
        assertEquals(2, detalle(deCaro.id, caro).gastos.size)

        transaction { borrarTodoLoDe(juan) }

        assertEquals(0L, transaction { EspaciosConectados.selectAll().count() })
        val despues = detalle(deCaro.id, caro)
        assertNull(despues.espacio.conexion)
        assertEquals(listOf("g_caro"), despues.gastos.map { it.id })
    }

    @Test
    fun `borrar el grupo lo desconecta`() = conApp {
        val deCaro = conectar()
        evento("e_juan", 40_000, juan)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_juan"}""", juan)
        assertEquals(1, detalle(deCaro.id, caro).gastos.size)

        assertEquals(HttpStatusCode.NoContent, borrar("/api/espacios/esp_casa", juan).status)

        val despues = detalle(deCaro.id, caro)
        assertNull(despues.espacio.conexion)
        assertTrue(despues.gastos.isEmpty())
        assertNotNull(transaction { EspaciosConectados.selectAll().single()[EspaciosConectados.desconectadoEn] })
    }
}
