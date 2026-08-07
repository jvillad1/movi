package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La política de contraseñas es la única fuente de verdad compartida por :server y :shared.
 * Estos tests fijan el contrato — sobre todo la AUSENCIA de reglas de composición, que es
 * una decisión deliberada (NIST SP 800-63B) y no un olvido.
 */
class PasswordPolicyTest {

    @Test
    fun `el minimo es 12`() {
        assertEquals(12, PasswordPolicy.MIN_LENGTH)
    }

    @Test
    fun `una contrasena de 11 caracteres es demasiado corta`() {
        assertEquals(PasswordProblem.TOO_SHORT, PasswordPolicy.problemWith("a".repeat(11)))
        assertFalse(PasswordPolicy.isValid("a".repeat(11)))
    }

    @Test
    fun `exactamente el minimo es valido`() {
        assertNull(PasswordPolicy.problemWith("a".repeat(PasswordPolicy.MIN_LENGTH)))
        assertTrue(PasswordPolicy.isValid("a".repeat(PasswordPolicy.MIN_LENGTH)))
    }

    @Test
    fun `la contrasena vacia es demasiado corta`() {
        assertEquals(PasswordProblem.TOO_SHORT, PasswordPolicy.problemWith(""))
    }

    @Test
    fun `el maximo no baja de 64 — NIST pide aceptar al menos esa longitud`() {
        assertTrue(PasswordPolicy.MAX_LENGTH >= 64, "MAX_LENGTH=${PasswordPolicy.MAX_LENGTH}")
        assertTrue(PasswordPolicy.isValid("a".repeat(64)))
    }

    @Test
    fun `pasarse del maximo es TOO_LONG`() {
        assertNull(PasswordPolicy.problemWith("a".repeat(PasswordPolicy.MAX_LENGTH)))
        assertEquals(
            PasswordProblem.TOO_LONG,
            PasswordPolicy.problemWith("a".repeat(PasswordPolicy.MAX_LENGTH + 1)),
        )
    }

    /**
     * Regla de composición = regla que rechaza una contraseña por lo que CONTIENE en vez de
     * por cuánto mide. Si alguien agrega una, este test se cae — que es exactamente lo que
     * queremos, porque NIST SP 800-63B las desaconseja explícitamente.
     */
    @Test
    fun `no hay reglas de composicion — solo longitud`() {
        val soloMinusculas = "correcthorsebatterystaple"
        val soloDigitos    = "184729103847"
        val soloEspacios   = " ".repeat(20)
        val conEmoji       = "contraseña🐴🔋📎"
        val frase          = "mi gato se llama pancho"
        for (p in listOf(soloMinusculas, soloDigitos, soloEspacios, conEmoji, frase)) {
            assertTrue(
                PasswordPolicy.isValid(p),
                "'$p' (${p.length} chars) debería ser válida: solo se valida longitud",
            )
        }
    }

    @Test
    fun `el mensaje nombra el numero real para que UI y servidor no diverjan`() {
        assertTrue(PasswordPolicy.messageFor(PasswordProblem.TOO_SHORT).contains("${PasswordPolicy.MIN_LENGTH}"))
        assertTrue(PasswordPolicy.messageFor(PasswordProblem.TOO_LONG).contains("${PasswordPolicy.MAX_LENGTH}"))
    }
}
