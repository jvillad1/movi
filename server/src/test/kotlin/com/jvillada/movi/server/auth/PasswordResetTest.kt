package com.jvillada.movi.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Lógica pura del reset de contraseña: generación del token, hash, expiración y el piso de
 * tiempo anti-enumeración. Todo esto es testeable sin la DB ni Ktor a propósito — es la parte
 * donde un error es silencioso (un token predecible sigue "funcionando").
 */
class PasswordResetTest {

    // ── Generación ────────────────────────────────────────────────────────────

    @Test
    fun `el token tiene al menos 256 bits de entropia`() {
        assertTrue(PasswordReset.TOKEN_BYTES >= 32, "TOKEN_BYTES=${PasswordReset.TOKEN_BYTES}")
    }

    @Test
    fun `el token es url-safe y sin padding — viaja en un query string`() {
        val token = PasswordReset.generateToken()
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "token='$token'")
        assertFalse(token.contains('='))
        // 32 bytes en base64 sin padding = 43 caracteres
        assertEquals(43, token.length)
    }

    @Test
    fun `dos tokens seguidos nunca coinciden y no se repiten en 1000 tiradas`() {
        val tokens = List(1000) { PasswordReset.generateToken() }
        assertEquals(1000, tokens.toSet().size, "hubo colisiones — el generador no es aleatorio")
    }

    // ── Hash ──────────────────────────────────────────────────────────────────

    @Test
    fun `el hash es determinista y de 64 hex — SHA-256`() {
        val token = PasswordReset.generateToken()
        val a = PasswordReset.hashToken(token)
        val b = PasswordReset.hashToken(token)
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertTrue(a.all { it in "0123456789abcdef" }, "hash='$a'")
    }

    @Test
    fun `el hash no deja recuperar el token y cambia con un solo caracter`() {
        val token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val hash = PasswordReset.hashToken(token)
        assertFalse(hash.contains(token))
        assertNotEquals(hash, PasswordReset.hashToken(token.dropLast(1) + "B"))
    }

    @Test
    fun `vector conocido de SHA-256 — el hash es el que decimos que es`() {
        // sha256("abc") — vector estándar FIPS 180-4
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PasswordReset.hashToken("abc"),
        )
    }

    // ── Expiración ────────────────────────────────────────────────────────────

    @Test
    fun `el token vive una hora`() {
        assertEquals(60 * 60 * 1000L, PasswordReset.TTL_MS)
        assertEquals(1_000_000L + 3_600_000L, PasswordReset.expiryFor(1_000_000L))
    }

    @Test
    fun `un token recien emitido no esta vencido`() {
        val now = 1_000_000L
        assertFalse(PasswordReset.isExpired(PasswordReset.expiryFor(now), now))
    }

    @Test
    fun `un segundo antes de vencer sigue vivo`() {
        val now = 1_000_000L
        val exp = PasswordReset.expiryFor(now)
        assertFalse(PasswordReset.isExpired(exp, exp - 1))
    }

    @Test
    fun `en el instante exacto del vencimiento ya vencio — falla cerrado`() {
        val exp = PasswordReset.expiryFor(1_000_000L)
        assertTrue(PasswordReset.isExpired(exp, exp))
        assertTrue(PasswordReset.isExpired(exp, exp + 1))
    }

    // ── Piso de tiempo anti-enumeración ───────────────────────────────────────

    @Test
    fun `el piso rellena lo que falta para llegar al minimo`() {
        assertEquals(200L, PasswordReset.remainingFloorMs(elapsedMs = 50, floorMs = 250))
        assertEquals(0L, PasswordReset.remainingFloorMs(elapsedMs = 250, floorMs = 250))
    }

    @Test
    fun `si ya se paso del piso no espera mas — nunca devuelve negativo`() {
        assertEquals(0L, PasswordReset.remainingFloorMs(elapsedMs = 5_000, floorMs = 250))
    }

    @Test
    fun `hay un piso configurado y no es cero`() {
        assertTrue(PasswordReset.REQUEST_FLOOR_MS > 0)
    }
}
