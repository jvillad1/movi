package com.jvillada.movi.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 78: el JSON compacto y comprimido
 *
 * Con el `configureSerialization` + `configureCompression` de `Application.module`:
 *
 * - una respuesta JSON grande sale con gzip si el cliente lo pide, y abierta dice **lo mismo** que la
 *   respuesta sin comprimir;
 * - el JSON va sin sangría;
 * - lo chico (< 1 KB) y lo binario (el `.wasm`) van tal cual;
 * - un pedido llega a la ruta con el cuerpo **crudo**, aunque diga `Content-Encoding: gzip` —el webhook
 *   de Resend firma esos bytes—;
 * - `/compartido` (HTML) sigue contestando, comprimido.
 */
class CompresionTest {

    @Serializable
    private data class Fila(val id: String, val descripcion: String, val monto: Long, val categoria: String)

    private val muchas = (1..400).map { Fila("ev-$it", "Mercado del sábado $it", 27_500L + it, "Mercado") }

    private fun ApplicationTestBuilder.montar() {
        application {
            configureSerialization()
            configureCompression()
            routing {
                get("/grande") { call.respond(muchas) }
                get("/chico") { call.respond(Fila("ev-1", "Pan", 2_000, "Mercado")) }
                get("/bundle.wasm") { call.respondBytes(ByteArray(64 * 1024) { (it % 251).toByte() }, ContentType("application", "wasm")) }
                get("/pagina") { call.respondText("<html>" + "Movi ".repeat(1_000) + "</html>", ContentType.Text.Html) }
                // Devuelve los bytes del cuerpo tal cual llegaron, en hexadecimal.
                post("/eco") { call.respondText(call.receiveChannel().toByteArray().toHex()) }
            }
        }
    }

    @Test
    fun `una respuesta JSON grande sale con gzip y abierta dice lo mismo`() = testApplication {
        montar()
        val plano = client.get("/grande")
        val comprimido = client.get("/grande") { header(HttpHeaders.AcceptEncoding, "gzip") }

        assertNull(plano.headers[HttpHeaders.ContentEncoding], "sin Accept-Encoding no se comprime")
        assertEquals("gzip", comprimido.headers[HttpHeaders.ContentEncoding])
        val bytes = comprimido.body<ByteArray>()
        val abierto = gunzip(bytes)
        val textoPlano = plano.bodyAsText()
        assertEquals(textoPlano, abierto)
        assertTrue(bytes.size * 4 < textoPlano.toByteArray().size, "comprime al menos 4 a 1 (${bytes.size} de ${textoPlano.length})")
    }

    @Test
    fun `el JSON sale sin sangria`() = testApplication {
        montar()
        val texto = client.get("/grande").bodyAsText()
        assertFalse('\n' in texto, "sin saltos de línea")
        assertTrue(texto.startsWith("""[{"id":"ev-1","descripcion":"""), texto.take(80))
    }

    @Test
    fun `deflate tambien, si es lo unico que acepta el cliente`() = testApplication {
        montar()
        val res = client.get("/grande") { header(HttpHeaders.AcceptEncoding, "deflate") }
        assertEquals("deflate", res.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `lo chico y lo binario van tal cual`() = testApplication {
        montar()
        val chico = client.get("/chico") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertNull(chico.headers[HttpHeaders.ContentEncoding], "menos de 1 KB")
        assertEquals("""{"id":"ev-1","descripcion":"Pan","monto":2000,"categoria":"Mercado"}""", chico.bodyAsText())

        val wasm = client.get("/bundle.wasm") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertNull(wasm.headers[HttpHeaders.ContentEncoding], "el .wasm no se toca")
        assertEquals(64 * 1024, wasm.body<ByteArray>().size)
    }

    @Test
    fun `el HTML se comprime`() = testApplication {
        montar()
        val res = client.get("/pagina") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", res.headers[HttpHeaders.ContentEncoding])
        assertTrue(gunzip(res.body<ByteArray>()).startsWith("<html>Movi"))
    }

    /**
     * El plugin de Ktor también sabe DEScomprimir pedidos; acá está apagado. Lo que llega a la ruta son
     * los mismos bytes que mandó el cliente —la firma Svix de Resend se calcula sobre ellos—.
     */
    @Test
    fun `un pedido llega crudo aunque diga que viene comprimido`() = testApplication {
        montar()
        val cuerpo = gzip("""{"type":"email.received","data":{"email_id":"e1"}}""")
        val res = client.post("/eco") {
            header(HttpHeaders.ContentEncoding, "gzip")
            setBody(cuerpo)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(cuerpo.toHex(), res.bodyAsText())
    }

    @Test
    fun `la pagina de un enlace compartido sigue contestando, comprimida`() = testApplication {
        application {
            configureSerialization()
            configureCompression()
            val secreto = "test-secret-for-compresion-tests-minimum-32-chars"
            val verifier = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
            authentication {
                jwt("jwt") {
                    this.verifier(verifier)
                    validate { JWTPrincipal(it.payload) }
                }
            }
            configureRouting()
        }
        val res = client.get("/compartido") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals(HttpStatusCode.OK, res.status)
        val html = if (res.headers[HttpHeaders.ContentEncoding] == "gzip") gunzip(res.body<ByteArray>()) else res.bodyAsText()
        assertTrue(html.contains("<html", ignoreCase = true), html.take(200))
        // Y una ruta de la API que no existe sigue siendo el 404 en JSON (ahora compacto).
        val noExiste = client.get("/api/no-existe")
        assertEquals(HttpStatusCode.NotFound, noExiste.status)
        assertTrue(noExiste.bodyAsText().startsWith("""{"error":"not_found""""))
    }

    private fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).readBytes().decodeToString()

    private fun gzip(texto: String): ByteArray {
        val salida = ByteArrayOutputStream()
        GZIPOutputStream(salida).use { it.write(texto.toByteArray()) }
        return salida.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
