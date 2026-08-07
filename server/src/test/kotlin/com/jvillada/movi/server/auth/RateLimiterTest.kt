package com.jvillada.movi.server.auth

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    @BeforeTest
    fun reset() = RateLimiter.reset()

    /**
     * First 10 calls within the window are allowed; the 11th is denied.
     */
    @Test
    fun `10 attempts allowed then 11th denied`() {
        val key = "test-ip-${System.nanoTime()}" // unique per test run
        repeat(10) { i ->
            assertTrue(RateLimiter.allow(key, maxAttempts = 10, windowMs = 60_000L),
                "Attempt ${i + 1} should be allowed")
        }
        assertFalse(RateLimiter.allow(key, maxAttempts = 10, windowMs = 60_000L),
            "11th attempt should be denied")
    }

    /**
     * Different keys are tracked independently — one key being exhausted
     * must not affect another.
     */
    @Test
    fun `distinct keys are independent`() {
        val keyA = "ip-a-${System.nanoTime()}"
        val keyB = "ip-b-${System.nanoTime()}"

        repeat(10) { RateLimiter.allow(keyA, maxAttempts = 10, windowMs = 60_000L) }
        assertFalse(RateLimiter.allow(keyA, maxAttempts = 10, windowMs = 60_000L),
            "keyA should be exhausted")

        assertTrue(RateLimiter.allow(keyB, maxAttempts = 10, windowMs = 60_000L),
            "keyB should still be allowed")
    }

    /**
     * Lo que el barrido NO debe hacer: regalar intentos. Un barrido demasiado agresivo es la
     * forma silenciosa de romper el límite — nadie ve un error, solo entran más intentos.
     *
     * La otra mitad —que efectivamente descarte las claves vencidas y acote la memoria— NO
     * está cubierta acá: el barrido descarta por RETENTION_MS (1 h), no por la ventana del
     * límite, y RateLimiter lee `System.currentTimeMillis()` directo, sin costura para
     * adelantar el reloj. Verificarlo exigiría inyectar el tiempo. Se deja anotado en vez de
     * fingir cobertura con un test que pasa por otra razón.
     */
    @Test
    fun `el barrido conserva las claves con intentos dentro de la ventana`() {
        RateLimiter.allow("viva", maxAttempts = 10, windowMs = 60 * 60_000L)
        repeat(600) { RateLimiter.allow("efimera:$it", maxAttempts = 1, windowMs = 1L) }
        repeat(9) {
            assertTrue(RateLimiter.allow("viva", maxAttempts = 10, windowMs = 60 * 60_000L),
                "la clave viva no debía perder sus intentos en el barrido")
        }
        assertFalse(RateLimiter.allow("viva", maxAttempts = 10, windowMs = 60 * 60_000L),
            "el intento 11 debía denegarse: el barrido no puede regalar intentos")
    }
}
