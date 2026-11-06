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
    fun `el retorno es exacto, salvo el puerto de un loopback declarado`() {
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
    }

    @Test
    fun `solo se declaran retornos https o de loopback`() {
        assertTrue(redirectValidoParaDeclarar("https://claude.ai/api/mcp/auth_callback"))
        assertTrue(redirectValidoParaDeclarar("http://localhost/callback"))
        assertFalse(redirectValidoParaDeclarar("http://claude.ai/cb"))
        assertFalse(redirectValidoParaDeclarar("movi://cb"))
        assertFalse(redirectValidoParaDeclarar("https://claude.ai/cb#frag"))
        assertFalse(redirectValidoParaDeclarar("https://user@claude.ai/cb"))
        assertFalse(redirectValidoParaDeclarar("javascript:alert(1)"))
    }

    @Test
    fun `un client_id CIMD es una URL https con camino`() {
        assertTrue(esClientIdCimd("https://claude.ai/oauth/mcp-oauth-client-metadata"))
        assertFalse(esClientIdCimd("https://claude.ai"))
        assertFalse(esClientIdCimd("https://claude.ai/"))
        assertFalse(esClientIdCimd("http://claude.ai/oauth/x"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/../x"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/x#y"))
    }

    @Test
    fun `el lector de CIMD nunca le pega a un host privado`() {
        listOf("127.0.0.1", "localhost", "10.0.0.8", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "::1", "0.0.0.0", "fd00::1")
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
    fun `solo claude ai se llama Claude, lo demas por su nombre limpio`() {
        val deClaude = ClienteDelConector("https://claude.ai/oauth/mcp-oauth-client-metadata", "Otro nombre", emptyList(), esCimd = true)
        assertEquals("Claude", nombreDelCliente(deClaude, "https://claude.ai/api/mcp/auth_callback"))
        val code = ClienteDelConector("https://claude.ai/oauth/claude-code-client-metadata", "", emptyList(), esCimd = true)
        assertEquals("Claude Code", nombreDelCliente(code, "http://localhost:1234/callback"))
        val impostor = ClienteDelConector("mvc_cli_x", "Claude", emptyList(), esCimd = false)
        assertEquals("Claude", nombreDelCliente(impostor, "https://claude.ai/api/mcp/auth_callback"), "vuelve a claude.ai: es Claude")
        val otro = ClienteDelConector("https://otro.example/cimd", nombreLimpio("Mi\u0000 app\n‮rara"), emptyList(), esCimd = true)
        assertEquals("Mi app rara", nombreDelCliente(otro, "https://otro.example/cb"))
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
