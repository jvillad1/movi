package com.jvillada.movi.server.cuenta

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.routes.borrarLaCuentaRoutes
import com.jvillada.movi.server.routes.exportDescargaRoutes
import com.jvillada.movi.server.routes.exportRoutes
import com.jvillada.movi.server.routes.userRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.BasicBinaryColumnType
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.util.jar.JarFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Borrar la cuenta (`DELETE /api/me`)
 *
 * La prueba que no se puede saltar es la primera: **recorre todas las tablas de Exposed que el server
 * compila** —no una lista escrita acá— y falla si alguna con columna `user_id` no está en
 * [TABLAS_DEL_USUARIO]. Así, el día que alguien agregue una tabla con datos de una persona y se olvide
 * de cubrirla en el borrado, se entera en el CI y no cuando alguien que se fue descubra que algo suyo
 * quedó.
 *
 * Las demás pruebas usan ese mismo recorrido para llenar **cada** tabla de la persona con una fila de
 * A y una de B, y miran todas después del borrado: A en cero en todas, B intacto en todas.
 */
class BorrarLaCuentaTest {

    private val a = "ua"
    private val b = "ub"
    private val claveDeA = "clave-de-a-segura-123"
    private val claveDeB = "clave-de-b-segura-456"

    /** Todas las tablas de Exposed del server, descubiertas en las clases compiladas. */
    private val todas: List<Table> by lazy { tablasCompiladas() }

    /** Las que tienen columna de usuario. `users` aparte: su usuario es el `id`. */
    private val conUsuario: List<Table> by lazy { todas.filter { t -> t.columns.any { it.name == "user_id" } } }

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-borrar-la-cuenta-min-32-chars")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:borrar_la_cuenta_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*todas.toTypedArray())
            SchemaUtils.create(*todas.toTypedArray())
            for ((uid, semilla, clave) in listOf(Triple(a, 1, claveDeA), Triple(b, 2, claveDeB))) {
                insertarFilaDePrueba(Users, uid, semilla)
                Users.update({ Users.id eq uid }) {
                    it[passwordHash] = BCrypt.withDefaults().hashToString(4, clave.toCharArray())
                    it[tokenVersion] = 0
                }
                conUsuario.forEach { insertarFilaDePrueba(it, uid, semilla) }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
        RateLimiter.reset()
    }

    // ── La cobertura ──────────────────────────────────────────────────────────────────────

    /**
     * **Toda tabla con columna de usuario está en el borrado.** Si esta prueba falla con el nombre de
     * una tabla, agrégala a [TABLAS_DEL_USUARIO] (y piensa si también va en la descarga de los datos).
     */
    @Test
    fun `toda tabla con columna de usuario esta cubierta por el borrado`() {
        // Que el recorrido de verdad encontró las tablas: sin esto, una lista vacía haría pasar todo.
        val nombres = todas.map { it.tableName }.toSet()
        assertTrue("users" in nombres && "screen_definitions" in nombres && "financial_events" in nombres, "encontré $nombres")
        assertTrue(todas.size >= 30, "el recorrido encontró solo ${todas.size} tablas")

        val cubiertas = TABLAS_DEL_USUARIO.map { it.tabla.tableName }.toSet()
        val sinCubrir = conUsuario.map { it.tableName }.toSet() - cubiertas
        assertTrue(sinCubrir.isEmpty(), "Estas tablas guardan datos de una persona y borrar la cuenta no las toca: $sinCubrir")
        // Y al revés: nada en la lista que no sea una tabla con usuario (un error de copiar y pegar).
        TABLAS_DEL_USUARIO.forEach { t ->
            assertEquals("user_id", t.usuario.name, "${t.tabla.tableName}: la columna del usuario")
            assertEquals(t.tabla, t.usuario.table, "${t.tabla.tableName}: la columna es de otra tabla")
        }
        assertEquals(TABLAS_DEL_USUARIO.size, cubiertas.size, "una tabla repetida en la lista")
    }

    // ── El borrado ────────────────────────────────────────────────────────────────────────

    @Test
    fun `con la contrasena y BORRAR se va todo lo de A, de todas las tablas, y nada de B`() = testApplication {
        armar()
        assertEquals(1, filasDe(Users, a), "el andamio")
        conUsuario.forEach { assertEquals(1, filasDe(it, a), "el andamio llenó ${it.tableName}") }

        val res = borrar(token(a), claveDeA)
        assertEquals(HttpStatusCode.NoContent, res.status, res.bodyAsText())

        assertEquals(0, filasDe(Users, a), "la fila de users")
        conUsuario.forEach { assertEquals(0, filasDe(it, a), "quedó algo de A en ${it.tableName}") }
        // Aislamiento: B, entero.
        assertEquals(1, filasDe(Users, b))
        conUsuario.forEach { assertEquals(1, filasDe(it, b), "se borró algo de B en ${it.tableName}") }
        // Y lo que no es de nadie sigue ahí (la tabla existe y no se tocó).
        assertEquals(0, transaction { Screens.selectAll().count() }.toInt())
    }

    @Test
    fun `con la contrasena mala contesta 403 y no borra nada`() = testApplication {
        armar()
        val res = borrar(token(a), "no-es-la-clave")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals(1, filasDe(Users, a))
        conUsuario.forEach { assertEquals(1, filasDe(it, a), "se borró ${it.tableName} con la contraseña mala") }
    }

