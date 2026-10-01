package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.exportar.FilasExportadas
import com.jvillada.movi.server.exportar.TABLAS_EXPORTADAS
import com.jvillada.movi.server.exportar.csvDe
import com.jvillada.movi.server.plugins.configureAuth
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«Descarga tus datos»** (`GET /api/export`): un ZIP con un CSV por tabla y `movi.json`.
 *
 * Lo que más importa es el aislamiento: el ZIP de A no puede traer **ni una fila** de B. Se mide
 * buscando cada id y cada texto de B en TODO el contenido del ZIP, no tabla por tabla — así una
 * tabla nueva mal filtrada también se pone roja.
 */
class ExportRoutesTest {

    private val a = "usr-a"
    private val b = "usr-b"

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-export-routes-min-32-chars")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:export_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = (TABLAS_EXPORTADAS.map { it.tabla } + listOf(PasswordResetTokens, PushSubscriptions)).distinct()
        transaction {
            SchemaUtils.drop(*tablas.toTypedArray())
            SchemaUtils.create(*tablas.toTypedArray())
            for (uid in listOf(a, b)) {
                Users.insert {
                    it[id] = uid
                    it[email] = "$uid@movi.test"
                    it[name] = "Nombre de $uid"
                    it[passwordHash] = "hash-secreto-de-$uid"
                }
                Accounts.insert {
                    it[id] = "cuenta-de-$uid"
                    it[userId] = uid
                    it[name] = "Ahorros de $uid"
                    it[type] = "SAVINGS"
                    it[balance] = 1_500_000
                }
                Events.insert {
                    it[id] = "mov-de-$uid"
                    it[userId] = uid
                    it[accountId] = "cuenta-de-$uid"
                    it[type] = "EXPENSE"
                    it[amount] = 45_000
                    it[category] = "Comida"
                    it[description] = "Almuerzo, con \"comillas\" de $uid"
                    it[timestamp] = 1_759_000_000_000
                }
                SmsMessages.insert {
                    it[id] = "sms-de-$uid"
                    it[userId] = uid
                    it[time] = "2026-09-29T10:00:00"
                    it[bank] = "Bancolombia"
                    it[text] = "=HYPERLINK(\"http://malo\") compra de $uid"
                    it[state] = "pending"
                    it[det] = ""
                }
                Documents.insert {
                    it[id] = "doc-de-$uid"
                    it[userId] = uid
                    it[name] = "extracto-de-$uid.pdf"
                    it[kind] = "EXTRACTO"
                    it[mimeType] = "application/pdf"
                    it[sizeBytes] = 9
                    it[uploadedAt] = 0
                    it[content] = "BINARIO-$uid".encodeToByteArray()
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
    }

    private fun ApplicationTestBuilder.armar() {
        application {
            configureSerialization()
            configureAuth()
            routing {
                exportDescargaRoutes()
                authenticate("jwt") {
                    exportRoutes()
                    userRoutes()
                }
            }
        }
    }

    private fun token(uid: String) = JwtConfig.makeToken(uid, "$uid@movi.test")

    /** Nombre de entrada → contenido como texto. */
    private fun abrirZip(bytes: ByteArray): Map<String, String> {
        val entradas = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entradas[e.name] = zip.readBytes().decodeToString()
            }
        }
        return entradas
    }

    private suspend fun ApplicationTestBuilder.exportar(uid: String): Map<String, String> {
        val res = client.get("/api/export") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertTrue(res.headers[HttpHeaders.ContentDisposition].orEmpty().contains("movi-datos-"))
        assertEquals("no-store", res.headers[HttpHeaders.CacheControl])
        return abrirZip(res.readRawBytes())
    }

    @Test
    fun `el zip trae un csv por tabla y movi json`() = testApplication {
        armar()
        val zip = exportar(a)
        val esperadas = TABLAS_EXPORTADAS.map { "${it.nombre}.csv" }.toSet() + "movi.json"
        assertEquals(esperadas, zip.keys)
        assertTrue(zip.getValue("movimientos.csv").contains("mov-de-usr-a"))
        assertTrue(zip.getValue("cuentas.csv").contains("Ahorros de usr-a"))
        assertTrue(zip.getValue("mensajes_del_banco.csv").contains("sms-de-usr-a"))
    }

    @Test
    fun `el zip de A no trae ninguna fila de B`() = testApplication {
        armar()
        val todo = exportar(a).values.joinToString("\n")
        assertTrue(todo.contains("usr-a"))
        assertFalse(todo.contains("usr-b"), "el ZIP de A trae datos de B")
    }

    @Test
    fun `sin sesion no hay exportacion`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/export").status)
    }

    @Test
    fun `no salen ni el binario de los documentos ni el hash de la contrasena`() = testApplication {
        armar()
        val zip = exportar(a)
        val todo = zip.values.joinToString("\n")
        assertFalse(todo.contains("BINARIO-usr-a"), "el contenido del documento no va en la exportación")
        assertFalse(todo.contains("hash-secreto"), "el hash de la contraseña no va en la exportación")
        assertFalse(zip.getValue("perfil.csv").contains("token_version"))
        assertTrue(zip.getValue("documentos.csv").contains("extracto-de-usr-a.pdf"), "los metadatos sí van")
    }

    @Test
    fun `movi json trae lo mismo con los tipos de verdad`() = testApplication {
        armar()
        val json = Json.parseToJsonElement(exportar(a).getValue("movi.json")).jsonObject
        val movimientos = json.getValue("tablas").jsonObject.getValue("movimientos").jsonArray
        assertEquals(1, movimientos.size)
        assertEquals(45_000L, movimientos.single().jsonObject.getValue("amount").jsonPrimitive.long)
        assertEquals("usr-a", json.getValue("usuario").jsonPrimitive.content)
    }

    @Test
    fun `el csv escapa comillas y desarma formulas`() {
        val csv = csvDe(
            FilasExportadas(
                "x",
                listOf("texto", "monto"),
                listOf(listOf("Almuerzo, con \"comillas\"", -50_000L), listOf("=1+1", null)),
            ),
        )
        val lineas = csv.removePrefix("﻿").split("\r\n")
        assertEquals("texto,monto", lineas[0])
        assertEquals("\"Almuerzo, con \"\"comillas\"\"\",-50000", lineas[1])
        assertEquals("'=1+1,", lineas[2])
    }

    // ── El enlace que usa la app ──────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.pedirEnlace(uid: String): String {
        val res = client.post("/api/export/enlace") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject.getValue("url").jsonPrimitive.content
    }

    @Test
    fun `el enlace baja el zip sin encabezado de sesion`() = testApplication {
        armar()
        val url = pedirEnlace(a)
        val res = client.get(url)
        assertEquals(HttpStatusCode.OK, res.status)
        val todo = abrirZip(res.readRawBytes()).values.joinToString("\n")
        assertTrue(todo.contains("mov-de-usr-a"))
        assertFalse(todo.contains("usr-b"))
    }

    @Test
    fun `un token de sesion no sirve como enlace de descarga`() = testApplication {
        armar()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/export/descarga?t=${token(a)}").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/export/descarga?t=inventado").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/export/descarga").status)
    }

    @Test
    fun `cerrar sesion en todos los aparatos mata el enlace pendiente`() = testApplication {
        armar()
        val url = pedirEnlace(a)
        client.post("/api/users/me/cerrar-sesiones") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.Unauthorized, client.get(url).status)
    }
}
