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
}