    /** La contraseña de OTRA cuenta tampoco sirve: se verifica contra la de quien pide. */
    @Test
    fun `con la contrasena de otra cuenta contesta 403`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.Forbidden, borrar(token(a), claveDeB).status)
        conUsuario.forEach { assertEquals(1, filasDe(it, a)) }
    }

    @Test
    fun `sin escribir BORRAR contesta 400 y no borra nada`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.BadRequest, borrar(token(a), claveDeA, confirmacion = "borrar cuenta").status)
        assertEquals(HttpStatusCode.BadRequest, borrar(token(a), claveDeA, confirmacion = "").status)
        conUsuario.forEach { assertEquals(1, filasDe(it, a)) }
    }

    @Test
    fun `sin cuerpo contesta 400`() = testApplication {
        armar()
        val res = client.delete("/api/me") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(1, filasDe(Users, a))
    }

    /** Ningún token viejo sirve después: ni el de este aparato, ni otro, ni el de la descarga. */
    @Test
    fun `despues de borrar ningun token viejo entra, y el de B si`() = testApplication {
        armar()
        val este = token(a)
        val otroAparato = JwtConfig.makeToken(a, "$a@movi.test", 0)
        val enlace = client.post("/api/export/enlace") { header(HttpHeaders.Authorization, "Bearer $este") }.bodyAsText()
        val descarga = Regex("""/api/export/descarga\?t=[^"]+""").find(enlace)!!.value

        assertEquals(HttpStatusCode.NoContent, borrar(este, claveDeA).status)

        assertEquals(HttpStatusCode.Unauthorized, perfil(este).status, "el token de este aparato")
        assertEquals(HttpStatusCode.Unauthorized, perfil(otroAparato).status, "el del otro aparato")
        assertEquals(HttpStatusCode.Unauthorized, borrar(este, claveDeA).status, "no se borra dos veces")
        assertEquals(HttpStatusCode.Unauthorized, client.get(descarga).status, "el enlace de descarga pedido antes")
        assertEquals(HttpStatusCode.OK, perfil(token(b)).status, "B sigue adentro")
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun ApplicationTestBuilder.armar() {
        application {
            configureSerialization()
            configureAuth()
            routing {
                exportDescargaRoutes()
                authenticate("jwt") {
                    userRoutes()
                    exportRoutes()
                    borrarLaCuentaRoutes()
                }
            }
        }
    }

    private fun token(uid: String) = JwtConfig.makeToken(uid, "$uid@movi.test")

    private suspend fun ApplicationTestBuilder.borrar(token: String, contrasena: String, confirmacion: String = "BORRAR") =
        client.delete("/api/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"contrasena":"$contrasena","confirmacion":"$confirmacion"}""")
        }

    private suspend fun ApplicationTestBuilder.perfil(token: String) =
        client.get("/api/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    private fun filasDe(tabla: Table, uid: String): Int = transaction {
        val columna = if (tabla == Users) Users.id else tabla.columns.single { it.name == "user_id" }
        @Suppress("UNCHECKED_CAST")
        tabla.selectAll().where { (columna as Column<String>) eq uid }.count().toInt()
    }

    /**
     * Una fila con un valor para cada columna, según su tipo: el usuario en `user_id` (o en `id` de
     * `users`), y en el resto algo que lleva el usuario adelante para que las claves y los índices
     * únicos no choquen entre A y B. Un tipo nuevo sin valor de prueba falla con su nombre: hay que
     * agregarlo acá, no saltarse la tabla.
     */
    private fun insertarFilaDePrueba(tabla: Table, uid: String, semilla: Int) {
        tabla.insert { fila ->
            for (columna in tabla.columns) {
                @Suppress("UNCHECKED_CAST")
                val c = columna as Column<Any?>
                val esUsuario = columna.name == "user_id" || (tabla == Users && columna.name == "id")
                fila[c] = if (esUsuario) uid else valorDePrueba(columna, uid, semilla)
            }
        }
    }

    private fun valorDePrueba(columna: Column<*>, uid: String, semilla: Int): Any? = when (val tipo = columna.columnType) {
        is VarCharColumnType -> "$uid-${columna.name}".take(tipo.colLength)
        is TextColumnType -> "$uid-${columna.name}"
        is IntegerColumnType -> semilla
        is LongColumnType -> semilla.toLong()
        is BooleanColumnType -> false
        is DoubleColumnType -> semilla.toDouble()
        is BasicBinaryColumnType -> byteArrayOf(semilla.toByte())
        else -> if (tipo.nullable) null
        else error("Sin valor de prueba para ${tipo::class.simpleName} en ${columna.table.tableName}.${columna.name}")
    }

    /**
     * **Las tablas de Exposed que el server compila**, sin listarlas a mano: se recorren las clases del
     * paquete `com.jvillada.movi.server` (en el directorio de clases o en el JAR, según cómo corra la
     * prueba) y se toma cada `object` que sea una [Table]. Una tabla nueva entra sola, en cualquier
     * archivo y en cualquier subpaquete.
     */
    private fun tablasCompiladas(): List<Table> {
        val origen = File(Users::class.java.protectionDomain.codeSource.location.toURI())
        val prefijo = "com/jvillada/movi/server/"
        val nombres: List<String> = if (origen.isDirectory) {
            origen.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { it.relativeTo(origen).invariantSeparatorsPath }
                .filter { it.startsWith(prefijo) }
                .toList()
        } else {
            JarFile(origen).use { jar ->
                jar.entries().asSequence().map { it.name }
                    .filter { it.startsWith(prefijo) && it.endsWith(".class") }
                    .toList()
            }
        }
        val cargador = Users::class.java.classLoader
        return nombres.mapNotNull { ruta ->
            val nombre = ruta.removeSuffix(".class").replace('/', '.')
            runCatching {
                val clase = Class.forName(nombre, false, cargador)
                if (!Table::class.java.isAssignableFrom(clase)) return@runCatching null
                clase.getDeclaredField("INSTANCE").get(null) as? Table
            }.getOrNull()
        }.distinctBy { it.tableName }
    }
}
