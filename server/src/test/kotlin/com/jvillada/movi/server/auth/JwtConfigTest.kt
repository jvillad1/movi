package com.jvillada.movi.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtConfigTest {
    @Test
    fun `resolveSecret returns provided value`() {
        assertEquals("abc123", JwtConfig.resolveSecret(env = "abc123", fromFile = null))
    }

    @Test
    fun `resolveSecret falls back to env file`() {
        assertEquals("filesecret", JwtConfig.resolveSecret(env = null, fromFile = "filesecret"))
    }

    @Test
    fun `resolveSecret throws when nothing is set`() {
        val ex = assertFailsWith<IllegalStateException> {
            JwtConfig.resolveSecret(env = null, fromFile = null)
        }
        assertEquals(true, ex.message?.contains("JWT_SECRET"))
    }

    @Test
    fun `resolveSecret rejects blank`() {
        assertFailsWith<IllegalStateException> { JwtConfig.resolveSecret(env = "  ", fromFile = null) }
    }
}
