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
    fun `un client_id CIMD es exactamente uno de los de Claude`() {
        assertTrue(esClientIdCimd("https://claude.ai/oauth/mcp-oauth-client-metadata"))
        assertTrue(esClientIdCimd("https://claude.ai/oauth/claude-code-client-metadata"))
        // N3 · ni otra ruta del mismo host: nunca se le pide a claude.ai una URL inventada.
        assertFalse(esClientIdCimd("https://claude.ai/oauth/x"))
        assertFalse(esClientIdCimd("https://claude.com/oauth/x"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/mcp-oauth-client-metadata/"))
        assertFalse(esClientIdCimd("https://claude.ai/oauth/mcp-oauth-client-metadata?x=1"))
        assertFalse(esClientIdCimd("http://claude.ai/oauth/mcp-oauth-client-metadata"))
        assertFalse(esClientIdCimd("https://evil.example/oauth/x"))
    }

    @Test
    fun `el client_id tiene que tener forma antes de tocar un balde`() {
        assertTrue(clientIdConForma("https://claude.ai/oauth/mcp-oauth-client-metadata"))
        assertTrue(clientIdConForma(idDelClienteDcr(listOf("https://claude.ai/api/mcp/auth_callback"))))
        assertFalse(clientIdConForma("mvc_cli_xyz"))
        assertFalse(clientIdConForma("https://claude.ai/oauth/otro"))
        assertFalse(clientIdConForma("x".repeat(300)))
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
    fun `la IP de un balde es la direccion en IPv4 y el 64 en IPv6`() {
        assertEquals("203.0.113.7", claveDeIp("203.0.113.7"))
        assertEquals("2001:db8:1:2::/64", claveDeIp("2001:db8:1:2:3:4:5:6"))
        assertEquals(claveDeIp("2001:db8:1:2::1"), claveDeIp("2001:0db8:0001:0002:ffff:ffff:ffff:ffff"))
        assertEquals("no-es-una-ip.example", claveDeIp("no-es-una-ip.example"), "nunca se resuelve un nombre")
    }

    @Test
    fun `el rango de salida de Anthropic se reconoce sin resolver nombres`() {
        assertTrue(esIpDeAnthropic("160.79.104.0"))
        assertTrue(esIpDeAnthropic("160.79.111.255"))
        assertFalse(esIpDeAnthropic("160.79.112.0"))
        assertFalse(esIpDeAnthropic("160.79.103.255"))
        assertFalse(esIpDeAnthropic("8.8.8.8"))
        assertFalse(esIpDeAnthropic("claude.ai"))
        assertFalse(esIpDeAnthropic("2001:db8::1"))
    }

    @Test
    fun `un aviso con tope escribe uno por minuto y dice cuantos omitio`() {
        val escritos = mutableListOf<String>()
        val aviso = AvisoConTope(60_000L) { escritos += it }
        repeat(100) { aviso.avisar("rango", 1_000L + it) { "lleno" } }
        assertEquals(listOf("lleno"), escritos)
        aviso.avisar("rango", 61_001L) { "lleno" }
        assertEquals(2, escritos.size)
        assertTrue("99 avisos iguales omitidos" in escritos[1], escritos[1])
        aviso.avisar("otro", 61_002L) { "otro" }
        assertEquals("otro", escritos.last(), "cada clave lleva su tope")
    }

    @Test
    fun `el limitador del conector nunca guarda mas de su tope de claves`() {
        val l = LimitadorAcotado(1_000)
        repeat(50_000) { l.permitir("cliente-inventado-$it", 10, 60_000L) }
        assertEquals(1_000, l.llaves())
        // Y sigue limitando lo que se usa.
        repeat(3) { assertTrue(l.permitir("claude", 3, 60_000L)) }
        assertFalse(l.permitir("claude", 3, 60_000L))
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
        // M5 · un monto y una fecha salen tal cual; un número de cuenta, no.
        assertEquals("Pagaste $1.500.000 a ••5432", textoSeguro("Pagaste $1.500.000 a 0098765432"))
        assertEquals("Compra por COP27.500 el 2026-10-01", textoSeguro("Compra por COP27.500 el 2026-10-01"))
        assertEquals("Abono 1.234.567,89 del 01/10/2026", textoSeguro("Abono 1.234.567,89 del 01/10/2026"))
        assertEquals("Monto $••5678", textoSeguro("Monto $12345678"), "8 dígitos seguidos no son un monto")
        assertEquals("Cuenta ••5678 · 2026-11-06", textoSeguro("Cuenta 0012345678 · 2026-11-06"))
        // N4 · cédulas, cuentas con puntos y cualquier cifra después de «$» se recortan; las fechas inválidas también.
        assertEquals("CC ••4050", textoSeguro("CC 1.020.304.050"))
        assertEquals("CC ••3456", textoSeguro("CC 79.123.456"))
        assertEquals("Cuenta ••7890", textoSeguro("Cuenta 001.234.567.890"))
        assertEquals("Llave ••4050", textoSeguro("Llave 1,020,304,050"))
        assertEquals("Pago $••5678", textoSeguro("Pago $4513220012345678"))
        assertEquals("Ref ••5678", textoSeguro("Ref 12/34/5678"))
        assertEquals("Cel ••4567", textoSeguro("Cel 300.123.4567"))
        assertEquals("Cta ••8901", textoSeguro("Cta 123-456789-01"))
        assertEquals("Tarjeta ••5678", textoSeguro("Tarjeta 4513 2200 1234 5678"))
        // Montos y fechas de verdad, enteros.
        assertEquals("Pagaste $1.500.000.", textoSeguro("Pagaste $1.500.000."))
        assertEquals("Retiro COP 600.000 el 2026-11-06", textoSeguro("Retiro COP 600.000 el 2026-11-06"))
        assertEquals("Cobro USD 1,234.56 del 31/12/2026", textoSeguro("Cobro USD 1,234.56 del 31/12/2026"))
        assertEquals("Abono 9.999.999 y 25,000", textoSeguro("Abono 9.999.999 y 25,000"))
        assertEquals("Monto $27500", textoSeguro("Monto $27500"))
        assertEquals("Fecha ••1301", textoSeguro("Fecha 2026-13-01"), "un mes que no existe no es una fecha")
        assertEquals("Cuotas 1, 2, 3, 4, 5", textoSeguro("Cuotas 1, 2, 3, 4, 5"))
        // M-b · hasta tres separadores entre dígitos.
        assertEquals("Tarjeta ••5678", textoSeguro("Tarjeta 4513 - 2200 - 1234 - 5678"))
        assertEquals("Tarjeta ••5678", textoSeguro("Tarjeta 4513  2200  1234  5678"))
        assertEquals("Tarjeta ••5678", textoSeguro("Tarjeta 4513_2200_1234_5678"))
        assertEquals("ref ••5678", textoSeguro("ref 1.234.567.8"))
        assertEquals("Abono 1.234.567,89", textoSeguro("Abono 1.234.567,89"))
        assertEquals("Cuotas 1, 2, 3", textoSeguro("Cuotas 1, 2, 3"))
        val largo = textoSeguro("a".repeat(500))
        assertEquals(LARGO_MAXIMO_DE_UN_TEXTO, largo.length)
        assertTrue(largo.endsWith("…"))
    }
}
