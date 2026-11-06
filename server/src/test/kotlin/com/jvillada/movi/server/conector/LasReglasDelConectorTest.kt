package com.jvillada.movi.server.conector

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** # Vuelta 135 · Las reglas puras del conector: retornos, CIMD, SSRF, PKCE y los textos que salen. */
class LasReglasDelConectorTest {

    @Test
    fun `solo valen los retornos de Claude, y el loopback ignora el puerto`() {
        val claude = listOf("https://claude.ai/api/mcp/auth_callback")
        assertTrue(redirectPermitido(claude, "https://claude.ai/api/mcp/auth_callback"))
        assertFalse(redirectPermitido(claude, "https://claude.ai/api/mcp/auth_callback/"))
        assertFalse(redirectPermitido(claude, "https://claude.ai/api/mcp/auth_callback?x=1"))
        assertFalse(redirectPermitido(claude, "https://claude.ai:8443/api/mcp/auth_callback"), "el puerto solo se ignora en loopback")
        val code = listOf("http://localhost/callback", "http://127.0.0.1/callback")
        assertTrue(redirectPermitido(code, "http://localhost:53682/callback"))
        assertTrue(redirectPermitido(code, "http://127.0.0.1:9999/callback"))
        assertFalse(redirectPermitido(code, "http://localhost:53682/otro"))
        assertFalse(redirectPermitido(code, "https://localhost:53682/callback"), "otro esquema")
        assertFalse(redirectPermitido(code, "http://127.0.0.1.atacante.example/callback"))
        // Un retorno ajeno no vale aunque esté declarado (I1).
        assertFalse(redirectPermitido(listOf("https://atacante.example/cb"), "https://atacante.example/cb"))
        // Un largo de más es un rechazo, no un 500 al guardarlo (M10).
        assertFalse(redirectPermitido(listOf("https://claude.ai/api/mcp/auth_callback"), "https://claude.ai/api/mcp/auth_callback" + "x".repeat(400)))
    }

    @Test
    fun `solo se declaran los retornos de Claude`() {
        assertTrue(redirectValidoParaDeclarar("https://claude.ai/api/mcp/auth_callback"))
        assertTrue(redirectValidoParaDeclarar("http://localhost/callback"))
        assertTrue(redirectValidoParaDeclarar("http://127.0.0.1:1234/callback"))
        assertFalse(redirectValidoParaDeclarar("https://claude.ai/otro"))
        assertFalse(redirectValidoParaDeclarar("https://atacante.example/api/mcp/auth_callback"))
        assertFalse(redirectValidoParaDeclarar("http://localhost/otro"))
        assertFalse(redirectValidoParaDeclarar("http://claude.ai/cb"))
        assertFalse(redirectValidoParaDeclarar("movi://cb"))
        assertFalse(redirectValidoParaDeclarar("https://claude.ai/api/mcp/auth_callback#frag"))
        assertFalse(redirectValidoParaDeclarar("http://user@localhost/callback"))
        assertFalse(redirectValidoParaDeclarar("javascript:alert(1)"))
    }

    @Test
    fun `un client_id CIMD es una URL https de Claude con camino`() {
        assertTrue(esClientIdCimd("https://claude.ai/oauth/mcp-oauth-client-metadata"))
        assertTrue(esClientIdCimd("https://claude.com/oauth/x"))
        assertFalse(esClientIdCimd("https://claude.ai"))
        assertFalse(esClientIdCimd("https://claude.ai/"))
        assertFalse(esClientIdCimd("http://claude.ai/oauth/x"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/../x"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/x#y"))
        assertFalse(esClientIdCimd("https://claude.ai:22/oauth/x"), "solo el 443")
        assertFalse(esClientIdCimd("https://evil.example/oauth/x"), "nunca un host cualquiera")
        assertFalse(esClientIdCimd("https://claude.ai.evil.example/oauth/x"))
    }

    @Test
    fun `el lector de CIMD nunca le pega a un host privado`() {
        listOf(
            "127.0.0.1", "localhost", "10.0.0.8", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "::1", "0.0.0.0",
            "fd00::1", "198.18.0.1", "192.0.0.8", "240.0.0.1", "fec0::1", "64:ff9b::a00:1",
        )
            .forEach { assertFalse(LectorDeCimdPorRed.hostEsPublico(it), it) }
        assertTrue(LectorDeCimdPorRed.hostEsPublico("8.8.8.8"))
    }

    @Test
    fun `PKCE S256 compara el desafio con el verificador`() {
        val verificador = "verificador-de-prueba-con-mas-de-cuarenta-y-tres-caracteres-123"
        val desafio = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray()))
        assertTrue(pkceCoincide(verificador, desafio))
        assertFalse(pkceCoincide(verificador + "x", desafio))
        assertFalse(pkceCoincide("corto", desafio), "menos de 43 caracteres")
        assertFalse(pkceCoincide(desafio, desafio), "plain no vale")
    }

    @Test
    fun `el nombre sale del retorno, nunca del nombre que dice el cliente`() {
        assertEquals("Claude", nombreDelCliente("https://claude.ai/api/mcp/auth_callback"))
        assertEquals("Claude Code", nombreDelCliente("http://localhost:1234/callback"))
        assertEquals("Mi app rara", nombreLimpio("Mi\u0000 app\n\u202Erara"))
    }

    @Test
    fun `un texto de afuera sale en una linea, corto y sin numeros completos`() {
        assertEquals("Ahorros ••5678", textoSeguro("Ahorros 0012345678"))
        assertEquals("Tarjeta ••5678", textoSeguro("Tarjeta 4513 2200 1234 5678"))
        assertEquals("Hipoteca 1254", textoSeguro("Hipoteca 1254"))
        assertEquals("Uno dos", textoSeguro("Uno\n\u0007dos"))
        val largo = textoSeguro("a".repeat(500))
        assertEquals(LARGO_MAXIMO_DE_UN_TEXTO, largo.length)
        assertTrue(largo.endsWith("…"))
    }
}
