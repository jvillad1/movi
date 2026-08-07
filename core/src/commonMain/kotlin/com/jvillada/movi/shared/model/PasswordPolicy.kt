package com.jvillada.movi.shared.model

/**
 * Política de contraseñas de movi — ÚNICA fuente de verdad.
 *
 * Vive en `:core` a propósito: `:server` la usa para la validación autoritativa (registro y
 * cambio de contraseña) y `:shared` la usa para la validación de UI. Antes el mínimo estaba
 * escrito a mano en dos lugares (`AuthRoutes.kt` y `RegisterScreen.kt`) más un comentario, y
 * cambiar uno no cambiaba el otro. Esa clase de invariante duplicada ya nos mordió varias
 * veces en este repo; acá queda estructural: hay un solo número y un solo mensaje.
 *
 * **No agregar reglas de composición.** NIST SP 800-63B §3.1.1.2 desaconseja explícitamente
 * exigir símbolos, dígitos o mayúsculas: en la práctica empujan a la gente hacia patrones
 * predecibles (`Password1!`) y BAJAN la fuerza real de las contraseñas. La palanca que sí
 * importa es la longitud. Por eso acá solo se mide longitud.
 */
object PasswordPolicy {

    /**
     * **12 caracteres.**
     *
     * NIST SP 800-63B fija 8 como piso absoluto para contraseñas elegidas por la persona;
     * 12 es el piso que recomiendan las guías modernas (CISA, OWASP ASVS L2) cuando lo que
     * se protege es material sensible. movi guarda las finanzas COMPLETAS de una persona
     * —cuentas, saldos, créditos, movimientos importados del banco— detrás de un registro
     * público, así que el objetivo correcto es el nivel "sensible", no el mínimo legal.
     *
     * Aritmética: con BCrypt cost 12 (~4 hashes/s/core en hardware de ataque razonable) una
     * contraseña de 12 caracteres aleatorios en minúsculas son ~56 bits, muy fuera de alcance
     * offline. Con 6 caracteres eran ~28 bits: crackeable en horas incluso con BCrypt.
     *
     * Subirlo más (14, 16) no es gratis: empuja a la gente a reusar contraseñas o anotarlas.
     * 12 es el punto donde el ataque offline deja de ser el eslabón débil.
     */
    const val MIN_LENGTH = 12

    /**
     * **72 BYTES en UTF-8** — no caracteres. No es una restricción de política sino el límite
     * real de BCrypt.
     *
     * Ojo con la creencia habitual: `at.favre.lib:bcrypt` **no trunca en silencio**. Usa
     * `LongPasswordStrategies.strict()` por defecto y **lanza** `IllegalArgumentException`
     * ("password must not be longer than 72 bytes plus null terminator encoded in utf-8").
     * Verificado ejecutando la librería:
     *
     * ```
     * "a" x 72                      chars=72  bytes=72   -> OK
     * "a" x 73                      chars=73  bytes=73   -> IllegalArgumentException
     * "ñ" x 36                      chars=36  bytes=72   -> OK          ← el borde real
     * "ñ" x 37                      chars=37  bytes=74   -> IllegalArgumentException
     * "mi contraseña … á é í ó ú ñ" chars=65  bytes=73   -> IllegalArgumentException
     * ```
     *
     * Por eso contar caracteres era **permisivo**, no conservador: una frase en español de 65
     * caracteres pasaba la política y reventaba dentro de BCrypt. Sin `StatusPages`, eso salía
     * como un 500 pelado en el camino de recuperación de cuenta. Acá se cuentan bytes, que es
     * exactamente lo que mide la librería.
     *
     * Cumple de sobra la recomendación de NIST de aceptar al menos 64: 72 bytes son 72
     * caracteres ASCII, y aun con acentos en cada letra quedan 36 — pero el mínimo es 12, así
     * que ninguna contraseña razonable choca contra este techo.
     */
    const val MAX_BYTES = 72

    /** Lo que realmente cuenta BCrypt. Idéntico a `length` para ASCII puro. */
    fun byteLength(password: String): Int = password.encodeToByteArray().size

    /**
     * `null` si la contraseña cumple la política.
     *
     * Asimetría deliberada: el **mínimo** se mide en caracteres (es una promesa a la persona:
     * "al menos 12 caracteres"; medirlo en bytes dejaría pasar una contraseña de 10 letras
     * acentuadas) y el **máximo** en bytes (es el límite de BCrypt, que no negocia).
     */
    fun problemWith(password: String): PasswordProblem? = when {
        password.length < MIN_LENGTH -> PasswordProblem.TOO_SHORT
        byteLength(password) > MAX_BYTES -> PasswordProblem.TOO_LONG
        else -> null
    }

    fun isValid(password: String): Boolean = problemWith(password) == null

    /**
     * Mensaje en español para mostrarle a la persona. Compartido para que la UI y el servidor
     * digan exactamente lo mismo y el número salga siempre de [MIN_LENGTH] / [MAX_BYTES].
     *
     * El mensaje de "demasiado larga" nombra los bytes y además explica por qué el número no
     * coincide con lo que la persona ve escrito: sin esa aclaración, "no puede superar los 72"
     * frente a una frase de 65 caracteres se lee como un bug.
     */
    fun messageFor(problem: PasswordProblem): String = when (problem) {
        PasswordProblem.TOO_SHORT -> "La contraseña debe tener al menos $MIN_LENGTH caracteres"
        PasswordProblem.TOO_LONG  ->
            "La contraseña no puede superar los $MAX_BYTES bytes (las tildes, la ñ y los emojis " +
                "ocupan más de un byte, así que el corte puede llegar antes de los $MAX_BYTES caracteres)"
    }
}

enum class PasswordProblem { TOO_SHORT, TOO_LONG }
