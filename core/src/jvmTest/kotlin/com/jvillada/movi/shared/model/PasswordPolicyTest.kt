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
        assertTrue(PasswordPolicy.MAX_BYTES >= 64, "MAX_BYTES=${PasswordPolicy.MAX_BYTES}")
        assertTrue(PasswordPolicy.isValid("a".repeat(64)))
    }

    @Test
    fun `pasarse del maximo es TOO_LONG`() {
        assertNull(PasswordPolicy.problemWith("a".repeat(PasswordPolicy.MAX_BYTES)))
        assertEquals(
            PasswordProblem.TOO_LONG,
            PasswordPolicy.problemWith("a".repeat(PasswordPolicy.MAX_BYTES + 1)),
        )
    }

    /**
     * El techo se mide en BYTES UTF-8 porque eso es lo que mide BCrypt, que además **lanza**
     * `IllegalArgumentException` al pasarse (`at.favre.lib:bcrypt` usa `LongPasswordStrategies
     * .strict()`, no trunca). Contar caracteres era permisivo en la dirección peligrosa: esta
     * frase tiene 65 caracteres —bajo cualquier techo de 72 caracteres— y 73 bytes, así que
     * habría llegado hasta adentro de BCrypt y salido como un 500.
     *
     * Es exactamente el caso de esta app: español, con tildes, y un mínimo de 12 que empuja a
     * escribir frases.
     */
    @Test
    fun `una frase en espanol bajo el limite de caracteres pero sobre el de bytes es TOO_LONG`() {
        val frase = "mi contraseña es una frase larga con muchas tildes: á é í ó ú ñ ü"
        assertEquals(65, frase.length)
        assertEquals(73, PasswordPolicy.byteLength(frase))
        assertTrue(frase.length <= PasswordPolicy.MAX_BYTES, "la trampa: por caracteres pasaba")
        assertEquals(PasswordProblem.TOO_LONG, PasswordPolicy.problemWith(frase))
    }

    /** El borde real con acentos: 36 eñes son 72 bytes exactos (OK), 37 son 74 (no). */
    @Test
    fun `el borde con acentos esta en bytes, no en caracteres`() {
        assertEquals(72, PasswordPolicy.byteLength("ñ".repeat(36)))
        assertNull(PasswordPolicy.problemWith("ñ".repeat(36)))

        assertEquals(74, PasswordPolicy.byteLength("ñ".repeat(37)))
        assertEquals(PasswordProblem.TOO_LONG, PasswordPolicy.problemWith("ñ".repeat(37)))
    }

    /** Para ASCII puro, bytes y caracteres coinciden: nada cambió para el caso común. */
    @Test
    fun `en ascii puro bytes y caracteres son lo mismo`() {
        val ascii = "una-contrasena-sin-acentos"
        assertEquals(ascii.length, PasswordPolicy.byteLength(ascii))
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
        assertTrue(PasswordPolicy.messageFor(PasswordProblem.TOO_LONG).contains("${PasswordPolicy.MAX_BYTES}"))
    }
}
