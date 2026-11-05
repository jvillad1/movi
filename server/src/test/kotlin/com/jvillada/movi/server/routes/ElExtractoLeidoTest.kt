package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.shared.model.BANCOS_DEL_USO
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.GUIAS_DEL_EXTRACTO
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.bancoDelExtractoParaElUso
import com.jvillada.movi.shared.model.problemaDelEvento
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 130 · `EXTRACTO_LEIDO`: por banco, qué archivo funciona, sin datos de nadie
 *
 * Cada lectura de un extracto deja un evento con el banco, el producto, el lector, el resultado, las filas
 * y los milisegundos. Nunca el texto, los montos ni el nombre del archivo.
 */
class ElExtractoLeidoTest {

    private val duena = "user-extracto-leido"

    private class LectorFalso(var lectura: ClaudeStatementParser.Lectura) : LectorDePapeles {
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = error("no se usa")
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>) = lectura
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>) = lectura
    }

    private val lector = LectorFalso(ClaudeStatementParser.Lectura.Incompleta)

    @BeforeTest
    fun setUp() {
        LectorDePapeles.actual = lector
        Observabilidad.olvidarLoRegistrado()
        Database.connect(
            url = "jdbc:h2:mem:extracto_leido_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(UsoDeLaApp, Users)
            SchemaUtils.create(Users, UsoDeLaApp)
            Users.insert {
                it[id] = duena
                it[email] = "$duena@extracto.test"
                it[name] = duena
                it[passwordHash] = "hash"
            }
        }
    }

    @AfterTest
    fun limpiar() {
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    private fun eventos(): List<Map<String, String>> = transaction {
        UsoDeLaApp.selectAll().where { UsoDeLaApp.nombre eq EventoDeUso.EXTRACTO_LEIDO.name }.map { fila ->
            Json.parseToJsonElement(fila[UsoDeLaApp.propiedades]).let { it as JsonObject }
                .mapValues { it.value.jsonPrimitive.content }
        }
    }

    private val fila = ParsedTransaction("f1", "2026-10-28", "TIENDA LIBERTARIO", 8_100, "COP", TransactionType.EXPENSE, "Comida", "COMPRA", "x")

    // ── Los valores permitidos ───────────────────────────────────────────────────────────────────

    @Test
    fun `los datos solo llevan claves cerradas, nunca el nombre del archivo ni un monto`() {
        val datos = datosDelExtractoLeido(
            nombreDelBanco = "Bancolombia",
            fileName = "Extracto Bancolombia Ahorros 8133 \$1.234.567 de Caro.pdf",
            producto = "CUENTA",
            lector = LECTOR_IA,
            resultado = "OK",
            filas = 71,
            ms = 41_250,
        )
        assertEquals(
            setOf(ClaveDeUso.BANCO, ClaveDeUso.PRODUCTO, ClaveDeUso.LECTOR, ClaveDeUso.RESULTADO, ClaveDeUso.CUANTOS, ClaveDeUso.MS),
            datos.keys,
        )
        assertEquals("BANCOLOMBIA", datos[ClaveDeUso.BANCO])
        assertNull(problemaDelEvento(EventoDeUso.EXTRACTO_LEIDO.name, datos.mapKeys { it.key.clave }))
        val todo = datos.values.joinToString(" ")
        listOf("8133", "1.234", "Caro", "Ahorros", ".pdf", "Extracto").forEach { assertTrue(it !in todo, "«$it» se coló: $todo") }
        // El banco solo por el nombre del archivo, si la lectura no lo detectó; si no es de la lista, OTRO.
        assertEquals("NU", datosDelExtractoLeido(null, "CuentaNu_2026-09.pdf", null, null, "CON_CLAVE", null, 3)[ClaveDeUso.BANCO])
        assertEquals("CUENTA", datosDelExtractoLeido(null, "CuentaNu_2026-09.pdf", null, null, "CON_CLAVE", null, 3)[ClaveDeUso.PRODUCTO])
        assertEquals("OTRO", datosDelExtractoLeido("Banco Inventado", "x.pdf", null, null, "OK", 0, 1)[ClaveDeUso.BANCO])
    }

    @Test
    fun `las claves rechazan texto, montos y nombres`() {
        listOf("Bancolombia", "bancolombia", "Banco de Bogotá", "\$1.234", "extracto.pdf").forEach {
            assertTrue(!ClaveDeUso.BANCO.admite(it), "BANCO admitió «$it»")
        }
        assertTrue(!ClaveDeUso.PRODUCTO.admite("Nu Tarjeta"))
        assertTrue(!ClaveDeUso.LECTOR.admite("Sonnet"))
        listOf("OK", "INCOMPLETA", "CON_CLAVE", "SIN_FILAS", "ERROR").forEach { assertTrue(ClaveDeUso.RESULTADO.admite(it), it) }
        listOf("SIN_IA", "IA").forEach { assertTrue(ClaveDeUso.LECTOR.admite(it)) }
        listOf("CUENTA", "TARJETA", "CREDITO", "FONDO", "OTRO").forEach { assertTrue(ClaveDeUso.PRODUCTO.admite(it)) }
    }

    @Test
    fun `todo banco del catalogo de guias se puede contar`() {
        GUIAS_DEL_EXTRACTO.forEach { g ->
            val id = bancoDelExtractoParaElUso(g.banco)
            assertTrue(id != "OTRO" && id in BANCOS_DEL_USO, "${g.banco} se contaría como $id")
        }
        assertEquals("BANCOLOMBIA", bancoDelExtractoParaElUso("American Express"))
        assertEquals("DAVIBANK", bancoDelExtractoParaElUso("Scotiabank Colpatria"))
        // Revisión de la vuelta 130: la Fiducuenta es el fondo de Bancolombia (usa su guía).
        assertEquals("BANCOLOMBIA", bancoDelExtractoParaElUso("Fiducuenta"))
        assertEquals("FAMIRIOS", bancoDelExtractoParaElUso("Famirios"))
        assertEquals("OTRO", bancoDelExtractoParaElUso("Extracto"))
    }

    @Test
    fun `cada falla del archivo tiene su resultado, y la que no es del archivo no se cuenta`() {
        val f = { s: HttpStatusCode, m: String -> resultadoDeLaFallaDelExtracto(FallaAlProcesarExtracto(s, m)) }
        assertEquals("INCOMPLETA", f(HttpStatusCode.UnprocessableEntity, EXTRACTO_INCOMPLETO))
        assertEquals("INCOMPLETA", f(HttpStatusCode.UnprocessableEntity, IMAGEN_INCOMPLETA))
        assertEquals("SIN_FILAS", f(HttpStatusCode.UnprocessableEntity, EXTRACTO_SIN_MOVIMIENTOS))
        assertEquals("ERROR", f(HttpStatusCode.UnprocessableEntity, LECTURA_FALLO))
        assertNull(f(HttpStatusCode.ServiceUnavailable, "IA_SIN_CREDITO"))
        assertNull(f(HttpStatusCode.PayloadTooLarge, "El archivo pesa más de 20 MB"))
        assertNull(f(HttpStatusCode.BadRequest, "No file received"))
    }

    // ── La lectura de verdad lo escribe ──────────────────────────────────────────────────────────

    @Test
    fun `una lectura con IA que se corta deja INCOMPLETA, con su banco y sin el texto`() = runBlocking {
        lector.lectura = ClaudeStatementParser.Lectura.Incompleta
        val csv = "Fecha,Descripcion,Valor\n2026-10-28,PANADERIA SECRETA,-41234\n".toByteArray()
        assertFailsWith<FallaAlProcesarExtracto> { leerElExtracto(duena, "Nu_octubre.csv", "text/csv", csv) }
        val e = eventos().single()
        assertEquals("NU", e["banco"])
        assertEquals("IA", e["lector"])
        assertEquals("INCOMPLETA", e["resultado"])
        assertTrue(e["ms"]!!.toLong() >= 0)
        assertNull(e["cuantos"])
        val todo = e.values.joinToString(" ")
        assertTrue("PANADERIA" !in todo && "41234" !in todo && "octubre" !in todo, todo)
    }

    @Test
    fun `una lectura con filas deja OK con cuantas`() = runBlocking {
        lector.lectura = ClaudeStatementParser.Lectura.Ok(listOf(fila, fila.copy(id = "f2")))
        leerElExtracto(duena, "Davivienda_10.csv", "text/csv", "Fecha,Valor\n2026-10-28,-8100\n".toByteArray())
        val e = eventos().single()
        assertEquals("DAVIVIENDA", e["banco"])
        assertEquals("OK", e["resultado"])
        assertEquals("2", e["cuantos"])
        assertEquals("IA", e["lector"])
    }

    @Test
    fun `con el uso apagado no se escribe nada`() = runBlocking {
        transaction {
            Users.update({ Users.id eq duena }) { it[mandarUso] = false }
        }
        lector.lectura = ClaudeStatementParser.Lectura.Ok(listOf(fila))
        leerElExtracto(duena, "Nu.csv", "text/csv", "a,b\n".toByteArray())
        assertTrue(eventos().isEmpty())
    }

    // ── Revisión de la vuelta 130 ────────────────────────────────────────────────────────────────

    @Test
    fun `una relectura que no es del duenio no se cuenta`() = runBlocking {
        lector.lectura = ClaudeStatementParser.Lectura.Ok(listOf(fila))
        leerElExtracto(duena, "Nu.csv", "text/csv", "a,b\n".toByteArray(), registrar = false)
        assertTrue(eventos().isEmpty())
    }

    @Test
    fun `si PDFBox no lo abre nadie lo leyo y el evento va sin lector`() = runBlocking {
        assertFailsWith<FallaAlProcesarExtracto> {
            leerElExtracto(duena, "Davivienda_10.pdf", "application/pdf", "esto no es un PDF".toByteArray())
        }
        val e = eventos().single()
        assertEquals("ERROR", e["resultado"])
        assertNull(e["lector"], "$e")
    }

    @Test
    fun `un PDF con clave subido por el importador se cuenta como CON_CLAVE`() = runBlocking {
        val out = java.io.ByteArrayOutputStream()
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            doc.addPage(org.apache.pdfbox.pdmodel.PDPage())
            val politica = org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                "propietario", "1037585071", org.apache.pdfbox.pdmodel.encryption.AccessPermission(),
            )
            politica.encryptionKeyLength = 128
            doc.protect(politica)
            doc.save(out)
        }
        assertFailsWith<FallaAlProcesarExtracto> {
            leerElExtracto(duena, "Bancolombia_Ahorros_09.pdf", "application/pdf", out.toByteArray())
        }
        val e = eventos().single()
        assertEquals("CON_CLAVE", e["resultado"])
        assertEquals("BANCOLOMBIA", e["banco"])
        assertNull(e["lector"])
    }
}
