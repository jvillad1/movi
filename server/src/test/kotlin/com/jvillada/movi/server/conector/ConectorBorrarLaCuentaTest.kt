package com.jvillada.movi.server.conector

import com.jvillada.movi.server.cuenta.TABLAS_DEL_USUARIO
import com.jvillada.movi.server.cuenta.TABLAS_DE_DOS_USUARIOS
import com.jvillada.movi.server.db.CodigosDelConector
import com.jvillada.movi.server.db.ConcesionesDelConector
import com.jvillada.movi.server.db.LlamadasDelConector
import com.jvillada.movi.server.db.TokensDelConector
import com.jvillada.movi.server.routes.borrarLaCuentaRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 135 · Borrar la cuenta se lleva todo lo del conector
 *
 * Por la ruta de verdad (`DELETE /api/me`): después de conectar Claude y de una llamada, borrar la cuenta de A deja
 * en cero sus concesiones, códigos, tokens y llamadas, y el token que Claude tenía ya no abre `/mcp`. Lo de B queda.
 * (`BorrarLaCuentaTest` además exige que toda tabla con `user_id` esté en `TABLAS_DEL_USUARIO`.)
 */
class ConectorBorrarLaCuentaTest {

    private lateinit var c: AndamioDelConector

    @BeforeTest
    fun setUp() {
        val todas = (TABLAS_DEL_USUARIO.map { it.tabla } + TABLAS_DE_DOS_USUARIOS.map { it.tabla }).distinctBy { it.tableName }
        c = AndamioDelConector("conector_borrar", tablasDeMas = todas.toTypedArray())
    }

    @AfterTest
    fun tearDown() = c.cerrar()

    @Test
    fun `borrar la cuenta se lleva concesiones, codigos, tokens y llamadas, y Claude queda afuera`() = testApplication {
        with(c) { montar { borrarLaCuentaRoutes() } }
        val (deA, _) = with(c) { conectar() }
        val (deB, _) = with(c) { conectar(c.correoDeB, c.contrasenaDeB) }
        with(c) { herramienta(deA, "cuentas") }
        with(c) { herramienta(deB, "cuentas") }
        val tablas = listOf(
            ConcesionesDelConector to ConcesionesDelConector.userId,
            CodigosDelConector to CodigosDelConector.userId,
            TokensDelConector to TokensDelConector.userId,
            LlamadasDelConector to LlamadasDelConector.userId,
        )
        fun filas(uid: String) = transaction { tablas.associate { (t, col) -> t.tableName to t.selectAll().where { col eq uid }.count() } }
        assertTrue(filas(c.a.duenoA).values.all { it > 0 }, "el andamio: ${filas(c.a.duenoA)}")

        val res = client.delete("/api/me") {
            header(HttpHeaders.Authorization, "Bearer ${c.a.token(c.a.duenoA)}")
            contentType(ContentType.Application.Json)
            setBody("""{"contrasena":"${c.contrasenaDeA}","confirmacion":"BORRAR"}""")
        }
        assertEquals(HttpStatusCode.NoContent, res.status)
        assertTrue(filas(c.a.duenoA).values.all { it == 0L }, "quedó algo de A: ${filas(c.a.duenoA)}")
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(deA, "ping") }.status)
        assertTrue(filas(c.a.duenoB).values.all { it > 0 }, "lo de B sigue")
        assertEquals(HttpStatusCode.OK, with(c) { mcp(deB, "ping") }.status)
    }
}
